/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nbandroid.netbeans.gradle.launch;

import com.android.ddmlib.*;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.StdLogger;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nbandroid.netbeans.gradle.v2.sdk.AndroidSdk;
import org.nbandroid.netbeans.gradle.v2.sdk.AndroidSdkProvider;
import org.netbeans.api.io.InputOutput;
import org.netbeans.api.project.Project;
import org.netbeans.modules.android.project.api.NbAndroidProjectImpl;
import org.netbeans.modules.android.project.launch.AdbUtils;
import org.netbeans.modules.android.spi.MainActivityConfiguration;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;

/**
 *
 * @author radim
 */
public class AndroidLauncherImpl implements AndroidLauncher {

    private static final Logger LOG = Logger.getLogger(AndroidLauncherImpl.class.getName());
    private static final RequestProcessor RP = new RequestProcessor(AndroidLauncherImpl.class);

    private InputOutput io;

    public AndroidLauncherImpl() {
    }

    @Override
    public Future<Client> launch(Lookup context, String mode) {

        LaunchConfiguration launchCfg = context.lookup(LaunchConfiguration.class);
        Project project = Preconditions.checkNotNull(context.lookup(Project.class));
        io = project.getLookup().lookup(InputOutput.class);
        AndroidSdk sdk = AndroidSdkProvider.getDefaultSdk();
        AndroidSdkHandler androidSdkHandler = sdk.getAndroidSdkHandler();
        AvdManager avdManager = null;
        try {
            avdManager = AvdManager.getInstance(androidSdkHandler, new StdLogger(StdLogger.Level.INFO));
        } catch (AndroidLocation.AndroidLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
        AdbUtils.LaunchData launchData = AdbUtils.getSelectedDevice((NbAndroidProjectImpl) project);
        if (launchData == null) {
            LOG.log(Level.INFO, "No target AVD selected or found. Cancelling launch");
            return null;
        }

        // TODO check if we need to start emulator
        if (launchData.getDevice() == null) {
            LOG.log(Level.INFO, "Device not running. Launch emulator");
            // TODO det Future<int> and stop launch asynchronously(?) if the command fails
            Map<String, IDevice> runningEmulators = AdbUtils.getRunningEmulators();
            IDevice device = runningEmulators.get(launchData.getAvdInfo().getName());
            if (device == null) {
                Future<IDevice> toBeDevice = new EmulatorLauncher(sdk)
                        .launchEmulator(launchData.getAvdInfo(), launchCfg);
                try {
                    launchData = new AdbUtils.LaunchData(launchData.getAvdInfo(), toBeDevice.get());
                } catch (InterruptedException | ExecutionException ex) {
                    LOG.log(Level.INFO, null, ex);
                    return null;
                }
            } else {
                launchData = new AdbUtils.LaunchData(launchData.getAvdInfo(), device);
            }

        }
        LaunchInfo info = context.lookup(LaunchInfo.class);
        if (!simpleLaunch(info, launchData.getDevice())) {
            return null;
        }
        LaunchAction launchAction = Preconditions.checkNotNull(context.lookup(LaunchAction.class));

        if (!launchAction.doLaunch(info, launchData.getDevice(), project, context.lookup(MainActivityConfiguration.class))) {
            return null;
        }
        final RunnableFuture<Client> futureClient = new ClientFuture(launchData.getDevice(), info);
        RP.execute(futureClient);
        return futureClient;
    }

    @Override
    public boolean simpleLaunch(LaunchInfo launchInfo, IDevice device) {
        // API level check
        if (!checkBuildInfo(launchInfo, device)) {
            LOG.log(Level.INFO, "Launch cancelled! Build info does not pass the check");
            stopLaunch(launchInfo);
            return false;
        }

        // sync the app
        if (!syncApp(launchInfo, device)) {
            LOG.log(Level.INFO, "Launch cancelled! Cannot sync the app");
            stopLaunch(launchInfo);
            return false;
        }

        return true;
    }

    private boolean checkBuildInfo(LaunchInfo launchInfo, IDevice device) {
        // TODO(radim): check IDevice.PROP_BUILD_* to find if we can deploy in this device
        return true;
    }

    private void stopLaunch(LaunchInfo launchInfo) {
        // seems no-op at the moment
    }

    private boolean syncApp(LaunchInfo launchInfo, IDevice device) {
        if (needsInstall(launchInfo, device)) {
            return doSyncApp(launchInfo, device);
        }
        return true;
    }

    private boolean needsInstall(LaunchInfo launchInfo, IDevice device) {
        // TODO(radim): check if it is already installed (or if we want to replace it)
        return true;
    }

    private boolean doSyncApp(LaunchInfo launchInfo, IDevice device) {
        try {
            // copy (upload) package to device
            if (device == null || launchInfo.packageFile == null) {
                LOG.log(Level.WARNING,
                        "Cannot deploy the application. device = {0}, packageFile = {1}",
                        new Object[]{device, launchInfo.packageFile});
                return false;
            }
            String remotePackagePath = device.syncPackageToDevice(launchInfo.packageFile.getPath());
            // install app
            boolean installResult = installPackage(launchInfo, remotePackagePath, device);
            // remove uploaded package
            device.removeRemotePackage(remotePackagePath);

            return installResult;
        } catch (InstallException | SyncException | AdbCommandRejectedException | TimeoutException | IOException e) {
            logEx(e, launchInfo, device);
        }
        return false;
    }

    private static void logEx(Exception e, LaunchInfo launchInfo, IDevice device) {
        LOG.log(Level.WARNING,
                "Failed to upload " + (launchInfo != null ? launchInfo.packageFile : "<no file>")
                + " on device " + (device != null ? device.getSerialNumber() : "<no device>"),
                e);
    }
    
    public static final String BLACK = "\033[0;30m";   // BLACK
    public static final String BLUE = "\033[0;34m";    // BLUE
    public static final String RED = "\033[0;31m";    // BLUE

    boolean installPackage(LaunchInfo launchInfo, String remotePackagePath, IDevice device) {
        try {
            device.installRemotePackage(remotePackagePath, launchInfo.reinstall);
            io.getOut().println("Package " + launchInfo.packageFile.getNameExt() + " deployed");
            return true;
        } catch (InstallException ex) {
            if (launchInfo.reinstall) {
                try {
                    // maybe it is not installed. try again clean install
                    device.installRemotePackage(remotePackagePath, false);
                    io.getOut().println("Package " + launchInfo.packageFile.getNameExt() + " deployed");
                } catch (InstallException ex1) {
                    io.show();
                    io.getOut().print("\n\r");
                    io.getOut().print(RED+"Package deployment failed with:\n\r");
                    io.getOut().println(ex.getMessage()+BLACK);
                    io.getOut().print("\n\r");
                }
            }
            return false;
        }
    }

    private static final Pattern sAmErrorType = Pattern.compile("Error type (\\d+)");

    /**
     * Output receiver for am process (Activity Manager)
     *
     * Monitors adb output for am errors, and retries launch as appropriate.
     */
    public static class AMReceiver extends MultiLineReceiver {

        private final LaunchInfo launchInfo;
        private final IDevice device;
        private final InputOutput io;

        /**
         * Basic constructor.
         *
         * @param launchInfo the {@link DelayedLaunchInfo} associated with the
         * am process.
         * @param device the Android device on which the launch is done.
         */
        public AMReceiver(LaunchInfo launchInfo, IDevice device, InputOutput io) {
            this.launchInfo = launchInfo;
            this.device = device;
            this.io = io;
        }

        /**
         * Monitors the am process for error messages. If an error occurs, will
         * reattempt launch up to <code>MAX_ATTEMPT_COUNT</code> times.
         *
         * @param lines a portion of the am output
         *
         * @see MultiLineReceiver#processNewLines(String[])
         */
        @Override
        public void processNewLines(String[] lines) {
            // first we check if one starts with error
            List<String> array = new ArrayList<>();
            boolean error = false;
            boolean warning = false;
            for (String s : lines) {
                // ignore empty lines.
                if (s.length() == 0) {
                    continue;
                }

                // check for errors that output an error type, if the attempt count is still
                // valid. If not the whole text will be output in the console
//        if (launchInfo.isCancelled() == false) {
                Matcher m = sAmErrorType.matcher(s);
                if (m.matches()) {
                    // get the error type
                    int type = Integer.parseInt(m.group(1));

                    final int waitTime = 3;
                    String msg;

                    switch (type) {
                        case 1:
                        /* Intended fall through */
                        case 2:
                            msg = String.format(
                                    "Device not ready in %d.",
                                    waitTime);
                            break;
                        case 3:
                            msg = String.format(
                                    "New package not yet registered with the system in %d.",
                                    waitTime);
                            break;
                        default:
                            msg = String.format(
                                    "Device not ready  in %1d (%2$d).",
                                    waitTime, type);
                            break;

                    }

                    io.getOut().println(msg);

                    // no need to parse the rest
                    // return;
                }
//        }

                // check for error if needed
                if (!error && s.startsWith("Error:")) {
                    error = true;
                }
                if (!warning && s.startsWith("Warning:")) {
                    warning = true;
                }

                // add the line to the list
                array.add("ActivityManager: " + s);
            }

            // then we display them in the console
            if (warning || error) {
                for (String l : array) {
                    io.getErr().println(l);
                }
            } else {
                for (String l : array) {
                    io.getOut().println(l);
                }
            }

            // if error then we cancel the launch, and remove the delayed info
//      if (error) {
//        mLaunchController.stopLaunch(mLaunchInfo);
//      }
        }

        /**
         * Returns true if launch has been cancelled
         */
        @Override
        public boolean isCancelled() {
            return false;
            // TODO implement cancelling
//        return launchInfo.isCancelled();
        }
    }

    private static class ClientFuture implements RunnableFuture<Client> {

        private final AtomicBoolean canceled = new AtomicBoolean();
        private final AtomicReference<Client> result = new AtomicReference<>();
        private final CountDownLatch done = new CountDownLatch(1);
        private final IDevice device;
        private final LaunchInfo launchInfo;

        private ClientFuture(final IDevice device, final LaunchInfo launchInfo) {
            assert device != null;
            assert launchInfo != null;
            this.device = device;
            this.launchInfo = launchInfo;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            canceled.set(true);
            return true;
        }

        @Override
        public boolean isCancelled() {
            return canceled.get();
        }

        @Override
        public boolean isDone() {
            return done.getCount() == 0;
        }

        @Override
        public Client get() throws InterruptedException, ExecutionException {
            if (canceled.get()) {
                throw new CancellationException();
            }
            done.await();
            return result.get();
        }

        @Override
        public Client get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
            if (canceled.get()) {
                throw new CancellationException();
            }
            done.await(timeout, unit);
            return result.get();
        }

        @Override
        public void run() {
            while (!isCancelled()) {
                final Client client = device.getClient(launchInfo.clientName);
                if (client != null) {
                    result.set(client);
                    done.countDown();
                    break;
                } else if (!device.isOnline()) {
                    result.set(null);
                    done.countDown();
                    break;
                }
                //TODO: Busy waiting, replace by listener + Latch
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }

    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.nbandroid.netbeans.gradle.v2.project.template;

import android.studio.imports.templates.Template;
import java.awt.Component;
import java.util.HashSet;
import java.util.Set;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import static org.nbandroid.netbeans.gradle.v2.project.template.AndroidProjectTemplatePanelTvActivityAndroidSettings.PROP_TV_CONFIG;
import org.openide.WizardDescriptor;
import org.openide.WizardValidationException;
import org.openide.util.HelpCtx;

/**
 * Panel just asking for basic info.
 */
public class AndroidProjectTemplateWizardPanelTvActivityAndroidSettings implements WizardDescriptor.Panel,
        WizardDescriptor.ValidatingPanel, WizardDescriptor.FinishablePanel {

    private WizardDescriptor wizardDescriptor;
    private AndroidProjectTemplatePanelTvActivityAndroidSettings component;

    public AndroidProjectTemplateWizardPanelTvActivityAndroidSettings() {
    }

    public Component getComponent() {
        if (component == null) {
            component = new AndroidProjectTemplatePanelTvActivityAndroidSettings(this);
            component.setName("Android platform");
        }
        return component;
    }

    public HelpCtx getHelp() {
        return new HelpCtx(AndroidProjectTemplatePanelTvActivityAndroidSettings.class);
    }

    public boolean isValid() {
        getComponent();
        return component.valid(wizardDescriptor);
    }

    private final Set<ChangeListener> listeners = new HashSet<ChangeListener>(1); // or can use ChangeSupport in NB 6.0

    public final void addChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public final void removeChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    protected final void fireChangeEvent() {
        wizardDescriptor.putProperty(PROP_TV_CONFIG, component.getCurrentTemplate() != null);
        Set<ChangeListener> ls;
        synchronized (listeners) {
            ls = new HashSet<ChangeListener>(listeners);
        }
        ChangeEvent ev = new ChangeEvent(this);
        for (ChangeListener l : ls) {
            l.stateChanged(ev);
        }
    }

    public void readSettings(Object settings) {
        wizardDescriptor = (WizardDescriptor) settings;
        component.read(wizardDescriptor);
    }

    public void storeSettings(Object settings) {
        WizardDescriptor d = (WizardDescriptor) settings;
        component.store(d);
    }

    public boolean isFinishPanel() {
        Template currentTemplate = component.getCurrentTemplate();
        if (currentTemplate == null) {
            return true;
        }
        return false;
    }

    public void validate() throws WizardValidationException {
        getComponent();
        component.validate(wizardDescriptor);
    }

}

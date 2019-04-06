/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.netbeans.modules.android.project.api;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.netbeans.spi.project.ProjectConfigurationProvider;

/**
 *
 * @author arsi
 */
public class NbAndroidProjectConfigurationProvider implements ProjectConfigurationProvider<NbAndroidProjectConfiguration> {

    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
    private NbAndroidProjectConfiguration activeConfiguration = new NbAndroidProjectConfiguration("DEFAULT");

    @Override
    public Collection<NbAndroidProjectConfiguration> getConfigurations() {
        List<NbAndroidProjectConfiguration> tmp = new ArrayList<>();
        tmp.add(new NbAndroidProjectConfiguration("DEFAULT"));
        return tmp;
    }

    @Override
    public NbAndroidProjectConfiguration getActiveConfiguration() {
        return activeConfiguration;
    }

    @Override
    public void setActiveConfiguration(NbAndroidProjectConfiguration c) throws IllegalArgumentException, IOException {
        activeConfiguration = c;
    }

    @Override
    public boolean hasCustomizer() {
        return false;
    }

    @Override
    public void customize() {
    }

    @Override
    public boolean configurationsAffectAction(String string) {
        return false;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener pl) {
        changeSupport.addPropertyChangeListener(pl);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener pl) {
        changeSupport.removePropertyChangeListener(pl);
    }

}

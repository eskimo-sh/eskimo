/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */


package ch.niceideas.eskimo.test.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.model.ServicesSettingsWrapper;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile("test-conf")
public class ConfigurationServiceTestImpl implements ConfigurationService {

    private static final Logger logger = Logger.getLogger(ConfigurationServiceTestImpl.class);

    @Autowired
    private ServicesDefinition servicesDefinition;

    private boolean standardKubernetesConfig = false;
    private boolean kubernetesConfigError = false;

    private boolean standard2NodesInstallStatus = false;
    private boolean standard2NodesSetup = false;
    private boolean nodesConfigError = false;

    private boolean serviceSettingsError = false;

    private boolean setupConfigNotCompletedError = false;
    private boolean setupCompleted = false;

    private ServicesSettingsWrapper serviceSettings = null;
    private String setupConfigAsString;
    private ServicesInstallStatusWrapper installStatus = null;
    private KubernetesServicesConfigWrapper kubeServicesConfig = null;

    private NodesConfigWrapper nodesConfig = null;

    public void reset() {
        this.standardKubernetesConfig = false;
        this.kubernetesConfigError = false;
        this.standard2NodesInstallStatus = false;
        this.standard2NodesSetup = false;
        this.nodesConfigError = false;
        this.serviceSettingsError = false;
        this.setupConfigNotCompletedError = false;
        this.setupCompleted = false;

        this.serviceSettings = null;
        this.setupConfigAsString = null;
        this.installStatus = null;
        this.kubeServicesConfig = null;

        this.nodesConfig = null;
    }

    public void setStandardKubernetesConfig() {
        this.standardKubernetesConfig = true;
        this.kubernetesConfigError = false;
    }

    public void setKubernetesConfigError() {
        this.standardKubernetesConfig = false;
        this.kubernetesConfigError = true;
    }

    public void setStandard2NodesInstallStatus() {
        this.standard2NodesInstallStatus = true;
        this.installStatus = null;
    }

    public void setStandard2NodesSetup() {
        this.standard2NodesSetup = true;
        this.nodesConfigError = false;
    }

    public void setNodesConfigError() {
        this.standard2NodesSetup = false;
        this.nodesConfigError = true;
    }

    public void setServiceSettingsError() {
        this.serviceSettingsError = true;
    }

    public void setSetupConfigNotCompletedError() {
        this.setupConfigNotCompletedError = true;
        setupCompleted = false;
    }

    public void setSetupCompleted() {
        this.setupConfigNotCompletedError = false;
        setupCompleted = true;
    }

    public boolean isSaveServicesInstallationStatusCalled () {
        return this.installStatus != null;
    }

    @Override
    public void saveServicesSettings(ServicesSettingsWrapper settings) throws FileException, SetupException {
        this.serviceSettings = settings;
    }

    @Override
    public ServicesSettingsWrapper loadServicesSettings() throws FileException, SetupException {
        if (serviceSettingsError) {
            throw new SetupException("Test Error");
        }
        if (serviceSettings != null) {
            return serviceSettings;
        }
        return ServicesSettingsWrapper.initEmpty(servicesDefinition);
    }

    @Override
    public ServicesSettingsWrapper loadServicesConfigNoLock() throws FileException, SetupException {
        return loadServicesSettings ();
    }

    @Override
    public void saveServicesInstallationStatus(ServicesInstallStatusWrapper status) throws FileException, SetupException {
        this.installStatus = status;
    }

    @Override
    public void updateAndSaveServicesInstallationStatus(SystemService.StatusUpdater statusUpdater) throws FileException, SetupException {
        if (this.installStatus != null) {
            statusUpdater.updateStatus(this.installStatus);
        }
    }

    @Override
    public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
        if (this.installStatus != null) {
            return this.installStatus;
        }
        if (standard2NodesInstallStatus) {
            return StandardSetupHelpers.getStandard2NodesInstallStatus();
        }
        return ServicesInstallStatusWrapper.empty();
    }

    @Override
    public void saveNodesConfig(NodesConfigWrapper nodesConfig) throws FileException, SetupException {
        this.nodesConfig = nodesConfig;
    }

    @Override
    public NodesConfigWrapper loadNodesConfig() throws SystemException, SetupException {
        if (standard2NodesSetup) {
            return StandardSetupHelpers.getStandard2NodesSetup();
        } else if (nodesConfigError) {
            throw new SystemException("Test Error");
        }
        if (nodesConfig != null) {
            return nodesConfig;
        }
        return NodesConfigWrapper.empty();
    }

    @Override
    public JsonWrapper createSetupConfigAndSaveStoragePath(String configAsString) throws SetupException, FileException {

        JsonWrapper setupConfigJSON = new JsonWrapper(configAsString);

        // First thing first : save storage path
        String configStoragePath = (String) setupConfigJSON.getValueForPath("setup_storage");

        File storagePath = new File(configStoragePath);
        if (!storagePath.exists()) {

            if (!storagePath.mkdirs()) {
                logger.debug ("mkdirs " + storagePath + " failed. Will crash on next line.");
            }

            if (!storagePath.exists()) {
                throw new SetupException("Path \"" + configStoragePath + "\" doesn't exist and couldn't be created.");
            }
        }
        if (!storagePath.canWrite()) {
            String username = System.getProperty("user.name");
            throw new SetupException("User " + username + " cannot write in path " + storagePath + " doesn't exist.");
        }

        return new JsonWrapper(configAsString);
    }

    @Override
    public void saveSetupConfig(String configAsString) throws SetupException, FileException {
        this.setupConfigNotCompletedError = false;
        this.setupConfigAsString = configAsString;
    }

    @Override
    public String loadSetupConfig() throws FileException, SetupException {
        if (setupConfigNotCompletedError) {
            throw new SetupException ("Application is not initialized properly. Missing file 'config.conf' system configuration");
        }
        return setupConfigAsString;
    }

    @Override
    public KubernetesServicesConfigWrapper loadKubernetesServicesConfig() throws SystemException {
        if (standardKubernetesConfig) {
            return StandardSetupHelpers.getStandardKubernetesConfig();
        } else if (kubernetesConfigError) {
            throw new SystemException("Test Error");
        } else if (this.kubeServicesConfig != null) {
            return this.kubeServicesConfig;
        } else {
            return KubernetesServicesConfigWrapper.empty();
        }
    }

    @Override
    public void saveKubernetesServicesConfig(KubernetesServicesConfigWrapper kubeServicesConfig) throws FileException, SetupException {
        this.kubeServicesConfig = kubeServicesConfig;
    }

}

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
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.model.ServicesSettingsWrapper;
import ch.niceideas.eskimo.services.*;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile("no-cluster")
public class ConfigurationServiceTestImpl implements ConfigurationService {

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

    public void reset() {
        this.standardKubernetesConfig = false;
        this.kubernetesConfigError = false;
        this.standard2NodesInstallStatus = false;
        this.standard2NodesSetup = false;
        this.nodesConfigError = false;
        serviceSettingsError = false;
        setupConfigNotCompletedError = false;
        setupCompleted = false;
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
        standard2NodesInstallStatus = true;
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

    @Override
    public void saveServicesSettings(ServicesSettingsWrapper settings) throws FileException, SetupException {
        this.serviceSettings = settings;
    }

    @Override
    public ServicesSettingsWrapper loadServicesSettings() throws FileException, SetupException {
        if (serviceSettingsError) {
            throw new SetupException("Test Error");
        }
        return serviceSettings;
    }

    @Override
    public ServicesSettingsWrapper loadServicesConfigNoLock() throws FileException, SetupException {
        return null;
    }

    @Override
    public void saveServicesInstallationStatus(ServicesInstallStatusWrapper status) throws FileException, SetupException {

    }

    @Override
    public void updateAndSaveServicesInstallationStatus(SystemService.StatusUpdater statusUpdater) throws FileException, SetupException {

    }

    @Override
    public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
        if (standard2NodesInstallStatus) {
            return StandardSetupHelpers.getStandard2NodesInstallStatus();
        }
        return null;
    }

    @Override
    public void saveNodesConfig(NodesConfigWrapper nodesConfig) throws FileException, SetupException {

    }

    @Override
    public NodesConfigWrapper loadNodesConfig() throws SystemException, SetupException {
        if (standard2NodesSetup) {
            return StandardSetupHelpers.getStandard2NodesSetup();
        } else if (nodesConfigError) {
            throw new SystemException("Test Error");
        }
        return null;
    }

    @Override
    public JsonWrapper createSetupConfigAndSaveStoragePath(String configAsString) throws SetupException, FileException {
        return null;
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
        } else {
            return KubernetesServicesConfigWrapper.empty();
        }
    }

    @Override
    public void saveKubernetesServicesConfig(KubernetesServicesConfigWrapper kubeServicesConfig) throws FileException, SetupException {

    }

}

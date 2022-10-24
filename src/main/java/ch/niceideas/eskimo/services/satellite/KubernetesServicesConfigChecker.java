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

package ch.niceideas.eskimo.services.satellite;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.Dependency;
import ch.niceideas.eskimo.model.service.KubeRequest;
import ch.niceideas.eskimo.model.service.MasterElectionStrategy;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.services.*;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class KubernetesServicesConfigChecker {

    private static final Logger logger = Logger.getLogger(KubernetesServicesConfigChecker.class);

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ConfigurationService configurationService;

    void setServicesDefinition (ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public void checkKubernetesServicesSetup(KubernetesServicesConfigWrapper kubeServicesConfig) throws KubernetesServicesConfigException {

        try {
            NodesConfigWrapper nodesConfig = configurationService.loadNodesConfig();
            if (nodesConfig == null) {
                throw new KubernetesServicesConfigException("Inconsistency found : No node configuration is found");
            }

            // ensure only kubernetes services
            for (String serviceName : kubeServicesConfig.getEnabledServices()) {
                Service service = servicesDefinition.getService(serviceName);
                if (!service.isKubernetes()) {
                    throw new KubernetesServicesConfigException("Inconsistency found : service " + serviceName + " is not a kubernetes service");
                }
            }

            // enforce dependencies
            for (String serviceName : kubeServicesConfig.getEnabledServices()) {

                Service service = servicesDefinition.getService(serviceName);

                for (Dependency dependency : service.getDependencies()) {

                    // All the following are unsupported for kubernetes service
                    if (dependency.getMes().equals(MasterElectionStrategy.SAME_NODE)
                            || dependency.getMes().equals(MasterElectionStrategy.SAME_NODE_OR_RANDOM)
                            || dependency.getMes().equals(MasterElectionStrategy.RANDOM_NODE_AFTER)
                            || dependency.getMes().equals(MasterElectionStrategy.RANDOM_NODE_AFTER_OR_SAME)) {

                        throw new KubernetesServicesConfigException(
                                "Inconsistency found : Service " + serviceName + " is a kubernetes service and defines a dependency " + dependency.getMes()
                                        + " on " + dependency.getMasterService() + " which is disallowed");
                    }

                    // I want the dependency somewhere
                    else if (dependency.isMandatory(nodesConfig)) {

                        Service depService = servicesDefinition.getService(dependency.getMasterService());
                        if (depService.isKubernetes()) {

                            if (dependency.getNumberOfMasters() > 1) {
                                throw new KubernetesServicesConfigException("Inconsistency found : Service " + serviceName + " is a kubernetes service and defines a dependency with master count "
                                        +  dependency.getNumberOfMasters() + " on " + dependency.getMasterService() + " which is disallowed for kubernetes dependencies");
                            }

                            // make sure dependency is installed or going to be
                            if (!kubeServicesConfig.isServiceInstallRequired(dependency.getMasterService())) {
                                throw new KubernetesServicesConfigException("Inconsistency found : Service " + serviceName + " expects a installaton of  " + dependency.getMasterService() +
                                        ". But it's not going to be installed");
                            }
                        }

                        else {
                            // ensure count of dependencies are available

                            try {
                                NodesConfigurationChecker.enforceMandatoryDependency(nodesConfig, serviceName, null, dependency);
                            } catch (NodesConfigurationException e) {
                                logger.debug(e, e);
                                logger.warn(e.getMessage());
                                throw new KubernetesServicesConfigException(e.getMessage(), e);
                            }
                        }
                    }
                }
            }


            for (String serviceName : servicesDefinition.listKubernetesServices()) {

                boolean enabled = kubeServicesConfig.isServiceInstallRequired(serviceName);

                String cpuSetting = kubeServicesConfig.getCpuSetting(serviceName);
                if (StringUtils.isBlank(cpuSetting) && enabled) {
                    throw new KubernetesServicesConfigException(
                            "Inconsistency found : Kubernetes Service " + serviceName + " is enabled but misses CPU request setting");
                }
                if (StringUtils.isNotBlank(cpuSetting) && !enabled) {
                    throw new KubernetesServicesConfigException(
                            "Inconsistency found : Kubernetes Service " + serviceName + " is NOT enabled but defines a CPU request setting");
                }

                String ramSetting = kubeServicesConfig.getRamSetting(serviceName);
                if (StringUtils.isBlank(ramSetting) && enabled) {
                    throw new KubernetesServicesConfigException(
                            "Inconsistency found : Kubernetes Service " + serviceName + " is enabled but misses RAM request setting");
                }
                if (StringUtils.isNotBlank(ramSetting) && !enabled) {
                    throw new KubernetesServicesConfigException(
                            "Inconsistency found : Kubernetes Service " + serviceName + " is NOT enabled but defines a RAM request setting");
                }

                if (StringUtils.isNotBlank(cpuSetting) && !KubeRequest.KUBE_REQUEST_CPU_RE.matcher(cpuSetting).matches()) {
                    throw new KubernetesServicesConfigException("CPU definition for " + serviceName + " doesn't match expected REGEX - [0-9\\\\.]+[m]{0,1}");
                }

                if (StringUtils.isNotBlank(ramSetting) && !KubeRequest.KUBE_REQUEST_RAM_RE.matcher(ramSetting).matches()) {
                    throw new KubernetesServicesConfigException("RAM definition for " + serviceName + " doesn't match expected REGEX - [0-9\\.]+[EPTGMk]{0,1}");
                }

            }

        } catch (SystemException | SetupException e) {
            logger.error (e, e);
            throw new KubernetesServicesConfigException(e);
        }
    }

}


/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.services;

import ch.niceideas.eskimo.model.*;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class MarathonServicesConfigChecker {

    private static final Logger logger = Logger.getLogger(MarathonServicesConfigChecker.class);

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

    public void checkMarathonServicesSetup(MarathonServicesConfigWrapper marathonConfig) throws MarathonServicesConfigException {

        try {
            NodesConfigWrapper nodesConfig = configurationService.loadNodesConfig();
            if (nodesConfig == null) {
                throw new MarathonServicesConfigException("Inconsistency found : No node configuration is found");
            }

            // ensure only marathon services
            for (String serviceName : marathonConfig.getEnabledServices()) {
                Service service = servicesDefinition.getService(serviceName);
                if (!service.isMarathon()) {
                    throw new MarathonServicesConfigException("Inconsistency found : service " + serviceName + " is not a marathon service");
                }
            }

            // enforce dependencies
            for (String serviceName : marathonConfig.getEnabledServices()) {

                Service service = servicesDefinition.getService(serviceName);

                for (Dependency dependency : service.getDependencies()) {

                    Service otherService = servicesDefinition.getService(dependency.getMasterService());
                    if (otherService.isMarathon()) {
                        throw new MarathonServicesConfigException(
                                "Inconsistency found : Service " + serviceName + " is defining a dependency on a marathon service :  " +
                                        dependency.getMasterService() + ", which is disallowed");
                    }

                    // I want the dependency on same node if dependency is mandatory
                    if (dependency.getMes().equals(MasterElectionStrategy.SAME_NODE)) {

                        throw new MarathonServicesConfigException(
                                "Inconsistency found : Service " + serviceName + " is a marathon service and defines a dependency SAME_NODE which is disallowed");
                    }

                    // I want the dependency somewhere
                    else if (dependency.isMandatory(nodesConfig)) {

                        // ensure count of dependencies are available
                        try {
                            NodesConfigurationChecker.enforceMandatoryDependency(nodesConfig, serviceName, null, dependency);
                        } catch (NodesConfigurationException e) {
                            logger.debug (e, e);
                            logger.warn (e.getMessage());
                            throw new MarathonServicesConfigException(e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (SystemException | SetupException e) {
            logger.error (e, e);
            throw new MarathonServicesConfigException(e);
        }
    }

}


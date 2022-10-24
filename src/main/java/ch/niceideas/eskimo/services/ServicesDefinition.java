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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.*;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;

import java.util.*;


public interface ServicesDefinition {

    String SERVICE_PREFIX = "Service ";

    void executeInEnvironmentLock (EnvironmentOperation operation)
            throws FileException, ServiceDefinitionException, SetupException;

    String getAllServicesString();

    Topology getTopology(NodesConfigWrapper nodesConfig, KubernetesServicesConfigWrapper kubeServicesConfig, String currentNode)
            throws ServiceDefinitionException, NodesConfigurationException;

    Service getService(String serviceName);

    String[] listAllServices();

    String[] listAllNodesServices();

    String[] listMultipleServicesNonKubernetes();

    String[] listMultipleServices();

    String[] listMandatoryServices();

    String[] listUniqueServices();

    String[] listKubernetesServices();

    String[] listProxiedServices();

    String[] listUIServices();

    Map<String, UIConfig> getUIServicesConfig();

    String[] listServicesInOrder();

    String[] listServicesOrderedByDependencies();

    String[] listKubernetesServicesOrderedByDependencies();

    int compareServices(Service one, Service other);

    int compareServices(String servOne, String servOther);

    Collection<String> getDependentServices(String service);


    public interface EnvironmentOperation {
        void call(JsonWrapper persistentEnvironment) throws ServiceDefinitionException;
    }
}

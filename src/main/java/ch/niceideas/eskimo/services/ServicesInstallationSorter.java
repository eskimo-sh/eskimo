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

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ServicesInstallationSorter {

    @Autowired
    private ServicesDefinition servicesDefinition;

    /** For tests only */
    public void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }


    public <T extends OperationId> List<List<T>> orderOperations  (
            List<T> operations,
            NodesConfigWrapper nodesConfig) throws NodesConfigurationException, ServiceDefinitionException {

        if (operations == null || operations.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. group services togethers
        Map<String, List<T>> groupedOperations = new HashMap<>();
        for (T operation : operations) {

            String service = operation.getService();

            List<T> operationForService = groupedOperations.computeIfAbsent(service, k -> new ArrayList<>());

            operationForService.add(operation);
        }

        // 2. Order by depencencies
        List<Service> services = groupedOperations.keySet().stream()
                .sorted((one, other) -> servicesDefinition.compareServices(one, other))
                .map(service -> servicesDefinition.getService(service))
                .collect(Collectors.toList());


        // 3. Reprocess and separate master installation
        List<List<T>> orderedOperationsSteps = new ArrayList<>();

        Topology topology = servicesDefinition.getTopology(nodesConfig, null, null);

        for (Service service : services) {

            List<T> group = groupedOperations.get(service.getName());

            // If service has it's own in dependency, this is where we have an issue
            // then it depends on the type of dependency:
            // if master is random, don't bother
            // if master is fixed, install it first

            Optional<Dependency> dependency = service.getDependency(service);

            if (dependency.isPresent()
                    && (dependency.get().getMes().equals(MasterElectionStrategy.FIRST_NODE)
                    || dependency.get().getMes().equals(MasterElectionStrategy.RANDOM))) {

                String[] masters = topology.getMasters(service);

                for (String master : masters) {

                    List<T> newGroup = new ArrayList<>();
                    for (T operation : group) {

                        if (operation.isOnNode(master)) {

                            // create ad'hoc single group for master
                            List<T> singleGroup = new ArrayList<>();
                            singleGroup.add(operation);
                            orderedOperationsSteps.add(singleGroup);

                        } else {

                            newGroup.add(operation);
                        }
                    }
                    group = newGroup;
                }

                if (!group.isEmpty()) { // there may well be none left
                    orderedOperationsSteps.add(group);
                }

            } else {

                orderedOperationsSteps.add(group);
            }
        }

        // 4. loop back through groups and merge down those that have no dependencies with the former one
        //    but don't allow multiple installations on same node in //
        for (int i = orderedOperationsSteps.size() - 1 ; i >= 0 ; i--) {

            List<T> current = orderedOperationsSteps.get(i);
            if (i > 0) {
                List<T> prev = orderedOperationsSteps.get(i - 1);

                // Does any service in curent have any dependency on any servic in prev ?
                Set<Service> currentServices = new HashSet<>();
                for (OperationId installation : current) {
                    currentServices.add(servicesDefinition.getService(installation.getService()));
                }

                Set<Service> prevServices = new HashSet<>();
                for (OperationId installation : prev) {
                    prevServices.add(servicesDefinition.getService(installation.getService()));
                }

                boolean hasDependency = false;
                for (Service currentService : currentServices) {
                    for (Service prevService : prevServices) {
                        if (currentService.hasDependency(prevService)) {
                            hasDependency = true;
                            break;
                        }
                    }
                }

                // Does any installation happen on same node ?
                boolean hasSameNode = false;
                for (OperationId installCurrent : current) {
                    for (OperationId installPrev : prev) {
                        if (installCurrent.isSameNode(installPrev)) {
                            hasSameNode = true;
                            break;
                        }
                    }
                }


                if (!hasDependency && !hasSameNode) {
                    prev.addAll(current);
                    orderedOperationsSteps.remove(i);
                }
            }
        }

        return orderedOperationsSteps;
    }
}

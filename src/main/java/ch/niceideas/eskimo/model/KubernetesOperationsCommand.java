/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.SystemService;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Operation;
import ch.niceideas.eskimo.types.Service;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static ch.niceideas.eskimo.model.SimpleOperationCommand.standardizeOperationMember;

public class KubernetesOperationsCommand
        extends AbstractServiceOperationsCommand<KubernetesOperationsCommand.KubernetesOperationId, KubernetesServicesConfigWrapper>
        implements Serializable {

    @Getter @Setter
    private String warnings = null;

    public static KubernetesOperationsCommand create (
            ServicesDefinition servicesDefinition,
            SystemService systemService,
            KubernetesServicesConfigWrapper previousConfig,
            ServicesInstallStatusWrapper servicesInstallStatus,
            KubernetesServicesConfigWrapper rawKubeServicesConfig) {

        KubernetesOperationsCommand retCommand = new KubernetesOperationsCommand(rawKubeServicesConfig);

        // 1. Find out about services that need to be installed
        for (Service service : servicesDefinition.listKubernetesServicesOrderedByDependencies()) {
            if (rawKubeServicesConfig.isServiceInstallRequired(service)
                    && !servicesInstallStatus.isServiceInstalledAnywhere(service)) {

                retCommand.addInstallation(new KubernetesOperationId(KuberneteOperation.INSTALLATION, service));
            }
        }

        // 2. Find out about services that need to be uninstalled
        for (String installStatusFlag : servicesInstallStatus.getRootKeys()) {

            Pair<Service, Node> serviceAndNodePair = Objects.requireNonNull(ServicesInstallStatusWrapper.parseInstallStatusFlag (installStatusFlag));
            Service installedService = serviceAndNodePair.getKey();
            Node node = serviceAndNodePair.getValue();

            if (node.equals(Node.KUBERNETES_NODE)
                    // search it in config
                    && !rawKubeServicesConfig.isServiceInstallRequired(installedService)) {

                retCommand.addUninstallation(new KubernetesOperationId(KuberneteOperation.UNINSTALLATION, installedService));
            }
        }

        // 3. Find out about service that need to be restarted
        if (previousConfig != null) {
            for (Service service : servicesDefinition.listKubernetesServicesOrderedByDependencies()) {
                if (rawKubeServicesConfig.isServiceInstallRequired(service)
                        && !retCommand.getInstallations().contains(new KubernetesOperationId(KuberneteOperation.INSTALLATION, service))
                        && rawKubeServicesConfig.isDifferentConfig(previousConfig, service)) {
                    retCommand.addRestart(new KubernetesOperationId(KuberneteOperation.RESTART, service));
                }
            }
        }

        // 3. If Kubernetes is not available, issue a warning in regards to what is going to happen
        if (retCommand.hasChanges()) {
            try {
                SystemStatusWrapper lastStatus = systemService.getStatus();

                Node kubeNode = lastStatus.getFirstNode(servicesDefinition.getKubeMasterServiceDef());
                if (kubeNode == null) {
                    retCommand.setWarnings("Kubernetes is not available. The changes in kubernetes services configuration and " +
                            "deployments will be saved but they will <strong>need to be applied again</strong> another time when " +
                            "Kubernetes Master is available");

                } else {

                    if (!lastStatus.isServiceOKOnNode(servicesDefinition.getKubeMasterServiceDef(), kubeNode)) {

                        retCommand.setWarnings("Kubernetes is not properly running. The changes in kubernetes services configuration and " +
                                "deployments will be saved but they will <strong>need to be applied again</strong> another time when " +
                                "Kubernetes Master is available");
                    }
                }

            } catch (SystemService.StatusExceptionWrapperException e) {

                String warnings = "Couldn't get last Kubernetes Service status to assess feasibility of kubernetes setup\n";
                warnings += e.getCause().getCause() + ":" + e.getCause().getMessage();
                retCommand.setWarnings(warnings);
            }
        }

        return retCommand;
    }

    KubernetesOperationsCommand(KubernetesServicesConfigWrapper rawKubeServiceConfig) {
        super (rawKubeServiceConfig);
    }

    @Override
    public JSONObject toJSON () {
        return new JSONObject(new HashMap<String, Object>() {{
            put("installations", new JSONArray(getInstallations().stream().map(KubernetesOperationId::getService).map(Service::getName).collect(Collectors.toList())));
            put("uninstallations", new JSONArray(getUninstallations().stream().map(KubernetesOperationId::getService).map(Service::getName).collect(Collectors.toList())));
            put("restarts", new JSONArray(getRestarts().stream().map(KubernetesOperationId::getService).map(Service::getName).collect(Collectors.toList())));
            put("warnings", warnings);
        }});
    }

    @Override
    public List<KubernetesOperationId> getNodesCheckOperation() {
        return List.of(new KubernetesOperationId(KuberneteOperation.INSTALLATION, Service.TOPOLOGY_ALL_NODES));
    }

    @Getter
    @EqualsAndHashCode
    @RequiredArgsConstructor
    public static class KubernetesOperationId implements OperationId<KuberneteOperation> {

        private final KuberneteOperation operation;
        private final Service service;

        @Override
        public boolean isOnNode(Node node) {
            return node.equals(Node.KUBERNETES_NODE);
        }

        @Override
        public boolean isSameNode(OperationId<? extends Operation> other) {
            return other.isOnNode(Node.KUBERNETES_NODE);
        }

        @Override
        public String getMessage() {
            return operation + " of " + getService() + " on kubernetes";
        }

        @Override
        public String toString() {
            return standardizeOperationMember (operation.getType())
                    + "_"
                    + standardizeOperationMember (service.getName());
        }

        @Override
        public int compareTo(OperationId<?> o) {
            return toString().compareTo(o.toString());
        }
    }

    @RequiredArgsConstructor
    public enum KuberneteOperation implements Operation {
        INSTALLATION(NodeServiceOperationsCommand.ServiceOperation.INSTALLATION.getType(), 21),
        UNINSTALLATION (NodeServiceOperationsCommand.ServiceOperation.UNINSTALLATION.getType(), 22),
        RESTART (NodeServiceOperationsCommand.ServiceOperation.RESTART.getType(), 23);

        @Getter
        private final String type;

        @Getter
        private final int ordinal;

        @Override
        public String toString () {
            return type;
        }
    }
}

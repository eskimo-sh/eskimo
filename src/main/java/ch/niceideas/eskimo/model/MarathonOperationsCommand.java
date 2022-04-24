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

package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.services.*;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static ch.niceideas.eskimo.model.SimpleOperationCommand.standardizeOperationMember;

@Deprecated /* To be renamed */
public class MarathonOperationsCommand extends JSONInstallOpCommand<MarathonOperationsCommand.MarathonOperationId> implements Serializable {

    private final MarathonServicesConfigWrapper rawMarathonServicesConfig;

    @Getter @Setter
    private String warnings = null;

    // Note : marathon dependent services not supported for now

    public static MarathonOperationsCommand create (
            ServicesDefinition servicesDefinition,
            SystemService systemService,
            ServicesInstallStatusWrapper servicesInstallStatus,
            MarathonServicesConfigWrapper rawMarathonServicesConfig) {

        MarathonOperationsCommand retCommand = new MarathonOperationsCommand(rawMarathonServicesConfig);

        // 1. Find out about services that need to be installed
        for (String service : servicesDefinition.listKubernetesServicesOrderedByDependencies()) {
            if (rawMarathonServicesConfig.isServiceInstallRequired(service)
                    && !servicesInstallStatus.isServiceInstalledAnywhere(service)) {

                retCommand.addInstallation(new MarathonOperationId ("installation", service));
            }
        }

        // 2. Find out about services that need to be uninstalled
        for (String installStatusFlag : servicesInstallStatus.getRootKeys()) {

            Pair<String, String> serviceAndNodePair = ServicesInstallStatusWrapper.parseInstallStatusFlag (installStatusFlag);
            String installedService = serviceAndNodePair.getKey();
            String nodeName = serviceAndNodePair.getValue();

            /* Deprecated */
            if (nodeName.equals(ServicesInstallStatusWrapper.MARATHON_NODE)
                // search it in config
                && !rawMarathonServicesConfig.isServiceInstallRequired(installedService)) {

                retCommand.addUninstallation(new MarathonOperationId ("uninstallation", installedService));
            }
            if (nodeName.equals(ServicesInstallStatusWrapper.KUBERNETES_NODE)
                    // search it in config
                    && !rawMarathonServicesConfig.isServiceInstallRequired(installedService)) {

                retCommand.addUninstallation(new MarathonOperationId ("uninstallation", installedService));
            }
        }

        // 3. If Marathon is not available, issue a warning in regards to what is going to happen
        if (retCommand.hasChanges()) {
            try {
                SystemStatusWrapper lastStatus = systemService.getStatus();

                String kubeNodeName = lastStatus.getFirstNodeName(KubernetesService.KUBE_MASTER);
                if (StringUtils.isBlank(kubeNodeName)) {
                    retCommand.setWarnings("Kubernetes is not available. The changes in kubernetes services configuration and " +
                            "deployments will be saved but they will <strong>need to be applied again</strong> another time when " +
                            "Kubernetes Master is available");

                } else {

                    if (!lastStatus.isServiceOKOnNode(KubernetesService.KUBE_MASTER, kubeNodeName)) {

                        retCommand.setWarnings("Kubernetes is not properly running. The changes in kubernetes services configuration and " +
                                "deployments will be saved but they will <strong>need to be applied again</strong> another time when " +
                                "Kubernetes Master is available");
                    }
                }

            } catch (SystemService.StatusExceptionWrapperException e) {

                String warnings = "Couldn't get last marathon Service status to assess feasibility of marathon setup\n";
                warnings += e.getCause().getCause() + ":" + e.getCause().getMessage();
                retCommand.setWarnings(warnings);
            }
        }

        return retCommand;
    }

    MarathonOperationsCommand(MarathonServicesConfigWrapper rawMarathonServicesConfig) {
        this.rawMarathonServicesConfig = rawMarathonServicesConfig;
    }

    public MarathonServicesConfigWrapper getRawConfig() {
        return rawMarathonServicesConfig;
    }

    public JSONObject toJSON () {
        return new JSONObject(new HashMap<String, Object>() {{
            put("installations", new JSONArray(getInstallations().stream().map(MarathonOperationId::getService).collect(Collectors.toList())));
            put("uninstallations", new JSONArray(getUninstallations().stream().map(MarathonOperationId::getService).collect(Collectors.toList())));
            put("warnings", warnings);
        }});
    }

    public List<MarathonOperationId> getAllOperationsInOrder (OperationsContext context) {

        List<MarathonOperationId> allOpList = new ArrayList<>();

        allOpList.add(new MarathonOperationId("Installation", MarathonService.TOPOLOGY_ALL_NODES));

        allOpList.addAll(getInstallations());
        allOpList.addAll(getUninstallations());

        return allOpList;
    }

    @Data
    @RequiredArgsConstructor
    @Deprecated /* To Be renamed */
    public static class MarathonOperationId implements OperationId {

        private final String type;
        private final String service;

        public boolean isOnNode(String node) {
            return  /* Deprecated */
                    node.equals(ServicesInstallStatusWrapper.MARATHON_NODE)
                    ||
                    node.equals(ServicesInstallStatusWrapper.KUBERNETES_NODE);
        }

        public boolean isSameNode(OperationId other) {
            return  /* Deprecated */
                    other.isOnNode(ServicesInstallStatusWrapper.MARATHON_NODE)
                    ||
                    other.isOnNode(ServicesInstallStatusWrapper.KUBERNETES_NODE);
        }

        public String getMessage() {
            return type + " of " + getService() + " on kubernetes";
        }

        @Override
        public String toString() {
            return standardizeOperationMember (type)
                    + "_"
                    + standardizeOperationMember (service);
        }
    }
}

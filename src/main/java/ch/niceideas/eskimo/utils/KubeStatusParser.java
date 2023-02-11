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


package ch.niceideas.eskimo.utils;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.service.ServiceDefinition;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.regex.Pattern;

public class KubeStatusParser {

    static final Pattern POD_NAME_REXP = Pattern.compile("[a-zA-Z]+(-[a-zA-Z]+)?(\\-[a-zA-Z0-9]+){1,2}");

    @RequiredArgsConstructor
    public enum KubernetesServiceStatus {
        RUNNING("Running"),
        __STATUS_TERMINATING("Terminating"),
        __STATUS_CONTAINER_CREATING("ContainerCreating"),
        __STATUS_CRASH_LOOP_BACK_OFF("CrashLoopBackOff"),
        __STATUS_ERROR("Error"),
        __STATUS_IMAGE_PULL_BACK_OFF("ImagePullBackOff"),
        NOT_OK("notOK"),
        NA("NA");
        @Getter
        private final String code;
        public static KubernetesServiceStatus fromCode(String code) {
            Objects.requireNonNull(code);
            for (KubernetesServiceStatus status : KubernetesServiceStatus.values()) {
                if (status.getCode().equals(code)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown code : " + code);
        }
    }

    private final String allPodStatus;
    private final String allServicesStatus;
    private final String allRegistryServices;
    private final ServicesDefinition servicesDefinition;

    private final Map<String, Map<String, String>> podStatuses = new HashMap<>();
    private final Map<String, Map<String, String>> serviceStatuses = new HashMap<>();
    private final List<String> registryServices = new ArrayList<>();


    public KubeStatusParser(String allPodStatus, String allServicesStatus, String allRegistryServices, ServicesDefinition servicesDefinition) {
        this.allPodStatus = allPodStatus;
        this.allServicesStatus = allServicesStatus;
        this.allRegistryServices = allRegistryServices;
        this.servicesDefinition = servicesDefinition;
        parse();
    }

    private void parse() {
        List<String> podHeader = null;
        for (String podStatusLine: allPodStatus.split("\n")) {
            String[] fields = podStatusLine.split("[ \t]{2,}");
            if (podHeader == null) {
                podHeader = new LinkedList<>(Arrays.asList(fields));
            } else {
                HashMap<String, String> podMap = new HashMap<>();
                for (int i = 0; i < fields.length; i++) {
                    podMap.put (podHeader.get(i), fields[i]);
                }
                String podName = podMap.get("NAME");
                if (POD_NAME_REXP.matcher(podName).matches()) {
                    podStatuses.put(podName, podMap);
                }
            }
        }

        List<String> serviceHeader = null;
        for (String serviceStatusLine : allServicesStatus.split("\n")) {
            String[] fields = serviceStatusLine.split("[ \t]{2,}");
            if (serviceHeader == null) {
                serviceHeader = new LinkedList<>(Arrays.asList(fields));
            } else {
                HashMap<String, String> serviceMap = new HashMap<>();
                for (int i= 0; i < fields.length; i++) {
                    serviceMap.put (serviceHeader.get(i), fields[i]);
                }
                serviceStatuses.put (serviceMap.get("NAME"), serviceMap);
            }
        }

        Arrays.stream(allRegistryServices.split("\n"))
                .filter (StringUtils::isNotBlank)
                .forEach(registryServices::add);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append ("POD STATUSES\n");
        appendStatuses(podStatuses, sb);
        sb.append ("SERVICE STATUSES\n");
        appendStatuses(serviceStatuses, sb);
        sb.append ("REGISTRY SERVICES\n");
        registryServices.forEach(service -> sb.append(service).append("\n"));
        return sb.toString();
    }

    private void appendStatuses(Map<String, Map<String, String>> statuses, StringBuilder sb) {
        for (Map.Entry<String, Map<String, String>> podAndStatus : statuses.entrySet()) {
            sb.append(podAndStatus.getKey()).append(" : ");
            podAndStatus.getValue().keySet().forEach(
                    key -> sb.append(key).append("=").append(podAndStatus.getValue().get(key)).append(", "));
            sb.append ("\n");
        }
    }

    private Node getServiceNode(String service) {
        Map<String, String> serviceFields = serviceStatuses.get(service);
        if (serviceFields == null) {
            return null;
        }
        String serviceIp =  serviceFields.get ("CLUSTER-IP");
        if (serviceIp.equalsIgnoreCase("None")) {
            return null;
        }
        return Node.fromAddress(serviceIp);
    }

    List<Pair<Node, KubernetesServiceStatus>> getPodNodesAndStatus(Service service) {
        List<Pair<Node, KubernetesServiceStatus>> retList = new ArrayList<>();

        List<String> podList = new ArrayList<>(podStatuses.keySet());
        podList.sort(Comparator.naturalOrder()); // need reproduceable results
        for (String podName : podList) {
            if (podName.startsWith(service.getName()) && !podNameMatchOtherService (service, podName)) {

                Map<String, String> podFields = podStatuses.get(podName);
                if (podFields != null) {
                    KubernetesServiceStatus status = KubernetesServiceStatus.fromCode(podFields.get("STATUS"));
                    Node node = Node.fromAddress(podFields.get("NODE"));
                    retList.add(new Pair<>(node, status));
                }
            }
        }

        return retList;
    }

    private boolean podNameMatchOtherService(Service curService, String podName) {
        return Arrays.stream(servicesDefinition.listKubernetesServices())
                .filter (service -> !service.equals(curService))
                .filter(service -> service.getName().length() > curService.getName().length())
                .map (servicesDefinition::getServiceDefinition)
                .filter (service -> podName.startsWith(service.getName()))
                .findAny().orElse(null) != null;
    }

    public Pair<Node, KubernetesServiceStatus> getServiceRuntimeNode(ServiceDefinition serviceDef, Node kubeNode) {

        List<Pair<Node, KubernetesServiceStatus>> podNodesAndStatus = getPodNodesAndStatus(serviceDef.toService());
        Node serviceNode = getServiceNode(serviceDef.getName());
        boolean serviceFound = serviceStatuses.get(serviceDef.getName()) != null;

        // 0. registryOnlyservices are a specific case
        if (serviceDef.isRegistryOnly()) {
            if (registryServices.stream().anyMatch(registrySrv -> registrySrv.equalsIgnoreCase(serviceDef.getName()))) {
                return new Pair<>(kubeNode, KubernetesServiceStatus.RUNNING);
            } else {
                return new Pair<>(null, KubernetesServiceStatus.NA);
            }
        }

        // 1. if at east one POD is running and service is OK, return running on kubeIp
        if (serviceFound
                && (serviceNode != null || !serviceDef.isUnique())
                && !podNodesAndStatus.isEmpty()
                && podNodesAndStatus.stream()
                    .map(Pair::getValue)
                    .anyMatch(status -> status.equals(KubernetesServiceStatus.RUNNING)
                            || status.equals(KubernetesServiceStatus.__STATUS_TERMINATING)
                            || status.equals(KubernetesServiceStatus.__STATUS_CONTAINER_CREATING))) {
            return new Pair<>(kubeNode, KubernetesServiceStatus.RUNNING);
        }

        // 2. If neither any POD nor the service cannot be found, return new Pair<>(null, "NA");
        if (!serviceFound && podNodesAndStatus.isEmpty()) {
            return new Pair<>(null, KubernetesServiceStatus.NA);
        }

        // 3. If no POD at all is running return notOK on kubeIp
        if (podNodesAndStatus.stream()
                .map(Pair::getValue)
                .noneMatch(status -> status.equals(KubernetesServiceStatus.RUNNING)
                        || status.equals(KubernetesServiceStatus.__STATUS_TERMINATING)
                        || status.equals(KubernetesServiceStatus.__STATUS_CONTAINER_CREATING))) {
            return new Pair<>(kubeNode, KubernetesServiceStatus.NOT_OK);
        }

        return new Pair<>(null, KubernetesServiceStatus.NOT_OK);
    }

    public List<Pair<Node, KubernetesServiceStatus>> getServiceRuntimeNodes(Service service) {
        return getPodNodesAndStatus(service);
    }
}

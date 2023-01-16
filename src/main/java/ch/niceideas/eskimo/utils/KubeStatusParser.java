package ch.niceideas.eskimo.utils;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.services.KubernetesService;
import ch.niceideas.eskimo.services.ServicesDefinition;

import java.util.*;
import java.util.regex.Pattern;

public class KubeStatusParser {

    static final Pattern POD_NAME_REXP = Pattern.compile("[a-zA-Z]+(-[a-zA-Z]+){0,1}(\\-[a-zA-Z0-9]+){1,2}");

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

    private String getServiceIp(String service) {
        Map<String, String> serviceFields = serviceStatuses.get(service);
        if (serviceFields == null) {
            return null;
        }
        String serviceIp =  serviceFields.get ("CLUSTER-IP");
        if (serviceIp.equalsIgnoreCase("None")) {
            return null;
        }
        return serviceIp;
    }

    List<Pair<String, String>> getPodNodesAndStatus(String service) {
        List<Pair<String, String>> retList = new ArrayList<>();

        List<String> podList = new ArrayList<>(podStatuses.keySet());
        podList.sort(Comparator.naturalOrder()); // need reproduceable results
        for (String podName : podList) {
            if (podName.startsWith(service) && !podNameMatchOtherService (service, podName)) {

                Map<String, String> podFields = podStatuses.get(podName);
                if (podFields != null) {
                    String status = podFields.get("STATUS");
                    String node = podFields.get("NODE");
                    retList.add(new Pair<>(node, status));
                }
            }
        }

        return retList;
    }

    private boolean podNameMatchOtherService(String curService, String podName) {
        return Arrays.stream(servicesDefinition.listKubernetesServices())
                .filter (serviceName -> !serviceName.equals(curService))
                .filter(serviceName -> serviceName.length() > curService.length())
                .map (servicesDefinition::getService)
                .filter (service -> podName.startsWith(service.getName()))
                .findAny().orElse(null) != null;
    }

    public Pair<String, String> getServiceRuntimeNode(Service service, String kubeIp) {

        List<Pair<String, String>> podNodesAndStatus = getPodNodesAndStatus(service.getName());
        String serviceIp = getServiceIp(service.getName());
        boolean serviceFound = serviceStatuses.get(service.getName()) != null;

        // 0. registryOnlyservices are a specific case
        if (service.isRegistryOnly()) {
            if (registryServices.stream().anyMatch(registrySrv -> registrySrv.equalsIgnoreCase(service.getName()))) {
                return new Pair<>(kubeIp, KubernetesService.STATUS_RUNNING);
            } else {
                return new Pair<>(null, "NA");
            }
        }

        // 1. if at east one POD is running and service is OK, return running on kubeIp
        if (serviceFound
                && (StringUtils.isNotBlank(serviceIp) || !service.isUnique())
                && !podNodesAndStatus.isEmpty()
                && podNodesAndStatus.stream()
                    .map(Pair::getValue)
                    .anyMatch(status -> status.equalsIgnoreCase(KubernetesService.STATUS_RUNNING)
                            || status.equalsIgnoreCase(KubernetesService.STATUS_TERMINATING)
                            || status.equalsIgnoreCase(KubernetesService.STATUS_CONTAINER_CREATING))) {
            return new Pair<>(kubeIp, KubernetesService.STATUS_RUNNING);
        }

        // 2. If neither any POD nor the service cannot be found, return new Pair<>(null, "NA");
        if (!serviceFound && podNodesAndStatus.isEmpty()) {
            return new Pair<>(null, "NA");
        }

        // 3. If no POD at all is running return notOK on kubeIp
        if (podNodesAndStatus.stream()
                .map(Pair::getValue)
                .noneMatch(status -> status.equalsIgnoreCase(KubernetesService.STATUS_RUNNING)
                        || status.equalsIgnoreCase(KubernetesService.STATUS_TERMINATING)
                        || status.equalsIgnoreCase(KubernetesService.STATUS_CONTAINER_CREATING))) {
            return new Pair<>(kubeIp, "notOK");
        }

        return new Pair<>(null, "notOK");
    }

    public List<Pair<String, String>> getServiceRuntimeNodes(String service) {
        return getPodNodesAndStatus(service);
    }
}

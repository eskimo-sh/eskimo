package ch.niceideas.eskimo.utils;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.services.KubernetesService;

import java.util.*;
import java.util.regex.Pattern;

public class KubeStatusParser {

    final static Pattern POD_NAME_REXP = Pattern.compile("[a-zA-Z\\-]+(\\-[a-zA-Z0-9]+){1,2}");

    final String allPodStatus;
    final String allServicesStatus;
    final String allRegistryServices;

    final Map<String, Map<String, String>> podStatuses = new HashMap<>();
    final Map<String, Map<String, String>> serviceStatuses = new HashMap<>();
    final List<String> registryServices = new ArrayList<>();


    public KubeStatusParser(String allPodStatus, String allServicesStatus, String allRegistryServices) {
        this.allPodStatus = allPodStatus;
        this.allServicesStatus = allServicesStatus;
        this.allRegistryServices = allRegistryServices;
        parse();
    }

    private void parse() {
        List<String> podHeader = null;
        for (String podStatusLine: allPodStatus.split("\n")) {
            String[] fields = podStatusLine.split("[ \t]{2,}");
            if (podHeader == null) {
                podHeader = new LinkedList<>();
                podHeader.addAll(Arrays.asList(fields));
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
        for (String podName : podStatuses.keySet()) {
            sb.append (podName + " : ");
            podStatuses.get(podName).keySet().forEach(key -> sb.append(key + "=" + podStatuses.get(podName).get(key) + ", "));
            sb.append ("\n");
        }
        sb.append ("SERVICE STATUSES\n");
        for (String serviceName : serviceStatuses.keySet()) {
            sb.append (serviceName + " : ");
            serviceStatuses.get(serviceName).keySet().forEach(key -> sb.append(key + "=" + serviceStatuses.get(serviceName).get(key) + ", "));
            sb.append ("\n");
        }
        sb.append ("REGISTRY SERVICES\n");
        registryServices.forEach(service -> sb.append(service + "\n"));
        return sb.toString();
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

    private List<Pair<String, String>> getPodNodesAndStatus(String service) {
        List<Pair<String, String>> retList = new ArrayList<>();

        List<String> podList = new ArrayList<>(podStatuses.keySet());
        podList.sort(Comparator.naturalOrder()); // need reproduceable results
        for (String podName : podList) {
            if (podName.startsWith(service)) {

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
                && podNodesAndStatus.size() > 0
                && podNodesAndStatus.stream()
                    .map(Pair::getValue)
                    .anyMatch(status -> status.equalsIgnoreCase(KubernetesService.STATUS_RUNNING)
                            || status.equalsIgnoreCase(KubernetesService.STATUS_TERMINATING)
                            || status.equalsIgnoreCase(KubernetesService.STATUS_CONTAINER_CREATING))) {
            return new Pair<>(kubeIp, KubernetesService.STATUS_RUNNING);
        }

        // 2. If neither any POD nor the service cannot be found, return new Pair<>(null, "NA");
        if (!serviceFound && podNodesAndStatus.size() <= 0) {
            return new Pair<>(null, "NA");
        }

        // 3. If no POD at all is running return notOK on kubeIp
        if (!podNodesAndStatus.stream()
                .map(Pair::getValue)
                .anyMatch(status -> status.equalsIgnoreCase(KubernetesService.STATUS_RUNNING)
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

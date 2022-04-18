package ch.niceideas.eskimo.utils;

import ch.niceideas.common.utils.Pair;

public class KubeStatusParser {

    final String allPodStatus;
    final String allServicesStatus;

    public KubeStatusParser(String allPodStatus, String allServicesStatus) {
        this.allPodStatus = allPodStatus;
        this.allServicesStatus = allServicesStatus;
    }

    public Pair<String, String> getServiceRuntimeNode(String service) {

        // FIXME TODO check both service and POD

        // FIXME TODO if POD is running and service is OK, return running on POD IP address

        // FIXME TODO If POD is running and service is not OK, return notOK on POD IP address

        // FIXME TODO If POD is starting and/or service has no IP address assigned, return notOK on Kube Master IP address

        // FIXME TODO If POD and/or service cannot be found, return new Pair<>(null, "NA");


        return new Pair<>(null, "notOK");

    }
}

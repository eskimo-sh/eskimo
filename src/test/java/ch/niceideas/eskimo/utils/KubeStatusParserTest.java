package ch.niceideas.eskimo.utils;

import org.junit.jupiter.api.Test;

public class KubeStatusParserTest {

    @Test
    public void testNominal() throws Exception {

        String allPodStatus =
                "NAMESPACE              NAME                                         READY   STATUS    RESTARTS      AGE   IP              NODE            NOMINATED NODE   READINESS GATES\n" +
                "default                cerebro-65d5556459-fjwh9                     1/1     Running   1 (47m ago)   54m   192.168.56.23   192.168.56.23   <none>           <none>\n" +
                "kube-system            coredns-5d8697db8f-gbbn7                     1/1     Running   0             54m   172.30.1.2      192.168.56.23   <none>           <none>\n" +
                "kubernetes-dashboard   dashboard-metrics-scraper-7b86d64486-z85tt   1/1     Running   0             13h   192.168.56.23   192.168.56.23   <none>           <none>\n" +
                "kubernetes-dashboard   kubernetes-dashboard-54dd7bccfc-wngd4        1/1     Running   0             54m   192.168.56.23   192.168.56.23   <none>           <none>\n";

        String allServicesStatus =
                "NAMESPACE              NAME                        TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)                  AGE   SELECTOR\n" +
                "default                cerebro                     ClusterIP   10.254.38.33     <none>        31900/TCP                13h   k8s-app=cerebro\n" +
                "default                kubernetes                  ClusterIP   10.254.0.1       <none>        443/TCP                  14h   <none>\n" +
                "kube-system            kube-dns                    ClusterIP   10.254.0.2       <none>        53/UDP,53/TCP,9153/TCP   14h   k8s-app=kube-dns\n" +
                "kubernetes-dashboard   dashboard-metrics-scraper   ClusterIP   10.254.112.116   <none>        8000/TCP                 13h   k8s-app=dashboard-metrics-scraper\n" +
                "kubernetes-dashboard   kubernetes-dashboard        ClusterIP   10.254.197.227   <none>        443/TCP                  13h   k8s-app=kubernetes-dashboard\n";

        KubeStatusParser parser = new KubeStatusParser(allPodStatus, allServicesStatus);

        // FIXME
    }

}

package ch.niceideas.eskimo.utils;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.model.Service;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.*;

public class KubeStatusParserTest {

    private String allPodStatus =
            "NAMESPACE              NAME                                         READY   STATUS    RESTARTS      AGE   IP              NODE            NOMINATED NODE   READINESS GATES\n" +
            "default                cerebro-65d5556459-fjwh9                     1/1     Error     1 (47m ago)   54m   192.168.56.23   192.168.56.23   <none>           <none>\n" +
            "kube-system            coredns-5d8697db8f-gbbn7                     1/1     Running   0             54m   172.30.1.2      192.168.56.23   <none>           <none>\n" +
            "kubernetes-dashboard   dashboard-metrics-scraper-7b86d64486-z85tt   1/1     Running   0             13h   192.168.56.23   192.168.56.23   <none>           <none>\n" +
            "kubernetes-dashboard   kubernetes-dashboard-54dd7bccfc-wngd4        1/1     Running   0             54m   192.168.56.23   192.168.56.23   <none>           <none>\n" +
            "default                elasticsearch-0                              1/1     Error     1 (11h ago)   12h   172.30.0.5      192.168.56.21   <none>           <none>\n" +
            "default                elasticsearch-1                              1/1     Running   0             12h   172.30.2.2      192.168.56.22   <none>           <none>\n" +
            "default                elasticsearch-2                              1/1     Running   0             11h   172.30.1.2      192.168.56.23   <none>           <none>\n";

    private String allServicesStatus =
            "NAMESPACE              NAME                        TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)                  AGE   SELECTOR\n" +
            "default                cerebro                     ClusterIP   10.254.38.33     <none>        31900/TCP                13h   k8s-app=cerebro\n" +
            "default                kubernetes                  ClusterIP   10.254.0.1       <none>        443/TCP                  14h   <none>\n" +
            "kube-system            kube-dns                    ClusterIP   10.254.0.2       <none>        53/UDP,53/TCP,9153/TCP   14h   k8s-app=kube-dns\n" +
            "default                elasticsearch               ClusterIP   None             <none>        9200/TCP,9300/TCP        12h   service=elasticsearch\n" +
            "kubernetes-dashboard   dashboard-metrics-scraper   ClusterIP   10.254.112.116   <none>        8000/TCP                 13h   k8s-app=dashboard-metrics-scraper\n" +
            "kubernetes-dashboard   kubernetes-dashboard        ClusterIP   10.254.197.227   <none>        443/TCP                  13h   k8s-app=kubernetes-dashboard\n";

    private String registryServices =
            "cerebro\n" +
            "coredns\n" +
            "elasticsearch\n" +
            "k8s.gcr.io\n" +
            "spark\n" +
            "spark-runtime";

    @Test
    public void testPodNameRexp() throws Exception {
        assertTrue(KubeStatusParser.POD_NAME_REXP.matcher("elasticsearch-0").matches());
        assertTrue (KubeStatusParser.POD_NAME_REXP.matcher("kafka-2").matches());
        assertTrue (KubeStatusParser.POD_NAME_REXP.matcher("kubernetes-dashboard-7db6bbdf55-vhgcc").matches());
        assertTrue (KubeStatusParser.POD_NAME_REXP.matcher("flink-5db698798f-nkj2p").matches());
        assertFalse (KubeStatusParser.POD_NAME_REXP.matcher("zeppelin-spark-e8ca018099847d6d-exec-20").matches());
    }

    @Test
    public void testNominal() throws Exception {

        KubeStatusParser parser = new KubeStatusParser(allPodStatus, allServicesStatus, registryServices);

        //System.err.println (parser.toString());

        assertEquals("POD STATUSES\n" +
                        "elasticsearch-2 : NAMESPACE=default, READY=1/1, STATUS=Running, READINESS GATES=<none>, NOMINATED NODE=<none>, NODE=192.168.56.23, IP=172.30.1.2, RESTARTS=0, NAME=elasticsearch-2, AGE=11h, \n" +
                        "kubernetes-dashboard-54dd7bccfc-wngd4 : NAMESPACE=kubernetes-dashboard, READY=1/1, STATUS=Running, READINESS GATES=<none>, NOMINATED NODE=<none>, NODE=192.168.56.23, IP=192.168.56.23, RESTARTS=0, NAME=kubernetes-dashboard-54dd7bccfc-wngd4, AGE=54m, \n" +
                        "elasticsearch-1 : NAMESPACE=default, READY=1/1, STATUS=Running, READINESS GATES=<none>, NOMINATED NODE=<none>, NODE=192.168.56.22, IP=172.30.2.2, RESTARTS=0, NAME=elasticsearch-1, AGE=12h, \n" +
                        "dashboard-metrics-scraper-7b86d64486-z85tt : NAMESPACE=kubernetes-dashboard, READY=1/1, STATUS=Running, READINESS GATES=<none>, NOMINATED NODE=<none>, NODE=192.168.56.23, IP=192.168.56.23, RESTARTS=0, NAME=dashboard-metrics-scraper-7b86d64486-z85tt, AGE=13h, \n" +
                        "elasticsearch-0 : NAMESPACE=default, READY=1/1, STATUS=Error, READINESS GATES=<none>, NOMINATED NODE=<none>, NODE=192.168.56.21, IP=172.30.0.5, RESTARTS=1 (11h ago), NAME=elasticsearch-0, AGE=12h, \n" +
                        "cerebro-65d5556459-fjwh9 : NAMESPACE=default, READY=1/1, STATUS=Error, READINESS GATES=<none>, NOMINATED NODE=<none>, NODE=192.168.56.23, IP=192.168.56.23, RESTARTS=1 (47m ago), NAME=cerebro-65d5556459-fjwh9, AGE=54m, \n" +
                        "coredns-5d8697db8f-gbbn7 : NAMESPACE=kube-system, READY=1/1, STATUS=Running, READINESS GATES=<none>, NOMINATED NODE=<none>, NODE=192.168.56.23, IP=172.30.1.2, RESTARTS=0, NAME=coredns-5d8697db8f-gbbn7, AGE=54m, \n" +
                        "SERVICE STATUSES\n" +
                        "kubernetes : NAMESPACE=default, EXTERNAL-IP=<none>, SELECTOR=<none>, CLUSTER-IP=10.254.0.1, PORT(S)=443/TCP, TYPE=ClusterIP, NAME=kubernetes, AGE=14h, \n" +
                        "cerebro : NAMESPACE=default, EXTERNAL-IP=<none>, SELECTOR=k8s-app=cerebro, CLUSTER-IP=10.254.38.33, PORT(S)=31900/TCP, TYPE=ClusterIP, NAME=cerebro, AGE=13h, \n" +
                        "elasticsearch : NAMESPACE=default, EXTERNAL-IP=<none>, SELECTOR=service=elasticsearch, CLUSTER-IP=None, PORT(S)=9200/TCP,9300/TCP, TYPE=ClusterIP, NAME=elasticsearch, AGE=12h, \n" +
                        "dashboard-metrics-scraper : NAMESPACE=kubernetes-dashboard, EXTERNAL-IP=<none>, SELECTOR=k8s-app=dashboard-metrics-scraper, CLUSTER-IP=10.254.112.116, PORT(S)=8000/TCP, TYPE=ClusterIP, NAME=dashboard-metrics-scraper, AGE=13h, \n" +
                        "kube-dns : NAMESPACE=kube-system, EXTERNAL-IP=<none>, SELECTOR=k8s-app=kube-dns, CLUSTER-IP=10.254.0.2, PORT(S)=53/UDP,53/TCP,9153/TCP, TYPE=ClusterIP, NAME=kube-dns, AGE=14h, \n" +
                        "kubernetes-dashboard : NAMESPACE=kubernetes-dashboard, EXTERNAL-IP=<none>, SELECTOR=k8s-app=kubernetes-dashboard, CLUSTER-IP=10.254.197.227, PORT(S)=443/TCP, TYPE=ClusterIP, NAME=kubernetes-dashboard, AGE=13h, \n" +
                        "REGISTRY SERVICES\n" +
                        "cerebro\n" +
                        "coredns\n" +
                        "elasticsearch\n" +
                        "k8s.gcr.io\n" +
                        "spark\n" +
                        "spark-runtime\n", parser.toString());
    }

    @Test
    public void testGetServiceRuntimeNode() throws Exception {

        KubeStatusParser parser = new KubeStatusParser(allPodStatus, allServicesStatus, registryServices);

        Service coreDnsSrv = new Service();
        coreDnsSrv.setName("coredns");
        coreDnsSrv.setUnique(true);
        Pair<String, String> srnCoredns = parser.getServiceRuntimeNode(coreDnsSrv, "111.111.111.111");
        assertNotNull (srnCoredns);
        assertEquals (null, srnCoredns.getKey());
        assertEquals ("notOK", srnCoredns.getValue());

        Service cerebroSrv = new Service();
        cerebroSrv.setName("cerebro");
        cerebroSrv.setUnique(true);
        Pair<String, String> srnCerebro = parser.getServiceRuntimeNode(cerebroSrv, "111.111.111.111");
        assertNotNull (srnCerebro);
        assertEquals ("111.111.111.111", srnCerebro.getKey());
        assertEquals ("notOK", srnCerebro.getValue());

        Service elasticsearchSrv = new Service();
        elasticsearchSrv.setName("elasticsearch");
        elasticsearchSrv.setUnique(false);
        Pair<String, String> srnES = parser.getServiceRuntimeNode(elasticsearchSrv, "111.111.111.111");
        assertNotNull (srnES);
        assertEquals ("111.111.111.111", srnES.getKey());
        assertEquals ("Running", srnES.getValue());

        Service kubeDasboardSrv = new Service();
        kubeDasboardSrv.setName("kubernetes-dashboard");
        kubeDasboardSrv.setUnique(false);
        Pair<String, String> srnKubeDasboardSrv = parser.getServiceRuntimeNode(kubeDasboardSrv, "111.111.111.111");
        assertNotNull (srnKubeDasboardSrv);
        assertEquals ("111.111.111.111", srnKubeDasboardSrv.getKey());
        assertEquals ("Running", srnKubeDasboardSrv.getValue());
    }


    @Test
    public void testGetServiceRuntimeNodes() throws Exception {

        KubeStatusParser parser = new KubeStatusParser(allPodStatus, allServicesStatus, registryServices);

        List<Pair<String, String>> coreDnsNodes = parser.getServiceRuntimeNodes("coredns");
        assertNotNull (coreDnsNodes);
        assertEquals (1, coreDnsNodes.size());
        assertEquals ("192.168.56.23", coreDnsNodes.get(0).getKey());
        assertEquals ("Running", coreDnsNodes.get(0).getValue());

        List<Pair<String, String>> cerebroNodes = parser.getServiceRuntimeNodes("cerebro");
        assertNotNull (cerebroNodes);
        assertEquals (1, cerebroNodes.size());
        assertEquals ("192.168.56.23", cerebroNodes.get(0).getKey());
        assertEquals ("Error", cerebroNodes.get(0).getValue());

        List<Pair<String, String>> esNodes = parser.getServiceRuntimeNodes("elasticsearch");
        assertNotNull (esNodes);
        assertEquals (3, esNodes.size());
        assertEquals ("192.168.56.21", esNodes.get(0).getKey());
        assertEquals ("Error", esNodes.get(0).getValue());

        assertEquals ("192.168.56.22", esNodes.get(1).getKey());
        assertEquals ("Running", esNodes.get(1).getValue());

        assertEquals ("192.168.56.23", esNodes.get(2).getKey());
        assertEquals ("Running", esNodes.get(2).getValue());
    }



}

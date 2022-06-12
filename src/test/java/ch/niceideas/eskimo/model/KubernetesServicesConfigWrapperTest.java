package ch.niceideas.eskimo.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KubernetesServicesConfigWrapperTest {

    KubernetesServicesConfigWrapper kscw = new KubernetesServicesConfigWrapper("{\n" +
            "    \"spark-runtime_ram\": \"1.2G\",\n" +
            "    \"zeppelin_ram\": \"3G\",\n" +
            "    \"kibana_ram\": \"1024M\",\n" +
            "    \"kafka-manager_ram\": \"1G\",\n" +
            "    \"kafka_ram\": \"1G\",\n" +
            "    \"kafka-manager_install\": \"on\",\n" +
            "    \"elasticsearch_cpu\": \"0.4\",\n" +
            "    \"kafka_cpu\": \"0.3\",\n" +
            "    \"flink-runtime_ram\": \"1.2G\",\n" +
            "    \"kafka-manager_cpu\": \"0.1\",\n" +
            "    \"elasticsearch_install\": \"on\",\n" +
            "    \"kafka_install\": \"on\",\n" +
            "    \"kibana_cpu\": \"0.3\",\n" +
            "    \"zeppelin_install\": \"on\",\n" +
            "    \"spark-runtime_cpu\": \"0.4\",\n" +
            "    \"flink-runtime_cpu\": \"0.4\",\n" +
            "    \"logstash_cpu\": \"0.4\",\n" +
            "    \"flink-runtime_install\": \"on\",\n" +
            "    \"logstash_ram\": \"1G\",\n" +
            "    \"spark-runtime_install\": \"on\",\n" +
            "    \"cerebro_ram\": \"800M\",\n" +
            "    \"kubernetes-dashboard_cpu\": \"0.1\",\n" +
            "    \"spark-console_install\": \"on\",\n" +
            "    \"spark-console_ram\": \"1G\",\n" +
            "    \"elasticsearch_ram\": \"1024M\",\n" +
            "    \"grafana_ram\": \"800M\",\n" +
            "    \"kubernetes-dashboard_ram\": \"1G\",\n" +
            "    \"cerebro_install\": \"on\",\n" +
            "    \"spark-console_cpu\": \"0.1\",\n" +
            "    \"zeppelin_cpu\": \"0.4\",\n" +
            "    \"kibana_install\": \"on\",\n" +
            "    \"logstash_install\": \"on\",\n" +
            "    \"grafana_install\": \"on\",\n" +
            "    \"kubernetes-dashboard_install\": \"on\",\n" +
            "    \"cerebro_cpu\": \"0.2\",\n" +
            "    \"grafana_cpu\": \"0.2\"\n" +
            "}");

    @Test
    public void testGetEnabledServices() throws Exception {
        assertEquals ("cerebro,elasticsearch,flink-runtime,grafana,kafka-manager,kafka,kibana,kubernetes-dashboard,logstash,spark-console,spark-runtime,zeppelin", String.join(",", kscw.getEnabledServices()));
    }

    @Test
    public void testGetCpuSetting() throws Exception {
        assertEquals ("0.4", kscw.getCpuSetting("elasticsearch"));
        assertEquals ("0.2", kscw.getCpuSetting("cerebro"));
    }

    @Test
    public void testGetRamSetting() throws Exception {
        assertEquals ("1024M", kscw.getRamSetting("elasticsearch"));
        assertEquals ("800M", kscw.getRamSetting("cerebro"));
    }

    @Test
    public void testIsServiceInstallRequired() throws Exception {
        assertTrue (kscw.isServiceInstallRequired("elasticsearch"));
        assertFalse (kscw.isServiceInstallRequired("dummy"));
    }

    @Test
    public void testHasEnabledServices() throws Exception {
        assertTrue (kscw.hasEnabledServices());

        KubernetesServicesConfigWrapper other = new KubernetesServicesConfigWrapper(kscw.getFormattedValue());
        kscw.getRootKeys().stream()
                .filter(key -> key.contains(KubernetesServicesConfigWrapper.INSTALL_FLAG))
                .forEach(key -> {
                    other.setValueForPath(key, "off");
                });

        assertFalse (other.hasEnabledServices());
    }

    @Test
    public void testIsDifferentConfig() throws Exception {
        assertFalse (kscw.isDifferentConfig(new KubernetesServicesConfigWrapper(kscw.getFormattedValue()), "zeppelin"));
        assertFalse (kscw.isDifferentConfig(new KubernetesServicesConfigWrapper(kscw.getFormattedValue()), "elasticsearch"));
        assertFalse (kscw.isDifferentConfig(new KubernetesServicesConfigWrapper(kscw.getFormattedValue()), "cerebro"));
        assertFalse (kscw.isDifferentConfig(new KubernetesServicesConfigWrapper(kscw.getFormattedValue()), "kibana"));

        KubernetesServicesConfigWrapper other = new KubernetesServicesConfigWrapper(kscw.getFormattedValue());
        other.setValueForPath("zeppelin_ram", "2048m");

        assertTrue (kscw.isDifferentConfig(other, "zeppelin"));
        assertFalse (kscw.isDifferentConfig(other, "elasticsearch"));
        assertFalse (kscw.isDifferentConfig(other, "kibana"));
        assertFalse (kscw.isDifferentConfig(other, "cerebro"));
    }

}

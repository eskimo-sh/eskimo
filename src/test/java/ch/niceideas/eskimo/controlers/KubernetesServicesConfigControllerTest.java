package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.services.*;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KubernetesServicesConfigControllerTest {

    private KubernetesServicesConfigController mscc = new KubernetesServicesConfigController();

    @BeforeEach
    public void testSetup() {
        mscc.setNotificationService(new NotificationService());

        mscc.setSystemService(new SystemService() {
            @Override
            public SystemStatusWrapper getStatus() {
                return SystemStatusWrapper.empty();
            }
        });

        mscc.setConfigurationService(new ConfigurationService() {
            @Override
            public void saveKubernetesServicesConfig(KubernetesServicesConfigWrapper kubeServicesConfig) {
                // No-Op
            }
        });

        mscc.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public boolean isProcessingPending() {
                return false;
            }
        });
    }

    @Test
    public void testLoadMarathonServicesConfig() throws Exception {

        mscc.setSetupService(new SetupService() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                // No-Op
            }
        });

        mscc.setConfigurationService(new ConfigurationService() {
            @Override
            public KubernetesServicesConfigWrapper loadKubernetesServicesConfig() throws SystemException  {
                return StandardSetupHelpers.getStandardKubernetesConfig();
            }
        });

        assertTrue (new JSONObject("{\n" +
                "    \"kibana_install\": \"on\",\n" +
                "    \"cerebro_install\": \"on\",\n" +
                "    \"spark-history-server_install\": \"on\",\n" +
                "    \"zeppelin_install\": \"on\",\n" +
                "    \"kafka-manager_install\": \"on\"\n" +
                "}").similar(new JSONObject (mscc.loadKubernetesServicesConfig())));

        mscc.setSetupService(new SetupService() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                throw new SetupException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"clear\": \"setup\",\n" +
                "  \"processingPending\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", mscc.loadKubernetesServicesConfig());

        mscc.setSetupService(new SetupService() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                // No-Op
            }
        });

        mscc.setConfigurationService(new ConfigurationService() {
            @Override
            public KubernetesServicesConfigWrapper loadKubernetesServicesConfig() throws SystemException  {
                throw new SystemException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", mscc.loadKubernetesServicesConfig());
    }

    @Test
    public void testReinstallMarathonServicesConfig() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = NodesConfigControllerTest.createHttpSession(sessionContent);

        mscc.setKubernetesService(new KubernetesService() {
            @Override
            public void applyServicesConfig(KubernetesOperationsCommand command) {
                // No Op
            }
        });

        mscc.setConfigurationService(new ConfigurationService() {
            @Override
            public void saveServicesInstallationStatus(ServicesInstallStatusWrapper status) {
                // No Op
            }
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus(){
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
            @Override
            public NodesConfigWrapper loadNodesConfig() {
                return StandardSetupHelpers.getStandard2NodesSetup();
            }
            @Override
            public KubernetesServicesConfigWrapper loadKubernetesServicesConfig() throws SystemException  {
                return StandardSetupHelpers.getStandardKubernetesConfig();
            }
            @Override
            public void saveKubernetesServicesConfig(KubernetesServicesConfigWrapper kubeServicesConfig) throws FileException, SetupException {
                // No Op
            }
        });

        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();
        mscc.setServicesDefinition(sd);

        assertEquals ("{\n" +
                "  \"command\": {\n" +
                "    \"uninstallations\": [],\n" +
                "    \"restarts\": [],\n" +
                "    \"installations\": [\n" +
                "      \"cerebro\",\n" +
                "      \"zeppelin\"\n" +
                "    ],\n" +
                "    \"warnings\": \"Kubernetes is not available. The changes in kubernetes services configuration and deployments will be saved but they will <strong>need to be applied again<\\/strong> another time when Kubernetes Master is available\"\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", mscc.reinstallKubernetesServiceConfig("{\"spark-runtime_install\":\"on\",\"kafka_install\":\"on\",\"elasticsearch_install\":\"on\",\"cerebro_install\":\"on\",\"grafana_install\":\"on\",\"zeppelin_install\":\"on\"}", session));

        assertEquals ("{\"status\": \"OK\"}", mscc.applyKubernetesServicesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }

    @Test
    public void testApplyNodesConfig_demoMode() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();
        HttpSession session = NodesConfigControllerTest.createHttpSession(sessionContent);

        mscc.setDemoMode(true);

        assertEquals ("{\n" +
                "  \"messages\": \"Unfortunately, re-applying marathon configuration or changing marathon configuration is not possible in DEMO mode.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", mscc.applyKubernetesServicesConfig(session));
    }

    @Test
    public void testApplyNodesConfig_processingPending() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();
        HttpSession session = NodesConfigControllerTest.createHttpSession(sessionContent);

        mscc.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public boolean isProcessingPending() {
                return true;
            }
        });

        assertEquals ("{\n" +
                "  \"messages\": \"Some backend operations are currently running. Please retry after they are completed.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", mscc.applyKubernetesServicesConfig(session));
    }

    @Test
    public void testSaveKubernetesServicesConfig() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = NodesConfigControllerTest.createHttpSession(sessionContent);

        mscc.setKubernetesService(new KubernetesService() {
            @Override
            public void applyServicesConfig(KubernetesOperationsCommand command) {
                // No Op
            }
        });

        mscc.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
            @Override
            public void saveKubernetesServicesConfig(KubernetesServicesConfigWrapper kubeServicesConfig) {
                // No Op
            }
            @Override
            public void saveServicesInstallationStatus(ServicesInstallStatusWrapper status) {
                // No Op
            }
            @Override
            public KubernetesServicesConfigWrapper loadKubernetesServicesConfig() {
                // No Op
                return null;
            }
        });

        mscc.setMarathonServicesConfigChecker(new KubernetesServicesConfigChecker() {
            @Override
            public void checkKubernetesServicesSetup(KubernetesServicesConfigWrapper kubeServicesConfig) throws KubernetesServicesConfigException {
                // No Op
            }
        });


        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();
        mscc.setServicesDefinition(sd);

        assertEquals ("{\n" +
                        "  \"command\": {\n" +
                        "    \"uninstallations\": [\n" +
                        "      \"kafka-manager\",\n" +
                        "      \"kibana\",\n" +
                        "      \"spark-history-server\"\n" +
                        "    ],\n" +
                        "    \"restarts\": [],\n" +
                        "    \"installations\": [\"grafana\"],\n" +
                        "    \"warnings\": \"Kubernetes is not available. The changes in kubernetes services configuration and deployments will be saved but they will <strong>need to be applied again<\\/strong> another time when Kubernetes Master is available\"\n" +
                        "  },\n" +
                        "  \"status\": \"OK\"\n" +
                        "}",
                mscc.saveKubernetesServicesConfig("{" +
                "\"cerebro_install\":\"on\"," +
                "\"elasticsearch_install\":\"on\"," +
                "\"kafka_install\":\"on\"," +
                "\"spark-runtime_install\":\"on\"," +
                "\"grafana_install\":\"on\"," +
                "\"zeppelin_install\":\"on\"," +
                "\"spark-history-server\":\"on\"," +
                "\"kibana\":\"on\"}", session));

        assertEquals ("{\"status\": \"OK\"}", mscc.applyKubernetesServicesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }
}

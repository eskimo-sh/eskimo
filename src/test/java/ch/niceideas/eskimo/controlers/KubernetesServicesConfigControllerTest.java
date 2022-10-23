package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.SimpleOperationCommand;
import ch.niceideas.eskimo.services.OperationsMonitoringService;
import ch.niceideas.eskimo.services.StandardSetupHelpers;
import ch.niceideas.eskimo.test.infrastructure.HttpSessionHelper;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import ch.niceideas.eskimo.test.services.SetupServiceTestImpl;
import ch.niceideas.eskimo.test.services.SystemServiceTestImpl;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-cluster", "no-web-stack"})
public class KubernetesServicesConfigControllerTest {

    @Autowired
    private KubernetesServicesConfigController kscc;

    @Autowired
    private SystemServiceTestImpl systemSeviceTest;

    @Autowired
    private SetupServiceTestImpl setupServiceTest;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @BeforeEach
    public void testSetup() {

        systemSeviceTest.setReturnEmptySystemStatus(true);

        if (operationsMonitoringService.isProcessingPending()) {
            operationsMonitoringService.operationsFinished(true);
        }

        SecurityContextHelper.loginAdmin();

        configurationServiceTest.reset();

        kscc.setDemoMode(false);
    }

    @Test
    public void testLoadKubernetesServicesConfig() throws Exception {

        setupServiceTest.setSetupCompleted();

        configurationServiceTest.setStandardKubernetesConfig();

        //System.err.println (kscc.loadKubernetesServicesConfig());
        assertTrue (new JSONObject("{\n" +
                "    \"spark-runtime_ram\": \"800M\",\n" +
                "    \"zeppelin_ram\": \"800M\",\n" +
                "    \"kibana_ram\": \"800M\",\n" +
                "    \"kafka-manager_ram\": \"800M\",\n" +
                "    \"kafka-manager_install\": \"on\",\n" +
                "    \"kafka_ram\": \"800M\",\n" +
                "    \"kafka_cpu\": \"1\",\n" +
                "    \"elasticsearch_cpu\": \"1\",\n" +
                "    \"kafka-manager_cpu\": \"1\",\n" +
                "    \"elasticsearch_install\": \"on\",\n" +
                "    \"kafka_install\": \"on\",\n" +
                "    \"kibana_cpu\": \"1\",\n" +
                "    \"zeppelin_install\": \"on\",\n" +
                "    \"spark-runtime_cpu\": \"1\",\n" +
                "    \"logstash_cpu\": \"1\",\n" +
                "    \"logstash_ram\": \"800M\",\n" +
                "    \"spark-runtime_install\": \"on\",\n" +
                "    \"cerebro_ram\": \"800M\",\n" +
                "    \"spark-console_cpu\": \"1\",\n" +
                "    \"spark-console_install\": \"on\",\n" +
                "    \"elasticsearch_ram\": \"800M\",\n" +
                "    \"spark-console_ram\": \"800M\",\n" +
                "    \"cerebro_install\": \"on\",\n" +
                "    \"zeppelin_cpu\": \"1\",\n" +
                "    \"kibana_install\": \"on\",\n" +
                "    \"logstash_install\": \"on\",\n" +
                "    \"cerebro_cpu\": \"1\"\n" +
                "}").similar(new JSONObject (kscc.loadKubernetesServicesConfig())));

        setupServiceTest.setSetupError();

        assertEquals ("{\n" +
                "  \"clear\": \"setup\",\n" +
                "  \"processingPending\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", kscc.loadKubernetesServicesConfig());

        setupServiceTest.setSetupCompleted();

        configurationServiceTest.setKubernetesConfigError();

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", kscc.loadKubernetesServicesConfig());
    }

    @Test
    public void testReinstallKubernetesServicesConfig() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpSessionHelper.createHttpSession(sessionContent);

        configurationServiceTest.setStandard2NodesInstallStatus();
        configurationServiceTest.setStandard2NodesSetup();
        configurationServiceTest.setStandardKubernetesConfig();;

        assertEquals ("{\n" +
                "  \"command\": {\n" +
                "    \"uninstallations\": [],\n" +
                "    \"restarts\": [],\n" +
                "    \"installations\": [\n" +
                "      \"elasticsearch\",\n" +
                "      \"cerebro\",\n" +
                "      \"spark-runtime\",\n" +
                "      \"kafka\",\n" +
                "      \"zeppelin\"\n" +
                "    ],\n" +
                "    \"warnings\": \"Kubernetes is not available. The changes in kubernetes services configuration and deployments will be saved but they will <strong>need to be applied again<\\/strong> another time when Kubernetes Master is available\"\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", kscc.reinstallKubernetesServiceConfig("{\"spark-runtime_install\":\"on\",\"kafka_install\":\"on\",\"elasticsearch_install\":\"on\",\"cerebro_install\":\"on\",\"grafana_install\":\"on\",\"zeppelin_install\":\"on\"}", session));

        assertEquals ("{\"status\": \"OK\"}", kscc.applyKubernetesServicesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }

    @Test
    public void testApplyNodesConfig_demoMode() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();
        HttpSession session = HttpSessionHelper.createHttpSession(sessionContent);

        kscc.setDemoMode(true);

        assertEquals ("{\n" +
                "  \"messages\": \"Unfortunately, re-applying kubernetes configuration or changing kubernetes configuration is not possible in DEMO mode.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", kscc.applyKubernetesServicesConfig(session));
    }

    @Test
    public void testApplyNodesConfig_processingPending() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();
        HttpSession session = HttpSessionHelper.createHttpSession(sessionContent);

        operationsMonitoringService.operationsStarted(new SimpleOperationCommand("test", "test", "192.168.10.15"));

        assertEquals ("{\n" +
                "  \"messages\": \"Some backend operations are currently running. Please retry after they are completed.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", kscc.applyKubernetesServicesConfig(session));
    }

    @Test
    public void testSaveKubernetesServicesConfig() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpSessionHelper.createHttpSession(sessionContent);

        configurationServiceTest.setStandard2NodesInstallStatus();
        configurationServiceTest.setStandard2NodesSetup();

        assertEquals ("{\n" +
                        "  \"command\": {\n" +
                        "    \"uninstallations\": [],\n" +
                        "    \"restarts\": [\n" +
                        "      \"spark-console\",\n" +
                        "      \"elasticsearch\",\n" +
                        "      \"cerebro\",\n" +
                        "      \"spark-runtime\",\n" +
                        "      \"logstash\",\n" +
                        "      \"kafka\",\n" +
                        "      \"kafka-manager\",\n" +
                        "      \"kibana\",\n" +
                        "      \"zeppelin\"\n" +
                        "    ],\n" +
                        "    \"installations\": [],\n" +
                        "    \"warnings\": \"Kubernetes is not available. The changes in kubernetes services configuration and deployments will be saved but they will <strong>need to be applied again<\\/strong> another time when Kubernetes Master is available\"\n" +
                        "  },\n" +
                        "  \"status\": \"OK\"\n" +
                        "}",
                kscc.saveKubernetesServicesConfig(StandardSetupHelpers.getStandardKubernetesConfig().getFormattedValue(), session));

        assertEquals ("{\"status\": \"OK\"}", kscc.applyKubernetesServicesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }
}

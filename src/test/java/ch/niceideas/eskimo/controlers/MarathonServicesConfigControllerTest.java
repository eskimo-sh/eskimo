package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.MarathonOperationsCommand;
import ch.niceideas.eskimo.model.MarathonServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.services.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;

public class MarathonServicesConfigControllerTest {

    private MarathonServicesConfigController mscc = new MarathonServicesConfigController();

    @Test
    public void testLoadMarathonServicesConfig() throws Exception {

        mscc.setSetupService(new SetupService() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                // No-Op
            }
        });

        mscc.setSystemService(new SystemService(false) {
            @Override
            public boolean isProcessingPending() {
                return false;
            }
        });

        mscc.setConfigurationService(new ConfigurationService() {
            @Override
            public MarathonServicesConfigWrapper loadMarathonServicesConfig() throws SystemException  {
                return StandardSetupHelpers.getStandardMarathonConfig();
            }
        });

        assertTrue (new JSONObject("{\n" +
                "    \"kibana_install\": \"on\",\n" +
                "    \"gdash_install\": \"on\",\n" +
                "    \"cerebro_install\": \"on\",\n" +
                "    \"spark-history-server_install\": \"on\",\n" +
                "    \"zeppelin_install\": \"on\",\n" +
                "    \"kafka-manager_install\": \"on\"\n" +
                "}").similar(new JSONObject (mscc.loadMarathonServicesConfig())));

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
                "}", mscc.loadMarathonServicesConfig());

        mscc.setSetupService(new SetupService() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                // No-Op
            }
        });

        mscc.setSystemService(new SystemService(false) {
            @Override
            public boolean isProcessingPending() {
                return false;
            }
        });

        mscc.setConfigurationService(new ConfigurationService() {
            @Override
            public MarathonServicesConfigWrapper loadMarathonServicesConfig() throws SystemException  {
                throw new SystemException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", mscc.loadMarathonServicesConfig());
    }

    @Test
    public void testReinstallMarathonServicesConfig() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = NodesConfigControllerTest.createHttpSession(sessionContent);

        mscc.setMarathonService(new MarathonService() {
            @Override
            public void applyMarathonServicesConfig(MarathonOperationsCommand command) {
                // No Op
            }
        });

        mscc.setSystemService(new SystemService(false) {
            @Override
            public boolean isProcessingPending() {
                return false;
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
            public MarathonServicesConfigWrapper loadMarathonServicesConfig() throws SystemException  {
                return StandardSetupHelpers.getStandardMarathonConfig();
            }
            @Override
            public void saveMarathonServicesConfig(MarathonServicesConfigWrapper marathonServicesConfig) throws FileException, SetupException {
                // No Op
            }
        });

        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();
        mscc.setServicesDefinition(sd);

        assertEquals ("{\n" +
                "  \"command\": {\n" +
                "    \"uninstallations\": [],\n" +
                "    \"installations\": [\n" +
                "      \"cerebro\",\n" +
                "      \"zeppelin\"\n" +
                "    ],\n" +
                "    \"warnings\": \"Marathon is not available. The changes in marathon services configuration and deployments will be saved but they will <b>need to be applied again<\\/b> another time when marathon is available\"\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", mscc.reinstallMarathonServiceConfig("{\"cerebro_install\":\"on\",\"grafana_install\":\"on\",\"zeppelin_install\":\"on\"}", session));

        assertEquals ("{\"status\": \"OK\" }", mscc.applyMarathonServicesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }

    @Test
    public void testApplyNodesConfig_demoMode() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();
        HttpSession session = NodesConfigControllerTest.createHttpSession(sessionContent);

        mscc.setSystemService(new SystemService(false) {
            @Override
            public boolean isProcessingPending() {
                return false;
            }
        });

        mscc.setDemoMode(true);

        assertEquals ("{\n" +
                "  \"messages\": \"Unfortunately, re-applying marathon configuration or changing marathon configuration is not possible in DEMO mode.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", mscc.applyMarathonServicesConfig(session));
    }

    @Test
    public void testApplyNodesConfig_processingPending() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();
        HttpSession session = NodesConfigControllerTest.createHttpSession(sessionContent);

        mscc.setSystemService(new SystemService(false) {
            @Override
            public boolean isProcessingPending() {
                return true;
            }
        });

        assertEquals ("{\n" +
                "  \"messages\": \"Some backend operations are currently running. Please retry after they are completed..\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", mscc.applyMarathonServicesConfig(session));
    }

    @Test
    public void testSaveNodesConfig() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = NodesConfigControllerTest.createHttpSession(sessionContent);

        mscc.setMarathonService(new MarathonService() {
            @Override
            public void applyMarathonServicesConfig(MarathonOperationsCommand command) {
                // No Op
            }
        });

        mscc.setSystemService(new SystemService(false) {
            @Override
            public boolean isProcessingPending() {
                return false;
            }
        });

        mscc.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
            @Override
            public void saveMarathonServicesConfig(MarathonServicesConfigWrapper marathonServicesConfig) throws FileException, SetupException {
                // No Op
            }
            @Override
            public void saveServicesInstallationStatus(ServicesInstallStatusWrapper status) throws FileException, JSONException, SetupException {
                // No Op
            }
        });

        mscc.setMarathonServicesConfigChecker(new MarathonServicesConfigChecker() {
            @Override
            public void checkMarathonServicesSetup(MarathonServicesConfigWrapper marathonConfig) throws MarathonServicesConfigException {
                // No Op
            }
        });


        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();
        mscc.setServicesDefinition(sd);

        assertEquals ("{\n" +
                        "  \"command\": {\n" +
                        "    \"uninstallations\": [\n" +
                        "      \"gdash\",\n" +
                        "      \"kafka-manager\",\n" +
                        "      \"kibana\",\n" +
                        "      \"spark-history-server\"\n" +
                        "    ],\n" +
                        "    \"installations\": [\"grafana\"],\n" +
                        "    \"warnings\": \"Marathon is not available. The changes in marathon services configuration and deployments will be saved but they will <b>need to be applied again<\\/b> another time when marathon is available\"\n" +
                        "  },\n" +
                        "  \"status\": \"OK\"\n" +
                        "}",
                mscc.saveMarathonServicesConfig("{" +
                "\"cerebro_install\":\"on\"," +
                "\"grafana_install\":\"on\"," +
                "\"zeppelin_install\":\"on\"," +
                "\"spark-history-server\":\"on\"," +
                "\"kibana\":\"on\"}", session));

        assertEquals ("{\"status\": \"OK\" }", mscc.applyMarathonServicesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }
}

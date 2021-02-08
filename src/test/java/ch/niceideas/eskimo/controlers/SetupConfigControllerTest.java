package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.SetupCommand;
import ch.niceideas.eskimo.services.*;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SetupConfigControllerTest {

    private SetupConfigController scc = new SetupConfigController();

    @BeforeEach
    public void testSetup() {
        scc.setMessagingService(new MessagingService());
        scc.setNotificationService(new NotificationService());

        scc.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public boolean isProcessingPending() {
                return false;
            }
        });
    }

    @Test
    public void testLoadSetupConfig() {

        scc.setSetupService(new SetupService() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                // No Op
            }
        });


        scc.setConfigurationService(new ConfigurationService() {
            @Override
            public String loadSetupConfig() throws FileException, SetupException {
                throw new SetupException ("Application is not initialized properly. Missing file 'config.conf' system configuration");
            }
        });

        assertEquals ("{\n" +
                "  \"clear\": \"missing\",\n" +
                "  \"isSnapshot\": true,\n" +
                "  \"version\": \"DEV-SNAPSHOT\",\n" +
                "  \"processingPending\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.loadSetupConfig());

        scc.setConfigurationService(new ConfigurationService() {
            @Override
            public String loadSetupConfig() throws FileException, SetupException {
                return "{\"config\": \"dummy\"}";
            }
        });

        assertEquals ("{\n" +
                "    \"processingPending\": false,\n" +
                "    \"isSnapshot\": true,\n" +
                "    \"config\": \"dummy\",\n" +
                "    \"version\": \"DEV-SNAPSHOT\"\n" +
                "}", scc.loadSetupConfig());

        scc.setSetupService(new SetupService() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                throw new SetupException("No loaded");
            }
        });

        scc.setConfigurationService(new ConfigurationService() {
            @Override
            public String loadSetupConfig() throws FileException, SetupException {
                return "{\"config\": \"dummy\"}";
            }
        });

        assertEquals ("{\n" +
                "    \"processingPending\": false,\n" +
                "    \"clear\": \"setup\",\n" +
                "    \"message\": \"No loaded\",\n" +
                "    \"isSnapshot\": true,\n" +
                "    \"config\": \"dummy\",\n" +
                "    \"version\": \"DEV-SNAPSHOT\"\n" +
                "}", scc.loadSetupConfig());

        scc.setConfigurationService(new ConfigurationService() {
            @Override
            public String loadSetupConfig() throws FileException, SetupException {
                throw new SetupException("Setup error");
            }
        });

        assertEquals ("{\n" +
                "  \"clear\": \"missing\",\n" +
                "  \"isSnapshot\": true,\n" +
                "  \"version\": \"DEV-SNAPSHOT\",\n" +
                "  \"processingPending\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.loadSetupConfig());

        scc.setSetupService(new SetupService() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                // No Op
            }
        });

        scc.setConfigurationService(new ConfigurationService() {
            @Override
            public String loadSetupConfig() throws FileException, SetupException {
                return "{\"config\": \"dummy\"}";
            }
        });

        scc.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public boolean isProcessingPending() {
                return true;
            }
        });

        assertEquals ("{\n" +
                "    \"processingPending\": true,\n" +
                "    \"isSnapshot\": true,\n" +
                "    \"config\": \"dummy\",\n" +
                "    \"version\": \"DEV-SNAPSHOT\"\n" +
                "}", scc.loadSetupConfig());
    }

    @Test
    public void testSaveSetup_demoMode() {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = NodesConfigControllerTest.createHttpSession(sessionContent);

        scc.setDemoMode (true);

        assertEquals ("{\n" +
                "  \"messages\": \"Unfortunately, changing setup configuration is not possible in DEMO mode.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.applySetup(session));
    }

    @Test
    public void testSaveSetup_processingPending() {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = NodesConfigControllerTest.createHttpSession(sessionContent);

        scc.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public boolean isProcessingPending() {
                return true;
            }
        });

        assertEquals ("{\n" +
                "  \"messages\": \"Some backend operations are currently running. Please retry after they are completed.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.applySetup(session));
    }

    @Test
    public void testSaveSetup() {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = NodesConfigControllerTest.createHttpSession(sessionContent);

        scc.setSetupService(new SetupService() {
            @Override
            public SetupCommand saveAndPrepareSetup(String configAsString) throws SetupException {
                JsonWrapper setupConfigJSON = new JsonWrapper(configAsString);
                return SetupCommand.create(setupConfigJSON, this);
            }
            @Override
            public void prepareSetup (
                    JsonWrapper setupConfig,
                    Set<String> downloadPackages, Set<String> buildPackage, Set<String> downloadMesos, Set<String> buildMesos, Set<String> packageUpdate)
                    throws SetupException {
                // No Op
            }
            @Override
            public String getPackagesDownloadUrlRoot() {
                return "dummy";
            }
            @Override
            public String applySetup(SetupCommand setupCommand) throws JSONException {
                // No Op
                return "OK";
            }
        });

        assertEquals ("{\n" +
                "  \"command\": {\n" +
                "    \"buildPackage\": [],\n" +
                "    \"buildMesos\": [],\n" +
                "    \"downloadMesos\": [],\n" +
                "    \"none\": true,\n" +
                "    \"downloadPackages\": [],\n" +
                "    \"packageUpdates\": [],\n" +
                "    \"packageDownloadUrl\": \"dummy\"\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}",
                scc.saveSetup("" +
                    "{\"setup_storage\":\"/data/eskimo_config\"," +
                    "\"ssh_username\":\"eskimo\"," +
                    "\"filename-ssh-key\":\"ssh_key\"," +
                    "\"content-ssh-key\":\"DUMMY\"," +
                    "\"setup-mesos-origin\":\"download\"," +
                    "\"setup-services-origin\":\"build\"}", session));

        assertEquals ("OK", scc.applySetup(session));

        assertTrue(sessionContent.isEmpty());
    }


}

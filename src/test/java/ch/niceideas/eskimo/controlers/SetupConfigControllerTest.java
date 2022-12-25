package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.SimpleOperationCommand;
import ch.niceideas.eskimo.services.OperationsMonitoringService;
import ch.niceideas.eskimo.test.infrastructure.HttpObjectsHelper;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import ch.niceideas.eskimo.test.services.SetupServiceTestImpl;
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
@ActiveProfiles({"no-web-stack", "test-services", "test-conf", "test-setup", "test-system"})
public class SetupConfigControllerTest {

    @Autowired
    private SetupConfigController scc;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private SetupServiceTestImpl setupServiceTest;

    @BeforeEach
    public void testSetup() {
        if (operationsMonitoringService.isProcessingPending()) {
            operationsMonitoringService.operationsFinished(true);
        }

        configurationServiceTest.reset();

        SecurityContextHelper.loginAdmin();

        scc.setDemoMode (false);
    }

    @Test
    public void testLoadSetupConfig() throws Exception {

        configurationServiceTest.setSetupConfigNotCompletedError();

        assertEquals ("{\n" +
                "  \"clear\": \"missing\",\n" +
                "  \"isSnapshot\": false,\n" +
                "  \"version\": \"@project.version@\",\n" +
                "  \"processingPending\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.loadSetupConfig());

        configurationServiceTest.saveSetupConfig("{\"config\": \"dummy\"}");

        assertEquals ("{\n" +
                "    \"processingPending\": false,\n" +
                "    \"isSnapshot\": false,\n" +
                "    \"config\": \"dummy\",\n" +
                "    \"version\": \"@project.version@\"\n" +
                "}", scc.loadSetupConfig());

        setupServiceTest.setSetupError();

        assertEquals ("{\n" +
                "    \"processingPending\": false,\n" +
                "    \"clear\": \"setup\",\n" +
                "    \"message\": \"Test Error\",\n" +
                "    \"isSnapshot\": false,\n" +
                "    \"config\": \"dummy\",\n" +
                "    \"version\": \"@project.version@\"\n" +
                "}", scc.loadSetupConfig());

        configurationServiceTest.setSetupConfigNotCompletedError();

        assertEquals ("{\n" +
                "  \"clear\": \"missing\",\n" +
                "  \"isSnapshot\": false,\n" +
                "  \"version\": \"@project.version@\",\n" +
                "  \"processingPending\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.loadSetupConfig());

        setupServiceTest.setSetupCompleted();
        configurationServiceTest.setSetupCompleted();

        operationsMonitoringService.operationsStarted(new SimpleOperationCommand("test", "test", "192.168.10.15"));

        assertEquals ("{\n" +
                "    \"processingPending\": true,\n" +
                "    \"isSnapshot\": false,\n" +
                "    \"config\": \"dummy\",\n" +
                "    \"version\": \"@project.version@\"\n" +
                "}", scc.loadSetupConfig());
    }

    @Test
    public void testSaveSetup_demoMode() {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        scc.setDemoMode (true);

        assertEquals ("{\n" +
                "  \"messages\": \"Unfortunately, changing setup configuration is not possible in DEMO mode.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.applySetup(session));
    }

    @Test
    public void testSaveSetup_processingPending() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        operationsMonitoringService.operationsStarted(new SimpleOperationCommand("test", "test", "192.168.10.15"));

        assertEquals ("{\n" +
                "  \"messages\": \"Some backend operations are currently running. Please retry after they are completed.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.applySetup(session));
    }

    @Test
    public void testSaveSetup() {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        assertEquals ("{\n" +
                        "  \"command\": {\n" +
                        "    \"buildPackage\": [],\n" +
                        "    \"buildKube\": [],\n" +
                        "    \"downloadKube\": [],\n" +
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
                    "\"setup-kube-origin\":\"download\"," +
                    "\"setup-services-origin\":\"download\"}", session));

        assertEquals ("OK", scc.applySetup(session));

        assertTrue(sessionContent.isEmpty());
    }


}

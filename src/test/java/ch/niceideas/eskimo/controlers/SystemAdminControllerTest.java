package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.SimpleOperationCommand;
import ch.niceideas.eskimo.services.NotificationService;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import ch.niceideas.eskimo.test.services.OperationsMonitoringServiceTestImpl;
import ch.niceideas.eskimo.test.services.SystemServiceTestImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-conf", "test-operations", "test-system"})
public class SystemAdminControllerTest {

    @Autowired
    private SystemAdminController sac;

    @Autowired
    private OperationsMonitoringServiceTestImpl operationsMonitoringServiceTest;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private SystemServiceTestImpl systemServiceTest;


    @BeforeEach
    public void testSetup() {

        configurationServiceTest.reset();

        systemServiceTest.reset();

        operationsMonitoringServiceTest.reset();

        SecurityContextHelper.loginAdmin();

        sac.setDemoMode(false);
    }

    @Test
    public void testInterruptProcessing() {

        assertEquals ("{\"status\": \"OK\"}", sac.interruptProcessing());

        operationsMonitoringServiceTest.setInteruptProcessingError();

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", sac.interruptProcessing());
    }

    @Test
    public void testShowJournal() {

        assertEquals ("{\n" +
                "  \"messages\": \"zookeeper journal display from 192.168.10.11.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.showJournal("zookeeper", "192.168.10.11"));
    }

    @Test
    public void testStartService() {

        assertEquals ("{\n" +
                "  \"messages\": \"zookeeper has been started successfuly on 192.168.10.11.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.startService("zookeeper", "192.168.10.11"));

        systemServiceTest.setStartServiceError();

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", sac.startService("zookeeper", "192.168.10.11"));
    }

    @Test
    public void testStopService() {

        assertEquals ("{\n" +
                "  \"messages\": \"zookeeper has been stopped successfuly on 192.168.10.11.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.stopService("zookeeper", "192.168.10.11"));
    }

    @Test
    public void testRestartService() {

        assertEquals ("{\n" +
                "  \"messages\": \"zookeeper has been restarted successfuly on 192.168.10.11.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.restartService("zookeeper", "192.168.10.11"));
    }

    @Test
    public void testServiceActionCustom() {

        assertEquals ("{\n" +
                "  \"messages\": \"command show_log for zookeeper has been executed successfuly on 192.168.10.11.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.serviceActionCustom("show_log", "zookeeper", "192.168.10.11"));
    }

    @Test
    public void testReinstallService_demoMode() throws Exception {

        sac.setDemoMode(true);

        assertEquals ("{\n" +
                "  \"messages\": \"Unfortunately, re-installing a service is not possible in DEMO mode.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.reinstallService("zookeeper", "192.168.10.13"));
    }

    @Test
    public void testReinstallService_processingPending() throws Exception {

        operationsMonitoringServiceTest.operationsStarted(new SimpleOperationCommand("test", "test", "192.168.10.15"));

        assertEquals ("{\n" +
                "  \"messages\": \"Some backend operations are currently running. Please retry after they are completed.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.reinstallService("zookeeper", "192.168.10.13"));
    }

    @Test
    public void testReinstallService() throws Exception {

        configurationServiceTest.setStandard2NodesSetup();
        configurationServiceTest.setStandard2NodesInstallStatus();

        assertEquals ("OK", configurationServiceTest.loadServicesInstallationStatus().getValueForPathAsString("zookeeper_installed_on_IP_192-168-10-13"));

        assertEquals ("{\n" +
                "  \"messages\": \"zookeeper has been reinstalled successfuly on 192.168.10.13.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.reinstallService("zookeeper", "192.168.10.13"));

        // should have been remove prior to reinstall and not set back since reinstall is mocked (No-Op)
        assertNull (configurationServiceTest.loadServicesInstallationStatus().getValueForPathAsString("zookeeper_installed_on_IP_192-168-10-13"));

        assertTrue(configurationServiceTest.isSaveServicesInstallationStatusCalled());
    }

}

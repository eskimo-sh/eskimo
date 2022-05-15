package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.Service;
import ch.niceideas.eskimo.model.ServiceOperationsCommand;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.services.*;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class SystemAdminControllerTest {

    private SystemAdminController sac = new SystemAdminController();
    private ServicesDefinition sd = new ServicesDefinition();

    @BeforeEach
    public void setUp() throws Exception {
        sd.afterPropertiesSet();
        sac.setServicesDefinition(sd);

        sac.setNotificationService(new NotificationService());

        sac.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public boolean isProcessingPending() {
                return false;
            }
        });
    }

    @Test
    public void testInterruptProcessing() {

        sac.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public void interruptProcessing() {
                // No Op
            }
        });

        assertEquals ("{\"status\": \"OK\"}", sac.interruptProcessing());

        sac.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public void interruptProcessing() {
                throw new JSONException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", sac.interruptProcessing());
    }

    @Test
    public void testShowJournal() {
        sac.setSystemService(new SystemService(false) {
            @Override
            public void showJournal(Service service, String node) {
                // No Op
            }
        });
        assertEquals ("{\n" +
                "  \"messages\": \"zookeeper journal display from 192.168.10.11.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.showJournal("zookeeper", "192.168.10.11"));
    }

    @Test
    public void testStartService() {
        sac.setSystemService(new SystemService(false) {
            @Override
            public void startService(Service service, String node) {
                // No Op
            }
        });
        assertEquals ("{\n" +
                "  \"messages\": \"zookeeper has been started successfuly on 192.168.10.11.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.startService("zookeeper", "192.168.10.11"));

        sac.setSystemService(new SystemService(false) {
            @Override
            public void startService(Service service, String node) throws SystemException {
                throw new SystemException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", sac.startService("zookeeper", "192.168.10.11"));
    }

    @Test
    public void testStopService() {
        sac.setSystemService(new SystemService(false) {
            @Override
            public void stopService(Service service, String node) {
                // No Op
            }
        });
        assertEquals ("{\n" +
                "  \"messages\": \"zookeeper has been stopped successfuly on 192.168.10.11.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.stopService("zookeeper", "192.168.10.11"));
    }

    @Test
    public void testRestartService() {
        sac.setSystemService(new SystemService(false) {
            @Override
            public void restartService(Service service, String node) {
                // No Op
            }
        });
        assertEquals ("{\n" +
                "  \"messages\": \"zookeeper has been restarted successfuly on 192.168.10.11.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.restartService("zookeeper", "192.168.10.11"));
    }

    @Test
    public void testServiceActionCustom() {
        sac.setSystemService(new SystemService(false) {
            @Override
            public void callCommand(String commandId, String serviceName, String node) {
                // No Op
            }
        });
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
    public void testReinstallService_processingPending() {

        sac.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public boolean isProcessingPending() {
                return true;
            }
        });

        assertEquals ("{\n" +
                "  \"messages\": \"Some backend operations are currently running. Please retry after they are completed.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.reinstallService("zookeeper", "192.168.10.13"));
    }

    @Test
    public void testReinstallService() throws Exception {

        AtomicBoolean called = new AtomicBoolean(false);

        sac.setSystemService(new SystemService(false) {

            @Override
            public void delegateApplyNodesConfig(ServiceOperationsCommand command)  {
                // No Op
            }

        });

        sac.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
                ServicesInstallStatusWrapper retWrapper = StandardSetupHelpers.getStandard2NodesInstallStatus();
                assertEquals ("OK", retWrapper.getValueForPathAsString("zookeeper_installed_on_IP_192-168-10-13"));
                return retWrapper;
            }
            @Override
            public void saveServicesInstallationStatus(ServicesInstallStatusWrapper status) throws FileException, JSONException, SetupException {
                called.set(true);
                // should have bneen removed
                assertNull(status.getValueForPathAsString("zookeeper_installed_on_IP_192-168-10-13"));
            }
            @Override
            public NodesConfigWrapper loadNodesConfig() throws SystemException, SetupException {
                return StandardSetupHelpers.getStandard2NodesSetup();
            }
        });

        sac.setNodeRangeResolver(new NodeRangeResolver() {
            @Override
            public NodesConfigWrapper resolveRanges(NodesConfigWrapper rawNodesConfig) throws NodesConfigurationException {
                return rawNodesConfig;
            }
        });

        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();
        sac.setServicesDefinition(sd);

        assertEquals ("{\n" +
                "  \"messages\": \"zookeeper has been reinstalled successfuly on 192.168.10.13.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.reinstallService("zookeeper", "192.168.10.13"));

        assertTrue(called.get());
    }

}

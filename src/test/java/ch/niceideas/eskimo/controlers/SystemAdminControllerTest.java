package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.OperationsCommand;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.services.*;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.TestCase.*;

public class SystemAdminControllerTest {

    private SystemAdminController sac = new SystemAdminController();
    private ServicesDefinition sd = new ServicesDefinition();

    @Before
    public void setUp() throws Exception {
        sd.afterPropertiesSet();
        sac.setServicesDefinition(sd);
    }

    @Test
    public void testInterruptProcessing() {

        sac.setSystemService(new SystemService(false) {
            @Override
            public void interruptProcessing() {
                // No Op
            }
        });

        assertEquals ("{\"status\": \"OK\"}", sac.interruptProcessing());

        sac.setSystemService(new SystemService(false) {
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
            public void showJournal(String service, String ipAddress) throws SSHCommandException {
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
            public void startService(String service, String ipAddress) throws SSHCommandException {
                // No Op
            }
        });
        assertEquals ("{\n" +
                "  \"messages\": \"zookeeper has been started successfuly on 192.168.10.11.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.startService("zookeeper", "192.168.10.11"));

        sac.setSystemService(new SystemService(false) {
            @Override
            public void startService(String service, String ipAddress) throws SSHCommandException {
                throw new SSHCommandException("Test Error");
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
            public void stopService(String service, String ipAddress) throws SSHCommandException {
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
            public void restartService(String service, String ipAddress) throws SSHCommandException {
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
            public void callCommand(String commandId, String serviceName, String ipAddress) throws SSHCommandException, MarathonException {
                // No Op
            }
        });
        assertEquals ("{\n" +
                "  \"messages\": \"command show_log for zookeeper has been executed successfuly on 192.168.10.11.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", sac.serviceActionCustom("show_log", "zookeeper", "192.168.10.11"));
    }

    @Test
    public void testReinstallService() throws Exception {

        AtomicBoolean called = new AtomicBoolean(false);

        sac.setSystemService(new SystemService(false) {
            @Override
            public boolean isProcessingPending() {
                return false;
            }
            @Override
            public void delegateApplyNodesConfig(OperationsCommand command)  {
                // No Op
            }

        });

        sac.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
                ServicesInstallStatusWrapper retWrapper = StandardSetupHelpers.getStandard2NodesInstallStatus();
                assertTrue(retWrapper.getValueForPathAsString("zookeeper_installed_on_IP_192-168-10-13").equals("OK"));
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

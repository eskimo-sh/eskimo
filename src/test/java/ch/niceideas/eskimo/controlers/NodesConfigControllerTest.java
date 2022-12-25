package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.SimpleOperationCommand;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.test.infrastructure.HttpObjectsHelper;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import ch.niceideas.eskimo.test.services.ConnectionManagerServiceTestImpl;
import ch.niceideas.eskimo.test.services.SSHCommandServiceTestImpl;
import ch.niceideas.eskimo.test.services.SetupServiceTestImpl;
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
@ActiveProfiles({"no-web-stack", "test-system", "test-setup", "test-conf", "test-connection-manager", "test-ssh"})
public class NodesConfigControllerTest {

    @Autowired
    private NodesConfigController ncc;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private SetupServiceTestImpl setupServiceTest;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Autowired
    private ConnectionManagerServiceTestImpl connectionManagerServiceTest;

    @Autowired
    private SSHCommandServiceTestImpl sshCommandServiceTest;

    @BeforeEach
    public void testSetup() {

        if (operationsMonitoringService.isProcessingPending()) {
            operationsMonitoringService.operationsFinished(true);
        }

        configurationServiceTest.setStandard2NodesSetup();
        configurationServiceTest.setStandard2NodesInstallStatus();

        SecurityContextHelper.loginAdmin();

        connectionManagerServiceTest.dontConnect();

        ncc.setDemoMode(false);

        sshCommandServiceTest.setNodeResultBuilder((node, script) -> {
            if (script.equals("echo OK")) {
                return "OK";
            }
            if (script.equals("if [[ -f /etc/debian_version ]]; then echo debian; fi")) {
                return "debian";
            }
            if (script.endsWith("cat /proc/meminfo | grep MemTotal")) {
                return "MemTotal:        9982656 kB";
            }
            return null;
        });
    }

    @Test
    public void testLoadNodesConfig() {

        setupServiceTest.setSetupCompleted();

        //System.err.println (ncc.loadNodesConfig());

        assertTrue (new JSONObject("{\n" +
                "    \"kube-master\": \"1\",\n" +
                "    \"etcd1\": \"on\",\n" +
                "    \"ntp1\": \"on\",\n" +
                "    \"zookeeper\": \"2\",\n" +
                "    \"etcd2\": \"on\",\n" +
                "    \"gluster1\": \"on\",\n" +
                "    \"ntp2\": \"on\",\n" +
                "    \"node_id1\": \"192.168.10.11\",\n" +
                "    \"kube-slave1\": \"on\",\n" +
                "    \"kube-slave2\": \"on\",\n" +
                "    \"node_id2\": \"192.168.10.13\",\n" +
                "    \"gluster2\": \"on\"\n" +
                "}").similar(new JSONObject (ncc.loadNodesConfig())));

        setupServiceTest.setSetupError();

        assertEquals ("{\n" +
                "  \"clear\": \"setup\",\n" +
                "  \"processingPending\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", ncc.loadNodesConfig());

        setupServiceTest.setSetupCompleted();

        configurationServiceTest.setNodesConfigError();

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", ncc.loadNodesConfig());
    }

    @Test
    public void testReinstallNodesConfig() {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        assertEquals ("{\n" +
                "  \"command\": {\n" +
                "    \"restarts\": [\n" +
                "      {\"kube-master\": \"192.168.10.11\"},\n" +
                "      {\"kube-slave\": \"192.168.10.11\"},\n" +
                "      {\"kube-slave\": \"192.168.10.13\"},\n" +
                "      {\"spark-console\": \"(kubernetes)\"},\n" +
                "      {\"logstash\": \"(kubernetes)\"},\n" +
                "      {\"zeppelin\": \"(kubernetes)\"}\n" +
                "    ],\n" +
                "    \"uninstallations\": [],\n" +
                "    \"installations\": [\n" +
                "      {\"gluster\": \"192.168.10.11\"},\n" +
                "      {\"gluster\": \"192.168.10.13\"},\n" +
                "      {\"etcd\": \"192.168.10.11\"},\n" +
                "      {\"etcd\": \"192.168.10.13\"}\n" +
                "    ]\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", ncc.reinstallNodesConfig("{\"gluster\":\"on\",\"etcd\":\"on\"}", session));

        assertEquals ("{\"status\": \"OK\"}", ncc.applyNodesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }

    @Test
    public void testSaveNodesConfig_demoMode() {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        ncc.setDemoMode(true);

        assertEquals ("{\n" +
                "  \"messages\": \"Unfortunately, re-applying nodes configuration or changing nodes configuration is not possible in DEMO mode.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", ncc.applyNodesConfig(session));
    }

    @Test
    public void testSaveNodesConfig_processingPending() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        operationsMonitoringService.operationsStarted(new SimpleOperationCommand("test", "test", "192.168.10.15"));

        assertEquals ("{\n" +
                "  \"messages\": \"Some backend operations are currently running. Please retry after they are completed.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", ncc.applyNodesConfig(session));
    }

    @Test
    public void testSaveNodesConfig() {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        assertEquals ("{\n" +
                        "  \"command\": {\n" +
                        "    \"restarts\": [\n" +
                        "      {\"gluster\": \"192.168.10.11\"},\n" +
                        "      {\"gluster\": \"192.168.10.13\"},\n" +
                        "      {\"kafka\": \"(kubernetes)\"},\n" +
                        "      {\"kafka-manager\": \"(kubernetes)\"},\n" +
                        "      {\"zeppelin\": \"(kubernetes)\"}\n" +
                        "    ],\n" +
                        "    \"uninstallations\": [{\"zookeeper\": \"192.168.10.13\"}],\n" +
                        "    \"installations\": [\n" +
                        "      {\"zookeeper\": \"192.168.10.11\"},\n" +
                        "      {\"prometheus\": \"192.168.10.11\"},\n" +
                        "      {\"prometheus\": \"192.168.10.13\"}\n" +
                        "    ]\n" +
                        "  },\n" +
                        "  \"status\": \"OK\"\n" +
                        "}",
                ncc.saveNodesConfig("" +
                "{\"node_id1\":\"192.168.10.11\"," +
                "\"kube-master\":\"1\"," +
                "\"zookeeper\":\"1\"," +
                "\"gluster1\":\"on\"," +
                "\"kube-slave1\":\"on\"," +
                "\"ntp1\":\"on\"," +
                "\"etcd1\":\"on\"," +
                "\"prometheus1\":\"on\"," +
                "\"node_id2\":\"192.168.10.13\"," +
                "\"gluster2\":\"on\"," +
                "\"kube-slave2\":\"on\"," +
                "\"ntp2\":\"on\"," +
                "\"etcd2\":\"on\"," +
                "\"prometheus2\":\"on\"}", session));

        assertEquals ("{\"status\": \"OK\"}", ncc.applyNodesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }
}

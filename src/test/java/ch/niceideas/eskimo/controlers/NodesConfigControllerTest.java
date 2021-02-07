package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.OperationsCommand;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.services.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpSession;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NodesConfigControllerTest {

    private NodesConfigController ncc = new NodesConfigController();

    @BeforeEach
    public void testSetup() {
        ncc.setMessagingService(new MessagingService());
        ncc.setNotificationService(new NotificationService());

        ncc.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public boolean isProcessingPending() {
                return false;
            }
        });

        ncc.setConfigurationService(new ConfigurationService() {
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
            public void saveNodesConfig(NodesConfigWrapper nodesConfig) {
                // No Op
            }
        });
    }

    @Test
    public void testLoadNodesConfig() throws Exception {

        ncc.setSetupService(new SetupService() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                // No-Op
            }
        });

        assertTrue (new JSONObject("{\n" +
                "    \"mesos-master\": \"2\",\n" +
                "    \"marathon\": \"1\",\n" +
                "    \"zookeeper\": \"2\",\n" +
                "    \"elasticsearch1\": \"on\",\n" +
                "    \"elasticsearch2\": \"on\",\n" +
                "    \"node_id2\": \"192.168.10.13\",\n" +
                "    \"node_id1\": \"192.168.10.11\",\n" +
                "    \"logstash1\": \"on\",\n" +
                "    \"kafka2\": \"on\",\n" +
                "    \"logstash2\": \"on\",\n" +
                "    \"mesos-agent1\": \"on\",\n" +
                "    \"mesos-agent2\": \"on\",\n" +
                "    \"ntp1\": \"on\",\n" +
                "    \"kafka1\": \"on\",\n" +
                "    \"gluster1\": \"on\",\n" +
                "    \"ntp2\": \"on\",\n" +
                "    \"spark-executor1\": \"on\",\n" +
                "    \"spark-executor2\": \"on\",\n" +
                "    \"gluster2\": \"on\"\n" +
                "}").similar(new JSONObject (ncc.loadNodesConfig())));

        ncc.setSetupService(new SetupService() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                throw new SetupException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"clear\": \"setup\",\n" +
                "  \"processingPending\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", ncc.loadNodesConfig());

        ncc.setSetupService(new SetupService() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                // No-Op
            }
        });

        ncc.setConfigurationService(new ConfigurationService() {
            @Override
            public NodesConfigWrapper loadNodesConfig() throws SystemException, SetupException {
                throw new SystemException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", ncc.loadNodesConfig());
    }

    @Test
    public void testReinstallNodesConfig() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = createHttpSession(sessionContent);

        ncc.setNodesConfigurationService(new NodesConfigurationService() {
            @Override
            public void applyNodesConfig(OperationsCommand command) {
                // No Op
            }
        });

        ncc.setNodeRangeResolver(new NodeRangeResolver() {
            @Override
            public NodesConfigWrapper resolveRanges(NodesConfigWrapper rawNodesConfig) throws NodesConfigurationException {
                return rawNodesConfig;
            }
        });

        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();
        ncc.setServicesDefinition(sd);

        assertEquals ("{\n" +
                "  \"command\": {\n" +
                "    \"restarts\": [\n" +
                "      {\"marathon\": \"192.168.10.11\"},\n" +
                "      {\"mesos-agent\": \"192.168.10.11\"},\n" +
                "      {\"mesos-agent\": \"192.168.10.13\"},\n" +
                "      {\"spark-history-server\": \"(marathon)\"},\n" +
                "      {\"zeppelin\": \"(marathon)\"}\n" +
                "    ],\n" +
                "    \"uninstallations\": [],\n" +
                "    \"installations\": [\n" +
                "      {\"gluster\": \"192.168.10.11\"},\n" +
                "      {\"gluster\": \"192.168.10.13\"},\n" +
                "      {\"logstash\": \"192.168.10.11\"},\n" +
                "      {\"logstash\": \"192.168.10.13\"},\n" +
                "      {\"spark-executor\": \"192.168.10.11\"},\n" +
                "      {\"spark-executor\": \"192.168.10.13\"}\n" +
                "    ]\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", ncc.reinstallNodesConfig("{\"gluster\":\"on\",\"spark-executor\":\"on\",\"logstash\":\"on\"}", session));

        assertEquals ("{\"status\": \"OK\"}", ncc.applyNodesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }

    @Test
    public void testSaveNodesConfig_demoMode() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = createHttpSession(sessionContent);

        ncc.setDemoMode(true);

        assertEquals ("{\n" +
                "  \"messages\": \"Unfortunately, re-applying nodes configuration or changing nodes configuration is not possible in DEMO mode.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", ncc.applyNodesConfig(session));
    }

    @Test
    public void testSaveNodesConfig_processingPending() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = createHttpSession(sessionContent);

        ncc.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public boolean isProcessingPending() {
                return true;
            }
        });

        assertEquals ("{\n" +
                "  \"messages\": \"Some backend operations are currently running. Please retry after they are completed.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", ncc.applyNodesConfig(session));
    }

    @Test
    public void testSaveNodesConfig() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = createHttpSession(sessionContent);

        ncc.setNodesConfigurationService(new NodesConfigurationService() {
            @Override
            public void applyNodesConfig(OperationsCommand command) {
                // No Op
            }
        });

        ncc.setNodeRangeResolver(new NodeRangeResolver() {
            @Override
            public NodesConfigWrapper resolveRanges(NodesConfigWrapper rawNodesConfig) throws NodesConfigurationException {
                return rawNodesConfig;
            }
        });

        ncc.setNodesConfigChecker(new NodesConfigurationChecker() {
            @Override
            public void checkNodesSetup(NodesConfigWrapper nodesConfig) throws NodesConfigurationException {
                // No Op
            }
        });

        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();
        ncc.setServicesDefinition(sd);

        assertEquals ("{\n" +
                        "  \"command\": {\n" +
                        "    \"restarts\": [\n" +
                        "      {\"gluster\": \"192.168.10.11\"},\n" +
                        "      {\"gluster\": \"192.168.10.13\"},\n" +
                        "      {\"kafka\": \"192.168.10.11\"},\n" +
                        "      {\"kafka\": \"192.168.10.13\"},\n" +
                        "      {\"spark-executor\": \"192.168.10.11\"},\n" +
                        "      {\"spark-executor\": \"192.168.10.13\"},\n" +
                        "      {\"mesos-agent\": \"192.168.10.11\"},\n" +
                        "      {\"mesos-agent\": \"192.168.10.13\"},\n" +
                        "      {\"cerebro\": \"(marathon)\"},\n" +
                        "      {\"kibana\": \"(marathon)\"},\n" +
                        "      {\"kafka-manager\": \"(marathon)\"},\n" +
                        "      {\"spark-history-server\": \"(marathon)\"},\n" +
                        "      {\"zeppelin\": \"(marathon)\"}\n" +
                        "    ],\n" +
                        "    \"uninstallations\": [\n" +
                        "      {\"marathon\": \"192.168.10.11\"},\n" +
                        "      {\"mesos-master\": \"192.168.10.13\"},\n" +
                        "      {\"zookeeper\": \"192.168.10.13\"}\n" +
                        "    ],\n" +
                        "    \"installations\": [\n" +
                        "      {\"zookeeper\": \"192.168.10.11\"},\n" +
                        "      {\"prometheus\": \"192.168.10.11\"},\n" +
                        "      {\"prometheus\": \"192.168.10.13\"},\n" +
                        "      {\"mesos-master\": \"192.168.10.11\"},\n" +
                        "      {\"flink-worker\": \"192.168.10.11\"},\n" +
                        "      {\"flink-worker\": \"192.168.10.13\"},\n" +
                        "      {\"flink-app-master\": \"192.168.10.11\"}\n" +
                        "    ]\n" +
                        "  },\n" +
                        "  \"status\": \"OK\"\n" +
                        "}",
                ncc.saveNodesConfig("" +
                "{\"node_id1\":\"192.168.10.11\"," +
                "\"flink-app-master\":\"1\"," +
                "\"mesos-master\":\"1\"," +
                "\"zookeeper\":\"1\"," +
                "\"elasticsearch1\":\"on\"," +
                "\"flink-worker1\":\"on\"," +
                "\"gluster1\":\"on\"," +
                "\"kafka1\":\"on\"," +
                "\"logstash1\":\"on\"," +
                "\"mesos-agent1\":\"on\"," +
                "\"ntp1\":\"on\"," +
                "\"prometheus1\":\"on\"," +
                "\"spark-executor1\":\"on\"," +
                "\"node_id2\":\"192.168.10.13\"," +
                "\"elasticsearch2\":\"on\"," +
                "\"flink-worker2\":\"on\"," +
                "\"gluster2\":\"on\"," +
                "\"kafka2\":\"on\"," +
                "\"logstash2\":\"on\"," +
                "\"mesos-agent2\":\"on\"," +
                "\"ntp2\":\"on\"," +
                "\"prometheus2\":\"on\"," +
                "\"spark-executor2\":\"on\"}", session));

        assertEquals ("{\"status\": \"OK\"}", ncc.applyNodesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }

    public static HttpSession createHttpSession(Map<String, Object> sessionContent) {
        return (HttpSession) Proxy.newProxyInstance(
            NodesConfigController.class.getClassLoader(),
            new Class[]{HttpSession.class},
            (proxy, method, methodArgs) -> {
                switch (method.getName()) {
                    case "setAttribute":
                        return sessionContent.put((String) methodArgs[0], methodArgs[1]);
                    case "getAttribute":
                        return sessionContent.get(methodArgs[0]);
                    case "removeAttribute":
                        return sessionContent.remove(methodArgs[0]);
                    default:
                        throw new UnsupportedOperationException(
                                "Unsupported method: " + method.getName());
                }
            });
    }
}

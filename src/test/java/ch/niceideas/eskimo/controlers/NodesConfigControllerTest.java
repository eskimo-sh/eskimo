package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.OperationsCommand;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.services.*;
import org.json.JSONException;
import org.junit.Test;

import javax.servlet.http.HttpSession;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class NodesConfigControllerTest {

    private NodesConfigController ncc = new NodesConfigController();

    @Test
    public void testLoadNodesConfig() throws Exception {

        ncc.setSetupService(new SetupService() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                // No-Op
            }
        });

        ncc.setSystemService(new SystemService() {
            @Override
            public boolean isProcessingPending() {
                return false;
            }
        });

        ncc.setConfigurationService(new ConfigurationService() {
            @Override
            public NodesConfigWrapper loadNodesConfig() throws SystemException, SetupException {
                return StandardSetupHelpers.getStandard2NodesSetup();
            }
        });

        assertEquals ("{\n" +
                "    \"mesos-master\": \"2\",\n" +
                "    \"marathon\": \"2\",\n" +
                "    \"zookeeper\": \"2\",\n" +
                "    \"elasticsearch1\": \"on\",\n" +
                "    \"elasticsearch2\": \"on\",\n" +
                "    \"action_id2\": \"192.168.10.13\",\n" +
                "    \"action_id1\": \"192.168.10.11\",\n" +
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
                "}", ncc.loadNodesConfig());

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

        ncc.setSystemService(new SystemService() {
            @Override
            public boolean isProcessingPending() {
                return false;
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

        ncc.setSystemService(new SystemService() {
            @Override
            public void applyNodesConfig(OperationsCommand command)
                    throws SystemException, JSONException, ServiceDefinitionException, NodesConfigurationException {
                // No Op
            }

        });

        ncc.setConfigurationService(new ConfigurationService() {
            @Override
            public void saveServicesInstallationStatus(ServicesInstallStatusWrapper status) throws FileException, JSONException, SetupException {
                // No Op
            }
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
                return StandardSetupHelpers.getStandard2NodesStatus();
            }
            @Override
            public NodesConfigWrapper loadNodesConfig() throws SystemException, SetupException {
                return StandardSetupHelpers.getStandard2NodesSetup();
            }
            @Override
            public void saveNodesConfig(NodesConfigWrapper nodesConfig) throws FileException, JSONException, SetupException {
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
                "      {\"marathon\": \"192.168.10.13\"},\n" +
                "      {\"gdash\": \"(marathon)\"},\n" +
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

        assertEquals ("{\"status\": \"OK\" }", ncc.applyNodesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }

    @Test
    public void testSaveNodesConfig() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = createHttpSession(sessionContent);

        ncc.setSystemService(new SystemService() {
            @Override
            public void applyNodesConfig(OperationsCommand command)
                    throws SystemException, JSONException, ServiceDefinitionException, NodesConfigurationException {
                // No Op
            }
        });
        ncc.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
                return StandardSetupHelpers.getStandard2NodesStatus();
            }
            @Override
            public void saveNodesConfig(NodesConfigWrapper nodesConfig) throws FileException, JSONException, SetupException {
                // No Op
            }
            @Override
            public void saveServicesInstallationStatus(ServicesInstallStatusWrapper status) throws FileException, JSONException, SetupException {
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
            public void checkServicesConfig(NodesConfigWrapper nodesConfig) throws NodesConfigurationException {
                // No Op
            }
        });

        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();
        ncc.setServicesDefinition(sd);

        assertEquals ("{\n" +
                "  \"command\": {\n" +
                "    \"restarts\": [\n" +
                "      {\"kafka\": \"192.168.10.11\"},\n" +
                "      {\"kafka\": \"192.168.10.13\"},\n" +
                "      {\"mesos-agent\": \"192.168.10.11\"},\n" +
                "      {\"mesos-agent\": \"192.168.10.13\"},\n" +
                "      {\"spark-executor\": \"192.168.10.11\"},\n" +
                "      {\"spark-executor\": \"192.168.10.13\"},\n" +
                "      {\"kafka-manager\": \"(marathon)\"},\n" +
                "      {\"spark-history-server\": \"(marathon)\"},\n" +
                "      {\"zeppelin\": \"(marathon)\"}\n" +
                "    ],\n" +
                "    \"uninstallations\": [\n" +
                "      {\"marathon\": \"192.168.10.13\"},\n" +
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
                "}", ncc.saveNodesConfig("" +
                "{\"action_id1\":\"192.168.10.11\"," +
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
                "\"action_id2\":\"192.168.10.13\"," +
                "\"elasticsearch2\":\"on\"," +
                "\"flink-worker2\":\"on\"," +
                "\"gluster2\":\"on\"," +
                "\"kafka2\":\"on\"," +
                "\"logstash2\":\"on\"," +
                "\"mesos-agent2\":\"on\"," +
                "\"ntp2\":\"on\"," +
                "\"prometheus2\":\"on\"," +
                "\"spark-executor2\":\"on\"}", session));

        assertEquals ("{\"status\": \"OK\" }", ncc.applyNodesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }

    public static HttpSession createHttpSession(Map<String, Object> sessionContent) {
        return (HttpSession) Proxy.newProxyInstance(
            NodesConfigController.class.getClassLoader(),
            new Class[]{HttpSession.class},
            (proxy, method, methodArgs) -> {
                if (method.getName().equals("setAttribute")) {
                    return sessionContent.put ((String)methodArgs[0], methodArgs[1]);
                } else if (method.getName().equals("getAttribute")) {
                    return sessionContent.get (methodArgs[0]);
                } else if (method.getName().equals("removeAttribute")) {
                    return sessionContent.remove (methodArgs[0]);
                } else {
                    throw new UnsupportedOperationException(
                            "Unsupported method: " + method.getName());
                }
            });
    }
}

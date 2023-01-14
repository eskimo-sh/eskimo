/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.*;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationChecker;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-services", "test-setup"})
public class ServicesDefinitionTest {

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Autowired
    private NodesConfigurationChecker nodesConfigurationChecker;

    private String jsonNodesConfig = null;
    private String jsonKubernetesConfig = null;
    private String jsonMinimalConfig = null;

    private final MemoryModel emptyModel = new MemoryModel(Collections.emptyMap());

    @BeforeEach
    public void setUp() throws Exception {
        jsonNodesConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesDefinitionTest/testConfig2.json"), StandardCharsets.UTF_8);
        jsonKubernetesConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesDefinitionTest/testKubernetesConfig2.json"), StandardCharsets.UTF_8);
        jsonMinimalConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesDefinitionTest/testMinimalConfig2.json"), StandardCharsets.UTF_8);
    }

    @Test
    public void testServiceToStringNoStackOverflow() {
        assertDoesNotThrow(() ->  servicesDefinition.getService("calculator-runtime").toString());
    }

    @Test
    public void testAfterPropertiesSet() {
        assertEquals (14, servicesDefinition.listAllServices().length);
    }

    @Test
    public void testServiceHasDependency() {

        assertFalse (servicesDefinition.getService("distributed-time").hasDependency(servicesDefinition.getService("user-console")));
        assertFalse (servicesDefinition.getService("distributed-filesystem").hasDependency(servicesDefinition.getService("cluster-master")));
        assertFalse (servicesDefinition.getService("cluster-manager").hasDependency(servicesDefinition.getService("broker-manager")));
        assertFalse (servicesDefinition.getService("cluster-manager").hasDependency(servicesDefinition.getService("broker")));

        assertTrue (servicesDefinition.getService("broker").hasDependency(servicesDefinition.getService("cluster-manager")));
        assertTrue (servicesDefinition.getService("cluster-slave").hasDependency(servicesDefinition.getService("cluster-master")));
    }

    @Test
    public void testMinimalExample() throws Exception {

        Topology topology = servicesDefinition.getTopology(
                new NodesConfigWrapper(jsonMinimalConfig),
                KubernetesServicesConfigWrapper.empty(),
                "192.168.56.23");

        assertEquals ("#Topology\n" +
                "export MASTER_CLUSTER_MANAGER_1=192.168.56.21\n" +
                "export MASTER_DISTRIBUTED_TIME_1=192.168.56.21\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty()));
    }

    @Test
    public void testRealLifeExample() throws Exception {

        Topology topology = servicesDefinition.getTopology(
                new NodesConfigWrapper(jsonNodesConfig),
                new KubernetesServicesConfigWrapper(jsonKubernetesConfig),
                "192.168.10.11");

        assertEquals (
                "#Topology\n" +
                        "export MASTER_CLUSTER_MANAGER_1=192.168.10.11\n" +
                        "export MASTER_CLUSTER_MASTER_1=192.168.10.11\n" +
                        "export MASTER_DISTRIBUTED_FILESYSTEM_1=192.168.10.11\n" +
                        "export MASTER_DISTRIBUTED_TIME_1=192.168.10.11\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty()));
    }

    @Test
    public void testRealLifeExampleComplete() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(jsonNodesConfig);

        Topology topology = servicesDefinition.getTopology(
                nodesConfig,
                new KubernetesServicesConfigWrapper(jsonKubernetesConfig),
                "192.168.10.11");

        assertEquals ("#Topology\n" +
                        "export MASTER_CLUSTER_MANAGER_1=192.168.10.11\n" +
                        "export MASTER_CLUSTER_MASTER_1=192.168.10.11\n" +
                        "export MASTER_DISTRIBUTED_FILESYSTEM_1=192.168.10.11\n" +
                        "export MASTER_DISTRIBUTED_TIME_1=192.168.10.11\n" +
                        "\n" +
                        "#Eskimo installation status\n" +
                        "export ESKIMO_INSTALLED_distributed_time_1921681013=OK\n" +
                        "export ESKIMO_INSTALLED_cluster_master_1921681011=OK\n" +
                        "export ESKIMO_INSTALLED_distributed_time_1921681011=OK\n" +
                        "export ESKIMO_INSTALLED_user_console_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_distributed_filesystem_1921681013=OK\n" +
                        "export ESKIMO_INSTALLED_distributed_filesystem_1921681011=OK\n" +
                        "export ESKIMO_INSTALLED_cluster_manager_1921681013=OK\n" +
                        "export ESKIMO_INSTALLED_database_manager_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_cluster_slave_1921681013=OK\n" +
                        "export ESKIMO_INSTALLED_broker_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_cluster_slave_1921681011=OK\n" +
                        "export ESKIMO_INSTALLED_database_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_calculator_runtime_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_broker_manager_KUBERNETES_NODE=OK\n" +
                        "\n" +
                        "#Additional Environment\n" +
                        "export NODE_NBR_CLUSTER_MANAGER_1921681011=1\n" +
                        "export ALL_NODES_LIST_cluster_slave=192.168.10.11,192.168.10.12,192.168.10.13\n" +
                        "export NODE_NBR_CLUSTER_SLAVE_1921681011=1\n" +
                        "export NODE_NBR_CLUSTER_SLAVE_1921681013=3\n" +
                        "export NODE_NBR_CLUSTER_SLAVE_1921681012=2\n" +
                        "export ALL_NODES_LIST_distributed_filesystem=192.168.10.11,192.168.10.12\n" +
                        "\n" +
                        "#Self identification\n" +
                        "export SELF_IP_ADDRESS=192.168.10.11\n" +
                        "export SELF_NODE_NUMBER=1\n" +
                        "export ESKIMO_NODE_COUNT=3\n" +
                        "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13\n" +
                        "\n" +
                        "#Kubernetes Topology\n",
                topology.getTopologyScriptForNode(nodesConfig,
                        new KubernetesServicesConfigWrapper(jsonKubernetesConfig),
                        StandardSetupHelpers.getStandard2NodesTestInstallStatus(), servicesDefinition, emptyModel, 1));
    }

    @Test
    public void testRealLifeExampleSingleNodes() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("cluster-manager", "1");
            put("cluster-master", "1");
            put("cluster-slave1", "on");
            put("distributed-time", "1");
        }});

        KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(new HashMap<>() {{
            
            put("database-manager_install", "on");

            put("user-console_install", "on");
            
            put("database_install", "on");
            put("broker_install", "on");
            put("calculator-runtime_install", "on");
        }});

        Topology topology = servicesDefinition.getTopology(
                nodesConfig,
                kubeServicesConfig,
                "192.168.10.11");

        assertEquals ("#Topology\n" +
                        "export MASTER_CLUSTER_MANAGER_1=192.168.10.11\n" +
                        "export MASTER_CLUSTER_MASTER_1=192.168.10.11\n" +
                        "export MASTER_DISTRIBUTED_TIME_1=192.168.10.11\n" +
                        "\n" +
                        "#Eskimo installation status\n" +
                        "export ESKIMO_INSTALLED_distributed_time_1921681013=OK\n" +
                        "export ESKIMO_INSTALLED_cluster_master_1921681011=OK\n" +
                        "export ESKIMO_INSTALLED_distributed_time_1921681011=OK\n" +
                        "export ESKIMO_INSTALLED_user_console_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_distributed_filesystem_1921681013=OK\n" +
                        "export ESKIMO_INSTALLED_distributed_filesystem_1921681011=OK\n" +
                        "export ESKIMO_INSTALLED_cluster_manager_1921681013=OK\n" +
                        "export ESKIMO_INSTALLED_database_manager_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_cluster_slave_1921681013=OK\n" +
                        "export ESKIMO_INSTALLED_broker_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_cluster_slave_1921681011=OK\n" +
                        "export ESKIMO_INSTALLED_database_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_calculator_runtime_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_broker_manager_KUBERNETES_NODE=OK\n" +
                        "\n" +
                        "#Additional Environment\n" +
                        "export NODE_NBR_CLUSTER_MANAGER_1921681011=1\n" +
                        "export ALL_NODES_LIST_cluster_slave=192.168.10.11\n" +
                        "export NODE_NBR_CLUSTER_SLAVE_1921681011=1\n" +
                        "\n" +
                        "#Self identification\n" +
                        "export SELF_IP_ADDRESS=192.168.10.11\n" +
                        "export SELF_NODE_NUMBER=1\n" +
                        "export ESKIMO_NODE_COUNT=1\n" +
                        "export ALL_NODES_LIST=192.168.10.11\n" +
                        "\n" +
                        "#Kubernetes Topology\n",
                topology.getTopologyScriptForNode(nodesConfig,
                        new KubernetesServicesConfigWrapper (jsonKubernetesConfig),
                        StandardSetupHelpers.getStandard2NodesTestInstallStatus(), servicesDefinition, emptyModel, 1));
    }

    @Test
    public void testListServicesOrderedByDependencies() {

        String[] orderedServices = servicesDefinition.listServicesOrderedByDependencies();

        assertEquals(14, orderedServices.length);

        assertTrue (orderedServices[0].equals("cluster-manager")
                || orderedServices[0].equals("distributed-time")
                || orderedServices[0].equals("broker-cli"));

        assertTrue (orderedServices[orderedServices.length - 1].equals("user-console")
                || orderedServices[orderedServices.length - 1].equals("calculator-runtime"));

    }

    @Test
    public void testListServicesInOrder() {

        String[] orderedServices = servicesDefinition.listServicesInOrder();

        assertEquals(14, orderedServices.length, String.join(",", orderedServices));

        assertArrayEquals(new String[] {
                "distributed-time",
                "cluster-manager",
                "distributed-filesystem",
                "cluster-master",
                "cluster-slave",
                "cluster-dashboard",
                "broker",
                "broker-cli",
                "broker-manager",
                "calculator-runtime",
                "calculator-cli",
                "database-manager",
                "database",
                "user-console"
        }, orderedServices);
    }

    @Test
    public void testListUniqueServices() {

        String[] orderedServices = servicesDefinition.listUniqueServices();

        assertEquals(2, orderedServices.length);

        assertArrayEquals(new String[] {
                "cluster-manager",
                "cluster-master"
        }, orderedServices);
    }

    @Test
    public void testListKubernetesServices() {
        String[] kubernetesServices = servicesDefinition.listKubernetesServices();

        assertEquals(7, kubernetesServices.length);

        assertArrayEquals(new String[] {
                "broker",
                "broker-manager",
                "calculator-runtime",
                "cluster-dashboard",
                "database",
                "database-manager",
                "user-console"
        }, kubernetesServices);
    }

    @Test
    public void testListUIServices() {

        String[] orderedServices = servicesDefinition.listUIServices();

        assertEquals(5, orderedServices.length, String.join(",", orderedServices));

        assertArrayEquals(new String[] {
                "distributed-filesystem",
                "cluster-dashboard",
                "broker-manager",
                "database-manager",
                "user-console",
        }, orderedServices);
    }

    @Test
    public void testListMultipleServices() {

        String[] orderedServices = servicesDefinition.listMultipleServicesNonKubernetes();

        assertEquals(5, orderedServices.length);

        assertArrayEquals(new String[] {
                "broker-cli",
                "calculator-cli",
                "cluster-slave",
                "distributed-filesystem",
                "distributed-time"
        }, orderedServices);
    }

    @Test
    public void testGetDependentServices() {

        String[] elasticsearchDep = servicesDefinition.getDependentServices("database").toArray(new String[0]);
        assertEquals(2, elasticsearchDep.length);
        assertArrayEquals(new String[] {
                "database-manager",
                "user-console"
        }, elasticsearchDep);

        String[] zookeeperDep = servicesDefinition.getDependentServices("cluster-manager").toArray(new String[0]);
        assertEquals(5, zookeeperDep.length, String.join(",", zookeeperDep));
        assertArrayEquals(new String[] {
                "cluster-manager",
                "broker",
                "broker-manager",
                "distributed-filesystem",
                "user-console"
        }, zookeeperDep);
    }

    @Test
    public void testZookeeperOnRange() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("cluster-manager", "1");
            put("distributed-filesystem1", "on");
            put("distributed-time1", "on");
            put("node_id2", "192.168.10.13-192.168.10.14");
            put("distributed-filesystem2", "on");
            put("distributed-time2", "on");
        }});

        KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(new HashMap<>());

        Topology topology = servicesDefinition.getTopology(
                nodeRangeResolver.resolveRanges(nodesConfig),
                kubeServicesConfig,
                "192.168.10.11");

        assertEquals ("#Topology\n" +
                        "export MASTER_CLUSTER_MANAGER_1=192.168.10.11\n" +
                        "export MASTER_DISTRIBUTED_TIME_1=192.168.10.11\n" +
                        "\n" +
                        "#Additional Environment\n" +
                        "export NODE_NBR_CLUSTER_MANAGER_1921681011=1\n" +
                        "export ALL_NODES_LIST_distributed_filesystem=192.168.10.11,192.168.10.13,192.168.10.14\n" +
                        "\n" +
                        "#Self identification\n" +
                        "export SELF_IP_ADDRESS=192.168.10.11\n" +
                        "export SELF_NODE_NUMBER=1\n" +
                        "export ESKIMO_NODE_COUNT=3\n" +
                        "export ALL_NODES_LIST=192.168.10.11,192.168.10.13,192.168.10.14\n",
                topology.getTopologyScriptForNode(
                        nodeRangeResolver.resolveRanges(nodesConfig),
                        StandardSetupHelpers.getStandardKubernetesConfig(),
                        ServicesInstallStatusWrapper.empty(), servicesDefinition, emptyModel, 1));
    }

    @Test
    public void testEditableConfiguration() throws Exception {

        Service sparkService = servicesDefinition.getService("calculator-runtime");
        assertNotNull(sparkService);

        List<EditableSettings> confs = sparkService.getEditableSettings();
        assertNotNull(confs);
        assertEquals(1, confs.size());

        EditableSettings conf = confs.get(0);
        assertNotNull(conf);

        assertEquals("calculator-defaults.conf", conf.getFilename());
        assertEquals (EditablePropertyType.VARIABLE, conf.getPropertyType());
        assertEquals ("{name}={value}", conf.getPropertyFormat());
        assertEquals("#", conf.getCommentPrefix());

        List<EditableProperty> props = conf.getProperties();
        assertNotNull(props);

        assertEquals(10, props.size());

        EditableProperty firstProp = props.get(0);
        assertNotNull(firstProp);

        assertEquals("calculator.driver.memory", firstProp.getName());
        assertEquals("Limiting the driver (client) memory", firstProp.getComment());
        assertEquals("800m", firstProp.getDefaultValue());

        EditableProperty lastProp = props.get(7);
        assertNotNull(lastProp);

        assertEquals("calculator.dynamicAllocation.shuffleTracking.timeout", lastProp.getName());
        assertEquals("When shuffle tracking is enabled, controls the timeout for executors that are holding shuffle data - should be consistent with spark.dynamicAllocation.cachedExecutorIdleTimeout.", lastProp.getComment());
        assertEquals("300s", lastProp.getDefaultValue());

        String expectedServicesConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesDefinitionTest/expectedServicesConfig.json"), StandardCharsets.UTF_8);

        assertEquals(
                expectedServicesConfig.replace("\r", "").trim(),
                conf.toJSON().toString(2).replace("\r", "").trim());
        //assertTrue (new JsonWrapper(expectedServicesConfig).getJSONObject().similar(conf.toJSON()));
    }

    @Test
    public void testCommandFrameworkDefinition() throws Exception {
        Service ntp = servicesDefinition.getService("distributed-time");
        assertNotNull (ntp.getCommands());
        assertEquals (1, ntp.getCommands().size());

        Command logCommand = ntp.getCommands().get(0);
        assertNotNull (logCommand);
        assertEquals ("show_log", logCommand.getId());
        assertEquals ("Show Logs", logCommand.getName());
        assertEquals ("fa-file", logCommand.getIcon());

        assertEquals ("{\n" +
                "  \"name\": \"Show Logs\",\n" +
                "  \"icon\": \"fa-file\",\n" +
                "  \"id\": \"show_log\"\n" +
                "}", logCommand.toStatusConfigJSON().toString(2));

        AtomicReference<String> callRef = new AtomicReference<>();
        logCommand.call("192.168.10.11", new SSHCommandServiceImpl() {
            public String runSSHCommand(String node, String command) {
                callRef.set(node + "-" + command);
                return callRef.get();
            }
        });
        assertNotNull(callRef.get());
        assertEquals("192.168.10.11-cat /var/log/distributed-time/distributed-time.log", callRef.get());
    }

    @Test
    public void testConditionalMandatory() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
        }});

        nodesConfigurationChecker.checkNodesSetup(nodesConfig);

        NodesConfigWrapper nodesConfig2 = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("node_id2", "192.168.10.12");
        }});

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            nodesConfigurationChecker.checkNodesSetup(nodesConfig2);
        });

        assertEquals("Inconsistency found : service distributed-filesystem is mandatory on all nodes but some nodes are lacking it.", exception.getMessage());

        NodesConfigWrapper nodesConfig3 = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("node_id2", "192.168.10.12");
            put("cluster-manager", "1");
            put("distributed-time1", "on");
            put("distributed-time2", "on");
            put("distributed-filesystem1", "on");
            put("distributed-filesystem2", "on");
        }});

        nodesConfigurationChecker.checkNodesSetup(nodesConfig3);
    }
}

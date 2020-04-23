/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.eskimo.model.*;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class ServicesDefinitionTest extends AbstractServicesDefinitionTest {

    private String jsonNodesConfig = null;
    private String jsonMarathonConfig = null;

    private MemoryModel emptyModel = new MemoryModel(Collections.emptyMap());

    @Before
    public void setUp() throws Exception {
        super.setUp();
        jsonNodesConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesDefinitionTest/testConfig.json"));
        jsonMarathonConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesDefinitionTest/testMarathonConfig.json"));
    }

    @Test
    public void testAfterPropertiesSet() throws Exception {
        assertEquals (20, def.listAllServices().length);
    }

    @Test
    public void testServiceHasDependency() {

        assertFalse (def.getService("ntp").hasDependency(def.getService("zeppelin")));
        assertFalse (def.getService("gluster").hasDependency(def.getService("mesos-master")));
        assertFalse (def.getService("zookeeper").hasDependency(def.getService("spark-history-server")));
        assertFalse (def.getService("zookeeper").hasDependency(def.getService("kafka")));

        assertTrue (def.getService("kafka").hasDependency(def.getService("zookeeper")));
        assertTrue (def.getService("mesos-master").hasDependency(def.getService("zookeeper")));
        assertTrue (def.getService("zeppelin").hasDependency(def.getService("spark-executor")));
    }

    @Test
    public void testRealLifeExample() throws Exception {

        Topology topology = def.getTopology(
                new NodesConfigWrapper(jsonNodesConfig),
                new MarathonServicesConfigWrapper(jsonMarathonConfig),
                new HashSet<>(), "192.168.10.11");

        assertEquals ("export MASTER_ELASTICSEARCH_1921681011=192.168.10.12\n" +
                "export MASTER_ELASTICSEARCH_1921681012=192.168.10.13\n" +
                "export MASTER_ELASTICSEARCH_1921681013=192.168.10.11\n" +
                "export MASTER_GLUSTER_1921681011=192.168.10.12\n" +
                "export MASTER_GLUSTER_1921681012=192.168.10.11\n" +
                "export MASTER_MARATHON_1=192.168.10.11\n" +
                "export MASTER_MESOS_MASTER_1=192.168.10.11\n" +
                "export MASTER_NTP_1=192.168.10.11\n" +
                "export MASTER_SPARK_EXECUTOR_1=192.168.10.11\n" +
                "export MASTER_ZOOKEEPER_1=192.168.10.11\n" +
                "export SELF_MASTER_ELASTICSEARCH_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_ELASTICSEARCH_1921681012=192.168.10.12\n" +
                "export SELF_MASTER_GLUSTER_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_KAFKA_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_LOGSTASH_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_MESOS_MASTER_1921681011=192.168.10.11\n", topology.getTopologyScript());
    }

    @Test
    public void testRealLifeExampleDeadIp() throws Exception {

        Topology topology = def.getTopology(
                new NodesConfigWrapper(jsonNodesConfig),
                new MarathonServicesConfigWrapper(jsonMarathonConfig),
                new HashSet<String>(){{add("192.168.10.13");}}, "192.168.10.11");

        assertEquals ("export MASTER_ELASTICSEARCH_1921681011=192.168.10.12\n" +
                "export MASTER_ELASTICSEARCH_1921681012=192.168.10.11\n" +
                "export MASTER_ELASTICSEARCH_1921681013=192.168.10.11\n" +
                "export MASTER_GLUSTER_1921681011=192.168.10.12\n" +
                "export MASTER_GLUSTER_1921681012=192.168.10.11\n" +
                "export MASTER_MARATHON_1=192.168.10.11\n" +
                "export MASTER_MESOS_MASTER_1=192.168.10.11\n" +
                "export MASTER_NTP_1=192.168.10.11\n" +
                "export MASTER_SPARK_EXECUTOR_1=192.168.10.11\n" +
                "export MASTER_ZOOKEEPER_1=192.168.10.11\n" +
                "export SELF_MASTER_ELASTICSEARCH_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_ELASTICSEARCH_1921681012=192.168.10.12\n" +
                "export SELF_MASTER_GLUSTER_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_KAFKA_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_LOGSTASH_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_MESOS_MASTER_1921681011=192.168.10.11\n", topology.getTopologyScript());
    }


    @Test
    public void testRealLifeExampleComplete() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(jsonNodesConfig);

        Topology topology = def.getTopology(
                nodesConfig,
                new MarathonServicesConfigWrapper(jsonMarathonConfig),
                new HashSet<>(), "192.168.10.11");

        assertEquals ("#Topology\n" +
                "export MASTER_ELASTICSEARCH_1921681011=192.168.10.12\n" +
                "export MASTER_ELASTICSEARCH_1921681012=192.168.10.13\n" +
                "export MASTER_ELASTICSEARCH_1921681013=192.168.10.11\n" +
                "export MASTER_GLUSTER_1921681011=192.168.10.12\n" +
                "export MASTER_GLUSTER_1921681012=192.168.10.11\n" +
                "export MASTER_MARATHON_1=192.168.10.11\n" +
                "export MASTER_MESOS_MASTER_1=192.168.10.11\n" +
                "export MASTER_NTP_1=192.168.10.11\n" +
                "export MASTER_SPARK_EXECUTOR_1=192.168.10.11\n" +
                "export MASTER_ZOOKEEPER_1=192.168.10.11\n" +
                "export SELF_MASTER_ELASTICSEARCH_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_ELASTICSEARCH_1921681012=192.168.10.12\n" +
                "export SELF_MASTER_GLUSTER_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_KAFKA_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_LOGSTASH_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_MESOS_MASTER_1921681011=192.168.10.11\n" +
                "\n" +
                "#Additional Environment\n" +
                "export NODE_NBR_KAFKA_1921681011=0\n" +
                "export NODE_NBR_KAFKA_1921681012=1\n" +
                "export NODE_NBR_KAFKA_1921681013=2\n" +
                "export NODE_NBR_ZOOKEEPER_1921681011=1\n" +
                "\n" +
                "#Self identification\n" +
                "export SELF_IP_ADDRESS=192.168.10.11\n" +
                "export SELF_NODE_NUMBER=1\n", topology.getTopologyScriptForNode(nodesConfig, emptyModel, 1));

    }

    @Test
    public void testRealLifeExampleSingleNodes() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("action_id1", "192.168.10.11");
            put("zookeeper", "1");
            put("mesos-master", "1");
            put("marathon", "1");
            put("mesos-agent1", "on");
            put("ntp", "1");
            put("elasticsearch1", "on");
            put("spark-executor1", "on");
            put("kafka1", "on");
            put("logstash1", "on");
        }});

        MarathonServicesConfigWrapper marathonConfig = new MarathonServicesConfigWrapper(new HashMap<String, Object>() {{
            put("spark-history-server_install", "on");
            put("cerebro_install", "on");
            put("kibana_install", "on");
            put("zeppelin_install", "on");
        }});

        Topology topology = def.getTopology(
                nodesConfig,
                marathonConfig,
                new HashSet<>(), "192.168.10.11");

        assertEquals ("#Topology\n" +
                "export MASTER_MARATHON_1=192.168.10.11\n" +
                "export MASTER_MESOS_MASTER_1=192.168.10.11\n" +
                "export MASTER_NTP_1=192.168.10.11\n" +
                "export MASTER_SPARK_EXECUTOR_1=192.168.10.11\n" +
                "export MASTER_ZOOKEEPER_1=192.168.10.11\n" +
                "export SELF_MASTER_ELASTICSEARCH_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_LOGSTASH_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_MESOS_MASTER_1921681011=192.168.10.11\n" +
                "\n" +
                "#Additional Environment\n" +
                "export NODE_NBR_KAFKA_1921681011=0\n" +
                "export NODE_NBR_ZOOKEEPER_1921681011=1\n" +
                "\n" +
                "#Self identification\n" +
                "export SELF_IP_ADDRESS=192.168.10.11\n" +
                "export SELF_NODE_NUMBER=1\n", topology.getTopologyScriptForNode(nodesConfig, emptyModel, 1));

    }

    @Test
    public void testListServicesOrderedByDependencies() throws Exception {

        String[] orderedServices = def.listServicesOrderedByDependencies();

        assertEquals(20, orderedServices.length);

        assertTrue (orderedServices[0].equals("zookeeper")
                || orderedServices[0].equals("ntp")
                || orderedServices[0].equals("prometheus")
                || orderedServices[0].equals("gluster"));

        assertTrue (orderedServices[orderedServices.length - 1].equals("zeppelin")
                || orderedServices[orderedServices.length - 1].equals("spark-executor"));

    }

    @Test
    public void testListServicesInOrder() throws Exception {

        String[] orderedServices = def.listServicesInOrder();

        assertEquals(String.join(",", orderedServices), 20, orderedServices.length);

        assertArrayEquals(new String[] {
                "ntp",
                "zookeeper",
                "prometheus",
                "grafana",
                "gluster",
                "gdash",
                "mesos-master",
                "mesos-agent",
                "marathon",
                "kafka",
                "kafka-manager",
                "spark-history-server",
                "spark-executor",
                "flink-app-master",
                "flink-worker",
                "logstash",
                "cerebro",
                "elasticsearch",
                "kibana",
                "zeppelin"
        }, orderedServices);
    }

    @Test
    public void testListUniqueServices() throws Exception {

        String[] orderedServices = def.listUniqueServices();

        assertEquals(4, orderedServices.length);

        assertArrayEquals(new String[] {
                "flink-app-master",
                "marathon",
                "mesos-master",
                "zookeeper"
        }, orderedServices);
    }

    @Test
    public void testListMarathonServices() throws Exception {
        String[] marathonServices = def.listMarathonServices();

        assertEquals(7, marathonServices.length);

        assertArrayEquals(new String[] {
                "cerebro",
                "gdash",
                "grafana",
                "kafka-manager",
                "kibana",
                "spark-history-server",
                "zeppelin"
        }, marathonServices);
    }

    @Test
    public void testListUIServices() throws Exception {

        String[] orderedServices = def.listUIServices();

        assertEquals(String.join(",", orderedServices), 10, orderedServices.length);

        assertArrayEquals(new String[] {
                "grafana",
                "gdash",
                "mesos-master",
                "marathon",
                "kafka-manager",
                "spark-history-server",
                "flink-app-master",
                "cerebro",
                "kibana",
                "zeppelin"
        }, orderedServices);
    }

    @Test
    public void testListMultipleServices() throws Exception {

        String[] orderedServices = def.listMultipleServices();

        assertEquals(9, orderedServices.length);

        assertArrayEquals(new String[] {
                "elasticsearch",
                "flink-worker",
                "gluster",
                "kafka",
                "logstash",
                "mesos-agent",
                "ntp",
                "prometheus",
                "spark-executor"
        }, orderedServices);
    }

    @Test
    public void testGetDependentServices() throws Exception {

        String[] elasticsearchDep = def.getDependentServices("elasticsearch").toArray(new String[0]);
        assertEquals(5, elasticsearchDep.length);
        assertArrayEquals(new String[] {
                "elasticsearch",
                "cerebro",
                "kibana",
                "logstash",
                "zeppelin"
        }, elasticsearchDep);

        String[] zookeeperDep = def.getDependentServices("zookeeper").toArray(new String[0]);
        assertEquals(String.join(",", zookeeperDep), 11, zookeeperDep.length);
        assertArrayEquals(new String[] {
                "zookeeper",
                "flink-app-master",
                "flink-worker",
                "kafka",
                "kafka-manager",
                "mesos-master",
                "marathon",
                "mesos-agent",
                "spark-executor",
                "spark-history-server",
                "zeppelin"
        }, zookeeperDep);

        System.err.println (def.getDependentServices("zookeeper").toArray(new String[0]));
    }

    @Test
    public void testZookeeperOnRange() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("action_id1", "192.168.10.11");
            put("zookeeper", "1");
            put("gluster1", "on");
            put("ntp1", "on");
            put("prometheus1", "on");
            put("action_id2", "192.168.10.13-192.168.10.14");
            put("gluster2", "on");
            put("ntp2", "on");
            put("prometheus2", "on");
        }});

        MarathonServicesConfigWrapper marathonConfig = new MarathonServicesConfigWrapper(new HashMap<String, Object>() {{
        }});

        Topology topology = def.getTopology(
                nrr.resolveRanges(nodesConfig),
                marathonConfig,
                new HashSet<>(), "192.168.10.11");

        assertEquals ("#Topology\n" +
                "export MASTER_GLUSTER_1921681011=192.168.10.13\n" +
                "export MASTER_GLUSTER_1921681013=192.168.10.14\n" +
                "export MASTER_GLUSTER_1921681014=192.168.10.11\n" +
                "export MASTER_NTP_1=192.168.10.11\n" +
                "export MASTER_PROMETHEUS_1=192.168.10.11\n" +
                "export MASTER_ZOOKEEPER_1=192.168.10.11\n" +
                "\n" +
                "#Additional Environment\n" +
                "export ALL_NODES_LIST_prometheus=192.168.10.11,192.168.10.13,192.168.10.14\n" +
                "export NODE_NBR_ZOOKEEPER_1921681011=1\n" +
                "\n" +
                "#Self identification\n" +
                "export SELF_IP_ADDRESS=192.168.10.11\n" +
                "export SELF_NODE_NUMBER=1\n", topology.getTopologyScriptForNode(nrr.resolveRanges(nodesConfig), emptyModel, 1));
    }

    @Test
    public void testEditableConfiguration() throws Exception {

        Service sparkService = def.getService("spark-executor");
        assertNotNull(sparkService);

        List<EditableConfiguration> confs = sparkService.getEditableConfigurations();
        assertNotNull(confs);
        assertEquals(1, confs.size());

        EditableConfiguration conf = confs.get(0);
        assertNotNull(conf);

        assertEquals("spark-defaults.conf", conf.getFilename());
        assertEquals (EditablePropertyType.VARIABLE, conf.getPropertyType());
        assertEquals ("{name}={value}", conf.getPropertyFormat());
        assertEquals("#", conf.getCommentPrefix());

        List<EditableProperty> props = conf.getProperties();
        assertNotNull(props);

        assertEquals(8, props.size());

        EditableProperty firstProp = props.get(0);
        assertNotNull(firstProp);

        assertEquals("spark.driver.memory", firstProp.getName());
        assertEquals("Limiting the driver (client) memory", firstProp.getComment());
        assertEquals("800m", firstProp.getDefaultValue());

        EditableProperty lastProp = props.get(7);
        assertNotNull(lastProp);

        assertEquals("spark.executor.memory", lastProp.getName());
        assertEquals("Defining default Spark executor memory allowed by Eskimo Memory Management (found in topology). \n" +
                "USE [ESKIMO_DEFAULT] to leave untouched or e.g. 800m, 1.2g, etc.", lastProp.getComment());
        assertEquals("[ESKIMO_DEFAULT]", lastProp.getDefaultValue());

        String expectedServicesConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesDefinitionTest/expectedServicesConfig.json"));

        assertEquals(expectedServicesConfig, conf.toJSON().toString(2));
    }
}

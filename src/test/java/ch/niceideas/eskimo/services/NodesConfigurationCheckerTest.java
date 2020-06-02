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

import ch.niceideas.eskimo.model.NodesConfigWrapper;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class NodesConfigurationCheckerTest {

    private NodesConfigurationChecker nodeConfigChecker = new NodesConfigurationChecker();

    @Before
    public void setUp() throws Exception {

        ServicesDefinition def = new ServicesDefinition();
        def.afterPropertiesSet();
        nodeConfigChecker.setServicesDefinition(def);
    }

    @Test
    public void testRangeOfIps() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11-192.168.10.15");
            put("ntp1", "on");
            put("prometheus1", "on");
        }});

        nodeConfigChecker.checkNodesSetup(nodesConfig);
    }

    @Test
    public void testNotAnIpAddress() throws Exception {

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "blabla");
                put("ntp1", "on");
                put("prometheus1", "on");
            }});

            nodeConfigChecker.checkNodesSetup(nodesConfig);
        });

        assertEquals("Node 1 has IP configured as blabla which is not an IP address or a range.", exception.getMessage());
    }

    @Test
    public void testCheckNodesSetupMultipleOK() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("node_id2", "192.168.10.12");
                put("marathon", "2");
                put("elasticsearch1", "on");
                put("elasticsearch2", "on");
                put("kafka1", "on");
                put("kafka2", "on");
                put("ntp1", "on");
                put("ntp2", "on");
                put("prometheus1", "on");
                put("prometheus2", "on");
                put("gluster1", "on");
                put("gluster2", "on");
                put("logstash1", "on");
                put("logstash2", "on");
                put("mesos-agent1", "on");
                put("mesos-agent2", "on");
                put("mesos-master", "1");
                put("spark-executor1", "on");
                put("spark-executor2", "on");
                put("zookeeper", "1");
        }});

        nodeConfigChecker.checkNodesSetup(nodesConfig);
    }

    @Test
    public void testCheckNodesSetupMultipleOKWithRange() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("node_id2", "192.168.10.12-192.160.10.15");
                put("marathon", "1");
                put("elasticsearch1", "on");
                put("elasticsearch2", "on");
                put("kafka1", "on");
                put("kafka2", "on");
                put("ntp1", "on");
                put("ntp2", "on");
                put("prometheus1", "on");
                put("prometheus2", "on");
                put("gluster1", "on");
                put("gluster2", "on");
                put("logstash1", "on");
                put("logstash2", "on");
                put("mesos-agent1", "on");
                put("mesos-agent2", "on");
                put("mesos-master", "1");
                put("spark-executor1", "on");
                put("spark-executor2", "on");
                put("zookeeper", "1");
        }});

        nodeConfigChecker.checkNodesSetup(nodesConfig);
    }

    @Test
    public void testCheckNodesSetupSingleOK() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("marathon", "1");
                put("elasticsearch1", "on");
                put("kafka1", "on");
                put("ntp1", "on");
                put("prometheus1", "on");
                put("logstash1", "on");
                put("mesos-agent1", "on");
                put("mesos-master", "1");
                put("spark-executor1", "on");
                put("zookeeper", "1");
        }});

        nodeConfigChecker.checkNodesSetup(nodesConfig);
    }

    @Test
    public void testUniqueServiceOnRange() throws Exception {

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                    put("node_id1", "192.168.10.11");
                    put("node_id2", "192.168.10.12-192.160.10.15");
                    put("marathon", "2");
                    put("elasticsearch1", "on");
                    put("elasticsearch2", "on");
                    put("kafka1", "on");
                    put("kafka2", "on");
                    put("ntp1", "on");
                    put("ntp2", "on");
                    put("prometheus1", "on");
                    put("prometheus2", "on");
                    put("gluster1", "on");
                    put("gluster2", "on");
                    put("logstash1", "on");
                    put("logstash2", "on");
                    put("mesos-agent1", "on");
                    put("mesos-agent2", "on");
                    put("mesos-master", "1");
                    put("spark-executor1", "on");
                    put("spark-executor2", "on");
                    put("zookeeper", "1");
            }});

            nodeConfigChecker.checkNodesSetup(nodesConfig);
        });

        assertEquals("Node 2 is a range an declares service marathon which is a unique service, hence forbidden on a range.", exception.getMessage());
    }

    @Test
    public void testMissingGluster() throws Exception {

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                    put("node_id1", "192.168.10.11");
                    put("node_id2", "192.168.10.12");
                    put("marathon", "2");
                    put("elasticsearch1", "on");
                    put("elasticsearch2", "on");
                    put("kafka1", "on");
                    put("kafka2", "on");
                    put("ntp1", "on");
                    put("ntp2", "on");
                    put("logstash1", "on");
                    put("logstash2", "on");
                    put("mesos-agent1", "on");
                    put("mesos-agent2", "on");
                    put("mesos-master", "1");
                    put("spark-executor1", "on");
                    put("spark-executor2", "on");
                    put("zookeeper", "1");
            }});

            nodeConfigChecker.checkNodesSetup(nodesConfig);
        });

        assertEquals("Inconsistency found : service gluster is mandatory on all nodes but some nodes are lacking it.", exception.getMessage());
    }

    @Test
    public void testNoIPConfigured() throws Exception {

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                    put("node_id1", "");
            }});

            nodeConfigChecker.checkNodesSetup(nodesConfig);
        });

        assertEquals("Node 1 has no IP configured.", exception.getMessage());
    }

    @Test
    public void testKeyGreaterThanNodeNumber() throws Exception {

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                        put("node_id2", "192.168.10.11");
            }});

            nodeConfigChecker.checkNodesSetup(nodesConfig);
        });

        assertEquals("Inconsistency found : got key node_id2 which is greater than node number 1", exception.getMessage());
    }

    @Test
    public void testNoPrometheus() throws Exception {

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                    put("node_id1", "192.168.10.11");
                    put("ntp1", "on");
            }});

            nodeConfigChecker.checkNodesSetup(nodesConfig);
        });

        assertEquals("Inconsistency found : service prometheus is mandatory on all nodes but some nodes are lacking it.", exception.getMessage());
    }

    @Test
    public void testTwoSparkNodesAndNoGLuster() throws Exception {

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                    put("node_id1", "192.168.10.11");
                    put("node_id2", "192.168.10.12");
                    put("ntp1", "on");
                    put("ntp2", "on");
                    put("prometheus1", "on");
                    put("prometheus2", "on");
                    put("spark-executor1", "on");
                    put("spark-executor2", "on");
            }});

            nodeConfigChecker.checkNodesSetup(nodesConfig);
        });

        assertEquals("Inconsistency found : service gluster is mandatory on all nodes but some nodes are lacking it.", exception.getMessage());
    }

    @Test
    public void testGlusterNoMoreDisabledOnSingleNode() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("marathon", "1");
            put("elasticsearch1", "on");
            put("kafka1", "on");
            put("ntp1", "on");
            put("prometheus1", "on");
            put("gluster1", "on");
            put("logstash1", "on");
            put("mesos-agent1", "on");
            put("mesos-master", "1");
            put("spark-executor1", "on");
            put("zookeeper", "1");
        }});

        nodeConfigChecker.checkNodesSetup(nodesConfig);
    }

    @Test
    public void testNoGlusterOnSingleNode() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("marathon", "1");
            put("elasticsearch1", "on");
            put("kafka1", "on");
            put("ntp1", "on");
            put("prometheus1", "on");
            put("logstash1", "on");
            put("mesos-agent1", "on");
            put("mesos-master", "1");
            put("spark-executor1", "on");
            put("zookeeper", "1");
        }});

        nodeConfigChecker.checkNodesSetup(nodesConfig);
    }

    @Test
    public void testSparkButNoMesosAgent() throws Exception {

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                //put("mesos-agent1", "on");
                put("mesos-master", "1");
                put("spark-executor1", "on");
                put("marathon", "1");
                put("ntp1", "on");
                put("prometheus1", "on");
                put("zookeeper", "1");
            }});

            nodeConfigChecker.checkNodesSetup(nodesConfig);
        });

        assertEquals("Inconsistency found : Service spark-executor was expecting a service mesos-agent on same node, but none were found !", exception.getMessage());
    }

    @Test
    public void testMesosAgentButNoMesosMaster() throws Exception {

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("mesos-agent1", "on");
                //put("mesos-master", "1");
                put("spark-executor1", "on");
                put("zookeeper", "1");
                put("ntp1", "on");
                put("prometheus1", "on");
            }});

            nodeConfigChecker.checkNodesSetup(nodesConfig);
        });

        assertEquals("Inconsistency found : Service mesos-agent expects 1 mesos-master instance(s). But only 0 has been found !", exception.getMessage());
    }

    @Test
    public void testMesosMasterButNoZookeeper() throws Exception {

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("mesos-agent1", "on");
                put("mesos-master", "1");
                put("spark-executor1", "on");
                put("ntp1", "on");
                put("prometheus1", "on");
                //put("zookeeper", "1");
            }});

            nodeConfigChecker.checkNodesSetup(nodesConfig);
        });

        assertEquals("Inconsistency found : Service mesos-agent expects 1 zookeeper instance(s). But only 0 has been found !", exception.getMessage());
    }

    @Test
    public void testFlinkAndZookeeperSeparatedIsNowOK() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("node_id2", "192.168.10.12");
            put("ntp1", "on");
            put("ntp2", "on");
            put("mesos-agent1", "on");
            put("mesos-agent2", "on");
            put("prometheus1", "on");
            put("prometheus2", "on");
            put("gluster1", "on");
            put("gluster2", "on");
            put("mesos-master", "1");
            put("zookeeper", "1");
            put("flink-worker1", "on");
            put("flink-worker2", "on");
            put("flink-app-master", "2");
        }});

        nodeConfigChecker.checkNodesSetup(nodesConfig);
    }

    @Test
    public void testNoMarathonServiceCanBeSelected() throws Exception {
        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("node_id2", "192.168.10.12");
                put("ntp1", "on");
                put("ntp2", "on");
                put("mesos-agent1", "on");
                put("mesos-agent2", "on");
                put("prometheus1", "on");
                put("prometheus2", "on");
                put("gluster1", "on");
                put("gluster2", "on");
                put("mesos-master", "1");
                put("zookeeper", "1");
                put("cerebro", "2");
            }});

            nodeConfigChecker.checkNodesSetup(nodesConfig);

        });

        assertEquals("Inconsistency found : service cerebro is a marathon service which should not be selectable here.", exception.getMessage());
    }

}

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
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MemoryComputerTest {

    private MemoryComputer memoryComputer = null;

    private ServicesDefinition servicesDefinition;

    private String nodesConfigString = null;
    private String kubeServicesConfigString = null;

    @BeforeEach
    public void setUp() throws Exception {
        nodesConfigString =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesDefinitionTest/testConfig.json"));
        kubeServicesConfigString =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesDefinitionTest/testKubernetesConfig.json"));

        servicesDefinition = new ServicesDefinition();
        servicesDefinition.afterPropertiesSet();

        memoryComputer = new MemoryComputer();
        memoryComputer.setServicesDefinition(servicesDefinition);

        memoryComputer.setSshCommandService(new SSHCommandService() {
            @Override
            public String runSSHScript(String node, String script, boolean throwsException) throws SSHCommandException {
                switch (node) {
                    case "192.168.10.11":
                        return "MemTotal:        5969796 kB";
                    case "192.168.10.12":
                        return "MemTotal:        5799444 kB";
                    default:
                        return "MemTotal:        3999444 kB";
                }
            }
            @Override
            public String runSSHCommand(String node, String command) throws SSHCommandException {
                return null;
            }
            @Override
            public void copySCPFile(String node, String filePath) throws SSHCommandException {
                // just do nothing
            }
        });
    }

    @Test
    public void testComputeMemory_singleNode() throws Exception {
        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(nodesConfigString);
        nodesConfig.keySet().stream()
                .filter(key -> key.contains ("192-168-10-13")
                        || key.contains ("192-168-10-12")
                        || (""+nodesConfig.getValueForPath(key)).contains("192.168.10.12")
                        || (""+nodesConfig.getValueForPath(key)).contains("192.168.10.13"))
                .forEach(nodesConfig::removeRootKey);
        KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(kubeServicesConfigString);

        Map<String, Map<String, Long>> res = memoryComputer.computeMemory(nodesConfig, kubeServicesConfig, new HashSet<>());

        assertNotNull(res);
        assertEquals(1, res.size());

        Map<String, Long> memmModel1 = res.get("192.168.10.11");
        assertNotNull(memmModel1);
        assertEquals(11, memmModel1.size());

        assertEquals(Long.valueOf(178), memmModel1.get("logstash"));
        assertEquals(Long.valueOf(534), memmModel1.get("elasticsearch"));
        assertEquals(Long.valueOf(534), memmModel1.get("spark-runtime"));
        assertNull(memmModel1.get("flink-runtime")); // flink is not configured
        assertNull(memmModel1.get("grafana")); // grafana is not configured
        assertEquals(Long.valueOf(356), memmModel1.get("kibana"));
        assertEquals(Long.valueOf(890), memmModel1.get("zeppelin"));
        assertEquals(Long.valueOf(356), memmModel1.get("kafka"));
        assertEquals(Long.valueOf(178), memmModel1.get("kafka-manager"));
        assertEquals(Long.valueOf(178), memmModel1.get("cerebro"));
        assertEquals(Long.valueOf(178), memmModel1.get("zookeeper"));
    }

    @Test
    public void testComputeMemory_multiNode() throws Exception {
        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(nodesConfigString);
        KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(kubeServicesConfigString);

        Map<String, Map<String, Long>> res = memoryComputer.computeMemory(nodesConfig, kubeServicesConfig, new HashSet<>());

        assertNotNull(res);
        assertEquals(3, res.size());

        Map<String, Long> memmModel1 = res.get("192.168.10.11");
        assertNotNull(memmModel1);
        assertEquals(11, memmModel1.size());

        assertEquals(Long.valueOf(240), memmModel1.get("logstash"));
        assertEquals(Long.valueOf(720), memmModel1.get("elasticsearch"));
        assertEquals(Long.valueOf(720), memmModel1.get("spark-runtime"));
        assertNull(memmModel1.get("flink-runtime")); // flink is not configured
        assertNull(memmModel1.get("grafana")); // grafana is not configured
        assertEquals(Long.valueOf(480), memmModel1.get("kibana"));
        assertEquals(Long.valueOf(1200), memmModel1.get("zeppelin"));
        assertEquals(Long.valueOf(480), memmModel1.get("kafka"));
        assertEquals(Long.valueOf(240), memmModel1.get("kafka-manager"));
        assertEquals(Long.valueOf(240), memmModel1.get("cerebro"));
        assertEquals(Long.valueOf(240), memmModel1.get("zookeeper"));

        Map<String, Long> memmModel2 = res.get("192.168.10.12");
        assertNotNull(memmModel2);
        assertEquals(10, memmModel2.size());

        assertEquals(Long.valueOf(240), memmModel1.get("logstash"));
        assertEquals(Long.valueOf(720), memmModel1.get("elasticsearch"));
        assertEquals(Long.valueOf(720), memmModel1.get("spark-runtime"));
        assertNull(memmModel1.get("flink-runtime")); // flink is not configured
        assertNull(memmModel1.get("grafana")); // grafana is not configured
        assertEquals(Long.valueOf(480), memmModel1.get("kibana"));
        assertEquals(Long.valueOf(1200), memmModel1.get("zeppelin"));
        assertEquals(Long.valueOf(480), memmModel1.get("kafka"));
        assertEquals(Long.valueOf(240), memmModel1.get("kafka-manager"));
        assertEquals(Long.valueOf(240), memmModel1.get("cerebro"));

        Map<String, Long> memmModel3 = res.get("192.168.10.13");
        assertNotNull(memmModel3);
        assertEquals(10, memmModel3.size());

        assertEquals(Long.valueOf(456), memmModel3.get("elasticsearch"));
        assertEquals(Long.valueOf(304), memmModel3.get("kafka"));

    }

    @Test
    public void testGetMemoryMap() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("service_b1", "on");
            put("node_id2", "192.168.10.12");
            put("service_a2", "on");
            put("service_b2", "on");
            put("service_c2", "on");
            put("node_id3", "192.168.10.13");
            put("service_c3", "on");
            put("node_id4", "192.168.10.14");
            put("service_c4", "on");
            put("service_b4", "on");
        }});

        Map<String, Long> memMap = memoryComputer.getMemoryMap(nodesConfig, new HashSet<>());

        assertNotNull(memMap);
        assertEquals(4, memMap.size());
        assertEquals(5818, (long) memMap.get("192.168.10.11"));
        assertEquals(5652, (long) memMap.get("192.168.10.12"));
        assertEquals(3898, (long) memMap.get("192.168.10.13"));
        assertEquals(3898, (long) memMap.get("192.168.10.14"));
    }

    @Test
    public void testOtherConfig() throws Exception {

        memoryComputer = new MemoryComputer();
        memoryComputer.setServicesDefinition(servicesDefinition);

        memoryComputer.setSshCommandService(new SSHCommandService() {
            @Override
            public String runSSHScript(String node, String script, boolean throwsException) throws SSHCommandException {
                return "MemTotal:        20000000 kB";
            }
            @Override
            public String runSSHCommand(String node, String command) throws SSHCommandException {
                return null;
            }
            @Override
            public void copySCPFile(String node, String filePath) throws SSHCommandException {
                // just do nothing
            }
        });

        Map<String, Map<String, Long>> res = memoryComputer.computeMemory(
                StandardSetupHelpers.getStandard2NodesSetup(),
                StandardSetupHelpers.getStandardKubernetesConfig(), new HashSet<>());

        assertNotNull(res);
        assertEquals(2, res.size());

        Map<String, Long> memmModel1 = res.get("192.168.10.11");
        assertNotNull(memmModel1);
        assertEquals(10, memmModel1.size());

        assertEquals(Long.valueOf(840), memmModel1.get("logstash"));
        assertEquals(Long.valueOf(2520), memmModel1.get("elasticsearch"));
        assertEquals(Long.valueOf(2520), memmModel1.get("spark-runtime"));
        assertNull(memmModel1.get("flink-runtime")); // flink is not configured
        assertNull(memmModel1.get("grafana")); // grafana is not configured
        assertEquals(Long.valueOf(1680), memmModel1.get("kibana"));
        assertEquals(Long.valueOf(4200), memmModel1.get("zeppelin"));
        assertEquals(Long.valueOf(1680), memmModel1.get("kafka"));
        assertEquals(Long.valueOf(840), memmModel1.get("kafka-manager"));
        assertEquals(Long.valueOf(840), memmModel1.get("cerebro"));
        assertNull(memmModel1.get("zookeeper"));

        Map<String, Long> memmModel3 = res.get("192.168.10.13");
        assertNotNull(memmModel3);
        assertEquals(11, memmModel3.size());

        assertEquals(Long.valueOf(2412), memmModel3.get("elasticsearch"));
        assertEquals(Long.valueOf(1608), memmModel3.get("kafka"));
    }
}
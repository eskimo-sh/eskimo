/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.services.satellite;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import ch.niceideas.eskimo.test.services.SSHCommandServiceTestImpl;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-ssh", "test-services"})
public class MemoryComputerTest {

    @Autowired
    private MemoryComputer memoryComputer;

    @Autowired
    private SSHCommandServiceTestImpl sshCommandServiceTest;

    private String nodesConfigString = null;
    private String kubeServicesConfigString = null;

    @BeforeEach
    public void setUp() throws Exception {
        nodesConfigString =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesDefinitionTest/testConfig.json"), StandardCharsets.UTF_8);
        kubeServicesConfigString =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesDefinitionTest/testKubernetesConfig.json"), StandardCharsets.UTF_8);

        sshCommandServiceTest.setNodeResultBuilder((node, script) -> {
            switch (node.getAddress()) {
                case "192.168.10.11":
                    return "MemTotal:        5969796 kB";
                case "192.168.10.12":
                    return "MemTotal:        5799444 kB";
                default:
                    return "MemTotal:        3999444 kB";
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

        Map<Node, Map<Service, Long>> res = memoryComputer.computeMemory(nodesConfig, kubeServicesConfig, new HashSet<>());

        assertNotNull(res);
        assertEquals(1, res.size());

        Map<Service, Long> memmModel1 = res.get(Node.fromAddress("192.168.10.11"));
        assertNotNull(memmModel1);
        assertEquals(7, memmModel1.size());

        assertEquals(Long.valueOf(801), memmModel1.get(Service.from("database")));
        assertEquals(Long.valueOf(801), memmModel1.get(Service.from("calculator-runtime")));
        assertNull(memmModel1.get(Service.from("calculator-cli"))); // calculator-cli is not configured
        assertEquals(Long.valueOf(267), memmModel1.get(Service.from("database-manager")));
        assertEquals(Long.valueOf(1335), memmModel1.get(Service.from("user-console")));
        assertEquals(Long.valueOf(534), memmModel1.get(Service.from("broker")));
        assertEquals(Long.valueOf(267), memmModel1.get(Service.from("broker-manager")));
        assertEquals(Long.valueOf(267), memmModel1.get(Service.from("broker-manager")));
    }

    @Test
    public void testComputeMemory_multiNode() throws Exception {
        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(nodesConfigString);
        KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(kubeServicesConfigString);

        Map<Node, Map<Service, Long>> res = memoryComputer.computeMemory(nodesConfig, kubeServicesConfig, new HashSet<>());

        assertNotNull(res);
        assertEquals(3, res.size());

        Map<Service, Long> memmModel1 = res.get(Node.fromAddress("192.168.10.11"));
        assertNotNull(memmModel1);
        assertEquals(7, memmModel1.size());

        assertEquals(Long.valueOf(1110), memmModel1.get(Service.from("database")));
        assertEquals(Long.valueOf(1110), memmModel1.get(Service.from("calculator-runtime")));
        assertNull(memmModel1.get(Service.from("calculator-cli"))); // calculator-cli is not configured
        assertEquals(Long.valueOf(370), memmModel1.get(Service.from("database-manager")));
        assertEquals(Long.valueOf(1850), memmModel1.get(Service.from("user-console")));
        assertEquals(Long.valueOf(740), memmModel1.get(Service.from("broker")));
        assertEquals(Long.valueOf(370), memmModel1.get(Service.from("broker-manager")));
        assertEquals(Long.valueOf(370), memmModel1.get(Service.from("broker-manager")));

        Map<Service, Long> memmModel2 = res.get(Node.fromAddress("192.168.10.12"));
        assertNotNull(memmModel2);
        assertEquals(6, memmModel2.size());
        
        assertEquals(Long.valueOf(1110), memmModel1.get(Service.from("database")));
        assertEquals(Long.valueOf(1110), memmModel1.get(Service.from("calculator-runtime")));
        assertNull(memmModel1.get(Service.from("calculator-cli"))); // calculator-cli is not configured
        assertEquals(Long.valueOf(370), memmModel1.get(Service.from("database-manager")));
        assertEquals(Long.valueOf(1850), memmModel1.get(Service.from("user-console")));
        assertEquals(Long.valueOf(740), memmModel1.get(Service.from("broker")));
        assertEquals(Long.valueOf(370), memmModel1.get(Service.from("broker-manager")));

        Map<Service, Long> memmModel3 = res.get(Node.fromAddress("192.168.10.13"));
        assertNotNull(memmModel3);
        assertEquals(6, memmModel3.size());

        assertEquals(Long.valueOf(723), memmModel3.get(Service.from("database")));
        assertEquals(Long.valueOf(482), memmModel3.get(Service.from("broker")));

    }

    @Test
    public void testGetMemoryMap() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
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

        Map<Node, Long> memMap = memoryComputer.getMemoryMap(nodesConfig, new HashSet<>());

        assertNotNull(memMap);
        assertEquals(4, memMap.size());
        assertEquals(5818, (long) memMap.get(Node.fromAddress("192.168.10.11")));
        assertEquals(5652, (long) memMap.get(Node.fromAddress("192.168.10.12")));
        assertEquals(3898, (long) memMap.get(Node.fromAddress("192.168.10.13")));
        assertEquals(3898, (long) memMap.get(Node.fromAddress("192.168.10.14")));
    }

    @Test
    public void testOtherConfig() throws Exception {

        sshCommandServiceTest.setNodeResultBuilder((node, script) -> "MemTotal:        20000000 kB");

        Map<Node, Map<Service, Long>> res = memoryComputer.computeMemory(
                StandardSetupHelpers.getStandard2NodesSetup(),
                StandardSetupHelpers.getStandardKubernetesConfig(), new HashSet<>());

        assertNotNull(res);
        assertEquals(2, res.size());

        Map<Service, Long> memmModel1 = res.get(Node.fromAddress("192.168.10.11"));
        assertNotNull(memmModel1);
        assertEquals(7, memmModel1.size());
        
        assertEquals(Long.valueOf(3696), memmModel1.get(Service.from("database")));
        assertEquals(Long.valueOf(3696), memmModel1.get(Service.from("calculator-runtime")));
        assertNull(memmModel1.get(Service.from("calculator-cli"))); // calculator-cli is not configured
        assertEquals(Long.valueOf(1232), memmModel1.get(Service.from("database-manager")));
        assertEquals(Long.valueOf(6160), memmModel1.get(Service.from("user-console")));
        assertEquals(Long.valueOf(2464), memmModel1.get(Service.from("broker")));
        assertEquals(Long.valueOf(1232), memmModel1.get(Service.from("broker-manager")));

        Map<Service, Long> memmModel3 = res.get(Node.fromAddress("192.168.10.13"));
        assertNotNull(memmModel3);
        assertEquals(8, memmModel3.size());

        assertEquals(Long.valueOf(3465), memmModel3.get(Service.from("database")));
        assertEquals(Long.valueOf(2310), memmModel3.get(Service.from("broker")));
    }
}
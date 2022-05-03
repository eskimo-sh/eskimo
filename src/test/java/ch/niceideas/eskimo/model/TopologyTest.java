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

package ch.niceideas.eskimo.model;

import ch.niceideas.eskimo.services.*;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TopologyTest extends AbstractServicesDefinitionTest {

    private MemoryModel emptyModel = new MemoryModel(Collections.emptyMap());

    @Test
    public void testMasterElectionStrategyFirstNode() throws Exception {

        initFirstNodeDependencies();

        NodesConfigWrapper nodesConfig = createStandardNodesConfig();
        KubernetesServicesConfigWrapper kubeServicesConfig = createStandardMarathonConfig();

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, "192.168.10.11");

        assertEquals ("export MASTER_SERVICE_B_1=192.168.10.12\n" +
                "export MASTER_SERVICE_C_1=192.168.10.11\n" +
                "export MASTER_SERVICE_C_2=192.168.10.13\n", topology.getTopologyScript());
    }

    private KubernetesServicesConfigWrapper createStandardMarathonConfig() {
        return new KubernetesServicesConfigWrapper(new HashMap<String, Object>() {{
            put("service_d_install", "on");
        }});
    }

    NodesConfigWrapper createStandardNodesConfig() {
        return new NodesConfigWrapper(new HashMap<String, Object>() {{
                    put("node_id1", "192.168.10.11");
                    put("service_a1", "on");
                    put("service_c1", "on");
                    put("node_id2", "192.168.10.12");
                    put("service_b", "2");
                    put("node_id3", "192.168.10.13");
                    put("service_c3", "on");
            }});
    }

    @Test
    public void testMasterElectionStrategySameNodeOrRandom() throws Exception {

        initSameNodeOrRandomDependencies();

        NodesConfigWrapper nodesConfig = createStandardNodesConfig();
        nodesConfig.setValueForPath("service_b", "1");

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, "192.168.10.11");

        assertEquals ("export SELF_MASTER_SERVICE_B_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_SERVICE_C_1921681011=192.168.10.11\n", topology.getTopologyScript());
    }

    @Test
    public void testMasterElectionStrategyRandom() throws Exception {

        initRandomDependencies();

        NodesConfigWrapper nodesConfig = createStandardNodesConfig();

        KubernetesServicesConfigWrapper kubeServicesConfig = createStandardMarathonConfig();

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, "192.168.10.11");

        assertEquals ("export MASTER_SERVICE_B_1=192.168.10.12\n" +
                "export MASTER_SERVICE_C_1=192.168.10.11\n" +
                "export MASTER_SERVICE_C_2=192.168.10.13\n", topology.getTopologyScript());
    }

    @Test
    public void testMasterElectionStrategyRandomNodeAfter() throws Exception {

        initRandomNodeAfterDependencies();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("service_a1", "on");
                put("service_c1", "on");
                put("node_id2", "192.168.10.12");
                put("service_b", "1");
                put("node_id3", "192.168.10.13");
                put("service_b3", "on");
                put("service_c3", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, "192.168.10.11");

        assertEquals ("export MASTER_SERVICE_B_1921681011=192.168.10.13\n" +
                "export MASTER_SERVICE_C_1921681011=192.168.10.13\n" +
                "export MASTER_SERVICE_C_1921681013=192.168.10.11\n", topology.getTopologyScript());
    }

    @Test
    public void testMasterElectionStrategyRandomNodeAfterOrSame() throws Exception {

        initRandomNodeAfterOrSameDependencies();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("service_c1", "on");
            put("node_id2", "192.168.10.12");
            put("service_b", "1");
            put("node_id3", "192.168.10.13");
            put("service_b3", "on");
            put("service_c3", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, "192.168.10.11");

        assertEquals ("export MASTER_SERVICE_B_1921681011=192.168.10.13\n" +
                "export MASTER_SERVICE_C_1921681011=192.168.10.13\n" +
                "export MASTER_SERVICE_C_1921681013=192.168.10.11\n", topology.getTopologyScript());
    }

    @Test
    public void testMasterElectionStrategyRandomNodeAfterOrSameSingleNode() throws Exception {

        initRandomNodeAfterOrSameDependencies();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("service_b1", "on");
            put("service_c1", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, "192.168.10.11");

        assertEquals ("export MASTER_SERVICE_B_1921681011=192.168.10.11\n" +
                "export MASTER_SERVICE_C_1921681011=192.168.10.11\n", topology.getTopologyScript());
    }

    @Test
    public void testMasterElectionStrategyRandomNodeAfterChain() throws Exception {

        initRandomNodeAfterDependencies();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("service_a1", "on");
                put("service_b1", "on");
                put("service_c1", "on");
                put("node_id2", "192.168.10.12");
                put("service_b2", "on");
                put("node_id3", "192.168.10.13");
                put("service_b3", "on");
                put("service_c3", "on");
                put("node_id4", "192.168.10.14");
                put("service_b4", "on");
                put("service_c4", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, "192.168.10.11");

        assertEquals ("export MASTER_SERVICE_B_1921681011=192.168.10.12\n" +
                "export MASTER_SERVICE_C_1921681011=192.168.10.13\n" +
                "export MASTER_SERVICE_C_1921681012=192.168.10.13\n" +
                "export MASTER_SERVICE_C_1921681013=192.168.10.14\n" +
                "export MASTER_SERVICE_C_1921681014=192.168.10.11\n", topology.getTopologyScript());
    }

    @Test
    public void testRealCaseGluster() throws Exception {

        NodeRangeResolver nrr = new NodeRangeResolver();

        def = new ServicesDefinition();
        def.setSetupService (setupService);

        Service serviceA = new Service();
        serviceA.setName("gluster");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER);
        depA.setMasterService("gluster");
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("node_id2", "192.168.10.13-192.168.10.14");
                put("node_id3", "192.168.10.12");
                put("gluster1", "on");
                put("gluster2", "on");
                put("gluster3", "on");
        }});

        Topology topology = Topology.create(nrr.resolveRanges(nodesConfig), KubernetesServicesConfigWrapper.empty(),  def, null, "192.168.10.11");

        assertEquals ("export MASTER_GLUSTER_1921681011=192.168.10.13\n" +
                "export MASTER_GLUSTER_1921681012=192.168.10.14\n" +
                "export MASTER_GLUSTER_1921681013=192.168.10.12\n" +
                "export MASTER_GLUSTER_1921681014=192.168.10.11\n", topology.getTopologyScript());
    }

    @Test
    public void testNoDependencies() throws Exception {

        initAdditionalEnvironment();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("service_a1", "on");
                put("node_id2", "192.168.10.12");
                put("service_b", "1");
                put("node_id3", "192.168.10.13");
                put("service_b3", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, "192.168.10.11");

        assertEquals ("", topology.getTopologyScript());
    }

    @Test
    public void testConditionalDependency() throws Exception {

        initConditionalDependency();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("node_id2", "192.168.10.12");
            put("service_b", "1");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, "192.168.10.11");

        assertEquals ("export MASTER_SERVICE_B_1=192.168.10.11\n", topology.getTopologyScript());

        final NodesConfigWrapper nodesConfig2 = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("node_id2", "192.168.10.12");
            put("service_b", "1");
            put("service_d", "1");
        }});

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class,
                () -> Topology.create(nodesConfig2, KubernetesServicesConfigWrapper.empty(), def, null, "192.168.10.11")
        );

        assertEquals ("Dependency service_c for service service_b could not found occurence 1", exception.getMessage());

        nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("node_id2", "192.168.10.12");
            put("service_b", "1");
            put("service_c", "2");
            put("service_d", "1");
        }});

        topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, "192.168.10.11");

        assertEquals ("export MASTER_SERVICE_B_1=192.168.10.11\n" +
                "export MASTER_SERVICE_C_1=192.168.10.12\n", topology.getTopologyScript());
    }

    @Test
    public void testAdditionalEnvironment() throws Exception {

        initAdditionalEnvironment();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("service_a1", "on");
                put("node_id2", "192.168.10.12");
                put("service_a2", "on");
                put("service_c2", "on");
                put("node_id3", "192.168.10.13");
                put("service_c3", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, "192.168.10.11");

        assertEquals ("#Topology\n" +
                "\n" +
                "#Additional Environment\n" +
                "export NODE_NBR_SERVICE_A_1921681012=1\n" +
                "export NODE_NBR_SERVICE_A_1921681011=0\n" +
                "\n" +
                "#Self identification\n" +
                "export SELF_IP_ADDRESS=192.168.10.11\n" +
                "export SELF_NODE_NUMBER=1\n" +
                "export ESKIMO_NODE_COUNT=3\n" +
                "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13\n", topology.getTopologyScriptForNode (nodesConfig, emptyModel, 1));

        assertEquals ("#Topology\n" +
                "\n" +
                "#Additional Environment\n" +
                "export NODE_NBR_SERVICE_A_1921681012=1\n" +
                "export NODE_NBR_SERVICE_A_1921681011=0\n" +
                "export NODE_NBR_SERVICE_C_1921681013=2\n" +
                "export NODE_NBR_SERVICE_C_1921681012=1\n" +
                "\n" +
                "#Self identification\n" +
                "export SELF_IP_ADDRESS=192.168.10.12\n" +
                "export SELF_NODE_NUMBER=2\n" +
                "export ESKIMO_NODE_COUNT=3\n" +
                "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13\n", topology.getTopologyScriptForNode (nodesConfig, emptyModel, 2));

        assertEquals ("#Topology\n" +
                "\n" +
                "#Additional Environment\n" +
                "export NODE_NBR_SERVICE_C_1921681013=2\n" +
                "export NODE_NBR_SERVICE_C_1921681012=1\n" +
                "\n" +
                "#Self identification\n" +
                "export SELF_IP_ADDRESS=192.168.10.13\n" +
                "export SELF_NODE_NUMBER=3\n" +
                "export ESKIMO_NODE_COUNT=3\n" +
                "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13\n", topology.getTopologyScriptForNode (nodesConfig, emptyModel, 3));
    }

    @Test
    public void testMemoryModel() throws Exception {

        MemoryModel memoryModel = new MemoryModel(new HashMap<String, Map<String, Long>>(){{
            put ("192.168.10.11", new HashMap<String, Long>(){{
                put ("service_a", Long.valueOf("100"));
                put ("service_b", Long.valueOf("200"));
                put ("service_c", Long.valueOf("300"));
            }});
        }});

        initAdditionalNodeList();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("service_b1", "on");
            put("service_c1", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, "192.168.10.11");

        assertEquals ("#Topology\n" +
                "\n" +
                "#Additional Environment\n" +
                "export ALL_NODES_LIST_service_a=192.168.10.11\n" +
                "export ALL_NODES_LIST_service_b=192.168.10.11\n" +
                "\n" +
                "#Self identification\n" +
                "export SELF_IP_ADDRESS=192.168.10.11\n" +
                "export SELF_NODE_NUMBER=1\n" +
                "export ESKIMO_NODE_COUNT=1\n" +
                "export ALL_NODES_LIST=192.168.10.11\n" +
                "\n" +
                "#Memory Management\n" +
                "export MEMORY_SERVICE_A=100\n" +
                "export MEMORY_SERVICE_B=200\n" +
                "export MEMORY_SERVICE_C=300\n", topology.getTopologyScriptForNode (nodesConfig, memoryModel, 1));
    }


    @Test
    public void testMemoryModelWithAdditional() throws Exception {

        MemoryModel memoryModel = new MemoryModel(new HashMap<String, Map<String, Long>>(){{
            put ("192.168.10.11", new HashMap<String, Long>(){{
                put ("service_a", Long.valueOf("100"));
                put ("service_b", Long.valueOf("200"));
                put ("service_c", Long.valueOf("300"));
            }});
        }});


        initAdditionalNodeListWithAdditionalMemory();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("service_b1", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, "192.168.10.11");

        assertEquals ("#Topology\n" +
                "\n" +
                "#Additional Environment\n" +
                "export ALL_NODES_LIST_service_a=192.168.10.11\n" +
                "\n" +
                "#Self identification\n" +
                "export SELF_IP_ADDRESS=192.168.10.11\n" +
                "export SELF_NODE_NUMBER=1\n" +
                "export ESKIMO_NODE_COUNT=1\n" +
                "export ALL_NODES_LIST=192.168.10.11\n" +
                "\n" +
                "#Memory Management\n" +
                "export MEMORY_SERVICE_A=100\n" +
                "export MEMORY_SERVICE_B=200\n" +
                "export MEMORY_SERVICE_C=300\n", topology.getTopologyScriptForNode (nodesConfig, memoryModel, 1));
    }


    @Test
    public void testAdditionalEnvironmentNodeList() throws Exception {

        initAdditionalNodeList();

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
                put("node_id5", "192.168.10.15");
                put("service_a5", "on");
                put("service_c5", "on");
        }});

        KubernetesServicesConfigWrapper kubeServicesConfig = createStandardMarathonConfig();

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, "192.168.10.11");

        assertEquals ("#Topology\n" +
                "export MASTER_SERVICE_C_1=192.168.10.12\n" +
                "\n" +
                "#Additional Environment\n" +
                "export ALL_NODES_LIST_service_a=192.168.10.11,192.168.10.12,192.168.10.15\n" +
                "\n" +
                "#Self identification\n" +
                "export SELF_IP_ADDRESS=192.168.10.11\n" +
                "export SELF_NODE_NUMBER=1\n" +
                "export ESKIMO_NODE_COUNT=5\n" +
                "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13,192.168.10.14,192.168.10.15\n", topology.getTopologyScriptForNode (nodesConfig, emptyModel, 1));

        assertEquals ("#Topology\n" +
                "export MASTER_SERVICE_C_1=192.168.10.12\n" +
                "\n" +
                "#Additional Environment\n" +
                "export ALL_NODES_LIST_service_b=192.168.10.11,192.168.10.12,192.168.10.14\n" +
                "\n" +
                "#Self identification\n" +
                "export SELF_IP_ADDRESS=192.168.10.13\n" +
                "export SELF_NODE_NUMBER=3\n" +
                "export ESKIMO_NODE_COUNT=5\n" +
                "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13,192.168.10.14,192.168.10.15\n", topology.getTopologyScriptForNode (nodesConfig, emptyModel, 3));
    }

    @Test
    public void testGetVariableName() throws Exception {
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.FIRST_NODE);
        depA.setMasterService("mesos-master");
        depA.setNumberOfMasters(1);

        Topology topology = new Topology();

        assertEquals ("MESOS_MASTER", topology.getVariableName(depA));

    }

    @Test
    public void testPersistentEnvironment() throws Exception {

        initAdditionalEnvironment();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("service_a1", "on");
                put("node_id2", "192.168.10.12");
                put("service_a2", "on");
                put("service_c2", "on");
                put("node_id3", "192.168.10.13");
                put("service_c3", "on");
        }});

        KubernetesServicesConfigWrapper kubeServicesConfig = createStandardMarathonConfig();

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, "192.168.10.11");

        assertEquals ("#Topology\n" +
                "\n" +
                "#Additional Environment\n" +
                "export NODE_NBR_SERVICE_A_1921681012=1\n" +
                "export NODE_NBR_SERVICE_A_1921681011=0\n" +
                "export NODE_NBR_SERVICE_C_1921681013=2\n" +
                "export NODE_NBR_SERVICE_C_1921681012=1\n" +
                "\n" +
                "#Self identification\n" +
                "export SELF_IP_ADDRESS=192.168.10.12\n" +
                "export SELF_NODE_NUMBER=2\n" +
                "export ESKIMO_NODE_COUNT=3\n" +
                "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13\n", topology.getTopologyScriptForNode (nodesConfig, emptyModel, 2));

        // now change topology and ensure node numbers for services A and C are unchanged
        nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("service_a1", "on");
                put("node_id2", "192.168.10.14");
                put("service_a2", "on");
                put("node_id3", "192.168.10.12");
                put("service_a3", "on");
                put("service_c3", "on");
                put("node_id4", "192.168.10.15");
                put("service_a4", "on");
                put("service_c4", "on");
                put("node_id5", "192.168.10.13");
                put("service_c5", "on");
        }});

        topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, "192.168.10.11");

        assertEquals ("#Topology\n" +
                "\n" +
                "#Additional Environment\n" +
                "export NODE_NBR_SERVICE_A_1921681012=1\n" +
                "export NODE_NBR_SERVICE_A_1921681011=0\n" +
                "export NODE_NBR_SERVICE_A_1921681015=3\n" +
                "export NODE_NBR_SERVICE_A_1921681014=2\n" +
                "export NODE_NBR_SERVICE_C_1921681013=2\n" +
                "export NODE_NBR_SERVICE_C_1921681012=1\n" +
                "export NODE_NBR_SERVICE_C_1921681015=3\n" +
                "\n" +
                "#Self identification\n" +
                "export SELF_IP_ADDRESS=192.168.10.12\n" +
                "export SELF_NODE_NUMBER=3\n" +
                "export ESKIMO_NODE_COUNT=5\n" +
                "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13,192.168.10.14,192.168.10.15\n", topology.getTopologyScriptForNode (nodesConfig, emptyModel, 3));
    }

    @Test
    public void testKubernetesServiceUnsupportedDependencies() throws Exception {

        Service serviceA = new Service();
        serviceA.setName("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER);
        depA.setMasterService("service_b");
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        Service serviceB = new Service();
        serviceB.setName("service_b");
        def.addService(serviceB);

        Service serviceD = new Service();
        serviceD.setName("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.SAME_NODE);
        depD.setMasterService("service_b");
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("node_id2", "192.168.10.12");
            put("service_a2", "on");
            put("node_id3", "192.168.10.13");
        }});

        KubernetesServicesConfigWrapper kubeServicesConfig = createStandardMarathonConfig();

        ServiceDefinitionException exception = assertThrows(ServiceDefinitionException.class,
                () -> Topology.create(nodesConfig, kubeServicesConfig,  def, null, "192.168.10.11"));

        assertEquals("Service service_d defines a SAME_NODE dependency on service_b, which is not supported for kubernetes services", exception.getMessage());

        depD.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER);

        exception = assertThrows(ServiceDefinitionException.class,
                () -> Topology.create(nodesConfig, kubeServicesConfig, def, null, "192.168.10.11"));

        assertEquals("Service service_d defines a RANDOM_NODE_AFTER dependency on service_b, which is not supported for kubernetes services", exception.getMessage());

        depD.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER_OR_SAME);

        exception = assertThrows(ServiceDefinitionException.class,
                () -> Topology.create(nodesConfig, kubeServicesConfig, def, null, "192.168.10.11"));

        assertEquals("Service service_d defines a RANDOM_NODE_AFTER_OR_SAME dependency on service_b, which is not supported for kubernetes services", exception.getMessage());
    }

    @Test
    public void testKubernetesOnKubernetesDependencies() throws Exception {

        Service serviceA = new Service();
        serviceA.setName("service_a");
        def.addService(serviceA);

        Service serviceB = new Service();
        serviceB.setKubernetes(true);
        serviceB.setName("service_b");
        def.addService(serviceB);

        Service serviceD = new Service();
        serviceD.setName("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.RANDOM);
        depD.setMasterService("service_b");
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("node_id2", "192.168.10.12");
            put("service_a2", "on");
            put("node_id3", "192.168.10.13");
        }});

        KubernetesServicesConfigWrapper kubeServicesConfig = createStandardMarathonConfig();

        ServiceDefinitionException exception = assertThrows(ServiceDefinitionException.class,
                () -> Topology.create(nodesConfig, kubeServicesConfig,  def, null, "192.168.10.11"));

        assertEquals("Service service_d defines a dependency on another kube service service_b but that service is not going to be installed.", exception.getMessage());

        kubeServicesConfig.setValueForPath("service_b_install", "on");

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig,  def, null, "192.168.10.11");

        assertEquals ("#Topology\n" +
                "\n" +
                "#Additional Environment\n" +
                "\n" +
                "#Self identification\n" +
                "export SELF_IP_ADDRESS=192.168.10.13\n" +
                "export SELF_NODE_NUMBER=3\n" +
                "export ESKIMO_NODE_COUNT=3\n" +
                "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13\n", topology.getTopologyScriptForNode (nodesConfig, emptyModel, 3));
    }

    @Test
    public void testMarathonServiceDependencies() throws Exception {

        initRandomDependenciesFewer();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("service_c1", "on");
            put("node_id2", "192.168.10.12");
            put("node_id3", "192.168.10.13");
            put("service_c3", "on");
            put("node_id4", "192.168.10.14");
            put("service_c4", "on");
        }});

        KubernetesServicesConfigWrapper kubeServicesConfig = createStandardMarathonConfig();

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, "192.168.10.11");

        assertEquals ("export MASTER_SERVICE_C_1=192.168.10.11\n" +
                "export MASTER_SERVICE_C_2=192.168.10.13\n", topology.getTopologyScript());
    }


    @Test
    public void testConsolidatedServiceDependencies() throws Exception {

        initRandomDependencies();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("service_b1", "on");
            put("service_c1", "on");
            put("node_id2", "192.168.10.12");
            put("service_b2", "on");
            put("node_id3", "192.168.10.13");
            put("service_b3", "on");
            put("service_c3", "on");
            put("node_id4", "192.168.10.14");
            put("service_b4", "on");
            put("service_c4", "on");
        }});

        KubernetesServicesConfigWrapper kubeServicesConfig = createStandardMarathonConfig();

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, "192.168.10.11");

        assertEquals ("export MASTER_SERVICE_B_1=192.168.10.11\n" +
                "export MASTER_SERVICE_C_1=192.168.10.11\n" +
                "export MASTER_SERVICE_C_2=192.168.10.13\n", topology.getTopologyScript());
    }
}

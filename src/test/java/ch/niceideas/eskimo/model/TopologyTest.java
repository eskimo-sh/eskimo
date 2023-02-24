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

package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.service.*;
import ch.niceideas.eskimo.services.ServiceDefinitionException;
import ch.niceideas.eskimo.services.ServicesDefinitionImpl;
import ch.niceideas.eskimo.services.SystemServiceTest;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import ch.niceideas.eskimo.test.services.ServicesDefinitionTestImpl;
import ch.niceideas.eskimo.test.testwrappers.SetupServiceUnderTest;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TopologyTest {

    private final MemoryModel emptyModel = new MemoryModel(Collections.emptyMap());

    protected SetupServiceUnderTest setupService = new SetupServiceUnderTest();

    protected ServicesDefinitionImpl def;

    private String tempStoragePath = null;

    @BeforeEach
    public void setUp() throws Exception {
        def = new ServicesDefinitionTestImpl();
        def.setSetupService (setupService);
        setupService.setServicesDefinition(def);
        //def.afterPropertiesSet();
        tempStoragePath = SystemServiceTest.createTempStoragePath();
        setupService.setConfigStoragePathInternal(tempStoragePath);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (StringUtils.isNotBlank(tempStoragePath)) {
            FileUtils.delete(new File(tempStoragePath));
        }
    }

    public void initFirstNodeDependencies() {

        ServiceDefinition serviceA = new ServiceDefinition("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.FIRST_NODE);
        depA.setMasterService(Service.from("service_b"));
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        ServiceDefinition serviceB = new ServiceDefinition("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.FIRST_NODE);
        depB.setMasterService(Service.from("service_c"));
        depB.setNumberOfMasters(2);
        serviceB.addDependency (depB);
        def.addService(serviceB);

        ServiceDefinition serviceC = new ServiceDefinition("service_c");
        def.addService(serviceC);

        ServiceDefinition serviceD = new ServiceDefinition("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.FIRST_NODE);
        depD.setMasterService(Service.from("service_c"));
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }

    public void initConditionalDependency() {

        ServiceDefinition serviceA = new ServiceDefinition("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.FIRST_NODE);
        depA.setMasterService(Service.from("service_b"));
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        ServiceDefinition serviceB = new ServiceDefinition("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.FIRST_NODE);
        depB.setMasterService(Service.from("service_c"));
        depB.setNumberOfMasters(1);
        depB.setConditionalDependency(Service.from("service_d"));
        serviceB.addDependency (depB);
        def.addService(serviceB);

        ServiceDefinition serviceC = new ServiceDefinition("service_c");
        def.addService(serviceC);

        ServiceDefinition serviceD = new ServiceDefinition("service_d");
        def.addService(serviceD);
    }

    public void initSameNodeOrRandomDependencies() {

        ServiceDefinition serviceA = new ServiceDefinition("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.SAME_NODE_OR_RANDOM);
        depA.setMasterService(Service.from("service_b"));
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        ServiceDefinition serviceB = new ServiceDefinition("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.SAME_NODE_OR_RANDOM);
        depB.setMasterService(Service.from("service_c"));
        depB.setNumberOfMasters(1);
        serviceB.addDependency (depB);
        def.addService(serviceB);

        ServiceDefinition serviceC = new ServiceDefinition("service_c");
        def.addService(serviceC);

        ServiceDefinition serviceD = new ServiceDefinition("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.FIRST_NODE);
        depD.setMasterService(Service.from("service_c"));
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }

    public void initRandomDependencies() {

        ServiceDefinition serviceA = new ServiceDefinition("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.RANDOM);
        depA.setMasterService(Service.from("service_b"));
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        ServiceDefinition serviceB = new ServiceDefinition("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.RANDOM);
        depB.setMasterService(Service.from("service_c"));
        depB.setNumberOfMasters(2);
        serviceB.addDependency (depB);
        def.addService(serviceB);

        ServiceDefinition serviceC = new ServiceDefinition("service_c");
        def.addService(serviceC);

        ServiceDefinition serviceD = new ServiceDefinition("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.FIRST_NODE);
        depD.setMasterService(Service.from("service_c"));
        depD.setNumberOfMasters(2);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }

    public void initRandomDependenciesFewer() {

        ServiceDefinition serviceA = new ServiceDefinition("service_a");
        def.addService(serviceA);

        ServiceDefinition serviceB = new ServiceDefinition("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.RANDOM);
        depB.setMasterService(Service.from("service_c"));
        depB.setNumberOfMasters(2);
        serviceB.addDependency (depB);
        def.addService(serviceB);

        ServiceDefinition serviceC = new ServiceDefinition("service_c");
        def.addService(serviceC);

        ServiceDefinition serviceD = new ServiceDefinition("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.FIRST_NODE);
        depD.setMasterService(Service.from("service_c"));
        depD.setNumberOfMasters(2);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }

    public void initRandomNodeAfterOrSameDependencies() {

        ServiceDefinition serviceA = new ServiceDefinition("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER_OR_SAME);
        depA.setMasterService(Service.from("service_b"));
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        ServiceDefinition serviceB = new ServiceDefinition("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER_OR_SAME);
        depB.setMasterService(Service.from("service_c"));
        depB.setNumberOfMasters(1);
        serviceB.addDependency (depB);
        def.addService(serviceB);

        ServiceDefinition serviceC = new ServiceDefinition("service_c");
        def.addService(serviceC);

        ServiceDefinition serviceD = new ServiceDefinition("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.RANDOM);
        depD.setMasterService(Service.from("service_c"));
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }

    public void initRandomNodeAfterDependencies() {

        ServiceDefinition serviceA = new ServiceDefinition("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER);
        depA.setMasterService(Service.from("service_b"));
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        ServiceDefinition serviceB = new ServiceDefinition("service_b");
        Dependency depB = new Dependency();
        depB.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER);
        depB.setMasterService(Service.from("service_c"));
        depB.setNumberOfMasters(1);
        serviceB.addDependency (depB);
        def.addService(serviceB);

        ServiceDefinition serviceC = new ServiceDefinition("service_c");
        def.addService(serviceC);

        ServiceDefinition serviceD = new ServiceDefinition("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.RANDOM);
        depD.setMasterService(Service.from("service_c"));
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }

    public void initAdditionalEnvironment() {

        ServiceDefinition serviceA = new ServiceDefinition("service_a");
        serviceA.addAdditionalEnvironment("SERVICE_NUMBER_0_BASED");
        def.addService(serviceA);

        ServiceDefinition serviceB = new ServiceDefinition("service_b");
        def.addService(serviceB);

        ServiceDefinition serviceC = new ServiceDefinition("service_c");
        serviceC.addAdditionalEnvironment("SERVICE_NUMBER_1_BASED");
        def.addService(serviceC);

        ServiceDefinition serviceD = new ServiceDefinition("service_d");
        serviceD.setKubernetes(true);
        def.addService(serviceD);
    }

    public void initAdditionalNodeList() {

        ServiceDefinition serviceA = new ServiceDefinition("service_a");
        serviceA.addAdditionalEnvironment("ALL_NODES_LIST_service_a");
        serviceA.setMemoryConsumptionSize(MemoryConsumptionSize.LARGE);
        def.addService(serviceA);

        ServiceDefinition serviceB = new ServiceDefinition("service_b");
        serviceB.setMemoryConsumptionSize(MemoryConsumptionSize.MEDIUM);
        def.addService(serviceB);

        ServiceDefinition serviceC = new ServiceDefinition("service_c");
        serviceC.addAdditionalEnvironment("ALL_NODES_LIST_service_b");
        serviceC.setMemoryConsumptionSize(MemoryConsumptionSize.NEGLIGIBLE);
        def.addService(serviceC);

        ServiceDefinition serviceD = new ServiceDefinition("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.FIRST_NODE);
        depD.setMasterService(Service.from("service_c"));
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }


    public void initAdditionalNodeListWithAdditionalMemory() {

        ServiceDefinition serviceA = new ServiceDefinition("service_a");
        serviceA.addAdditionalEnvironment("ALL_NODES_LIST_service_a");
        serviceA.setMemoryConsumptionSize(MemoryConsumptionSize.LARGE);
        def.addService(serviceA);

        ServiceDefinition serviceB = new ServiceDefinition("service_b");
        serviceB.setMemoryConsumptionSize(MemoryConsumptionSize.MEDIUM);
        serviceB.addAdditionalMemory(Service.from("service_c"));
        def.addService(serviceB);

        ServiceDefinition serviceC = new ServiceDefinition("service_c");
        serviceC.addAdditionalEnvironment("ALL_NODES_LIST_service_b");
        serviceC.setMemoryConsumptionSize(MemoryConsumptionSize.NEGLIGIBLE);
        def.addService(serviceC);

        ServiceDefinition serviceD = new ServiceDefinition("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.FIRST_NODE);
        depD.setMasterService(Service.from("service_c"));
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);
    }

    @Test
    public void testMasterElectionStrategyFirstNode() throws Exception {

        initFirstNodeDependencies();

        NodesConfigWrapper nodesConfig = createTestNodesConfig();
        KubernetesServicesConfigWrapper kubeServicesConfig = createTestKubernetesConfig();

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                "export MASTER_SERVICE_B_1=192.168.10.12\n" +
                "export MASTER_SERVICE_C_1=192.168.10.11\n" +
                "export MASTER_SERVICE_C_2=192.168.10.13\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty(), def));
    }

    private KubernetesServicesConfigWrapper createTestKubernetesConfig() {
        return new KubernetesServicesConfigWrapper(new HashMap<>() {{
            put("service_d_install", "on");
            put("service_d_cpu", "1");
            put("service_d_ram", "1024m");
        }});
    }

    NodesConfigWrapper createTestNodesConfig() {
        return new NodesConfigWrapper(new HashMap<>() {{
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

        NodesConfigWrapper nodesConfig = createTestNodesConfig();
        nodesConfig.setValueForPath("service_b", "1");

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                "export SELF_MASTER_SERVICE_B_1921681011=192.168.10.11\n" +
                "export SELF_MASTER_SERVICE_C_1921681011=192.168.10.11\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty(), def));
    }

    @Test
    public void testMasterElectionStrategyRandom() throws Exception {

        initRandomDependencies();

        NodesConfigWrapper nodesConfig = createTestNodesConfig();

        KubernetesServicesConfigWrapper kubeServicesConfig = createTestKubernetesConfig();

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                "export MASTER_SERVICE_B_1=192.168.10.12\n" +
                "export MASTER_SERVICE_C_1=192.168.10.11\n" +
                "export MASTER_SERVICE_C_2=192.168.10.13\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty(), def));
    }

    @Test
    public void testMasterElectionStrategyRandomNodeAfter() throws Exception {

        initRandomNodeAfterDependencies();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
                put("node_id1", "192.168.10.11");
                put("service_a1", "on");
                put("service_c1", "on");
                put("node_id2", "192.168.10.12");
                put("service_b", "1");
                put("node_id3", "192.168.10.13");
                put("service_b3", "on");
                put("service_c3", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                "export MASTER_SERVICE_B_1921681011=192.168.10.13\n" +
                "export MASTER_SERVICE_C_1921681011=192.168.10.13\n" +
                "export MASTER_SERVICE_C_1921681013=192.168.10.11\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty(), def));
    }

    @Test
    public void testMasterElectionStrategyRandomNodeAfterOrSame() throws Exception {

        initRandomNodeAfterOrSameDependencies();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("service_c1", "on");
            put("node_id2", "192.168.10.12");
            put("service_b", "1");
            put("node_id3", "192.168.10.13");
            put("service_b3", "on");
            put("service_c3", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                "export MASTER_SERVICE_B_1921681011=192.168.10.13\n" +
                "export MASTER_SERVICE_C_1921681011=192.168.10.13\n" +
                "export MASTER_SERVICE_C_1921681013=192.168.10.11\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty(), def));
    }

    @Test
    public void testMasterElectionStrategyRandomNodeAfterOrSameSingleNode() throws Exception {

        initRandomNodeAfterOrSameDependencies();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("service_b1", "on");
            put("service_c1", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                "export MASTER_SERVICE_B_1921681011=192.168.10.11\n" +
                "export MASTER_SERVICE_C_1921681011=192.168.10.11\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty(), def));
    }

    @Test
    public void testMasterElectionStrategyRandomNodeAfterChain() throws Exception {

        initRandomNodeAfterDependencies();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
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

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                "export MASTER_SERVICE_B_1921681011=192.168.10.12\n" +
                "export MASTER_SERVICE_C_1921681011=192.168.10.13\n" +
                "export MASTER_SERVICE_C_1921681012=192.168.10.13\n" +
                "export MASTER_SERVICE_C_1921681013=192.168.10.14\n" +
                "export MASTER_SERVICE_C_1921681014=192.168.10.11\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty(), def));
    }

    @Test
    public void testRealCaseGluster() throws Exception {

        NodeRangeResolver nrr = new NodeRangeResolver();

        def = new ServicesDefinitionImpl();
        def.setSetupService (setupService);

        ServiceDefinition serviceA = new ServiceDefinition("distributed-filesystem");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER);
        depA.setMasterService(Service.from ("distributed-filesystem"));
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
                put("node_id1", "192.168.10.11");
                put("node_id2", "192.168.10.13-192.168.10.14");
                put("node_id3", "192.168.10.12");
                put("distributed-filesystem1", "on");
                put("distributed-filesystem2", "on");
                put("distributed-filesystem3", "on");
        }});

        Topology topology = Topology.create(nrr.resolveRanges(nodesConfig), KubernetesServicesConfigWrapper.empty(),  def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                        "export MASTER_DISTRIBUTED_FILESYSTEM_1921681011=192.168.10.13\n" +
                        "export MASTER_DISTRIBUTED_FILESYSTEM_1921681012=192.168.10.14\n" +
                        "export MASTER_DISTRIBUTED_FILESYSTEM_1921681013=192.168.10.12\n" +
                        "export MASTER_DISTRIBUTED_FILESYSTEM_1921681014=192.168.10.11\n" +
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
                        "export ESKIMO_INSTALLED_cluster_dashboard_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_database_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_calculator_runtime_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_broker_manager_KUBERNETES_NODE=OK\n",
                topology.getTopologyScript(StandardSetupHelpers.getStandard2NodesInstallStatus(), def));
    }

    @Test
    public void testUserDefinition() throws Exception {

        initAdditionalEnvironment();

        def.getServiceDefinition(Service.from("service_a")).setUser(new ServiceUser("test", 1000));;
        def.getServiceDefinition(Service.from("service_b")).setUser(new ServiceUser("test", 1000));;

        def.getServiceDefinition(Service.from("service_c")).setUser(new ServiceUser("test2", 1001));;
        def.getServiceDefinition(Service.from("service_d")).setUser(new ServiceUser("test2", 1001));;

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("node_id2", "192.168.10.12");
            put("service_b", "1");
            put("node_id3", "192.168.10.13");
            put("service_b3", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                "\n" +
                "#Eskimo System Users\n" +
                "export ESKIMO_USERS=test:1000,test2:1001\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty(), def));
    }

    @Test
    public void testNoDependencies() throws Exception {

        initAdditionalEnvironment();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
                put("node_id1", "192.168.10.11");
                put("service_a1", "on");
                put("node_id2", "192.168.10.12");
                put("service_b", "1");
                put("node_id3", "192.168.10.13");
                put("service_b3", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty(), def));
    }

    @Test
    public void testConditionalDependency() throws Exception {

        initConditionalDependency();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("node_id2", "192.168.10.12");
            put("service_b", "1");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                "export MASTER_SERVICE_B_1=192.168.10.11\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty(), def));

        final NodesConfigWrapper nodesConfig2 = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("node_id2", "192.168.10.12");
            put("service_b", "1");
            put("service_d", "1");
        }});

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class,
                () -> Topology.create(nodesConfig2, KubernetesServicesConfigWrapper.empty(), def, null, Node.fromAddress("192.168.10.11"))
        );

        assertEquals ("Dependency service_c for service service_b could not found occurence 1", exception.getMessage());

        nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("node_id2", "192.168.10.12");
            put("service_b", "1");
            put("service_c", "2");
            put("service_d", "1");
        }});

        topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                "export MASTER_SERVICE_B_1=192.168.10.11\n" +
                "export MASTER_SERVICE_C_1=192.168.10.12\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty(), def));
    }

    @Test
    public void testAdditionalEnvironment() throws Exception {

        initAdditionalEnvironment();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
                put("node_id1", "192.168.10.11");
                put("service_a1", "on");
                put("node_id2", "192.168.10.12");
                put("service_a2", "on");
                put("service_c2", "on");
                put("node_id3", "192.168.10.13");
                put("service_c3", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, Node.fromAddress("192.168.10.11"));

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
                        "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13\n",
                topology.getTopologyScriptForNode (
                        nodesConfig,
                        createTestKubernetesConfig(),
                        ServicesInstallStatusWrapper.empty(),
                        def,
                        emptyModel, 1));

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
                        "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13\n",
                topology.getTopologyScriptForNode (
                        nodesConfig,
                        createTestKubernetesConfig(),
                        ServicesInstallStatusWrapper.empty(),
                        def,
                        emptyModel, 2));

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
                        "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13\n",
                topology.getTopologyScriptForNode (
                        nodesConfig,
                        createTestKubernetesConfig(),
                        ServicesInstallStatusWrapper.empty(),
                        def,
                        emptyModel, 3));
    }

    @Test
    public void testAdditionalEnvironmentWithKubeTopology() throws Exception {

        def.afterPropertiesSet();

        KubernetesServicesConfigWrapper kubeConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubeConfig.setValueForPath("cerebro_cpu", "1");
        kubeConfig.setValueForPath("cerebro_ram", "1024");
        kubeConfig.setValueForPath("kibana_cpu", "2");
        kubeConfig.setValueForPath("kibana_ram", "2048m");

        Topology topology = Topology.create(
                StandardSetupHelpers.getStandard2NodesSetup(),
                kubeConfig, def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                        "export MASTER_CLUSTER_MANAGER_1=192.168.10.13\n" +
                        "export MASTER_CLUSTER_MASTER_1=192.168.10.11\n" +
                        "export MASTER_DISTRIBUTED_FILESYSTEM_1=192.168.10.11\n" +
                        "export MASTER_DISTRIBUTED_TIME_1=192.168.10.11\n" +
                        "\n" +
                        "#Eskimo System Users\n" +
                        "export ESKIMO_USERS=broker:1002,calculator:1003,cluster:1001,database:1004\n" +
                        "\n" +
                        "#Additional Environment\n" +
                        "export ALL_NODES_LIST_cluster_slave=192.168.10.11,192.168.10.13\n" +
                        "export NODE_NBR_CLUSTER_SLAVE_1921681011=1\n" +
                        "export NODE_NBR_CLUSTER_SLAVE_1921681013=2\n" +
                        "export ALL_NODES_LIST_distributed_filesystem=192.168.10.11,192.168.10.13\n" +
                        "\n" +
                        "#Self identification\n" +
                        "export SELF_IP_ADDRESS=192.168.10.11\n" +
                        "export SELF_NODE_NUMBER=1\n" +
                        "export ESKIMO_NODE_COUNT=2\n" +
                        "export ALL_NODES_LIST=192.168.10.11,192.168.10.13\n" +
                        "\n" +
                        "#Kubernetes Topology\n" +
                        "export ESKIMO_KUBE_REQUEST_BROKER_MANAGER_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_BROKER_MANAGER_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_BROKER_MANAGER_DEPLOY_STRAT=CLUSTER_WIDE\n" +
                        "export ESKIMO_KUBE_REQUEST_BROKER_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_BROKER_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_BROKER_DEPLOY_STRAT=CLUSTER_WIDE\n" +
                        "export ESKIMO_KUBE_REQUEST_CALCULATOR_RUNTIME_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_CALCULATOR_RUNTIME_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_CALCULATOR_RUNTIME_DEPLOY_STRAT=CLUSTER_WIDE\n" +
                        "export ESKIMO_KUBE_REQUEST_CLUSTER_DASHBOARD_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_CLUSTER_DASHBOARD_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_CLUSTER_DASHBOARD_DEPLOY_STRAT=CLUSTER_WIDE\n" +
                        "export ESKIMO_KUBE_REQUEST_DATABASE_MANAGER_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_DATABASE_MANAGER_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_DATABASE_MANAGER_DEPLOY_STRAT=CLUSTER_WIDE\n" +
                        "export ESKIMO_KUBE_REQUEST_DATABASE_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_DATABASE_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_DATABASE_DEPLOY_STRAT=CLUSTER_WIDE\n" +
                        "export ESKIMO_KUBE_REQUEST_USER_CONSOLE_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_USER_CONSOLE_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_USER_CONSOLE_DEPLOY_STRAT=CLUSTER_WIDE\n",
                topology.getTopologyScriptForNode (
                        StandardSetupHelpers.getStandard2NodesSetup(),
                        kubeConfig,
                        ServicesInstallStatusWrapper.empty(),
                        def,
                        emptyModel, 1));
    }

    @Test
    public void testServiceInstallationInTopology() throws Exception {

        def.afterPropertiesSet();

        Topology topology = Topology.create(
                StandardSetupHelpers.getStandard2NodesSetup(),
                StandardSetupHelpers.getStandardKubernetesConfig(), def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                        "export MASTER_CLUSTER_MANAGER_1=192.168.10.13\n" +
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
                        "export ESKIMO_INSTALLED_cluster_dashboard_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_database_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_calculator_runtime_KUBERNETES_NODE=OK\n" +
                        "export ESKIMO_INSTALLED_broker_manager_KUBERNETES_NODE=OK\n" +
                        "\n" +
                        "#Eskimo System Users\n" +
                        "export ESKIMO_USERS=broker:1002,calculator:1003,cluster:1001,database:1004\n" +
                        "\n" +
                        "#Additional Environment\n" +
                        "export ALL_NODES_LIST_cluster_slave=192.168.10.11,192.168.10.13\n" +
                        "export NODE_NBR_CLUSTER_SLAVE_1921681011=1\n" +
                        "export NODE_NBR_CLUSTER_SLAVE_1921681013=2\n" +
                        "export ALL_NODES_LIST_distributed_filesystem=192.168.10.11,192.168.10.13\n" +
                        "\n" +
                        "#Self identification\n" +
                        "export SELF_IP_ADDRESS=192.168.10.11\n" +
                        "export SELF_NODE_NUMBER=1\n" +
                        "export ESKIMO_NODE_COUNT=2\n" +
                        "export ALL_NODES_LIST=192.168.10.11,192.168.10.13\n" +
                        "\n" +
                        "#Kubernetes Topology\n" +
                        "export ESKIMO_KUBE_REQUEST_BROKER_MANAGER_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_BROKER_MANAGER_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_BROKER_MANAGER_DEPLOY_STRAT=CLUSTER_WIDE\n" +
                        "export ESKIMO_KUBE_REQUEST_BROKER_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_BROKER_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_BROKER_DEPLOY_STRAT=CLUSTER_WIDE\n" +
                        "export ESKIMO_KUBE_REQUEST_CALCULATOR_RUNTIME_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_CALCULATOR_RUNTIME_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_CALCULATOR_RUNTIME_DEPLOY_STRAT=CLUSTER_WIDE\n" +
                        "export ESKIMO_KUBE_REQUEST_CLUSTER_DASHBOARD_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_CLUSTER_DASHBOARD_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_CLUSTER_DASHBOARD_DEPLOY_STRAT=CLUSTER_WIDE\n" +
                        "export ESKIMO_KUBE_REQUEST_DATABASE_MANAGER_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_DATABASE_MANAGER_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_DATABASE_MANAGER_DEPLOY_STRAT=CLUSTER_WIDE\n" +
                        "export ESKIMO_KUBE_REQUEST_DATABASE_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_DATABASE_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_DATABASE_DEPLOY_STRAT=CLUSTER_WIDE\n" +
                        "export ESKIMO_KUBE_REQUEST_USER_CONSOLE_CPU=1\n" +
                        "export ESKIMO_KUBE_REQUEST_USER_CONSOLE_RAM=800M\n" +
                        "export ESKIMO_KUBE_DEPLOYMENT_USER_CONSOLE_DEPLOY_STRAT=CLUSTER_WIDE\n",
                topology.getTopologyScriptForNode (
                        StandardSetupHelpers.getStandard2NodesSetup(),
                        StandardSetupHelpers.getStandardKubernetesConfig(),
                        StandardSetupHelpers.getStandard2NodesInstallStatus(),
                        def,
                        emptyModel, 1));
    }

    @Test
    public void testMemoryModel() throws Exception {

        MemoryModel memoryModel = new MemoryModel(new HashMap<>(){{
            put (Node.fromAddress("192.168.10.11"), new HashMap<>(){{
                put (Service.from("service_a"), Long.valueOf("100"));
                put (Service.from("service_b"), Long.valueOf("200"));
                put (Service.from("service_c"), Long.valueOf("300"));
            }});
        }});

        initAdditionalNodeList();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("service_b1", "on");
            put("service_c1", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, Node.fromAddress("192.168.10.11"));

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
                        "export MEMORY_SERVICE_C=300\n",
                topology.getTopologyScriptForNode (
                        nodesConfig,
                        createTestKubernetesConfig(),
                        ServicesInstallStatusWrapper.empty(), def, memoryModel, 1));
    }


    @Test
    public void testMemoryModelWithAdditional() throws Exception {

        MemoryModel memoryModel = new MemoryModel(new HashMap<>(){{
            put (Node.fromAddress("192.168.10.11"), new HashMap<>(){{
                put (Service.from("service_a"), Long.valueOf("100"));
                put (Service.from("service_b"), Long.valueOf("200"));
                put (Service.from("service_c"), Long.valueOf("300"));
            }});
        }});


        initAdditionalNodeListWithAdditionalMemory();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("service_b1", "on");
        }});

        Topology topology = Topology.create(nodesConfig, KubernetesServicesConfigWrapper.empty(), def, null, Node.fromAddress("192.168.10.11"));

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
                        "export MEMORY_SERVICE_C=300\n",
                topology.getTopologyScriptForNode (nodesConfig,
                        createTestKubernetesConfig(),
                        ServicesInstallStatusWrapper.empty(), def, memoryModel, 1));
    }


    @Test
    public void testAdditionalEnvironmentNodeList() throws Exception {

        initAdditionalNodeList();

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
                put("node_id5", "192.168.10.15");
                put("service_a5", "on");
                put("service_c5", "on");
        }});

        KubernetesServicesConfigWrapper kubeServicesConfig = createTestKubernetesConfig();

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, Node.fromAddress("192.168.10.11"));

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
                        "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13,192.168.10.14,192.168.10.15\n",
                topology.getTopologyScriptForNode (
                        nodesConfig,
                        createTestKubernetesConfig(),
                        ServicesInstallStatusWrapper.empty(), def, emptyModel, 1));

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
                        "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13,192.168.10.14,192.168.10.15\n",
                topology.getTopologyScriptForNode (
                        nodesConfig,
                        createTestKubernetesConfig(),
                        ServicesInstallStatusWrapper.empty(),
                        def,
                        emptyModel, 3));
    }

    @Test
    public void testGetVariableName() {
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.FIRST_NODE);
        depA.setMasterService(Service.from("mesos-master"));
        depA.setNumberOfMasters(1);

        Topology topology = new Topology();

        assertEquals ("MESOS_MASTER", topology.getVariableName(depA));

    }

    @Test
    public void testPersistentEnvironment() throws Exception {

        initAdditionalEnvironment();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
                put("node_id1", "192.168.10.11");
                put("service_a1", "on");
                put("node_id2", "192.168.10.12");
                put("service_a2", "on");
                put("service_c2", "on");
                put("node_id3", "192.168.10.13");
                put("service_c3", "on");
        }});

        KubernetesServicesConfigWrapper kubeServicesConfig = createTestKubernetesConfig();

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, Node.fromAddress("192.168.10.11"));

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
                        "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13\n",
                topology.getTopologyScriptForNode (
                        nodesConfig,
                        kubeServicesConfig,
                        ServicesInstallStatusWrapper.empty(), def, emptyModel, 2));

        // now change topology and ensure node numbers for services A and C are unchanged
        nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
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

        topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, Node.fromAddress("192.168.10.11"));

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
                        "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13,192.168.10.14,192.168.10.15\n",
                topology.getTopologyScriptForNode (
                        nodesConfig,
                        kubeServicesConfig,
                        ServicesInstallStatusWrapper.empty(), def, emptyModel, 3));
    }

    @Test
    public void testKubernetesServiceUnsupportedDependencies() {

        ServiceDefinition serviceA = new ServiceDefinition("service_a");
        Dependency depA = new Dependency();
        depA.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER);
        depA.setMasterService(Service.from("service_b"));
        depA.setNumberOfMasters(1);
        serviceA.addDependency (depA);
        def.addService(serviceA);

        ServiceDefinition serviceB = new ServiceDefinition("service_b");
        def.addService(serviceB);

        ServiceDefinition serviceD = new ServiceDefinition("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.SAME_NODE);
        depD.setMasterService(Service.from("service_b"));
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("node_id2", "192.168.10.12");
            put("service_a2", "on");
            put("node_id3", "192.168.10.13");
            put("service_b3", "on");
        }});

        KubernetesServicesConfigWrapper kubeServicesConfig = createTestKubernetesConfig();

        ServiceDefinitionException exception = assertThrows(ServiceDefinitionException.class,
                () -> Topology.create(nodesConfig, kubeServicesConfig,  def, null, Node.fromAddress("192.168.10.11")));

        assertEquals("Service service_d defines a SAME_NODE dependency on service_b, which is not supported for kubernetes services", exception.getMessage());

        depD.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER);

        exception = assertThrows(ServiceDefinitionException.class,
                () -> Topology.create(nodesConfig, kubeServicesConfig, def, null, Node.fromAddress("192.168.10.11")));

        assertEquals("Service service_d defines a RANDOM_NODE_AFTER dependency on service_b, which is not supported for kubernetes services", exception.getMessage());

        depD.setMes(MasterElectionStrategy.RANDOM_NODE_AFTER_OR_SAME);

        exception = assertThrows(ServiceDefinitionException.class,
                () -> Topology.create(nodesConfig, kubeServicesConfig, def, null, Node.fromAddress("192.168.10.11")));

        assertEquals("Service service_d defines a RANDOM_NODE_AFTER_OR_SAME dependency on service_b, which is not supported for kubernetes services", exception.getMessage());
    }

    @Test
    public void testKubernetesOnKubernetesDependencies() throws Exception {

        ServiceDefinition serviceA = new ServiceDefinition("service_a");
        def.addService(serviceA);

        ServiceDefinition serviceB = new ServiceDefinition("service_b");
        serviceB.setKubernetes(true);
        def.addService(serviceB);

        ServiceDefinition serviceD = new ServiceDefinition("service_d");

        serviceD.setKubernetes(true);
        Dependency depD = new Dependency();
        depD.setMes(MasterElectionStrategy.RANDOM);
        depD.setMasterService(Service.from("service_b"));
        depD.setNumberOfMasters(1);
        serviceD.addDependency (depD);
        def.addService(serviceD);

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("node_id2", "192.168.10.12");
            put("service_a2", "on");
            put("node_id3", "192.168.10.13");
        }});

        KubernetesServicesConfigWrapper kubeServicesConfig = createTestKubernetesConfig();

        ServiceDefinitionException exception = assertThrows(ServiceDefinitionException.class,
                () -> Topology.create(nodesConfig, kubeServicesConfig,  def, null, Node.fromAddress("192.168.10.11")));

        assertEquals("Service service_d defines a dependency on another kube service service_b but that service is not going to be installed.", exception.getMessage());

        kubeServicesConfig.setValueForPath("service_b_install", "on");

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig,  def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                        "\n" +
                        "#Additional Environment\n" +
                        "\n" +
                        "#Self identification\n" +
                        "export SELF_IP_ADDRESS=192.168.10.13\n" +
                        "export SELF_NODE_NUMBER=3\n" +
                        "export ESKIMO_NODE_COUNT=3\n" +
                        "export ALL_NODES_LIST=192.168.10.11,192.168.10.12,192.168.10.13\n",
                topology.getTopologyScriptForNode (
                        nodesConfig,
                        createTestKubernetesConfig(),
                        ServicesInstallStatusWrapper.empty(), def, emptyModel, 3));
    }

    @Test
    public void testKubernetesServiceDependencies() throws Exception {

        initRandomDependenciesFewer();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("service_a1", "on");
            put("service_c1", "on");
            put("node_id2", "192.168.10.12");
            put("node_id3", "192.168.10.13");
            put("service_c3", "on");
            put("node_id4", "192.168.10.14");
            put("service_c4", "on");
        }});

        KubernetesServicesConfigWrapper kubeServicesConfig = createTestKubernetesConfig();

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                "export MASTER_SERVICE_C_1=192.168.10.11\n" +
                "export MASTER_SERVICE_C_2=192.168.10.13\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty(), def));
    }


    @Test
    public void testConsolidatedServiceDependencies() throws Exception {

        initRandomDependencies();

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
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

        KubernetesServicesConfigWrapper kubeServicesConfig = createTestKubernetesConfig();

        Topology topology = Topology.create(nodesConfig, kubeServicesConfig, def, null, Node.fromAddress("192.168.10.11"));

        assertEquals ("#Topology\n" +
                "export MASTER_SERVICE_B_1=192.168.10.11\n" +
                "export MASTER_SERVICE_C_1=192.168.10.11\n" +
                "export MASTER_SERVICE_C_2=192.168.10.13\n", topology.getTopologyScript(ServicesInstallStatusWrapper.empty(), def));
    }
}

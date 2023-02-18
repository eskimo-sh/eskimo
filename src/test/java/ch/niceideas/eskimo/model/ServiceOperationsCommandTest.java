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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.services.satellite.ServicesInstallationSorter;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-conf", "test-setup", "test-services"})
public class ServiceOperationsCommandTest {

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Autowired
    private ServicesInstallationSorter servicesInstallationSorter;

    @Test
    public void testNoChanges() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        assertEquals(0, oc.getInstallations().size());
        assertEquals(0, oc.getUninstallations().size());
        assertEquals(0, oc.getRestarts().size());
    }

    @Test
    public void testInstallationKubeMaster() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("cluster-master_installed_on_IP_192-168-10-11");

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        assertEquals(1, oc.getInstallations().size());
        assertEquals(Service.from("cluster-master"), oc.getInstallations().get(0).getService());
        assertEquals(Node.fromAddress("192.168.10.11"), oc.getInstallations().get(0).getNode());

        assertEquals(0, oc.getUninstallations().size());

        assertEquals(2, oc.getRestarts().size());
        assertEquals(Service.from("cluster-slave"), oc.getRestarts().get(0).getService());
        assertEquals(Node.fromAddress("192.168.10.11"), oc.getRestarts().get(0).getNode());
    }

    @Test
    public void testUninstallationKubeMaster() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("cluster-master");

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        assertEquals(0, oc.getInstallations().size());

        assertEquals(1, oc.getUninstallations().size());
        assertEquals(Service.from("cluster-master"), oc.getUninstallations().get(0).getService());
        assertEquals(Node.fromAddress("192.168.10.11"), oc.getUninstallations().get(0).getNode());

        assertEquals(2, oc.getRestarts().size());
        assertEquals(Service.from("cluster-slave"), oc.getRestarts().get(0).getService());
        assertEquals(Node.fromAddress("192.168.10.11"), oc.getRestarts().get(0).getNode());
    }

    @Test
    public void testRestartMany() throws Exception {

        ServiceOperationsCommand oc = prepareThreeOps();

        System.err.println (oc.toJSON());

        assertTrue (new JSONObject(
                "{\"restarts\":[{\"distributed-filesystem\":\"192.168.10.11\"},{\"distributed-filesystem\":\"192.168.10.13\"},{\"database\":\"(kubernetes)\"},{\"broker\":\"(kubernetes)\"},{\"broker-manager\":\"(kubernetes)\"},{\"user-console\":\"(kubernetes)\"}]," +
                        "\"uninstallations\":[{\"cluster-manager\":\"192.168.10.13\"},{\"cluster-slave\":\"192.168.10.11\"},{\"distributed-time\":\"192.168.10.11\"}]," +
                        "\"installations\":[{\"distributed-time\":\"192.168.10.13\"},{\"cluster-manager\":\"192.168.10.11\"}]}")
                .similar(oc.toJSON()));
    }

    @Test
    public void testRestartOnlySameNode() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("distributed-filesystem_installed_on_IP_192-168-10-11");

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        assertEquals(1, oc.getInstallations().size());

        assertEquals(Service.from("distributed-filesystem"), oc.getInstallations().get(0).getService());
        assertEquals(Node.fromAddress("192.168.10.11"), oc.getInstallations().get(0).getNode());

        assertEquals(0, oc.getUninstallations().size());

        assertEquals(3, oc.getRestarts().size());

        assertEquals (
                "cluster-slave=192.168.10.11\n" +
                        "cluster-master=192.168.10.11\n" +
                        "user-console=(kubernetes)"
                , oc.getRestarts().stream()
                        .map(operationId -> operationId.getService()+"="+operationId.getNode())
                        .collect(Collectors.joining("\n")));
    }

    @Test
    public void testMoveServices() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = new ServicesInstallStatusWrapper (new HashMap<>() {{
            put ("database-manager_installed_on_IP_KUBERNETES_NODE", "OK");
            put ("distributed-time_installed_on_IP_192-168-10-11", "OK");
            put ("cluster-manager_installed_on_IP_192-168-10-11", "OK");
        }});

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put ("node_id1", "192.168.10.11");
            put ("node_id2", "192.168.10.13");
            put ("cluster-manager2", "on");
            put ("distributed-time2", "on");
        }} );

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        System.err.println (oc.toJSON());

        assertTrue (new JSONObject(
                "{\"restarts\":[]," +
                        "\"uninstallations\":[{\"cluster-manager\":\"192.168.10.11\"},{\"distributed-time\":\"192.168.10.11\"}]," +
                        "\"installations\":[{\"distributed-time\":\"192.168.10.13\"},{\"cluster-manager\":\"192.168.10.13\"}]}")
                .similar(oc.toJSON()));

    }

    @Test
    public void testBuggyScenario() throws Exception {

        InputStream servicesConfigStream = ResourceUtils.getResourceAsStream("OperationsCommandTest/nodes-config.json");
        NodesConfigWrapper newNodesConfig = new NodesConfigWrapper(StreamUtils.getAsString(servicesConfigStream, StandardCharsets.UTF_8));

        InputStream nodesStatusStream = ResourceUtils.getResourceAsStream("OperationsCommandTest/nodes-status.json");
        ServicesInstallStatusWrapper status = new ServicesInstallStatusWrapper(StreamUtils.getAsString(nodesStatusStream, StandardCharsets.UTF_8));

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, status, newNodesConfig);

        assertNotNull(oc);

        assertTrue(oc.getUninstallations().isEmpty());

        // new installations on .15, .16, .17, .18
        assertEquals(24, oc.getInstallations().size());
    }


    @Test
    public void testRecoverUninstallationWhenNodeDownMiddleUninstall() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("distributed-time_installed_on_IP_192-168-10-13");

        // 1. some services are uninstalled from a node, one service is moved
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("cluster-slave1");
        nodesConfig.getJSONObject().remove("distribted-time1");

        nodesConfig.setValueForPath("cluster-manager", "1");

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        System.err.println (oc.toJSON());

        assertTrue (new JSONObject(
                "{\"restarts\":[{\"distributed-time\":\"192.168.10.11\"},{\"distributed-filesystem\":\"192.168.10.11\"},{\"distributed-filesystem\":\"192.168.10.13\"},{\"database\":\"(kubernetes)\"},{\"broker\":\"(kubernetes)\"},{\"broker-manager\":\"(kubernetes)\"},{\"user-console\":\"(kubernetes)\"}]," +
                        "\"uninstallations\":[{\"cluster-manager\":\"192.168.10.13\"},{\"cluster-slave\":\"192.168.10.11\"}]," +
                        "\"installations\":[{\"distributed-time\":\"192.168.10.13\"},{\"cluster-manager\":\"192.168.10.11\"}]}")
                .similar(oc.toJSON()));

        // node vanished
        // uninstallation fails in the middle (after cluster-manager)
        savedServicesInstallStatus.setValueForPath("cluster-manager_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("distributed-time_installed_on_IP_192-168-10-13", "OK");
        savedServicesInstallStatus.getJSONObject().remove("distributed-time_installed_on_IP_192-168-10-11");

        // flag all restarts
        savedServicesInstallStatus.setValueForPath("distributed-time_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("cluster-master_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("cluster-slave_installed_on_IP_192-168-10-13", "restart");

        ServiceOperationsCommand oc2 = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        System.err.println (oc2.toJSON());

        assertTrue (new JSONObject(
                "{\"restarts\":[{\"cluster-manager\":\"192.168.10.11\"},{\"distributed-time\":\"192.168.10.11\"},{\"distributed-filesystem\":\"192.168.10.11\"},{\"distributed-filesystem\":\"192.168.10.13\"},{\"cluster-master\":\"192.168.10.11\"},{\"cluster-slave\":\"192.168.10.13\"},{\"cluster-master\":\"192.168.10.13\"},{\"database\":\"(kubernetes)\"},{\"broker\":\"(kubernetes)\"},{\"broker-manager\":\"(kubernetes)\"},{\"user-console\":\"(kubernetes)\"}]," +
                        "\"uninstallations\":[{\"cluster-manager\":\"192.168.10.13\"},{\"cluster-master\":\"192.168.10.13\"},{\"cluster-slave\":\"192.168.10.11\"}]," +
                        "\"installations\":[]}")
                .similar(oc2.toJSON()));
    }

    @Test
    public void testRecoverUninstallationWhenNodeDownAfterUninstall() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("distributed-time_installed_on_IP_192-168-10-13");

        // 1. some services are uninstalled from a node, one service is moved
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("cluster-slave1");
        nodesConfig.getJSONObject().remove("distributed-time1");

        nodesConfig.setValueForPath("cluster-manager", "1");

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        System.err.println (oc.toJSON());

        assertTrue(new JSONObject(
                "{\"restarts\":[{\"distributed-filesystem\":\"192.168.10.11\"},{\"distributed-filesystem\":\"192.168.10.13\"},{\"database\":\"(kubernetes)\"},{\"broker\":\"(kubernetes)\"},{\"broker-manager\":\"(kubernetes)\"},{\"user-console\":\"(kubernetes)\"}]," +
                        "\"uninstallations\":[{\"cluster-manager\":\"192.168.10.13\"},{\"cluster-slave\":\"192.168.10.11\"},{\"distributed-time\":\"192.168.10.11\"}]," +
                        "\"installations\":[{\"distributed-time\":\"192.168.10.13\"},{\"cluster-manager\":\"192.168.10.11\"}]}")
                .similar(oc.toJSON()));

        // node vanished
        // uninstallation fails in the middle (after cluster-manager)
        savedServicesInstallStatus.setValueForPath("cluster-manager_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("distributed-time_installed_on_IP_192-168-10-13", "OK");
        savedServicesInstallStatus.getJSONObject().remove("cluster-manager_installed_on_IP_192-168-10-13");
        savedServicesInstallStatus.getJSONObject().remove("cluster-slave_installed_on_IP_192-168-10-11");
        savedServicesInstallStatus.getJSONObject().remove("distributed-time_installed_on_IP_192-168-10-11");

        // flag all restarts
        savedServicesInstallStatus.setValueForPath("distributed-time_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("cluster-master_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("cluster-slave_installed_on_IP_192-168-10-13", "restart");

        ServiceOperationsCommand oc2 = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        System.err.println (oc2.toJSON());

        assertTrue (new JSONObject(
                "{\"restarts\":[{\"distributed-time\":\"192.168.10.13\"},{\"cluster-master\":\"192.168.10.11\"},{\"cluster-slave\":\"192.168.10.13\"},{\"cluster-master\":\"192.168.10.13\"}]," +
                        "\"uninstallations\":[{\"cluster-master\":\"192.168.10.13\"}]," +
                        "\"installations\":[]}")
                .similar(oc2.toJSON()));
    }

    @Test
    public void testRecoverUninstallationWhenNodeDownMiddleRestart() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("distributed-time_installed_on_IP_192-168-10-13");

        // 1. some services are uninstalled from a node, one service is moved
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("cluster-slave1");
        nodesConfig.getJSONObject().remove("distributed-time1");

        nodesConfig.setValueForPath("cluster-manager", "1");

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        System.err.println (oc.toJSON());

        assertTrue(new JSONObject(
                "{\"restarts\":[{\"distributed-filesystem\":\"192.168.10.11\"},{\"distributed-filesystem\":\"192.168.10.13\"},{\"database\":\"(kubernetes)\"},{\"broker\":\"(kubernetes)\"},{\"broker-manager\":\"(kubernetes)\"},{\"user-console\":\"(kubernetes)\"}]," +
                        "\"uninstallations\":[{\"cluster-manager\":\"192.168.10.13\"},{\"cluster-slave\":\"192.168.10.11\"},{\"distributed-time\":\"192.168.10.11\"}]," +
                        "\"installations\":[{\"distributed-time\":\"192.168.10.13\"},{\"cluster-manager\":\"192.168.10.11\"}]}")
                .similar(oc.toJSON()));


        // node vanished
        // uninstallation fails in the middle (after cluster-manager)
        savedServicesInstallStatus.setValueForPath("cluster-manager_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("distributed-time_installed_on_IP_192-168-10-13", "OK");
        savedServicesInstallStatus.getJSONObject().remove("cluster-manager_installed_on_IP_192-168-10-13");
        savedServicesInstallStatus.getJSONObject().remove("cluster-slave_installed_on_IP_192-168-10-11");
        savedServicesInstallStatus.getJSONObject().remove("distributed-time_installed_on_IP_192-168-10-11");

        // flag all restarts
        savedServicesInstallStatus.setValueForPath("cluster-master_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("cluster-slave_installed_on_IP_192-168-10-13", "restart");

        // some restarts done
        savedServicesInstallStatus.setValueForPath("distributed-filesystem_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("distributed-filesystem_installed_on_IP_192-168-10-13", "OK");
        savedServicesInstallStatus.setValueForPath("cluster-master_installed_on_IP_192-168-10-13", "OK");

        ServiceOperationsCommand oc2 = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        System.err.println (oc2.toJSON());

        assertTrue (new JSONObject(
                "{\"restarts\":[{\"cluster-master\":\"192.168.10.11\"},{\"cluster-slave\":\"192.168.10.13\"}]," +
                        "\"uninstallations\":[{\"cluster-master\":\"192.168.10.13\"}]," +
                        "\"installations\":[]}")
                .similar(oc2.toJSON()));
    }

    @Test
    public void testToJSON () throws Exception {

        ServiceOperationsCommand oc = prepareThreeOps();

        assertEquals ("{\n" +
                "  \"restarts\": [\n" +
                "    {\"distributed-filesystem\": \"192.168.10.11\"},\n" +
                "    {\"distributed-filesystem\": \"192.168.10.13\"},\n" +
                "    {\"database\": \"(kubernetes)\"},\n" +
                "    {\"broker\": \"(kubernetes)\"},\n" +
                "    {\"broker-manager\": \"(kubernetes)\"},\n" +
                "    {\"user-console\": \"(kubernetes)\"}\n" +
                "  ],\n" +
                "  \"uninstallations\": [\n" +
                "    {\"cluster-manager\": \"192.168.10.13\"},\n" +
                "    {\"cluster-slave\": \"192.168.10.11\"},\n" +
                "    {\"distributed-time\": \"192.168.10.11\"}\n" +
                "  ],\n" +
                "  \"installations\": [\n" +
                "    {\"distributed-time\": \"192.168.10.13\"},\n" +
                "    {\"cluster-manager\": \"192.168.10.11\"}\n" +
                "  ]\n" +
                "}", oc.toJSON().toString(2));
    }

    private ServiceOperationsCommand prepareThreeOps() throws NodesConfigurationException {
        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("distributed-time_installed_on_IP_192-168-10-13");

        // 1. some services are uninstalled from a node, one service is moved
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("cluster-slave1");
        nodesConfig.getJSONObject().remove("distributed-time1");

        nodesConfig.setValueForPath("cluster-manager", "1");

        return ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);
    }

    @Test
    public void testGetAllOperationsInOrder() throws Exception {

        ServiceOperationsCommand oc = prepareThreeOps();

        List<ServiceOperationsCommand.ServiceOperationId> opsInOrder =  oc.getAllOperationsInOrder(new OperationsContext() {
            @Override
            public ServicesInstallationSorter getServicesInstallationSorter() {
                return servicesInstallationSorter;
            }

            @Override
            public NodesConfigWrapper getNodesConfig() {
                NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
                nodesConfig.getJSONObject().remove("cluster-slave1");
                nodesConfig.getJSONObject().remove("distributed-time1");
                return nodesConfig;
            }
        });

        assertEquals(
                "Check--Install_Base-System_192-168-10-11,\n" +
                        "Check--Install_Base-System_192-168-10-13,\n" +
                        "installation_cluster-manager_192-168-10-11,\n" +
                        "uninstallation_cluster-manager_192-168-10-13,\n" +
                        "installation_distributed-time_192-168-10-13,\n" +
                        "uninstallation_distributed-time_192-168-10-11,\n" +
                        "restart_distributed-filesystem_192-168-10-11,\n" +
                        "restart_distributed-filesystem_192-168-10-13,\n" +
                        "uninstallation_cluster-slave_192-168-10-11,\n" +
                        "restart_database_kubernetes,\n" +
                        "restart_broker_kubernetes,\n" +
                        "restart_broker-manager_kubernetes,\n" +
                        "restart_user-console_kubernetes",
                opsInOrder.stream()
                        .map(ServiceOperationsCommand.ServiceOperationId::toString)
                        .collect(Collectors.joining(","))
                        .replace(",", ",\n"));
    }

}

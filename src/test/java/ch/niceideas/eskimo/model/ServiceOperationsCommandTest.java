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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.services.satellite.ServicesInstallationSorter;
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
@ActiveProfiles({"no-web-stack"})
public class ServiceOperationsCommandTest {

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

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
        savedServicesInstallStatus.getJSONObject().remove("kube-master_installed_on_IP_192-168-10-11");

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        assertEquals(1, oc.getInstallations().size());
        assertEquals("kube-master", oc.getInstallations().get(0).getService());
        assertEquals("192.168.10.11", oc.getInstallations().get(0).getNode());

        assertEquals(0, oc.getUninstallations().size());

        assertEquals(9, oc.getRestarts().size());
        assertEquals("kube-slave", oc.getRestarts().get(0).getService());
        assertEquals("192.168.10.11", oc.getRestarts().get(0).getNode());
    }

    @Test
    public void testUninstallationKubeMaster() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("kube-master");

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        assertEquals(0, oc.getInstallations().size());

        assertEquals(1, oc.getUninstallations().size());
        assertEquals("kube-master", oc.getUninstallations().get(0).getService());
        assertEquals("192.168.10.11", oc.getUninstallations().get(0).getNode());

        assertEquals(9, oc.getRestarts().size());
        assertEquals("kube-slave", oc.getRestarts().get(0).getService());
        assertEquals("192.168.10.11", oc.getRestarts().get(0).getNode());
    }

    @Test
    public void testRestartMany() throws Exception {

        ServiceOperationsCommand oc = prepareFiveOps();

        System.err.println (oc.toJSON());

        assertTrue (new JSONObject(
                "{\"restarts\":[{\"gluster\":\"192.168.10.11\"},{\"gluster\":\"192.168.10.13\"},{\"elasticsearch\":\"(kubernetes)\"},{\"kafka\":\"(kubernetes)\"},{\"kafka-manager\":\"(kubernetes)\"},{\"logstash\":\"(kubernetes)\"},{\"zeppelin\":\"(kubernetes)\"}]," +
                        "\"uninstallations\":[{\"etcd\":\"192.168.10.11\"},{\"kube-slave\":\"192.168.10.11\"},{\"zookeeper\":\"192.168.10.13\"}]," +
                        "\"installations\":[{\"zookeeper\":\"192.168.10.11\"},{\"etcd\":\"192.168.10.13\"}]}")
                .similar(oc.toJSON()));
    }

    @Test
    public void testRestartOnlySameNode() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("gluster_installed_on_IP_192-168-10-11");

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        assertEquals(1, oc.getInstallations().size());

        assertEquals("gluster", oc.getInstallations().get(0).getService());
        assertEquals("192.168.10.11", oc.getInstallations().get(0).getNode());

        assertEquals(0, oc.getUninstallations().size());

        assertEquals(6, oc.getRestarts().size());

        assertEquals (
                "etcd=192.168.10.11, kube-master=192.168.10.11, kube-slave=192.168.10.11, spark-console=(kubernetes), logstash=(kubernetes), zeppelin=(kubernetes)"
                , oc.getRestarts().stream()
                        .map(operationId -> operationId.getService()+"="+operationId.getNode())
                        .collect(Collectors.joining(", ")));
    }

    @Test
    public void testMoveServices() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = new ServicesInstallStatusWrapper (new HashMap<>() {{
            put ("cerebro_installed_on_IP_KUBERNETES_NODE", "OK");
            put ("ntp_installed_on_IP_192-168-10-11", "OK");
            put ("etcd_installed_on_IP_192-168-10-11", "OK");
        }});

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put ("node_id1", "192.168.10.11");
            put ("node_id2", "192.168.10.13");
            put ("etcd2", "on");
            put ("ntp2", "on");
        }} );

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        //System.err.println (oc.toJSON());

        assertTrue (new JSONObject("{" +
                "\"restarts\":[]," +
                "\"uninstallations\":[{\"etcd\":\"192.168.10.11\"},{\"ntp\":\"192.168.10.11\"}]," +
                "\"installations\":[{\"ntp\":\"192.168.10.13\"},{\"etcd\":\"192.168.10.13\"}]}")
                .similar(oc.toJSON()));

    }

    @Test
    public void testToJSON() throws Exception {

        ServiceOperationsCommand oc = new ServiceOperationsCommand(NodesConfigWrapper.empty());

        oc.addInstallation(new ServiceOperationsCommand.ServiceOperationId("installation", "elasticsearch", "192.168.10.11"));
        oc.addInstallation(new ServiceOperationsCommand.ServiceOperationId("installation", "kibana", "192.168.10.11"));
        oc.addInstallation(new ServiceOperationsCommand.ServiceOperationId("installation", "cerebro", "192.168.10.11"));

        oc.addUninstallation(new ServiceOperationsCommand.ServiceOperationId("uninstallation", "cerebro", "192.168.10.13"));
        oc.addUninstallation(new ServiceOperationsCommand.ServiceOperationId("uninstallation", "kibana", "192.168.10.13"));
        oc.addUninstallation(new ServiceOperationsCommand.ServiceOperationId("uninstallation", "logstash", "192.168.10.13"));

        oc.addRestartIfNotInstalled("zeppelin", "192.168.10.13");

        assertTrue(new JSONObject("{\n" +
                "  \"installations\": [\n" +
                "    {\"elasticsearch\": \"192.168.10.11\"},\n" +
                "    {\"kibana\": \"192.168.10.11\"},\n" +
                "    {\"cerebro\": \"192.168.10.11\"}\n" +
                "  ],\n" +
                "  \"restarts\": [{\"zeppelin\": \"192.168.10.13\"}],\n" +
                "  \"uninstallations\": [\n" +
                "    {\"cerebro\": \"192.168.10.13\"},\n" +
                "    {\"kibana\": \"192.168.10.13\"},\n" +
                "    {\"logstash\": \"192.168.10.13\"}\n" +
                "  ]\n" +
                "}").similar(oc.toJSON()));
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
        assertEquals(32, oc.getInstallations().size());
    }


    @Test
    public void testRecoverUninstallationWhenNodeDownMiddleUninstall() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("etcd_installed_on_IP_192-168-10-13");

        // 1. some services are uninstalled from a node, one service is moved
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("kube-slave1");
        nodesConfig.getJSONObject().remove("etcd1");

        nodesConfig.setValueForPath("zookeeper", "1");

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        //System.err.println (oc.toJSON());

        assertTrue (new JSONObject("{" +
                "\"restarts\":[{\"gluster\":\"192.168.10.11\"},{\"gluster\":\"192.168.10.13\"},{\"elasticsearch\":\"(kubernetes)\"},{\"kafka\":\"(kubernetes)\"},{\"kafka-manager\":\"(kubernetes)\"},{\"logstash\":\"(kubernetes)\"},{\"zeppelin\":\"(kubernetes)\"}]," +
                "\"uninstallations\":[{\"etcd\":\"192.168.10.11\"},{\"kube-slave\":\"192.168.10.11\"},{\"zookeeper\":\"192.168.10.13\"}]," +
                "\"installations\":[{\"zookeeper\":\"192.168.10.11\"},{\"etcd\":\"192.168.10.13\"}]}")
                .similar(oc.toJSON()));

        // node vanished
        // uninstallation fails in the middle (after zookeeper)
        savedServicesInstallStatus.setValueForPath("zookeeper_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("etcd_installed_on_IP_192-168-10-13", "OK");
        savedServicesInstallStatus.getJSONObject().remove("etcd_installed_on_IP_192-168-10-11");

        // flag all restarts
        savedServicesInstallStatus.setValueForPath("etcd_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("kube-master_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("kube-slave_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("logstash_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("logstash_installed_on_IP_192-168-10-13", "restart");

        ServiceOperationsCommand oc2 = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        System.err.println (oc2.toJSON());

        assertTrue (new JSONObject(
                "{\"restarts\":[{\"zookeeper\":\"192.168.10.11\"},{\"gluster\":\"192.168.10.11\"},{\"gluster\":\"192.168.10.13\"},{\"etcd\":\"192.168.10.11\"},{\"kube-master\":\"192.168.10.11\"},{\"kube-master\":\"192.168.10.13\"},{\"kube-slave\":\"192.168.10.13\"},{\"elasticsearch\":\"(kubernetes)\"},{\"cerebro\":\"(kubernetes)\"},{\"spark-console\":\"(kubernetes)\"},{\"kafka\":\"(kubernetes)\"},{\"kafka-manager\":\"(kubernetes)\"},{\"logstash\":\"(kubernetes)\"},{\"zeppelin\":\"(kubernetes)\"}]," +
                        "\"uninstallations\":[{\"etcd\":\"192.168.10.11\"},{\"kube-master\":\"192.168.10.13\"},{\"kube-slave\":\"192.168.10.11\"},{\"zookeeper\":\"192.168.10.13\"}]," +
                        "\"installations\":[]}")
                .similar(oc2.toJSON()));
    }

    @Test
    public void testRecoverUninstallationWhenNodeDownAfterUninstall() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("etcd_installed_on_IP_192-168-10-13");

        // 1. some services are uninstalled from a node, one service is moved
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("kube-slave1");
        nodesConfig.getJSONObject().remove("etcd1");

        nodesConfig.setValueForPath("zookeeper", "1");

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        //System.err.println (oc.toJSON());

        assertTrue(new JSONObject("{" +
                "\"restarts\":[{\"gluster\":\"192.168.10.11\"},{\"gluster\":\"192.168.10.13\"},{\"elasticsearch\":\"(kubernetes)\"},{\"kafka\":\"(kubernetes)\"},{\"kafka-manager\":\"(kubernetes)\"},{\"logstash\":\"(kubernetes)\"},{\"zeppelin\":\"(kubernetes)\"}]," +
                "\"uninstallations\":[{\"etcd\":\"192.168.10.11\"},{\"kube-slave\":\"192.168.10.11\"},{\"zookeeper\":\"192.168.10.13\"}]," +
                "\"installations\":[{\"zookeeper\":\"192.168.10.11\"},{\"etcd\":\"192.168.10.13\"}]}")
                .similar(oc.toJSON()));

        // node vanished
        // uninstallation fails in the middle (after zookeeper)
        savedServicesInstallStatus.setValueForPath("zookeeper_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("etcd_installed_on_IP_192-168-10-13", "OK");
        savedServicesInstallStatus.getJSONObject().remove("zookeeper_installed_on_IP_192-168-10-13");
        savedServicesInstallStatus.getJSONObject().remove("kube-slave_installed_on_IP_192-168-10-11");
        savedServicesInstallStatus.getJSONObject().remove("etcd_installed_on_IP_192-168-10-11");

        // flag all restarts
        savedServicesInstallStatus.setValueForPath("etcd_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("kube-master_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("kube-slave_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("logstash_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("logstash_installed_on_IP_192-168-10-13", "restart");

        ServiceOperationsCommand oc2 = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        System.err.println (oc2.toJSON());

        assertTrue (new JSONObject("{" +
                "\"restarts\":[{\"etcd\":\"192.168.10.13\"},{\"kube-master\":\"192.168.10.11\"},{\"kube-master\":\"192.168.10.13\"},{\"kube-slave\":\"192.168.10.13\"},{\"elasticsearch\":\"(kubernetes)\"},{\"cerebro\":\"(kubernetes)\"},{\"spark-console\":\"(kubernetes)\"},{\"kafka\":\"(kubernetes)\"},{\"kafka-manager\":\"(kubernetes)\"},{\"logstash\":\"(kubernetes)\"},{\"zeppelin\":\"(kubernetes)\"}]," +
                "\"uninstallations\":[{\"kube-master\":\"192.168.10.13\"}]," +
                "\"installations\":[]}")
                .similar(oc2.toJSON()));
    }

    @Test
    public void testRecoverUninstallationWhenNodeDownMiddleRestart() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("etcd_installed_on_IP_192-168-10-13");

        // 1. some services are uninstalled from a node, one service is moved
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("kube-slave1");
        nodesConfig.getJSONObject().remove("etcd1");

        nodesConfig.setValueForPath("zookeeper", "1");

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        //System.err.println (oc.toJSON());

        assertTrue(new JSONObject("{" +
                "\"restarts\":[{\"gluster\":\"192.168.10.11\"},{\"gluster\":\"192.168.10.13\"},{\"elasticsearch\":\"(kubernetes)\"},{\"kafka\":\"(kubernetes)\"},{\"kafka-manager\":\"(kubernetes)\"},{\"logstash\":\"(kubernetes)\"},{\"zeppelin\":\"(kubernetes)\"}]," +
                "\"uninstallations\":[{\"etcd\":\"192.168.10.11\"},{\"kube-slave\":\"192.168.10.11\"},{\"zookeeper\":\"192.168.10.13\"}]," +
                "\"installations\":[{\"zookeeper\":\"192.168.10.11\"},{\"etcd\":\"192.168.10.13\"}]}")
                .similar(oc.toJSON()));


        // node vanished
        // uninstallation fails in the middle (after zookeeper)
        savedServicesInstallStatus.setValueForPath("zookeeper_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("etcd_installed_on_IP_192-168-10-13", "OK");
        savedServicesInstallStatus.getJSONObject().remove("zookeeper_installed_on_IP_192-168-10-13");
        savedServicesInstallStatus.getJSONObject().remove("kube-slave_installed_on_IP_192-168-10-11");
        savedServicesInstallStatus.getJSONObject().remove("etcd_installed_on_IP_192-168-10-11");

        // flag all restarts
        savedServicesInstallStatus.setValueForPath("kube-master_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("kube-slave_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("logstash_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("logstash_installed_on_IP_192-168-10-13", "restart");

        // some restarts done
        savedServicesInstallStatus.setValueForPath("gluster_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("gluster_installed_on_IP_192-168-10-13", "OK");
        savedServicesInstallStatus.setValueForPath("kube-master_installed_on_IP_192-168-10-13", "OK");

        ServiceOperationsCommand oc2 = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);

        System.err.println (oc2.toJSON());

        assertTrue (new JSONObject("{" +
                "\"restarts\":[{\"kube-master\":\"192.168.10.11\"},{\"kube-slave\":\"192.168.10.13\"},{\"elasticsearch\":\"(kubernetes)\"},{\"cerebro\":\"(kubernetes)\"},{\"spark-console\":\"(kubernetes)\"},{\"kafka\":\"(kubernetes)\"},{\"kafka-manager\":\"(kubernetes)\"},{\"logstash\":\"(kubernetes)\"},{\"zeppelin\":\"(kubernetes)\"}]," +
                "\"uninstallations\":[{\"kube-master\":\"192.168.10.13\"}]," +
                "\"installations\":[]}")
                .similar(oc2.toJSON()));
    }

    @Test
    public void toJSON () throws Exception {

        ServiceOperationsCommand oc = prepareFiveOps();

        assertEquals ("{\n" +
                "  \"restarts\": [\n" +
                "    {\"gluster\": \"192.168.10.11\"},\n" +
                "    {\"gluster\": \"192.168.10.13\"},\n" +
                "    {\"elasticsearch\": \"(kubernetes)\"},\n" +
                "    {\"kafka\": \"(kubernetes)\"},\n" +
                "    {\"kafka-manager\": \"(kubernetes)\"},\n" +
                "    {\"logstash\": \"(kubernetes)\"},\n" +
                "    {\"zeppelin\": \"(kubernetes)\"}\n" +
                "  ],\n" +
                "  \"uninstallations\": [\n" +
                "    {\"etcd\": \"192.168.10.11\"},\n" +
                "    {\"kube-slave\": \"192.168.10.11\"},\n" +
                "    {\"zookeeper\": \"192.168.10.13\"}\n" +
                "  ],\n" +
                "  \"installations\": [\n" +
                "    {\"zookeeper\": \"192.168.10.11\"},\n" +
                "    {\"etcd\": \"192.168.10.13\"}\n" +
                "  ]\n" +
                "}", oc.toJSON().toString(2));
    }

    private ServiceOperationsCommand prepareFiveOps() throws NodesConfigurationException {
        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("etcd_installed_on_IP_192-168-10-13");

        // 1. some services are uninstalled from a node, one service is moved
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("kube-slave1");
        nodesConfig.getJSONObject().remove("etcd1");

        nodesConfig.setValueForPath("zookeeper", "1");

        return ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedServicesInstallStatus, nodesConfig);
    }

    @Test
    public void testGetAllOperationsInOrder() throws Exception {

        ServiceOperationsCommand oc = prepareFiveOps();

        List<ServiceOperationsCommand.ServiceOperationId> opsInOrder =  oc.getAllOperationsInOrder(new OperationsContext() {
            @Override
            public ServicesInstallationSorter getServicesInstallationSorter() {
                ServicesInstallationSorter sis = new ServicesInstallationSorter();
                sis.setServicesDefinition(servicesDefinition);
                ConfigurationServiceImpl cs = new ConfigurationServiceImpl();
                sis.setConfigurationService(cs);
                cs.setSetupService(new SetupServiceImpl() {
                    public String getConfigStoragePath() {
                        return "/tmp";
                    }
                });
                return sis;
            }

            @Override
            public NodesConfigWrapper getNodesConfig() {
                NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
                nodesConfig.getJSONObject().remove("kube-slave1");
                nodesConfig.getJSONObject().remove("etcd1");
                return nodesConfig;
            }
        });

        assertEquals(
                "Check--Install_Base-System_192-168-10-11," +
                        "Check--Install_Base-System_192-168-10-13," +
                        "installation_zookeeper_192-168-10-11," +
                        "installation_etcd_192-168-10-13," +
                        "uninstallation_kube-slave_192-168-10-11," +
                        "uninstallation_zookeeper_192-168-10-13," +
                        "uninstallation_etcd_192-168-10-11," +
                        "restart_gluster_192-168-10-11," +
                        "restart_gluster_192-168-10-13," +
                        "restart_elasticsearch_kubernetes," +
                        "restart_logstash_kubernetes," +
                        "restart_kafka_kubernetes," +
                        "restart_kafka-manager_kubernetes," +
                        "restart_zeppelin_kubernetes",
                opsInOrder.stream().map(ServiceOperationsCommand.ServiceOperationId::toString).collect(Collectors.joining(",")));
    }

}

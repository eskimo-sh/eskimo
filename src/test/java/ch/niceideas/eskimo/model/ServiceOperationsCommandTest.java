/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.common.utils.SerializablePair;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.services.AbstractServicesDefinitionTest;
import ch.niceideas.eskimo.services.NodeRangeResolver;
import ch.niceideas.eskimo.services.StandardSetupHelpers;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceOperationsCommandTest extends AbstractServicesDefinitionTest {

    private NodeRangeResolver nrr;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        nrr = new NodeRangeResolver();
    }

    @Test
    public void testNoChanges() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(0, oc.getInstallations().size());
        assertEquals(0, oc.getUninstallations().size());
        assertEquals(0, oc.getRestarts().size());
    }

    @Test
    public void testInstallationLogstash() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("logstash_installed_on_IP_192-168-10-13");

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(1, oc.getInstallations().size());
        assertEquals("logstash", oc.getInstallations().get(0).getService());
        assertEquals("192.168.10.13", oc.getInstallations().get(0).getNode());

        assertEquals(0, oc.getUninstallations().size());

        assertEquals(1, oc.getRestarts().size());
        assertEquals("zeppelin", oc.getRestarts().get(0).getService());
        assertEquals("(marathon)", oc.getRestarts().get(0).getNode());
    }

    @Test
    public void testUninstallationLogstash() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("logstash2");

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(0, oc.getInstallations().size());

        assertEquals(1, oc.getUninstallations().size());
        assertEquals("logstash", oc.getUninstallations().get(0).getService());
        assertEquals("192.168.10.13", oc.getUninstallations().get(0).getNode());

        assertEquals(1, oc.getRestarts().size());
        assertEquals("zeppelin", oc.getRestarts().get(0).getService());
        assertEquals("(marathon)", oc.getRestarts().get(0).getNode());
    }

    @Test
    public void testRestartMany() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("elasticsearch_installed_on_IP_192-168-10-13");


        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("mesos-agent1");
        nodesConfig.getJSONObject().remove("spark-executor1");
        nodesConfig.setValueForPath("zookeeper", "1");

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(2, oc.getInstallations().size());

        assertEquals("zookeeper", oc.getInstallations().get(0).getService());
        assertEquals("192.168.10.11", oc.getInstallations().get(0).getNode());

        assertEquals("elasticsearch", oc.getInstallations().get(1).getService());
        assertEquals("192.168.10.13", oc.getInstallations().get(1).getNode());

        assertEquals(3, oc.getUninstallations().size());

        assertEquals("mesos-agent", oc.getUninstallations().get(0).getService());
        assertEquals("192.168.10.11", oc.getUninstallations().get(0).getNode());

        assertEquals("spark-executor", oc.getUninstallations().get(1).getService());
        assertEquals("192.168.10.11", oc.getUninstallations().get(1).getNode());

        assertEquals("zookeeper", oc.getUninstallations().get(2).getService());
        assertEquals("192.168.10.13", oc.getUninstallations().get(2).getNode());

        assertEquals(15, oc.getRestarts().size());

        assertEquals (
                "gluster=192.168.10.11, " +
                "gluster=192.168.10.13, " +
                "kafka=192.168.10.11, " +
                "kafka=192.168.10.13, " +
                "mesos-master=192.168.10.13, " +
                "logstash=192.168.10.11, " +
                "logstash=192.168.10.13, " +
                "marathon=192.168.10.11, " +
                "spark-executor=192.168.10.13, " +
                "mesos-agent=192.168.10.13, " +
                "cerebro=(marathon), " +
                "kibana=(marathon), " +
                "kafka-manager=(marathon), " +
                "spark-history-server=(marathon), " +
                "zeppelin=(marathon)"
                , oc.getRestarts().stream()
                        .map(operationId -> operationId.getService()+"="+operationId.getNode())
                        .collect(Collectors.joining(", ")));
    }

    @Test
    public void testRestartOnlySameNode() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("gluster_installed_on_IP_192-168-10-11");

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(1, oc.getInstallations().size());

        assertEquals("gluster", oc.getInstallations().get(0).getService());
        assertEquals("192.168.10.11", oc.getInstallations().get(0).getNode());

        assertEquals(0, oc.getUninstallations().size());

        assertEquals(4, oc.getRestarts().size());

        assertEquals (
                        "logstash=192.168.10.11, " +
                        "marathon=192.168.10.11, " +
                        "spark-history-server=(marathon), " +
                        "zeppelin=(marathon)"
                , oc.getRestarts().stream()
                        .map(operationId -> operationId.getService()+"="+operationId.getNode())
                        .collect(Collectors.joining(", ")));
    }

    @Test
    public void testMoveServices() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = new ServicesInstallStatusWrapper (new HashMap<String, Object>() {{
                put ("cerebro_installed_on_IP_MARATHON_NODE", "OK");
                put ("elasticsearch_installed_on_IP_192-168-10-11", "OK");
                put ("logstash_installed_on_IP_192-168-10-11", "OK");
        }});

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put ("node_id1", "192.168.10.11");
                put ("node_id2", "192.168.10.13");
                put ("logstash2", "on");
                put ("elasticsearch2", "on");
        }} );

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(2, oc.getInstallations().size());
        assertEquals("elasticsearch", oc.getInstallations().get(0).getService());
        assertEquals("192.168.10.13", oc.getInstallations().get(0).getNode());
        assertEquals("logstash", oc.getInstallations().get(1).getService());
        assertEquals("192.168.10.13", oc.getInstallations().get(1).getNode());

        assertEquals(2, oc.getUninstallations().size());
        assertEquals("elasticsearch", oc.getUninstallations().get(0).getService());
        assertEquals("192.168.10.11", oc.getUninstallations().get(0).getNode());
        assertEquals("logstash", oc.getUninstallations().get(1).getService());
        assertEquals("192.168.10.11", oc.getUninstallations().get(1).getNode());

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
        NodesConfigWrapper newNodesConfig = new NodesConfigWrapper(StreamUtils.getAsString(servicesConfigStream));

        InputStream nodesStatusStream = ResourceUtils.getResourceAsStream("OperationsCommandTest/nodes-status.json");
        ServicesInstallStatusWrapper status = new ServicesInstallStatusWrapper(StreamUtils.getAsString(nodesStatusStream));

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(def, nrr, status, newNodesConfig);

        assertNotNull(oc);

        assertTrue(oc.getUninstallations().isEmpty());

        // new installations on .15, .16, .17, .18
        assertEquals(32, oc.getInstallations().size());
    }


    @Test
    public void testRecoverUninstallationWhenNodeDownMiddleUninstall() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("elasticsearch_installed_on_IP_192-168-10-13");

        // 1. some services are uninstalled from a node, one service is moved
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("mesos-agent1");
        nodesConfig.getJSONObject().remove("spark-executor1");

        nodesConfig.setValueForPath("zookeeper", "1");

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(2, oc.getInstallations().size());

        assertEquals("zookeeper", oc.getInstallations().get(0).getService());
        assertEquals("192.168.10.11", oc.getInstallations().get(0).getNode());

        assertEquals("elasticsearch", oc.getInstallations().get(1).getService());
        assertEquals("192.168.10.13", oc.getInstallations().get(1).getNode());

        assertEquals(3, oc.getUninstallations().size());

        assertEquals("mesos-agent", oc.getUninstallations().get(0).getService());
        assertEquals("192.168.10.11", oc.getUninstallations().get(0).getNode());

        assertEquals("spark-executor", oc.getUninstallations().get(1).getService());
        assertEquals("192.168.10.11", oc.getUninstallations().get(1).getNode());

        assertEquals("zookeeper", oc.getUninstallations().get(2).getService());
        assertEquals("192.168.10.13", oc.getUninstallations().get(2).getNode());

        assertEquals(15, oc.getRestarts().size());

        assertEquals (
                "gluster=192.168.10.11, " +
                "gluster=192.168.10.13, " +
                "kafka=192.168.10.11, " +
                "kafka=192.168.10.13, " +
                "mesos-master=192.168.10.13, " +
                "logstash=192.168.10.11, " +
                "logstash=192.168.10.13, " +
                "marathon=192.168.10.11, " +
                "spark-executor=192.168.10.13, " +
                "mesos-agent=192.168.10.13, " +
                "cerebro=(marathon), " +
                "kibana=(marathon), " +
                "kafka-manager=(marathon), " +
                "spark-history-server=(marathon), " +
                "zeppelin=(marathon)", oc.getRestarts().stream()
                        .map(operationId -> operationId.getService()+"="+operationId.getNode())
                        .collect(Collectors.joining(", ")));


        // node vanished
        // uninstallation fails in the middle (after zookeeper)
        savedServicesInstallStatus.setValueForPath("zookeeper_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("elasticsearch_installed_on_IP_192-168-10-13", "OK");
        savedServicesInstallStatus.getJSONObject().remove("zookeeper_installed_on_IP_192-168-10-13");
        
        // flag all restarts
        savedServicesInstallStatus.setValueForPath("elasticsearch_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("mesos-master_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("cerebro_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("kafka_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("kafka_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("kibana_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("mesos-agent_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("logstash_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("logstash_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("kafka-manager_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("spark-history-server_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("spark-executor_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("zeppelin_installed_on_IP_192-168-10-13", "restart");

        ServiceOperationsCommand oc2 = ServiceOperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(0, oc2.getInstallations().size());

        assertEquals(2, oc2.getUninstallations().size());

        assertEquals("mesos-agent", oc2.getUninstallations().get(0).getService());
        assertEquals("192.168.10.11", oc2.getUninstallations().get(0).getNode());

        assertEquals("spark-executor", oc2.getUninstallations().get(1).getService());
        assertEquals("192.168.10.11", oc2.getUninstallations().get(1).getNode());

        assertEquals(13, oc2.getRestarts().size());

        assertEquals (
                "elasticsearch=192.168.10.11, " +
                "kafka=192.168.10.11, " +
                "kafka=192.168.10.13, " +
                "mesos-master=192.168.10.13, " +
                "logstash=192.168.10.11, " +
                "logstash=192.168.10.13, " +
                "spark-executor=192.168.10.13, " +
                "mesos-agent=192.168.10.13, " +
                "cerebro=(marathon), " +
                "kibana=(marathon), " +
                "kafka-manager=(marathon), " +
                "spark-history-server=(marathon), " +
                "zeppelin=(marathon)", oc2.getRestarts().stream()
                .map(operationId -> operationId.getService()+"="+operationId.getNode())
                .collect(Collectors.joining(", ")));
    }

    @Test
    public void testRecoverUninstallationWhenNodeDownAfterUninstall() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("elasticsearch_installed_on_IP_192-168-10-13");

        // 1. some services are uninstalled from a node, one service is moved
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("mesos-agent1");
        nodesConfig.getJSONObject().remove("spark-executor1");

        nodesConfig.setValueForPath("zookeeper", "1");

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(2, oc.getInstallations().size());

        assertEquals("zookeeper", oc.getInstallations().get(0).getService());
        assertEquals("192.168.10.11", oc.getInstallations().get(0).getNode());

        assertEquals("elasticsearch", oc.getInstallations().get(1).getService());
        assertEquals("192.168.10.13", oc.getInstallations().get(1).getNode());

        assertEquals(3, oc.getUninstallations().size());

        assertEquals("mesos-agent", oc.getUninstallations().get(0).getService());
        assertEquals("192.168.10.11", oc.getUninstallations().get(0).getNode());

        assertEquals("spark-executor", oc.getUninstallations().get(1).getService());
        assertEquals("192.168.10.11", oc.getUninstallations().get(1).getNode());

        assertEquals("zookeeper", oc.getUninstallations().get(2).getService());
        assertEquals("192.168.10.13", oc.getUninstallations().get(2).getNode());

        assertEquals(15, oc.getRestarts().size());

        assertEquals (
                "gluster=192.168.10.11, " +
                "gluster=192.168.10.13, " +
                "kafka=192.168.10.11, " +
                "kafka=192.168.10.13, " +
                "mesos-master=192.168.10.13, " +
                "logstash=192.168.10.11, " +
                "logstash=192.168.10.13, " +
                "marathon=192.168.10.11, " +
                "spark-executor=192.168.10.13, " +
                "mesos-agent=192.168.10.13, " +
                "cerebro=(marathon), " +
                "kibana=(marathon), " +
                "kafka-manager=(marathon), " +
                "spark-history-server=(marathon), " +
                "zeppelin=(marathon)", oc.getRestarts().stream()
                        .map(operationId -> operationId.getService()+"="+operationId.getNode())
                        .collect(Collectors.joining(", ")));

        // node vanished
        // uninstallation fails in the middle (after zookeeper)
        savedServicesInstallStatus.setValueForPath("zookeeper_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("elasticsearch_installed_on_IP_192-168-10-13", "OK");
        savedServicesInstallStatus.getJSONObject().remove("zookeeper_installed_on_IP_192-168-10-13");
        savedServicesInstallStatus.getJSONObject().remove("mesos-agent_installed_on_IP_192-168-10-11");
        savedServicesInstallStatus.getJSONObject().remove("spark-executor_installed_on_IP_192-168-10-11");

        // flag all restarts
        savedServicesInstallStatus.setValueForPath("elasticsearch_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("mesos-master_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("cerebro_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("kafka_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("kafka_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("kibana_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("mesos-agent_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("logstash_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("logstash_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("kafka-manager_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("spark-history-server_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("spark-executor_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("zeppelin_installed_on_IP_192-168-10-13", "restart");

        ServiceOperationsCommand oc2 = ServiceOperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(0, oc2.getInstallations().size());

        assertEquals(0, oc2.getUninstallations().size());

        assertEquals(13, oc2.getRestarts().size());

        assertEquals (
                "elasticsearch=192.168.10.11, " +
                "kafka=192.168.10.11, " +
                "kafka=192.168.10.13, " +
                "mesos-master=192.168.10.13, " +
                "logstash=192.168.10.11, " +
                "logstash=192.168.10.13, " +
                "spark-executor=192.168.10.13, " +
                "mesos-agent=192.168.10.13, " +
                "cerebro=(marathon), " +
                "kibana=(marathon), " +
                "kafka-manager=(marathon), " +
                "spark-history-server=(marathon), " +
                "zeppelin=(marathon)"
                , oc2.getRestarts().stream()
                        .map(operationId -> operationId.getService()+"="+operationId.getNode())
                        .collect(Collectors.joining(", ")));
    }


    @Test
    public void testRecoverUninstallationWhenNodeDownMiddleRestart() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("elasticsearch_installed_on_IP_192-168-10-13");

        // 1. some services are uninstalled from a node, one service is moved
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("mesos-agent1");
        nodesConfig.getJSONObject().remove("spark-executor1");

        nodesConfig.setValueForPath("zookeeper", "1");

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(2, oc.getInstallations().size());

        assertEquals("zookeeper", oc.getInstallations().get(0).getService());
        assertEquals("192.168.10.11", oc.getInstallations().get(0).getNode());

        assertEquals("elasticsearch", oc.getInstallations().get(1).getService());
        assertEquals("192.168.10.13", oc.getInstallations().get(1).getNode());

        assertEquals(3, oc.getUninstallations().size());

        assertEquals("mesos-agent", oc.getUninstallations().get(0).getService());
        assertEquals("192.168.10.11", oc.getUninstallations().get(0).getNode());

        assertEquals("spark-executor", oc.getUninstallations().get(1).getService());
        assertEquals("192.168.10.11", oc.getUninstallations().get(1).getNode());

        assertEquals("zookeeper", oc.getUninstallations().get(2).getService());
        assertEquals("192.168.10.13", oc.getUninstallations().get(2).getNode());

        assertEquals(15, oc.getRestarts().size());

        assertEquals (
                "gluster=192.168.10.11, " +
                "gluster=192.168.10.13, " +
                "kafka=192.168.10.11, " +
                "kafka=192.168.10.13, " +
                "mesos-master=192.168.10.13, " +
                "logstash=192.168.10.11, " +
                "logstash=192.168.10.13, " +
                "marathon=192.168.10.11, " +
                "spark-executor=192.168.10.13, " +
                "mesos-agent=192.168.10.13, " +
                "cerebro=(marathon), " +
                "kibana=(marathon), " +
                "kafka-manager=(marathon), " +
                "spark-history-server=(marathon), " +
                "zeppelin=(marathon)"
                , oc.getRestarts().stream()
                        .map(operationId -> operationId.getService()+"="+operationId.getNode())
                        .collect(Collectors.joining(", ")));


        // node vanished
        // uninstallation fails in the middle (after zookeeper)
        savedServicesInstallStatus.setValueForPath("zookeeper_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("elasticsearch_installed_on_IP_192-168-10-13", "OK");
        savedServicesInstallStatus.getJSONObject().remove("zookeeper_installed_on_IP_192-168-10-13");
        savedServicesInstallStatus.getJSONObject().remove("mesos-agent_installed_on_IP_192-168-10-11");
        savedServicesInstallStatus.getJSONObject().remove("spark-executor_installed_on_IP_192-168-10-11");

        // flag all restarts
        savedServicesInstallStatus.setValueForPath("elasticsearch_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("mesos-master_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("cerebro_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("kafka_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("kafka_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("kibana_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("mesos-agent_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("logstash_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("logstash_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("kafka-manager_installed_on_IP_192-168-10-11", "restart");
        savedServicesInstallStatus.setValueForPath("spark-history-server_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("spark-executor_installed_on_IP_192-168-10-13", "restart");
        savedServicesInstallStatus.setValueForPath("zeppelin_installed_on_IP_192-168-10-13", "restart");

        // some restarts done
        savedServicesInstallStatus.setValueForPath("gluster_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("gluster_installed_on_IP_192-168-10-13", "OK");
        savedServicesInstallStatus.setValueForPath("elasticsearch_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("mesos-master_installed_on_IP_192-168-10-13", "OK");
        savedServicesInstallStatus.setValueForPath("cerebro_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("kafka_installed_on_IP_192-168-10-11", "OK");
        savedServicesInstallStatus.setValueForPath("kafka_installed_on_IP_192-168-10-13", "OK");

        ServiceOperationsCommand oc2 = ServiceOperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(0, oc2.getInstallations().size());

        assertEquals(0, oc2.getUninstallations().size());

        assertEquals(8, oc2.getRestarts().size());

        assertEquals (
                "logstash=192.168.10.11, " +
                "logstash=192.168.10.13, " +
                "spark-executor=192.168.10.13, " +
                "mesos-agent=192.168.10.13, " +
                "kibana=(marathon), " +
                "kafka-manager=(marathon), " +
                "spark-history-server=(marathon), " +
                "zeppelin=(marathon)", oc2.getRestarts().stream()
                .map(operationId -> operationId.getService()+"="+operationId.getNode())
                .collect(Collectors.joining(", ")));
    }


}

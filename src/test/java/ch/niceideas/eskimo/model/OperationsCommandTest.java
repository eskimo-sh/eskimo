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

package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.services.AbstractServicesDefinitionTest;
import ch.niceideas.eskimo.services.NodeRangeResolver;
import ch.niceideas.eskimo.services.StandardSetupHelpers;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.*;

public class OperationsCommandTest extends AbstractServicesDefinitionTest {

    private NodeRangeResolver nrr;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        nrr = new NodeRangeResolver();
    }

    @Test
    public void testNoChanges() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesStatus();

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        OperationsCommand oc = OperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(0, oc.getInstallations().size());
        assertEquals(0, oc.getUninstallations().size());
        assertEquals(0, oc.getRestarts().size());
    }

    @Test
    public void testInstallationLogstash() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesStatus();
        savedServicesInstallStatus.getJSONObject().remove("logstash_installed_on_IP_192-168-10-13");

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        OperationsCommand oc = OperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(1, oc.getInstallations().size());
        assertEquals("logstash", oc.getInstallations().get(0).getKey());
        assertEquals("192.168.10.13", oc.getInstallations().get(0).getValue());

        assertEquals(0, oc.getUninstallations().size());
        assertEquals(0, oc.getRestarts().size());
    }

    @Test
    public void testUninstallationLogstash() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesStatus();

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("logstash2");

        OperationsCommand oc = OperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(0, oc.getInstallations().size());

        assertEquals(1, oc.getUninstallations().size());
        assertEquals("logstash", oc.getUninstallations().get(0).getKey());
        assertEquals("192.168.10.13", oc.getUninstallations().get(0).getValue());

        assertEquals(0, oc.getRestarts().size());
    }

    @Test
    public void testRestartMany() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesStatus();
        savedServicesInstallStatus.getJSONObject().remove("elasticsearch_installed_on_IP_192-168-10-13");


        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.getJSONObject().remove("mesos-agent1");
        nodesConfig.getJSONObject().remove("spark-executor1");
        nodesConfig.setValueForPath("zookeeper", "1");

        OperationsCommand oc = OperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(2, oc.getInstallations().size());

        assertEquals("zookeeper", oc.getInstallations().get(0).getKey());
        assertEquals("192.168.10.11", oc.getInstallations().get(0).getValue());

        assertEquals("elasticsearch", oc.getInstallations().get(1).getKey());
        assertEquals("192.168.10.13", oc.getInstallations().get(1).getValue());

        assertEquals(3, oc.getUninstallations().size());

        assertEquals("mesos-agent", oc.getUninstallations().get(0).getKey());
        assertEquals("192.168.10.11", oc.getUninstallations().get(0).getValue());

        assertEquals("spark-executor", oc.getUninstallations().get(1).getKey());
        assertEquals("192.168.10.11", oc.getUninstallations().get(1).getValue());

        assertEquals("zookeeper", oc.getUninstallations().get(2).getKey());
        assertEquals("192.168.10.13", oc.getUninstallations().get(2).getValue());

        assertEquals(13, oc.getRestarts().size());

        assertEquals ("[" +
                "elasticsearch=192.168.10.11, " +
                "logstash=192.168.10.11, " +
                "logstash=192.168.10.13, " +
                "cerebro=192.168.10.11, " +
                "mesos-master=192.168.10.13, " +
                "kafka=192.168.10.11, " +
                "kafka=192.168.10.13, " +
                "kibana=192.168.10.11, " +
                "kafka-manager=192.168.10.11, " +
                "mesos-agent=192.168.10.13, " +
                "spark-history-server=192.168.10.13, " +
                "spark-executor=192.168.10.13, " +
                "zeppelin=192.168.10.13]", Arrays.toString(oc.getRestarts().toArray()));
    }

    @Test
    public void testMoveServices() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = new ServicesInstallStatusWrapper (new HashMap<String, Object>() {{
                put ("cerebro_installed_on_IP_192-168-10-11", "OK");
                put ("elasticsearch_installed_on_IP_192-168-10-11", "OK");
                put ("logstash_installed_on_IP_192-168-10-11", "OK");
        }});

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put ("action_id1", "192.168.10.11");
                put ("action_id2", "192.168.10.13");
                put ("logstash2", "on");
                put ("elasticsearch2", "on");
                put ("cerebro", "2");
        }} );

        OperationsCommand oc = OperationsCommand.create(def, nrr, savedServicesInstallStatus, nodesConfig);

        assertEquals(3, oc.getInstallations().size());
        assertEquals("elasticsearch", oc.getInstallations().get(0).getKey());
        assertEquals("192.168.10.13", oc.getInstallations().get(0).getValue());
        assertEquals("logstash", oc.getInstallations().get(1).getKey());
        assertEquals("192.168.10.13", oc.getInstallations().get(1).getValue());
        assertEquals("cerebro", oc.getInstallations().get(2).getKey());
        assertEquals("192.168.10.13", oc.getInstallations().get(2).getValue());

        assertEquals(3, oc.getUninstallations().size());
        assertEquals("cerebro", oc.getUninstallations().get(0).getKey());
        assertEquals("192.168.10.11", oc.getUninstallations().get(0).getValue());
        assertEquals("elasticsearch", oc.getUninstallations().get(1).getKey());
        assertEquals("192.168.10.11", oc.getUninstallations().get(1).getValue());
        assertEquals("logstash", oc.getUninstallations().get(2).getKey());
        assertEquals("192.168.10.11", oc.getUninstallations().get(2).getValue());

    }

    @Test
    public void testToJSON() throws Exception {

        OperationsCommand oc = new OperationsCommand(NodesConfigWrapper.empty());

        oc.addInstallation("elasticsearch", "192.168.10.11");
        oc.addInstallation("kibana", "192.168.10.11");
        oc.addInstallation("cerebro", "192.168.10.11");

        oc.addUninstallation("cerebro", "192.168.10.13");
        oc.addUninstallation("kibana", "192.168.10.13");
        oc.addUninstallation("logstash", "192.168.10.13");

        oc.addRestart("zeppelin", "192.168.10.13");

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

        OperationsCommand oc = OperationsCommand.create(def, nrr, status, newNodesConfig);

        assertNotNull(oc);

        assertTrue(oc.getUninstallations().isEmpty());

        // new installations on .15, .16, .17, .18
        assertEquals(32, oc.getInstallations().size());

    }
}

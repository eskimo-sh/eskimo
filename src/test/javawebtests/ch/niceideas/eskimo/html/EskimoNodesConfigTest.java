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

package ch.niceideas.eskimo.html;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EskimoNodesConfigTest extends AbstractWebTest {

    private String jsonServices = null;

    @Before
    public void setUp() throws Exception {

        jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"));

        loadScript(page, "eskimoUtils.js");
        loadScript(page, "eskimoNodesConfig.js");

        page.executeJavaScript("UNIQUE_SERVICES = [\"zookeeper\", \"mesos-master\", \"cerebro\", \"kibana\", \"gdash\", \"spark-history-server\", \"zeppelin\"];");
        page.executeJavaScript("MULTIPLE_SERVICES = [\"elasticsearch\", \"kafka\", \"mesos-agent\", \"spark-executor\", \"gluster\", \"logstash\"];");
        page.executeJavaScript("MANDATORY_SERVICES = [\"ntp\", \"gluster\"];");
        page.executeJavaScript("CONFIGURED_SERVICES = UNIQUE_SERVICES.concat(MULTIPLE_SERVICES);");

        // instantiate test object
        page.executeJavaScript("eskimoNodesConfig = new eskimo.NodesConfig();");

        waitForElementIdInDOM("reset-nodes-config");

        page.executeJavaScript("SERVICES_CONFIGURATION = " + jsonServices + ";");

        page.executeJavaScript("eskimoNodesConfig.setServicesConfig(SERVICES_CONFIGURATION);");

        // set services for tests
        page.executeJavaScript("eskimoNodesConfig.setServicesConfigForTest (UNIQUE_SERVICES, MULTIPLE_SERVICES, CONFIGURED_SERVICES, MANDATORY_SERVICES);");
    }

    @Test
    public void testRemoveNode() throws Exception {

        // add two nodes
        page.executeJavaScript("eskimoNodesConfig.addNode()");
        page.executeJavaScript("eskimoNodesConfig.addNode()");

        // manipulate node 2
        page.executeJavaScript("$('#action_id2').attr('value', '192.168.10.11')");
        page.executeJavaScript("$('#zeppelin2').get(0).checked = true");
        page.executeJavaScript("$('#elasticsearch2').get(0).checked = true");

        // remove node 1
        page.executeJavaScript("eskimoNodesConfig.removeNode ('remove1')");

        // ensure values are found in node 1
        assertEquals ("192.168.10.11", page.executeJavaScript("$('#action_id1').attr('value')").getJavaScriptResult());
        assertEquals (true, page.executeJavaScript("$('#zeppelin1').get(0).checked").getJavaScriptResult());
        assertEquals (true, page.executeJavaScript("$('#elasticsearch1').get(0).checked").getJavaScriptResult());
    }

    @Test
    public void testAddNode() throws Exception {

        page.executeJavaScript("eskimoNodesConfig.addNode()");

        assertNotNull (page.getElementById("action_id1"));
        assertEquals ("input", page.getElementById("action_id1").getTagName());

        assertNotNull (page.getElementById("spark-history-server1"));
        assertEquals ("input", page.getElementById("spark-history-server1").getTagName());

        assertNotNull (page.getElementById("zookeeper1"));
        assertEquals ("input", page.getElementById("zookeeper1").getTagName());

        assertNotNull (page.getElementById("gluster1"));
        assertEquals ("input", page.getElementById("gluster1").getTagName());

        assertNotNull (page.getElementById("cerebro1"));
        assertEquals ("input", page.getElementById("cerebro1").getTagName());

        assertNotNull (page.getElementById("mesos-master1"));
        assertEquals ("input", page.getElementById("mesos-master1").getTagName());

        assertNotNull (page.getElementById("mesos-agent1"));
        assertEquals ("input", page.getElementById("mesos-agent1").getTagName());

        assertNotNull (page.getElementById("kibana1"));
        assertEquals ("input", page.getElementById("kibana1").getTagName());

        assertNotNull (page.getElementById("gdash1"));
        assertEquals ("input", page.getElementById("gdash1").getTagName());

        assertNotNull (page.getElementById("elasticsearch1"));
        assertEquals ("input", page.getElementById("elasticsearch1").getTagName());

        assertNotNull (page.getElementById("zeppelin1"));
        assertEquals ("input", page.getElementById("zeppelin1").getTagName());

        assertNotNull (page.getElementById("spark-executor1"));
        assertEquals ("input", page.getElementById("spark-executor1").getTagName());

        assertNotNull (page.getElementById("kafka1"));
        assertEquals ("input", page.getElementById("kafka1").getTagName());

        assertNotNull (page.getElementById("logstash1"));
        assertEquals ("input", page.getElementById("logstash1").getTagName());

        assertEquals (1.0, (double) page.executeJavaScript("eskimoNodesConfig.getNodesCount()").getJavaScriptResult(), 0.0);

    }
}

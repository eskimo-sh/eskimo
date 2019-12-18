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
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.services.StandardSetupHelpers;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EskimoNodesConfigTest extends AbstractWebTest {

    private String jsonServices = null;

    @Before
    public void setUp() throws Exception {

        jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"));

        loadScript(page, "eskimoUtils.js");
        loadScript(page, "eskimoNodesConfig.js");

        page.executeJavaScript("UNIQUE_SERVICES = [\"zookeeper\", \"mesos-master\", \"cerebro\", \"kibana\", \"gdash\", \"spark-history-server\", \"zeppelin\", \"kafka-manager\"];");
        page.executeJavaScript("MULTIPLE_SERVICES = [\"ntp\", \"elasticsearch\", \"kafka\", \"mesos-agent\", \"spark-executor\", \"gluster\", \"logstash\"];");
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
    public void testServicesConfigMethods() throws Exception {

        assertJavascriptEquals("kibana.png", "eskimoNodesConfig.getServiceLogoPath('kibana')");
        assertJavascriptEquals("kibana.png", "eskimoNodesConfig.getServiceIconPath('kibana')");
        assertJavascriptEquals("true", "eskimoNodesConfig.isServiceUnique('kibana')");

        assertJavascriptEquals("false", "eskimoNodesConfig.isServiceUnique('gluster')");

        assertJavascriptEquals("undefined", "eskimoNodesConfig.getServiceIconPath('test-none')");
    }

    @Test
    public void testRenderNodesConfig() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        page.executeJavaScript("eskimoNodesConfig.renderNodesConfig("+nodesConfig.getFormattedValue()+");");

        // test a few nodes
        assertJavascriptEquals("1.0", "$('#ntp1:checked').length");
        assertJavascriptEquals("1.0", "$('#spark-executor1:checked').length");
        assertJavascriptEquals("1.0", "$('#logstash1:checked').length");

        assertJavascriptEquals("1.0", "$('#ntp2:checked').length");
        assertJavascriptEquals("1.0", "$('#spark-executor2:checked').length");
        assertJavascriptEquals("1.0", "$('#logstash2:checked').length");

        assertJavascriptEquals("0.0", "$('#zookeeper1:checked').length");
        assertJavascriptEquals("1.0", "$('#zookeeper2:checked').length");

        assertJavascriptEquals("0.0", "$('#mesos-master1:checked').length");
        assertJavascriptEquals("1.0", "$('#mesos-master2:checked').length");
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
        assertAttrValue("#action_id1", "value", "192.168.10.11");

        assertJavascriptEquals("true", "$('#zeppelin1').get(0).checked");
        assertJavascriptEquals("true", "$('#elasticsearch1').get(0).checked");
    }

    @Test
    public void testAddNode() throws Exception {

        page.executeJavaScript("eskimoNodesConfig.addNode()");

        assertNotNull (page.getElementById("action_id1"));
        assertTagName ("action_id1", "input");

        assertNotNull (page.getElementById("spark-history-server1"));
        assertTagName ("spark-history-server1", "input");

        assertNotNull (page.getElementById("zookeeper1"));
        assertTagName ("zookeeper1", "input");

        assertNotNull (page.getElementById("gluster1"));
        assertTagName ("gluster1", "input");

        assertNotNull (page.getElementById("cerebro1"));
        assertTagName ("cerebro1", "input");

        assertNotNull (page.getElementById("mesos-master1"));
        assertTagName ("mesos-master1", "input");

        assertNotNull (page.getElementById("kibana1"));
        assertTagName ("kibana1", "input");

        assertNotNull (page.getElementById("gdash1"));
        assertTagName ("gdash1", "input");

        assertNotNull (page.getElementById("elasticsearch1"));
        assertTagName ("elasticsearch1", "input");

        assertNotNull (page.getElementById("zeppelin1"));
        assertTagName ("zeppelin1", "input");

        assertNotNull (page.getElementById("spark-executor1"));
        assertTagName ("spark-executor1", "input");

        assertNotNull (page.getElementById("kafka1"));
        assertTagName ("kafka1", "input");

        assertNotNull (page.getElementById("logstash1"));
        assertTagName ("logstash1", "input");

        assertJavascriptEquals ("1.0", "eskimoNodesConfig.getNodesCount()");

    }
}

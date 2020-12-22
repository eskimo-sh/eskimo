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
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EskimoNodesConfigTest extends AbstractWebTest {

    private String jsonServices = null;

    @BeforeEach
    public void setUp() throws Exception {

        jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"));

        loadScript(page, "eskimoUtils.js");
        loadScript(page, "eskimoNodesConfigurationChecker.js");
        loadScript(page, "eskimoNodesConfig.js");

        js("UNIQUE_SERVICES = [\"zookeeper\", \"mesos-master\", \"flink-app-master\", \"marathon\" ];");
        js("MULTIPLE_SERVICES = [\"ntp\", \"elasticsearch\", \"kafka\", \"mesos-agent\", \"spark-executor\", \"gluster\", \"logstash\", \"flink-worker\", \"prometheus\"];");
        js("MANDATORY_SERVICES = [\"ntp\", \"gluster\"];");
        js("CONFIGURED_SERVICES = UNIQUE_SERVICES.concat(MULTIPLE_SERVICES);");

        // instantiate test object
        js("eskimoNodesConfig = new eskimo.NodesConfig()");
        js("eskimoNodesConfig.eskimoMain = eskimoMain");
        js("eskimoNodesConfig.eskimoServicesSelection =  eskimoServicesSelection");
        js("eskimoNodesConfig.eskimoServices = eskimoServices");
        js("eskimoNodesConfig.eskimoOperationsCommand = eskimoOperationsCommand");
        js("eskimoNodesConfig.initialize()");

        waitForElementIdInDOM("reset-nodes-config");

        js("SERVICES_CONFIGURATION = " + jsonServices + ";");

        js("eskimoNodesConfig.setServicesConfig(SERVICES_CONFIGURATION);");

        // set services for tests
        js("eskimoNodesConfig.setServicesSettingsForTest (UNIQUE_SERVICES, MULTIPLE_SERVICES, CONFIGURED_SERVICES, MANDATORY_SERVICES);");

        js("$('#inner-content-nodes').css('display', 'inherit')");
        js("$('#inner-content-nodes').css('visibility', 'visible')");
    }

    @Test
    public void testServicesConfigMethods() throws Exception {

        assertJavascriptEquals("images/flink-app-master-logo.png", "eskimoNodesConfig.getServiceLogoPath('flink-app-master')");
        assertJavascriptEquals("images/flink-app-master-icon.png", "eskimoNodesConfig.getServiceIconPath('flink-app-master')");
        assertJavascriptEquals("true", "eskimoNodesConfig.isServiceUnique('flink-app-master')");

        assertJavascriptEquals("false", "eskimoNodesConfig.isServiceUnique('gluster')");

        assertJavascriptEquals("undefined", "eskimoNodesConfig.getServiceIconPath('test-none')");
    }

    @Test
    public void testRenderNodesConfig() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        js("eskimoNodesConfig.renderNodesConfig("+nodesConfig.getFormattedValue()+");");

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
    public void testSaveNodesButton() throws Exception {
        testRenderNodesConfig();

        js("function checkNodesSetup(nodeSetup) {" +
                "    window.nodeSetup = nodeSetup" +
                "}");

        page.getElementById("save-nodes-btn").click();

        //System.err.println(js("JSON.stringify (window.nodeSetup)").getJavaScriptResult());

        JSONObject expectedResult = new JSONObject("{" +
                "\"node_id1\":\"192.168.10.11\"," +
                "\"marathon\":\"1\"," +
                "\"ntp1\":\"on\"," +
                "\"elasticsearch1\":\"on\"," +
                "\"kafka1\":\"on\"," +
                "\"mesos-agent1\":\"on\"," +
                "\"spark-executor1\":\"on\"," +
                "\"gluster1\":\"on\"," +
                "\"logstash1\":\"on\"," +
                "\"node_id2\":\"192.168.10.13\"," +
                "\"zookeeper\":\"2\"," +
                "\"mesos-master\":\"2\"," +
                "\"ntp2\":\"on\"," +
                "\"elasticsearch2\":\"on\"," +
                "\"kafka2\":\"on\"," +
                "\"mesos-agent2\":\"on\"," +
                "\"spark-executor2\":\"on\"," +
                "\"gluster2\":\"on\"," +
                "\"logstash2\":\"on\"}");

        JSONObject actualResult = new JSONObject((String)js("JSON.stringify (window.nodeSetup)").getJavaScriptResult());

        assertTrue(expectedResult.similar(actualResult));
    }

    @Test
    public void testShowNodesConfigWithResetButton() throws Exception {

        js("eskimoMain.isSetupDone = function () { return true; }");

        // test clear = missing
        js("$.ajax = function (object) {" +
                "    object.success ({clear: \"missing\"});" +
                "}");

        page.getElementById("reset-nodes-config").click();

        assertTrue(page.getElementById("nodes-placeholder").getTextContent().contains("(No nodes / services configured yet)"));

        // test clear = setup
        js("$.ajax = function (object) {" +
                "    object.success ({clear: \"setup\"});" +
                "}");

        js("eskimoMain.handleSetupNotCompleted = function () { window.handleSetupNotCompletedCalled = true; }");

        page.getElementById("reset-nodes-config").click();

        assertTrue((boolean)js("window.handleSetupNotCompletedCalled").getJavaScriptResult());

        // test OK
        js("$.ajax = function (object) {" +
                "    object.success ({result: \"OK\"});" +
                "}");

        js("eskimoNodesConfig.renderNodesConfig = function (config) { window.nodesConfig = config; }");

        page.getElementById("reset-nodes-config").click();

        assertJavascriptEquals("{\"result\":\"OK\"}", "JSON.stringify (window.nodesConfig)");
    }

    @Test
    public void testOnServiceSelectedForNode() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        js("eskimoNodesConfig.renderNodesConfig(" + nodesConfig.getFormattedValue() + ");");

        js("eskimoNodesConfig.onServicesSelectedForNode({\n" +
                "\"elasticsearch2\": \"on\",\n" +
                "\"flink-worker2\": \"on\",\n" +
                "\"flink-app-master\": \"2\",\n" +
                "\"gluster2\": \"on\",\n" +
                "\"kafka2\": \"on\",\n" +
                "\"logstash2\": \"on\",\n" +
                "\"mesos-agent2\": \"on\",\n" +
                "\"ntp2\": \"on\",\n" +
                "\"prometheus2\": \"on\",\n" +
                "\"spark-executor2\": \"on\"\n" +
                "}, 2)");

        assertJavascriptEquals("1.0", "$('#prometheus2:checked').length");

        assertJavascriptEquals("1.0", "$('#flink-app-master2:checked').length");
    }


        @Test
    public void testRemoveNode() throws Exception {

        // add two nodes
        js("eskimoNodesConfig.addNode()");
        js("eskimoNodesConfig.addNode()");

        // manipulate node 2
        js("$('#node_id2').attr('value', '192.168.10.11')");
        js("$('#flink-app-master2').get(0).checked = true");
        js("$('#elasticsearch2').get(0).checked = true");

        // remove node 1
        js("eskimoNodesConfig.removeNode ('remove1')");

        // ensure values are found in node 1
        assertAttrValue("#node_id1", "value", "192.168.10.11");

        assertJavascriptEquals("true", "$('#flink-app-master1').get(0).checked");
        assertJavascriptEquals("true", "$('#elasticsearch1').get(0).checked");
    }

    @Test
    public void testAddNode() throws Exception {

        js("eskimoNodesConfig.addNode()");

        assertTrue(page.getElementById("label1").getTextContent().contains("Node no  1"));

        assertNotNull (page.getElementById("node_id1"));
        assertTagName ("node_id1", "input");

        assertNotNull (page.getElementById("zookeeper1"));
        assertTagName ("zookeeper1", "input");

        assertNotNull (page.getElementById("gluster1"));
        assertTagName ("gluster1", "input");

        assertNotNull (page.getElementById("mesos-master1"));
        assertTagName ("mesos-master1", "input");

        assertNotNull (page.getElementById("elasticsearch1"));
        assertTagName ("elasticsearch1", "input");

        assertNotNull (page.getElementById("spark-executor1"));
        assertTagName ("spark-executor1", "input");

        assertNotNull (page.getElementById("kafka1"));
        assertTagName ("kafka1", "input");

        assertNotNull (page.getElementById("logstash1"));
        assertTagName ("logstash1", "input");

        assertJavascriptEquals ("1.0", "eskimoNodesConfig.getNodesCount()");

    }
}

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

package ch.niceideas.eskimo.html;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EskimoNodesConfigTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        String jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"), StandardCharsets.UTF_8);

        loadScript("eskimoUtils.js");
        loadScript("eskimoNodesConfigurationChecker.js");
        loadScript("eskimoNodesConfig.js");

        js("window.UNIQUE_SERVICES = [\"zookeeper\", \"kube-master\", ];");
        js("window.MULTIPLE_SERVICES = [\"ntp\", \"prometheus\", \"etcd\", \"kube-slave\", \"gluster\" ];");
        js("window.MANDATORY_SERVICES = [\"ntp\", \"gluster\"];");
        js("window.CONFIGURED_SERVICES = UNIQUE_SERVICES.concat(MULTIPLE_SERVICES);");

        // instantiate test object
        js("eskimoNodesConfig = new eskimo.NodesConfig()");
        js("eskimoNodesConfig.eskimoMain = eskimoMain");
        js("eskimoNodesConfig.eskimoServicesSelection =  eskimoServicesSelection");
        js("eskimoNodesConfig.eskimoServices = eskimoServices");
        js("eskimoNodesConfig.eskimoOperationsCommand = eskimoOperationsCommand");

        js("$.ajaxGet = function(callback) { console.log(callback); }");
        js("$.ajaxPost = function(callback) { console.log(callback); }");

        js("eskimoNodesConfig.initialize()");

        waitForElementIdInDOM("reset-nodes-config");

        js("window.SERVICES_CONFIGURATION = " + jsonServices + ";");

        js("eskimoNodesConfig.setServicesConfig(SERVICES_CONFIGURATION);");

        // set services for tests
        js("eskimoNodesConfig.setServicesSettingsForTest (UNIQUE_SERVICES, MULTIPLE_SERVICES, CONFIGURED_SERVICES, MANDATORY_SERVICES);");

        js("$('#inner-content-nodes').css('display', 'inherit')");
        js("$('#inner-content-nodes').css('visibility', 'visible')");
    }

    @Test
    public void testServicesConfigMethods() throws Exception {

        assertJavascriptEquals("images/kube-slave-logo.png", "eskimoNodesConfig.getServiceLogoPath('kube-slave')");
        assertJavascriptEquals("images/kube-slave-icon.png", "eskimoNodesConfig.getServiceIconPath('kube-slave')");
        assertJavascriptEquals("true", "eskimoNodesConfig.isServiceUnique('zookeeper')");

        assertJavascriptEquals("false", "eskimoNodesConfig.isServiceUnique('gluster')");

        assertJavascriptEquals("undefined", "eskimoNodesConfig.getServiceIconPath('test-none')");
    }

    @Test
    public void testRenderNodesConfig() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        js("eskimoNodesConfig.renderNodesConfig("+nodesConfig.getFormattedValue()+");");

        // test a few nodes
        assertJavascriptEquals("1", "$('#ntp1:checked').length");
        assertJavascriptEquals("1", "$('#etcd1:checked').length");
        assertJavascriptEquals("1", "$('#kube-slave1:checked').length");

        assertJavascriptEquals("1", "$('#ntp2:checked').length");
        assertJavascriptEquals("1", "$('#etcd2:checked').length");
        assertJavascriptEquals("1", "$('#kube-slave2:checked').length");

        assertJavascriptEquals("0", "$('#zookeeper1:checked').length");
        assertJavascriptEquals("1", "$('#zookeeper2:checked').length");

        assertJavascriptEquals("1", "$('#kube-master1:checked').length");
        assertJavascriptEquals("0", "$('#kube-master2:checked').length");
    }

    @Test
    public void testSaveNodesButton() throws Exception {
        testRenderNodesConfig();

        js("window.checkNodesSetup = function (nodeSetup) {" +
                "    window.nodeSetup = nodeSetup" +
                "}");

        getElementById("save-nodes-btn").click();

        //System.err.println(js("JSON.stringify (window.nodeSetup)"));

        JSONObject expectedResult = new JSONObject("{" +
                "\"kube-master\":\"1\"," +
                "\"ntp1\":\"on\"," +
                "\"etcd1\":\"on\"," +
                "\"zookeeper\":\"2\"," +
                "\"etcd2\":\"on\"," +
                "\"gluster1\":\"on\"," +
                "\"ntp2\":\"on\"," +
                "\"node_id1\":\"192.168.10.11\"," +
                "\"kube-slave1\":\"on\"," +
                "\"kube-slave2\":\"on\"," +
                "\"node_id2\":\"192.168.10.13\"," +
                "\"gluster2\":\"on\"}");

        JSONObject actualResult = new JSONObject((String)js("return JSON.stringify (window.nodeSetup)"));

        System.err.println (actualResult);
        assertTrue(expectedResult.similar(actualResult));
    }

    @Test
    public void testShowNodesConfigWithResetButton() throws Exception {

        js("eskimoMain.isSetupDone = function () { return true; }");

        // test clear = missing
        js("$.ajaxGet = function (object) {" +
                "    object.success ({clear: \"missing\"});" +
                "}");

        getElementById("reset-nodes-config").click();

        assertTrue(getElementById("nodes-placeholder").getText().contains("(No nodes / services configured yet)"));

        // test clear = setup
        js("$.ajaxGet = function (object) {" +
                "    object.success ({clear: \"setup\"});" +
                "}");

        js("eskimoMain.handleSetupNotCompleted = function () { window.handleSetupNotCompletedCalled = true; }");

        getElementById("reset-nodes-config").click();

        assertTrue((boolean)js("return window.handleSetupNotCompletedCalled"));

        // test OK
        js("$.ajaxGet = function (object) {" +
                "    object.success ({result: \"OK\"});" +
                "}");

        js("eskimoNodesConfig.renderNodesConfig = function (config) { window.nodesConfig = config; }");

        getElementById("reset-nodes-config").click();

        assertJavascriptEquals("{\"result\":\"OK\"}", "JSON.stringify (window.nodesConfig)");
    }

    @Test
    public void testOnServiceSelectedForNode() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        js("eskimoNodesConfig.renderNodesConfig(" + nodesConfig.getFormattedValue() + ");");

        js("eskimoNodesConfig.onServicesSelectedForNode({\n" +
                "\"kube-master\": \"2\",\n" +
                "\"gluster2\": \"on\",\n" +
                "\"etcd2\": \"on\",\n" +
                "\"kube-slave2\": \"on\",\n" +
                "\"ntp2\": \"on\",\n" +
                "\"prometheus2\": \"on\",\n" +
                "}, 2)");

        assertJavascriptEquals("1", "$('#prometheus2:checked').length");

        assertJavascriptEquals("1", "$('#ntp2:checked').length");
    }


        @Test
    public void testRemoveNode() throws Exception {

        // add two nodes
        js("eskimoNodesConfig.addNode()");
        js("eskimoNodesConfig.addNode()");

        // manipulate node 2
        js("$('#node_id2').attr('value', '192.168.10.11')");
        js("$('#zookeeper2').get(0).checked = true");
        js("$('#ntp2').get(0).checked = true");

        // remove node 1
        js("eskimoNodesConfig.removeNode ('remove1')");

        // ensure values are found in node 2 now as node 1
        assertAttrValue("#node_id1", "value", "192.168.10.11");

        assertJavascriptEquals("true", "$('#zookeeper1').get(0).checked");
        assertJavascriptEquals("true", "$('#ntp1').get(0).checked");
    }

    @Test
    public void testAddNode() throws Exception {

        js("eskimoNodesConfig.addNode()");

        assertTrue(getElementById("label1").getText().contains(" Node no \n" +
                "1"));

        assertNotNull (getElementById("node_id1"));
        assertTagName ("node_id1", "input");

        assertNotNull (getElementById("zookeeper1"));
        assertTagName ("zookeeper1", "input");

        assertNotNull (getElementById("gluster1"));
        assertTagName ("gluster1", "input");

        assertNotNull (getElementById("kube-master1"));
        assertTagName ("kube-master1", "input");

        assertNotNull (getElementById("etcd1"));
        assertTagName ("etcd1", "input");

        assertJavascriptEquals ("1", "eskimoNodesConfig.getNodesCount()");

    }
}

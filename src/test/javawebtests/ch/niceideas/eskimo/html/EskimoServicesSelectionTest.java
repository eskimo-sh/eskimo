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

package ch.niceideas.eskimo.html;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.utils.ActiveWaiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

public class EskimoServicesSelectionTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        String jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"), StandardCharsets.UTF_8);

        loadScript("vendor/bootstrap-5.2.0.js");

        loadScript("eskimoUtils.js");
        loadScript("eskimoServicesSelection.js");

        waitForDefinition("window.eskimo");
        waitForDefinition("window.eskimo.ServicesSelection");

        // instantiate test object
        js("eskimoServicesSelection = new eskimo.ServicesSelection();");
        js("eskimoServicesSelection.eskimoMain = eskimoMain");
        js("eskimoServicesSelection.eskimoNodesConfig = eskimoNodesConfig");

        js("$.ajaxGet = function(callback) { console.log(callback); }");

        js("eskimoServicesSelection.initialize()");

        waitForElementIdInDOM("services-selection-button-select-all");

        js("window.SERVICES_CONFIGURATION = " + jsonServices + ";");

        js("eskimoServicesSelection.setServicesSettingsForTest(SERVICES_CONFIGURATION);");

        jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"), StandardCharsets.UTF_8);

        js("window.SERVICES_CONFIGURATION = " + jsonServices + ";");
        //js("eskimoNodesConfig.setServicesConfig(SERVICES_CONFIGURATION);");

        js("window.UNIQUE_SERVICES = [\"zookeeper\", \"kube-master\"];");
        js("window.MULTIPLE_SERVICES = [\"ntp\", \"etcd\", \"kube-slave\", \"gluster\", \"prometheus\"];");
        js("window.MANDATORY_SERVICES = [\"ntp\", \"gluster\"];");
        js("window.CONFIGURED_SERVICES = UNIQUE_SERVICES.concat(MULTIPLE_SERVICES);");

        js("eskimoNodesConfig.getConfiguredServices = function() {\n"+
                "    return CONFIGURED_SERVICES;\n"+
                "};\n");

        js("eskimoServicesSelection.initModalServicesConfig();");
    }

    @Test
    public void testGetService() {
        assertJavascriptEquals("ntp", "eskimoServicesSelection.getService(1, 1).name");
        assertJavascriptEquals("spark-cli", "eskimoServicesSelection.getService(3, 3).name");
    }

    @Test
    public void testInitModalServicesConfig() {
        assertJavascriptEquals("1", "$('#etcd-choice').length");
        assertJavascriptEquals("1", "$('#kube-master-choice').length");
        assertJavascriptEquals("1", "$('#zookeeper-choice').length");
    }

    @Test
    public void testServicesSelectionRadioMouseDown() {
        getElementById("zookeeper-choice").click();

        assertJavascriptEquals("true", "$('#zookeeper-choice').get(0).checked");

        getElementById("zookeeper-choice").click();
        ActiveWaiter.wait(() -> js("return $('#zookeeper-choice').get(0).checked").toString().equals ("false"));
        assertJavascriptEquals("false", "$('#zookeeper-choice').get(0).checked");
    }

    @Test
    public void testNominal() throws Exception {

        js("window.result = null;");

        js("eskimoServicesSelection.getCurrentNodesConfig = function() {" +
                "return {\n" +
                "  \"node_id1\": \"192.168.10.11\",\n" +
                "  \"etcd1\": \"on\",\n" +
                "  \"kube-slave1\": \"on\",\n" +
                "  \"kube-master\": \"1\",\n" +
                "  \"ntp1\": \"on\",\n" +
                "  \"gluster1\": \"on\",\n" +
                "  \"prometheus1\": \"on\",\n" +
                "  \"zookeeper\": \"1\"\n" +
                "};" +
                "}");

        js("eskimoNodesConfig.getNodesCount = function() { return 2; }");

        js("eskimoServicesSelection.showServiceSelection(1, function(model) {result = model;})");

        js("eskimoServicesSelection.validateServicesSelection();");

        assertJavascriptEquals("{\"ntp1\":\"on\",\"zookeeper\":\"1\",\"gluster1\":\"on\",\"kube-master\":\"1\",\"prometheus1\":\"on\",\"etcd1\":\"on\",\"kube-slave1\":\"on\"}", "JSON.stringify (result)");
    }

    @Test
    public void testSelectAll() {

        js("window.result = null;");

        js("eskimoNodesConfig.getNodesCount = function() { return 2; }");

        js("eskimoServicesSelection.showServiceSelection(\"empty\", function(model) {result = model;})");

        js("eskimoServicesSelection.servicesSelectionSelectAll();");

        js("eskimoServicesSelection.validateServicesSelection();");

        assertJavascriptEquals("{\"ntp\":\"on\",\"zookeeper\":\"on\",\"gluster\":\"on\",\"kube-master\":\"on\",\"prometheus\":\"on\",\"etcd\":\"on\",\"kube-slave\":\"on\"}", "JSON.stringify (result)");
    }
}

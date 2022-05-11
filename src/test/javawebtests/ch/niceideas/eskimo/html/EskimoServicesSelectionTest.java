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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class EskimoServicesSelectionTest extends AbstractWebTest {

    private String jsonServices = null;

    @BeforeEach
    public void setUp() throws Exception {

        jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"));

        loadScript(page, "bootstrap.js");

        loadScript(page, "eskimoUtils.js");
        loadScript(page, "eskimoServicesSelection.js");

        // instantiate test object
        js("eskimoServicesSelection = new eskimo.ServicesSelection();");
        js("eskimoServicesSelection.eskimoMain = eskimoMain");
        js("eskimoServicesSelection.eskimoNodesConfig = eskimoNodesConfig");
        js("eskimoServicesSelection.initialize()");

        waitForElementIdInDOM("services-selection-button-select-all");

        js("SERVICES_CONFIGURATION = " + jsonServices + ";");

        js("eskimoServicesSelection.setServicesSettingsForTest(SERVICES_CONFIGURATION);");

        jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"));

        js("SERVICES_CONFIGURATION = " + jsonServices + ";");
        //js("eskimoNodesConfig.setServicesConfig(SERVICES_CONFIGURATION);");

        js("UNIQUE_SERVICES = [\"zookeeper\", \"kube-master\"];");
        js("MULTIPLE_SERVICES = [\"ntp\", \"etcd\", \"kube-slave\", \"gluster\", \"prometheus\"];");
        js("MANDATORY_SERVICES = [\"ntp\", \"gluster\"];");
        js("CONFIGURED_SERVICES = UNIQUE_SERVICES.concat(MULTIPLE_SERVICES);");

        js("eskimoNodesConfig.getConfiguredServices = function() {\n"+
                "    return CONFIGURED_SERVICES;\n"+
                "};\n");

        js("eskimoServicesSelection.initModalServicesConfig();");
    }

    @Test
    public void testGetService() throws Exception {

        assertJavascriptEquals("ntp", "eskimoServicesSelection.getService(1, 1).name");
        assertJavascriptEquals("spark-runtime", "eskimoServicesSelection.getService(3, 3).name");

    }

    @Test
    public void testInitModalServicesConfig() throws Exception {

        assertJavascriptEquals("1.0", "$('#etcd-choice').length");
        assertJavascriptEquals("1.0", "$('#kube-master-choice').length");
        assertJavascriptEquals("1.0", "$('#zookeeper-choice').length");
    }

    @Test
    public void testServicesSelectionRadioMouseDown() throws Exception {
        page.getElementById("zookeeper-choice").click();

        assertJavascriptEquals("true", "$('#zookeeper-choice').get(0).checked");

        page.getElementById("zookeeper-choice").click();

        await().atMost(10, TimeUnit.SECONDS).until(() -> js("$('#zookeeper-choice').get(0).checked").getJavaScriptResult().toString().equals ("false"));

        assertJavascriptEquals("false", "$('#zookeeper-choice').get(0).checked");
    }

    @Test
    public void testNominal() throws Exception {

        js("var result = null;");

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

        assertJavascriptEquals("{\"ntp1\":\"on\",\"zookeeper\":\"1\",\"kube-slave1\":\"on\",\"gluster1\":\"on\",\"prometheus1\":\"on\",\"etcd1\":\"on\",\"kube-master\":\"1\"}", "JSON.stringify (result)");
    }

    @Test
    public void testSelectAll() throws Exception {

        js("var result = null;");

        js("eskimoNodesConfig.getNodesCount = function() { return 2; }");

        js("eskimoServicesSelection.showServiceSelection(\"empty\", function(model) {result = model;})");

        js("eskimoServicesSelection.servicesSelectionSelectAll();");

        js("eskimoServicesSelection.validateServicesSelection();");

        assertJavascriptEquals("{\"ntp\":\"on\",\"zookeeper\":\"on\",\"kube-slave\":\"on\",\"gluster\":\"on\",\"prometheus\":\"on\",\"etcd\":\"on\",\"kube-master\":\"on\"}", "JSON.stringify (result)");
    }
}

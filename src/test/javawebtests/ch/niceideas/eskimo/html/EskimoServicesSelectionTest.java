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

import java.net.URL;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.fail;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

public class EskimoServicesSelectionTest extends AbstractWebTest {

    private String jsonServices = null;

    @Before
    public void setUp() throws Exception {

        jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"));

        loadScript(page, "bootstrap.js");

        loadScript(page, "eskimoUtils.js");
        loadScript(page, "eskimoServicesSelection.js");

        // instantiate test object
        page.executeJavaScript("eskimoServicesSelection = new eskimo.ServicesSelection({" +
                "    eskimoMain: eskimoMain" +
                "});");

        waitForElementIdInDOM("services-selection-button-select-all");

        page.executeJavaScript("SERVICES_CONFIGURATION = " + jsonServices + ";");

        page.executeJavaScript("eskimoServicesSelection.setServicesConfigForTest(SERVICES_CONFIGURATION);");

        jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"));

        page.executeJavaScript("SERVICES_CONFIGURATION = " + jsonServices + ";");
        //page.executeJavaScript("eskimoNodesConfig.setServicesConfig(SERVICES_CONFIGURATION);");

        page.executeJavaScript("UNIQUE_SERVICES = [\"zookeeper\", \"mesos-master\", \"flink-app-master\", \"marathon\"];");
        page.executeJavaScript("MULTIPLE_SERVICES = [\"ntp\", \"elasticsearch\", \"kafka\", \"mesos-agent\", \"spark-executor\", \"gluster\", \"logstash\", \"flink-worker\", \"prometheus\"];");
        page.executeJavaScript("MANDATORY_SERVICES = [\"ntp\", \"gluster\"];");
        page.executeJavaScript("CONFIGURED_SERVICES = UNIQUE_SERVICES.concat(MULTIPLE_SERVICES);");

        page.executeJavaScript("eskimoNodesConfig.getConfiguredServices = function() {\n"+
                "    return CONFIGURED_SERVICES;\n"+
                "};\n");

        page.executeJavaScript("eskimoServicesSelection.initModalServicesConfig();");
    }

    @Test
    public void testGetService() throws Exception {

        assertJavascriptEquals("ntp", "eskimoServicesSelection.getService(1, 1).name");
        assertJavascriptEquals("spark-executor", "eskimoServicesSelection.getService(3, 3).name");

    }

    @Test
    public void testInitModalServicesConfig() throws Exception {

        assertJavascriptEquals("1.0", "$('#flink-app-master-choice').length");
        assertJavascriptEquals("1.0", "$('#mesos-master-choice').length");
        assertJavascriptEquals("1.0", "$('#zookeeper-choice').length");
    }

    @Test
    public void testServicesSelectionRadioMouseDown() throws Exception {
        page.getElementById("flink-app-master-choice").click();

        assertJavascriptEquals("true", "$('#flink-app-master-choice').get(0).checked");

        page.getElementById("flink-app-master-choice").click();

        await().atMost(10, TimeUnit.SECONDS).until(() -> page.executeJavaScript("$('#flink-app-master-choice').get(0).checked").getJavaScriptResult().toString().equals ("false"));

        assertJavascriptEquals("false", "$('#flink-app-master-choice').get(0).checked");
    }

    @Test
    public void testNominal() throws Exception {

        page.executeJavaScript("var result = null;");

        page.executeJavaScript("eskimoServicesSelection.getCurrentNodesConfig = function() {" +
                "return {\n" +
                "  \"node_id1\": \"192.168.10.11\",\n" +
                "  \"elasticsearch1\": \"on\",\n" +
                "  \"flink-app-master\": \"1\",\n" +
                "  \"flink-worker1\": \"on\",\n" +
                "  \"kafka1\": \"on\",\n" +
                "  \"logstash1\": \"on\",\n" +
                "  \"mesos-agent1\": \"on\",\n" +
                "  \"mesos-master\": \"1\",\n" +
                "  \"ntp1\": \"on\",\n" +
                "  \"prometheus1\": \"on\",\n" +
                "  \"spark-executor1\": \"on\",\n" +
                "  \"zookeeper\": \"1\"\n" +
                "};" +
                "}");

        page.executeJavaScript("eskimoNodesConfig.getNodesCount = function() { return 2; }");

        page.executeJavaScript("eskimoServicesSelection.showServiceSelection(1, function(model) {result = model;})");

        page.executeJavaScript("eskimoServicesSelection.validateServicesSelection();");

        assertJavascriptEquals("{\"ntp1\":\"on\"," +
                "\"zookeeper\":\"1\"," +
                "\"elasticsearch1\":\"on\"," +
                "\"gluster1\":\"on\"," +
                "\"mesos-master\":\"1\"," +
                "\"logstash1\":\"on\"," +
                "\"mesos-agent1\":\"on\"," +
                "\"spark-executor1\":\"on\"," +
                "\"prometheus1\":\"on\"," +
                "\"flink-app-master\":\"1\"," +
                "\"flink-worker1\":\"on\"," +
                "\"kafka1\":\"on\"}", "JSON.stringify (result)");
    }

    @Test
    public void testSelectAll() throws Exception {

        page.executeJavaScript("var result = null;");

        page.executeJavaScript("eskimoNodesConfig.getNodesCount = function() { return 2; }");

        page.executeJavaScript("eskimoServicesSelection.showServiceSelection(\"empty\", function(model) {result = model;})");

        page.executeJavaScript("eskimoServicesSelection.servicesSelectionSelectAll();");

        page.executeJavaScript("eskimoServicesSelection.validateServicesSelection();");

        assertJavascriptEquals("{\"ntp\":\"on\"," +
                "\"zookeeper\":\"on\"," +
                "\"elasticsearch\":\"on\"," +
                "\"gluster\":\"on\"," +
                "\"mesos-master\":\"on\"," +
                "\"logstash\":\"on\"," +
                "\"mesos-agent\":\"on\"," +
                "\"marathon\":\"on\"," +
                "\"spark-executor\":\"on\"," +
                "\"prometheus\":\"on\"," +
                "\"flink-app-master\":\"on\"," +
                "\"flink-worker\":\"on\"," +
                "\"kafka\":\"on\"}", "JSON.stringify (result)");
    }
}

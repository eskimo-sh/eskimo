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

import static junit.framework.TestCase.fail;
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
        page.executeJavaScript("eskimoServicesSelection = new eskimo.ServicesSelection();");

        waitForElementIdInDOM("select-all-services-button");

        page.executeJavaScript("SERVICES_CONFIGURATION = " + jsonServices + ";");

        page.executeJavaScript("eskimoServicesSelection.setServicesConfigForTest(SERVICES_CONFIGURATION);");

        jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"));

        page.executeJavaScript("SERVICES_CONFIGURATION = " + jsonServices + ";");
        //page.executeJavaScript("nodesConfig.setServicesConfig(SERVICES_CONFIGURATION);");

        page.executeJavaScript("UNIQUE_SERVICES = [\"zookeeper\", \"mesos-master\", \"cerebro\", \"kibana\", \"gdash\", \"spark-history-server\", \"zeppelin\", \"kafka-manager\", \"flink-app-master\", \"grafana\"];");
        page.executeJavaScript("MULTIPLE_SERVICES = [\"ntp\", \"elasticsearch\", \"kafka\", \"mesos-agent\", \"spark-executor\", \"gluster\", \"logstash\", \"flink-worker\", \"prometheus\"];");
        page.executeJavaScript("MANDATORY_SERVICES = [\"ntp\", \"gluster\"];");
        page.executeJavaScript("CONFIGURED_SERVICES = UNIQUE_SERVICES.concat(MULTIPLE_SERVICES);");

        page.executeJavaScript("nodesConfig.getConfiguredServices = function() {\n"+
                "    return CONFIGURED_SERVICES;\n"+
                "};\n");

        page.executeJavaScript("eskimoServicesSelection.initModalServicesConfig();");
    }

    @Test
    public void testGetService() throws Exception {

        assertJavascriptEquals("spark-history-server", "eskimoServicesSelection.getService(1, 1).name");
        assertJavascriptEquals ("elasticsearch", "eskimoServicesSelection.getService(3, 3).name");

    }

    @Test
    public void testInitModalServicesConfig() throws Exception {

        page.executeJavaScript("eskimoServicesSelection.initModalServicesConfig()");

        assertJavascriptEquals("1.0", "$('#cerebro-choice').length");
        assertJavascriptEquals("1.0", "$('#kibana-choice').length");
        assertJavascriptEquals("1.0", "$('#grafana-choice').length");
    }

    @Test
    public void testNominal() throws Exception {

        page.executeJavaScript("var result = null;");

        page.executeJavaScript("eskimoServicesSelection.getCurrentNodesConfig = function() {" +
                "return {\n" +
                "  \"action_id1\": \"192.168.10.11\",\n" +
                "  \"cerebro\": \"1\",\n" +
                "  \"elasticsearch1\": \"on\",\n" +
                "  \"flink-app-master\": \"1\",\n" +
                "  \"flink-worker1\": \"on\",\n" +
                "  \"grafana\": \"1\",\n" +
                "  \"kafka1\": \"on\",\n" +
                "  \"kafka-manager\": \"1\",\n" +
                "  \"kibana\": \"1\",\n" +
                "  \"logstash1\": \"on\",\n" +
                "  \"mesos-agent1\": \"on\",\n" +
                "  \"mesos-master\": \"1\",\n" +
                "  \"ntp1\": \"on\",\n" +
                "  \"prometheus1\": \"on\",\n" +
                "  \"spark-executor1\": \"on\",\n" +
                "  \"spark-history-server\": \"1\",\n" +
                "  \"zeppelin\": \"1\",\n" +
                "  \"zookeeper\": \"1\"\n" +
                "};" +
                "}");

        page.executeJavaScript("nodesConfig.getNodesCount = function() { return 2; }");

        page.executeJavaScript("eskimoServicesSelection.showServiceSelection(1, function(model) {result = model;})");

        page.executeJavaScript("eskimoServicesSelection.validateServicesSelection();");

        assertJavascriptEquals("" +
                "{" +
                "\"spark-history-server\":\"1\"," +
                "\"ntp1\":\"on\"," +
                "\"gluster1\":\"on\"," +
                "\"cerebro\":\"1\"," +
                "\"zookeeper\":\"1\"," +
                "\"mesos-agent1\":\"on\"," +
                "\"kibana\":\"1\"," +
                "\"mesos-master\":\"1\"," +
                "\"elasticsearch1\":\"on\"," +
                "\"zeppelin\":\"1\"," +
                "\"spark-executor1\":\"on\"," +
                "\"kafka-manager\":\"1\"," +
                "\"kafka1\":\"on\"," +
                "\"logstash1\":\"on\"," +
                "\"grafana\":\"1\"," +
                "\"prometheus1\":\"on\"," +
                "\"flink-worker1\":\"on\"," +
                "\"flink-app-master1\":\"on\"" +
                "}", "JSON.stringify (result)");
    }

    @Test
    public void testSelectAll() throws Exception {

        page.executeJavaScript("var result = null;");

        page.executeJavaScript("nodesConfig.getNodesCount = function() { return 2; }");

        page.executeJavaScript("eskimoServicesSelection.showServiceSelection(\"empty\", function(model) {result = model;})");

        page.executeJavaScript("eskimoServicesSelection.servicesSelectionSelectAll();");

        page.executeJavaScript("eskimoServicesSelection.validateServicesSelection();");

        assertJavascriptEquals("{" +
                "\"spark-history-server\":\"on\"," +
                "\"ntp\":\"on\"," +
                "\"gluster\":\"on\"," +
                "\"cerebro\":\"on\"," +
                "\"zookeeper\":\"on\"," +
                "\"mesos-agent\":\"on\"," +
                "\"kibana\":\"on\"," +
                "\"mesos-master\":\"on\"," +
                "\"elasticsearch\":\"on\"," +
                "\"zeppelin\":\"on\"," +
                "\"gdash\":\"on\"," +
                "\"spark-executor\":\"on\"," +
                "\"kafka-manager\":\"on\"," +
                "\"kafka\":\"on\"," +
                "\"logstash\":\"on\"," +
                "\"grafana\":\"on\"," +
                "\"prometheus\":\"on\"," +
                "\"flink-worker\":\"on\"," +
                "\"flink-app-master\":\"on\"" +
                "}", "JSON.stringify (result)");
    }
}

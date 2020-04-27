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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNotNull;

public class EskimoMarathonServicesConfigTest extends AbstractWebTest {

    private String expectedMarathonConfigTableContent = null;
    private String expectedMarathonSelectionTableContent = null;

    @Before
    public void setUp() throws Exception {

        expectedMarathonConfigTableContent = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoMarathonServicesConfigTest/expectedMarathonConfigTableContent.html"));
        expectedMarathonSelectionTableContent = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoMarathonServicesConfigTest/expectedMarathonSelectionTableContent.html"));

        loadScript(page, "eskimoUtils.js");
        loadScript(page, "eskimoMarathonServicesConfigChecker.js");
        loadScript(page, "eskimoMarathonServicesConfig.js");

        /*
        page.executeJavaScript("UNIQUE_SERVICES = [\"zookeeper\", \"mesos-master\", \"cerebro\", \"kibana\", \"gdash\", \"spark-history-server\", \"zeppelin\", \"kafka-manager\", \"flink-app-master\", \"grafana\"];");
        page.executeJavaScript("MULTIPLE_SERVICES = [\"ntp\", \"elasticsearch\", \"kafka\", \"mesos-agent\", \"spark-executor\", \"gluster\", \"logstash\", \"flink-worker\", \"prometheus\"];");
        page.executeJavaScript("MANDATORY_SERVICES = [\"ntp\", \"gluster\"];");
        page.executeJavaScript("CONFIGURED_SERVICES = UNIQUE_SERVICES.concat(MULTIPLE_SERVICES);");

        */

        // instantiate test object
        page.executeJavaScript("eskimoMarathonServicesConfig = new eskimo.MarathonServicesConfig();");

        waitForElementIdInDOM("reset-marathon-servicesconfig");

        page.executeJavaScript("eskimoMarathonServicesConfig.setMarathonServicesForTest([\n" +
                "    \"cerebro\",\n" +
                "    \"gdash\",\n" +
                "    \"grafana\",\n" +
                "    \"kafka-manager\",\n" +
                "    \"kibana\",\n" +
                "    \"spark-history-server\",\n" +
                "    \"zeppelin\"\n" +
                "  ]);");

        /*

        page.executeJavaScript("SERVICES_CONFIGURATION = " + jsonServices + ";");

        page.executeJavaScript("eskimoNodesConfig.setServicesConfig(SERVICES_CONFIGURATION);");

        // set services for tests
        page.executeJavaScript("eskimoNodesConfig.setServicesConfigForTest (UNIQUE_SERVICES, MULTIPLE_SERVICES, CONFIGURED_SERVICES, MANDATORY_SERVICES);");
        */
    }

    @Test
    public void testDummy() throws Exception {
        // implement other tests
        fail ("To Be Implemented");
    }

    @Test
    public void testRenderMarathonConfig() throws Exception {
        page.executeJavaScript("eskimoMarathonServicesConfig.renderMarathonConfig({\n" +
                "    \"cerebro_install\": \"on\",\n" +
                "    \"zeppelin_install\": \"on\",\n" +
                "    \"kafka-manager_install\": \"on\",\n" +
                "    \"kibana_install\": \"on\",\n" +
                "    \"gdash_install\": \"on\",\n" +
                "    \"spark-history-server_install\": \"on\",\n" +
                "    \"grafana_install\": \"on\"\n" +
                "})");

        //System.err.println (page.getElementById("marathon-services-table-body").asXml());
        assertEquals(
                expectedMarathonConfigTableContent.replace("  ", ""),
                page.getElementById("marathon-services-table-body").asXml().replace("  ", "").replace("\r\n", "\n"));
    }

    @Test
    public void testSowReinstallSelection() throws Exception {

        testRenderMarathonConfig();

        // mocking stuff
        page.executeJavaScript("eskimoMain.getMarathonServicesSelection = function() { return { 'showMarathonServiceSelection' : function() {} } }");

        page.executeJavaScript("$('#main-content').append($('<div id=\"marathon-services-selection-body\"></div>'))");

        page.executeJavaScript("eskimoMarathonServicesConfig.showReinstallSelection()");

        System.err.println (page.getElementById("marathon-services-selection-body").asXml());
        assertEquals(
                expectedMarathonSelectionTableContent.replace("  ", ""),
                page.getElementById("marathon-services-selection-body").asXml().replace("  ", "").replace("\r\n", "\n"));
    }
}

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
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

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
        page.executeJavaScript("eskimoMarathonServicesConfig = new eskimo.MarathonServicesConfig({eskimoMain: eskimoMain});");

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

        page.executeJavaScript("$('#inner-content-marathon-services-config').css('display', 'inherit')");
        page.executeJavaScript("$('#inner-content-marathon-services-config').css('visibility', 'visible')");
    }

    @Test
    public void testSaveMarathonServicesButtonClick() throws Exception {

        testRenderMarathonConfig();

        page.executeJavaScript("function checkMarathonSetup (marathonConfig) {" +
                "    console.log (marathonConfig);" +
                "    window.savedMarathonConfig = JSON.stringify (marathonConfig);" +
                "};");

        page.getElementById("save-marathon-servicesbtn").click();

        JSONObject expectedConfig = new JSONObject("" +
                "{\"cerebro_install\":\"on\"," +
                "\"gdash_install\":\"on\"," +
                "\"grafana_install\":\"on\"," +
                "\"kafka-manager_install\":\"on\"," +
                "\"kibana_install\":\"on\"," +
                "\"spark-history-server_install\":\"on\"," +
                "\"zeppelin_install\":\"on\"}");

        JSONObject actualConfig = new JSONObject((String)page.executeJavaScript("window.savedMarathonConfig").getJavaScriptResult());
        assertTrue(expectedConfig.similar(actualConfig));
    }

    @Test
    public void testSelectAll() throws Exception {

        testRenderMarathonConfig();

        page.executeJavaScript("eskimoMarathonServicesConfig.selectAll()");

        assertEquals (false, page.executeJavaScript("$('#kibana_install').get(0).checked").getJavaScriptResult());
        assertEquals (false, page.executeJavaScript("$('#gdash_install').get(0).checked").getJavaScriptResult());
        assertEquals (false, page.executeJavaScript("$('#zeppelin_install').get(0).checked").getJavaScriptResult());
        assertEquals (false, page.executeJavaScript("$('#grafana_install').get(0).checked").getJavaScriptResult());
        assertEquals (false, page.executeJavaScript("$('#cerebro_install').get(0).checked").getJavaScriptResult());

        page.executeJavaScript("eskimoMarathonServicesConfig.selectAll()");

        assertEquals (true, page.executeJavaScript("$('#kibana_install').get(0).checked").getJavaScriptResult());
        assertEquals (true, page.executeJavaScript("$('#gdash_install').get(0).checked").getJavaScriptResult());
        assertEquals (true, page.executeJavaScript("$('#zeppelin_install').get(0).checked").getJavaScriptResult());
        assertEquals (true, page.executeJavaScript("$('#grafana_install').get(0).checked").getJavaScriptResult());
        assertEquals (true, page.executeJavaScript("$('#cerebro_install').get(0).checked").getJavaScriptResult());

        page.executeJavaScript("eskimoMarathonServicesConfig.selectAll()");

        assertEquals (false, page.executeJavaScript("$('#kibana_install').get(0).checked").getJavaScriptResult());
        assertEquals (false, page.executeJavaScript("$('#gdash_install').get(0).checked").getJavaScriptResult());
        assertEquals (false, page.executeJavaScript("$('#zeppelin_install').get(0).checked").getJavaScriptResult());
        assertEquals (false, page.executeJavaScript("$('#grafana_install').get(0).checked").getJavaScriptResult());
        assertEquals (false, page.executeJavaScript("$('#cerebro_install').get(0).checked").getJavaScriptResult());

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
    public void testShowReinstallSelection() throws Exception {

        testRenderMarathonConfig();

        // mocking stuff
        page.executeJavaScript("eskimoMain.getMarathonServicesSelection = function() { return { 'showMarathonServiceSelection' : function() {} } }");

        page.executeJavaScript("$('#main-content').append($('<div id=\"marathon-services-selection-body\"></div>'))");

        page.executeJavaScript("eskimoMarathonServicesConfig.showReinstallSelection()");

        //System.err.println (page.getElementById("marathon-services-selection-body").asXml());
        assertEquals(
                expectedMarathonSelectionTableContent.replace("  ", ""),
                page.getElementById("marathon-services-selection-body").asXml().replace("  ", "").replace("\r\n", "\n"));
    }

    @Test
    public void testShowMarathonServicesConfig() throws Exception {

        // 1. setup not OK
        page.executeJavaScript("eskimoMain.isSetupDone = function () { return false; }");
        page.executeJavaScript("eskimoMain.showSetupNotDone = function () { window.setupNotDoneCalled = true; }");

        page.executeJavaScript("eskimoMarathonServicesConfig.showMarathonServicesConfig()");
        assertJavascriptEquals("true", "window.setupNotDoneCalled");

        // 2. setup OK, operation in progress
        page.executeJavaScript("eskimoMain.isSetupDone = function () { return true; }");
        page.executeJavaScript("eskimoMain.isOperationInProgress = function () { return true; }");
        page.executeJavaScript("eskimoMain.showProgressbar = function () { window.showProgressBarCalled = true; }");
        page.executeJavaScript("$.ajax = function() {}");

        page.executeJavaScript("eskimoMarathonServicesConfig.showMarathonServicesConfig()");
        assertJavascriptEquals("true", "window.showProgressBarCalled");

        // 3. data clear, setup
        page.executeJavaScript("eskimoMain.isOperationInProgress = function () { return false; }");
        page.executeJavaScript("$.ajax = function(object) { object.success( { 'clear': 'setup'}); }");
        page.executeJavaScript("eskimoMain.handleSetupNotCompleted = function () { window.handleSetupNotCompletedCalled = true; }");

        page.executeJavaScript("eskimoMarathonServicesConfig.showMarathonServicesConfig()");
        assertJavascriptEquals("true", "window.handleSetupNotCompletedCalled");

        // 4. data clear, missing
        page.executeJavaScript("window.handleSetupNotCompletedCalled = false;");
        page.executeJavaScript("window.showProgressBarCalled = false;");
        page.executeJavaScript("window.setupNotDoneCalled = false;");

        page.executeJavaScript("$.ajax = function(object) { object.success( { 'clear': 'missing'}); }");

        page.executeJavaScript("eskimoMarathonServicesConfig.showMarathonServicesConfig()");

        assertEquals (false, page.executeJavaScript("$('#kibana_install').get(0).checked").getJavaScriptResult());
        assertEquals (false, page.executeJavaScript("$('#gdash_install').get(0).checked").getJavaScriptResult());
        assertEquals (false, page.executeJavaScript("$('#zeppelin_install').get(0).checked").getJavaScriptResult());
        assertEquals (false, page.executeJavaScript("$('#grafana_install').get(0).checked").getJavaScriptResult());
        assertEquals (false, page.executeJavaScript("$('#cerebro_install').get(0).checked").getJavaScriptResult());

        // 5. all good, all services selected
        page.executeJavaScript("$.ajax = function(object) { object.success( {\n" +
                "    \"cerebro_install\": \"on\",\n" +
                        "    \"zeppelin_install\": \"on\",\n" +
                        "    \"kafka-manager_install\": \"on\",\n" +
                        "    \"kibana_install\": \"on\",\n" +
                        "    \"gdash_install\": \"on\",\n" +
                        "    \"spark-history-server_install\": \"on\",\n" +
                        "    \"grafana_install\": \"on\"\n" +
                        "} ); }");

        page.executeJavaScript("eskimoMarathonServicesConfig.showMarathonServicesConfig()");

        assertEquals (true, page.executeJavaScript("$('#kibana_install').get(0).checked").getJavaScriptResult());
        assertEquals (true, page.executeJavaScript("$('#gdash_install').get(0).checked").getJavaScriptResult());
        assertEquals (true, page.executeJavaScript("$('#zeppelin_install').get(0).checked").getJavaScriptResult());
        assertEquals (true, page.executeJavaScript("$('#grafana_install').get(0).checked").getJavaScriptResult());
        assertEquals (true, page.executeJavaScript("$('#cerebro_install').get(0).checked").getJavaScriptResult());
    }

    @Test
    public void testProceedWithMarathonInstallation() throws Exception  {

        testRenderMarathonConfig();

        // 1. error
        page.executeJavaScript("console.error = function (error) { window.consoleError = error; };");
        page.executeJavaScript("$.ajax = function (object) { object.success ( { error: 'dGVzdEVycm9y' } );}");

        page.executeJavaScript("eskimoMarathonServicesConfig.proceedWithMarathonInstallation ( { }, false);");

        assertJavascriptEquals("testError", "window.consoleError");

        // 2. pass command
        page.executeJavaScript("eskimoMain.getMarathonOperationsCommand = function () { " +
                "    return {" +
                "        showCommand: function (command) {" +
                "            window.command = command;" +
                "        }" +
                "    }" +
                "};");

        page.executeJavaScript("$.ajax = function (object) { object.success ( { command: 'testCommand' } );}");

        page.executeJavaScript("eskimoMarathonServicesConfig.proceedWithMarathonInstallation ( { }, false);");

        assertJavascriptEquals("testCommand", "window.command");
    }
}

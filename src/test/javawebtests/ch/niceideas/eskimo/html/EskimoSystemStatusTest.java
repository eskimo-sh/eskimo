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

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.model.SystemStatusWrapper;
import com.gargoylesoftware.htmlunit.ScriptException;
import com.gargoylesoftware.htmlunit.html.HtmlTableDataCell;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.*;

public class EskimoSystemStatusTest extends AbstractWebTest {

    private String jsonFullStatus = null;
    private String jsonNodesStatus = null;
    private String jsonStatusConfig = null;

    @Before
    public void setUp() throws Exception {

        jsonFullStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/testFullStatus.json"));
        jsonNodesStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/testNodeStatus.json"));
        jsonStatusConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/testStatusConfig.json"));

        loadScript(page, "eskimoUtils.js");
        loadScript(page, "eskimoSystemStatus.js");

        page.executeJavaScript("STATUS_SERVICES = [\"ntp\",\"zookeeper\",\"gluster\",\"gdash\",\"mesos-master\",\"mesos-agent\",\"kafka\",\"kafka-manager\",\"spark-history-server\",\"spark-executor\",\"logstash\",\"cerebro\",\"elasticsearch\",\"kibana\",\"zeppelin\"];");

        page.executeJavaScript("SERVICES_STATUS_CONFIG = " + jsonStatusConfig + ";");

        // instantiate test object
        page.executeJavaScript("eskimoSystemStatus = new eskimo.SystemStatus({\n" +
                "            eskimoNotifications: eskimoNotifications,\n" +
                "            eskimoMessaging: eskimoMessaging,\n" +
                "            eskimoNodesConfig: eskimoNodesConfig,\n" +
                "            eskimoSetup: eskimoSetup,\n" +
                "            eskimoServices: eskimoServices,\n" +
                "            eskimoMain: eskimoMain\n" +
                "        });");

        waitForElementIdInDOM("service-status-warning");

        // set services for tests
        page.executeJavaScript("eskimoSystemStatus.setStatusServices (STATUS_SERVICES);");
        page.executeJavaScript("eskimoSystemStatus.setServicesStatusConfig (SERVICES_STATUS_CONFIG);");

        page.executeJavaScript("$('#inner-content-status').css('display', 'inherit')");
        page.executeJavaScript("$('#inner-content-status').css('visibility', 'visible')");
    }

    @Test
    public void testRenderNodesStatusEmpty() throws Exception {

        page.executeJavaScript("eskimoSystemStatus.renderNodesStatusEmpty()");

        assertCssValue("#status-node-container-empty", "visibility", "inherit");
        assertCssValue("#status-node-container-empty", "display", "inherit");
    }

    @Test
    public void testRegisterMenu() throws Exception {

        testRenderNodesStatusTable();

        Exception error = null;
        try {
            page.executeJavaScript("eskimoSystemStatus.registerMenu('#status-node-table-body td.status-node-cell', 'status-node-cell');");
        } catch (Exception e) {
            error = e;
        }
        assertNull(error);

    }

    @Test
    public void testHideShowGrafanaDashboard() {

        page.executeJavaScript("eskimoSystemStatus.hideGrafanaDashboard()");

        assertCssValue("#status-monitoring-grafana", "display", "none");
        assertJavascriptEquals("col-xs-12 col-sm-12 col-md-12", "$('#status-monitoring-info-container').attr('class')");

        page.executeJavaScript("eskimoSystemStatus.showGrafanaDashboard()");

        assertCssValue("#status-monitoring-grafana", "display", "inherit");
        assertJavascriptEquals("col-xs-12 col-sm-12 col-md-4", "$('#status-monitoring-info-container').attr('class')");

        page.executeJavaScript("eskimoSystemStatus.hideGrafanaDashboard()");

        assertCssValue("#status-monitoring-grafana", "display", "none");
        assertJavascriptEquals("col-xs-12 col-sm-12 col-md-12", "$('#status-monitoring-info-container').attr('class')");
    }

    @Test
    public void testMenuTemplate() {
        assertJavascriptEquals("" +
                "    <li><a id=\"start\" tabindex=\"-1\" href=\"#\" title=\"Start Service\"><i class=\"fa fa-play\"></i> Start Service</a></li>\n" +
                "    <li><a id=\"stop\" tabindex=\"-1\" href=\"#\" title=\"Stop Service\"><i class=\"fa fa-stop\"></i> Stop Service</a></li>\n" +
                "    <li><a id=\"restart\" tabindex=\"-1\" href=\"#\" title=\"Restart Service\"><i class=\"fa fa-refresh\"></i> Restart Service</a></li>\n" +
                "    <li class=\"divider\"></li>    <li><a id=\"reinstall\" tabindex=\"-1\" href=\"#\" title=\"Reinstall Service\"><i class=\"fa fa-undo\"></i> Reinstall Service</a></li>\n" +
                "    <li class=\"divider\"></li>    <li><a id=\"show_journal\" tabindex=\"-1\" href=\"#\" title=\"Show Journal\"><i class=\"fa fa-file\"></i> Show Journal</a></li>\n" +
                "",
                "$('#serviceContextMenuTemplate').html()");
    }

    @Test
    public void testClickMenu() throws Exception {

        testRenderNodesStatusTable();

        assertNotNull (page.querySelector("#status-node-table-body td.status-node-cell"));

        ((HtmlTableDataCell)page.querySelector("#status-node-table-body td.status-node-cell")).click();

        assertJavascriptEquals("" +
                        "    <li><a id=\"start\" tabindex=\"-1\" href=\"#\" title=\"Start Service\"><i class=\"fa fa-play\"></i> Start Service</a></li>\n" +
                        "    <li><a id=\"stop\" tabindex=\"-1\" href=\"#\" title=\"Stop Service\"><i class=\"fa fa-stop\"></i> Stop Service</a></li>\n" +
                        "    <li><a id=\"restart\" tabindex=\"-1\" href=\"#\" title=\"Restart Service\"><i class=\"fa fa-refresh\"></i> Restart Service</a></li>\n" +
                        "    <li class=\"divider\"></li>    <li><a id=\"reinstall\" tabindex=\"-1\" href=\"#\" title=\"Reinstall Service\"><i class=\"fa fa-undo\"></i> Reinstall Service</a></li>\n" +
                        "    <li class=\"divider\"></li>    <li><a id=\"show_journal\" tabindex=\"-1\" href=\"#\" title=\"Show Journal\"><i class=\"fa fa-file\"></i> Show Journal</a></li>\n" +
                        "<li class=\"divider\"></li><li><a id=\"show_log\" tabindex=\"-1\" href=\"#\" title=\"Show Logs\"><i class=\"fa fa-file\"></i> Show Logs</a></li>\n" +
                        "",
                "$('#serviceContextMenu').html()");

        assertCssValue("#serviceContextMenu", "position", "absolute");
    }

    @Test
    public void testRenderNodesStatusTable() throws Exception {

        page.executeJavaScript("eskimoSystemStatus.renderNodesStatus(" + jsonNodesStatus + ", false)");

        String tableString = page.executeJavaScript("$('#status-node-table-body').html()").getJavaScriptResult().toString();

        assertNotNull (tableString);

        assertTrue (tableString.contains("192.168.10.11"));
        assertTrue (tableString.contains("192.168.10.13"));
    }

    @Test
    public void testShowStatus() throws Exception {

        page.executeJavaScript("$.ajax = function (options) {" +
                "    options.success(" + jsonFullStatus + ")" +
                "}");

        page.executeJavaScript("eskimoSystemStatus.showStatus()");

        System.err.println (page.asXml());

        String tableString = page.executeJavaScript("$('#status-node-table-body').html()").getJavaScriptResult().toString();

        assertNotNull (tableString);

        assertTrue (tableString.contains("192.168.10.11"));

        String infoActionsString = page.executeJavaScript("$('#status-monitoring-info-actions').html()").getJavaScriptResult().toString();

        assertNotNull (infoActionsString);

        assertTrue (infoActionsString.contains(" Use Zeppelin for your Data Science projects"));
    }

    @Test
    public void testStatusTableNodeFilteringWithButtons() throws Exception {

        testShowStatus();

        page.getElementById("show-issues-btn").click();

        String tableString = page.executeJavaScript("$('#status-node-table-body').html()").getJavaScriptResult().toString();

        assertNotNull (tableString);

        assertFalse (tableString.contains("192.168.10.11"));

        page.getElementById("show-master-services-btn").click();

        tableString = page.executeJavaScript("$('#status-node-table-body').html()").getJavaScriptResult().toString();

        assertNotNull (tableString);

        assertTrue (tableString.contains("192.168.10.11"));
    }

    @Test
    public void testDisplayMonitoringDashboard() throws Exception {

        page.executeJavaScript("$.ajax = function (options) {" +
                "    options.error()" +
                "}");

        page.executeJavaScript("eskimoSystemStatus.displayMonitoringDashboard('abcd', '50');");

        assertJavascriptEquals("<b>Grafana doesn't know dashboard with ID abcd</b>", "$('#status-monitoring-no-dashboard').html()");

        page.executeJavaScript("$.ajax = function (options) {" +
                "    options.success()" +
                "}");

        // HTMLUnit cannot load grafana
        ScriptException exception = assertThrows(ScriptException.class,
                () -> page.executeJavaScript("eskimoSystemStatus.displayMonitoringDashboard('abcd', '50');"));
        assertTrue(exception.getMessage().endsWith("grafana/d/abcd/monitoring?orgId=1&&kiosk&refresh=50"));

        await().atMost(15, TimeUnit.SECONDS).until(() -> page.executeJavaScript("$('#status-monitoring-no-dashboard').css('display')").getJavaScriptResult().toString().equals("none"));

        assertCssValue("#status-monitoring-dashboard-frame", "display", "inherit");
        assertCssValue("#status-monitoring-no-dashboard", "display", "none");
    }

    @Test
    public void testRenderNodesStatusTableFiltering() throws Exception {

        JsonWrapper statusWrapper = new JsonWrapper(jsonNodesStatus);
        statusWrapper.setValueForPath("service_kafka_192-168-10-13", "NA");
        statusWrapper.setValueForPath("service_logstash_192-168-10-13", "KO");

        page.executeJavaScript("eskimoSystemStatus.setNodeFilter(\"\")");

        page.executeJavaScript("eskimoSystemStatus.renderNodesStatus(" + statusWrapper.getFormattedValue() + ", false)");

        String tableString = page.executeJavaScript("$('#status-node-table-body').html()").getJavaScriptResult().toString();
        assertNotNull (tableString);
        assertTrue (tableString.contains("192.168.10.11"));
        assertTrue (tableString.contains("192.168.10.13"));

        page.executeJavaScript("eskimoSystemStatus.setNodeFilter(\"issues\")");

        page.executeJavaScript("eskimoSystemStatus.renderNodesStatus(" + statusWrapper.getFormattedValue() + ", false)");

        tableString = page.executeJavaScript("$('#status-node-table-body').html()").getJavaScriptResult().toString();
        assertNotNull (tableString);
        assertFalse (tableString.contains("192.168.10.11"));
        assertTrue (tableString.contains("192.168.10.13"));

        page.executeJavaScript("eskimoSystemStatus.setNodeFilter(\"master\")");

        page.executeJavaScript("eskimoSystemStatus.renderNodesStatus(" + statusWrapper.getFormattedValue() + ", false)");

        tableString = page.executeJavaScript("$('#status-node-table-body').html()").getJavaScriptResult().toString();
        assertNotNull (tableString);
        assertTrue (tableString.contains("192.168.10.11"));
        assertFalse (tableString.contains("192.168.10.13"));
    }

    @Test
    public void testHandleSystemStatus() throws Exception {

        String jsonFullStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/testFullStatus.json"));

        page.executeJavaScript("var jsonFullStatus = " + jsonFullStatus);

        // grafana not available
        page.executeJavaScript("eskimoServices.isServiceAvailable = function () { return false; }");

        page.executeJavaScript("eskimoSystemStatus.handleSystemStatus (jsonFullStatus.nodeServicesStatus, jsonFullStatus.systemStatus, true)");

        assertCssValue("#status-monitoring-no-dashboard", "display", "inherit");
        assertCssValue("#status-monitoring-dashboard-frame", "display", "none");

        assertAttrValue("#status-monitoring-dashboard-frame", "src", "html/emptyPage.html");

        // grafana available
        page.executeJavaScript("eskimoServices.isServiceAvailable = function () { return true; }");

        page.executeJavaScript("eskimoSystemStatus.handleSystemStatus (jsonFullStatus.nodeServicesStatus, jsonFullStatus.systemStatus, true)");

        /* FIXME Find out why this fails
        await().atMost(15, TimeUnit.SECONDS).until(() -> page.executeJavaScript("$('#status-monitoring-no-dashboard').css('display')").getJavaScriptResult().toString().equals("none"));

        assertCssValue("#status-monitoring-no-dashboard", "display", "none");
        assertCssValue("#status-monitoring-dashboard-frame", "display", "inherit");
        */

        assertJavascriptEquals("<span style=\"color: darkgreen;\">OK</span>", "$('#system-information-nodes-status').html()");

        assertJavascriptEquals("<span style=\"color: darkgreen;\">OK</span>", "$('#system-information-services-status').html()");

        // ruin a service and a node
        SystemStatusWrapper ssw = new SystemStatusWrapper(jsonFullStatus);
        ssw.setValueForPath("nodeServicesStatus.service_mesos-agent_192-168-10-11", "KO");
        ssw.setValueForPath("nodeServicesStatus.node_alive_192-168-10-13", "KO");
        ssw.setValueForPath("nodeServicesStatus.node_address_192-168-10-13", "192.168.10.13");
        page.executeJavaScript("jsonFullStatus = " + ssw.getFormattedValue());

        page.executeJavaScript("eskimoSystemStatus.handleSystemStatus (jsonFullStatus.nodeServicesStatus, jsonFullStatus.systemStatus, true)");

        await().atMost(15, TimeUnit.SECONDS).until(() -> page.executeJavaScript("$('#system-information-nodes-status').html()").getJavaScriptResult().toString()
                .equals("Following nodes are reporting problems : <span style=\"color: darkred;\">192.168.10.13</span>"));

        assertJavascriptEquals("Following nodes are reporting problems : <span style=\"color: darkred;\">192.168.10.13</span>",
                "$('#system-information-nodes-status').html()");

        assertJavascriptEquals("Following services are reporting problems : <span style=\"color: darkred;\">mesos-agent</span>",
                "$('#system-information-services-status').html()");

        String expectedHtmlInformation = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/expectedHtmlInformation.html"));
        assertJavascriptEquals(expectedHtmlInformation.replace("\n", "").replace("  ", ""), "$('#system-information-actions').html()");
    }

    @Test
    public void testShowStatusMessage() throws Exception {

        page.executeJavaScript("eskimoSystemStatus.showStatusMessage ('test');");

        assertCssValue("#service-status-warning", "display", "block");
        assertCssValue("#service-status-warning", "visibility", "visible");

        assertAttrValue("#service-status-warning-message", "class", "alert alert-warning");

        assertJavascriptEquals("test", "$('#service-status-warning-message').html()");

        page.executeJavaScript("eskimoSystemStatus.showStatusMessage ('test', true);");

        assertAttrValue("#service-status-warning-message", "class", "alert alert-danger");
    }
}

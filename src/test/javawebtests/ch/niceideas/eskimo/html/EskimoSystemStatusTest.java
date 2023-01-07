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

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.model.SystemStatusWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class EskimoSystemStatusTest extends AbstractWebTest {

    private String jsonFullStatus = null;
    private String jsonNodesStatus = null;
    private String jsonStatusConfig = null;
    private String jsonMastersStatus = null;

    @BeforeEach
    public void setUp() throws Exception {

        jsonFullStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/testFullStatus.json"), StandardCharsets.UTF_8);
        jsonNodesStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/testNodeStatus.json"), StandardCharsets.UTF_8);
        jsonStatusConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/testStatusConfig.json"), StandardCharsets.UTF_8);
        jsonMastersStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/testMastersStatus.json"), StandardCharsets.UTF_8);

        loadScript("eskimoUtils.js");
        loadScript("eskimoSystemStatus.js");

        js("window.STATUS_SERVICES = [\"ntp\",\"zookeeper\",\"gluster\",\"mesos-master\",\"mesos-agent\",\"kafka\",\"kafka-manager\",\"spark-console\",\"spark-runtime\",\"logstash\",\"cerebro\",\"elasticsearch\",\"kibana\",\"zeppelin\"];");

        js("window.SERVICES_STATUS_CONFIG = " + jsonStatusConfig + ";");

        // instantiate test object
        js("eskimoSystemStatus = new eskimo.SystemStatus()");
        js("eskimoSystemStatus.eskimoNotifications = eskimoNotifications");
        js("eskimoSystemStatus.eskimoOperations = eskimoOperations");
        js("eskimoSystemStatus.eskimoMenu = eskimoMenu");
        js("eskimoSystemStatus.eskimoNodesConfig = eskimoNodesConfig");
        js("eskimoSystemStatus.eskimoSetup = eskimoSetup");
        js("eskimoSystemStatus.eskimoServices = eskimoServices");
        js("eskimoSystemStatus.eskimoMain = eskimoMain");

        js("$.ajaxGet = function(callback) { console.log(callback); }");

        js("eskimoSystemStatus.initialize()");

        waitForElementIdInDOM("service-status-warning");

        // set services for tests
        js("eskimoSystemStatus.setStatusServices (STATUS_SERVICES);");
        js("eskimoSystemStatus.setServicesStatusConfig (SERVICES_STATUS_CONFIG);");

        js("$('#inner-content-status').css('display', 'inherit')");
        js("$('#inner-content-status').css('visibility', 'visible')");

        js("window.eskimoFlavour = \"CE\"");
    }

    @Test
    public void testRenderNodesStatusEmpty() throws Exception {

        js("eskimoSystemStatus.renderNodesStatusEmpty()");

        assertCssValue("#status-node-container-empty", "visibility", "visible");
        assertCssValue("#status-node-container-empty", "display", "block");
    }

    @Test
    public void testRegisterServiceMenu() throws Exception {

        testRenderNodesStatusTable();

        Exception error = null;
        try {
            js("eskimoSystemStatus.registerServiceMenu('#status-node-table-body td.status-node-cell', 'status-node-cell');");
        } catch (Exception e) {
            error = e;
        }
        assertNull(error);
    }

    @Test
    public void testRegisterNodeMenu() throws Exception {

        testRenderNodesStatusTable();

        Exception error = null;
        try {
            js("eskimoSystemStatus.registerNodeMenu('#status-node-table-body td.status-node-cell-intro', 'status-node-cell-intro');");
        } catch (Exception e) {
            error = e;
        }
        assertNull(error);
    }

    @Test
    public void testHideShowGrafanaDashboard() {

        js("eskimoSystemStatus.hideGrafanaDashboard()");

        assertCssValue("#status-monitoring-grafana", "display", "none");
        assertJavascriptEquals("col-xs-12 col-sm-12 col-md-12", "$('#status-monitoring-info-container').attr('class')");

        js("eskimoSystemStatus.showGrafanaDashboard()");

        assertCssValue("#status-monitoring-grafana", "display", "block");
        assertJavascriptEquals("col-xs-12 col-sm-12 col-md-4", "$('#status-monitoring-info-container').attr('class')");

        js("eskimoSystemStatus.hideGrafanaDashboard()");

        assertCssValue("#status-monitoring-grafana", "display", "none");
        assertJavascriptEquals("col-xs-12 col-sm-12 col-md-12", "$('#status-monitoring-info-container').attr('class')");
    }

    @Test
    public void testNodeMenuTemplate() {
        js("eskimoSystemStatus.initializeStatusTableMenus()");
        assertJavascriptEquals("" +
                        "    <li><a id=\"terminal\" tabindex=\"-1\" href=\"#\" title=\"Start Service\"><i class=\"fa fa-terminal\"></i> SSH Terminal</a></li>\n" +
                        "    <li><a id=\"file_manager\" tabindex=\"-1\" href=\"#\" title=\"Stop Service\"><i class=\"fa fa-folder\"></i> SFTP File Manager</a></li>\n",
                "$('#nodeContextMenuTemplate').html()");
    }

    @Test
    public void testClickServiceMenu() throws Exception {

        testRenderNodesStatusTable();

        js("eskimoSystemStatus.initializeStatusTableMenus()");

        assertNotNull (driver.findElement(By.cssSelector("#status-node-table-body td.status-node-cell")));

        driver.findElement(By.cssSelector("#status-node-table-body td.status-node-cell")).click();

        assertJavascriptEquals("    <li><a id=\"start\" tabindex=\"-1\" href=\"#\" title=\"Start Service\"><i class=\"fa fa-play\"></i> Start Service</a></li>\n" +
                        "    <li><a id=\"stop\" tabindex=\"-1\" href=\"#\" title=\"Stop Service\"><i class=\"fa fa-stop\"></i> Stop Service</a></li>\n" +
                        "    <li><a id=\"restart\" tabindex=\"-1\" href=\"#\" title=\"Restart Service\"><i class=\"fa fa-refresh\"></i> Restart Service</a></li>\n" +
                        "    <li class=\"dropdown-divider\"></li>    <li><a id=\"reinstall\" tabindex=\"-1\" href=\"#\" title=\"Reinstall Service\"><i class=\"fa fa-undo\"></i> Reinstall Service</a></li>\n" +
                        "    <li class=\"dropdown-divider\"></li>    <li><a id=\"show_journal\" tabindex=\"-1\" href=\"#\" title=\"Show Journal\"><i class=\"fa fa-file\"></i> Show Journal</a></li>\n" +
                        "<li class=\"dropdown-divider\"></li><li><a id=\"show_log\" tabindex=\"-1\" href=\"#\" title=\"Show Logs\"><i class=\"fa fa-file\"></i> Show Logs</a></li>\n",
                "$('#serviceContextMenu').html()");

        assertCssValue("#serviceContextMenu", "position", "absolute");
    }

    @Test
    public void testClickNodeMenu() throws Exception {

        testRenderNodesStatusTable();

        js("eskimoSystemStatus.initializeStatusTableMenus()");

        assertNotNull (driver.findElement(By.cssSelector("#status-node-table-body td.status-node-cell-intro")));

        driver.findElement(By.cssSelector("#status-node-table-body td.status-node-cell-intro")).click();

        assertJavascriptEquals("" +
                        "    <li><a id=\"terminal\" tabindex=\"-1\" href=\"#\" title=\"Start Service\"><i class=\"fa fa-terminal\"></i> SSH Terminal</a></li>\n" +
                        "    <li><a id=\"file_manager\" tabindex=\"-1\" href=\"#\" title=\"Stop Service\"><i class=\"fa fa-folder\"></i> SFTP File Manager</a></li>\n",
                "$('#nodeContextMenu').html()");

        assertCssValue("#nodeContextMenu", "position", "absolute");
    }

    @Test
    public void testRenderNodesStatusTable() throws Exception {

        js("eskimoSystemStatus.renderNodesStatus(" + jsonNodesStatus + ", null, false)");

        String tableString = js("return $('#status-node-table-body').html()").toString();

        assertNotNull (tableString);

        assertTrue (tableString.contains("192.168.10.11"));
        assertTrue (tableString.contains("192.168.10.13"));
    }

    @Test
    public void testShowStatus() throws Exception {

        js("$.ajaxGet = function (options) {" +
                "    options.success(" + jsonFullStatus + ")" +
                "}");

        js("eskimoSystemStatus.showStatus()");

        //System.err.println (page.asXml());

        String tableString = js("return $('#status-node-table-body').html()").toString();

        assertNotNull (tableString);

        assertTrue (tableString.contains("192.168.10.11"));
    }

    @Test
    public void testStatusTableNodeFilteringWithButtons() throws Exception {

        testShowStatus();

        getElementById("show-issues-btn").click();

        String tableString = js("return $('#status-node-table-body').html()").toString();

        assertNotNull (tableString);

        assertFalse (tableString.contains("192.168.10.11"));

        getElementById("show-master-services-btn").click();

        tableString = js("return $('#status-node-table-body').html()").toString();

        assertNotNull (tableString);

        assertTrue (tableString.contains("192.168.10.11"));
    }

    @Test
    public void testDisplayMonitoringDashboard() throws Exception {

        js("$.ajax = function (options) {" +
                "    options.error()" +
                "}");

        js("eskimoSystemStatus.displayMonitoringDashboard('abcd', '50');");

        assertJavascriptEquals("<strong>Grafana doesn't know dashboard with ID abcd</strong>", "$('#status-monitoring-no-dashboard').html()");

        js("$.ajax = function (options) {" +
                "    options.success()" +
                "}");

        // HTMLUnit cannot load grafana
        js("eskimoSystemStatus.displayMonitoringDashboard('abcd', '50');");
        assertJavascriptEquals("grafana/d/abcd/monitoring?orgId=1&&kiosk&refresh=50", "$('#status-monitoring-dashboard-frame').attr('src')");

        await().atMost(15, TimeUnit.SECONDS).until(() -> js("return $('#status-monitoring-no-dashboard').css('display')").toString().equals("none"));

        assertCssValue("#status-monitoring-dashboard-frame", "display", "block");
        assertCssValue("#status-monitoring-no-dashboard", "display", "none");
    }

    @Test
    public void testRenderNodesStatusTableFiltering() throws Exception {

        JsonWrapper statusWrapper = new JsonWrapper(jsonNodesStatus);
        statusWrapper.setValueForPath("service_kafka_192-168-10-13", "NA");
        statusWrapper.setValueForPath("service_logstash_192-168-10-13", "KO");

        js("eskimoSystemStatus.setNodeFilter(\"\")");

        js("eskimoSystemStatus.renderNodesStatus(" + statusWrapper.getFormattedValue() + ", false)");

        String tableString = js("return $('#status-node-table-body').html()").toString();
        assertNotNull (tableString);
        assertTrue (tableString.contains("192.168.10.11"));
        assertTrue (tableString.contains("192.168.10.13"));

        js("eskimoSystemStatus.setNodeFilter(\"issues\")");

        js("eskimoSystemStatus.renderNodesStatus(" + statusWrapper.getFormattedValue() + ", false)");

        tableString = js("return $('#status-node-table-body').html()").toString();
        assertNotNull (tableString);
        assertFalse (tableString.contains("192.168.10.11"));
        assertTrue (tableString.contains("192.168.10.13"));

        js("eskimoSystemStatus.setNodeFilter(\"master\")");

        js("eskimoSystemStatus.renderNodesStatus(" + statusWrapper.getFormattedValue() + ", false)");

        tableString = js("return $('#status-node-table-body').html()").toString();
        assertNotNull (tableString);
        assertTrue (tableString.contains("192.168.10.11"));
        assertFalse (tableString.contains("192.168.10.13"));
    }

    @Test
    public void testHandleSystemStatus() throws Exception {

        String jsonFullStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/testFullStatus.json"), StandardCharsets.UTF_8);

        js("window.jsonFullStatus = " + jsonFullStatus);

        // grafana not available
        js("eskimoServices.isServiceAvailable = function () { return false; }");

        js("eskimoSystemStatus.handleSystemStatus (jsonFullStatus.nodeServicesStatus, jsonFullStatus.systemStatus, true)");

        assertCssValue("#status-monitoring-no-dashboard", "display", "block");
        assertCssValue("#status-monitoring-dashboard-frame", "display", "none");

        assertAttrValue("#status-monitoring-dashboard-frame", "src", "html/emptyPage.html");

        // grafana available
        js("eskimoServices.isServiceAvailable = function () { return true; }");

        js("eskimoSystemStatus.handleSystemStatus (jsonFullStatus.nodeServicesStatus, jsonFullStatus.systemStatus, true)");

        /* FIXME Find out why this fails
        await().atMost(15, TimeUnit.SECONDS).until(() -> js("$('#status-monitoring-no-dashboard').css('display')").getJavaScriptResult().toString().equals("none"));

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
        js("window.jsonFullStatus = " + ssw.getFormattedValue());

        js("eskimoSystemStatus.handleSystemStatus (jsonFullStatus.nodeServicesStatus, jsonFullStatus.systemStatus, true)");

        await().atMost(15, TimeUnit.SECONDS).until(() -> js("return $('#system-information-nodes-status').html()").toString()
                .equals("Following nodes are reporting problems : <span style=\"color: darkred;\">192.168.10.13</span>"));

        assertJavascriptEquals("Following nodes are reporting problems : <span style=\"color: darkred;\">192.168.10.13</span>",
                "$('#system-information-nodes-status').html()");

        assertJavascriptEquals("Following services are reporting problems : <span style=\"color: darkred;\">mesos-agent</span>",
                "$('#system-information-services-status').html()");
    }

    @Test
    public void testShowStatusMessage() throws Exception {

        js("eskimoSystemStatus.showStatusMessage ('test');");

        assertCssValue("#service-status-warning", "display", "block");
        assertCssValue("#service-status-warning", "visibility", "visible");

        assertAttrContains("#service-status-warning-message", "class", "warning");

        assertJavascriptEquals("test", "$('#service-status-warning-message').html()");

        js("eskimoSystemStatus.showStatusMessage ('test', true);");

        assertAttrContains("#service-status-warning-message", "class", "danger");
    }

    @Test
    public void testRenderNodesStatusCallsServiceMenuServiceFoundHook() throws Exception {

        js("window.serviceMenuServiceFoundHookCalls = '';");

        js("eskimoServices.serviceMenuServiceFoundHook = function (nodeName, nodeAddress, service, found, blocking) {" +
                "window.serviceMenuServiceFoundHookCalls = window.serviceMenuServiceFoundHookCalls + " +
                "    (nodeName + '-' + nodeAddress + '-' + service + '-' + found + '-' + blocking + '\\n');" +
                "}");

        js("eskimoSystemStatus.renderNodesStatus(" + jsonNodesStatus + ", " + jsonMastersStatus + ", false)");

        assertJavascriptEquals("192-168-10-11-192.168.10.11-zookeeper-true-false\n" +
                "192-168-10-11-192.168.10.11-gluster-true-false\n" +
                "192-168-10-11-192.168.10.11-mesos-master-true-false\n" +
                "192-168-10-11-192.168.10.11-kafka-manager-true-false\n" +
                "192-168-10-11-192.168.10.11-spark-console-true-false\n" +
                "192-168-10-11-192.168.10.11-cerebro-true-false\n" +
                "192-168-10-11-192.168.10.11-kibana-true-false\n" +
                "192-168-10-11-192.168.10.11-zeppelin-true-false\n", "window.serviceMenuServiceFoundHookCalls");
    }

    @Test
    public void testInitializeStatusTableMenusAdmin() {
        js("eskimoSystemStatus.initializeStatusTableMenus()");
        assertJavascriptEquals("    <li><a id=\"start\" tabindex=\"-1\" href=\"#\" title=\"Start Service\"><i class=\"fa fa-play\"></i> Start Service</a></li>\n" +
                        "    <li><a id=\"stop\" tabindex=\"-1\" href=\"#\" title=\"Stop Service\"><i class=\"fa fa-stop\"></i> Stop Service</a></li>\n" +
                        "    <li><a id=\"restart\" tabindex=\"-1\" href=\"#\" title=\"Restart Service\"><i class=\"fa fa-refresh\"></i> Restart Service</a></li>\n" +
                        "    <li class=\"dropdown-divider\"></li>    <li><a id=\"reinstall\" tabindex=\"-1\" href=\"#\" title=\"Reinstall Service\"><i class=\"fa fa-undo\"></i> Reinstall Service</a></li>\n" +
                        "    <li class=\"dropdown-divider\"></li>    <li><a id=\"show_journal\" tabindex=\"-1\" href=\"#\" title=\"Show Journal\"><i class=\"fa fa-file\"></i> Show Journal</a></li>\n",
                "$('#serviceContextMenuTemplate').html()");
    }

    @Test
    public void testInitializeStatusTableMenusUser() {
        js("eskimoMain.hasRole = function(role) { return false; }");
        js("eskimoSystemStatus.initializeStatusTableMenus()");
        assertJavascriptEquals("" +
                        "    <li><a id=\"show_journal\" tabindex=\"-1\" href=\"#\" title=\"Show Journal\"><i class=\"fa fa-file\"></i> Show Journal</a></li>\n" +
                        "",
                "$('#serviceContextMenuTemplate').html()");
    }
}

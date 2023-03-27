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

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.model.SystemStatusWrapper;
import ch.niceideas.eskimo.utils.ActiveWaiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class EskimoSystemStatusTest extends AbstractWebTest {

    private String jsonNodesStatus = null;
    private String jsonMastersStatus = null;

    @BeforeEach
    public void setUp() throws Exception {

        String jsonFullStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/testFullStatus.json"), StandardCharsets.UTF_8);
        jsonNodesStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/testNodeStatus.json"), StandardCharsets.UTF_8);
        jsonMastersStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/testMastersStatus.json"), StandardCharsets.UTF_8);

        String jsonStatusConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSystemStatusTest/testStatusConfig.json"), StandardCharsets.UTF_8);

        loadScript("eskimoUtils.js");
        loadScript("eskimoSystemStatus.js");

        waitForDefinition("window.eskimo");
        waitForDefinition("window.eskimo.SystemStatus");

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

        js("$.ajaxGet = function(callback) { " +
                "if (callback.url == \"get-ui-services-status-config\") {\n" +
                "    callback.success({'status': 'OK', 'uiServicesStatusConfig': SERVICES_STATUS_CONFIG});\n" +
                "} else if (callback.url == \"list-services\") {\n" +
                "    callback.success({'status': 'OK', 'services': STATUS_SERVICES});\n" +
                "} else if (callback.url == \"get-status\") {\n" +
                "    callback.success(" + jsonFullStatus + ");\n" +
                "}\n"+
                "console.log(callback); }");

        js("eskimoSystemStatus.initialize()");

        waitForElementIdInDOM("service-status-warning");

        js("$('#inner-content-status').css('display', 'inherit')");
        js("$('#inner-content-status').css('visibility', 'visible')");

        js("window.eskimoFlavour = \"CE\"");
    }

    @Test
    public void testShowJournal() {
        js("$.ajaxGet = function(callback) { " +
                "    window.actionUrl = callback.url;" +
                "    callback.success({'message': 'journal OK'});\n" +
                "    console.log(callback); " +
                "}");

        js("eskimoSystemStatus.showJournal('kibana', '192.168.56.11')");

        assertJavascriptEquals("show-journal?service=kibana&nodeAddress=192.168.56.11", "window.actionUrl");
        assertJavascriptEquals("journal OK", "$('#service-status-warning-message').html()");
    }

    @Test
    public void testStartService() {
        js("$.ajaxGet = function(callback) { " +
                "    window.actionUrl = callback.url;" +
                "    callback.success({'message': 'start OK'});\n" +
                "    console.log(callback); " +
                "}");

        js("eskimoSystemStatus.startService('kibana', '192.168.56.11')");

        assertJavascriptEquals("start-service?service=kibana&nodeAddress=192.168.56.11", "window.actionUrl");
        assertJavascriptEquals("start OK", "$('#service-status-warning-message').html()");
    }

    @Test
    public void testStopService() {
        js("$.ajaxGet = function(callback) { " +
                "    window.actionUrl = callback.url;" +
                "    callback.success({'message': 'stop OK'});\n" +
                "    console.log(callback); " +
                "}");

        js("eskimoSystemStatus.stopService('kibana', '192.168.56.11')");

        assertJavascriptEquals("stop-service?service=kibana&nodeAddress=192.168.56.11", "window.actionUrl");
        assertJavascriptEquals("stop OK", "$('#service-status-warning-message').html()");
    }

    @Test
    public void testRestartService() {
        js("$.ajaxGet = function(callback) { " +
                "    window.actionUrl = callback.url;" +
                "    callback.success({'message': 'restart OK'});\n" +
                "    console.log(callback); " +
                "}");

        js("eskimoSystemStatus.restartService('kibana', '192.168.56.11')");

        assertJavascriptEquals("restart-service?service=kibana&nodeAddress=192.168.56.11", "window.actionUrl");
        assertJavascriptEquals("restart OK", "$('#service-status-warning-message').html()");
    }

    @Test
    public void testFetchOperationResult() {
        js("$.ajaxGet = function(callback) { " +
                "    window.actionUrl = callback.url;" +
                "    callback.success({'status': 'OK', 'success': 'operation OK'});\n" +
                "    console.log(callback); " +
                "}");

        js("eskimoSystemStatus.fetchOperationResult()");

        assertJavascriptEquals("true", "window.scheduleStopOperationInProgress");

        js("$.ajaxGet = function(callback) { " +
                "    window.actionUrl = callback.url;" +
                "    callback.success({'status': 'KO', 'error': 'operation failed'});\n" +
                "    console.log(callback); " +
                "}");

        js("eskimoSystemStatus.fetchOperationResult()");

        assertJavascriptEquals("3 : operation failed", "window.lastAlert");
    }

    @Test
    public void testReinstallService() {
        js("$.ajaxGet = function(callback) { " +
                "    window.actionUrl = callback.url;" +
                "    callback.success({'message': 'reinstall OK'});\n" +
                "    console.log(callback); " +
                "}");

        js("eskimoSystemStatus.reinstallService('kibana', '192.168.56.11')");

        assertJavascriptEquals("reinstall-service?service=kibana&nodeAddress=192.168.56.11", "window.actionUrl");
        assertJavascriptEquals("reinstall OK", "$('#service-status-warning-message').html()");
    }

    @Test
    public void testPerformServiceAction() {
        js("$.ajaxGet = function(callback) { " +
                "    window.actionUrl = callback.url;" +
                "    callback.success({'message': 'action OK'});\n" +
                "    console.log(callback); " +
                "}");

        js("eskimoSystemStatus.performServiceAction('test', 'kibana', '192.168.56.11')");

        assertJavascriptEquals("service-custom-action?action=test&service=kibana&nodeAddress=192.168.56.11", "window.actionUrl");
        assertJavascriptEquals("action OK", "$('#service-status-warning-message').html()");
    }

    @Test
    public void testRenderNodesStatusEmpty() {

        js("eskimoSystemStatus.renderNodesStatusEmpty()");

        assertCssEquals("visible", "#status-node-container-empty", "visibility");
        assertCssEquals("block", "#status-node-container-empty", "display");
    }

    @Test
    public void testRegisterServiceMenu() {

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
    public void testRegisterNodeMenu() {

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

        assertCssEquals("none", "#status-monitoring-grafana", "display");
        assertClassEquals("col-xs-12 col-sm-12 col-md-12", "#status-monitoring-info-container");

        js("eskimoSystemStatus.showGrafanaDashboard()");

        assertCssEquals("block", "#status-monitoring-grafana", "display");
        assertClassEquals("col-xs-12 col-sm-12 col-md-4", "#status-monitoring-info-container");

        js("eskimoSystemStatus.hideGrafanaDashboard()");

        assertCssEquals("none", "#status-monitoring-grafana", "display");
        assertClassEquals("col-xs-12 col-sm-12 col-md-12", "#status-monitoring-info-container");
    }

    @Test
    public void testNodeMenuTemplate() {
        js("eskimoSystemStatus.initializeStatusTableMenus()");
        assertJavascriptEquals("" +
                        "    <li><a id=\"terminal\" tabindex=\"-1\" href=\"#\" title=\"Launch SSH Terminal\"><i class=\"fa fa-terminal\"></i> SSH Terminal</a></li>\n" +
                        "    <li><a id=\"file_manager\" tabindex=\"-1\" href=\"#\" title=\"Launch SFTP File Manager\"><i class=\"fa fa-folder\"></i> SFTP File Manager</a></li>\n",
                "$('#nodeContextMenuTemplate').html()");
    }

    @Test
    public void testClickServiceMenu() {

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

        assertCssEquals("absolute", "#serviceContextMenu", "position");
    }

    @Test
    public void testClickNodeMenu() {

        testRenderNodesStatusTable();

        js("eskimoSystemStatus.initializeStatusTableMenus()");

        assertNotNull (driver.findElement(By.cssSelector("#status-node-table-body td.status-node-cell-intro")));

        driver.findElement(By.cssSelector("#status-node-table-body td.status-node-cell-intro")).click();

        assertJavascriptEquals("" +
                        "    <li><a id=\"terminal\" tabindex=\"-1\" href=\"#\" title=\"Launch SSH Terminal\"><i class=\"fa fa-terminal\"></i> SSH Terminal</a></li>\n" +
                        "    <li><a id=\"file_manager\" tabindex=\"-1\" href=\"#\" title=\"Launch SFTP File Manager\"><i class=\"fa fa-folder\"></i> SFTP File Manager</a></li>\n",
                "$('#nodeContextMenu').html()");

        assertCssEquals("absolute", "#nodeContextMenu", "position");
    }

    @Test
    public void testRenderNodesStatusTable() {

        js("eskimoSystemStatus.renderNodesStatus(" + jsonNodesStatus + ", null, false)");

        String tableString = js("return $('#status-node-table-body').html()").toString();

        assertNotNull (tableString);

        assertTrue (tableString.contains("192.168.10.11"));
        assertTrue (tableString.contains("192.168.10.13"));
    }

    @Test
    public void testShowStatus() {

        js("eskimoSystemStatus.showStatus()");

        assertCssEquals("visible", "#inner-content-status", "visibility");
        assertCssEquals("block", "#inner-content-status", "display");

        //System.err.println (page.asXml());

        ActiveWaiter.wait(() -> js("return $('#status-node-table-body').html()").toString().contains("192.168.10.1"));

        assertTrue (js("return $('.status-node-cell[data-eskimo-service=zookeeper').html()").toString().contains("status-node-cell-span-ok"));
        assertTrue (js("return $('.status-node-cell[data-eskimo-service=mesos-master').html()").toString().contains("status-node-cell-span-ok"));
        assertTrue (js("return $('.status-node-cell[data-eskimo-service=mesos-agent').html()").toString().contains("status-node-cell-span-ok"));
        assertTrue (js("return $('.status-node-cell[data-eskimo-service=kafka').html()").toString().contains("status-node-cell-span-ok"));
        assertTrue (js("return $('.status-node-cell[data-eskimo-service=kafka-manager').html()").toString().contains("status-node-cell-span-ok"));
        assertTrue (js("return $('.status-node-cell[data-eskimo-service=spark-console').html()").toString().contains("status-node-cell-span-ok"));
        assertTrue (js("return $('.status-node-cell[data-eskimo-service=spark-runtime').html()").toString().contains("status-node-cell-span-ok"));
        assertTrue (js("return $('.status-node-cell[data-eskimo-service=logstash').html()").toString().contains("status-node-cell-span-ok"));
        assertTrue (js("return $('.status-node-cell[data-eskimo-service=cerebro').html()").toString().contains("status-node-cell-span-ok"));
        assertTrue (js("return $('.status-node-cell[data-eskimo-service=elasticsearch').html()").toString().contains("status-node-cell-span-ok"));
        assertTrue (js("return $('.status-node-cell[data-eskimo-service=kibana').html()").toString().contains("status-node-cell-span-ok"));
        assertTrue (js("return $('.status-node-cell[data-eskimo-service=zeppelin').html()").toString().contains("status-node-cell-span-ok"));
    }

    @Test
    public void testUpdateStatus_clearSetup() {
        testShowStatus();

        js("$.ajaxGet = function(callback) {" +
                "    callback.success({clear: 'setup'});" +
                "}");

        js("eskimoSystemStatus.updateStatus()");

        assertJavascriptEquals("true", "window.handleSetupNotCompletedCalled");
    }

    @Test
    public void testUpdateStatus_clearNodes() {
        testShowStatus();

        js("$.ajaxGet = function(callback) {" +
                "    callback.success({clear: 'nodes'});" +
                "}");

        js("eskimoSystemStatus.updateStatus()");

        assertCssEquals("block", "#status-node-container-empty", "display");
        assertCssEquals("visible", "#status-node-container-empty", "visibility");
    }

    @Test
    public void testStatusTableNodeFilteringWithButtons() {

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
    public void testDisplayMonitoringDashboard() {

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

        ActiveWaiter.wait(() -> js("return $('#status-monitoring-no-dashboard').css('display')").toString().equals("none"));

        assertCssEquals("block", "#status-monitoring-dashboard-frame", "display");
        assertCssEquals("none", "#status-monitoring-no-dashboard", "display");
    }

    @Test
    public void testRenderNodesStatusTableFiltering() {

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

        assertCssEquals("block", "#status-monitoring-no-dashboard", "display");
        assertCssEquals("none", "#status-monitoring-dashboard-frame", "display");

        assertAttrEquals("html/emptyPage.html", "#status-monitoring-dashboard-frame", "src");

        assertJavascriptEquals("CE", "$('#eskimo-flavour').html()");
        assertJavascriptEquals("0.5-SNAPSHOT", "$('#system-information-version').html()");
        assertJavascriptEquals("2023-01-12T09:54:15Z", "$('#system-information-timestamp').html()");
        assertJavascriptEquals("eskimo", "$('#system-information-user').html()");
        assertJavascriptEquals("admin", "$('#logged-user').html()");
        assertJavascriptEquals("ADMIN", "$('#logged-role').html()");
        assertJavascriptEquals("4", "$('#system-number-nodes').html()");
        assertJavascriptEquals("11 / 11", "$('#system-native-services').html()");
        assertJavascriptEquals("12 / 12", "$('#system-kube-services').html()");

        // grafana available
        js("eskimoServices.isServiceAvailable = function () { return true; }");

        js("eskimoSystemStatus.handleSystemStatus (jsonFullStatus.nodeServicesStatus, jsonFullStatus.systemStatus, true)");

        /* FIXME Find out why this fails
        await().atMost(15, TimeUnit.SECONDS).until(() -> js("$('#status-monitoring-no-dashboard').css('display')").getJavaScriptResult().toString().equals("none"));

        assertCssValue("#status-monitoring-no-dashboard", "display", "none");
        assertCssValue("#status-monitoring-dashboard-frame", "display", "inherit");
        */

        assertJavascriptEquals("<span class=\"status-node-cell-span-ok\">OK</span>", "$('#system-information-nodes-status').html()");

        assertJavascriptEquals("<span class=\"status-node-cell-span-ok\">OK</span>", "$('#system-information-services-status').html()");

        // ruin a service and a node
        SystemStatusWrapper ssw = new SystemStatusWrapper(jsonFullStatus);
        ssw.setValueForPath("nodeServicesStatus.service_mesos-agent_192-168-10-11", "KO");
        ssw.setValueForPath("nodeServicesStatus.node_alive_192-168-10-13", "KO");
        ssw.setValueForPath("nodeServicesStatus.node_address_192-168-10-13", "192.168.10.13");
        js("window.jsonFullStatus = " + ssw.getFormattedValue());

        js("eskimoSystemStatus.handleSystemStatus (jsonFullStatus.nodeServicesStatus, jsonFullStatus.systemStatus, true)");

        ActiveWaiter.wait(() -> js("return $('#system-information-nodes-status').html()").toString()
                .equals("Following nodes are reporting problems : <span class=\"status-node-cell-span-restart\">192.168.10.13</span>"));

        assertJavascriptEquals("Following nodes are reporting problems : <span class=\"status-node-cell-span-restart\">192.168.10.13</span>",
                "$('#system-information-nodes-status').html()");

        assertJavascriptEquals("Following services are reporting problems : <span class=\"status-node-cell-span-restart\">mesos-agent</span>",
                "$('#system-information-services-status').html()");
    }

    @Test
    public void testShowStatusMessage() {

        js("eskimoSystemStatus.showStatusMessage ('test');");

        assertCssEquals("block", "#service-status-warning", "display");
        assertCssEquals("visible", "#service-status-warning", "visibility");

        assertClassContains("warning", "#service-status-warning-message");

        assertJavascriptEquals("test", "$('#service-status-warning-message').html()");

        js("eskimoSystemStatus.showStatusMessage ('test', true);");

        assertClassContains("danger", "#service-status-warning-message");
    }

    @Test
    public void testRenderNodesStatusCallsServiceMenuServiceFoundHook() {

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

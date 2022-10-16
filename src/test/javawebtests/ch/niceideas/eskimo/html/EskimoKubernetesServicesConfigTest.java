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
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EskimoKubernetesServicesConfigTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript("eskimoUtils.js");
        loadScript("eskimoKubernetesServicesConfigChecker.js");
        loadScript("eskimoKubernetesServicesConfig.js");

        /*
        js("UNIQUE_SERVICES = [\"zookeeper\", \"mesos-master\", \"cerebro\", \"kibana\", \"spark-console\", \"zeppelin\", \"kafka-manager\", \"flink-app-master\", \"grafana\"];");
        js("MULTIPLE_SERVICES = [\"ntp\", \"elasticsearch\", \"kafka\", \"mesos-agent\", \"spark-runtime\", \"gluster\", \"logstash\", \"flink-worker\", \"prometheus\"];");
        js("MANDATORY_SERVICES = [\"ntp\", \"gluster\"];");
        js("CONFIGURED_SERVICES = UNIQUE_SERVICES.concat(MULTIPLE_SERVICES);");

        */

        // instantiate test object
        js("eskimoKubernetesServicesConfig = new eskimo.KubernetesServicesConfig();");
        js("eskimoKubernetesServicesConfig.eskimoMain = eskimoMain;");
        js("eskimoKubernetesServicesConfig.eskimoKubernetesServicesSelection = eskimoKubernetesServicesSelection");
        js("eskimoKubernetesServicesConfig.eskimoKubernetesOperationsCommand = eskimoKubernetesOperationsCommand");
        js("eskimoKubernetesServicesConfig.eskimoNodesConfig = eskimoNodesConfig");
        js("eskimoKubernetesServicesConfig.initialize();");

        waitForElementIdInDOM("reset-kubernetes-servicesconfig");

        js("eskimoKubernetesServicesConfig.setKubernetesServicesForTest([\n" +
                "    \"cerebro\",\n" +
                "    \"grafana\",\n" +
                "    \"kafka-manager\",\n" +
                "    \"kibana\",\n" +
                "    \"spark-console\",\n" +
                "    \"zeppelin\"\n" +
                "  ]);");

        js("eskimoKubernetesServicesConfig.setKubernetesServicesConfigForTest({\n" +
                "    \"cerebro\": { \"kubeConfig\" : { \"request\": { \"cpu\": \"1\", \"ram\": \"1G\" }}},\n" +
                "    \"grafana\": { \"kubeConfig\" : { \"request\": { \"cpu\": \"1\", \"ram\": \"1G\" }}},\n" +
                "    \"kafka-manager\": { \"kubeConfig\" : { \"request\": { \"cpu\": \"1\", \"ram\": \"1G\" }}},\n" +
                "    \"kibana\": { \"kubeConfig\" : { \"request\": { \"cpu\": \"1\", \"ram\": \"1G\" }}},\n" +
                "    \"spark-console\": { \"kubeConfig\" : { \"request\": { \"cpu\": \"1\", \"ram\": \"1G\" }}},\n" +
                "    \"zeppelin\": { \"kubeConfig\" : { \"request\": { \"cpu\": \"1\", \"ram\": \"1G\" }}},\n" +
                "  });");

        js("$('#inner-content-kubernetes-services-config').css('display', 'inherit')");
        js("$('#inner-content-kubernetes-services-config').css('visibility', 'visible')");
    }

    @Test
    public void testSaveKubernetesServicesButtonClick() throws Exception {

        testRenderKubernetesConfig();

        js("window.checkKubernetesSetup = function (kubernetesConfig) {" +
                "    console.log (kubernetesConfig);" +
                "    window.savedKubernetesConfig = JSON.stringify (kubernetesConfig);" +
                "};");

        getElementById("save-kubernetes-servicesbtn").click();

        JSONObject expectedConfig = new JSONObject("" +
                "{\"zeppelin_ram\":\"1G\"," +
                "\"kibana_ram\":\"1G\"," +
                "\"kafka-manager_ram\":\"1G\"," +
                "\"cerebro_ram\":\"1G\"," +
                "\"kafka-manager_install\":\"on\"," +
                "\"spark-console_install\":\"on\"," +
                "\"spark-console_ram\":\"1G\"," +
                "\"kafka-manager_cpu\":\"1\"," +
                "\"grafana_ram\":\"1G\"," +
                "\"cerebro_install\":\"on\"," +
                "\"spark-console_cpu\":\"1\"," +
                "\"zeppelin_cpu\":\"1\"," +
                "\"kibana_cpu\":\"1\"," +
                "\"zeppelin_install\":\"on\"," +
                "\"kibana_install\":\"on\"," +
                "\"grafana_install\":\"on\"," +
                "\"cerebro_cpu\":\"1\"," +
                "\"grafana_cpu\":\"1\"}");

        JSONObject actualConfig = new JSONObject((String)js("window.savedKubernetesConfig"));
        assertTrue(expectedConfig.similar(actualConfig));
    }

    @Test
    public void testSelectAll() throws Exception {

        testRenderKubernetesConfig();

        js("eskimoKubernetesServicesConfig.selectAll()");

        assertEquals (false, js("return $('#kibana_install').get(0).checked"));
        assertEquals (false, js("return $('#zeppelin_install').get(0).checked"));
        assertEquals (false, js("return $('#grafana_install').get(0).checked"));
        assertEquals (false, js("return $('#cerebro_install').get(0).checked"));

        js("eskimoKubernetesServicesConfig.selectAll()");

        assertEquals (true, js("return $('#kibana_install').get(0).checked"));
        assertEquals (true, js("return $('#zeppelin_install').get(0).checked"));
        assertEquals (true, js("return $('#grafana_install').get(0).checked"));
        assertEquals (true, js("return $('#cerebro_install').get(0).checked"));

        js("eskimoKubernetesServicesConfig.selectAll()");

        assertEquals (false, js("return $('#kibana_install').get(0).checked"));
        assertEquals (false, js("return $('#zeppelin_install').get(0).checked"));
        assertEquals (false, js("return $('#grafana_install').get(0).checked"));
        assertEquals (false, js("return $('#cerebro_install').get(0).checked"));

    }

    @Test
    public void testRenderKubernetesConfig() throws Exception {
        js("eskimoKubernetesServicesConfig.renderKubernetesConfig({\n" +
                "    \"cerebro_install\": \"on\",\n" +
                "    \"zeppelin_install\": \"on\",\n" +
                "    \"kafka-manager_install\": \"on\",\n" +
                "    \"kibana_install\": \"on\",\n" +
                "    \"spark-console_install\": \"on\",\n" +
                "    \"grafana_install\": \"on\"\n" +
                "})");

        assertTrue((boolean)js("return $('#cerebro_install').is(':checked')"));
        assertTrue((boolean)js("return $('#zeppelin_install').is(':checked')"));
        assertTrue((boolean)js("return $('#kafka-manager_install').is(':checked')"));
        assertTrue((boolean)js("return $('#kibana_install').is(':checked')"));
        assertTrue((boolean)js("return $('#spark-console_install').is(':checked')"));
        assertTrue((boolean)js("return $('#grafana_install').is(':checked')"));

        // just test a few
        assertEquals("1", js("return $('#cerebro_cpu').val()"));
        assertEquals("1G", js("return $('#cerebro_ram').val()"));

        assertEquals("1", js("return $('#kafka-manager_cpu').val()"));
        assertEquals("1G", js("return $('#kafka-manager_ram').val()"));

        assertEquals("1", js("return $('#grafana_cpu').val()"));
        assertEquals("1G", js("return $('#grafana_ram').val()"));
    }

    @Test
    public void testShowReinstallSelection() throws Exception {

        testRenderKubernetesConfig();

        js("$('#main-content').append($('<div id=\"kubernetes-services-selection-body\"></div>'))");

        js("eskimoKubernetesServicesConfig.showReinstallSelection()");

        // on means nothing, it doesn't mean checkbox is checked, but it enables to validate the rendering is OK
        assertEquals("on", js("return $('#cerebro_reinstall').val()"));
        assertEquals("on", js("return $('#zeppelin_reinstall').val()"));
        assertEquals("on", js("return $('#kafka-manager_reinstall').val()"));
        assertEquals("on", js("return $('#kibana_reinstall').val()"));
        assertEquals("on", js("return $('#spark-console_reinstall').val()"));
        assertEquals("on", js("return $('#grafana_reinstall').val()"));

    }

    @Test
    public void testShowKubernetesServicesConfig() throws Exception {

        // 1. setup not OK
        js("eskimoMain.isSetupDone = function () { return false; }");
        js("eskimoMain.showSetupNotDone = function () { window.setupNotDoneCalled = true; }");

        js("eskimoKubernetesServicesConfig.showKubernetesServicesConfig()");
        assertJavascriptEquals("true", "window.setupNotDoneCalled");

        // 2. setup OK, operation in progress
        js("eskimoMain.isSetupDone = function () { return true; }");
        js("eskimoMain.isOperationInProgress = function () { return true; }");
        js("eskimoMain.showProgressbar = function () { window.showProgressBarCalled = true; }");
        js("$.ajax = function() {}");

        js("eskimoKubernetesServicesConfig.showKubernetesServicesConfig()");
        assertJavascriptEquals("true", "window.showProgressBarCalled");

        // 3. data clear, setup
        js("eskimoMain.isOperationInProgress = function () { return false; }");
        js("$.ajax = function(object) { object.success( { 'clear': 'setup'}); }");
        js("eskimoMain.handleSetupNotCompleted = function () { window.handleSetupNotCompletedCalled = true; }");

        js("eskimoKubernetesServicesConfig.showKubernetesServicesConfig()");
        assertJavascriptEquals("true", "window.handleSetupNotCompletedCalled");

        // 4. data clear, missing
        js("window.handleSetupNotCompletedCalled = false;");
        js("window.showProgressBarCalled = false;");
        js("window.setupNotDoneCalled = false;");

        js("$.ajax = function(object) { object.success( { 'clear': 'missing'}); }");

        js("eskimoKubernetesServicesConfig.showKubernetesServicesConfig()");

        assertEquals (false, js("return $('#kibana_install').get(0).checked"));
        assertEquals (false, js("return $('#zeppelin_install').get(0).checked"));
        assertEquals (false, js("return $('#grafana_install').get(0).checked"));
        assertEquals (false, js("return $('#cerebro_install').get(0).checked"));

        // 5. all good, all services selected
        js("$.ajax = function(object) { object.success( {\n" +
                "    \"cerebro_install\": \"on\",\n" +
                        "    \"zeppelin_install\": \"on\",\n" +
                        "    \"kafka-manager_install\": \"on\",\n" +
                        "    \"kibana_install\": \"on\",\n" +
                        "    \"spark-console_install\": \"on\",\n" +
                        "    \"grafana_install\": \"on\"\n" +
                        "} ); }");

        js("eskimoKubernetesServicesConfig.showKubernetesServicesConfig()");

        assertEquals (true, js("return $('#kibana_install').get(0).checked"));
        assertEquals (true, js("return $('#zeppelin_install').get(0).checked"));
        assertEquals (true, js("return $('#grafana_install').get(0).checked"));
        assertEquals (true, js("return $('#cerebro_install').get(0).checked"));
    }

    @Test
    public void testProceedWithKubernetesInstallation() throws Exception  {

        testRenderKubernetesConfig();

        // 1. error
        js("console.error = function (error) { window.consoleError = error; };");
        js("$.ajax = function (object) { object.success ( { error: 'dGVzdEVycm9y' } );}");

        js("eskimoKubernetesServicesConfig.proceedWithKubernetesInstallation ( { }, false);");

        assertJavascriptEquals("testError", "window.consoleError");

        // 2. pass command
        js("eskimoKubernetesOperationsCommand.showCommand = function (command) {" +
                "            window.command = command;" +
                "        };");

        js("$.ajax = function (object) { object.success ( { command: 'testCommand' } );}");

        js("eskimoKubernetesServicesConfig.proceedWithKubernetesInstallation ( { }, false);");

        assertJavascriptEquals("testCommand", "window.command");
    }
}

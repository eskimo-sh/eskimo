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

import ch.niceideas.eskimo.controlers.ServicesController;
import ch.niceideas.eskimo.test.services.ServicesDefinitionTestImpl;
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

        ServicesController sc = new ServicesController();
        ServicesDefinitionTestImpl sd = new ServicesDefinitionTestImpl();
        sd.afterPropertiesSet();
        sc.setServicesDefinition(sd);

        waitForDefinition("window.eskimo");
        waitForDefinition("window.eskimo.KubernetesServicesConfig");

        // instantiate test object
        js("window.eskimoKubernetesServicesConfig = new eskimo.KubernetesServicesConfig();");
        js("eskimoKubernetesServicesConfig.eskimoMain = eskimoMain;");
        js("eskimoKubernetesServicesConfig.eskimoKubernetesServicesSelection = eskimoKubernetesServicesSelection");
        js("eskimoKubernetesServicesConfig.eskimoKubernetesOperationsCommand = eskimoKubernetesOperationsCommand");
        js("eskimoKubernetesServicesConfig.eskimoNodesConfig = eskimoNodesConfig");

        js("$.ajaxGet = function(callback) { console.log(callback); }");

        js("" +
                "$.ajaxGet = function (object) {\n" +
                "    if (object.url === 'get-kubernetes-services') {" +
                "        object.success(" + sc.getKubernetesServices() + ");\n" +
                "    } else {\n" +
                "        console.log(object); " +
                "    } \n" +
                "}");


        js("eskimoKubernetesServicesConfig.initialize();");

        waitForElementIdInDOM("reset-kubernetes-servicesconfig");

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

        JSONObject expectedConfig = new JSONObject("{\n" +
                "  \"broker_cpu\": \"1\",\n" +
                "  \"broker-manager_cpu\": \"0.3\",\n" +
                "  \"database-manager_cpu\": \"0.3\",\n" +
                "  \"user-console_install\": \"on\",\n" +
                "  \"calculator-runtime_cpu\": \"1\",\n" +
                "  \"database_cpu\": \"1\",\n" +
                "  \"database_install\": \"on\",\n" +
                "  \"database_ram\": \"1G\",\n" +
                "  \"calculator-runtime_ram\": \"800M\",\n" +
                "  \"user-console_ram\": \"1G\",\n" +
                "  \"broker_ram\": \"1G\",\n" +
                "  \"database-manager_install\": \"on\",\n" +
                "  \"database-manager_ram\": \"400M\",\n" +
                "  \"broker-manager_ram\": \"400M\",\n" +
                "  \"broker-manager_install\": \"on\",\n" +
                "  \"calculator-runtime_install\": \"on\",\n" +
                "  \"broker_install\": \"on\",\n" +
                "  \"database_deployment_strategy\": \"on\",\n" +
                "  \"broker_deployment_strategy\": \"on\",\n" +
                "  \"user-console_cpu\": \"1\"\n" +
                "}");

        JSONObject actualConfig = new JSONObject((String)js("return window.savedKubernetesConfig"));
        System.err.println (actualConfig.toString(2));
        assertTrue(expectedConfig.similar(actualConfig));
    }

    @Test
    public void testSelectAll() {

        testRenderKubernetesConfig();

        js("eskimoKubernetesServicesConfig.selectAll()");

        assertEquals (true, js("return $('#broker_install').get(0).checked"));
        assertEquals (true, js("return $('#user-console_install').get(0).checked"));
        assertEquals (true, js("return $('#calculator-runtime_install').get(0).checked"));
        assertEquals (true, js("return $('#database-manager_install').get(0).checked"));

        js("eskimoKubernetesServicesConfig.selectAll()");

        assertEquals (false, js("return $('#broker_install').get(0).checked"));
        assertEquals (false, js("return $('#user-console_install').get(0).checked"));
        assertEquals (false, js("return $('#calculator-runtime_install').get(0).checked"));
        assertEquals (false, js("return $('#database-manager_install').get(0).checked"));

        js("eskimoKubernetesServicesConfig.selectAll()");

        assertEquals (true, js("return $('#broker_install').get(0).checked"));
        assertEquals (true, js("return $('#user-console_install').get(0).checked"));
        assertEquals (true, js("return $('#calculator-runtime_install').get(0).checked"));
        assertEquals (true, js("return $('#database-manager_install').get(0).checked"));

        js("eskimoKubernetesServicesConfig.selectAll()");

        assertEquals (false, js("return $('#broker_install').get(0).checked"));
        assertEquals (false, js("return $('#user-console_install').get(0).checked"));
        assertEquals (false, js("return $('#calculator-runtime_install').get(0).checked"));
        assertEquals (false, js("return $('#database-manager_install').get(0).checked"));

    }

    @Test
    public void testRenderKubernetesConfig() {

        js("eskimoKubernetesServicesConfig.renderKubernetesConfig({\n" +
                "    \"database-manager_install\": \"on\",\n" +
                "    \"user-console_install\": \"on\",\n" +
                "    \"broker_install\": \"on\",\n" +
                "    \"broker-manager_install\": \"on\",\n" +
                "    \"database_install\": \"on\",\n" +
                "    \"calculator-runtime_install\": \"on\",\n" +
                "})");

        assertTrue((boolean)js("return $('#database-manager_install').is(':checked')"));
        assertTrue((boolean)js("return $('#user-console_install').is(':checked')"));
        assertTrue((boolean)js("return $('#broker_install').is(':checked')"));
        assertTrue((boolean)js("return $('#broker-manager_install').is(':checked')"));
        assertTrue((boolean)js("return $('#database_install').is(':checked')"));
        assertTrue((boolean)js("return $('#calculator-runtime_install').is(':checked')"));

        assertTrue((boolean)js("return $('#broker_deployment_strategy').is(':checked')"));
        assertTrue((boolean)js("return $('#database_deployment_strategy').is(':checked')"));

        // just test a few
        assertEquals("0.3", js("return $('#database-manager_cpu').val()"));
        assertEquals("400M", js("return $('#database-manager_ram').val()"));

        assertEquals("0.3", js("return $('#broker-manager_cpu').val()"));
        assertEquals("400M", js("return $('#broker-manager_ram').val()"));
    }

    @Test
    public void testShowReinstallSelection() {

        testRenderKubernetesConfig();

        js("$('#main-content').append($('<div id=\"kubernetes-services-selection-body\"></div>'))");

        js("eskimoKubernetesServicesConfig.showReinstallSelection()");

        // on means nothing, it doesn't mean checkbox is checked, but it enables to validate the rendering is OK
        assertEquals("on", js("return $('#database-manager_reinstall').val()"));
        assertEquals("on", js("return $('#user-console_reinstall').val()"));
        assertEquals("on", js("return $('#broker-manager_reinstall').val()"));
        assertEquals("on", js("return $('#broker_reinstall').val()"));
        assertEquals("on", js("return $('#calculator-runtime_reinstall').val()"));

    }

    @Test
    public void testShowKubernetesServicesConfig() {

        // 1. setup not OK
        js("eskimoMain.isSetupDone = function () { return false; }");
        js("eskimoMain.showSetupNotDone = function () { window.setupNotDoneCalled = true; }");

        js("eskimoKubernetesServicesConfig.showKubernetesServicesConfig()");
        assertJavascriptEquals("true", "window.setupNotDoneCalled");

        // 2. setup OK, operation in progress
        js("eskimoMain.isSetupDone = function () { return true; }");
        js("eskimoMain.isOperationInProgress = function () { return true; }");
        js("eskimoMain.showProgressbar = function () { window.showProgressBarCalled = true; }");
        js("$.ajaxGet = function() {}");

        js("eskimoKubernetesServicesConfig.showKubernetesServicesConfig()");
        assertJavascriptEquals("true", "window.showProgressBarCalled");

        // 3. data clear, setup
        js("eskimoMain.isOperationInProgress = function () { return false; }");
        js("$.ajaxGet = function(object) { object.success( { 'clear': 'setup'}); }");
        js("eskimoMain.handleSetupNotCompleted = function () { window.handleSetupNotCompletedCalled = true; }");

        js("eskimoKubernetesServicesConfig.showKubernetesServicesConfig()");
        assertJavascriptEquals("true", "window.handleSetupNotCompletedCalled");

        // 4. data clear, missing
        js("window.handleSetupNotCompletedCalled = false;");
        js("window.showProgressBarCalled = false;");
        js("window.setupNotDoneCalled = false;");

        js("$.ajaxGet = function(object) { object.success( { 'clear': 'missing'}); }");

        js("eskimoKubernetesServicesConfig.showKubernetesServicesConfig()");

        assertEquals (false, js("return $('#broker_install').get(0).checked"));
        assertEquals (false, js("return $('#calculator-runtime_install').get(0).checked"));
        assertEquals (false, js("return $('#broker-manager_install').get(0).checked"));
        assertEquals (false, js("return $('#database-manager_install').get(0).checked"));

        // 5. all good, all services selected
        js("$.ajaxGet = function(object) { object.success( {\n" +
                "    \"database-manager_install\": \"on\",\n" +
                "    \"user-console_install\": \"on\",\n" +
                "    \"broker-manager_install\": \"on\",\n" +
                "    \"broker_install\": \"on\",\n" +
                "    \"calculator-runtime_install\": \"on\",\n" +
                "} ); }");

        js("eskimoKubernetesServicesConfig.showKubernetesServicesConfig()");

        assertEquals (true, js("return $('#broker_install').get(0).checked"));
        assertEquals (true, js("return $('#user-console_install').get(0).checked"));
        assertEquals (true, js("return $('#broker-manager_install').get(0).checked"));
        assertEquals (true, js("return $('#database-manager_install').get(0).checked"));
    }

    @Test
    public void testProceedWithKubernetesInstallation() {

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

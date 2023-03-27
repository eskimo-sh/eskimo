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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EskimoMainTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript("eskimoApp.js");
        loadScript("eskimoMain.js");
        loadScript("eskimoUtils.js");

        waitForDefinition("window.eskimo");
        waitForDefinition("window.eskimo.Main");

        js("window.eskimoFlavour = \"CE\";");

        // redefine constructor
        js("eskimo.Alert = function(){ this.initialize = function(){};  };");
        js("eskimo.Menu = function(){ " +
                "    this.initialize = function(){};" +
                "    this.adaptMenuToUserRole = function(){};" +
                "    this.setActiveMenuEntry = function(){};" +
                "};");
        js("eskimo.Setup = function(){ this.initialize = function(){};  };");
        js("eskimo.NodesConfig = function(){ this.initialize = function(){};  };");
        js("eskimo.Notifications = function(){ this.initialize = function(){}; };");
        js("eskimo.Messaging = function(){" +
                "    this.setOperationInProgress = function() {" +
                "    };" +
                "    this.startOperationInProgress = function() {" +
                "    };" +
                "    this.initialize = function(){}; " +
                "    this.stopOperationInProgress = function(success, callback) {" +
                "        if (callback != null) {" +
                "            callback();" +
                "        }" +
                "    };" +
                "};");
        js("eskimo.Operations = function(){" +
                "    this.showOperations = function() {};" +
                "    this.setOperationInProgress = function() {" +
                "    };" +
                "    this.startOperationInProgress = function() {" +
                "    };" +
                "    this.initialize = function(){}; " +
                "    this.stopOperationInProgress = function(success, callback) {" +
                "        if (callback != null) {" +
                "            callback();" +
                "        }" +
                "    };" +
                "};");
        js("eskimo.SystemStatus = function(){ " +
                "    this.initialize = function(){}; " +
                "    this.scheduleInitializeStatusTableMenus = function() {};" +
                "};");
        js("eskimo.Consoles = function(){ this.initialize = function(){}; };");
        js("eskimo.Services = function(){" +
                "   this.handleServiceHiding = function() {};" +
                "   this.initialize =  function() {}; " +
                "};");
        js("eskimo.ServicesSelection = function(){ this.initialize = function(){}; };");
        js("eskimo.ServicesSettings = function(){ this.initialize = function(){}; };");
        js("eskimo.KubernetesServicesConfig = function(){ this.initialize = function(){}; };");
        js("eskimo.KubernetesServicesSelection = function(){ this.initialize = function(){}; };");
        js("eskimo.OperationsCommand = function(){ this.initialize = function(){} ; };");
        js("eskimo.KubernetesOperationsCommand = function(){ this.initialize = function(){} ;};");
        js("eskimo.SettingsOperationsCommand = function(){ this.initialize = function(){} ;};");
        js("eskimo.SetupCommand = function(){ this.initialize = function(){} ;};");
        js("eskimo.FileManagers = function(){ this.initialize = function(){} ;};");
        js("eskimo.Setup = function(){ this.initialize = function(){} ;};");
        js("eskimo.About = function(){ this.initialize = function(){}; };");
        js("eskimo.EditUser = function(){ this.initialize = function(){}; };");

        // Don0t let jquery load real eskimoMain
        js("$.fn.ready = function () {};");

        js("$.ajaxGetSaved = $.ajaxGet");
        js("$.ajaxGet = function(callback) { console.log(callback); }");

        // instantiate test object
        js("eskimoMain = new eskimo.Main();");
        js("eskimoMain.doInitializeInternal();");
    }

    @Test
    public void testShowOnlyContent() {

        js("eskimoMain.showOnlyContent('operations')");

        assertCssEquals("hidden", "#inner-content-status", "visibility");

        assertCssEquals("hidden", "#inner-content-consoles", "visibility");
        assertCssEquals("hidden", "#inner-content-setup", "visibility");
        assertCssEquals("hidden", "#inner-content-nodes-config", "visibility");
        assertCssEquals("hidden", "#inner-content-services-settings", "visibility");
        assertCssEquals("hidden", "#inner-content-file-managers", "visibility");

        assertCssEquals("visible", "#inner-content-operations", "visibility");

        assertJavascriptEquals("true", "eskimoMain.isCurrentDisplayedScreen('operations')");

        js("eskimoMain.showOnlyContent('kibana', true)");

        assertCssEquals("hidden", "#inner-content-operations", "visibility");

    }

    @Test
    public void testShowHideProgressBar() throws Exception {

        js("$('#hoeapp-wrapper').load('html/eskimoMain.html');");

        waitForElementIdInDOM("inner-content-progress");

        js("$('#main-content').css('visibility', 'visible');");
        js("$('#hoeapp-container').css('visibility', 'visible');");

        js("eskimoMain.showProgressbar()");

        assertEquals("visible", js("return $('.inner-content-show').css('visibility')"));

        js("eskimoMain.hideProgressbar()");

        assertEquals("hidden", js("return $('.inner-content-show').css('visibility')"));
    }

    @Test
    public void testStartStopOperationInprogress() {

        assertEquals(false, js("return eskimoMain.isOperationInProgress()"));
        assertEquals(false, js("return eskimoMain.isOperationInProgressRecovery()"));

        js("eskimoMain.startOperationInProgress();");

        assertEquals(true, js("return eskimoMain.isOperationInProgress()"));
        assertEquals(false, js("return eskimoMain.isOperationInProgressRecovery()"));

        js("eskimoMain.scheduleStopOperationInProgress();");

        assertEquals(false, js("return eskimoMain.isOperationInProgress()"));
        assertEquals(false, js("return eskimoMain.isOperationInProgressRecovery()"));
    }

    @Test
    public void testRecoverOperationInprogress() {

        assertEquals(false, js("return eskimoMain.isOperationInProgress()"));
        assertEquals(false, js("return eskimoMain.isOperationInProgressRecovery()"));

        js("eskimoMain.recoverOperationInProgress();");

        assertEquals(true, js("return eskimoMain.isOperationInProgress()"));
        assertEquals(true, js("return eskimoMain.isOperationInProgressRecovery()"));

        js("eskimoMain.scheduleStopOperationInProgress();");

        assertEquals(false, js("return eskimoMain.isOperationInProgress()"));
        assertEquals(false, js("return eskimoMain.isOperationInProgressRecovery()"));
    }

    @Test
    public void testFetchContext() {

        js("$.ajaxGet = $.ajaxGetSaved");

        js("$.ajax = function (callback) { callback.success ( {\n" +
                "    \"version\": \"0.4-SNAPSHOT\",\n" +
                "    \"roles\": [\"ADMIN\"],\n" +
                "    \"status\": \"OK\"\n" +
                "} ); }");

        js("eskimoMain.fetchContext();");

        assertJavascriptEquals("0.4-SNAPSHOT", "$('#eskimo-version').html()");
    }

    @Test
    public void testHaRole() {
        testFetchContext();

        assertJavascriptEquals("true", "eskimoMain.hasRole('*')");
        assertJavascriptEquals("false", "eskimoMain.hasRole('DUMMY')");
        assertJavascriptEquals("true", "eskimoMain.hasRole('ADMIN')");
    }

}

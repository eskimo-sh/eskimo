/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

        loadScript(page, "hoe.js");
        loadScript(page, "eskimoMain.js");
        loadScript(page, "eskimoUtils.js");

        // redefine constructor
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
        js("eskimo.SystemStatus = function(){ this.initialize = function(){}; };");
        js("eskimo.Consoles = function(){ this.initialize = function(){}; };");
        js("eskimo.Services = function(){" +
                "   this.handleServiceHiding = function() {};" +
                "   this.initialize =  function() {}; " +
                "};");
        js("eskimo.ServicesSelection = function(){ this.initialize = function(){}; };");
        js("eskimo.ServicesSettings = function(){ this.initialize = function(){}; };");
        js("eskimo.MarathonServicesConfig = function(){ this.initialize = function(){}; };");
        js("eskimo.MarathonServicesSelection = function(){ this.initialize = function(){}; };");
        js("eskimo.OperationsCommand = function(){ this.initialize = function(){} ; };");
        js("eskimo.MarathonOperationsCommand = function(){ this.initialize = function(){} ;};");
        js("eskimo.SettingsOperationsCommand = function(){ this.initialize = function(){} ;};");
        js("eskimo.SetupCommand = function(){ this.initialize = function(){} ;};");
        js("eskimo.FileManagers = function(){ this.initialize = function(){} ;};");
        js("eskimo.Setup = function(){ this.initialize = function(){} ;};");
        js("eskimo.About = function(){ this.initialize = function(){}; };");
        js("eskimo.Operations = function(){ this.initialize = function(){}; };");

        // Don0t let jquery load real eskimoMain
        js("$.fn.ready = function () {};");

        // instantiate test object
        js("eskimoMain = new eskimo.Main();");
        js("eskimoMain.doInitializeInternal();");
    }

    @Test
    public void testShowOnlyContent() throws Exception {

        js("eskimoMain.showOnlyContent('pending')");

        assertCssValue("#inner-content-status", "visibility", "hidden");

        assertCssValue("#inner-content-consoles", "visibility", "hidden");
        assertCssValue("#inner-content-setup", "visibility", "hidden");
        assertCssValue("#inner-content-nodes", "visibility", "hidden");
        assertCssValue("#inner-content-services-settings", "visibility", "hidden");
        assertCssValue("#inner-content-file-managers", "visibility", "hidden");

        assertCssValue("#inner-content-pending", "visibility", "visible");

        assertJavascriptEquals("true", "eskimoMain.isCurrentDisplayedService('pending')");
    }

    @Test
    public void testShowHideProgressBar() throws Exception {

        js("$('#hoeapp-wrapper').load('html/eskimoMain.html');");

        waitForElementIdInDOM("inner-content-progress");

        js("$('#main-content').css('visibility', 'visible');");
        js("$('#hoeapp-container').css('visibility', 'visible');");

        js("eskimoMain.showProgressbar()");

        assertEquals ("visible", js("$('.inner-content-show').css('visibility')").getJavaScriptResult());

        js("eskimoMain.hideProgressbar()");

        assertEquals ("hidden", js("$('.inner-content-show').css('visibility')").getJavaScriptResult());
    }

    @Test
    public void testStartStopOperationInprogress() throws Exception {

        assertEquals(false, js("eskimoMain.isOperationInProgress()").getJavaScriptResult());

        js("eskimoMain.startOperationInProgress();");

        assertEquals(true, js("eskimoMain.isOperationInProgress()").getJavaScriptResult());

        js("eskimoMain.scheduleStopOperationInProgress();");

        assertEquals(false, js("eskimoMain.isOperationInProgress()").getJavaScriptResult());
    }

    @Test
    public void testHandleSetupCompletedAndNotCompleted() throws Exception {

        js("$('#hoeapp-wrapper').load('html/eskimoMain.html');");

        waitForElementIdInDOM("menu-container");

        js("eskimoMain.handleSetupCompleted()");

        js("var allDisabled = true;");
        js("var allEnabled = true;");

        js("" +
                " $('.config-menu-items').each(function() {\n" +
                "     if ($(this).attr('class') == 'config-menu-items') {\n" +
                "         allDisabled=false;\n"+
                "     }\n" +
                "     if ($(this).attr('class') == 'config-menu-items disabled') {\n" +
                "         allEnabled=false;\n"+
                "     }\n" +
                "});");

        assertJavascriptEquals("false", "allDisabled");
        assertJavascriptEquals("true", "allEnabled");

        js("eskimoMain.handleSetupNotCompleted()");

        js("allEnabled = true;");

        js("" +
                " $('.config-menu-items').each(function() {\n" +
                "     if ($(this).attr('class') == 'config-menu-items disabled') {\n" +
                "         allEnabled=false;\n"+
                "     }\n" +
                "});");

        assertJavascriptEquals("false", "allEnabled");
    }

}

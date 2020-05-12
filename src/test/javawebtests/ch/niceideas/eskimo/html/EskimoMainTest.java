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

import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;

public class EskimoMainTest extends AbstractWebTest {

    @Before
    public void setUp() throws Exception {

        loadScript(page, "hoe.js");
        loadScript(page, "eskimoMain.js");
        loadScript(page, "eskimoUtils.js");

        // redefine constructor
        page.executeJavaScript("eskimo.Setup = function(){};");
        page.executeJavaScript("eskimo.NodesConfig = function(){};");
        page.executeJavaScript("eskimo.Notifications = function(){};");
        page.executeJavaScript("eskimo.Messaging = function(){" +
                "    this.setOperationInProgress = function() {" +
                "    };" +
                "    this.startOperationInProgress = function() {" +
                "    };" +
                "    this.stopOperationInProgress = function(success, callback) {" +
                "        if (callback != null) {" +
                "            callback();" +
                "        }" +
                "    };" +
                "};");
        page.executeJavaScript("eskimo.SystemStatus = function(){};");
        page.executeJavaScript("eskimo.Consoles = function(){};");
        page.executeJavaScript("eskimo.Services = function(){" +
                "   this.handleServiceHiding = function() {}" +
                "};");
        page.executeJavaScript("eskimo.ServicesSelection = function(){};");
        page.executeJavaScript("eskimo.ServicesConfig = function(){};");
        page.executeJavaScript("eskimo.MarathonServicesConfig = function(){};");
        page.executeJavaScript("eskimo.MarathonServicesSelection = function(){};");
        page.executeJavaScript("eskimo.OperationsCommand = function(){};");
        page.executeJavaScript("eskimo.MarathonOperationsCommand = function(){};");
        page.executeJavaScript("eskimo.SetupCommand = function(){};");
        page.executeJavaScript("eskimo.FileManagers = function(){};");
        page.executeJavaScript("eskimo.Setup = function(){};");
        page.executeJavaScript("eskimo.About = function(){};");

        // Don0t let jquery load real eskimoMain
        page.executeJavaScript("$.fn.ready = function () {};");

        // instantiate test object
        page.executeJavaScript("eskimoMain = new eskimo.Main();");
        page.executeJavaScript("eskimoMain.doInitializeInternal();");
    }

    @Test
    public void testShowOnlyContent() throws Exception {

        page.executeJavaScript("eskimoMain.showOnlyContent('pending')");

        assertCssValue("#inner-content-status", "visibility", "hidden");

        assertCssValue("#inner-content-consoles", "visibility", "hidden");
        assertCssValue("#inner-content-setup", "visibility", "hidden");
        assertCssValue("#inner-content-nodes", "visibility", "hidden");
        assertCssValue("#inner-content-services-config", "visibility", "hidden");
        assertCssValue("#inner-content-file-managers", "visibility", "hidden");

        assertCssValue("#inner-content-pending", "visibility", "visible");

        assertJavascriptEquals("true", "eskimoMain.isCurrentDisplayedService('pending')");
    }

    @Test
    public void testShowHideProgressBar() throws Exception {

        page.executeJavaScript("$('#hoeapp-wrapper').load('html/eskimoMain.html');");

        waitForElementIdInDOM("inner-content-progress");

        page.executeJavaScript("$('#main-content').css('visibility', 'visible');");
        page.executeJavaScript("$('#hoeapp-container').css('visibility', 'visible');");

        page.executeJavaScript("eskimoMain.showProgressbar()");

        assertEquals ("visible", page.executeJavaScript("$('.inner-content-show').css('visibility')").getJavaScriptResult());

        page.executeJavaScript("eskimoMain.hideProgressbar()");

        assertEquals ("hidden", page.executeJavaScript("$('.inner-content-show').css('visibility')").getJavaScriptResult());
    }

    @Test
    public void testStartStopOperationInprogress() throws Exception {

        assertEquals(false, page.executeJavaScript("eskimoMain.isOperationInProgress()").getJavaScriptResult());

        page.executeJavaScript("eskimoMain.startOperationInProgress();");

        assertEquals(true, page.executeJavaScript("eskimoMain.isOperationInProgress()").getJavaScriptResult());

        page.executeJavaScript("eskimoMain.scheduleStopOperationInProgress();");

        assertEquals(false, page.executeJavaScript("eskimoMain.isOperationInProgress()").getJavaScriptResult());
    }

    @Test
    public void testHandleSetupCompletedAndNotCompleted() throws Exception {

        page.executeJavaScript("$('#hoeapp-wrapper').load('html/eskimoMain.html');");

        waitForElementIdInDOM("menu-container");

        page.executeJavaScript("eskimoMain.handleSetupCompleted()");

        page.executeJavaScript("var allDisabled = true;");
        page.executeJavaScript("var allEnabled = true;");

        page.executeJavaScript("" +
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

        page.executeJavaScript("eskimoMain.handleSetupNotCompleted()");

        page.executeJavaScript("allEnabled = true;");

        page.executeJavaScript("" +
                " $('.config-menu-items').each(function() {\n" +
                "     if ($(this).attr('class') == 'config-menu-items disabled') {\n" +
                "         allEnabled=false;\n"+
                "     }\n" +
                "});");

        assertJavascriptEquals("false", "allEnabled");
    }

}

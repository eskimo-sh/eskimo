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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class EskimoMenuTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript("app.js");
        loadScript("eskimoMenu.js");
        loadScript("eskimoUtils.js");

        js("window.eskimoFlavour = \"CE\";");

        // redefine constructor
        js("eskimo.Setup = function(){ this.initialize = function(){};  };");
        js("eskimo.NodesConfig = function(){ this.initialize = function(){};  };");
        js("eskimo.SystemStatus = function(){ this.initialize = function(){};  };");
        js("eskimo.Consoles = function(){ this.initialize = function(){}; };");
        js("eskimo.Services = function(){" +
                "   this.handleServiceHiding = function() {};" +
                "   this.initialize =  function() {}; " +
                "};");
        js("eskimo.ServicesSelection = function(){ this.initialize = function(){}; };");
        js("eskimo.ServicesSettings = function(){ this.initialize = function(){}; };");
        js("eskimo.KubernetesServicesConfig = function(){ this.initialize = function(){}; };");
        js("eskimo.KubernetesServicesSelection = function(){ this.initialize = function(){}; };");
        js("eskimo.FileManagers = function(){ this.initialize = function(){} ;};");
        js("eskimo.Setup = function(){ this.initialize = function(){} ;};");
        js("eskimo.About = function(){ this.initialize = function(){}; };");
        js("eskimo.EditUser = function(){ this.initialize = function(){}; };");

        // Don0t let jquery load real eskimoMain
        js("$.fn.ready = function () {};");

        js("$.ajaxGetSaved = $.ajaxGet");
        js("$.ajaxGet = function(callback) { console.log(callback); }");

        js("window.UI_SERVICES = [\"cerebro\", \"kibana\", \"spark-console\", \"zeppelin\"];");

        js("window.UI_SERVICES_CONFIG = {" +
                "\"cerebro\" : {'urlTemplate': './cerebro/{NODE_ADDRESS}:9999/cerebro', 'title' : 'cerebro', 'waitTime': 10 }, " +
                "\"kibana\" : {'urlTemplate': './kibana/{NODE_ADDRESS}:9999/kibana', 'title' : 'kibana', 'waitTime': 15 }, " +
                "\"spark-console\" : {'urlTemplate': './spark-histo/{NODE_ADDRESS}:9999/spark-histo', 'title' : 'spark-histo', 'waitTime': 25 }, " +
                "\"zeppelin\" : {'urlTemplate': './zeppelin/{NODE_ADDRESS}:9999/zeppelin', 'title' : 'zeppelin', 'waitTime': 30 }" +
                "};");

        // instantiate test object
        js("eskimoMenu = new eskimo.Menu();");
        js("eskimoMenu.initialize();");

        js("eskimoMenu.eskimoNodesConfig = eskimoNodesConfig;");

        waitForElementIdInDOM("main-menu-show-operations-link");
    }

    @Test
    public void testCreateServicesMenu() throws Exception {

        // add services menu
        js("eskimoMenu.createServicesMenu(UI_SERVICES, UI_SERVICES_CONFIG)");

        // make sure they're created
        assertJavascriptEquals ("1", "$('#folderMenuCerebro').length");
        assertJavascriptEquals ("1", "$('#folderMenuKibana').length");
        assertJavascriptEquals ("1", "$('#folderMenuSparkConsole').length");
        assertJavascriptEquals ("1", "$('#folderMenuZeppelin').length");

        // test zeppelin menu entry
        assertJavascriptEquals ("zeppelin-icon.png", "$('#folderMenuZeppelin').find('a > i > img').attr('src')");
        assertJavascriptEquals ("zeppelin", "$('#folderMenuZeppelin').find('a > span').html()");
    }

    @Test
    public void testMenuHidingNonAdmin() throws Exception {

        js("eskimoMain.hasRole = function(role) { return false; }");

        assertJavascriptEquals("list-item", "$('#folderMenuConsoles').css('display')");
        assertJavascriptEquals("list-item", "$('#menu-configure-setup').css('display')");

        js("eskimoMenu.adaptMenuToUserRole()");

        assertJavascriptEquals("none", "$('#folderMenuConsoles').css('display')");
        assertJavascriptEquals("none", "$('#menu-configure-setup').css('display')");
    }

    @Test
    public void testHandleSetupCompletedAndNotCompleted() throws Exception {

        js("eskimoMenu.handleSetupCompleted()");

        js("window.allDisabled = true;");
        js("window.allEnabled = true;");

        js("" +
                " $('.side-nav-item').each(function() {\n" +
                "     if ($(this).attr('class') == 'side-nav-item') {\n" +
                "         allDisabled=false;\n" +
                "     }\n" +
                "     if ($(this).attr('class') == 'side-nav-item disabled') {\n" +
                "         allEnabled=false;\n" +
                "     }\n" +
                "});");

        //Thread.sleep(100000);

        assertJavascriptEquals("false", "allDisabled");
        assertJavascriptEquals("true", "allEnabled");

        js("eskimoMenu.handleSetupNotCompleted()");

        js("allEnabled = true;");

        js("" +
                " $('.side-nav-item').each(function() {\n" +
                "     if ($(this).attr('class') == 'side-nav-item disabled') {\n" +
                "         allEnabled=false;\n" +
                "     }\n" +
                "});");

        assertJavascriptEquals("false", "allEnabled");
    }

    @Test
    public void testOthers() throws Exception {
        fail ("To Be Implemented");
    }


}

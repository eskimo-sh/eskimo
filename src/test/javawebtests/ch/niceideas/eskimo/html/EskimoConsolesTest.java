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

import static org.junit.Assert.assertEquals;

public class EskimoConsolesTest extends AbstractWebTest {

    @Before
    public void setUp() throws Exception {

        page.executeJavaScript("loadScript('../../src/main/webapp/scripts/jquery-3.3.1.js')");
        page.executeJavaScript("loadScript('../../src/main/webapp/scripts/eskimoConsoles.js')");

        page.executeJavaScript("$('#inner-content-consoles').html('" +
                "<div id=\"consoles-management\"" +
                "     class=\"panel theme-panel inner-content-inner \">" +
                "    <div class=\"col-xs-12\">" +
                "        <div class=\"col-md-12\" id=\"consoles-title\">" +
                "        </div>" +
                "    </div>" +
                "    <div class=\"col-xs-12\" style=\"padding-bottom: 5px; margin-bottom: 5px; border-bottom: 1px solid #AAAAAA;\">" +
                "        <div class=\"col-md-12\" id=\"consoles-action\">" +
                "            <div class=\"btn-group\">" +
                "                <button type=\"button\" class=\"btn btn-default dropdown-toggle\" data-toggle=\"dropdown\" aria-haspopup=\"true\" aria-expanded=\"false\">" +
                "                    Open Console <span class=\"caret\"></span>" +
                "                </button>" +
                "                <ul id=\"consoles-action-open-console\" class=\"dropdown-menu\">" +
                "                </ul>" +
                "            </div>" +
                "        </div>" +
                "    </div>" +
                "    <div class=\"col-xs-12\">" +
                "        <div class=\"col-md-12\" id=\"consoles-consoles\">" +
                "            <ul id=\"consoles-tab-list\" class=\"nav nav-tabs\"> </ul>" +
                "        </div>" +
                "    </div>" +
                "    <div id=\"consoles-console-content\" class=\"col-xs-12\">" +
                "    </div>" +
                "</div>')");


        // redefine constructor
        page.executeJavaScript("eskimo.Consoles.initialize = function() {};");

        // mock ajax term
        page.executeJavaScript("ajaxterm = {};");
        page.executeJavaScript("ajaxterm.Terminal = function() {\n" +
                "this.setShowNextTab = function() {};\n" +
                "this.setShowPrevTab = function() {};\n" +
                "}\n");

        // instantiate test object
        page.executeJavaScript("eskimoConsoles = new eskimo.Consoles();");

        page.executeJavaScript("var openedConsoles = [];");
        page.executeJavaScript("eskimoConsoles.setOpenedConsoles(openedConsoles);");

        // set services for tests
        page.executeJavaScript("eskimoConsoles.setAvailableNodes (" +
                "[{\"nbr\": 1, \"nodeName\": \"192-168-10-11\", \"nodeAddress\": \"192.168.10.11\"}, " +
                " {\"nbr\": 2, \"nodeName\": \"192-168-10-13\", \"nodeAddress\": \"192.168.10.13\"} ] );");

    }

    @Test
    public void testNominal() throws Exception {
        page.executeJavaScript("eskimoConsoles.openConsole('192.168.10.11', '192-168-10-11')");

        // Honestly if this ends up without an error, we're good
    }

    @Test
    public void testShowPrevTab() throws Exception {
        page.executeJavaScript("eskimoConsoles.openConsole('192.168.10.11', '192-168-10-11')");
        page.executeJavaScript("eskimoConsoles.openConsole('192.168.10.13', '192-168-10-13')");

        assertEquals ("hidden", page.executeJavaScript("$('#consoles-console-192-168-10-11').css('visibility')").getJavaScriptResult().toString());
        assertEquals ("none", page.executeJavaScript("$('#consoles-console-192-168-10-11').css('display')").getJavaScriptResult().toString());

        assertEquals ("inherit", page.executeJavaScript("$('#consoles-console-192-168-10-13').css('visibility')").getJavaScriptResult().toString());
        assertEquals ("inherit", page.executeJavaScript("$('#consoles-console-192-168-10-13').css('display')").getJavaScriptResult().toString());

        page.executeJavaScript("eskimoConsoles.showPrevTab()");

        assertEquals ("inherit", page.executeJavaScript("$('#consoles-console-192-168-10-11').css('visibility')").getJavaScriptResult().toString());
        assertEquals ("inherit", page.executeJavaScript("$('#consoles-console-192-168-10-11').css('display')").getJavaScriptResult().toString());

        assertEquals ("hidden", page.executeJavaScript("$('#consoles-console-192-168-10-13').css('visibility')").getJavaScriptResult().toString());
        assertEquals ("none", page.executeJavaScript("$('#consoles-console-192-168-10-13').css('display')").getJavaScriptResult().toString());
    }

    @Test
    public void testShowNextTab() throws Exception {

        // reinitiate the situation as testShowPrevTab
        testShowPrevTab();

        page.executeJavaScript("eskimoConsoles.showNextTab()");

        // situation is now inverse
        assertEquals ("hidden", page.executeJavaScript("$('#consoles-console-192-168-10-11').css('visibility')").getJavaScriptResult().toString());
        assertEquals ("none", page.executeJavaScript("$('#consoles-console-192-168-10-11').css('display')").getJavaScriptResult().toString());

        assertEquals ("inherit", page.executeJavaScript("$('#consoles-console-192-168-10-13').css('visibility')").getJavaScriptResult().toString());
        assertEquals ("inherit", page.executeJavaScript("$('#consoles-console-192-168-10-13').css('display')").getJavaScriptResult().toString());

    }

}

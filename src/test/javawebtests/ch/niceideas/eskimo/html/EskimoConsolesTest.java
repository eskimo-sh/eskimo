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

import static org.junit.jupiter.api.Assertions.*;

public class EskimoConsolesTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript ("eskimoUtils.js");
        loadScript ("eskimoConsoles.js");

        waitForDefinition("window.eskimo");
        waitForDefinition("window.eskimo.Consoles");

        js("$.ajaxGet = function(object) { object.success( { 'message': 'OK'}); }");

        // mock ajax term
        js("ajaxterm = {};");
        js("ajaxterm.Terminal = function(id, options) {\n" +
                "this.setShowNextTab = function() {};\n" +
                "this.setShowPrevTab = function() {};\n" +
                "this.getSessionId = function() { return 123;};\n" +
                "this.close = function () {if (!window.terminalCloseCalled) { window.terminalCloseCalled = []; }; window.terminalCloseCalled.push(id); };\n" +
                "}\n");

        // instantiate test object
        js("eskimoConsoles = new eskimo.Consoles()");
        js("eskimoConsoles.eskimoMain = {" +
                "       isSetupDone: function() {return true; }," +
                "       showOnlyContent: function() {}," +
                "       hideProgressbar: function() {}" +
                "   }");
        js("eskimoConsoles.initialize()");

        waitForElementIdInDOM("consoles-console-content");

        js("window.openedConsoles = [];");
        js("eskimoConsoles.setOpenedConsoles(openedConsoles);");

        // set services for tests
        js("eskimoConsoles.setAvailableNodes (" +
                "[{\"nbr\": 1, \"nodeName\": \"192-168-10-11\", \"nodeAddress\": \"192.168.10.11\"}, " +
                " {\"nbr\": 2, \"nodeName\": \"192-168-10-13\", \"nodeAddress\": \"192.168.10.13\"} ] );");

        js("$('#inner-content-consoles').css('display', 'inherit')");
        js("$('#inner-content-consoles').css('visibility', 'visible')");
    }

    @Test
    public void testNominal() {

        try {
            js("eskimoConsoles.openConsole('192.168.10.11', '192-168-10-11')");

            // Honestly if this ends up without an error, we're good
        } catch (Throwable e) {
            fail ("No error expected ");
        }
    }

    @Test
    public void testNodeVanish() {

        testClickOpenConsle();

        // node 192-168-10-13 vanishes !
        js("eskimoConsoles.setAvailableNodes (" +
                "[{\"nbr\": 1, \"nodeName\": \"192-168-10-11\", \"nodeAddress\": \"192.168.10.11\"} ] );");

        // ensure console was disabled
        //System.err.println (page.asXml());

        assertJavascriptEquals ("[\"term_192-168-10-13\"]", "JSON.stringify (window.terminalCloseCalled)");

        assertJavascriptEquals("\n" +
                "    <div id=\"term_192-168-10-11\" class=\"ajaxterm\" tabindex=\"0\"></div>\n" +
                "    <div id=\"console-actions-192-168-10-11\">\n" +
                "        <button id=\"console-close-192-168-10-11\" name=\"console-close-1\" class=\"btn btn-secondary\">\n" +
                "            Close\n" +
                "        </button>\n" +
                "    </div>\n", "$('#consoles-console-192-168-10-11').html()");
    }

    @Test
    public void testCloseConsole() {

        testClickOpenConsle();

        js("$.ajaxGet = function (dataObj) {" +
                "    window.ajaxGetUrl = dataObj.url;" +
                "    dataObj.success({" +
                "        'message' : 'OK'" +
                "    });" +
                "}");

        js("eskimoConsoles.closeConsole('192-168-10-13')");

        assertJavascriptEquals ("[\"term_192-168-10-13\"]", "JSON.stringify (window.terminalCloseCalled)");

        assertJavascriptEquals("\n" +
                "    <div id=\"term_192-168-10-11\" class=\"ajaxterm\" tabindex=\"0\"></div>\n" +
                "    <div id=\"console-actions-192-168-10-11\">\n" +
                "        <button id=\"console-close-192-168-10-11\" name=\"console-close-1\" class=\"btn btn-secondary\">\n" +
                "            Close\n" +
                "        </button>\n" +
                "    </div>\n", "$('#consoles-console-192-168-10-11').html()");

        assertJavascriptEquals("terminal-remove?session=123", "window.ajaxGetUrl");
    }

    @Test
    public void testShowConsoles() {
        js("eskimoConsoles.showConsoles()");

        assertCssEquals("visible", "#inner-content-consoles", "visibility");
        assertCssEquals("block", "#inner-content-consoles", "display");

        assertNotNull (getElementById("console_open_192-168-10-11"));
        assertEquals ("192.168.10.11", getElementById("console_open_192-168-10-11").getText());

        assertNotNull (getElementById("console_open_192-168-10-13"));
        assertEquals ("192.168.10.13", getElementById("console_open_192-168-10-13").getText());
    }

    @Test
    public void testClickOpenConsle() {

        testShowConsoles();

        getElementById("console_open_192-168-10-13").click();


        assertCssEquals("visible", "#consoles-console-192-168-10-13", "visibility");
        assertCssEquals("block", "#consoles-console-192-168-10-13", "display");

        getElementById("console_open_192-168-10-11").click();

        assertCssEquals("visible", "#consoles-console-192-168-10-11", "visibility");
        assertCssEquals("block", "#consoles-console-192-168-10-11", "display");

        assertCssEquals("hidden", "#consoles-console-192-168-10-13", "visibility");
        assertCssEquals("none", "#consoles-console-192-168-10-13", "display");
    }

    @Test
    public void testShowPrevTab() {
        js("eskimoConsoles.openConsole('192.168.10.11', '192-168-10-11')");
        js("eskimoConsoles.openConsole('192.168.10.13', '192-168-10-13')");

        assertCssEquals("hidden", "#consoles-console-192-168-10-11", "visibility");
        assertCssEquals("none", "#consoles-console-192-168-10-11", "display");

        assertCssEquals("visible", "#consoles-console-192-168-10-13", "visibility");
        assertCssEquals("block", "#consoles-console-192-168-10-13", "display");

        js("eskimoConsoles.showPrevTab()");

        assertCssEquals("visible", "#consoles-console-192-168-10-11", "visibility");
        assertCssEquals("block", "#consoles-console-192-168-10-11", "display");

        assertCssEquals("hidden", "#consoles-console-192-168-10-13", "visibility");
        assertCssEquals("none", "#consoles-console-192-168-10-13", "display");
    }

    @Test
    public void testShowNextTab() {

        // reinitiate the situation as testShowPrevTab
        testShowPrevTab();

        js("eskimoConsoles.showNextTab()");

        // situation is now inverse
        assertCssEquals("hidden", "#consoles-console-192-168-10-11", "visibility");
        assertCssEquals("none", "#consoles-console-192-168-10-11", "display");

        assertCssEquals("visible", "#consoles-console-192-168-10-13", "visibility");
        assertCssEquals("block", "#consoles-console-192-168-10-13", "display");
    }

}

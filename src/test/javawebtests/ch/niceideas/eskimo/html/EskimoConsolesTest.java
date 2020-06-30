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

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;

public class EskimoConsolesTest extends AbstractWebTest {

    @Before
    public void setUp() throws Exception {

        loadScript (page, "eskimoConsoles.js");

        // mock ajax term
        page.executeJavaScript("ajaxterm = {};");
        page.executeJavaScript("ajaxterm.Terminal = function(id, options) {\n" +
                "this.setShowNextTab = function() {};\n" +
                "this.setShowPrevTab = function() {};\n" +
                "this.close = function () {if (!window.terminalCloseCalled) { window.terminalCloseCalled = []; }; window.terminalCloseCalled.push(id); };\n" +
                "}\n");

        // instantiate test object
        page.executeJavaScript("eskimoConsoles = new eskimo.Consoles()");
        page.executeJavaScript("eskimoConsoles.eskimoMain = {" +
                "       isSetupDone: function() {return true; }," +
                "       showOnlyContent: function() {}," +
                "       hideProgressbar: function() {}" +
                "   }");
        page.executeJavaScript("eskimoConsoles.initialize()");

        waitForElementIdInDOM("consoles-console-content");

        page.executeJavaScript("var openedConsoles = [];");
        page.executeJavaScript("eskimoConsoles.setOpenedConsoles(openedConsoles);");

        // set services for tests
        page.executeJavaScript("eskimoConsoles.setAvailableNodes (" +
                "[{\"nbr\": 1, \"nodeName\": \"192-168-10-11\", \"nodeAddress\": \"192.168.10.11\"}, " +
                " {\"nbr\": 2, \"nodeName\": \"192-168-10-13\", \"nodeAddress\": \"192.168.10.13\"} ] );");

        page.executeJavaScript("$('#inner-content-consoles').css('display', 'inherit')");
        page.executeJavaScript("$('#inner-content-consoles').css('visibility', 'visible')");
    }

    @Test
    public void testNominal() {

        try {
            page.executeJavaScript("eskimoConsoles.openConsole('192.168.10.11', '192-168-10-11')");

            // Honestly if this ends up without an error, we're good
        } catch (Throwable e) {
            fail ("No error expected ");
        }
    }

    @Test
    public void testNodeVanish() throws Exception {

        testClickOpenConsle();

        // node 192-168-10-13 vanishes !
        page.executeJavaScript("eskimoConsoles.setAvailableNodes (" +
                "[{\"nbr\": 1, \"nodeName\": \"192-168-10-11\", \"nodeAddress\": \"192.168.10.11\"} ] );");

        // ensure console was disabled
        //System.err.println (page.asXml());

        assertJavascriptEquals ("[\"term_192-168-10-13\"]", "JSON.stringify (window.terminalCloseCalled)");

        assertJavascriptEquals("\n" +
                "    <div id=\"term_192-168-10-11\" class=\"ajaxterm\" tabindex=\"0\"></div>\n" +
                "    <div id=\"console-actions-192-168-10-11\">\n" +
                "        <button id=\"console-close-192-168-10-11\" name=\"console-close-1\" class=\"btn btn-primary\">\n" +
                "            Close\n" +
                "        </button>\n" +
                "    </div>", "$('#consoles-console-192-168-10-11').html()");
    }

    @Test
    public void testShowConsoles() {
        page.executeJavaScript("eskimoConsoles.showConsoles()");

        assertNotNull (page.getElementById("console_open_192-168-10-11"));
        assertEquals ("192.168.10.11", page.getElementById("console_open_192-168-10-11").getTextContent());

        assertNotNull (page.getElementById("console_open_192-168-10-13"));
        assertEquals ("192.168.10.13", page.getElementById("console_open_192-168-10-13").getTextContent());
    }

    @Test
    public void testClickOpenConsle() throws Exception {

        testShowConsoles();

        page.getElementById("console_open_192-168-10-13").click();


        assertCssValue ("#consoles-console-192-168-10-13", "visibility", "inherit");
        assertCssValue ("#consoles-console-192-168-10-13", "display", "inherit");

        page.getElementById("console_open_192-168-10-11").click();

        assertCssValue ("#consoles-console-192-168-10-11", "visibility", "inherit");
        assertCssValue ("#consoles-console-192-168-10-11", "display", "inherit");

        assertCssValue ("#consoles-console-192-168-10-13", "visibility", "hidden");
        assertCssValue ("#consoles-console-192-168-10-13", "display", "none");
    }

    @Test
    public void testShowPrevTab() throws Exception {
        page.executeJavaScript("eskimoConsoles.openConsole('192.168.10.11', '192-168-10-11')");
        page.executeJavaScript("eskimoConsoles.openConsole('192.168.10.13', '192-168-10-13')");

        assertCssValue ("#consoles-console-192-168-10-11", "visibility", "hidden");
        assertCssValue ("#consoles-console-192-168-10-11", "display", "none");

        assertCssValue ("#consoles-console-192-168-10-13", "visibility", "inherit");
        assertCssValue ("#consoles-console-192-168-10-13", "display", "inherit");

        page.executeJavaScript("eskimoConsoles.showPrevTab()");

        assertCssValue ("#consoles-console-192-168-10-11", "visibility", "inherit");
        assertCssValue ("#consoles-console-192-168-10-11", "display", "inherit");

        assertCssValue ("#consoles-console-192-168-10-13", "visibility", "hidden");
        assertCssValue ("#consoles-console-192-168-10-13", "display", "none");
    }

    @Test
    public void testShowNextTab() throws Exception {

        // reinitiate the situation as testShowPrevTab
        testShowPrevTab();

        page.executeJavaScript("eskimoConsoles.showNextTab()");

        // situation is now inverse
        assertCssValue ("#consoles-console-192-168-10-11", "visibility", "hidden");
        assertCssValue ("#consoles-console-192-168-10-11", "display", "none");

        assertCssValue ("#consoles-console-192-168-10-13", "visibility", "inherit");
        assertCssValue ("#consoles-console-192-168-10-13", "display", "inherit");
    }

}

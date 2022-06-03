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

import com.gargoylesoftware.htmlunit.AjaxController;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class EskimoAjaxtermTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript (page, "ajaxterm.js");

        js("$('#main-content').html('<div id=\"test-term\"></div>')");

        js("" +
                "var t = new ajaxterm.Terminal(\"test-term\", {\n" +
                "                width: 80,\n" +
                "                height: 40,\n" +
                "                endpoint: \"./terminal?node=test\"\n" +
                "            });\n" +
                "            t.setShowNextTab(function() { window.showNextTabCalled = true;} );\n" +
                "            t.setShowPrevTab(function() { window.showPrevTabCalled = true;} );" +
                "");

        js("window.XMLHttpRequestBAK = window.XMLHttpRequest;");

        js("window.XMLHttpRequest = function() {" +
                "    this.open = function (method, url) {" +
                "        window.xhrOpenedOn = url;" +
                //"        console.log (url);" +
                "    };" +
                "    this.setRequestHeader = function () { };" +
                "    this.getResponseHeader = function () { };" +
                "    this.send = function (data) {" +
                "        let re = new RegExp('.*k=([a-zA-Z]+).*'); " +
                "        let res = re.exec (data); "+
                "        window.ajtData = data;" +
                "    };" +
                "}");
    }

    @AfterEach
    public void tearDown() throws Exception {
        // need to restore this for further code which require it
        js("window.XMLHttpRequest = window.XMLHttpRequestBAK;");
    }

    @Test
    public void testNominal() throws Exception {

        js("window.XMLHttpRequest = function() {" +
                "    this.open = function (method, url) {" +
                "        window.xhrOpenedOn = url;" +
                //"        console.log (url);" +
                "    };" +
                "    this.setRequestHeader = function () { };" +
                "    this.getResponseHeader = function () { };" +
                "    this.send = function (data) {" +
                "        this.readyState = 4;" +
                "        this.status = 200;" +
                "        console.log (data);" +
                "        let re = new RegExp('.*k=([a-zA-Z]+).*'); " +
                "        let res = re.exec (data); "+
                "        if (res != null && res.length > 0) { " +
                "            console.log (res[1]);" +
                "            this.responseText = res[1]; " +
                "            this.onreadystatechange();" +
                "        } " +
                "    };" +
                "}");

        ((HtmlDivision)page.getElementById("test-term")).type("a");

        //Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> js("window.xhrOpenedOn").getJavaScriptResult().equals("./terminal?node=test"));
        Thread.sleep (2000);
        assertJavascriptEquals("./terminal?node=test", "window.xhrOpenedOn");

        assertJavascriptEquals("a", "$('.screen div:first-child').html()");
    }

    @Test
    public void testOnKeyDown_a() throws Exception {
        js("var e = jQuery.Event(\"keypress\"); e.which = 65; $('#test-term').trigger(e);");
        //Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> js("window.xhrOpenedOn").getJavaScriptResult().equals("./terminal?node=test"));
        //Thread.sleep (2000);
        // active wait
        boolean found = false;
        for (int i = 0; i < 100; i++) { // 10 seconds
            String sentData = js("window.ajtData").getJavaScriptResult().toString();
            Thread.sleep(100);
            if (sentData.endsWith("k=A&t=0")) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testOnKeyDown_F1() throws Exception {
        js("var e = jQuery.Event(\"keypress\"); e.keyCode = 112; e.which = 0; $('#test-term').trigger(e);");
        //Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> js("window.xhrOpenedOn").getJavaScriptResult().equals("./terminal?node=test"));
        //Thread.sleep (2000);
        // active wait
        boolean found = false;
        for (int i = 0; i < 100; i++) { // 10 seconds
            String sentData = js("window.ajtData").getJavaScriptResult().toString();
            Thread.sleep(100);
            if (sentData.endsWith("k=%1B%5B%5BA&t=0")) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testOnKeyDown_backspace() throws Exception {
        js("var e = jQuery.Event(\"keypress\"); e.keyCode = 8; e.which = 0; $('#test-term').trigger(e);");
        //Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> js("window.xhrOpenedOn").getJavaScriptResult().equals("./terminal?node=test"));
        // active wait
        boolean found = false;
        for (int i = 0; i < 100; i++) { // 10 seconds
            String sentData = js("window.ajtData").getJavaScriptResult().toString();
            Thread.sleep(100);
            if (sentData.endsWith("k=%7F&t=0")) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }
}

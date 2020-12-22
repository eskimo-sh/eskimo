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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class EskimoFileManagersTest extends AbstractWebTest {

    private String dirContent = null;

    @BeforeEach
    public void setUp() throws Exception {

        dirContent = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoFileManagersTest/dirContentTest.json"));

        loadScript (page, "eskimoFileManagers.js");

        js("function errorHandler() {};");

        js("var dirContent = " + dirContent + "");

        // instantiate test object
        js("eskimoFileManagers = new eskimo.FileManagers()");
        js("eskimoFileManagers.eskimoMain = {" +
                "        isSetupDone: function() { return true; }," +
                "        showOnlyContent: function() {}," +
                "        hideProgressbar: function() {}" +
                "    };");
        js("eskimoFileManagers.initialize()");

        waitForElementIdInDOM("file-upload-progress-modal");

        // mock functions
        js("eskimo.FileManagers.submitFormFileUpload = function (e) {}");

        // set services for tests
        js("eskimoFileManagers.setAvailableNodes (" +
                "[{\"nbr\": 1, \"nodeName\": \"192-168-10-11\", \"nodeAddress\": \"192.168.10.11\"}, " +
                " {\"nbr\": 2, \"nodeName\": \"192-168-10-13\", \"nodeAddress\": \"192.168.10.13\"} ] );");

        js("eskimoFileManagers.openFolder = function (nodeAddress, nodeName, currentFolder, subFolder) {" +
                "    eskimoFileManagers.listFolder (nodeAddress, nodeName, currentFolder+'/'+subFolder, dirContent);\n" +
                "}");

        js("eskimoFileManagers.connectFileManager = function (nodeAddress, nodeName) {" +
                "    alert (\"calledFor : \" + nodeAddress);\n" +
                "    eskimoFileManagers.getOpenedFileManagers().push({\"nodeName\" : nodeName, \"nodeAddress\": nodeAddress, \"current\": \"/\"});" +
                "    eskimoFileManagers.listFolder (nodeAddress, nodeName, '/', dirContent);\n" +
                "}");

        js("$('#inner-content-file-managers').css('display', 'inherit')");
        js("$('#inner-content-file-managers').css('visibility', 'visible')");
    }

    @Test
    public void testNominal() {

        js("eskimoFileManagers.openFileManager('192.168.10.11', '192-168-10-11');");

        assertJavascriptEquals("1.0", "eskimoFileManagers.getOpenedFileManagers().length");
        assertJavascriptEquals("192-168-10-11", "eskimoFileManagers.getOpenedFileManagers()[0].nodeName");

        // if this is set then we want as far as listFolder function
        assertJavascriptEquals("/", "eskimoFileManagers.getOpenedFileManagers()[0].current");
    }

    @Test
    public void testNodeVanish() throws Exception {

        testClickOpenFileManagers();

        // node 192-168-10-13 vanishes !
        js("eskimoFileManagers.setAvailableNodes (" +
                "[{\"nbr\": 1, \"nodeName\": \"192-168-10-11\", \"nodeAddress\": \"192.168.10.11\"} ] );");

        // ensure console was disabled
        //System.err.println (page.asXml());

        assertJavascriptEquals("\n" +
                "    <div id=\"file-manager-actions-192-168-10-13\">\n" +
                "        <nav id=\"file-manager-folder-menu-192-168-10-13\" class=\"btn-toolbar file-manager-folder-menu\">            <div class=\"btn-group\">                <button id=\"file-manager-close-192-168-10-13\" name=\"file-manager-close-192-168-10-13\" class=\"btn btn-primary\">Close</button>\n" +
                "            </div></nav>        <div id=\"file-manager-folder-content-192-168-10-13\">(connection to backend lost)</div>\n" +
                "    </div>", "$('#file-managers-file-manager-192-168-10-13').html()");
    }

    @Test
    public void testFindFileManager() {

        js("eskimoFileManagers.openFileManager('192.168.10.11', '192-168-10-11');");
        js("eskimoFileManagers.openFileManager('192.168.10.13', '192-168-10-13');");

        // if this is set then we want as far as listFolder function
        assertJavascriptEquals("/", "eskimoFileManagers.getOpenedFileManagers()[0].current");
        assertJavascriptEquals("/", "eskimoFileManagers.getOpenedFileManagers()[1].current");

        assertJavascriptEquals("192.168.10.11", "eskimoFileManagers.findFileManager('192-168-10-11').nodeAddress");
        assertJavascriptEquals("192.168.10.13", "eskimoFileManagers.findFileManager('192-168-10-13').nodeAddress");
    }

    @Test
    public void testUpdateCurrentFolder() {

        js("eskimoFileManagers.openFileManager('192.168.10.11', '192-168-10-11');");
        js("eskimoFileManagers.openFileManager('192.168.10.13', '192-168-10-13');");

        js("eskimoFileManagers.updateCurrentFolder('192-168-10-11', 'test1');");
        js("eskimoFileManagers.updateCurrentFolder('192-168-10-13', 'test2');");

        assertJavascriptEquals("test1", "eskimoFileManagers.getOpenedFileManagers()[0].current");
        assertJavascriptEquals("test2", "eskimoFileManagers.getOpenedFileManagers()[1].current");
    }

    @Test
    public void testListFolder() throws Exception {

        js("eskimoFileManagers.openFileManager('192.168.10.11', '192-168-10-11');");

        js("eskimoFileManagers.listFolder('192.168.10.11', '192-168-10-11', '/', "+dirContent+");");

        String htmlContent = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoFileManagersTest/expectedContent.rawhtml"));

        assertJavascriptEquals(htmlContent, "$('#file-manager-folder-content-192-168-10-11').html()");
    }

    @Test
    public void testShowParent() throws Exception {

        js("eskimoFileManagers.openFileManager('192.168.10.11', '192-168-10-11');");

        js("eskimoFileManagers.getOpenedFileManagers()[0].current = '/home/eskimo'");

        js("eskimoFileManagers.showParent('192.168.10.11', '192-168-10-11');");

        assertJavascriptEquals("/home/.", "eskimoFileManagers.getOpenedFileManagers()[0].current");
    }

    @Test
    public void testShowPrevious() throws Exception {

        testShowParent();

        js("eskimoFileManagers.showPrevious('192.168.10.11', '192-168-10-11');");

        assertJavascriptEquals("/home/eskimo/.", "eskimoFileManagers.getOpenedFileManagers()[0].current");
    }

    @Test
    public void testCloseFileManager() throws Exception {

        js("eskimoFileManagers.showFileManagers()");

        js("eskimoFileManagers.openFileManager('192.168.10.11', '192-168-10-11');");
        js("eskimoFileManagers.openFileManager('192.168.10.13', '192-168-10-13');");

        js("eskimoFileManagers.selectFileManager('192.168.10.11', '192-168-10-11');");

        //System.err.println (page.asXml());

        waitForElementIdInDOM("file-manager-close-192-168-10-11");

        page.getElementById("file-manager-close-192-168-10-11").click();

        assertJavascriptEquals("1.0", "eskimoFileManagers.getOpenedFileManagers().length");
        assertJavascriptEquals("192-168-10-13", "eskimoFileManagers.getOpenedFileManagers()[0].nodeName");
    }

    @Test
    public void testShowFileManagers() {
        js("eskimoFileManagers.showFileManagers()");

        assertNotNull (page.getElementById("file_manager_open_192-168-10-11"));
        assertEquals ("192.168.10.11", page.getElementById("file_manager_open_192-168-10-11").getTextContent().trim());

        assertNotNull (page.getElementById("file_manager_open_192-168-10-13"));
        assertEquals ("192.168.10.13", page.getElementById("file_manager_open_192-168-10-13").getTextContent().trim());
    }

    @Test
    public void testClickOpenFileManagers() throws Exception {

        testShowFileManagers();

        page.getElementById("file_manager_open_192-168-10-13").click();

        //System.err.println (page.asXml());

        assertCssValue ("#file-managers-file-manager-192-168-10-13", "visibility", "inherit");
        assertCssValue ("#file-managers-file-manager-192-168-10-13", "display", "inherit");

        page.getElementById("file_manager_open_192-168-10-11").click();

        assertCssValue ("#file-managers-file-manager-192-168-10-11", "visibility", "inherit");
        assertCssValue ("#file-managers-file-manager-192-168-10-11", "display", "inherit");

        assertCssValue ("#file-managers-file-manager-192-168-10-13", "visibility", "hidden");
        assertCssValue ("#file-managers-file-manager-192-168-10-13", "display", "none");
    }
}

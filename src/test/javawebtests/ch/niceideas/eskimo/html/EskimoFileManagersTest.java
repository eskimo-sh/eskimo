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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EskimoFileManagersTest extends AbstractWebTest {

    private String dirContent = null;

    @Before
    public void setUp() throws Exception {

        dirContent = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoFileManagersTest/dirContentTest.json"));

        page.executeJavaScript("loadScript('../../src/main/webapp/scripts/eskimoFileManagers.js')");

        page.executeJavaScript("function errorHandler() {};");

        page.executeJavaScript("var dirContent = " + dirContent + "");

        // instantiate test object
        page.executeJavaScript("eskimoFileManagers = new eskimo.FileManagers();");

        waitForElementIdInDOM("file-managers-file-manager-content");

        page.executeJavaScript("var openedFileManagers = [];");
        page.executeJavaScript("eskimoFileManagers.setOpenedFileManagers(openedFileManagers);");

        // mock functions
        page.executeJavaScript("eskimo.FileManagers.submitFormFileUpload = function (e) {}");
        page.executeJavaScript("eskimo.FileManagers.connectFileManager = function (nodeAddress) {}");

        // set services for tests
        page.executeJavaScript("eskimoFileManagers.setAvailableNodes (" +
                "[{\"nbr\": 1, \"nodeName\": \"192-168-10-11\", \"nodeAddress\": \"'192.168.10.11\"}, " +
                " {\"nbr\": 1, \"nodeName\": \"192-168-10-11\", \"nodeAddress\": \"'192.168.10.11\"} ] );");

    }

    @Test
    public void testNominal() throws Exception {
        page.executeJavaScript("eskimo.FileManagers.connectFileManager = function (nodeAddress) {" +
                "    eskimoFileManagers.listFolder (nodeAddress, '192-168-10-11', 'dirContent', dirContent);" +
                "}");


        page.executeJavaScript("openedFileManagers.push({\"nodeName\" : \"192-168-10-11\", \"nodeAddress\": \"192.168.10.11\", \"current\": \"/\"});");
        assertEquals ("1.0", page.executeJavaScript("eskimoFileManagers.getOpenedFileManagers().length").getJavaScriptResult().toString());
        assertEquals ("192-168-10-11", page.executeJavaScript("eskimoFileManagers.getOpenedFileManagers()[0].nodeName").getJavaScriptResult().toString());

        page.executeJavaScript("eskimoFileManagers.openFileManager('192.168.10.11', '192-168-10-11');");

        // if this is set then we want as far as listFolder function
        assertEquals("/", page.executeJavaScript("eskimoFileManagers.getOpenedFileManagers()[0].current").getJavaScriptResult().toString());
    }

}

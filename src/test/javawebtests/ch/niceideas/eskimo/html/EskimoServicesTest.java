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
import org.junit.Before;
import org.junit.Test;

import java.net.URL;

import static junit.framework.TestCase.fail;

public class EskimoServicesTest extends AbstractWebTest {

    @Before
    public void setUp() throws Exception {

        loadScript(page, "eskimoUtils.js");
        loadScript(page, "eskimoServices.js");

        page.executeJavaScript("function errorHandler() {};");

        // instantiate test object
        page.executeJavaScript("eskimoServices = new eskimo.Services();");

        URL testPage = ResourceUtils.getURL("classpath:emptyPage.html");

        page.executeJavaScript("eskimoServices.setEmptyFrameTarget (\""+testPage.getPath()+"/\");");

        page.executeJavaScript("eskimoServices.setUiServices( [\"cerebro\", \"kibana\", \"gdash\", \"spark-history-server\", \"zeppelin\"] );");

        page.executeJavaScript("eskimoServices.setUiServicesConfig( {" +
                "\"cerebro\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/cerebro', 'title' : 'cerebro' }, " +
                "\"kibana\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/kibana', 'title' : 'kibana' }, " +
                "\"gdash\": {'urlTemplate': 'http://{NODE_ADDRESS}:9999/gdash', 'title' : 'gdash' }, " +
                "\"spark-history-server\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/spark-histo', 'title' : 'spark-histo' }, " +
                "\"zeppelin\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/zeppelin', 'title' : 'zeppelin' }" +
                "});");
    }

    @Test
    public void testCreateMenu() throws Exception {

        // add services menu
        page.executeJavaScript("eskimoServices.createServicesMenu()");

        // make sure they're created
        assertJavascriptEquals ("1.0", "$('#folderMenuCerebro').length");
        assertJavascriptEquals ("1.0", "$('#folderMenuKibana').length");
        assertJavascriptEquals ("1.0", "$('#folderMenuGdash').length");
        assertJavascriptEquals ("1.0", "$('#folderMenuSparkHistoryServer').length");
        assertJavascriptEquals ("1.0", "$('#folderMenuZeppelin').length");

        // test zeppelin menu entry
        assertJavascriptEquals ("zeppelin-icon.png", "$('#folderMenuZeppelin').find('a > img').attr('src')");
        assertJavascriptEquals ("zeppelin", "$('#folderMenuZeppelin').find('a > span').html()");
    }

    @Test
    public void testShouldReinitialize() throws Exception {

        page.executeJavaScript("eskimoServices.setUiServicesConfig( {" +
                "\"zeppelin\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/zeppelin', 'icon' : 'testIcon', 'title' : 'zeppelin' }});");

        assertJavascriptEquals("true", "eskimoServices.shouldReinitialize('zeppelin', '192.168.10.11')");

        page.executeJavaScript("eskimoServices.setUiServicesConfig( {" +
                "\"zeppelin\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/zeppelin', 'icon' : 'testIcon', 'title' : 'zeppelin'," +
                "'actualUrl' : 'http://other-ip-address:9999/zeppelin' }});");

        assertJavascriptEquals("true", "eskimoServices.shouldReinitialize('zeppelin', '192.168.10.11')");

        page.executeJavaScript("eskimoServices.setUiServicesConfig( {" +
                "\"zeppelin\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/zeppelin', 'icon' : 'testIcon', 'title' : 'zeppelin'," +
                "'actualUrl' : 'http://192.168.10.11:9999/zeppelin' }});");

        assertJavascriptEquals("false", "eskimoServices.shouldReinitialize('zeppelin', '192.168.10.11')");
    }

    @Test
    public void testCreateIFrames() throws Exception {

        // add iframe nodes
        page.executeJavaScript("eskimoServices.createServicesIFrames()");

        // make sure they are created

        // ensure values are found in node 1
        assertJavascriptEquals ("1.0", "$('#iframe-content-cerebro').length");
        assertJavascriptEquals ("1.0", "$('#iframe-content-kibana').length");
        assertJavascriptEquals ("1.0", "$('#iframe-content-gdash').length");
        assertJavascriptEquals ("1.0", "$('#iframe-content-spark-history-server').length");
        assertJavascriptEquals ("1.0", "$('#iframe-content-zeppelin').length");
    }

    @Test
    public void testBuildUrl() throws Exception {

        page.executeJavaScript("uiConfig = {'urlTemplate': 'http://{NODE_ADDRESS}:9999/test'}"); // proxyContext
        assertJavascriptEquals("http://192.168.10.11:9999/test", "eskimoServices.buildUrl(uiConfig, '192.168.10.11')");

        page.executeJavaScript("uiConfig = {'proxyContext': '/test', 'unique': true}"); //
        assertJavascriptEquals("/test", "eskimoServices.buildUrl(uiConfig, '192.168.10.11')");

        page.executeJavaScript("uiConfig = {'proxyContext': '/test'}"); //
        assertJavascriptEquals("/test/192-168-10-11", "eskimoServices.buildUrl(uiConfig, '192.168.10.11')");
    }

    @Test
    public void testHandleServiceDisplayed() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testHandleServiceHiding() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testServiceMenuServiceFoundHook() throws Exception {
        fail ("To Be Implemented");
    }

}

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EskimoServicesTest extends AbstractWebTest {

    @Before
    public void setUp() throws Exception {

        page.executeJavaScript("loadScript('../../src/main/webapp/scripts/jquery-3.3.1.js')");
        page.executeJavaScript("loadScript('../../src/main/webapp/scripts/eskimoUtils.js')");
        page.executeJavaScript("loadScript('../../src/main/webapp/scripts/eskimoServices.js')");

        page.executeJavaScript("function errorHandler() {};");

        // redefine constructor
        page.executeJavaScript("eskimo.Services.initialize = function() {};");

        // instantiate test object
        page.executeJavaScript("eskimoServices = new eskimo.Services();");

        URL testPage = ResourceUtils.getURL("classpath:emptyPage.html");

        page.executeJavaScript("eskimoServices.setEmptyFrameTarget (\""+testPage.getPath()+"/\");");

        page.executeJavaScript("eskimoServices.setUiServices( [\"cerebro\", \"kibana\", \"gdash\", \"spark-history-server\", \"zeppelin\"] );");

        page.executeJavaScript("eskimoServices.setUiServicesConfig( {" +
                "\"cerebro\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/cerebro', 'icon' : 'testIcon', 'title' : 'cerebro' }, " +
                "\"kibana\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/kibana', 'icon' : 'testIcon', 'title' : 'kibana' }, " +
                "\"gdash\": {'urlTemplate': 'http://{NODE_ADDRESS}:9999/gdash', 'icon' : 'testIcon', 'title' : 'gdash' }, " +
                "\"spark-history-server\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/spark-histo', 'icon' : 'testIcon', 'title' : 'spark-histo' }, " +
                "\"zeppelin\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/zeppelin', 'icon' : 'testIcon', 'title' : 'zeppelin' }" +
                "});");
    }

    @Test
    public void testCreateMenu() throws Exception {

        // add services menu
        page.executeJavaScript("eskimoServices.createServicesMenu()");

        // make sure they're created
        assertEquals (1.0, page.executeJavaScript("$( \"#folderMenuCerebro\" ).length").getJavaScriptResult());
        assertEquals (1.0, page.executeJavaScript("$( \"#folderMenuKibana\" ).length").getJavaScriptResult());
        assertEquals (1.0, page.executeJavaScript("$( \"#folderMenuGdash\" ).length").getJavaScriptResult());
        assertEquals (1.0, page.executeJavaScript("$( \"#folderMenuSparkHistoryServer\" ).length").getJavaScriptResult());
        assertEquals (1.0, page.executeJavaScript("$( \"#folderMenuZeppelin\" ).length").getJavaScriptResult());

        // test zeppelin menu entry
        assertEquals ("testIcon", page.executeJavaScript("$('#folderMenuZeppelin').find('a > img').attr('src')").getJavaScriptResult());
        assertEquals ("zeppelin", page.executeJavaScript("$('#folderMenuZeppelin').find('a > span').html()").getJavaScriptResult());
    }

    @Test
    public void testShouldReinitialize() throws Exception {

        page.executeJavaScript("eskimoServices.setUiServicesConfig( {" +
                "\"zeppelin\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/zeppelin', 'icon' : 'testIcon', 'title' : 'zeppelin' }});");

        assertEquals(true, page.executeJavaScript("eskimoServices.shouldReinitialize('zeppelin', '192.168.10.11')").getJavaScriptResult());

        page.executeJavaScript("eskimoServices.setUiServicesConfig( {" +
                "\"zeppelin\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/zeppelin', 'icon' : 'testIcon', 'title' : 'zeppelin'," +
                "'actualUrl' : 'http://other-ip-address:9999/zeppelin' }});");

        assertEquals(true, page.executeJavaScript("eskimoServices.shouldReinitialize('zeppelin', '192.168.10.11')").getJavaScriptResult());

        page.executeJavaScript("eskimoServices.setUiServicesConfig( {" +
                "\"zeppelin\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/zeppelin', 'icon' : 'testIcon', 'title' : 'zeppelin'," +
                "'actualUrl' : 'http://192.168.10.11:9999/zeppelin' }});");

        assertEquals(false, page.executeJavaScript("eskimoServices.shouldReinitialize('zeppelin', '192.168.10.11')").getJavaScriptResult());
    }

    @Test
    public void testCreateIFrames() throws Exception {

        // add iframe nodes
        page.executeJavaScript("eskimoServices.createServicesIFrames()");

        // make sure they are created

        // ensure values are found in node 1
        assertEquals (1.0, page.executeJavaScript("$( \"#iframe-content-cerebro\" ).length").getJavaScriptResult());
        assertEquals (1.0, page.executeJavaScript("$( \"#iframe-content-kibana\" ).length").getJavaScriptResult());
        assertEquals (1.0, page.executeJavaScript("$( \"#iframe-content-gdash\" ).length").getJavaScriptResult());
        assertEquals (1.0, page.executeJavaScript("$( \"#iframe-content-spark-history-server\" ).length").getJavaScriptResult());
        assertEquals (1.0, page.executeJavaScript("$( \"#iframe-content-zeppelin\" ).length").getJavaScriptResult());
    }

    @Test
    public void testBuildUrl() throws Exception {

        page.executeJavaScript("uiConfig = {'urlTemplate': 'http://{NODE_ADDRESS}:9999/test'}"); // proxyContext
        assertEquals("http://192.168.10.11:9999/test", page.executeJavaScript("eskimoServices.buildUrl(uiConfig, '192.168.10.11')").getJavaScriptResult());

        page.executeJavaScript("uiConfig = {'proxyContext': '/test', 'unique': true}"); //
        assertEquals("/test", page.executeJavaScript("eskimoServices.buildUrl(uiConfig, '192.168.10.11')").getJavaScriptResult());

        page.executeJavaScript("uiConfig = {'proxyContext': '/test'}"); //
        assertEquals("/test/192-168-10-11", page.executeJavaScript("eskimoServices.buildUrl(uiConfig, '192.168.10.11')").getJavaScriptResult());
    }

}

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

import ch.niceideas.common.utils.ResourceUtils;
import org.json.JSONArray;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class EskimoServicesTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript(page, "eskimoUtils.js");
        loadScript(page, "eskimoServices.js");

        js("function errorHandler() {};");

        // instantiate test object
        js("eskimoServices = new eskimo.Services()");
        js("eskimoServices.eskimoMain = eskimoMain");
        js("eskimoServices.eskimoSystemStatus = eskimoSystemStatus");
        js("eskimoServices.eskimoNodesConfig = eskimoNodesConfig");
        js("eskimoServices.initialize()");

        URL testPage = ResourceUtils.getURL("classpath:emptyPage.html");

        js("eskimoServices.setEmptyFrameTarget (\""+testPage.getPath()+"/\");");

        js("eskimoServices.setUiServices( [\"cerebro\", \"kibana\", \"spark-console\", \"zeppelin\"] );");

        js("var UI_SERVICES_CONFIG = {" +
                "\"cerebro\" : {'urlTemplate': './cerebro/{NODE_ADDRESS}:9999/cerebro', 'title' : 'cerebro', 'waitTime': 10 }, " +
                "\"kibana\" : {'urlTemplate': './kibana/{NODE_ADDRESS}:9999/kibana', 'title' : 'kibana', 'waitTime': 15 }, " +
                "\"spark-console\" : {'urlTemplate': './spark-histo/{NODE_ADDRESS}:9999/spark-histo', 'title' : 'spark-histo', 'waitTime': 25 }, " +
                "\"zeppelin\" : {'urlTemplate': './zeppelin/{NODE_ADDRESS}:9999/zeppelin', 'title' : 'zeppelin', 'waitTime': 30 }" +
                "};");

        js("eskimoServices.setUiServicesConfig(UI_SERVICES_CONFIG);");
    }

    @Test
    public void testRandomizeUrl() throws Exception {

        js("Math.random = function () {return 0.111; };");

        assertJavascriptEquals("a/b/c?dummyarg=0.111", "eskimoServices.randomizeUrl ('a/b/c');");
        assertJavascriptEquals("a/b/c?dummyarg=0.111#/e/f", "eskimoServices.randomizeUrl ('a/b/c#/e/f');");

        assertJavascriptEquals("a/b/c?a=2&c=3&dummyarg=0.111", "eskimoServices.randomizeUrl ('a/b/c?a=2&c=3');");
        assertJavascriptEquals("a/b/c?a=2&c=3&dummyarg=0.111#/e/f", "eskimoServices.randomizeUrl ('a/b/c?a=2&c=3#/e/f');");

        assertJavascriptEquals("a/b/c?dummyarg=0.111#/e/f?test=a", "eskimoServices.randomizeUrl ('a/b/c#/e/f?test=a');");
    }

    @Test
    public void testShowServiceIFrame() throws Exception {

        // 1. setup not done
        js("eskimoMain.showSetupNotDone = function(message) { window.setupNotDoneMessage = message; }");
        js("eskimoMain.isSetupDone = function() { return false; }");

        js("eskimoServices.showServiceIFrame('cerebro')");

        assertJavascriptEquals("Service cerebro is not available at this stage.", "window.setupNotDoneMessage");

        // 2. disconnected
        js("eskimoMain.isSetupDone = function() { return true; }");
        js("eskimoSystemStatus.isDisconnected = function() { return true; }");
        js("eskimoSystemStatus.showStatus = function() { window.showStatusCalled = true; }");

        js("eskimoServices.showServiceIFrame('cerebro')");

        assertJavascriptEquals("true", "window.showStatusCalled");

        // 3. Service not yet initialized
        js("eskimoSystemStatus.isDisconnected = function() { return false; }");
        js("eskimoSystemStatus.showStatusWhenServiceUnavailable = function (service) { window.statusUnavailableService = service;}");

        js("eskimoServices.showServiceIFrame('cerebro')");

        assertJavascriptEquals("cerebro", "window.statusUnavailableService");

        // 4. service initialized
        js("eskimoMain.hideProgressbar = function() { window.hideProgressbarCalled = true; }");
        js("eskimoMain.setNavigationCompact = function() { window.setNavigationCompactCalled = true; }");
        js("eskimoMain.showOnlyContent = function (content) { window.onlyContentShown = content; }");

        js("eskimoServices.setServiceInitializedForTests ('cerebro');");

        js("eskimoServices.showServiceIFrame('cerebro')");

        assertJavascriptEquals("true", "window.hideProgressbarCalled");
        assertJavascriptEquals("true", "window.setNavigationCompactCalled");
        assertJavascriptEquals("cerebro", "window.onlyContentShown");
    }

    @Test
    public void testIsServiceAvailable() throws Exception {

        assertJavascriptEquals("false", "eskimoServices.isServiceAvailable('non-existent');");

        js("eskimoServices.setUiServicesConfig( {" +
                "\"zeppelin\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/zeppelin', 'icon' : 'testIcon', 'title' : 'zeppelin', 'refreshWaiting': true }});");

        assertJavascriptEquals("false", "eskimoServices.isServiceAvailable('zeppelin');");

        js("eskimoServices.setUiServicesConfig( {" +
                "\"zeppelin\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/zeppelin', 'icon' : 'testIcon', 'title' : 'zeppelin'}});");

        assertJavascriptEquals("true", "!(eskimoServices.isServiceAvailable('zeppelin'))"); // undefined

        js("eskimoServices.setServiceInitializedForTests ('zeppelin');");

        assertJavascriptEquals("true", "eskimoServices.isServiceAvailable('zeppelin');");
    }

    @Test
    public void testPeriodicRetryServices() throws Exception {

        js("eskimoServices.handleServiceDisplay('cerebro', UI_SERVICES_CONFIG['cerebro'], '192-168-10-11', false);");
        js("eskimoServices.handleServiceDisplay('kibana', UI_SERVICES_CONFIG['kibana'], '192-168-10.11', false);");
        js("eskimoServices.handleServiceDisplay('kibana', UI_SERVICES_CONFIG['kibana'], '192.168.10.11', false);");

        js("alert (JSON.stringify (eskimoServices.getUIConfigsToRetryForTests()));");

        assertJavascriptEquals("2.0", "eskimoServices.getUIConfigsToRetryForTests().length");
        assertJavascriptEquals("cerebro", "eskimoServices.getUIConfigsToRetryForTests()[0].title");
        assertJavascriptEquals("kibana", "eskimoServices.getUIConfigsToRetryForTests()[1].title");

        js("$.ajax = function(object) { object.success(); }");

        js("eskimoServices.periodicRetryServices();");

        await().atMost(10, TimeUnit.SECONDS).until(() -> ((Double)js("eskimoServices.getUIConfigsToRetryForTests().length").getJavaScriptResult()) == 0.0);
        assertJavascriptEquals("0.0", "eskimoServices.getUIConfigsToRetryForTests().length");
    }

    @Test
    public void testCreateMenu() throws Exception {

        // add services menu
        js("eskimoServices.createServicesMenu()");

        // make sure they're created
        assertJavascriptEquals ("1.0", "$('#folderMenuCerebro').length");
        assertJavascriptEquals ("1.0", "$('#folderMenuKibana').length");
        assertJavascriptEquals ("1.0", "$('#folderMenuSparkHistoryServer').length");
        assertJavascriptEquals ("1.0", "$('#folderMenuZeppelin').length");

        // test zeppelin menu entry
        assertJavascriptEquals ("zeppelin-icon.png", "$('#folderMenuZeppelin').find('a > img').attr('src')");
        assertJavascriptEquals ("zeppelin", "$('#folderMenuZeppelin').find('a > span').html()");
    }

    @Test
    public void testShouldReinitialize() throws Exception {

        js("eskimoServices.setUiServicesConfig( {" +
                "\"zeppelin\" : {'urlTemplate': 'http://{NODE_ADDRESS}:9999/zeppelin', 'icon' : 'testIcon', 'title' : 'zeppelin' }});");

        assertJavascriptEquals("true", "eskimoServices.shouldReinitialize('zeppelin', '192.168.10.11')");

        js("eskimoServices.setUiServicesConfig( {" +
                "\"zeppelin\" : {'urlTemplate': './zeppelin/{NODE_ADDRESS}:9999/zeppelin', 'icon' : 'testIcon', 'title' : 'zeppelin'," +
                "'actualUrl' : './zeppelin/other-ip-address:9999/zeppelin' }});");

        assertJavascriptEquals("true", "eskimoServices.shouldReinitialize('zeppelin', '192.168.10.11')");

        js("eskimoServices.setUiServicesConfig( {" +
                "\"zeppelin\" : {'urlTemplate': './zeppelin/{NODE_ADDRESS}:9999/zeppelin', 'icon' : 'testIcon', 'title' : 'zeppelin'," +
                "'actualUrl' : './zeppelin/192-168-10-11:9999/zeppelin' }});");

        assertJavascriptEquals("false", "eskimoServices.shouldReinitialize('zeppelin', '192.168.10.11')");
    }

    @Test
    public void testCreateIFrames() throws Exception {

        // add iframe nodes
        js("eskimoServices.createServicesIFrames()");

        // make sure they are created

        // ensure values are found in node 1
        assertJavascriptEquals ("1.0", "$('#iframe-content-cerebro').length");
        assertJavascriptEquals ("1.0", "$('#iframe-content-kibana').length");
        assertJavascriptEquals ("1.0", "$('#iframe-content-spark-console').length");
        assertJavascriptEquals ("1.0", "$('#iframe-content-zeppelin').length");
    }

    @Test
    public void testBuildUrl() throws Exception {

        js("uiConfig = {'urlTemplate': './test/{NODE_ADDRESS}:9999/test'}"); // proxyContext
        assertJavascriptEquals("./test/192-168-10-11:9999/test", "eskimoServices.buildUrl(uiConfig, '192.168.10.11')");

        js("uiConfig = {'proxyContext': '/test', 'unique': true}"); //
        assertJavascriptEquals("/test", "eskimoServices.buildUrl(uiConfig, '192.168.10.11')");

        js("uiConfig = {'proxyContext': '/test'}"); //
        assertJavascriptEquals("/test/192-168-10-11", "eskimoServices.buildUrl(uiConfig, '192.168.10.11')");
    }

    @Test
    public void testHandleServiceDisplay() throws Exception {

        assertJavascriptEquals("undefined", "typeof UI_SERVICES_CONFIG['cerebro'].targetUrl");
        assertJavascriptEquals("undefined", "typeof UI_SERVICES_CONFIG['cerebro'].service");
        assertJavascriptEquals("undefined", "typeof UI_SERVICES_CONFIG['cerebro'].targetWaitTime");
        assertJavascriptEquals("undefined", "typeof UI_SERVICES_CONFIG['cerebro'].refreshWaiting");

        js("eskimoServices.setServiceNotInitializedForTests ('cerebro');");

        assertJavascriptEquals("false", "eskimoServices.isServiceAvailable('cerebro')");

        js("eskimoServices.handleServiceDisplay('cerebro', UI_SERVICES_CONFIG['cerebro'], '192.168.10.11', false);");

        assertJavascriptEquals("./cerebro/192-168-10-11:9999/cerebro", "UI_SERVICES_CONFIG['cerebro'].targetUrl");
        assertJavascriptEquals("cerebro", "UI_SERVICES_CONFIG['cerebro'].service");
        assertJavascriptEquals("10.0", "UI_SERVICES_CONFIG['cerebro'].targetWaitTime");
        assertJavascriptEquals("true", "UI_SERVICES_CONFIG['cerebro'].refreshWaiting");


        js("UI_SERVICES_CONFIG['cerebro'].refreshWaiting = false;");
        js("eskimoServices.handleServiceDisplay('cerebro', UI_SERVICES_CONFIG['cerebro'], '192.168.10.11', true);");

        assertJavascriptEquals("0.0", "UI_SERVICES_CONFIG['cerebro'].targetWaitTime");

        js("eskimoServices.handleServiceIsUp(UI_SERVICES_CONFIG['cerebro'])");

        await().atMost(5, TimeUnit.SECONDS).until(() -> js("UI_SERVICES_CONFIG['cerebro'].refreshWaiting").getJavaScriptResult().toString().equals ("false"));

        assertJavascriptEquals("false", "UI_SERVICES_CONFIG['cerebro'].refreshWaiting");
        assertJavascriptEquals("./cerebro/192-168-10-11:9999/cerebro", "UI_SERVICES_CONFIG['cerebro'].actualUrl");
    }

    @Test
    public void testHandleServiceHiding() throws Exception {

        testHandleServiceDisplay();

        js("eskimoServices.handleServiceHiding('cerebro', UI_SERVICES_CONFIG['cerebro'])");

        assertJavascriptNull("UI_SERVICES_CONFIG['cerebro'].actualUrl");
    }

    @Test
    public void testRetryPolicyNominal() throws Exception {

        js("eskimoServices.periodicRetryServices = function () { " +
                "    window.uiConfigsToRetry = eskimoServices.getUIConfigsToRetryForTests();" +
                "}");

        js("eskimoServices.serviceMenuServiceFoundHook('192-168-10-11', '192.168.10.11', 'kibana', true, false)");

        js("eskimoServices.periodicRetryServices()");

        assertTrue (
                new JSONArray("[{" +
                        "\"urlTemplate\":\"./kibana/{NODE_ADDRESS}:9999/kibana\"," +
                        "\"title\":\"kibana\"," +
                        "\"waitTime\":15," +
                        "\"targetUrl\":\"./kibana/192-168-10-11:9999/kibana\"," +
                        "\"service\":\"kibana\"," +
                        "\"targetWaitTime\":15," +
                        "\"refreshWaiting\":true}]")
                .similar(
                new JSONArray((String)js("JSON.stringify (window.uiConfigsToRetry)").getJavaScriptResult())));
    }

    @Test
    public void testRetryPolicyStoppedWhenServiceVanishes() throws Exception {

        testRetryPolicyNominal();

        js("eskimoServices.serviceMenuServiceFoundHook('192-168-10-11', '192.168.10.11', 'kibana', false, false)");

        js("eskimoServices.periodicRetryServices()");

        System.err.println(js("JSON.stringify (window.uiConfigsToRetry)").getJavaScriptResult());

        assertTrue (
                new JSONArray("[]")
                        .similar(
                                new JSONArray((String)js("JSON.stringify (window.uiConfigsToRetry)").getJavaScriptResult())));
    }

    @Test
    public void testRetryPolicyNominalThenServiceUp() throws Exception {

        testRetryPolicyNominal();

        js("eskimoServices.handleServiceIsUp(window.uiConfigsToRetry[0])");

        js("eskimoServices.periodicRetryServices()");

        System.err.println(js("JSON.stringify (window.uiConfigsToRetry)").getJavaScriptResult());

        assertTrue (
                new JSONArray("[]")
                        .similar(
                                new JSONArray((String)js("JSON.stringify (window.uiConfigsToRetry)").getJavaScriptResult())));

    }

    @Test
    public void testRetryPolicyServiceClearDoesntStopRetry() throws Exception {

        testRetryPolicyNominal();

        // this is what eskimoMain.serviceMenuClear calls
        js("eskimoServices.handleServiceHiding('kibana')");

        js("eskimoServices.periodicRetryServices()");

        System.err.println(js("JSON.stringify (window.uiConfigsToRetry)").getJavaScriptResult());

        assertTrue (
                new JSONArray("[{" +
                        "\"urlTemplate\":\"./kibana/{NODE_ADDRESS}:9999/kibana\"," +
                        "\"title\":\"kibana\"," +
                        "\"waitTime\":15," +
                        "\"targetUrl\":\"./kibana/192-168-10-11:9999/kibana\"," +
                        "\"service\":\"kibana\"," +
                        "\"targetWaitTime\":15," +
                        "\"refreshWaiting\":true," +
                        "\"actualUrl\":null}]")
                        .similar(
                                new JSONArray((String)js("JSON.stringify (window.uiConfigsToRetry)").getJavaScriptResult())));
    }
}

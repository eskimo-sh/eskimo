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

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.eskimo.utils.GenerateLCOV;
import com.gargoylesoftware.htmlunit.AjaxController;
import com.gargoylesoftware.htmlunit.ScriptResult;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import jscover.Main;
import jscover.report.FileData;
import jscover.report.JSONDataMerger;
import org.apache.log4j.Logger;
import org.junit.*;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public abstract class AbstractWebTest {

    private static final Logger logger = Logger.getLogger(AbstractWebTest.class);

    //public static final int MAX_WAIT_TIME_SECS = 50;

    private static final int INCREMENTAL_WAIT_MS = 500;
    private static final int MAX_WAIT_RETRIES = 50;

    private static Thread server;
    private static Main main = null;

    private static File jsCoverageFlagFile = new File("target/jsCoverageFlag");

    private static String jsCoverReportDir = "target/jscov-report";
    private static String[] jsCoverArgs = new String[]{
            "-ws",
            "--document-root=src/main/webapp",
            "--port=9001",
            //"--no-branch",
            //"--no-function",
            //"--no-instrument=example/lib",
            "--log=INFO",
            "--report-dir=" + jsCoverReportDir
    };

    private static String className = null;
    private static List<String> coverages = new ArrayList<>();

    private static JSONDataMerger jsonDataMerger = new JSONDataMerger();

    protected WebClient webClient;
    protected HtmlPage page;

    ScriptResult js (String jsCode) {
        return page.executeJavaScript (jsCode);
    }

    @BeforeClass
    public static void setUpOnce() {
        if (isCoverageRun()) {
            main = new Main();
            server = new Thread(() -> main.runMain(jsCoverArgs));
            server.start();
        }
    }

    @AfterClass
    public static void tearDownOnce() throws Exception {
        if (isCoverageRun()) {
            main.stop();

            File targetFile = new File(jsCoverReportDir + "/" + className, "jscoverage.json");
            targetFile.getParentFile().mkdirs();
            FileUtils.writeFile(targetFile, mergeJSON());
        }
    }

    @Before
    public void setUpClassName() {
        Class<?> clazz = this.getClass(); //if you want to get Class object
        className = clazz.getCanonicalName(); //you want to get only class name
    }

    @After
    public void tearDown() throws Exception {
        if (isCoverageRun()) {
            js("window.jscoverFinished = false;");
            js("jscoverage_report('', function(){window.jscoverFinished=true;});");

            // FIXME I have failing tests with Awaitility !?!
            /*
            await().atMost(MAX_WAIT_TIME_SECS * (isCoverageRun() ? 2 : 1)  , TimeUnit.SECONDS).until(
                    () -> (Boolean) js("window.jscoverFinished").getJavaScriptResult());

            */

            int attempt = 0;
            while ((!((Boolean) (js("window.jscoverFinished").getJavaScriptResult())).booleanValue()) && attempt < 10) {
                logger.debug("Waiting for coverage report to be written ...");
                Thread.sleep(500);
                attempt++;
            }

            String json = (String) (js("jscoverage_serializeCoverageToJSON();")).getJavaScriptResult();
            coverages.add(json);
        }
    }

    private static String mergeJSON() {
        SortedMap<String, FileData> total = new TreeMap<>();
        for (String json : coverages) {
            total = jsonDataMerger.mergeJSONCoverageMaps(total, jsonDataMerger.jsonToMap(json));
        }
        return GenerateLCOV.toJSON(total);
    }

    protected static boolean isCoverageRun() {
        //return true;
        return jsCoverageFlagFile.exists();
    }

    protected final void loadScript (HtmlPage page, String script) {
        if (isCoverageRun()) {
            js("loadScript('http://localhost:9001/scripts/"+script+"')");
        } else {
            js("loadScript('../../src/main/webapp/scripts/"+script+"')");
        }
    }

    @Before
    public void init() throws Exception {
        webClient = new WebClient();

        webClient.setAlertHandler((page, s) -> logger.info(s));

        webClient.setAjaxController(new AjaxController() {

        });

        URL testPage = ResourceUtils.getURL("classpath:GenericTestPage.html");
        page = webClient.getPage(testPage);
        Assert.assertEquals("Generic Test Page", page.getTitleText());

        // create common mocks
        // create mock functions
        js("var eskimoServices = {};");
        js("eskimoServices.serviceMenuServiceFoundHook = function (){};");
        js("eskimoServices.getServiceIcon = function (service) { return service + '-icon.png'; };");
        js("eskimoServices.isServiceAvailable = function (){ return true; };");

        js("var eskimoConsoles = {}");
        js("eskimoConsoles.setAvailableNodes = function () {};");

        js("var eskimoServicesSelection = {" +
                "}");

        js("var eskimoOperationsCommand = {" +
                "showCommand : function() {}" +
                "}");

        js("var eskimoMessaging = {}");
        js("eskimoMessaging.isOperationInProgress = function() { return false; };");
        js("eskimoMessaging.setOperationInProgress = function() {};");
        js("eskimoMessaging.showMessages = function() {};");

        js("var eskimoNotifications = {}");
        js("eskimoNotifications.fetchNotifications = function() {};");

        js("var eskimoSetup = {}");
        js("eskimoSetup.setSnapshot = function () {};");

        js("var eskimoFileManagers = {};");
        js("eskimoFileManagers.setAvailableNodes = function() {};");

        js("var eskimoNodesConfig = {};");
        js("eskimoNodesConfig.getServiceLogoPath = function (serviceName){ return serviceName + '-logo.png'; };");
        js("eskimoNodesConfig.getServiceIconPath = function (serviceName){ return serviceName + '-icon.png'; };");
        js("eskimoNodesConfig.getServicesDependencies = function () { return {}; };");
        js("eskimoNodesConfig.isServiceUnique = function (serviceName){ " +
                "return (serviceName == 'mesos-master' " +
                "    || serviceName == 'zookeeper' " +
                "    || serviceName == 'grafana' " +
                "    || serviceName == 'gdash' " +
                "    || serviceName == 'kafka-manager' " +
                "    || serviceName == 'spark-history-server' " +
                "    || serviceName == 'flink-app-master' " +
                "    || serviceName == 'cerebro' " +
                "    || serviceName == 'kibana' " +
                "    || serviceName == 'zeppelin' ); " +
                "};");

        js("var eskimoSystemStatus = {};");
        js("eskimoSystemStatus.showStatus = function () {};");

        js("var eskimoMarathonServicesSelection = {" +
                "showMarathonServiceSelection: function () {}" +
                "};");

        js("var eskimoMarathonOperationsCommand = {" +
                "showCommand : function() {}" +
                "};");

        js("var eskimoSettingsOperationsCommand = {}");

        js("var eskimoMain = {};");
        js("eskimoMain.handleSetupCompleted = function (){};");
        js("eskimoMain.getServices = function (){ return eskimoServices; };");
        js("eskimoMain.getMessaging = function (){ return eskimoMessaging; };");
        js("eskimoMain.getFileManagers = function (){ return eskimoFileManagers; };");
        js("eskimoMain.getConsoles = function (){ return eskimoConsoles; };");
        js("eskimoMain.getNodesConfig = function () { return eskimoNodesConfig; };");
        js("eskimoMain.isOperationInProgress = function() { return false; };");
        js("eskimoMain.setAvailableNodes = function () {};");
        js("eskimoMain.menuResize = function () {};");
        js("eskimoMain.isSetupDone = function () { return true; }");
        js("eskimoMain.hideProgressbar = function () { }");
        js("eskimoMain.isCurrentDisplayedService = function () { return false; }");
        js("eskimoMain.setSetupLoaded = function () {}");
        js("eskimoMain.startOperationInProgress = function() {}");
        js("eskimoMain.scheduleStopOperationInProgress = function() {}");
        js("eskimoMain.handleMarathonSubsystem = function() {}");
        js("eskimoMain.showProgressbar = function() {}");
        js("eskimoMain.isSetupLoaded = function() { return true; }");
        js("eskimoMain.serviceMenuClear = function() { return true; }");

        js("eskimoMain.getSystemStatus = function() { return eskimoSystemStatus; }");

        js("eskimoMain.showOnlyContent = function (content) { " +
                "    $(\".inner-content\").css(\"visibility\", \"hidden\");\n" +
                "    $(\"#inner-content-\" + content).css(\"visibility\", \"visible\");" +
                "    $(\"#inner-content-\" + content).css(\"display\", \"block\");" +
                "}");


        // 3 attempts
        for (int i = 0; i < 3 ; i++) {
            logger.info ("Loading jquery : attempt " + i);
            loadScript(page, "jquery-3.3.1.js");

            waitForDefinition("window.$");

            if (!js("typeof window.$").getJavaScriptResult().toString().equals ("undefined")) {
                break;
            }
        }

        waitForDefinition("$.fn");

        // override jquery load
        js("$.fn._internalLoad = $.fn.load;");
        js("$.fn.load = function (resource, callback) { return this._internalLoad ('../../src/main/webapp/'+resource, callback); };");

    }

    @After
    public void close() {
        webClient.close();
    }

    protected void assertAttrValue(String selector, String attribute, String value) {
        assertEquals (value, js("$('"+selector+"').attr('"+attribute+"')").getJavaScriptResult());
    }

    protected void assertCssValue(String selector, String attribute, String value) {
        assertEquals (value, js("$('"+selector+"').css('"+attribute+"')").getJavaScriptResult());
    }

    protected void assertJavascriptEquals(String value, String javascript) {
        assertEquals (value, js(javascript).getJavaScriptResult().toString());
    }

    protected void assertJavascriptNull(String javascript) {
        assertNull (js(javascript).getJavaScriptResult());
    }

    protected void assertTagName(String elementId, String tagName) {
        assertEquals (tagName, page.getElementById(elementId).getTagName());
    }

    protected void waitForElementIdInDOM(String elementId) throws InterruptedException {

        // FIXME I have failing tests with Awaitility !?!
        /*
        await().atMost(MAX_WAIT_TIME_SECS * (isCoverageRun() ? 2 : 1) , TimeUnit.SECONDS).until(
                () -> page.getElementById(elementId) != null);
        */

        int attempt = 0;
        while (page.getElementById(elementId) == null && attempt < MAX_WAIT_RETRIES) {
            Thread.sleep(INCREMENTAL_WAIT_MS);
            attempt++;
        }
    }

    protected void waitForDefinition(String varName) throws InterruptedException {

        // FIXME I have failing tests with Awaitility !?!
        /*
        await().atMost(MAX_WAIT_TIME_SECS * (isCoverageRun() ? 2 : 1) , TimeUnit.SECONDS).until(
                () -> !js("typeof " + varName).getJavaScriptResult().toString().equals ("undefined"));
        */

        int attempt = 0;
        while (js("typeof " + varName).getJavaScriptResult().toString().equals ("undefined") && attempt < MAX_WAIT_RETRIES) {
            Thread.sleep(INCREMENTAL_WAIT_MS);
            attempt++;
        }
    }
}

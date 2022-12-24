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

import ch.niceideas.eskimo.html.infra.TestResourcesServer;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public abstract class AbstractWebTest {

    private static final Logger logger = Logger.getLogger(AbstractWebTest.class);

    private static File jsCoverageFlagFile = new File("target/jsCoverageFlag");

    private static final int INCREMENTAL_WAIT_MS = 500;
    private static final int MAX_WAIT_RETRIES = 50;

    private static String className = null;

    private static TestResourcesServer server;

    protected static WebDriver driver;

    @BeforeAll
    public static void setUpOnce() throws Exception {
        server = TestResourcesServer.getServer(isCoverageRun());
        server.startServer(className);

        ChromeOptions co = new ChromeOptions();

        co.setCapability(CapabilityType.UNHANDLED_PROMPT_BEHAVIOUR, "ignore");

        co.addArguments("--no-sandbox");
        co.addArguments("--window-position=0,0");
        co.addArguments("--window-size=1900,1024");
        co.addArguments("--headless");
        co.addArguments("--disable-gpu");

        driver = WebDriverManager.chromedriver()
                .capabilities(co)
                .create();

        driver.get("http://localhost:9001/src/test/resources/GenericTestPage.html");
    }

    @AfterAll
    public static void tearDownOnce() throws Exception {
        server.stopServer();

        if (driver != null) {
            driver.quit();
        }

        Thread.sleep(1000); // give some time to selenium driver to really shutdown before running next test
    }

    @BeforeEach
    public void init() throws Exception {

        Class<?> clazz = this.getClass(); //if you want to get Class object
        className = clazz.getCanonicalName(); //you want to get only class name

        driver.navigate().refresh();

        // wait for page to load
        Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(100));
        wait.until(innerDriver -> {
            return String
                    .valueOf(((JavascriptExecutor) innerDriver).executeScript("return document.readyState"))
                    .equals("complete");
        });

        assertEquals("Generic Test Page", driver.getTitle());

        // create common mocks
        initDriver();
    }

    @AfterEach
    public void tearDown() throws Exception {
        server.postTestMethodHook(this::js);
    }

    private void initDriver() throws InterruptedException {
        // create mock functions
        js("window.eskimoServices = {};");
        js("eskimoServices.serviceMenuServiceFoundHook = function (){};");
        js("eskimoServices.getServiceIcon = function (service) { return service + '-icon.png'; };");
        js("eskimoServices.isServiceAvailable = function (){ return true; };");

        js("window.eskimoConsoles = {}");
        js("eskimoConsoles.setAvailableNodes = function () {};");

        js("window.eskimoServicesSelection = {" +
                "}");

        js("window.eskimoOperationsCommand = {" +
                "showCommand : function() {}" +
                "}");

        js("window.eskimoMessaging = {}");
        js("eskimoMessaging.isOperationInProgress = function() { return false; };");
        js("eskimoMessaging.setOperationInProgress = function() {};");
        js("eskimoMessaging.showMessages = function() {};");

        js("window.eskimoOperations = {}");
        js("eskimoOperations.isOperationInProgress = function() { return false; };");
        js("eskimoOperations.setOperationInProgress = function() {};");
        js("eskimoOperations.showOperations = function() {};");

        js("window.eskimoNotifications = {}");
        js("eskimoNotifications.fetchNotifications = function() {};");

        js("window.eskimoSetup = {}");
        js("eskimoSetup.setSnapshot = function () {};");

        js("window.eskimoSetupCommand = {}");

        js("window.eskimoFileManagers = {};");
        js("eskimoFileManagers.setAvailableNodes = function() {};");

        js("window.eskimoServicesSettings = {};");

        js("window.eskimoNodesConfig = {};");
        js("eskimoNodesConfig.getServiceLogoPath = function (serviceName){ return serviceName + '-logo.png'; };");
        js("eskimoNodesConfig.getServiceIconPath = function (serviceName){ return serviceName + '-icon.png'; };");
        js("eskimoNodesConfig.getServicesDependencies = function () { return {}; };");
        js("eskimoNodesConfig.isServiceUnique = function (serviceName){ " +
                "return (serviceName == 'mesos-master' " +
                "    || serviceName == 'zookeeper' " +
                "    || serviceName == 'grafana' " +
                "    || serviceName == 'kafka-manager' " +
                "    || serviceName == 'spark-console' " +
                "    || serviceName == 'flink-app-master' " +
                "    || serviceName == 'cerebro' " +
                "    || serviceName == 'kibana' " +
                "    || serviceName == 'zeppelin' ); " +
                "};");

        js("window.eskimoSystemStatus = {};");
        js("eskimoSystemStatus.showStatus = function () {};");

        js("window.eskimoKubernetesServicesSelection = {" +
                "showKubernetesServiceSelection: function () {}" +
                "};");

        js("window.eskimoKubernetesOperationsCommand = {" +
                "showCommand : function() {}" +
                "};");

        js("window.eskimoSettingsOperationsCommand = {}");

        js("window.eskimoMain = {};");
        js("eskimoMain.handleSetupCompleted = function (){};");
        js("eskimoMain.getServices = function (){ return eskimoServices; };");
        js("eskimoMain.getMessaging = function (){ return eskimoMessaging; };");
        js("eskimoMain.getOperations = function (){ return eskimoOperations; };");
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
        js("eskimoMain.handleKubernetesSubsystem = function() {}");
        js("eskimoMain.showProgressbar = function() {}");
        js("eskimoMain.isSetupLoaded = function() { return true; }");
        js("eskimoMain.serviceMenuClear = function() { return true; }");
        js("eskimoMain.windowResize = function() {  }");
        js("eskimoMain.hasRole = function(role) { return true; }");

        js("eskimoMain.getSystemStatus = function() { return eskimoSystemStatus; }");

        js("eskimoMain.showOnlyContent = function (content) { " +
                "    $(\".inner-content\").css(\"visibility\", \"hidden\");\n" +
                "    $(\"#inner-content-\" + content).css(\"visibility\", \"visible\");" +
                "    $(\"#inner-content-\" + content).css(\"display\", \"block\");" +
                "}");


        // 3 attempts
        for (int i = 0; i < 3 ; i++) {
            logger.info ("Loading jquery : attempt " + i);
            loadScript("jquery-3.6.0.js");

            waitForDefinition("window.$");

            if (!js("return typeof window.$").toString().equals ("undefined")) {
                break;
            }
        }

        waitForDefinition("$.fn");

        // override jquery load
        js("$.fn._internalLoad = $.fn.load;");
        js("$.fn.load = function (resource, callback) { return this._internalLoad ('../../../src/main/webapp/'+resource, callback); };");
        //js("$.fn.load = function (resource, callback) { return this._internalLoad ('file://" + System.getProperty("user.dir") + "/src/main/webapp/'+resource, callback); };");
    }

    Object js (String jsCode) {
        JavascriptExecutor js = (JavascriptExecutor)driver;
        Object result = js.executeScript (jsCode);

        closeAlertIfAny();

        return result;
    }

    void closeAlertIfAny() {
        try {

            //Switch to alert
            Alert alert = driver.switchTo().alert();

            //Capture text on alert window
            String alertMessage = alert.getText();
            logger.info ("DRIVER ALERT : " + alertMessage);

            //Close alert window
            alert.accept();

        } catch (NoAlertPresentException e) {
            // ignore
        } catch (UnhandledAlertException e) {
            logger.error (e.getMessage());
        }
    }

    WebElement getElementBy (By by) {
        try {
            return driver.findElement(by);
        } catch (org.openqa.selenium.NoSuchElementException e) {
            logger.debug (e.getMessage());
            return null;
        }
    }

    WebElement getElementById (String elementId) {
        try {
            return driver.findElement(By.id(elementId));
        } catch (org.openqa.selenium.NoSuchElementException e) {
            logger.debug (e.getMessage());
            return null;
        }
    }

    protected static boolean isCoverageRun() {
        //return true;
        return jsCoverageFlagFile.exists();
    }

    protected final void loadScript (String script) {
        js("loadScript('http://localhost:9001/src/main/webapp/scripts/"+script+"')");
        try {
            waitForElementInDOM(By.cssSelector("script[src=\"http://localhost:9001/src/main/webapp/scripts/" + script + "\"]"));
            Thread.sleep(20);
        } catch (InterruptedException e) {
            logger.debug(e, e);
        }
    }

    protected void assertAttrValue(String selector, String attribute, String value) {
        assertEquals (value, js("return $('"+selector+"').attr('"+attribute+"')"));
    }

    protected void assertCssValue(String selector, String attribute, String value) {
        assertEquals (value, js("return $('"+selector+"').css('"+attribute+"')"));
    }

    protected void assertJavascriptEquals(String value, String javascript) {
        assertEquals (value, js("return " + javascript).toString());
    }

    protected void assertJavascriptNull(String javascript) {
        assertNull (js("return " + javascript));
    }

    protected void assertTagName(String elementId, String tagName) {
        assertEquals (tagName, getElementById(elementId).getTagName());
    }

    protected void waitForElementInDOM(By by) throws InterruptedException{

        // FIXME I have failing tests with Awaitility !?!
        /*
        await().atMost(MAX_WAIT_TIME_SECS * (isCoverageRun() ? 2 : 1) , TimeUnit.SECONDS).until(
                () -> page.getElementById(elementId) != null);
        */

        int attempt = 0;
        while (getElementBy(by) == null && attempt < MAX_WAIT_RETRIES) {

            /*
            if (attempt > 20) {
                System.err.println (page.asXml());
                System.exit(-1);
            }
            */

            Thread.sleep(INCREMENTAL_WAIT_MS);
            attempt++;
        }
    }

    protected void waitForElementIdInDOM(String elementId) throws InterruptedException {

        // FIXME I have failing tests with Awaitility !?!
        /*
        await().atMost(MAX_WAIT_TIME_SECS * (isCoverageRun() ? 2 : 1) , TimeUnit.SECONDS).until(
                () -> page.getElementById(elementId) != null);
        */

        int attempt = 0;
        while (getElementById(elementId) == null && attempt < MAX_WAIT_RETRIES) {

            /*
            if (attempt > 20) {
                System.err.println (page.asXml());
                System.exit(-1);
            }
            */

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
        while (js("return typeof " + varName).toString().equals ("undefined") && attempt < MAX_WAIT_RETRIES) {
            Thread.sleep(INCREMENTAL_WAIT_MS);
            attempt++;
        }
    }
}

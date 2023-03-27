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

import ch.niceideas.common.exceptions.CommonBusinessException;
import ch.niceideas.eskimo.html.infra.TestResourcesServer;
import ch.niceideas.eskimo.utils.ActiveWaiter;
import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.bonigarcia.wdm.config.WebDriverManagerException;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v106.emulation.Emulation;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractWebTest {

    private static final Logger logger = Logger.getLogger(AbstractWebTest.class);

    private static final File jsCoverageFlagFile = new File("target/jsCoverageFlag");

    private static String className = null;

    private static TestResourcesServer server;

    protected static WebDriver driver;

    @BeforeAll
    public static void setUpOnce() throws Exception {
        server = TestResourcesServer.getServer(isCoverageRun());
        server.startServer(className);

        driver = buildSeleniumDriver();

        driver.get("http://localhost:" + TestResourcesServer.LOCAL_TEST_SERVER_PORT + "/src/test/resources/GenericTestPage.html");
    }

    public static String findVendorLib (String libName) {
        File vendorDir = new File ("./src/main/webapp/scripts/vendor");
        assertNotNull(vendorDir);
        assertTrue (vendorDir.exists());
        String foundLibName = Arrays.stream(Objects.requireNonNull(vendorDir.list()))
                .filter(fileName -> fileName.contains(libName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No lib found with name " + libName));
        return "vendor/" + foundLibName;
    }

    public static WebDriver buildSeleniumDriver() throws InterruptedException, CommonBusinessException {
        ChromeOptions co = new ChromeOptions();

        co.setCapability(CapabilityType.UNHANDLED_PROMPT_BEHAVIOUR, "ignore");

        co.addArguments("--no-sandbox");
        co.addArguments("--window-position=0,0");
        co.addArguments("--window-size=1900,1024");
        co.addArguments("--remote-allow-origins=*");
        co.addArguments("--headless");
        co.addArguments("--disable-gpu");

        File resolutionCachePath = new File("/tmp/eskimo-selenium-resolution-cache");
        if (!resolutionCachePath.exists()) {
            if (!resolutionCachePath.mkdirs()) {
                throw new CommonBusinessException("Couldn't create selenium resolution cache path " + resolutionCachePath);
            }
        }

        File driverCachePath = new File("/tmp/eskimo-selenium-driver-cache");
        if (!driverCachePath.exists()) {
            if (!driverCachePath.mkdirs()) {
                throw new CommonBusinessException("Couldn't create selenium driver cache path " + driverCachePath);
            }
        }

        WebDriver driver;
        for (int i = 0; ; i++) { // 10 attempts
            try {
                driver = WebDriverManager.chromedriver()
                        .capabilities(co)
                        .resolutionCachePath(resolutionCachePath.getAbsolutePath())
                        .cachePath(driverCachePath.getAbsolutePath())
                        .create();
                break;
            } catch (WebDriverManagerException e) {
                if (i < 10) {
                    logger.error (e, e);
                    //noinspection BusyWait
                    Thread.sleep (200);
                } else {
                    throw new CommonBusinessException(e);
                }
            }
        }

        DevTools devTools = ((ChromeDriver) driver).getDevTools();
        devTools.createSession();
        devTools.send(Emulation.setTimezoneOverride("Europe/Zurich"));

        return driver;
    }

    protected static void logConsoleLogs() {
        LogEntries logEntries = driver.manage().logs().get(LogType.BROWSER);
        for (LogEntry entry : logEntries) {
            switch (entry.getLevel().intValue()) {
                case 1000: //Level.SEVERE:
                    logger.error(entry.getMessage());
                    break;
                case 900: //Level.WARNING:
                    logger.warn(entry.getMessage());
                    break;
                case 800: // Level.INFO:
                    logger.info(entry.getMessage());
                    break;
                case 500: //Level.FINE:
                case 400: //Level.FINER:
                    logger.debug(entry.getMessage());
                    break;
                default:
                    logger.info(entry.getLevel() + " " + entry.getMessage());
            }
        }
    }

    private static boolean hasQuit(WebDriver driver) {
        try {
            if (driver == null) {
                return true;
            }
            driver.getTitle();
            return false;
        } catch (WebDriverException e) {
            return true;
        }
    }

    @AfterAll
    public static void tearDownOnce() throws Exception {
        server.stopServer();

        if (driver != null) {
            driver.quit();
        }

        // give some time to selenium driver to really shutdown before running next test
        ActiveWaiter.wait(() ->hasQuit(driver), 2000);
        Thread.sleep(100); // give it even a little more time
    }

    @BeforeEach
    public void init() throws Exception {

        Class<?> clazz = this.getClass(); //if you want to get Class object
        className = clazz.getCanonicalName(); //you want to get only class name

        driver.navigate().refresh();

        // wait for page to load
        Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(100));
        wait.until(innerDriver -> String
                .valueOf(((JavascriptExecutor) innerDriver).executeScript("return document.readyState"))
                .equals("complete"));

        assertEquals("Generic Test Page", driver.getTitle());

        // create common mocks
        initDriver();
    }

    @AfterEach
    public void tearDown() throws Exception {
        logConsoleLogs();
        server.postTestMethodHook(this::js);
    }

    private void initDriver() {
        // create mock functions
        js("window.eskimoServices = {};");
        js("eskimoServices.handleServiceHiding = function (){};");
        js("eskimoServices.serviceMenuServiceFoundHook = function (){};");
        js("eskimoServices.getServiceIcon = function (service) { return service + '-icon.png'; };");
        js("eskimoServices.isServiceAvailable = function (){ return true; };");
        js("eskimoServices.initialize = function (){};");

        js("window.eskimoKubernetesServicesConfig = {}");

        js("window.eskimoConsoles = {}");
        js("eskimoConsoles.setAvailableNodes = function () {};");

        js("window.eskimoServicesSelection = {" +
                "  showServiceSelection: function(nodeNbr, onServicesSelectedForNode, isRange) { window.showServiceSelectionCalled = nodeNbr + '-' + typeof (onServicesSelectedForNode) + '-' + isRange; }" +
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
        js("eskimoSetup.showSetupMessage = function (message, success) {window.setupMessage = message; window.setupStatus = success;};");
        js("eskimoSetup.loadSetup = function () {};");


        js("window.eskimoSetupCommand = {}");
        js("window.eskimoAlert = {" +
                "showAlert : function (level, message) {window.lastAlert = level + \" : \" + message} }");
        js("window.eskimoApp = {}");
        js("window.eskimoAbout = {}");
        js("window.eskimoMenu = { createServicesMenu: function() {}, serviceMenuClear: function(){} }");

        js("window.eskimoFileManagers = {};");
        js("eskimoFileManagers.setAvailableNodes = function() {};");

        js("window.eskimoKubernetesServicesSelection = function(){ this.initialize = function(){}; };");

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
        js("eskimoSystemStatus.serviceIsUp = function() {return true;}");
        js("eskimoSystemStatus.updateStatus = function () {};");
        js("eskimoSystemStatus.isDisconnected = function () {return false; };");

        js("window.eskimoKubernetesServicesSelection = {" +
                "showKubernetesServiceSelection: function () {}" +
                "};");

        js("window.eskimoKubernetesOperationsCommand = {" +
                "showCommand : function() {}" +
                "};");

        js("window.eskimoSettingsOperationsCommand = {}");

        js("window.eskimoMain = {\n"+
                "    confirm: function (message, callback) { callback() },\n" +
                "    adaptMenuToUserRole : function (){},\n"+
                "    handleSetupCompleted : function (){},\n"+
                "    getServices : function (){ return eskimoServices; },\n"+
                "    getMessaging : function (){ return eskimoMessaging; },\n"+
                "    getOperations : function (){ return eskimoOperations; },\n"+
                "    getFileManagers : function (){ return eskimoFileManagers; },\n"+
                "    getConsoles : function (){ return eskimoConsoles; },\n"+
                "    getNodesConfig : function () { return eskimoNodesConfig; },\n"+
                "    isOperationInProgress : function() { return false; },\n"+
                "    setAvailableNodes : function () {},\n"+
                "    menuResize : function () {},\n"+
                "    isSetupDone : function () { return true; },\n"+
                "    hideProgressbar : function () { window.hideProgressbarCalled = true;},\n"+
                "    isCurrentDisplayedScreen : function () { return false; },\n"+
                "    setSetupLoaded : function () {},\n"+
                "    startOperationInProgress : function() { window.startOperationInProgessCalled = true; },\n"+
                "    scheduleStopOperationInProgress : function() { window.scheduleStopOperationInProgress = true; },\n"+
                "    handleKubernetesSubsystem : function() {},\n"+
                "    showProgressbar : function() {},\n"+
                "    isSetupLoaded : function() { return true; },\n"+
                "    serviceMenuClear : function() { return true; },\n"+
                "    windowResize : function() {  },\n"+
                "    showSetupNotDone : function() {  },\n"+
                "    hasRole : function(role) { return true; },\n"+
                "    alert : function(level, message) { alert(level + ' : ' + message); window.lastAlert = level + ' : ' + message; },\n" +
                "    handleSetupNotCompleted: function() { window.handleSetupNotCompletedCalled = true; }"+
                "}");

        js("window.ESKIMO_ALERT_LEVEL = {ERROR: 3, WARNING: 2, INFO: 1}");

        js("eskimoMain.getSystemStatus = function() { return eskimoSystemStatus; }");

        js("eskimoMain.showOnlyContent = function (content) { " +
                "    $(\".inner-content\").css(\"visibility\", \"hidden\");\n" +
                "    $(\"#inner-content-\" + content).css(\"visibility\", \"visible\");" +
                "    $(\"#inner-content-\" + content).css(\"display\", \"block\");" +
                "}");


        // 3 attempts
        for (int i = 0; i < 3 ; i++) {
            logger.info ("Loading jquery : attempt " + i);
            loadScript(findVendorLib("jquery"));

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
        logConsoleLogs();
        closeAlertIfAny();
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
        logConsoleLogs();
        try {
            return driver.findElement(by);
        } catch (org.openqa.selenium.NoSuchElementException e) {
            logger.debug (e.getMessage());
            return null;
        }
    }

    WebElement getElementById (String elementId) {
        return getElementBy (By.id(elementId));
    }

    protected static boolean isCoverageRun() {
        //return true;
        return jsCoverageFlagFile.exists();
    }

    protected final void loadScript (String script) {
        js("loadScript('http://localhost:" + TestResourcesServer.LOCAL_TEST_SERVER_PORT + "/src/main/webapp/scripts/"+script+"')");
        try {
            waitForElementInDOM(By.cssSelector("script[src=\"http://localhost:" + TestResourcesServer.LOCAL_TEST_SERVER_PORT + "/src/main/webapp/scripts/" + script + "\"]"));
            Thread.sleep(20); // give it some more time to actually load script elements
        } catch (InterruptedException e) {
            logger.debug(e, e);
        }
    }

    protected void assertClassContains(String expectedValue, String selector) {
        assertAttrContains(expectedValue, selector, "class");
    }

    protected void assertAttrContains(String expectedValue, String selector, String attribute) {
        logConsoleLogs();
        String cssValue = js("return $('"+selector+"').attr('"+attribute+"')").toString();
        assertTrue (cssValue.contains(expectedValue));
    }

    protected void assertClassEquals(String expectedValue, String selector) {
        assertAttrEquals(expectedValue, selector, "class");
    }

    protected void assertAttrEquals(String expectedValue, String selector, String attribute) {
        logConsoleLogs();
        assertEquals (expectedValue, js("return $('"+selector+"').attr('"+attribute+"')"));
    }

    protected void assertCssEquals(String expectedValue, String selector, String attribute) {
        logConsoleLogs();
        assertEquals (expectedValue, js("return $('"+selector+"').css('"+attribute+"')"));
    }

    protected void assertJavascriptEquals(String expectedValue, String javascript) {
        logConsoleLogs();
        Object jsResult = Optional.ofNullable(js("return " + javascript))
                .orElseThrow(() -> new NullPointerException("javascript execution returned null value."));
        assertEquals (expectedValue, jsResult.toString());
    }

    protected void assertJavascriptNull(String javascript) {
        logConsoleLogs();
        assertNull (js("return " + javascript));
    }

    protected void assertTagNameEquals(String tagName, String elementId) {
        logConsoleLogs();
        assertEquals (tagName, getElementById(elementId).getTagName());
    }

    protected void waitForElementInDOM(By by) {
        ActiveWaiter.wait(() -> getElementBy(by) != null);
        logConsoleLogs();
    }

    protected void waitForElementIdInDOM(String elementId) {
        ActiveWaiter.wait(() -> getElementById(elementId) != null);
        logConsoleLogs();
    }

    protected void waitForDefinition(String varName) {
        ActiveWaiter.wait(() -> !js("return typeof " + varName).toString().equals ("undefined"));
        logConsoleLogs();
    }
}

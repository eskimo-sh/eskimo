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


package ch.niceideas.eskimo.html.screenshotgen;

import ch.niceideas.common.exceptions.CommonBusinessException;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.html.AbstractWebTest;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.eskimo.utils.ActiveWaiter;
import org.apache.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;

public class ScreenshotGenerator {

    private static final Logger logger = Logger.getLogger(ScreenshotGenerator.class);

    public static void main (String[] args) {

        // FIXME shiuld take folder where to put the screenshots in argument

        if (args.length != 2) {
            logger.error ("Expecting 'target Eskimo URL' as first argument and 'destination folder for screenshots' as second argument");
            System.exit (1);
        }

        String targetEskimoUrl = args[0];
        if (StringUtils.isBlank(targetEskimoUrl)) {
            logger.error ("Expecting 'target Eskimo URL' as argument");
            System.exit (2);
        }

        String targetScreenshotFolder = args[1];
        if (StringUtils.isBlank(targetScreenshotFolder)) {
            logger.error ("Expecting 'target screenshot folder' as argument");
            System.exit (3);
        }

        WebDriver driver = null;
        try {
            driver = AbstractWebTest.buildSeleniumDriver();

            driver.get(targetEskimoUrl);

            login(driver);

            initInfrastructure (driver);


            screenshotsGrafana(driver, targetScreenshotFolder);

            /*

            screenshotsGluster(driver, targetScreenshotFolder);

            screenshotsKubeDashboard(driver, targetScreenshotFolder);

            screenshotsKafkaManager(driver, targetScreenshotFolder);

            screenshotsSparkConsole(driver, targetScreenshotFolder);

            screenshotsFlinkDashboard(driver, targetScreenshotFolder);

            screenshotsCerebro(driver, targetScreenshotFolder);


            screenshotsKibana(driver, targetScreenshotFolder);

            screenshotsZeppelin(driver, targetScreenshotFolder);

            screenshotsConsole(driver, targetScreenshotFolder);

            screenshotsFileManager(driver, targetScreenshotFolder);
            */

            Thread.sleep (10000);

            screenshotsStatus(driver, targetScreenshotFolder);

            screenshotsSetup(driver, targetScreenshotFolder);

            screenshotsServicesConfig(driver, targetScreenshotFolder);

            screenshotsNodesConfig(driver, targetScreenshotFolder);


            logger.info (" - TEMP HACK : waiting");
            Thread.sleep (100000);

        } catch (InterruptedException| CommonBusinessException | IOException | FileUtils.FileDeleteFailedException e) {
            logger.error (e, e);
            System.exit (2);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private static void initInfrastructure(WebDriver driver) {

        JavascriptExecutor jsExec = (JavascriptExecutor) driver;
        jsExec.executeScript("window.resizeDone = true");
        jsExec.executeScript("$(window).resize(function() {\n" +
                "    window.resizeDone = false;\n" +
                "    clearTimeout(window.resizedFinished);\n" +
                "    window.resizedFinished = setTimeout(function(){\n" +
                "        window.resizeDone = true;\n" +
                "    }, 500);\n" +
                "});");
    }

    private static void screenshotsNodesConfig(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Nodes Config");

        reachService(driver, "main-menu-show-nodes-config-link", null, "inner-content-nodes-config");

        handleScreenshots(driver, targetScreenshotFolder, "nodes-config");
    }

    private static void screenshotsServicesConfig(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Services Config");

        reachService(driver, "main-menu-show-services-settings-link", null, "inner-content-services-settings");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[href='#collapse-gluster']")));
        driver.findElement(By.cssSelector("a[href='#collapse-gluster']")).click();

        wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("gluster-target---volumes")));

        handleScreenshots(driver, targetScreenshotFolder, "services-config");
    }

    private static void screenshotsSetup(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Setup");

        reachService(driver, "main-menu-show-setup-link", null, "inner-content-setup");

        handleScreenshots(driver, targetScreenshotFolder, "setup");
    }

    private static void screenshotsStatus(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Status");

        reachService(driver, "main-menu-show-status-link", null, "inner-content-status");

        handleScreenshots(driver, targetScreenshotFolder, "status");
    }

    private static void screenshotsFileManager(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - File Manager");

        reachService(driver, "main-menu-show-file-managers-link", "folderMenuFileManagers", "inner-content-file-managers");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("#file-managers-management div.btn-group button.dropdown-toggle")));
        driver.findElement(By.cssSelector("#file-managers-management div.btn-group button.dropdown-toggle")).click();

        wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.elementToBeClickable(By.id("file_manager_open_192-168-56-51")));

        driver.findElement(By.id("file_manager_open_192-168-56-51")).click();

        wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("file-manager-close-192-168-56-51")));

        handleScreenshots(driver, targetScreenshotFolder, "file-manager");
    }

    private static void screenshotsConsole(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Console");

        reachService(driver, "main-menu-show-consoles-link", "folderMenuConsoles", "inner-content-consoles");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("#consoles-management div.btn-group button.dropdown-toggle")));
        driver.findElement(By.cssSelector("#consoles-management div.btn-group button.dropdown-toggle")).click();

        wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.elementToBeClickable(By.id("console_open_192-168-56-51")));

        driver.findElement(By.id("console_open_192-168-56-51")).click();

        wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("console-close-192-168-56-51")));

        handleScreenshots(driver, targetScreenshotFolder, "console");
    }

    private static void screenshotsZeppelin(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Zeppelin");

        // reach proper place
        reachService(driver, "services-menu_zeppelin", "folderMenuZeppelin", "iframe-content-zeppelin");

        driver.switchTo().frame("iframe-content-zeppelin");

        // Show Spark SQL notebook
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("a[href=\"#/notebook/2HS3PQPV6\"]")));
        driver.findElement(By.cssSelector("a[href=\"#/notebook/2HS3PQPV6\"]")).click();

        Thread.sleep(5000);

        JavascriptExecutor jsExec = (JavascriptExecutor) driver;
        jsExec.executeScript("window.scrollBy(0, 670)");

        // selecting proper chart types
        JavascriptExecutor js = (JavascriptExecutor)driver;
        String chartSelectDivs = js.executeScript("window.eskCharts = ''; $('.result-chart-selector').each(function(cnt, el) {window.eskCharts += ($(el).attr('id') + ' '); }); return window.eskCharts").toString();
        //logger.info(chartSelectDivs);
        String[] divIds = chartSelectDivs.split(" ");

        driver.findElement(By.id(divIds[0])).findElement(By.cssSelector("button.btn-default[uib-tooltip=\"Pie Chart\"]")).click();

        driver.findElement(By.id(divIds[1])).findElement(By.cssSelector("button.btn-default[uib-tooltip=\"Bar Chart\"]")).click();

        driver.findElement(By.id(divIds[2])).findElement(By.cssSelector("button.btn-default[uib-tooltip=\"Line Chart\"]")).click();

        Thread.sleep(2000);

        driver.switchTo().parentFrame();

        handleScreenshots(driver, targetScreenshotFolder, "zeppelin");
    }

    private static void screenshotsKibana(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Kibana");

        // reach proper place
        reachService(driver, "services-menu_kibana", "folderMenuKibana", "iframe-content-kibana");

        driver.switchTo().frame("iframe-content-kibana");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.header__toggleNavButtonSection")));

        // dismiss alerts
        try {
            driver.findElement(By.cssSelector("button[data-test-subj=\"dismissAlertButton\"]")).click();
        } catch (NoSuchElementException e) {
            logger.debug (e.getMessage());
        }

        try {
            driver.findElement(By.cssSelector("button#mute.euiButton")).click();
        } catch (NoSuchElementException e) {
            logger.debug (e.getMessage());
        }

        // click on menu toggle
        driver.findElement(By.cssSelector("div.header__toggleNavButtonSection button.euiHeaderSectionItemButton")).click();

        // CLick on Dashboard
        wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("span.euiListGroupItem__label[title=\"Dashboard\"]")));
        driver.findElement(By.cssSelector("span.euiListGroupItem__label[title=\"Dashboard\"]")).click();

        wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("a[data-test-subj=\"dashboardListingTitleLink-berka-transactions\"]")));

        // Open berka dashboard
        driver.findElement(By.cssSelector("a[data-test-subj=\"dashboardListingTitleLink-berka-transactions\"]")).click();

        JavascriptExecutor js = (JavascriptExecutor)driver;
        ActiveWaiter.wait(() -> js.executeScript("return $(\"div.legacyMtrVis__value:contains('1,056,320')\").length").toString().equals("1"), 6000);

        Thread.sleep(4000);

        driver.switchTo().parentFrame();

        handleScreenshots(driver, targetScreenshotFolder, "kibana");
    }

    private static void screenshotsCerebro(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Cerebro");

        // reach proper place
        reachService(driver, "services-menu_cerebro", "folderMenuCerebro", "iframe-content-cerebro");

        driver.switchTo().frame("iframe-content-cerebro");

        JavascriptExecutor js = (JavascriptExecutor)driver;
        ActiveWaiter.wait(() -> js.executeScript("return $(\"span.stat-value:contains('eskimo')\").length").toString().equals("1"), 6000);

        driver.switchTo().parentFrame();

        handleScreenshots(driver, targetScreenshotFolder, "cerebro");
    }

    private static void screenshotsFlinkDashboard(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Flink Runtime");

        // reach proper place
        reachService(driver, "services-menu_flink-runtime", "folderMenuFlinkRuntime", "iframe-content-flink-runtime");

        driver.switchTo().frame("iframe-content-flink-runtime");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("nz-divider.ant-divider-vertical")));

        driver.switchTo().parentFrame();

        handleScreenshots(driver, targetScreenshotFolder, "flink-runtime");
    }

    private static void screenshotsSparkConsole(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Spark Console");

        // reach proper place
        reachService(driver, "services-menu_spark-console", "folderMenuSparkConsole", "iframe-content-spark-console");

        driver.switchTo().frame("iframe-content-spark-console");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("history-summary-table_wrapper")));

        driver.switchTo().parentFrame();

        handleScreenshots(driver, targetScreenshotFolder, "spark-console");
    }

    private static void screenshotsKafkaManager(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Kafka Manager");

        // reach proper place
        reachService(driver, "services-menu_kafka-manager", "folderMenuKafkaManager", "iframe-content-kafka-manager");

        driver.switchTo().frame("iframe-content-kafka-manager");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("li.breadcrumb-item")));

        driver.findElement(By.cssSelector("a[href='clusters/Eskimo']")).click();

        JavascriptExecutor js = (JavascriptExecutor)driver;
        ActiveWaiter.wait(() -> js.executeScript("return $(\"td b:contains('Version')\").length").toString().equals("1"), 6000);

        driver.switchTo().parentFrame();

        handleScreenshots(driver, targetScreenshotFolder, "kafka-manager");
    }

    private static void screenshotsKubeDashboard(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Kube Dashboard");

        // reach proper place
        reachService(driver, "services-menu_kubernetes-dashboard", "folderMenuKubernetesDashboard", "iframe-content-kubernetes-dashboard");

        driver.switchTo().frame("iframe-content-kubernetes-dashboard");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("kubernetes-logo-white")));

        driver.switchTo().parentFrame();

        handleScreenshots(driver, targetScreenshotFolder, "kubernetes-dashboard");
    }

    private static void screenshotsGluster(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Gluster");

        // reach proper place
        reachService(driver, "services-menu_gluster", "folderMenuGluster", "iframe-content-gluster");

        driver.switchTo().frame("iframe-content-gluster");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("status-volume-container-table")));

        driver.switchTo().parentFrame();

        handleScreenshots(driver, targetScreenshotFolder, "gluster");
    }

    private static void screenshotsGrafana(WebDriver driver, String targetScreenshotFolder)
            throws IOException, FileUtils.FileDeleteFailedException, InterruptedException {

        logger.info (" - Grafana");

        // reach proper place
        reachService(driver, "services-menu_grafana", "folderMenuGrafana", "iframe-content-grafana");

        driver.switchTo().frame("iframe-content-grafana");

        JavascriptExecutor js = (JavascriptExecutor)driver;
        js.executeScript ("window.location.href = '/grafana/d/C9M0YVnWk/eskimo-nodes-system-monitoring?orgId=1'");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("button.dashboard-row__title")));

        driver.switchTo().parentFrame();

        handleScreenshots(driver, targetScreenshotFolder, "grafana");
    }

    private static void reachService(WebDriver driver, String menuLinkId, String menuLinkWrapperId, String contentId) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(20000));
        wait.until(ExpectedConditions.elementToBeClickable(By.id(menuLinkId)));

        if (StringUtils.isNotBlank(menuLinkWrapperId)) {
            wait.until(ExpectedConditions.not(ExpectedConditions.attributeContains(By.id(menuLinkWrapperId), "class", "disabled")));
        }

        driver.findElement(By.id(menuLinkId)).click();

        wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(contentId)));
    }

    private static void handleScreenshots(WebDriver driver, String targetScreenshotFolder, String system) throws InterruptedException, IOException, FileUtils.FileDeleteFailedException {
        driver.manage().window().setSize(new Dimension(1900,1024));
        Thread.sleep (200);

        takeScreenshot((TakesScreenshot) driver, targetScreenshotFolder, system + "-wide.png");

        driver.manage().window().setSize(new Dimension(960,600));

        JavascriptExecutor js = (JavascriptExecutor)driver;
        ActiveWaiter.wait(() -> js.executeScript("return window.resizeDone;").equals(true));

        takeScreenshot((TakesScreenshot) driver, targetScreenshotFolder, system + "-small.png");

        driver.manage().window().setSize(new Dimension(1280,720));

        ActiveWaiter.wait(() -> js.executeScript("return window.resizeDone;").equals(true));

        driver.findElement(By.cssSelector("button.button-toggle-menu")).click();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(20000));
        wait.until(ExpectedConditions.attributeToBe(By.cssSelector("html"), "data-sidenav-size", "condensed"));

        takeScreenshot((TakesScreenshot) driver, targetScreenshotFolder, system + "-medium-condensed.png");

        driver.findElement(By.cssSelector("button.button-toggle-menu")).click();
        wait = new WebDriverWait(driver, Duration.ofMillis(20000));
        wait.until(ExpectedConditions.attributeToBe(By.cssSelector("html"), "data-sidenav-size", "default"));

        takeScreenshot((TakesScreenshot) driver, targetScreenshotFolder, system + "-medium.png");

        driver.manage().window().setSize(new Dimension(1900,1024));
        ActiveWaiter.wait(() -> js.executeScript("return window.resizeDone;").equals(true));

        driver.findElement(By.cssSelector("button.button-toggle-menu")).click();
        wait = new WebDriverWait(driver, Duration.ofMillis(20000));
        wait.until(ExpectedConditions.attributeToBe(By.cssSelector("html"), "data-sidenav-size", "condensed"));

        takeScreenshot((TakesScreenshot) driver, targetScreenshotFolder, system + "-wide-condensed.png");

        driver.findElement(By.cssSelector("button.button-toggle-menu")).click();
        wait = new WebDriverWait(driver, Duration.ofMillis(20000));
        wait.until(ExpectedConditions.attributeToBe(By.cssSelector("html"), "data-sidenav-size", "default"));

    }

    private static void takeScreenshot(TakesScreenshot driver, String targetScreenshotFolder, String filename) throws IOException, FileUtils.FileDeleteFailedException {
        File file = driver.getScreenshotAs(OutputType.FILE);
        File destFile = new File(targetScreenshotFolder + "/" + filename);

        FileUtils.copy (file, destFile);
        FileUtils.delete(file);
    }

    private static void login(WebDriver driver) {
        logger.info (" - Login");

        driver.findElement(By.id("eskimo-username")).sendKeys("admin");
        driver.findElement(By.id("eskimo-password")).sendKeys("password");

        driver.findElement(By.cssSelector("button.btn-info")).click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("show-all-nodes-btn")));
    }
}

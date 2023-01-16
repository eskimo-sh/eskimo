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


package ch.niceideas.eskimo.html.screenshotgen;

import ch.niceideas.common.exceptions.CommonBusinessException;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.html.AbstractWebTest;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

public class ScreenshotGenerator {

    private static final Logger logger = Logger.getLogger(ScreenshotGenerator.class);

    public static void main (String[] args) {

        // FIXME shiuld take folder where to put the screenshots in argument

        if (args.length != 1) {
            logger.error ("Expecting target Eskimo URL as argument");
            System.exit (1);
        }

        String targetEskimoUrl = args[0];
        if (StringUtils.isBlank(targetEskimoUrl)) {
            logger.error ("Expecting target Eskimo URL as argument");
            System.exit (1);
        }

        WebDriver driver = null;
        try {
            driver = AbstractWebTest.buildSeleniumDriver();

            driver.get(targetEskimoUrl);

            logger.info (" - Login");
            login(driver);

            logger.info (" - Grafana");
            screenshotsGrafana(driver);



            logger.info ("- TEMP HACK : waiting");
            Thread.sleep (100000);

        } catch (InterruptedException| CommonBusinessException | IOException e) {
            logger.error (e, e);
            System.exit (2);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

    }

    private static void screenshotsGrafana(WebDriver driver) throws IOException {

        // reach proper place
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(20000));
        wait.until(ExpectedConditions.elementToBeClickable(By.id("services-menu_grafana")));

        wait.until(ExpectedConditions.not (ExpectedConditions.attributeContains(By.id("folderMenuGrafana"), "class", "disabled")));

        driver.findElement(By.id("services-menu_grafana")).click();

        wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("iframe-content-grafana")));

        driver.switchTo().frame("iframe-content-grafana");

        JavascriptExecutor js = (JavascriptExecutor)driver;
        Object result = js.executeScript ("window.location.href = '/grafana/d/C9M0YVnWk/eskimo-nodes-system-monitoring?orgId=1'");

        wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("button.dashboard-row__title pointer")));

        /*
        // screenshots
        File file = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        File destFile = new File(fileWithPath);
//Copy file at destination
        FileUtils.copyFile (file, destFile);
        */
    }

    private static void login(WebDriver driver) {

        driver.findElement(By.id("eskimo-username")).sendKeys("admin");
        driver.findElement(By.id("eskimo-password")).sendKeys("password");

        driver.findElement(By.cssSelector("button.btn-info")).click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(10000));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("show-all-nodes-btn")));
    }
}

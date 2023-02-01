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

import ch.niceideas.eskimo.utils.ActiveWaiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class EskimoAlertTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript("vendor/bootstrap-5.2.0.js");

        loadScript("eskimoUtils.js");
        loadScript("eskimoAlert.js");

        waitForDefinition("window.eskimo");
        waitForDefinition("window.eskimo.Alert");

        js("eskimoAlert = new eskimo.Alert();");
        js("eskimoAlert.initialize()");

        waitForElementIdInDOM("alert-body");

        // force hide it
        js("$('#alert-modal').modal('show');");
        Thread.sleep(200); // unfortunately need this one
        ActiveWaiter.wait(() -> js("return $('#alert-modal').css('display')").equals("block"));
        assertCssValue("#alert-modal", "display", "block");

        js("$('#alert-modal').modal('hide');");
        Thread.sleep(200); // unfortunately need this one
        ActiveWaiter.wait(() -> js("return $('#alert-modal').css('display')").equals("none"));
        assertCssValue("#alert-modal", "display", "none");
    }

    @Test
    public void testUnsupportedLevels() throws Exception {

        js("eskimoAlert.showAlert (0, 'unsuported level 0');");

        ActiveWaiter.wait(() -> !js("return $('#alert-modal').css('display')").equals("none"), 500);
        // shouldn't have worked
        assertCssValue("#alert-modal", "display", "none");

        js("eskimoAlert.showAlert (4, 'unsuported level 4');");

        ActiveWaiter.wait(() -> !js("return $('#alert-modal').css('display')").equals("none"), 500);
        // shouldn't have worked
        assertCssValue("#alert-modal", "display", "none");
    }

    @Test
    public void testConfirm() throws Exception {

        js("eskimoAlert.confirm ('dummy message', " +
                "function() { window.confirmCallbackCalled = \"called\"}, " +
                "function() { window.closeCallbackCalled = \"called\"});");

        ActiveWaiter.wait(() -> js("return $('#alert-modal').css('display')").equals("block"));
        assertCssValue("#alert-modal", "display", "block");

        getElementById("alert-button-cancel").click();

        assertJavascriptEquals("called", "window.closeCallbackCalled ? window.closeCallbackCalled : 'none'");
        assertJavascriptEquals("none", "window.confirmCallbackCalled ? window.confirmCallbackCalled : 'none'");

        js("delete window.closeCallbackCalled");
        js("delete window.confirmCallbackCalled");

        js("eskimoAlert.confirm ('dummy message', " +
                "function() { window.confirmCallbackCalled = \"called\"}, " +
                "function() { window.closeCallbackCalled = \"called\"});");

        getElementById("alert-button-validate").click();

        assertJavascriptEquals("called", "window.closeCallbackCalled ? window.closeCallbackCalled : 'none'");
        assertJavascriptEquals("called", "window.confirmCallbackCalled ? window.confirmCallbackCalled : 'none'");
    }

    @Test
    public void testNominal() throws Exception {

        js("eskimoAlert.showAlert (1, 'test info');");

        ActiveWaiter.wait(() -> js("return $('#alert-modal').css('display')").equals("block"));
        assertCssValue("#alert-modal", "display", "block");

        assertJavascriptEquals("test info", "$('#alert-body').html()");
        assertJavascriptEquals("modal-header", "$('#alert-header').attr('class')");

        js("eskimoAlert.showAlert (2, 'test warning');");

        assertJavascriptEquals("test info<br>test warning", "$('#alert-body').html()");
        assertJavascriptEquals("modal-header bg-warning text-white", "$('#alert-header').attr('class')");

        js("eskimoAlert.showAlert (3, 'test error');");

        assertJavascriptEquals("test info<br>test warning<br>test error", "$('#alert-body').html()");
        assertJavascriptEquals("modal-header bg-danger text-white", "$('#alert-header').attr('class')");

        // lower level doesn't change header style

        js("eskimoAlert.showAlert (2, 'other warning');");
        assertJavascriptEquals("modal-header bg-danger text-white", "$('#alert-header').attr('class')");

        js("eskimoAlert.closeAlert ();");
        ActiveWaiter.wait(() -> js("return $('#alert-modal').css('display')").equals("none"));
        assertCssValue("#alert-modal", "display", "none");
    }

}


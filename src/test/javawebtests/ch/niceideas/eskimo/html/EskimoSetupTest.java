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

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.services.SetupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

public class EskimoSetupTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript("eskimoUtils.js");
        loadScript("eskimoSetup.js");

        waitForDefinition("window.eskimo");
        waitForDefinition("window.eskimo.Setup");

        js("window.errorHandler = function () {};");

        String currentDir = System.getProperty("user.dir");
        System.out.println("Current dir using System:" +currentDir);

        // instantiate test object
        js("eskimoSetup = new eskimo.Setup()");
        js("eskimoSetup.eskimoMain = eskimoMain;");
        js("eskimoSetup.eskimoSystemStatus = eskimoSystemStatus;");
        js("eskimoSetup.eskimoSetupCommand = eskimoSetupCommand;");
        js("eskimoSetup.initialize()");

        waitForElementIdInDOM("reset-setup-btn");
    }

    @Test
    public void testShowSetup() {
        js("eskimoSetup.showSetup()");

        assertCssEquals("visible", "#inner-content-setup", "visibility");
        assertCssEquals("block", "#inner-content-setup", "display");
    }

    @Test
    public void testSaveSetupMessagesAndReset() {

        testShowSetup();

        // add services menu
        getElementById("save-setup-btn").click();

        //System.out.println (page.asXml());

        // should have displayed the error
        assertJavascriptEquals("3 : Configuration Storage path should be set", "window.lastAlert");

        js("$('#setup_storage').val('/tmp/test')");

        getElementById("save-setup-btn").click();

        assertJavascriptEquals("3 : SSH Username to use to reach cluster nodes should be set", "window.lastAlert");

        js("$('#" + SetupService.SSH_USERNAME_FIELD + "').val('eskimo')");

        getElementById("save-setup-btn").click();

        assertJavascriptEquals("3 : SSH Identity Private Key to use to reach cluster nodes should be set", "window.lastAlert");
    }

    @Test
    public void testReset() {

        js("$.ajaxGet = function(callback) { window.calledUrl = callback.url; callback.success ({ \"status\" : \"OK\"}); }");

        testShowSetup();

        getElementById("reset-setup-btn").click();

       assertJavascriptEquals("load-setup", "window.calledUrl");
    }

    @Test
    public void testSaveSetupShowsCommand() throws Exception {

        String setup = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSetupTest/setup.json"), StandardCharsets.UTF_8);

        js("$.ajax = function (callback) { callback.success ( { command: { test: \"test\" }} ); }");

        js("eskimoSetupCommand.showCommand = function (command) { window.setupCommand = command; }");

        js("eskimoSetup.doSaveSetup(" + setup + ")");

        assertJavascriptEquals("{\"test\":\"test\"}", "JSON.stringify (window.setupCommand)");
    }

    @Test
    public void testSaveSetupAlerts() throws Exception {

        String setup = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoSetupTest/setup.json"), StandardCharsets.UTF_8);

        js("$.ajax = function (callback) { callback.success ( { error: btoa(\"test-error\") } ); }");
        js("eskimoSetup.doSaveSetup(" + setup + ")");
        assertJavascriptEquals("3 : test-error", "window.lastAlert");

        js("$.ajax = function (callback) { callback.success ( { dummy: \"dummy\" } ); }");
        js("eskimoSetup.doSaveSetup(" + setup + ")");
        assertJavascriptEquals("3 : Expected pending operations command but got none !", "window.lastAlert");

        js("$.ajax = function (callback) { callback.success ( { command: { none: true }} ); }");
        js("eskimoSetup.doSaveSetup(" + setup + ")");
        assertJavascriptEquals("1 : Configuration applied successfully", "window.lastAlert");
    }

    @Test
    public void testDisableDownloadInSnapshot_EnableInRelease() throws Exception {

        // build
        String setupBuild =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SetupServiceTest/setupConfig.json"), StandardCharsets.UTF_8);

        js("eskimoSetup.handleSetup("+setupBuild+")");

        assertClassEquals("radio-inline disabled", "#setup-kube-origin-download-label");
        assertClassEquals("radio-inline disabled", "#setup-services-origin-download-label");

        JsonWrapper setupWrapper = new JsonWrapper(setupBuild);
        setupWrapper.setValueForPath("isSnapshot", false);

        js("eskimoSetup.handleSetup("+setupWrapper.getFormattedValue()+")");

        assertClassEquals("radio-inline", "#setup-kube-origin-download-label");
        assertClassEquals("radio-inline", "#setup-services-origin-download-label");
    }

    @Test
    public void testHandleSetup() throws Exception {

        String setupConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SetupServiceTest/setupConfig.json"), StandardCharsets.UTF_8);

        js("$.ajaxGet = function(callback) {\n" +
                "    callback.success(" + setupConfig + ");\n" +
                "}");

        js("eskimoSetup.loadSetup(true)");

        assertJavascriptEquals("/tmp/setupConfigTest", "$('#setup_storage').val()");
        assertJavascriptEquals("eskimo", "$('#" + SetupService.SSH_USERNAME_FIELD + "').val()");
        assertJavascriptEquals("ssh_key", "$('#filename-ssh-key').val()");
    }

    @Test
    public void testHandleSetupClear() {

        js("$.ajaxGet = function(callback) {\n" +
                "    callback.success({ version: \"1.0\", clear: \"setup\", message: \"test-message\"});\n" +
                "}");

        js("eskimoSetup.loadSetup(true)");

        assertJavascriptEquals("true", "window.handleSetupNotCompletedCalled");
        assertJavascriptEquals("1 : test-message", "window.lastAlert");
    }

}

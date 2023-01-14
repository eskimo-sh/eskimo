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
    public void testSaveSetupMessages() {

        // add services menu
        js("eskimoSetup.saveSetup()");

        //System.out.println (page.asXml());

        // should have displayed the error
        assertJavascriptEquals ("3 : Configuration Storage path should be set", "window.lastAlert");

        js("$('#setup_storage').val('/tmp/test')");

        js("eskimoSetup.saveSetup()");

        assertJavascriptEquals ("3 : SSH Username to use to reach cluster nodes should be set", "window.lastAlert");

        js("$('#" + SetupService.SSH_USERNAME_FIELD + "').val('eskimo')");

        js("eskimoSetup.saveSetup()");

        assertJavascriptEquals ("3 : SSH Identity Private Key to use to reach cluster nodes should be set", "window.lastAlert");
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
    public void testDisableDownloadInSnapshot_EnableInRelease() throws Exception {

        // build
        String setupBuild =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SetupServiceTest/setupConfig.json"), StandardCharsets.UTF_8);

        js("eskimoSetup.handleSetup("+setupBuild+")");

        assertJavascriptEquals("radio-inline disabled", "$('#setup-kube-origin-download-label').attr('class')");
        assertJavascriptEquals("radio-inline disabled", "$('#setup-services-origin-download-label').attr('class')");

        JsonWrapper setupWrapper = new JsonWrapper(setupBuild);
        setupWrapper.setValueForPath("isSnapshot", false);

        js("eskimoSetup.handleSetup("+setupWrapper.getFormattedValue()+")");

        assertJavascriptEquals("radio-inline", "$('#setup-kube-origin-download-label').attr('class')");
        assertJavascriptEquals("radio-inline", "$('#setup-services-origin-download-label').attr('class')");
    }

    @Test
    public void testHandleSetup() throws Exception {

        String setupConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SetupServiceTest/setupConfig.json"), StandardCharsets.UTF_8);

        js("eskimoSetup.handleSetup("+setupConfig+")");

        assertJavascriptEquals("/tmp/setupConfigTest", "$('#setup_storage').val()");
        assertJavascriptEquals("eskimo", "$('#" + SetupService.SSH_USERNAME_FIELD + "').val()");
        assertJavascriptEquals("ssh_key", "$('#filename-ssh-key').val()");
    }

}

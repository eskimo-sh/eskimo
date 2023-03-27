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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class EskimoServicesSettingsTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript("eskimoUtils.js");
        loadScript("eskimoServicesSettings.js");

        waitForDefinition("window.eskimo");
        waitForDefinition("window.eskimo.ServicesSettings");

        // instantiate test object
        js("eskimoServicesSettings = new eskimo.ServicesSettings()");
        js("eskimoServicesSettings.eskimoMain = eskimoMain");
        js("eskimoServicesSettings.eskimoMessaging = eskimoMessaging");
        js("eskimoServicesSettings.eskimoSettingsOperationsCommand = eskimoSettingsOperationsCommand");
        js("eskimoServicesSettings.eskimoNodesConfig = eskimoNodesConfig");
        js("eskimoServicesSettings.initialize()");

        waitForElementIdInDOM("reset-services-settings-btn");

        String jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testConfig.json"), StandardCharsets.UTF_8);

        js("window.TEST_SERVICE_SETTINGS = " + jsonConfig + ";");

        js("eskimoServicesSettings.setServicesSettingsForTest(TEST_SERVICE_SETTINGS.settings)");

        js("$('#inner-content-services-settings').css('display', 'inherit')");
        js("$('#inner-content-services-settings').css('visibility', 'visible')");
    }

    @Test
    public void testCheckServicesSettings() throws Exception {

        String testFormWithValidation = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testFormWithValidation.json"), StandardCharsets.UTF_8);

        js("eskimoServicesSettings.checkServicesSettings(" + testFormWithValidation + ");");

        JSONObject tamperedForm = new JSONObject(testFormWithValidation);

        tamperedForm.put("broker-socket---send---buffer---bytes", "abc");
        tamperedForm.put("calculator-runtime-calculator---driver---memory", "1024x");

        JavascriptException exp = assertThrows(JavascriptException.class,
                () -> js("eskimoServicesSettings.checkServicesSettings(" + tamperedForm.toString(2) + ");"));

        assertNotNull(exp);

        assertTrue(exp.getMessage().startsWith("javascript error: " +
                "Value abc for broker / socket.send.buffer.bytes doesn't comply to format ^[0-9\\.]+$<br>" +
                "Value 1024x for calculator-runtime / calculator.driver.memory doesn't comply to format ^([0-9\\.]+[kmgt]?)$|^(\\[ESKIMO_DEFAULT\\])$<br>"));
    }

    @Test
    public void testShowServicesSettings() {

        js("$.ajaxGet = function(callback) { console.log(callback); }");

        js("eskimoServicesSettings.showServicesSettings();");

        assertCssEquals("visible", "#inner-content-services-settings", "visibility");
        assertCssEquals("block", "#inner-content-services-settings", "display");
    }

    @Test
    public void testSaveServicesSettingsWithButton() {

        testShowServicesSettings();

        js("$.ajaxPost = function(object) {" +
                "    object.success({status: \"OK\", command: true})" +
                "}");

        js("eskimoSettingsOperationsCommand.showCommand = function () {window.showCommandCalled = true;  }");

        getElementById("save-services-settings-btn").click();

        assertJavascriptEquals("true", "window.showCommandCalled");
    }

    @Test
    public void testLayoutServicesSettings() {

        js("eskimoServicesSettings.layoutServicesSettings();");

        //System.out.println (page.asXml());

        // test a few input values

        assertJavascriptEquals ("false", "$('#database-action---destructive_requires_name').val()");

        assertJavascriptEquals ("", "$('#broker-socket---send---buffer---bytes').val()");

        assertJavascriptEquals ("3", "$('#broker-num---partitions').val()");

        assertJavascriptEquals ("", "$('#calculator-runtime-calculator---rpc---numRetries').val()");

        assertJavascriptEquals ("", "$('#calculator-runtime-calculator---dynamicAllocation---cachedExecutorIdleTimeout').val()");
    }

}

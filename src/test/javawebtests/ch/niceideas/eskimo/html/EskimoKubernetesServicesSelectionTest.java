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
import ch.niceideas.common.utils.StreamUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class EskimoKubernetesServicesSelectionTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript("bootstrap-5.2.0.js");
        loadScript("eskimoUtils.js");
        loadScript("eskimoKubernetesServicesSelection.js");

        js("eskimoKubernetesServicesConfig = {};");

        // leaving zeppelin out intentionally
        js("eskimoKubernetesServicesConfig.getKubernetesServices = function() {return ['cerebro', 'kibana', 'kafka-manager', 'spark-console', 'grafana']};");

        // instantiate test object
        js("eskimoKubernetesServicesSelection = new eskimo.KubernetesServicesSelection();");
        js("eskimoKubernetesServicesSelection.eskimoKubernetesServicesConfig = eskimoKubernetesServicesConfig;");
        js("eskimoKubernetesServicesSelection.initialize();");

        waitForElementIdInDOM("kubernetes-services-selection-body");

        String htmlForm = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoKubernetesServicesSelectionTest/form.html"), StandardCharsets.UTF_8);

        js("window.INNER_FORM = '" + htmlForm.replace("\n", " ").replace("\r", "") + "';");

        js("$('#kubernetes-services-selection-body').html(INNER_FORM);");
    }

    @Test
    public void testNominal() throws Exception {

        // this is just to ensure everything has been properly loaded by setup
        assertNotNull (getElementById("select-all-kubernetes-services-button"));

        js("eskimoKubernetesServicesSelection.showKubernetesServiceSelection()");

        await().atMost(1, TimeUnit.SECONDS).until(() -> js("return $('#kubernetes-services-selection-modal').css('display')").toString().equals ("block"));

        assertCssValue("#kubernetes-services-selection-modal", "display", "block");
        assertCssValue("#kubernetes-services-selection-modal", "visibility", "visible");
    }

    @Test
    public void testClickButtonValidate() throws Exception {

        testNominal();

        js("eskimoKubernetesServicesConfig.proceedWithReinstall = function (reinstallConfig) {" +
                "    window.reinstallConfig = JSON.stringify (reinstallConfig);" +
                "}");

        testSelectAll();

        getElementById("kubernetes-services-select-button-validate").click();

        JSONObject expectedResult = new JSONObject("{" +
                "\"cerebro_reinstall\":\"on\"," +
                "\"grafana_reinstall\":\"on\"," +
                "\"kafka-manager_reinstall\":\"on\"," +
                "\"kibana_reinstall\":\"on\"," +
                "\"spark-console_reinstall\":\"on\"}");

        JSONObject actualResult = new JSONObject((String)js("return window.reinstallConfig"));
        assertTrue(expectedResult.similar(actualResult));
    }

    @Test
    public void testSelectAll() throws Exception {

        js("eskimoKubernetesServicesSelection.kubernetesServicesSelectionSelectAll();");

        assertTrue ((Boolean)js("return $('#cerebro_reinstall').get(0).checked"));
        assertTrue ((Boolean)js("return $('#kibana_reinstall').get(0).checked"));
        assertTrue ((Boolean)js("return $('#kafka-manager_reinstall').get(0).checked"));
        assertTrue ((Boolean)js("return $('#spark-console_reinstall').get(0).checked"));
        assertTrue ((Boolean)js("return $('#grafana_reinstall').get(0).checked"));

        assertFalse ((Boolean)js("return $('#zeppelin_reinstall').get(0).checked"));
    }
}

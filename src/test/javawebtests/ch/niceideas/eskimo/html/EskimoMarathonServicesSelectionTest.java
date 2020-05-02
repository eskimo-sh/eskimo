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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.*;

public class EskimoMarathonServicesSelectionTest extends AbstractWebTest {

    @Before
    public void setUp() throws Exception {

        loadScript(page, "bootstrap.js");
        loadScript(page, "eskimoUtils.js");
        loadScript(page, "eskimoMarathonServicesSelection.js");

        page.executeJavaScript("eskimoMarathonServicesConfig = {};");

        // leaving gdash out intentionally
        page.executeJavaScript("eskimoMarathonServicesConfig.getMarathonServices = function() {return ['cerebro', 'kibana', 'kafka-manager', 'spark-history-server', 'grafana', 'zeppelin']};");


        // instantiate test object
        page.executeJavaScript("eskimoMarathonServicesSelection = new eskimo.MarathonServicesSelection({" +
                "    eskimoMarathonServicesConfig: eskimoMarathonServicesConfig" +
                "});");


        waitForElementIdInDOM("marathon-services-selection-body");

        String htmlForm = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoMarathonServicesSelectionTest/form.html"));

        page.executeJavaScript("INNER_FORM = '" + htmlForm.replace("\n", " ") + "';");

        page.executeJavaScript("$('#marathon-services-selection-body').html(INNER_FORM);");
    }

    @Test
    public void testNominal() throws Exception {

        // this is just to ensure everything has been properly loaded by setup
        assertNotNull (page.getElementById("select-all-marathon-services-button"));

        page.executeJavaScript("eskimoMarathonServicesSelection.showMarathonServiceSelection()");

        Thread.sleep(10);

        assertCssValue("#marathon-services-selection-modal", "display", "block");
        assertCssValue("#marathon-services-selection-modal", "visibility", "visible");
    }

    @Test
    public void testClickButtonValidate() throws Exception {

        testNominal();

        page.executeJavaScript("eskimoMarathonServicesConfig.proceedWithReinstall = function (reinstallConfig) {" +
                "    window.reinstallConfig = JSON.stringify (reinstallConfig);" +
                "}");

        testSelectAll();

        page.getElementById("marathon-services-select-button-validate").click();

        JSONObject expectedResult = new JSONObject("{" +
                "\"cerebro_reinstall\":\"on\"," +
                "\"grafana_reinstall\":\"on\"," +
                "\"kafka-manager_reinstall\":\"on\"," +
                "\"kibana_reinstall\":\"on\"," +
                "\"spark-history-server_reinstall\":\"on\"," +
                "\"zeppelin_reinstall\":\"on\"}");

        JSONObject actualResult = new JSONObject((String)page.executeJavaScript("window.reinstallConfig").getJavaScriptResult());
        assertTrue(expectedResult.similar(actualResult));
    }

    @Test
    public void testSelectAll() throws Exception {

        page.executeJavaScript("eskimoMarathonServicesSelection.marathonServicesSelectionSelectAll();");

        assertTrue ((Boolean)page.executeJavaScript("$('#cerebro_reinstall').get(0).checked").getJavaScriptResult());
        assertTrue ((Boolean)page.executeJavaScript("$('#kibana_reinstall').get(0).checked").getJavaScriptResult());
        assertTrue ((Boolean)page.executeJavaScript("$('#kafka-manager_reinstall').get(0).checked").getJavaScriptResult());
        assertTrue ((Boolean)page.executeJavaScript("$('#spark-history-server_reinstall').get(0).checked").getJavaScriptResult());
        assertTrue ((Boolean)page.executeJavaScript("$('#grafana_reinstall').get(0).checked").getJavaScriptResult());
        assertTrue ((Boolean)page.executeJavaScript("$('#zeppelin_reinstall').get(0).checked").getJavaScriptResult());

        assertFalse ((Boolean)page.executeJavaScript("$('#gdash_reinstall').get(0).checked").getJavaScriptResult());
    }
}

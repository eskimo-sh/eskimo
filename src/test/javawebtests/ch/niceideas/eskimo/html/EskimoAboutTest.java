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

public class EskimoAboutTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript(findVendorLib ("bootstrap"));

        loadScript("eskimoAbout.js");

        waitForDefinition("window.eskimo");
        waitForDefinition("window.eskimo.About");

        js("eskimoAbout = new eskimo.About();");
        js("eskimoAbout.initialize()");

        waitForElementIdInDOM("about-modal-body");
    }

    @Test
    public void testNominal() throws Exception {

        js("$.ajaxGet = function (dataObj) {" +
                "    dataObj.success({\n" +
                "  \"about-eskimo-demo-mode\": false,\n" +
                "  \"about-eskimo-runtime-timestamp\": \"2023-04-17 16:51:13\",\n" +
                "  \"about-eskimo-kube-enabled\": true,\n" +
                "  \"about-eskimo-os-version\": \"6.1.0-5-amd64\",\n" +
                "  \"about-eskimo-version\": \"0.5-SNAPSHOT\",\n" +
                "  \"about-eskimo-build-timestamp\": \"2023-04-17T10:17:18Z\",\n" +
                "  \"about-eskimo-packages-url\": \"https://www.eskimo.sh/eskimo/V0.5/\",\n" +
                "  \"about-eskimo-java-home\": \"/usr/lib/jvm/java-11-openjdk-amd64\",\n" +
                "  \"about-eskimo-working-dir\": \"/data/badtrash/work/eskimo\",\n" +
                "  \"about-eskimo-os-name\": \"Linux\",\n" +
                "  \"status\": \"OK\",\n" +
                "  \"about-eskimo-java-version\": \"11.0.17\"\n" +
                "});" +
                "}");

        js("eskimoAbout.showAbout()");
        ActiveWaiter.wait(() -> js("return $('#about-modal').css('display')").equals("block"));

        assertCssEquals("block", "#about-modal", "display");
        assertCssEquals("visible", "#about-modal", "visibility");

        assertJavascriptEquals("0.5-SNAPSHOT", "$('#about-eskimo-version').html()");

        assertJavascriptEquals("false", "$('#about-eskimo-demo-mode').html()");
        assertJavascriptEquals("2023-04-17 16:51:13", "$('#about-eskimo-runtime-timestamp').html()");
        assertJavascriptEquals("true", "$('#about-eskimo-kube-enabled').html()");
        assertJavascriptEquals("2023-04-17T10:17:18Z", "$('#about-eskimo-build-timestamp').html()");
        assertJavascriptEquals("https://www.eskimo.sh/eskimo/V0.5/", "$('#about-eskimo-packages-url').html()");

        assertJavascriptEquals("6.1.0-5-amd64", "$('#about-eskimo-os-version').html()");
        assertJavascriptEquals("/usr/lib/jvm/java-11-openjdk-amd64", "$('#about-eskimo-java-home').html()");
        assertJavascriptEquals("/data/badtrash/work/eskimo", "$('#about-eskimo-working-dir').html()");
        assertJavascriptEquals("Linux", "$('#about-eskimo-os-name').html()");
        assertJavascriptEquals("11.0.17", "$('#about-eskimo-java-version').html()");

        js("eskimoAbout.cancelAbout()");
        ActiveWaiter.wait(() -> {
            Object display = js("$('#about-modal').css('display')");
            return display == null || display.equals("none");
        });

        //assertCssValue("#about-modal", "visibility", "hidden");
        assertCssEquals("none", "#about-modal", "display");

    }

}


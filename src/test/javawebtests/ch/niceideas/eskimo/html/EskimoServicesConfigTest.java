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
import org.junit.Before;
import org.junit.Test;

import java.net.URL;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;

public class EskimoServicesConfigTest extends AbstractWebTest {

    private String jsonConfig = null;

    @Before
    public void setUp() throws Exception {

        page.executeJavaScript("loadScript('../../src/main/webapp/scripts/eskimoUtils.js')");
        page.executeJavaScript("loadScript('../../src/main/webapp/scripts/eskimoServicesConfig.js')");

        // instantiate test object
        page.executeJavaScript("eskimoServicesConfig = new eskimo.ServicesConfig();");

        waitForElementIdinDOM("reset-services-config-btn");

        jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesConfigTest/testConfig.json"));

        page.executeJavaScript("var TEST_SERVICE_CONFIG = " + jsonConfig + ";");

        page.executeJavaScript("eskimoServicesConfig.setServicesConfigForTest(TEST_SERVICE_CONFIG.configs)");

    }

    @Test
    public void testLayoutServicesConfig() throws Exception {

        page.executeJavaScript("eskimoServicesConfig.layoutServicesConfig();");

        //System.out.println (page.asXml());

        // test a few input values

        assertEquals ("true", page.executeJavaScript("$('#elasticsearch-action-destructive_requires_name').val()").getJavaScriptResult());

        assertEquals ("102400", page.executeJavaScript("$('#kafka-socket-send-buffer-bytes').val()").getJavaScriptResult());

        assertEquals ("1", page.executeJavaScript("$('#kafka-num-partitions').val()").getJavaScriptResult());

        assertEquals ("5", page.executeJavaScript("$('#spark-executor-spark-rpc-numRetries').val()").getJavaScriptResult());

        assertEquals ("300s", page.executeJavaScript("$('#spark-executor-spark-dynamicAllocation-cachedExecutorIdleTimeout').val()").getJavaScriptResult());

    }

}

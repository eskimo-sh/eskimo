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
import ch.niceideas.eskimo.controlers.ServicesController;
import ch.niceideas.eskimo.model.MarathonServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.services.MarathonServicesConfigException;
import ch.niceideas.eskimo.services.ServicesDefinition;
import com.gargoylesoftware.htmlunit.ScriptException;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class EskimoMarathonServicesConfigCheckerTest extends AbstractWebTest {

    private static final Logger logger = Logger.getLogger(EskimoMarathonServicesConfigCheckerTest.class);

    private String jsonServices = null;

    @Before
    public void setUp() throws Exception {

        jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"));

        loadScript(page, "eskimoNodesConfigurationChecker.js");
        loadScript(page, "eskimoMarathonServicesConfigChecker.js");

        ServicesController sc = new ServicesController();

        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();

        sc.setServicesDefinition(sd);

        String servicesDependencies = sc.getServicesDependencies();

        page.executeJavaScript("var SERVICES_DEPENDENCIES_WRAPPER = " + servicesDependencies + ";");

        page.executeJavaScript("function callCheckMarathonSetup(nodesConfig, marathonConfig) {\n" +
                "   return doCheckMarathonSetup(nodesConfig, marathonConfig, SERVICES_DEPENDENCIES_WRAPPER.servicesDependencies);\n" +
                "}");
    }


    @Test
    public void testCheckMarathonSetupOK() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("marathon", "1");
            put("ntp1", "on");
            put("prometheus1", "on");
            put("elasticsearch1", "on");
        }});

        JSONObject marathonConfig = new JSONObject(new HashMap<String, Object>() {{
            put("cerebro_installed", "on");
            put("kibana_install", "on");
            put("grafana_install", "on");
        }});

        page.executeJavaScript("callCheckMarathonSetup(" + nodesConfig.toString() + "," + marathonConfig.toString() + ")");
    }


    @Test
    public void testOneCerebroButNoES() throws Exception {

        ScriptException exception = assertThrows(ScriptException.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("marathon", "1");
                put("ntp1", "on");
                put("prometheus1", "on");
            }});

            JSONObject marathonConfig = new JSONObject(new HashMap<String, Object>() {{
                put("cerebro_installed", "on");
            }});

            page.executeJavaScript("callCheckMarathonSetup(" + nodesConfig.toString() + "," + marathonConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().startsWith("Inconsistency found : Service cerebro expects 1 elasticsearch instance(s). But only 0 has been found !"));
    }

    @Test
    public void testGdashButNoGluster() throws Exception {

        ScriptException exception = assertThrows(ScriptException.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("marathon", "1");
                put("elasticsearch1", "on");
                put("ntp1", "on");
                put("prometheus1", "on");
                put("logstash1", "on");
            }});

            JSONObject marathonConfig = new JSONObject(new HashMap<String, Object>() {{
                put("gdash_installed", "on");
            }});

            page.executeJavaScript("callCheckMarathonSetup(" + nodesConfig.toString() + "," + marathonConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().startsWith("Inconsistency found : Service gdash expects 1 gluster instance(s). But only 0 has been found !"));
    }

    @Test
    public void testZeppelinButNoZookeeper() throws Exception {

        ScriptException exception = assertThrows(ScriptException.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("ntp1", "on");
                put("marathon", "1");
                put("prometheus1", "on");
                put("elasticsearch1", "on");
            }});

            JSONObject marathonConfig = new JSONObject(new HashMap<String, Object>() {{
                put("zeppelin_installed", "on");
            }});

            page.executeJavaScript("callCheckMarathonSetup(" + nodesConfig.toString() + "," + marathonConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().startsWith("Inconsistency found : Service zeppelin expects 1 zookeeper instance(s). But only 0 has been found !"));
    }
}

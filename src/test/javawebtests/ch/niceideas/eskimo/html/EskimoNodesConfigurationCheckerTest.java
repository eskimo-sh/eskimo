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
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.services.NodesConfigurationException;
import ch.niceideas.eskimo.services.ServicesDefinition;
import com.gargoylesoftware.htmlunit.ScriptResult;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class EskimoNodesConfigurationCheckerTest extends AbstractWebTest {

    private String jsonServices = null;

    @Before
    public void setUp() throws Exception {

        jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"));

        loadScript(page, "eskimoNodesConfigurationChecker.js");

        ServicesController sc = new ServicesController();

        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();

        sc.setServicesDefinition(sd);

        String servicesDependencies = sc.getServicesDependencies();

        page.executeJavaScript("var SERVICES_DEPENDENCIES_WRAPPER = " + servicesDependencies + ";");

        page.executeJavaScript("var UNIQUE_SERVICES = [\"zookeeper\", \"mesos-master\", \"marathon\" ];");
        page.executeJavaScript("var MULTIPLE_SERVICES = [\"elasticsearch\", \"kafka\", \"mesos-agent\", \"spark-executor\", \"gluster\", \"logstash\"];");
        page.executeJavaScript("var MANDATORY_SERVICES = [\"ntp\", \"gluster\"];");
        page.executeJavaScript("var CONFIGURED_SERVICES = UNIQUE_SERVICES.concat(MULTIPLE_SERVICES);");

        page.executeJavaScript("var SERVICES_CONFIGURATION = " + jsonServices + ";");

        page.executeJavaScript("function callCheckNodeSetup(config) {\n" +
                "   return checkNodesSetup(config, UNIQUE_SERVICES, MANDATORY_SERVICES, SERVICES_CONFIGURATION, SERVICES_DEPENDENCIES_WRAPPER.servicesDependencies);\n" +
                "}");

    }

    @Test
    public void testRangeOfIps() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
            put("action_id1", "192.168.10.11-192.168.10.15");
            put("ntp1", "on");
            put("prometheus1", "on");
        }});

        assertJavascriptEquals("true", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testNotAnIpAddress() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
            put("action_id1", "blabla");
            put("ntp1", "on");
            put("prometheus1", "on");
        }});

        assertJavascriptEquals("false", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testCheckNodesSetupMultipleOK() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
            put("action_id1", "192.168.10.11");
            put("action_id2", "192.168.10.12");
            put("marathon", "2");
            put("elasticsearch1", "on");
            put("elasticsearch2", "on");
            put("kafka1", "on");
            put("kafka2", "on");
            put("ntp1", "on");
            put("ntp2", "on");
            put("gluster1", "on");
            put("gluster2", "on");
            put("logstash1", "on");
            put("logstash2", "on");
            put("mesos-agent1", "on");
            put("mesos-agent2", "on");
            put("mesos-master", "1");
            put("spark-executor1", "on");
            put("spark-executor2", "on");
            put("zookeeper", "1");
        }});

        assertJavascriptEquals("true", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testCheckNodesSetupMultipleOKWithRange() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("action_id1", "192.168.10.11");
                put("action_id2", "192.168.10.12-192.160.10.15");
                put("marathon", "1");
                put("elasticsearch1", "on");
                put("elasticsearch2", "on");
                put("kafka1", "on");
                put("kafka2", "on");
                put("ntp1", "on");
                put("ntp2", "on");
                put("prometheus1", "on");
                put("prometheus2", "on");
                put("gluster1", "on");
                put("gluster2", "on");
                put("logstash1", "on");
                put("logstash2", "on");
                put("mesos-agent1", "on");
                put("mesos-agent2", "on");
                put("mesos-master", "1");
                put("spark-executor1", "on");
                put("spark-executor2", "on");
                put("zookeeper", "1");
        }});

        assertJavascriptEquals("true", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testCheckNodesSetupSingleOK() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("action_id1", "192.168.10.11");
                put("marathon", "1");
                put("elasticsearch1", "on");
                put("kafka1", "on");
                put("ntp1", "on");
                put("logstash1", "on");
                put("mesos-agent1", "on");
                put("mesos-master", "1");
                put("spark-executor1", "on");
                put("zookeeper", "1");
        }});

        assertJavascriptEquals("true", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testUniqueServiceOnRange() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("action_id1", "192.168.10.11");
                put("action_id2", "192.168.10.12-192.160.10.15");
                put("marathon", "2");
                put("elasticsearch1", "on");
                put("elasticsearch2", "on");
                put("kafka1", "on");
                put("kafka2", "on");
                put("ntp1", "on");
                put("ntp2", "on");
                put("prometheus1", "on");
                put("prometheus2", "on");
                put("gluster1", "on");
                put("gluster2", "on");
                put("logstash1", "on");
                put("logstash2", "on");
                put("mesos-agent1", "on");
                put("mesos-agent2", "on");
                put("mesos-master", "1");
                put("spark-executor1", "on");
                put("spark-executor2", "on");
                put("zookeeper", "1");
        }});

        assertJavascriptEquals("false", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testMissingGluster() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
            put("action_id1", "192.168.10.11");
            put("action_id2", "192.168.10.12");
            put("marathon", "2");
            put("elasticsearch1", "on");
            put("elasticsearch2", "on");
            put("kafka1", "on");
            put("kafka2", "on");
            put("ntp1", "on");
            put("ntp2", "on");
            put("logstash1", "on");
            put("logstash2", "on");
            put("mesos-agent1", "on");
            put("mesos-agent2", "on");
            put("mesos-master", "1");
            put("spark-executor1", "on");
            put("spark-executor2", "on");
            put("zookeeper", "1");
        }});

        assertJavascriptEquals("false", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testNoIPConfigured() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("action_id1", "");
        }});

        assertJavascriptEquals("false", "callCheckNodeSetup(" + nodesConfig.toString() + ")");    }

    @Test
    public void testKeyGreaterThanNodeNumber() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("action_id2", "192.168.10.11");
        }});

        assertJavascriptEquals("false", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testOneCerebroButNoES() throws Exception {

        fail ("This needs to be moved to EskimoMarathonServicesConfigCheckSetupTest");

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("action_id1", "192.168.10.11");
                put("cerebro", "1");
                put("ntp1", "on");
        }});

        assertJavascriptEquals("false", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testTwoSparkNodesAndNoGLuster() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("action_id1", "192.168.10.11");
                put("action_id2", "192.168.10.12");
                put("spark-executor1", "on");
                put("spark-executor2", "on");
                put("ntp1", "on");
                put("ntp2", "on");
        }});

        assertJavascriptEquals("false", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testGdashButNoGluster() throws Exception {

        fail ("This needs to be moved to EskimoMarathonServicesConfigCheckerTest");

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("action_id1", "192.168.10.11");
                put("cerebro", "1");
                put("ntp1", "on");
                put("elasticsearch1", "on");
                put("gdash", "on");
                put("logstash1", "on");
        }});

        assertJavascriptEquals("false", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testGlusterDisabledOnSingleNode() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("action_id1", "192.168.10.11");
                put("marathon", "1");
                put("elasticsearch1", "on");
                put("ntp1", "on");
                put("kafka1", "on");
                put("gluster1", "on");
                put("logstash1", "on");
                put("mesos-agent1", "on");
                put("mesos-master", "1");
                put("spark-executor1", "on");
                put("zookeeper", "1");
        }});

        assertJavascriptEquals("false", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testSparkButNoMesosAgent() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("action_id1", "192.168.10.11");
                put("mesos-master", "1");
                put("ntp", "1");
                put("spark-executor1", "on");
                put("marathon", "1");
                put("zookeeper", "1");
        }});

        assertJavascriptEquals("false", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testoMesosAgentButNoMesosMaster() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("action_id1", "192.168.10.11");
                put("mesos-agent1", "on");
                put("spark-executor1", "on");
                put("zookeeper", "1");
        }});

        assertJavascriptEquals("false", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testMesosMasterButNoZookeeper() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("action_id1", "192.168.10.11");
                put("mesos-agent1", "on");
                put("ntp1", "on");
                put("mesos-master", "1");
                put("spark-executor1", "on");
        }});

        assertJavascriptEquals("false", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testFlinkAndZookeeperSeparatedIsNowOK() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
            put("action_id1", "192.168.10.11");
            put("action_id2", "192.168.10.12");
            put("ntp1", "on");
            put("ntp2", "on");
            put("mesos-agent1", "on");
            put("mesos-agent2", "on");
            put("prometheus1", "on");
            put("prometheus2", "on");
            put("gluster1", "on");
            put("gluster2", "on");
            put("mesos-master", "1");
            put("zookeeper", "1");
            put("flink-worker1", "on");
            put("flink-worker2", "on");
            put("flink-app-master", "2");
        }});

        assertJavascriptEquals("true", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }


    @Test
    public void testNoMarathonServiceCanBeSelected() throws Exception {
        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
            put("action_id1", "192.168.10.11");
            put("action_id2", "192.168.10.12");
            put("ntp1", "on");
            put("ntp2", "on");
            put("mesos-agent1", "on");
            put("mesos-agent2", "on");
            put("prometheus1", "on");
            put("prometheus2", "on");
            put("gluster1", "on");
            put("gluster2", "on");
            put("mesos-master", "1");
            put("zookeeper", "1");
            put("cerebro", "2");
        }});

        assertJavascriptEquals("false", "callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }


}

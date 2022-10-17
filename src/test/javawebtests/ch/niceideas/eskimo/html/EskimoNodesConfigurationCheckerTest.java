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
import ch.niceideas.eskimo.controlers.ServicesController;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EskimoNodesConfigurationCheckerTest extends AbstractWebTest {

    private static final Logger logger = Logger.getLogger(EskimoNodesConfigurationCheckerTest.class);

    private String jsonServices = null;

    @BeforeEach
    public void setUp() throws Exception {

        jsonServices = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSelectionTest/testServices.json"), StandardCharsets.UTF_8);

        loadScript("eskimoNodesConfigurationChecker.js");

        ServicesController sc = new ServicesController();

        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();

        sc.setServicesDefinition(sd);

        String servicesDependencies = sc.getServicesDependencies();

        js("window.SERVICES_DEPENDENCIES_WRAPPER = " + servicesDependencies + ";");

        js("window.UNIQUE_SERVICES = [\"zookeeper\", \"kube-master\" ];");
        js("window.MULTIPLE_SERVICES = [\"kube-slave\", \"prometheus\", \"gluster\", \"logstash\", \"etcd\"];");
        js("window.MANDATORY_SERVICES = [\"ntp\", \"gluster\"];");
        js("window.CONFIGURED_SERVICES = UNIQUE_SERVICES.concat(MULTIPLE_SERVICES);");

        js("window.SERVICES_CONFIGURATION = " + jsonServices + ";");

        js("window.callCheckNodeSetup = function (config) {\n" +
                "   return checkNodesSetup(config, UNIQUE_SERVICES, MANDATORY_SERVICES, SERVICES_CONFIGURATION, SERVICES_DEPENDENCIES_WRAPPER.servicesDependencies);\n" +
                "}");

    }

    @Test
    public void testRangeOfIps() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
            put("node_id1", "192.168.10.11-192.168.10.15");
            put("node_id2", "192.168.10.16");
            put("ntp1", "on");
            put("ntp2", "on");
            put("zookeeper", "2");
            put("gluster1", "on");
            put("gluster2", "on");
            put("prometheus1", "on");
            put("prometheus2", "on");
        }});

        js ("callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testNotAnIpAddress() throws Exception {

        Exception exception = assertThrows(Exception.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
                put("node_id1", "blabla");
                put("ntp1", "on");
                put("prometheus1", "on");
            }});

            js("callCheckNodeSetup(" + nodesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("Node 1 has IP configured as blabla which is not an IP address or a range."));
    }

    @Test
    public void testCheckNodesSetupMultipleOK() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("node_id2", "192.168.10.12");
            put("ntp1", "on");
            put("ntp2", "on");
            put("prometheus1", "on");
            put("prometheus2", "on");
            put("gluster1", "on");
            put("gluster2", "on");
            put("etcd1", "on");
            put("etcd2", "on");
            put("kube-slave1", "on");
            put("kube-slave2", "on");
            put("kube-master", "1");
            put("zookeeper", "1");
        }});

        js("callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testCheckNodesSetupMultipleOKWithRange() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
                put("node_id1", "192.168.10.11");
                put("node_id2", "192.168.10.12-192.160.10.15");
                put("ntp1", "on");
                put("ntp2", "on");
                put("prometheus1", "on");
                put("prometheus2", "on");
                put("gluster1", "on");
                put("gluster2", "on");
                put("etcd1", "on");
                put("etcd2", "on");
                put("kube-slave1", "on");
                put("kube-slave2", "on");
                put("kube-master", "1");
                put("zookeeper", "1");
        }});

        js("callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testCheckNodesSetupSingleOK() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("etcd1", "on");
            put("ntp1", "on");
            put("gluster1", "on");
            put("prometheus1", "on");
            put("kube-slave1", "on");
            put("kube-master", "1");
            put("zookeeper", "1");
        }});

        js("callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testUniqueServiceOnRange() throws Exception {

        Exception exception = assertThrows(Exception.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
                put("node_id1", "192.168.10.11");
                put("node_id2", "192.168.10.12-192.160.10.15");
                put("ntp1", "on");
                put("ntp2", "on");
                put("prometheus1", "on");
                put("prometheus2", "on");
                put("gluster1", "on");
                put("gluster2", "on");
                put("etcd1", "on");
                put("etcd2", "on");
                put("logstash1", "on");
                put("logstash2", "on");
                put("kube-slave1", "on");
                put("kube-slave2", "on");
                put("kube-master", "1");
                put("zookeeper", "2");
            }});

            js("callCheckNodeSetup(" + nodesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("Node 2 is a range an declares service zookeeper which is a unique service, hence forbidden on a range."));
    }

    @Test
    public void testMissingGluster() throws Exception {

        Exception exception = assertThrows(Exception.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
                put("node_id1", "192.168.10.11");
                put("node_id2", "192.168.10.12");
                put("ntp1", "on");
                put("ntp2", "on");
                put("etcd1", "on");
                put("etcd2", "on");
                put("kube-slave1", "on");
                put("kube-slave2", "on");
                put("kube-master", "1");
                put("zookeeper", "1");
            }});

            js("callCheckNodeSetup(" + nodesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("Inconsistency found : service gluster is mandatory on all nodes but some nodes are lacking i"));
    }

    @Test
    public void testNoIPConfigured() throws Exception {

        Exception exception = assertThrows(Exception.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
                put("node_id1", "");
            }});

            js("callCheckNodeSetup(" + nodesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("Node 1 has no IP configured."));

    }

    @Test
    public void testKeyGreaterThanNodeNumber() throws Exception {
        Exception exception = assertThrows(Exception.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
                put("node_id2", "192.168.10.11");
            }});

            js("callCheckNodeSetup(" + nodesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("Inconsistency found : got key node_id2 which is greater than node number 1"));
    }

    @Test
    public void testGlusterNoMoreDisabledOnSingleNode() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("ntp1", "on");
            put("prometheus1", "on");
            put("gluster1", "on");
            put("etcd1", "on");
            put("kube-slave1", "on");
            put("kube-master", "1");
            put("zookeeper", "1");
        }});

        js("callCheckNodeSetup(" + nodesConfig.toString() + ")");
    }

    @Test
    public void testNoGlusterOnSingleNode() throws Exception {

        Exception exception = assertThrows(Exception.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
                put("node_id1", "192.168.10.11");
                put("ntp1", "on");
                put("etcd1", "on");
                put("prometheus1", "on");
                put("kube-slave1", "on");
                put("kube-master", "1");
                put("zookeeper", "1");
            }});

            js("callCheckNodeSetup(" + nodesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());

        assertTrue(exception.getMessage().contains("Inconsistency found : service gluster is mandatory on all nodes but some nodes are lacking it."));
    }

    @Test
    public void testKubeSlaveButNoKubeMaster() throws Exception {

        Exception exception = assertThrows(Exception.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
                put("node_id1", "192.168.10.11");
                put("kube-slave1", "on");
                //put("kube-master", "1");
                put("zookeeper", "1");
                put("ntp1", "on");
                put("etcd1", "on");
                put("gluster1", "on");
                put("prometheus1", "on");
            }});

            js("callCheckNodeSetup(" + nodesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("Inconsistency found : Service kube-slave expects 1 kube-master instance(s). But only 0 has been found !"));
    }

    @Test
    public void testGlusterButNoZookeeper() throws Exception {

        Exception exception = assertThrows(Exception.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
                put("node_id1", "192.168.10.11");
                put("kube-slave1", "on");
                put("kube-master", "1");
                put("ntp1", "on");
                put("etcd1", "on");
                put("gluster1", "on");
                put("prometheus1", "on");
                //put("zookeeper", "1");
            }});

            js("callCheckNodeSetup(" + nodesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("Inconsistency found : Service gluster expects 1 zookeeper instance(s). But only 0 has been found !"));
    }

    @Test
    public void testNoKubernetesServiceCanBeSelected() throws Exception {

        Exception exception = assertThrows(Exception.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
                put("node_id1", "192.168.10.11");
                put("node_id2", "192.168.10.12");
                put("ntp1", "on");
                put("ntp2", "on");
                put("etcd1", "on");
                put("etcd2", "on");
                put("kube-slave1", "on");
                put("kube-slave2", "on");
                put("prometheus1", "on");
                put("prometheus2", "on");
                put("gluster1", "on");
                put("gluster2", "on");
                put("kube-master", "1");
                put("zookeeper", "1");
                put("cerebro", "2");
            }});

            js("callCheckNodeSetup(" + nodesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("Inconsistency found : service cerebro is either undefined or a kubernetes service which should not be selectable here."));
    }


}

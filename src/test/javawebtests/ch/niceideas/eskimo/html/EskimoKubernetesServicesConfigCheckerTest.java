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

import ch.niceideas.eskimo.controlers.ServicesController;
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class EskimoKubernetesServicesConfigCheckerTest extends AbstractWebTest {

    private static final Logger logger = Logger.getLogger(EskimoKubernetesServicesConfigCheckerTest.class);


    JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
        put("node_id1", "192.168.10.11");
        put("kube-master", "1");
        put("kube-slave", "1");
        put("ntp1", "on");
        put("etcd1", "on");
        put("prometheus1", "on");
        put("gluster1", "on");

        put("node_id2", "192.168.10.12");
        put("ntp2", "on");
        put("zookeeper", "2");
        put("etcd2", "on");
        put("prometheus2", "on");
        put("gluster2", "on");

        put("node_id3", "192.168.10.13");
        put("ntp3", "on");
        put("etcd3", "on");
        put("prometheus3", "on");
        put("gluster3", "on");
    }});

    KubernetesServicesConfigWrapper kubernetesConfig = new KubernetesServicesConfigWrapper("{\n" +
            "    \"cerebro_install\": \"on\",\n" +
            "    \"cerebro_cpu\": \"0.3\",\n" +
            "    \"cerebro_ram\": \"400M\",\n" +
            "    \"elasticsearch_install\": \"on\",\n" +
            "    \"elasticsearch_cpu\": \"1\",\n" +
            "    \"elasticsearch_ram\": \"1G\",\n" +
            "    \"flink-runtime_install\": \"on\",\n" +
            "    \"flink-runtime_cpu\": \"1\",\n" +
            "    \"flink-runtime_ram\": \"1G\",\n" +
            "    \"grafana_install\": \"on\",\n" +
            "    \"grafana_cpu\": \"0.3\",\n" +
            "    \"grafana_ram\": \"400M\",\n" +
            "    \"kafka_install\": \"on\",\n" +
            "    \"kafka_cpu\": \"1\",\n" +
            "    \"kafka_ram\": \"1G\",\n" +
            "    \"kubernetes-dashboard_install\": \"on\",\n" +
            "    \"kubernetes-dashboard_cpu\": \"0.3\",\n" +
            "    \"kubernetes-dashboard_ram\": \"500M\",\n" +
            "    \"spark-runtime_install\": \"on\",\n" +
            "    \"spark-runtime_cpu\": \"1\",\n" +
            "    \"spark-runtime_ram\": \"800M\"\n" +
            "}");

    @BeforeEach
    public void setUp() throws Exception {

        loadScript("eskimoNodesConfigurationChecker.js");
        loadScript("eskimoKubernetesServicesConfigChecker.js");

        ServicesController sc = new ServicesController();

        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();

        sc.setServicesDefinition(sd);

        String servicesDependencies = sc.getServicesDependencies();
        String kubernetesServiceConfig = sc.getKubernetesServices();

        js("window.SERVICES_DEPENDENCIES_WRAPPER = " + servicesDependencies + ";");

        js("window.KUBERNETES_SERVICES_CONFIG = " + kubernetesServiceConfig + ";");

        js("window.callCheckKubernetesSetup = function (nodesConfig, kubernetesConfig) {\n" +
                "   return doCheckKubernetesSetup(nodesConfig, kubernetesConfig, SERVICES_DEPENDENCIES_WRAPPER.servicesDependencies, KUBERNETES_SERVICES_CONFIG.kubernetesServicesConfigurations);\n" +
                "}");
    }

    @Test
    public void testWithKubeConfigRequest() throws Exception {

        Exception error = null;
        try {
            js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.getFormattedValue() + ")");
        } catch (Exception e) {
            error = e;
            logger.error (e, e);
        }
        assertNull(error);
    }

    @Test
    public void testWithKubeConfigRequest_BuggyCPU() throws Exception {

        kubernetesConfig.removeRootKey("flink-runtime_install");

        Exception exception = assertThrows(Exception.class, () -> {
                js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.getFormattedValue() + ")");
        });
        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("Inconsistency found : Found a CPU definition for flink-runtime_cpu. But corresponding service installation is not enabled"));
    }

    @Test
    public void testWithKubeConfigRequest_BuggyRAM() throws Exception {

        kubernetesConfig.removeRootKey("flink-runtime_install");
        kubernetesConfig.removeRootKey("flink-runtime_cpu");

        Exception exception = assertThrows(Exception.class, () -> {
            js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.getFormattedValue() + ")");
        });
        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("Inconsistency found : Found a RAM definition for flink-runtime_ram. But corresponding service installation is not enabled"));
    }

    @Test
    public void testWithKubeConfigRequest_wrongCPU() throws Exception {

        kubernetesConfig.setValueForPath("flink-runtime_cpu", "0.5z");

        Exception exception = assertThrows(Exception.class, () -> {
            js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.getFormattedValue() + ")");
        });
        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("CPU definition for flink-runtime_cpu doesn't match expected REGEX - [0-9\\\\.]+[m]{0,1}"));
    }

    @Test
    public void testWithKubeConfigRequest_wrongRAM() throws Exception {

        kubernetesConfig.setValueForPath("flink-runtime_ram", "500Gb");

        Exception exception = assertThrows(Exception.class, () -> {
            js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.getFormattedValue() + ")");
        });
        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("RAM definition for flink-runtime_ram doesn't match expected REGEX - [0-9\\.]+[EPTGMk]{0,1}"));
    }

    @Test
    public void testCheckKubernetesSetupOK() throws Exception {

        JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("kube-master", "1");
            put("kube-slave", "1");
            put("ntp1", "on");
            put("prometheus1", "on");
        }});

        JSONObject kubernetesConfig = new JSONObject(new HashMap<String, Object>() {{
            put("elasticsearch_install", "on");
            put("cerebro_install", "on");
            put("kibana_install", "on");
            put("grafana_install", "on");
        }});

        Exception error = null;
        try {
            js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.toString() + ")");
        } catch (Exception e) {
            error = e;
            logger.error (e, e);
        }
        assertNull(error);
    }


    @Test
    public void testOneCerebroButNoES() throws Exception {

        Exception exception = assertThrows(Exception.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("kubernetes", "1");
                put("ntp1", "on");
                put("prometheus1", "on");
            }});

            JSONObject kubernetesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("cerebro_install", "on");
            }});

            js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("Inconsistency found : Service cerebro expects a installaton of  elasticsearch. But it's not going to be installed"));
    }

    @Test
    public void testSparkButNoKube() throws Exception {

        Exception exception = assertThrows(Exception.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("ntp1", "on");
            }});

            JSONObject kubernetesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("spark-runtime_install", "on");
            }});

            js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("Inconsistency found : Service spark-runtime expects 1 kube-master instance(s). But only 0 has been found !"));
    }

    @Test
    public void testZeppelinButNoZookeeper() throws Exception {

        Exception exception = assertThrows(Exception.class, () -> {
            JSONObject nodesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("node_id1", "192.168.10.11");
                put("ntp1", "on");
                put("prometheus1", "on");
                put("kube-master1", "on");
                put("kube-slave1", "on");
            }});

            JSONObject kubernetesConfig = new JSONObject(new HashMap<String, Object>() {{
                put("elasticsearch_install", "on");
                put("zeppelin_install", "on");
            }});

            js("callCheckKubernetesSetup(" + nodesConfig.toString() + "," + kubernetesConfig.toString() + ")");
        });

        logger.debug (exception.getMessage());
        assertTrue(exception.getMessage().contains("Inconsistency found : Service zeppelin expects 1 zookeeper instance(s). But only 0 has been found !"));
    }

}

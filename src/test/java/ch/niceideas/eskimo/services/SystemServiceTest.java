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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.model.SystemStatusWrapper;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.util.*;

import static org.junit.Assert.*;

public class SystemServiceTest extends AbstractSystemTest {

    private static final Logger logger = Logger.getLogger(SystemServiceTest.class);

    @Test
    public void testInstallService() throws Exception {

        setupService.setConfigStoragePathInternal(createTempStoragePath());

        ServicesInstallStatusWrapper savedStatus = new ServicesInstallStatusWrapper(new HashMap<String, Object>() {{
                put("mesos_installed_on_IP_192-168-10-11", "OK");
                put("mesos_installed_on_IP_192-168-10-13", "OK");
                put("node_check_IP_192-168-10-11", "OK");
                put("node_check_IP_192-168-10-13", "OK");
                put("ntp_installed_on_IP_192-168-10-11", "OK");
                put("ntp_installed_on_IP_192-168-10-13", "OK");
        }});

        systemService.saveServicesInstallationStatus(savedStatus);

        // testing zookeeper installation
        systemService.installService("zookeeper", "localhost");

        assertTrue(testSSHCommandScript.toString().startsWith("rm -Rf /tmp/zookeeper\n" +
                "rm -f /tmp/zookeeper.tgz\n"));

        assertTrue(testSSHCommandScript.toString().contains(
                "tar xfz /tmp/zookeeper.tgz --directory=/tmp/\n" +
                "chmod 755 /tmp/zookeeper/setup.sh\n"));
        assertTrue(testSSHCommandScript.toString().contains(
                "bash /tmp/zookeeper/setup.sh localhost \n" +
                "rm -Rf /tmp/zookeeper\n" +
                "rm -f /tmp/zookeeper.tgz\n"));
    }

    static String createTempStoragePath() throws Exception {
        File dtempFileName = File.createTempFile("test_systemservice_", "config_storage");
        FileUtils.delete (dtempFileName); // delete file to create directory below

        File configStoragePathFile = new File (dtempFileName.getAbsolutePath() + "/");
        configStoragePathFile.mkdirs();
        return configStoragePathFile.getAbsolutePath();
    }


    @Test
    public void testFetchNodeStatus() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        Map<String, String> statusMap = new HashMap<>();

        int nodeNbr = 1;
        String ipAddress = "192.168.10.11";

        Pair<String, String> nbrAndPair = new Pair<>(""+nodeNbr, ipAddress);

        systemService.setSshCommandService(new SSHCommandService() {
            public String runSSHScript(String hostAddress, String script, boolean throwsException) throws SSHCommandException {
                testSSHCommandScript.append(script).append("\n");
                if (script.equals("echo OK")) {
                    return "OK";
                }
                if (script.startsWith("sudo systemctl status --no-pager -al")) {
                    return systemStatusTest;
                }
                return testSSHCommandResultBuilder.toString();
            }
            public String runSSHCommand(String hostAddress, String command) throws SSHCommandException {
                testSSHCommandScript.append(command + "\n");
                return testSSHCommandResultBuilder.toString();
            }
            public void copySCPFile(String hostAddress, String filePath) throws SSHCommandException {
                // just do nothing
            }
        });


        systemService.fetchNodeStatus (nodesConfig, statusMap, nbrAndPair);

        assertEquals(5, statusMap.size());

        assertEquals("NA", statusMap.get("service_kafka-manager_192-168-10-11"));
        assertEquals("OK", statusMap.get("node_alive_192-168-10-11"));
        assertEquals("OK", statusMap.get("service_spark-executor_192-168-10-11"));
        assertEquals("OK", statusMap.get("service_gluster_192-168-10-11"));
        assertEquals("OK", statusMap.get("service_ntp_192-168-10-11"));
    }

    @Test
    public void testCheckServiceDisappearance() throws Exception {

        setupService.setConfigStoragePathInternal(createTempStoragePath());

        SystemStatusWrapper prevSystemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        prevSystemStatus.getJSONObject().remove("service_kafka-manager_192-168-10-11");

        File prevStatusFile = new File(setupService.getConfigStoragePath() + "/nodes-status-check-previous.json");
        FileUtils.writeFile(prevStatusFile, prevSystemStatus.getFormattedValue());

        // we'll make it so that kibana and cerebro seem to have disappeared
        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        systemStatus.getJSONObject().remove("service_kafka-manager_192-168-10-11");
        systemStatus.setValueForPath("service_cerebro_192-168-10-11", "NA");
        systemStatus.setValueForPath("service_kibana_192-168-10-11", "KO");

        systemService.checkServiceDisappearance (systemStatus);

        Pair<Integer, List<JSONObject>> notifications = notificationService.fetchElements(0);

        assertNotNull (notifications);

        assertEquals(2, notifications.getKey().intValue());

        assertEquals("{\"type\":\"error\",\"message\":\"Service service_cerebro_192-168-10-11 got into problem\"}", notifications.getValue().get(0).toString());

        assertEquals("{\"type\":\"error\",\"message\":\"Service service_kibana_192-168-10-11 got into problem\"}", notifications.getValue().get(1).toString());
    }

    @Test
    public void testHandleStatusChanges() throws Exception {

        Set<String> liveIps = new HashSet<String>(){{
            add("192.168.10.11");
            add("192.168.10.13");
        }};

        setupService.setConfigStoragePathInternal(createTempStoragePath());

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        systemStatus.getJSONObject().remove("service_kafka-manager_192-168-10-11");

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesStatus();

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, liveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = systemService.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, liveIps);
        }

        // now I have changes
        resultPrevStatus = systemService.loadServicesInstallationStatus();

        // kafka manager is missing
        assertTrue (new JSONObject(expectedPrevStatus).similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testHandleStatusChangeWhenNodeVanishes() throws Exception {

        Set<String> liveIps = new HashSet<String>(){{
            add("192.168.10.11");
            add("192.168.10.13");
        }};

        // the objective here is to make sur that when a node entirely vanishes from configuration,
        // if it is still referenced by status, then it is checked for services and services are removed from it
        // if they are down
        // IMPORTANT : they should be kept if they are still there to be properly uninstalled indeed !

        setupService.setConfigStoragePathInternal(createTempStoragePath());

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        logger.debug (systemStatus.getIpAddresses());

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesStatus();
        logger.debug (servicesInstallStatus.getIpAddresses());

        // remove all status for node 2 : 192-168-10-13 EXCEPT KAFKA AND ELASTICSEARCH
        List<String> toBeremoved = new ArrayList<>();
        systemStatus.getRootKeys().stream()
                .filter(key -> key.contains("192-168-10-13") && !key.contains("kafka") && !key.contains("elasticsearch"))
                .forEach(toBeremoved::add);
        toBeremoved.stream()
                .forEach(systemStatus::removeRootKey);

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, liveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = systemService.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, liveIps);
        }

        // now I have changes
        resultPrevStatus = systemService.loadServicesInstallationStatus();

        // kafka and elasticsearch have been kept
        assertTrue(new JSONObject("{\n" +
                "    \"cerebro_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"elasticsearch_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"elasticsearch_installed_on_IP_192-168-10-13\": \"OK\",\n" +
                "    \"gluster_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kafka-manager_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kafka_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kafka_installed_on_IP_192-168-10-13\": \"OK\",\n" +
                "    \"kibana_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"logstash_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"mesos-agent_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"ntp_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"spark-executor_installed_on_IP_192-168-10-11\": \"OK\"\n" +
                "}").similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testHandleStatusChangeWhenNodeVanishesAndNodeIsDown() throws Exception {

        Set<String> liveIps = new HashSet<String>(){{
            add("192.168.10.11");
        }};

        // the objective here is to make sur that when a node entirely vanishes from configuration,
        // if it is still referenced by status, then it is checked for services and
        // if it is down, all services are removed without checking them
        // IMPORTANT : they should be kept if they are still there to be properly uninstalled indeed !

        setupService.setConfigStoragePathInternal(createTempStoragePath());

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        logger.debug (systemStatus.getIpAddresses());

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesStatus();
        logger.debug (servicesInstallStatus.getIpAddresses());

        // remove all status for node 2 : 192-168-10-13 EXCEPT KAFKA AND ELASTICSEARCH
        List<String> toBeremoved = new ArrayList<>();
        systemStatus.getRootKeys().stream()
                .filter(key -> key.contains("192-168-10-13"))
                .forEach(toBeremoved::add);
        toBeremoved.stream()
                .forEach(systemStatus::removeRootKey);

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, liveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = systemService.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, liveIps);
        }

        // now I have changes
        resultPrevStatus = systemService.loadServicesInstallationStatus();

        // everything has been removed, including kafka and elasticsearch
        assertTrue(new JSONObject("{\n" +
                "    \"cerebro_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"elasticsearch_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"gluster_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kafka-manager_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kafka_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kibana_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"logstash_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"mesos-agent_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"ntp_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"spark-executor_installed_on_IP_192-168-10-11\": \"OK\"\n" +
                "}").similar(resultPrevStatus.getJSONObject()));
    }

}

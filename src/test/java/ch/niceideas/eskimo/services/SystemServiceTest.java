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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.exceptions.CommonBusinessException;
import ch.niceideas.common.exceptions.CommonRTException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.infrastructure.TestMessageLogger;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import ch.niceideas.eskimo.test.services.ConnectionManagerServiceTestImpl;
import ch.niceideas.eskimo.test.services.SSHCommandServiceTestImpl;
import ch.niceideas.eskimo.test.testwrappers.SystemServiceUnderTest;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-setup", "test-conf", "test-proxy", "test-ssh", "test-connection-manager", "system-under-test"})
public class SystemServiceTest {

    private static final Logger logger = Logger.getLogger(SystemServiceTest.class);

    protected String systemStatusTest = null;
    protected String expectedFullStatus = null;
    protected String expectedFullStatusNoKubernetes = null;
    protected String expectedPrevStatusServicesRemoved = null;
    protected String expectedPrevStatusAllServicesStay = null;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private SystemServiceUnderTest systemService;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Autowired
    private SSHCommandServiceTestImpl sshCommandServiceTest;

    @Autowired
    private ConnectionManagerServiceTestImpl connectionManagerServiceTest;

    @BeforeEach
    public void setUp() throws Exception {
        SecurityContextHelper.loginAdmin();
        configurationServiceTest.reset();
        sshCommandServiceTest.reset();
        notificationService.clear();
        connectionManagerServiceTest.dontConnect();
        systemStatusTest = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/systemStatusTest.log"), StandardCharsets.UTF_8);
        expectedFullStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedFullStatus.json"), StandardCharsets.UTF_8);
        expectedFullStatusNoKubernetes = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedFullStatusNoKubernetes.json"), StandardCharsets.UTF_8);
        expectedPrevStatusServicesRemoved = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedPrevStatusServicesRemoved.json"), StandardCharsets.UTF_8);
        expectedPrevStatusAllServicesStay = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedPrevStatusAllServicesStay.json"), StandardCharsets.UTF_8);
    }

    public static String createTempStoragePath() throws Exception {
        File dtempFileName = File.createTempFile("test_systemservice_", "config_storage");
        FileUtils.delete (dtempFileName); // delete file to create directory below
        assertTrue (dtempFileName.mkdirs());
        return dtempFileName.getAbsolutePath();
    }


    @Test
    public void testInstallationCleanup() throws Exception {
        systemService.installationCleanup(new TestMessageLogger(new StringBuilder()),
                connectionManagerServiceTest.getPrivateConnection(Node.fromAddress("192.168.56.11")),
                Service.from("cerebro"),
                "cerebro",
                File.createTempFile("test_cleanup", "test"));

        assertEquals ("rm -Rf /tmp/cerebro\n" +
                "rm -f /tmp/cerebro.tgz\n" +
                "docker image rm eskimo/cerebro_template:latest || true\n", sshCommandServiceTest.getExecutedCommands());
    }

    @Test
    public void testInstallationSetup() throws Exception{

        String tempStorage = createTempStoragePath();

        File serviceFolder = new File (tempStorage, "cerebro");
        assertTrue (serviceFolder.mkdirs());

        FileUtils.writeFile(new File (serviceFolder, "setup.sh"), "#!/bin/bash\necho OK");

        sshCommandServiceTest.setConnectionResultBuilder((node, script) -> {
            throw new SSHCommandException("test error");
        });

        assertThrows(SystemException.class,
                () -> systemService.installationSetup(new TestMessageLogger(new StringBuilder()),
                        connectionManagerServiceTest.getPrivateConnection(Node.fromAddress("192.168.56.11")),
                        Node.fromAddress("192.168.56.11"),
                        Service.from("cerebro")));

        sshCommandServiceTest.setConnectionResultBuilder((node, script) -> "");

        systemService.installationSetup(new TestMessageLogger(new StringBuilder()),
                connectionManagerServiceTest.getPrivateConnection(Node.fromAddress("192.168.56.11")),
                Node.fromAddress("192.168.56.11"),
                Service.from("cerebro"));

        assertEquals(
                "bash /tmp/cerebro/setup.sh 192.168.56.11\n" +
                "bash /tmp/cerebro/setup.sh 192.168.56.11\n",
                sshCommandServiceTest.getExecutedCommands());
    }

    @Test
    public void testCallUninstallScript() throws Exception {

        String tempStorage = createTempStoragePath();

        File serviceFolder = new File (tempStorage, "cerebro");
        assertTrue (serviceFolder.mkdirs());

        FileUtils.writeFile(new File (serviceFolder, "uninstall.sh"), "#!/bin/bash\necho OK");

        systemService.setServicesSetupPath(tempStorage);

        assertThrows (SystemException.class,
                () -> systemService.callUninstallScript(
                    new TestMessageLogger(new StringBuilder()),
                    connectionManagerServiceTest.getPrivateConnection(Node.fromAddress("192.168.56.11")),
                    Service.from("grafana")));

        systemService.callUninstallScript(
                new TestMessageLogger(new StringBuilder()),
                connectionManagerServiceTest.getPrivateConnection(Node.fromAddress("192.168.56.11")),
                Service.from("cerebro"));

        assertTrue(sshCommandServiceTest.getExecutedCommands().contains("/cerebro/uninstall.sh"));
    }

    @Test
    public void testShowJournal() throws Exception {
        systemService.showJournal(servicesDefinition.getServiceDefinition(Service.from("ntp")), Node.fromAddress("192.168.10.11"));
        assertEquals ("sudo journalctl -u ntp --no-pager", sshCommandServiceTest.getExecutedCommands().trim());

        assertThrows(UnsupportedOperationException.class,
                () -> systemService.showJournal(servicesDefinition.getServiceDefinition(Service.from("grafana")), Node.fromAddress("192.168.10.11")));
    }

    @Test
    public void testStartService() throws Exception {
        systemService.startService(servicesDefinition.getServiceDefinition(Service.from("ntp")), Node.fromAddress("192.168.10.11"));
        assertEquals ("sudo bash -c 'systemctl reset-failed ntp && systemctl start ntp'", sshCommandServiceTest.getExecutedCommands().trim());

        assertThrows(UnsupportedOperationException.class,
                () -> systemService.startService(servicesDefinition.getServiceDefinition(Service.from("grafana")), Node.fromAddress("192.168.10.11")));
    }

    @Test
    public void testStopService() throws Exception {
        systemService.stopService(servicesDefinition.getServiceDefinition(Service.from("ntp")), Node.fromAddress("192.168.10.11"));
        assertEquals ("sudo systemctl stop ntp", sshCommandServiceTest.getExecutedCommands().trim());

        assertThrows(UnsupportedOperationException.class,
                () -> systemService.stopService(servicesDefinition.getServiceDefinition(Service.from("grafana")), Node.fromAddress("192.168.10.11")));
    }

    @Test
    public void testRestartService() throws Exception {
        systemService.restartService(servicesDefinition.getServiceDefinition(Service.from("ntp")), Node.fromAddress("192.168.10.11"));
        assertEquals ("sudo bash -c 'systemctl reset-failed ntp && systemctl restart ntp'", sshCommandServiceTest.getExecutedCommands().trim());

        assertThrows(UnsupportedOperationException.class,
                () -> systemService.restartService(servicesDefinition.getServiceDefinition(Service.from("grafana")), Node.fromAddress("192.168.10.11")));
    }

    @Test
    public void testCallCommand() throws Exception {

        SystemException exception = assertThrows(SystemException.class,
                () -> systemService.callCommand("dummy", Service.from("ntp"), Node.fromAddress("192.168.10.11")));
        assertNotNull(exception);
        assertEquals("Command dummy is unknown for service ntp", exception.getMessage());

        systemService.callCommand("show_log", Service.from("ntp"), Node.fromAddress("192.168.10.11"));
        assertEquals ("cat /var/log/ntp/ntp.log", sshCommandServiceTest.getExecutedCommands().trim());
    }

    @Test
    public void testGetStatus() throws Exception {
        systemService.setLastStatusForTest(null);
        systemService.setLastStatusExceptionForTest(null);

        assertEquals("{\"clear\": \"initializing\"}", systemService.getStatus().getFormattedValue());

        systemService.setLastStatusExceptionForTest(new CommonBusinessException("test"));

        assertThrows(SystemService.StatusExceptionWrapperException.class,
                () -> systemService.getStatus().getFormattedValue(),
                "ch.niceideas.common.exceptions.CommonBusinessException: test");
    }

    @Test
    public void testPerformPooledOperation() throws Exception {

        Set<String> result = new ConcurrentSkipListSet<>();
        systemService.performPooledOperation(
                new ArrayList<>(){{
                        add("test1");
                        add("test2");
                        add("test3");
                        add("test4");
                    }},
                2,
                100000,
                (SystemService.PooledOperation<String>) (operation, error) -> result.add(operation)
        );

        assertTrue (result.contains("test1"));
        assertTrue (result.contains("test2"));
        assertTrue (result.contains("test3"));
        assertTrue (result.contains("test4"));
    }

    @Test
    public void testCreateRemotePackageFolder() throws Exception {

        String tempStorage = createTempStoragePath();

        File serviceFolder = new File (tempStorage, "cerebro");
        assertTrue (serviceFolder.mkdirs());

        FileUtils.writeFile(new File (serviceFolder, "build.sh"), "#!/bin/bash\necho OK");

        systemService.setServicesSetupPath(tempStorage);

        File tempPackageDist = File.createTempFile("setup_service_test", "package_dist");
        assertTrue(tempPackageDist.delete());
        assertTrue(tempPackageDist.mkdirs());
        tempPackageDist.deleteOnExit();

        FileUtils.writeFile(new File (tempPackageDist, "cerebro_1.0_1.tar.gz"), "#!/bin/bash\necho OK");

        systemService.setPackageDistributionPath(tempPackageDist.getAbsolutePath());

        MessageLogger ml = new TestMessageLogger(new StringBuilder());
        systemService.createRemotePackageFolder(ml,
                connectionManagerServiceTest.getPrivateConnection(Node.fromAddress("192.168.56.11")),
                Service.from("cerebro"),
                "cerebro");

        assertTrue(sshCommandServiceTest.getExecutedScpCommands().contains("192.168.56.11:/tmp/cerebro"));
        assertTrue(sshCommandServiceTest.getExecutedScpCommands().contains("192.168.56.11:/tmp/setup_service_test"));

        //System.err.println (sshCommandServiceTest.getExecutedCommands());

        assertTrue (sshCommandServiceTest.getExecutedCommands().contains(
                "rm -Rf /tmp/cerebro\n" +
                "rm -f /tmp/cerebro.tgz"));

        assertTrue (sshCommandServiceTest.getExecutedCommands().contains(
                "tar xfz /tmp/cerebro.tgz --directory=/tmp/\n" +
                "chmod 755 /tmp/cerebro/setup.sh\n" +
                "mv cerebro_1.0_1.tar.gz /tmp/cerebro/\n" +
                "mv /tmp/cerebro/cerebro_1.0_1.tar.gz /tmp/cerebro/docker_template_cerebro.tar.gz"));
    }

    @Test
    public void testDiscoverAliveAndDeadNodes() {

        sshCommandServiceTest.setNodeResultBuilder((node, script) -> {
            if (script.equals("echo OK")) {
                if (node.equals(Node.fromAddress("192.168.10.11"))) {
                    throw new SSHCommandException("test");
                } else if (node.equals(Node.fromAddress("192.168.10.12"))) {
                    return "KO";
                } else {
                    return "OK";
                }
            }
            return "";
        });

        Set<Node> liveNodes = new HashSet<>();
        Set<Node> deadNodes = new HashSet<>();

        List<Pair<String, Node>> nodesSetup = systemService.discoverAliveAndDeadNodes(
                new HashSet<>(){{
                      add (Node.fromAddress("192.168.10.11"));
                      add (Node.fromAddress("192.168.10.12"));
                  }},
                StandardSetupHelpers.getStandard2NodesSetup(),
                liveNodes,
                deadNodes);

        assertTrue(liveNodes.contains(Node.fromAddress("192.168.10.13")));

        assertTrue(deadNodes.contains(Node.fromAddress("192.168.10.11")));
        assertTrue(deadNodes.contains(Node.fromAddress("192.168.10.12")));

        StringBuilder resultBuilder = new StringBuilder();
        nodesSetup.forEach(
                nodeSetup -> resultBuilder.append(nodeSetup.getKey()).append("-").append(nodeSetup.getValue()).append("\n")
        );
        assertEquals("node_setup-192.168.10.13\n" +
                "node_setup-192.168.10.12\n" +
                "node_setup-192.168.10.11\n", resultBuilder.toString());
    }

    @Test
    public void testUpdateStatus() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        configurationServiceTest.saveNodesConfig(nodesConfig);

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        configurationServiceTest.saveServicesInstallationStatus(servicesInstallStatus);

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        configurationServiceTest.saveKubernetesServicesConfig(kubeServicesConfig);

        sshCommandServiceTest.setNodeResultBuilder((node, script) -> {
            if (script.equals("echo OK")) {
                return "OK";
            }
            if (script.startsWith("sudo systemctl status --no-pager")) {
                return systemStatusTest;
            }
            if (script.startsWith("/usr/local/bin/kubectl get pod")) {
                try {
                    return StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/kubeCtlPods.txt"), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new CommonRTException(e);
                }

            } else if (script.startsWith("/usr/local/bin/kubectl get service")) {
                try {
                    return StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/kubeCtlServices.txt"), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new CommonRTException(e);
                }

            } else if (script.startsWith("/bin/ls -1")) {
                try {
                    return StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/lsl.txt"), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new CommonRTException(e);
                }

            }
            return "";
        });

        sshCommandServiceTest.setConnectionResultBuilder((connection, script) -> script);

        systemService.updateStatus();

        SystemStatusWrapper systemStatus = systemService.getStatus();

        JSONObject actual = systemStatus.getJSONObject();

        //System.err.println (actual.toString(2));

        assertTrue(new JSONObject(expectedFullStatus).similar(actual), actual.toString(2));
    }

    @Test
    public void testUpdateStatusNoKubernetes() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        configurationServiceTest.saveNodesConfig(nodesConfig);

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        configurationServiceTest.saveServicesInstallationStatus(servicesInstallStatus);

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        configurationServiceTest.saveKubernetesServicesConfig(kubeServicesConfig);

        sshCommandServiceTest.setNodeResultBuilder((node, script) -> {
            if (script.equals("echo OK")) {
                return "OK";
            }
            if (script.startsWith("sudo systemctl status --no-pager")) {
                return systemStatusTest;
            }
            return "";
        });

        sshCommandServiceTest.setConnectionResultBuilder((connection, script) -> script);

        systemService.updateStatus();

        SystemStatusWrapper systemStatus = systemService.getStatus();

        JSONObject actual = systemStatus.getJSONObject();

        //System.err.println (actual.toString(2));

        assertTrue(new JSONObject(expectedFullStatusNoKubernetes).similar(actual), actual.toString(2));
    }

    @Test
    public void testFetchNodeStatus() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        Map<String, String> statusMap = new HashMap<>();

        Pair<Integer, Node> nodeNumnberAndIpAddress = new Pair<>(1, Node.fromAddress("192.168.10.11"));

        sshCommandServiceTest.setNodeResultBuilder((node, script) -> {
            if (script.equals("echo OK")) {
                return "OK";
            }
            if (script.startsWith("sudo systemctl status --no-pager")) {
                return systemStatusTest;
            }
            return "";
        });

        sshCommandServiceTest.setConnectionResultBuilder((connection, script) -> script);

        systemService.fetchNodeStatus (nodesConfig, statusMap, nodeNumnberAndIpAddress, servicesInstallStatus);

        assertEquals(6, statusMap.size());

        assertNull(statusMap.get("service_kafka-manager_192-168-10-11")); // this is moved to Kubernetes
        assertEquals("OK", statusMap.get("node_alive_192-168-10-11"));
        assertEquals("OK", statusMap.get("service_etcd_192-168-10-11"));
        assertEquals("OK", statusMap.get("service_gluster_192-168-10-11"));
        assertEquals("OK", statusMap.get("service_ntp_192-168-10-11"));
    }

    @Test
    public void testFetchNodeStatusWithRestarts() throws Exception {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        Map<String, String> statusMap = new HashMap<>();

        Pair<Integer, Node> nbrAndPair = new Pair<>(1, Node.fromAddress("192.168.10.11"));

        sshCommandServiceTest.setNodeResultBuilder((node, script) -> {
            if (script.equals("echo OK")) {
                return "OK";
            }
            if (script.startsWith("sudo systemctl status --no-pager")) {
                return systemStatusTest;
            }
            return "";
        });

        sshCommandServiceTest.setConnectionResultBuilder((connection, script) -> script);

        servicesInstallStatus.setValueForPath("etcd_installed_on_IP_192-168-10-11", "restart");
        servicesInstallStatus.setValueForPath("gluster_installed_on_IP_192-168-10-11", "restart");

        systemService.fetchNodeStatus (nodesConfig, statusMap, nbrAndPair, servicesInstallStatus);

        assertEquals(6, statusMap.size());

        assertNull(statusMap.get("service_kafka-manager_192-168-10-11")); // kafka manager is moved to kubernetes
        assertEquals("OK", statusMap.get("node_alive_192-168-10-11"));
        assertEquals("restart", statusMap.get("service_etcd_192-168-10-11"));
        assertEquals("restart", statusMap.get("service_gluster_192-168-10-11"));
        assertEquals("OK", statusMap.get("service_ntp_192-168-10-11"));
    }

    @Test
    public void testCheckServiceDisappearance() {

        SystemStatusWrapper prevSystemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        prevSystemStatus.getJSONObject().remove("service_kafka-manager_192-168-10-11");

        systemService.setLastStatusForTest(prevSystemStatus);

        // we'll make it so that kibana and cerebro seem to have disappeared
        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        systemStatus.getJSONObject().remove("service_kafka-manager_192-168-10-11");
        systemStatus.setValueForPath("service_cerebro_192-168-10-11", "NA");
        systemStatus.setValueForPath("service_kibana_192-168-10-11", "KO");

        systemService.checkServiceDisappearance (systemStatus);

        Pair<Integer, List<JSONObject>> notifications = notificationService.fetchElements(0);

        assertNotNull (notifications);

        assertEquals(2, notifications.getKey().intValue());

        assertEquals("Error", notifications.getValue().get(0).getString("type"));
        assertEquals("Service cerebro on 192.168.10.11 got into problem", notifications.getValue().get(0).getString("message"));

        assertEquals("Error", notifications.getValue().get(1).getString("type"));
        assertEquals("Service kibana on 192.168.10.11 got into problem", notifications.getValue().get(1).getString("message"));
    }

    @Test
    public void testCheckServiceDisappearanceWithRestarts() {

        SystemStatusWrapper prevSystemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        prevSystemStatus.getJSONObject().remove("service_kafka-manager_192-168-10-11");

        systemService.setLastStatusForTest(prevSystemStatus);

        // we'll make it so that kibana and cerebro seem to have disappeared
        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        systemStatus.getJSONObject().remove("service_kafka-manager_192-168-10-11");
        systemStatus.setValueForPath("service_cerebro_192-168-10-11", "NA");
        systemStatus.setValueForPath("service_kibana_192-168-10-11", "KO");

        // flag a few services as restart => should not be reported as in issue
        systemStatus.setValueForPath("service_kafka_192-168-10-11", "restart");
        systemStatus.setValueForPath("service_kafka_192-168-10-13", "restart");
        systemStatus.setValueForPath("service_kafka-manager_192-168-10-11", "restart");

        systemService.checkServiceDisappearance (systemStatus);

        Pair<Integer, List<JSONObject>> notifications = notificationService.fetchElements(0);

        assertNotNull (notifications);

        assertEquals(2, notifications.getKey().intValue());

        assertEquals("Error", notifications.getValue().get(0).getString("type"));
        assertEquals("Service cerebro on 192.168.10.11 got into problem", notifications.getValue().get(0).getString("message"));

        assertEquals("Error", notifications.getValue().get(1).getString("type"));
        assertEquals("Service kibana on 192.168.10.11 got into problem", notifications.getValue().get(1).getString("message"));
    }

    @Test
    public void testCheckServiceDisappearanceNodeDown() {

        SystemStatusWrapper prevSystemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();

        systemService.setLastStatusForTest(prevSystemStatus);

        // we'll make it so that kibana and cerebro seem to have disappeared
        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        List<String> toBeremoved = new ArrayList<>();
        systemStatus.getRootKeys().stream()
                .filter(key -> key.contains("192-168-10-13") && !key.contains("nbr")  && !key.contains("alive"))
                .forEach(toBeremoved::add);
        toBeremoved
                .forEach(systemStatus::removeRootKey);
        systemStatus.setValueForPath(SystemStatusWrapper.NODE_ALIVE_FLAG + "192-168-10-13", "KO");

        systemService.checkServiceDisappearance (systemStatus);

        Pair<Integer, List<JSONObject>> notifications = notificationService.fetchElements(0);

        assertNotNull (notifications);

        assertEquals(1, notifications.getKey().intValue());

        assertEquals("Error", notifications.getValue().get(0).getString("type"));
        assertEquals("Service Node Alive on 192.168.10.13 got into problem", notifications.getValue().get(0).getString("message"));
    }

    @Test
    public void testHandleStatusChanges() throws Exception {

        Set<Node> configuredAndLiveIps = new HashSet<>(){{
            add(Node.fromAddress("192.168.10.11"));
            add(Node.fromAddress("192.168.10.13"));
        }};

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        systemStatus.getJSONObject().remove("service_etcd_192-168-10-11");

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // now I have changes
        resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();

        // etcd is missing
        System.err.println (resultPrevStatus.getFormattedValue());
        assertTrue (new JSONObject(expectedPrevStatusServicesRemoved).similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testHandleStatusChangesNodeDown() throws Exception {

        Set<Node> configuredAndLiveIps = new HashSet<>(){{
            add(Node.fromAddress("192.168.10.11"));
            add(Node.fromAddress("192.168.10.13"));
        }};

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();

        // remove all status for node 2 (except node down and node nbr)
        List<String> toBeremoved = new ArrayList<>();
        systemStatus.getRootKeys().stream()
                .filter(key -> key.contains("192-168-10-13") && !key.contains("nbr")  && !key.contains("alive"))
                .forEach(toBeremoved::add);
        toBeremoved
                .forEach(systemStatus::removeRootKey);
        systemStatus.setValueForPath("node_alive_192-168-10-13", "KO");

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // now I have changes
        resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();

        // nothing is removed (don't act on node down)
        System.err.println (resultPrevStatus.getFormattedValue());
        assertTrue (new JSONObject(expectedPrevStatusAllServicesStay).similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testHandleStatusChangesKubernetesService() throws Exception {

        Set<Node> configuredAndLiveIps = new HashSet<>(){{
            add(Node.fromAddress("192.168.10.11"));
            add(Node.fromAddress("192.168.10.13"));
        }};

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        systemStatus.getJSONObject().remove("service_cerebro_192-168-10-11");

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // now I have changes
        resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();

        JSONObject expectedPrevStatusJson = new JSONObject(expectedPrevStatusServicesRemoved);
        expectedPrevStatusJson.put("etcd_installed_on_IP_192-168-10-11", "OK"); // need to re-add this since the expectedPrevStatusServicesRemoved is for another test
        expectedPrevStatusJson.remove("cerebro_installed_on_IP_KUBERNETES_NODE");

        // cerebro is missing
        //System.err.println (expectedPrevStatusJson.toString(2));
        //System.err.println (resultPrevStatus.getJSONObject().toString(2));
        assertTrue (expectedPrevStatusJson.similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testHandleStatusChangesKubernetesServiceWhenKubernetesDown() throws Exception {

        Set<Node> configuredAndLiveIps = new HashSet<>(){{
            add(Node.fromAddress("192.168.10.11"));
            add(Node.fromAddress("192.168.10.13"));
        }};

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        systemStatus.getJSONObject().remove("service_cerebro_192-168-10-11");
        systemStatus.getJSONObject().put("service_kube-master_192-168-10-11", "KO");

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // Since the Kubernetes service status change has not been saved (since marazthon is down), it's still empty !!
        resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();

        assertEquals("{}", resultPrevStatus.getJSONObject().toString(2));
    }

    @Test
    public void testHandleStatusChangeMoreServicesRemoved() throws Exception {

        Set<Node> configuredAndLiveIps = new HashSet<>(){{
            add(Node.fromAddress("192.168.10.11"));
            add(Node.fromAddress("192.168.10.13"));
        }};

        // the objective here is to make sur that when a node entirely vanishes from configuration,
        // if it is still referenced by status, then it is checked for services and services are removed from it
        // if they are down
        // IMPORTANT : they should be kept if they are still there to be properly uninstalled indeed !

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        //logger.debug (systemStatus.getIpAddresses());

        // remove all status for node 2 : 192-168-10-13 EXCEPT KAFKA AND ELASTICSEARCH
        List<String> toBeremoved = new ArrayList<>();
        systemStatus.getRootKeys().stream()
                .filter(key -> key.contains("192-168-10-13") && !key.contains("kafka") && !key.contains("elasticsearch") && !key.contains("nbr")  && !key.contains("alive"))
                .forEach(toBeremoved::add);
        toBeremoved
                .forEach(systemStatus::removeRootKey);

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        //logger.debug (servicesInstallStatus.getIpAddresses());


        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // now I have changes
        resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();

        // kafka and elasticsearch have been kept
        System.err.println (resultPrevStatus.getFormattedValue());
        assertTrue(new JSONObject("{\n" +
                "    \"kafka-manager_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"logstash_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"kube-slave_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kibana_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"kafka_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"etcd_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"elasticsearch_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"ntp_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"cerebro_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"gluster_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kube-master_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"spark-runtime_installed_on_IP_KUBERNETES_NODE\": \"OK\"\n" +
                "}").similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testHandleStatusChangeWhenNodeRemovedFromConfig() throws Exception {

        Set<Node> configuredAndLiveIps = new HashSet<>(){{
            add(Node.fromAddress("192.168.10.11"));
            add(Node.fromAddress("192.168.10.13"));
        }};

        // the objective here is to make sur that when a node entirely vanishes from configuration,
        // if it is still referenced by status, then it is checked for services and services are removed from it
        // if they are down
        // IMPORTANT : they should be kept if they are still there to be properly uninstalled indeed !

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        //logger.debug (systemStatus.getIpAddresses());

        // remove all status for node 2 (really means it has been removed from config)
        List<String> toBeremoved = new ArrayList<>();
        systemStatus.getRootKeys().stream()
                .filter(key -> key.contains("192-168-10-13"))
                .forEach(toBeremoved::add);
        toBeremoved
                .forEach(systemStatus::removeRootKey);


        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        //logger.debug (servicesInstallStatus.getIpAddresses());

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // now I have changes
        resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();

        // kafka and elasticsearch have been kept
        System.err.println (resultPrevStatus.getFormattedValue());
        JSONObject expectedPrevStatusJson = new JSONObject(expectedPrevStatusAllServicesStay);
        assertTrue(expectedPrevStatusJson.similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testHandleStatusChangeWhenNodeRemovedFromConfigAndNodeIsDown() throws Exception {

        Set<Node> configuredAndLiveIps = new HashSet<>(){{
            add(Node.fromAddress("192.168.10.11"));
        }};

        // the objective here is to make sur that when a node entirely vanishes from configuration,
        // if it is still referenced by status, then it is checked for services and
        // if it is down, all services are removed without checking them
        // IMPORTANT : they should be kept if they are still there to be properly uninstalled indeed !

        SystemStatusWrapper systemStatus = StandardSetupHelpers.getStandard2NodesSystemStatus();
        logger.debug (systemStatus.getNodes());

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        logger.debug (servicesInstallStatus.getNodes());

        // remove all status for node 2
        List<String> toBeremoved = new ArrayList<>();
        systemStatus.getRootKeys().stream()
                .filter(key -> key.contains("192-168-10-13"))
                .forEach(toBeremoved::add);
        toBeremoved
                .forEach(systemStatus::removeRootKey);

        systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);

        // no changes so empty (since not saved !)
        ServicesInstallStatusWrapper resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();
        assertEquals ("{}", resultPrevStatus.getFormattedValue());

        // run it four more times
        for (int i = 0 ; i < 6; i++) {
            systemService.handleStatusChanges(servicesInstallStatus, systemStatus, configuredAndLiveIps);
        }

        // now I have changes
        resultPrevStatus = configurationServiceTest.loadServicesInstallationStatus();

        // everything has been removed
        System.err.println(resultPrevStatus.getFormattedValue());
        assertTrue(new JSONObject("{\n" +
                "    \"kafka-manager_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"logstash_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"kube-slave_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kibana_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"kafka_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"etcd_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"elasticsearch_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"ntp_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"cerebro_installed_on_IP_KUBERNETES_NODE\": \"OK\",\n" +
                "    \"gluster_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"kube-master_installed_on_IP_192-168-10-11\": \"OK\",\n" +
                "    \"spark-runtime_installed_on_IP_KUBERNETES_NODE\": \"OK\"\n" +
                "}").similar(resultPrevStatus.getJSONObject()));
    }

    @Test
    public void testApplyServiceOperation() throws Exception {

        AtomicBoolean called = new AtomicBoolean(false);

        systemService.applyServiceOperation(Service.from("ntp"), Node.fromAddress("192.168.10.11"), SimpleOperationCommand.SimpleOperation.COMMAND, () -> {
            called.set(true);
            return "OK";
        });

        assertTrue (called.get());

        List<JSONObject> result = notificationService.getSubList(0);

        assertEquals("Doing", result.get(0).getString("type"));
        assertEquals("Calling custom command ntp on 192.168.10.11", result.get(0).getString("message"));

        assertEquals("Info", result.get(1).getString("type"));
        assertEquals("Calling custom command ntp succeeded on 192.168.10.11", result.get(1).getString("message"));

        SimpleOperationCommand.SimpleOperationId operationId = new SimpleOperationCommand.SimpleOperationId(
                SimpleOperationCommand.SimpleOperation.COMMAND,
                Service.from("ntp"),
                Node.fromAddress("192.168.10.11"));

        List<String> messages = operationsMonitoringService.getNewMessages(operationId, 0);

        assertEquals ("[\n" +
                "Calling custom command ntp on 192.168.10.11, " +
                "Done Calling custom command ntp on 192.168.10.11, " +
                "-------------------------------------------------------------------------------, " +
                "OK" +
                "]", ""+messages);
    }

}

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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.MessageLogger;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.NodeServiceOperationsCommand;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.infrastructure.TestMessageLogger;
import ch.niceideas.eskimo.test.services.*;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({
        "no-web-stack",
        "test-setup",
        "test-conf",
        "test-system",
        "test-operation",
        "test-operations",
        "test-proxy",
        "test-kube",
        "test-ssh",
        "test-connection-manager",
        "test-services"})
public class NodesConfigurationServiceTest {

    private final String testRunUUID = UUID.randomUUID().toString();

    @Autowired
    private NodesConfigurationService nodesConfigurationService;

    @Autowired
    private SSHCommandServiceTestImpl sshCommandServiceTest;

    @Autowired
    private OperationsMonitoringServiceTestImpl operationsMonitoringServiceTest;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ConnectionManagerServiceTestImpl connectionManagerServiceTest;

    @Autowired
    private SystemOperationServiceTestImpl systemOperationServiceTest;

    @Autowired
    private SystemServiceTestImpl systemServiceTest;

    @BeforeEach
    public void setUp() throws Exception {
        SecurityContextHelper.loginAdmin();
        operationsMonitoringServiceTest.endCommand(true);
        connectionManagerServiceTest.dontConnect();
        sshCommandServiceTest.reset();
        systemServiceTest.reset();
        sshCommandServiceTest.setResult("");
        systemOperationServiceTest.setMockCalls(false);
        systemServiceTest.setMockCalls(false);
    }


    @Test
    public void testInstallEskimoBaseSystem() throws Exception {

        StringBuilder sb = new StringBuilder();
        MessageLogger ml = new TestMessageLogger(sb);
        nodesConfigurationService.installEskimoBaseSystem(ml, Node.fromAddress("192.168.10.11"));


        assertEquals (" - Calling install-eskimo-base-system.sh\n" +
                " - Copying jq program\n" +
                " - Copying script kube_do\n" +
                " - Copying script eskimo-kube-exec\n" +
                " - Copying script eskimo-edit-image.sh\n" +
                " - Copying script gluster-mount.sh\n" +
                " - Copying script eskimo-utils.sh\n" +
                " - Copying script glusterMountChecker.sh\n" +
                " - Copying script glusterMountCheckerPeriodic.sh\n" +
                " - Copying script inContainerMountGluster.sh\n" +
                " - Copying script settingsInjector.sh\n" +
                " - Copying script containerWatchDog.sh\n" +
                " - Copying script import-hosts.sh\n" +
                " - Copying eskimo-kubectl script\n", sb.toString());

        assertEquals ("192.168.10.11:./services_setup/base-eskimo/jq-1.6-linux64\n" +
                "192.168.10.11:./services_setup/base-eskimo/kube_do\n" +
                "192.168.10.11:./services_setup/base-eskimo/eskimo-kube-exec\n" +
                "192.168.10.11:./services_setup/base-eskimo/eskimo-edit-image.sh\n" +
                "192.168.10.11:./services_setup/base-eskimo/gluster-mount.sh\n" +
                "192.168.10.11:./services_setup/base-eskimo/eskimo-utils.sh\n" +
                "192.168.10.11:./services_setup/base-eskimo/glusterMountChecker.sh\n" +
                "192.168.10.11:./services_setup/base-eskimo/glusterMountCheckerPeriodic.sh\n" +
                "192.168.10.11:./services_setup/base-eskimo/inContainerMountGluster.sh\n" +
                "192.168.10.11:./services_setup/base-eskimo/settingsInjector.sh\n" +
                "192.168.10.11:./services_setup/base-eskimo/containerWatchDog.sh\n" +
                "192.168.10.11:./services_setup/base-eskimo/import-hosts.sh\n" +
                "192.168.10.11:./services_setup/base-eskimo/eskimo-kubectl", sshCommandServiceTest.getExecutedScpCommands().trim());

        assertEquals ("./services_setup/base-eskimo/install-eskimo-base-system.sh\n" +
                "sudo mv jq-1.6-linux64 /usr/local/bin/jq\n" +
                "sudo chown root.root /usr/local/bin/jq\n" +
                "sudo chmod 755 /usr/local/bin/jq\n" +
                "sudo mv kube_do /usr/local/bin/kube_do\n" +
                "sudo chown root.root /usr/local/bin/kube_do\n" +
                "sudo chmod 755 /usr/local/bin/kube_do\n" +
                "sudo mv eskimo-kube-exec /usr/local/bin/eskimo-kube-exec\n" +
                "sudo chown root.root /usr/local/bin/eskimo-kube-exec\n" +
                "sudo chmod 755 /usr/local/bin/eskimo-kube-exec\n" +
                "sudo mv eskimo-edit-image.sh /usr/local/bin/eskimo-edit-image.sh\n" +
                "sudo chown root.root /usr/local/bin/eskimo-edit-image.sh\n" +
                "sudo chmod 755 /usr/local/bin/eskimo-edit-image.sh\n" +
                "sudo mv gluster-mount.sh /usr/local/sbin/gluster-mount.sh\n" +
                "sudo chown root.root /usr/local/sbin/gluster-mount.sh\n" +
                "sudo chmod 755 /usr/local/sbin/gluster-mount.sh\n" +
                "sudo mv eskimo-utils.sh /usr/local/sbin/eskimo-utils.sh\n" +
                "sudo chown root.root /usr/local/sbin/eskimo-utils.sh\n" +
                "sudo chmod 755 /usr/local/sbin/eskimo-utils.sh\n" +
                "sudo mv glusterMountChecker.sh /usr/local/sbin/glusterMountChecker.sh\n" +
                "sudo chown root.root /usr/local/sbin/glusterMountChecker.sh\n" +
                "sudo chmod 755 /usr/local/sbin/glusterMountChecker.sh\n" +
                "sudo mv glusterMountCheckerPeriodic.sh /usr/local/sbin/glusterMountCheckerPeriodic.sh\n" +
                "sudo chown root.root /usr/local/sbin/glusterMountCheckerPeriodic.sh\n" +
                "sudo chmod 755 /usr/local/sbin/glusterMountCheckerPeriodic.sh\n" +
                "sudo mv inContainerMountGluster.sh /usr/local/sbin/inContainerMountGluster.sh\n" +
                "sudo chown root.root /usr/local/sbin/inContainerMountGluster.sh\n" +
                "sudo chmod 755 /usr/local/sbin/inContainerMountGluster.sh\n" +
                "sudo mv settingsInjector.sh /usr/local/sbin/settingsInjector.sh\n" +
                "sudo chown root.root /usr/local/sbin/settingsInjector.sh\n" +
                "sudo chmod 755 /usr/local/sbin/settingsInjector.sh\n" +
                "sudo mv containerWatchDog.sh /usr/local/sbin/containerWatchDog.sh\n" +
                "sudo chown root.root /usr/local/sbin/containerWatchDog.sh\n" +
                "sudo chmod 755 /usr/local/sbin/containerWatchDog.sh\n" +
                "sudo mv import-hosts.sh /usr/local/sbin/import-hosts.sh\n" +
                "sudo chown root.root /usr/local/sbin/import-hosts.sh\n" +
                "sudo chmod 755 /usr/local/sbin/import-hosts.sh\n" +
                "sudo mv eskimo-kubectl /usr/local/bin/eskimo-kubectl\n" +
                "sudo chown root.root /usr/local/bin/eskimo-kubectl\n" +
                "sudo chmod 755 /usr/local/bin/eskimo-kubectl\n", sshCommandServiceTest.getExecutedCommands());
    }

    @Test
    public void testGetNodeFlavour() throws Exception {

        sshCommandServiceTest.setResult("debian");
        assertEquals ("debian", nodesConfigurationService.getNodeFlavour(null));

        sshCommandServiceTest.setResult("redhat");
        assertEquals ("redhat", nodesConfigurationService.getNodeFlavour(null));

        sshCommandServiceTest.setResult("suse");
        assertEquals ("suse", nodesConfigurationService.getNodeFlavour(null));
    }

    @Test
    public void testCopyCommand() throws Exception {

        nodesConfigurationService.copyCommand("source", "target", null);

        assertEquals ("null:./services_setup/base-eskimo/source", sshCommandServiceTest.getExecutedScpCommands().trim());
        assertEquals ("sudo mv source target\n" +
                "sudo chown root.root target\n" +
                "sudo chmod 755 target\n", sshCommandServiceTest.getExecutedCommands());
    }

    @Test
    public void testInstallService() throws Exception {

        ServicesInstallStatusWrapper savedStatus = new ServicesInstallStatusWrapper(new HashMap<>() {{
                put("distributed-time_installed_on_IP_192-168-10-11", "OK");
                put("distributed-time_installed_on_IP_192-168-10-13", "OK");
        }});

        configurationServiceTest.saveServicesInstallationStatus(savedStatus);
        configurationServiceTest.setStandard2NodesSetup();

        NodeServiceOperationsCommand command = NodeServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedStatus, StandardSetupHelpers.getStandard2NodesSetup());

        operationsMonitoringServiceTest.startCommand(command);

        // testing cluster-manager installation
        nodesConfigurationService.installService(new NodeServiceOperationsCommand.ServiceOperationId(
                NodeServiceOperationsCommand.ServiceOperation.INSTALLATION,
                Service.from("cluster-manager"),
                Node.fromAddress("192.168.10.13")));

        String executedActions = String.join("\n", systemServiceTest.getExecutedActions());

        assertEquals ("" +
                "Installation setup  - cluster-manager - 192.168.10.13 - 192.168.10.13\n" +
                "Installation cleanup  - cluster-manager - cluster-manager - 192.168.10.13", executedActions);

        operationsMonitoringServiceTest.endCommand(true);
    }

    @Test
    public void testApplyNodesConfig() throws Exception {

        ServicesInstallStatusWrapper servicesInstallStatus = new ServicesInstallStatusWrapper(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/serviceInstallStatus.json"), StandardCharsets.UTF_8));
        configurationServiceTest.saveServicesInstallationStatus(servicesInstallStatus);

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper (StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/rawNodesConfig.json"), StandardCharsets.UTF_8));
        configurationServiceTest.saveNodesConfig(nodesConfig);

        configurationServiceTest.setStandardKubernetesConfig();

        NodeServiceOperationsCommand command = NodeServiceOperationsCommand.create(
                servicesDefinition,
                nodeRangeResolver,
                servicesInstallStatus,
                nodesConfig
        );

        sshCommandServiceTest.setNodeResultBuilder((node, script) -> {
            if (script.equals("echo OK")) {
                return "OK";
            }
            if (script.equals("if [[ -f /etc/debian_version ]]; then echo debian; fi")) {
                return "debian";
            }
            if (script.endsWith("cat /proc/meminfo | grep MemTotal")) {
                return "MemTotal:        9982656 kB";
            }
            return null;
        });

        sshCommandServiceTest.setConnectionResultBuilder((connection, script) -> {
            if (script.equals("cat /etc/eskimo_flag_base_system_installed")) {
                return "OK";
            }
            return null;
        });

        nodesConfigurationService.applyNodesConfig(command);

        String expectedCommandStart = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedCommandsStart.txt"), StandardCharsets.UTF_8);
        expectedCommandStart = expectedCommandStart.replace("{UUID}", testRunUUID);

        String expectedCommandEnd = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedCommandsEnd.txt"), StandardCharsets.UTF_8);
        expectedCommandEnd = expectedCommandEnd.replace("{UUID}", testRunUUID);

        String commandString = sshCommandServiceTest.getExecutedCommands();

        System.err.println (systemServiceTest.getExecutedActions());

        assertEquals ("Installation setup  - cluster-master - 192.168.10.15 - 192.168.10.15\n" +
                "Installation cleanup  - cluster-master - cluster-master - 192.168.10.15\n" +
                "call Uninstall script  - cluster-master - 192.168.10.13\n" +
                "Installation setup  - calculator-runtime - 192.168.10.15 - 192.168.10.15\n" +
                "Installation cleanup  - calculator-runtime - calculator - 192.168.10.15\n" +
                "Installation setup  - calculator-runtime - 192.168.10.13 - 192.168.10.13\n" +
                "Installation cleanup  - calculator-runtime - calculator - 192.168.10.13\n" +
                "Installation setup  - database - 192.168.10.15 - 192.168.10.15\n" +
                "Installation cleanup  - database - database - 192.168.10.15\n" +
                "Installation setup  - database - 192.168.10.13 - 192.168.10.13\n" +
                "Installation cleanup  - database - database - 192.168.10.13\n" +
                "Installation setup  - user-console - 192.168.10.15 - 192.168.10.15\n" +
                "Installation cleanup  - user-console - user-console - 192.168.10.15", String.join("\n", systemServiceTest.getExecutedActions()));

        for (String commandStart : expectedCommandStart.split("\n")) {
            assertTrue (commandString.contains(commandStart.replace("\r", "")), commandStart + "\nis contained in \n" + commandString);
        }

        for (String commandEnd : expectedCommandEnd.split("\n")) {
            assertTrue (commandString.contains(commandEnd.replace("\r", "")), commandEnd + "\nis contained in \n" + commandString);
        }

        //systemServiceTest.getExecutedActions()
    }

    @Test
    public void testUninstallation() throws Exception {

        ServicesInstallStatusWrapper savedStatus = new ServicesInstallStatusWrapper(new HashMap<>() {{
            put("distributed-time_installed_on_IP_192-168-10-11", "OK");
            put("distributed-time_installed_on_IP_192-168-10-13", "OK");
            put("cluster-manager_installed_on_IP_192-168-10-11", "OK");
        }});

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("distributed-time1", "on");
            put("node_id2", "192.168.10.13");
            put("distributed-time2", "on");
        }});

        configurationServiceTest.saveServicesInstallationStatus(savedStatus);
        configurationServiceTest.saveNodesConfig(nodesConfig);

        NodeServiceOperationsCommand command = NodeServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedStatus, nodesConfig);

        operationsMonitoringServiceTest.startCommand(command);

        // testing cluster-manager installation
        nodesConfigurationService.uninstallService(new NodeServiceOperationsCommand.ServiceOperationId(
                NodeServiceOperationsCommand.ServiceOperation.INSTALLATION,
                Service.from("cluster-manager"),
                Node.fromAddress("192.168.10.11")));

        //System.err.println (sshCommandServiceTest.getExecutedCommands());

        assertTrue(sshCommandServiceTest.getExecutedCommands().contains(
                "sudo systemctl stop cluster-manager\n" +
                "if [[ -d /lib/systemd/system/ ]]; then echo found_standard; fi\n" +
                "sudo rm -f  /usr/lib/systemd/system/cluster-manager.service\n" +
                "sudo docker rm -f cluster-manager || true \n" +
                "sudo bash -c '. /usr/local/sbin/eskimo-utils.sh && docker image rm -f eskimo/cluster-manager:$(get_last_tag cluster-manager)'\n" +
                "sudo systemctl daemon-reload\n" +
                "sudo systemctl reset-failed\n"));

        operationsMonitoringServiceTest.endCommand(true);
    }

    @Test
    public void testUninstallationWithPreUninstallHook() throws Exception {

        ServicesInstallStatusWrapper savedStatus = new ServicesInstallStatusWrapper(new HashMap<>() {{
            put("distributed-time_installed_on_IP_192-168-10-11", "OK");
            put("distributed-time_installed_on_IP_192-168-10-13", "OK");
            put("cluster-manager_installed_on_IP_192-168-10-11", "OK");
            put("cluster-master_installed_on_IP_192-168-10-11", "OK");
            put("cluster-slave_installed_on_IP_192-168-10-11", "OK");
            put("cluster-slave_installed_on_IP_192-168-10-13", "OK");
        }});

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("node_id2", "192.168.10.13");
            put("distributed-time1", "on");
            put("distributed-time2", "on");
            put("cluster-manager", "1");
            put("cluster-mmaster", "1");
            put("cluster-slave1", "on");
        }});

        configurationServiceTest.saveServicesInstallationStatus(savedStatus);
        configurationServiceTest.saveNodesConfig(nodesConfig);

        NodeServiceOperationsCommand command = NodeServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedStatus, nodesConfig);

        operationsMonitoringServiceTest.startCommand(command);

        // testing cluster-manager installation
        nodesConfigurationService.uninstallService(new NodeServiceOperationsCommand.ServiceOperationId(
                NodeServiceOperationsCommand.ServiceOperation.INSTALLATION,
                Service.from("cluster-slave"),
                Node.fromAddress("192.168.10.11")));

       assertEquals("Pre-uninstall hook  - cluster-slave\n" +
               "call Uninstall script  - cluster-slave - 192.168.10.11", String.join("\n", systemServiceTest.getExecutedActions()));

        operationsMonitoringServiceTest.endCommand(true);
    }

}

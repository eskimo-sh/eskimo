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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.AbstractBaseSSHTest;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.*;
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
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-setup", "test-conf", "test-system", "test-operation", "test-operations", "test-proxy", "test-kube", "test-ssh", "test-connection-manager"})
public class NodesConfigurationServiceTest {

    private String testRunUUID = UUID.randomUUID().toString();

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
        operationsMonitoringServiceTest.operationsFinished(true);
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
        MessageLogger ml = new MessageLogger() {
            @Override
            public void addInfo(String message) {
                if (StringUtils.isNotBlank(message)) {
                    sb.append(message).append("\n");
                }
            }

            @Override
            public void addInfo(String[] messages) {
                if (messages != null && messages.length > 0) {
                    for (String message : messages) {
                        sb.append(message).append("\n");
                    }
                }
            }
        };
        nodesConfigurationService.installEskimoBaseSystem(ml, "192.168.10.11");


        assertEquals (" - Calling install-eskimo-base-system.sh\n" +
                " - Copying jq program\n" +
                " - Copying gluster-mount script\n" +
                " - Copying eskimo-kubectl script\n", sb.toString());

        assertEquals ("192.168.10.11:./services_setup/base-eskimo/jq-1.6-linux64\n" +
                "192.168.10.11:./services_setup/base-eskimo/gluster_mount.sh\n" +
                "192.168.10.11:./services_setup/base-eskimo/eskimo-kubectl", sshCommandServiceTest.getExecutedScpCommands().toString().trim());

        assertEquals ("./services_setup/base-eskimo/install-eskimo-base-system.sh\n" +
                "sudo mv jq-1.6-linux64 /usr/local/bin/jq\n" +
                "sudo chown root.root /usr/local/bin/jq\n" +
                "sudo chmod 755 /usr/local/bin/jq\n" +
                "sudo mv gluster_mount.sh /usr/local/sbin/gluster_mount.sh\n" +
                "sudo chown root.root /usr/local/sbin/gluster_mount.sh\n" +
                "sudo chmod 755 /usr/local/sbin/gluster_mount.sh\n" +
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

        ServicesInstallStatusWrapper savedStatus = new ServicesInstallStatusWrapper(new HashMap<String, Object>() {{
                put("ntp_installed_on_IP_192-168-10-11", "OK");
                put("ntp_installed_on_IP_192-168-10-13", "OK");
        }});

        configurationServiceTest.saveServicesInstallationStatus(savedStatus);
        configurationServiceTest.setStandard2NodesSetup();

        ServiceOperationsCommand command = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedStatus, StandardSetupHelpers.getStandard2NodesSetup());

        operationsMonitoringServiceTest.operationsStarted(command);

        // testing zookeeper installation
        nodesConfigurationService.installService(new ServiceOperationsCommand.ServiceOperationId("installation", "zookeeper", "192.168.10.13"));

        String executedActions = String.join("\n", systemServiceTest.getExecutedActions());

        assertEquals ("" +
                "Installation setup  - zookeeper - 192.168.10.13 - 192.168.10.13\n" +
                "Installation cleanup  - zookeeper - zookeeper - 192.168.10.13", executedActions);

        operationsMonitoringServiceTest.operationsFinished(true);
    }

    @Test
    public void testApplyNodesConfig() throws Exception {

        ServicesInstallStatusWrapper servicesInstallStatus = new ServicesInstallStatusWrapper(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/serviceInstallStatus.json"), StandardCharsets.UTF_8));
        configurationServiceTest.saveServicesInstallationStatus(servicesInstallStatus);

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper (StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/rawNodesConfig.json"), StandardCharsets.UTF_8));
        configurationServiceTest.saveNodesConfig(nodesConfig);

        configurationServiceTest.setStandardKubernetesConfig();

        ServiceOperationsCommand command = ServiceOperationsCommand.create(
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

        assertEquals ("Installation setup  - kube-master - 192.168.10.15 - 192.168.10.15\n" +
                "Installation cleanup  - kube-master - kube-master - 192.168.10.15\n" +
                "Installation setup  - kube-slave - 192.168.10.15 - 192.168.10.15\n" +
                "Installation cleanup  - kube-slave - null - 192.168.10.15\n" +
                "Installation setup  - kube-slave - 192.168.10.13 - 192.168.10.13\n" +
                "Installation cleanup  - kube-slave - null - 192.168.10.13\n" +
                "Installation setup  - spark-console - 192.168.10.15 - 192.168.10.15\n" +
                "Installation cleanup  - spark-console - spark - 192.168.10.15\n" +
                "Installation setup  - elasticsearch - 192.168.10.15 - 192.168.10.15\n" +
                "Installation cleanup  - elasticsearch - elasticsearch - 192.168.10.15\n" +
                "Installation setup  - elasticsearch - 192.168.10.13 - 192.168.10.13\n" +
                "Installation cleanup  - elasticsearch - elasticsearch - 192.168.10.13\n" +
                "Installation setup  - spark-runtime - 192.168.10.15 - 192.168.10.15\n" +
                "Installation cleanup  - spark-runtime - spark - 192.168.10.15\n" +
                "Installation setup  - spark-runtime - 192.168.10.13 - 192.168.10.13\n" +
                "Installation cleanup  - spark-runtime - spark - 192.168.10.13\n" +
                "Installation setup  - zeppelin - 192.168.10.15 - 192.168.10.15\n" +
                "Installation cleanup  - zeppelin - zeppelin - 192.168.10.15", String.join("\n", systemServiceTest.getExecutedActions()));

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

        ServicesInstallStatusWrapper savedStatus = new ServicesInstallStatusWrapper(new HashMap<String, Object>() {{
            put("ntp_installed_on_IP_192-168-10-11", "OK");
            put("ntp_installed_on_IP_192-168-10-13", "OK");
            put("zookeeper_installed_on_IP_192-168-10-11", "OK");
        }});

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("ntp1", "on");
            put("node_id2", "192.168.10.13");
            put("ntp2", "on");
        }});

        configurationServiceTest.saveServicesInstallationStatus(savedStatus);
        configurationServiceTest.saveNodesConfig(nodesConfig);

        ServiceOperationsCommand command = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedStatus, nodesConfig);

        operationsMonitoringServiceTest.operationsStarted(command);

        // testing zookeeper installation
        nodesConfigurationService.uninstallService(new ServiceOperationsCommand.ServiceOperationId("uninstallation", "zookeeper", "192.168.10.11"));

        assertTrue(sshCommandServiceTest.getExecutedCommands().contains(
                "sudo systemctl stop zookeeper\n" +
                "if [[ -d /lib/systemd/system/ ]]; then echo found_standard; fi\n" +
                "sudo rm -f  /usr/lib/systemd/system/zookeeper.service\n" +
                "sudo docker rm -f zookeeper || true \n" +
                "sudo docker image rm -f eskimo:zookeeper\n" +
                "sudo systemctl daemon-reload\n" +
                "sudo systemctl reset-failed\n"));

        operationsMonitoringServiceTest.operationsFinished(true);
    }

}

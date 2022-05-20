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
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NodesConfigurationServiceTest extends AbstractSystemTest {

    private String testRunUUID = UUID.randomUUID().toString();

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setupService.setConfigStoragePathInternal(SystemServiceTest.createTempStoragePath());
    }

    @Override
    protected SystemService createSystemService() {
        SystemService ss = new SystemService(false) {
            @Override
            protected File createTempFile(String serviceOrFlag, String node, String extension) throws IOException {
                File retFile = new File (System.getProperty("java.io.tmpdir") + "/" + serviceOrFlag+"-"+testRunUUID+"-"+ node +extension);
                retFile.createNewFile();
                return retFile;
            }
        };
        ss.setConfigurationService(configurationService);
        return ss;
    }

    @Override
    protected SetupService createSetupService() {
        return new SetupService() {
            @Override
            public String findLastPackageFile(String prefix, String packageName) {
                return prefix+"_"+packageName+"_dummy_1.dummy";
            }
        };
    }

    @Test
    public void testInstallEskimoBaseSystem() throws Exception {

        nodesConfigurationService.setConnectionManagerService(new ConnectionManagerService() {
            @Override
            public void forceRecreateConnection(String node) {
                // No-Op
            }
            @Override
            public SSHConnection getPrivateConnection (String node) {
                return null;
            }
            @Override
            public SSHConnection getSharedConnection (String node) {
                return null;
            }
        });

        StringBuilder sb = new StringBuilder();
        MessageLogger ml = new MessageLogger() {
            @Override
            public void addInfo(String message) {
                if (StringUtils.isNotBlank(message)) {
                    sb.append(message + "\n");
                }
            }

            @Override
            public void addInfo(String[] messages) {
                if (messages != null && messages.length > 0) {
                    for (String message : messages) {
                        sb.append(message + "\n");
                    }
                }
            }
        };
        nodesConfigurationService.installEskimoBaseSystem(ml, "192.168.10.11");


        assertEquals (" - Calling install-eskimo-base-system.sh\n" +
                " - Copying jq program\n" +
                " - Copying gluster-mount script\n" +
                " - Copying eskimo-kubectl script\n", sb.toString());

        assertEquals ("192.168.10.11-./services_setup/base-eskimo/jq-1.6-linux64\n" +
                "192.168.10.11-./services_setup/base-eskimo/gluster_mount.sh\n" +
                "192.168.10.11-./services_setup/base-eskimo/eskimo-kubectl", testSCPCommands.toString().trim());

        assertTrue (testSSHCommandScript.toString().startsWith("#!/bin/bash\n"));
    }

    @Test
    public void testGetNodeFlavour() throws Exception {

        AtomicReference<String> command = new AtomicReference<>();

        nodesConfigurationService.setSshCommandService(new SSHCommandService() {
            @Override
            public String runSSHScript(SSHConnection connection, String script){
                return (String) command.get();
            }
        });

        command.set("debian");
        assertEquals ("debian", nodesConfigurationService.getNodeFlavour(null));

        command.set("redhat");
        assertEquals ("redhat", nodesConfigurationService.getNodeFlavour(null));

        command.set("suse");
        assertEquals ("suse", nodesConfigurationService.getNodeFlavour(null));
    }

    @Test
    public void testCopyCommand() throws Exception {

        nodesConfigurationService.copyCommand("source", "target", null);

        assertEquals ("192.168.10.11-./services_setup/base-eskimo/source", testSCPCommands.toString().trim());
        assertEquals ("sudo mv source target \n" +
                "sudo chown root.root target \n" +
                "sudo chmod 755 target \n", testSSHCommandScript.toString());
    }

    @Test
    public void testInstallService() throws Exception {

        ServicesInstallStatusWrapper savedStatus = new ServicesInstallStatusWrapper(new HashMap<String, Object>() {{
                put("ntp_installed_on_IP_192-168-10-11", "OK");
                put("ntp_installed_on_IP_192-168-10-13", "OK");
        }});

        configurationService.saveServicesInstallationStatus(savedStatus);
        configurationService.saveNodesConfig(StandardSetupHelpers.getStandard2NodesSetup());

        ServiceOperationsCommand command = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedStatus, StandardSetupHelpers.getStandard2NodesSetup());

        operationsMonitoringService.operationsStarted(command);

        // testing zookeeper installation
        nodesConfigurationService.installService(new ServiceOperationsCommand.ServiceOperationId("installation", "zookeeper", "192.168.10.13"));

        assertTrue(testSSHCommandScript.toString().startsWith("rm -Rf /tmp/zookeeper\n" +
                "rm -f /tmp/zookeeper.tgz\n"));

        assertTrue(testSSHCommandScript.toString().contains(
                "tar xfz /tmp/zookeeper.tgz --directory=/tmp/\n" +
                "chmod 755 /tmp/zookeeper/setup.sh\n"));
        assertTrue(testSSHCommandScript.toString().contains(
                "bash /tmp/zookeeper/setup.sh 192.168.10.13 \n" +
                "rm -Rf /tmp/zookeeper\n" +
                "rm -f /tmp/zookeeper.tgz\n"));

        operationsMonitoringService.operationsFinished(true);
    }

    @Test
    public void testApplyNodesConfig() throws Exception {

        ServicesInstallStatusWrapper servicesInstallStatus = new ServicesInstallStatusWrapper(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/serviceInstallStatus.json"), "UTF-8"));
        configurationService.saveServicesInstallationStatus(servicesInstallStatus);

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper (StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/rawNodesConfig.json"), "UTF-8"));
        configurationService.saveNodesConfig(nodesConfig);

        configurationService.saveKubernetesServicesConfig(StandardSetupHelpers.getStandardKubernetesConfig());

        ServiceOperationsCommand command = ServiceOperationsCommand.create(
                servicesDefinition,
                nodeRangeResolver,
                servicesInstallStatus,
                nodesConfig
        );

        SSHCommandService sshCommandService = new SSHCommandService() {
            @Override
            public synchronized String runSSHScript(SSHConnection connection, String script, boolean throwsException) {
                return runSSHScript((String)null, script, throwsException);
            }
            @Override
            public synchronized String runSSHScript(String node, String script, boolean throwsException) {
                testSSHCommandScript.append(script).append("\n");
                if (script.equals("echo OK")) {
                    return "OK";
                }
                if (script.equals("if [[ -f /etc/debian_version ]]; then echo debian; fi")) {
                    return "debian";
                }
                if (script.endsWith("cat /proc/meminfo | grep MemTotal")) {
                    return "MemTotal:        9982656 kB";
                }

                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public synchronized String runSSHCommand(SSHConnection connection, String command) {
                return runSSHCommand((String)null, command);
            }
            @Override
            public synchronized String runSSHCommand(String node, String command) {
                testSSHCommandScript.append(command).append("\n");
                if (command.equals("cat /etc/eskimo_flag_base_system_installed")) {
                    return "OK";
                }

                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public synchronized void copySCPFile(SSHConnection connection, String filePath) {
                // just do nothing
            }
            @Override
            public synchronized void copySCPFile(String node, String filePath) {
                // just do nothing
            }
        };

        nodesConfigurationService.setKubernetesService(new KubernetesService() {

            @Override
            protected String restartServiceInternal(Service service, String node) throws KubernetesException, SSHCommandException {
                // No-Op
                return null;
            }
        });

        systemService.setSshCommandService(sshCommandService);

        memoryComputer.setSshCommandService(sshCommandService);

        nodesConfigurationService.setSshCommandService(sshCommandService);

        nodesConfigurationService.setConnectionManagerService(new ConnectionManagerService() {
            @Override
            public void forceRecreateConnection(String node) {
                // no Op
            }
            @Override
            public SSHConnection getPrivateConnection (String node) {
                return null;
            }
            @Override
            public SSHConnection getSharedConnection (String node)  {
                return null;
            }
        });

        nodesConfigurationService.applyNodesConfig(command);

        String expectedCommandStart = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedCommandsStart.txt"), "UTF-8");
        expectedCommandStart = expectedCommandStart.replace("{UUID}", testRunUUID);

        String expectedCommandEnd = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedCommandsEnd.txt"), "UTF-8");
        expectedCommandEnd = expectedCommandEnd.replace("{UUID}", testRunUUID);

        String commandString = testSSHCommandScript.toString();

        for (String commandStart : expectedCommandStart.split("\n")) {
            assertTrue (commandString.contains(commandStart), commandStart + "\nis contained in \n" + commandString);
        }

        for (String commandEnd : expectedCommandEnd.split("\n")) {
            assertTrue (commandString.contains(commandEnd), commandEnd + "\nis contained in \n" + commandString);
        }
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

        configurationService.saveServicesInstallationStatus(savedStatus);
        configurationService.saveNodesConfig(nodesConfig);

        ServiceOperationsCommand command = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savedStatus, nodesConfig);

        operationsMonitoringService.operationsStarted(command);


        // testing zookeeper installation
        nodesConfigurationService.uninstallService(new ServiceOperationsCommand.ServiceOperationId("uninstallation", "zookeeper", "192.168.10.11"));

        assertTrue(testSSHCommandScript.toString().contains(
                "sudo systemctl stop zookeeper\n" +
                "if [[ -d /lib/systemd/system/ ]]; then echo found_standard; fi\n" +
                "sudo rm -f  /usr/lib/systemd/system/zookeeper.service\n" +
                "sudo docker rm -f zookeeper || true \n" +
                "sudo docker image rm -f eskimo:zookeeper\n" +
                "sudo systemctl daemon-reload\n" +
                "sudo systemctl reset-failed\n"));

        operationsMonitoringService.operationsFinished(true);
    }

}

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

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.proxy.WebSocketProxyServer;
import com.trilead.ssh2.Connection;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Set;

public abstract class AbstractSystemTest {

    private static final Logger logger = Logger.getLogger(AbstractSystemTest.class);

    protected SetupService setupService;

    protected SystemService systemService = null;

    protected NotificationService notificationService = null;

    protected ProxyManagerService proxyManagerService = null;

    protected ServicesDefinition servicesDefinition = null;

    protected SystemOperationService systemOperationService = null;

    protected NodeRangeResolver nodeRangeResolver = null;

    protected ServicesInstallationSorter servicesInstallationSorter = null;

    protected ServicesSettingsService servicesSettingsService = null;

    protected MemoryComputer memoryComputer = null;

    protected ConfigurationService configurationService = null;

    protected KubernetesService kubernetesService = null;

    protected SSHCommandService sshCommandService = null;

    protected NodesConfigurationService nodesConfigurationService = null;

    protected ApplicationStatusService applicationStatusService = null;

    protected ConnectionManagerService connectionManagerService;

    protected OperationsMonitoringService operationsMonitoringService;

    protected StringBuilder testSSHCommandResultBuilder = new StringBuilder(10000);
    protected StringBuilder testSSHCommandScript = new StringBuilder(10000);
    protected StringBuilder testSCPCommands = new StringBuilder(10000);

    protected String systemStatusTest = null;
    protected String expectedFullStatus = null;
    protected String expectedPrevStatusServicesRemoved = null;
    protected String expectedPrevStatusAllServicesStay = null;


    @BeforeEach
    public void setUp() throws Exception {

        systemStatusTest = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/systemStatusTest.log"), "UTF-8");
        expectedFullStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedFullStatus.json"), "UTF-8");
        expectedPrevStatusServicesRemoved = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedPrevStatusServicesRemoved.json"), "UTF-8");
        expectedPrevStatusAllServicesStay = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedPrevStatusAllServicesStay.json"), "UTF-8");

        clearResultBuilder();
        clearCommandScript();

        servicesDefinition = new ServicesDefinition() {
            @Override
            public String getAllServicesString() {
                return "kafka zookeeper ntp kube-master kube-slave kubernetes-dashboard spark-runtime kibana cerebro zeppelin kafka-manager gluster spark-history-server elasticsearch";
            }
            @Override
            public String[] listAllServices() {
                return new String[] {"kafka",  "zookeeper", "ntp", "kube-master", "kube-slave", "etcd", "kubernetes-dashboard", "spark-runtime", "kibana", "cerebro", "zeppelin", "kafka-manager", "gluster", "spark-history-server", "elasticsearch"};
            }
        };
        servicesDefinition.afterPropertiesSet();

        configurationService = createConfigurationService();

        nodeRangeResolver = new NodeRangeResolver();

        proxyManagerService = createProxyManagerService();
        proxyManagerService.setServicesDefinition(servicesDefinition);

        proxyManagerService.setConnectionManagerService(new ConnectionManagerService() {
            @Override
            public void recreateTunnels(String host) {
            }
        });
        proxyManagerService.setWebSocketProxyServer(new WebSocketProxyServer(proxyManagerService, servicesDefinition) {
            @Override
            public void removeForwardersForService(String serviceId) {
            }
        });

        setupService = createSetupService();
        servicesDefinition.setSetupService(setupService);
        configurationService.setSetupService(setupService);
        configurationService.setServicesDefinition (servicesDefinition);

        setupService.setConfigurationService (configurationService);

        applicationStatusService = new ApplicationStatusService();
        applicationStatusService.setConfigurationService(configurationService);
        applicationStatusService.setServicesDefinition(servicesDefinition);

        setupService.setApplicationStatusService(applicationStatusService);

        systemService = createSystemService();

        sshCommandService = new SSHCommandService() {
            @Override
            public String runSSHScript(SSHConnection connection, String script, boolean throwsException) {
                return runSSHScript("192.168.10.11", script, throwsException);
            }
            @Override
            public String runSSHScript(String node, String script, boolean throwsException) {
                if (script.equals("echo OK")) {
                    return "OK";
                }
                if (script.endsWith("cat /proc/meminfo | grep MemTotal")) {
                    switch (node) {
                        case "192.168.10.11":
                            return "MemTotal:        5969796 kB";
                        case "192.168.10.12":
                            return "MemTotal:        5799444 kB";
                        default:
                            return "MemTotal:        3999444 kB";
                    }
                }
                if (script.equals("grep 'I am the new leader' /var/log/gluster/egmi/egmi.log")) {
                    if (node.equals("192.168.10.11")) {
                        return "2021-01-17 22:44:05,633 INFO c.n.e.e.c.CommandServer [http-nio-28901-exec-4] About to execute command: volume - subcommand: status - options: all detail\n" +
                                "2021-01-17 22:44:36,564 INFO c.n.e.e.c.CommandServer [http-nio-28901-exec-6] About to execute command: pool - subcommand: list - options: \n" +
                                "2021-01-17 22:44:36,682 INFO c.n.e.e.c.CommandServer [http-nio-28901-exec-7] About to execute command: volume - subcommand: info - options: \n" +
                                "2021-01-17 22:44:36,746 INFO c.n.e.e.c.CommandServer [http-nio-28901-exec-8] About to execute command: volume - subcommand: status - options: all detail\n" +
                                "2021-01-17 22:44:52,559 INFO c.n.e.e.z.ElectionProcess$ProcessNodeWatcher [Thread-1-EventThread] [Process: test-node1] Event received: WatchedEvent state:SyncConnected type:NodeDeleted path:/egmi/egmi_election/p_0000000000\n" +
                                "2021-01-17 22:44:52,561 INFO c.n.e.e.z.ElectionProcess [Thread-1-EventThread] [Process: test-node1] I am the new leader!\n" +
                                "2021-01-17 22:44:52,566 INFO c.n.e.e.z.ElectionProcess$ProcessNodeWatcher [Thread-1-EventThread] [Process: test-node1] Event received: WatchedEvent state:SyncConnected type:NodeDataChanged path:/egmi/master_id\n" +
                                "2021-01-17 22:44:52,567 INFO c.n.e.e.z.ElectionProcess$ProcessNodeWatcher [Thread-1-EventThread] [Process: test-node1] Master changed: test-node1\n" +
                                "2021-01-17 22:44:59,136 INFO c.n.e.e.m.ManagementService [pool-2-thread-1] - Updating System Status\n" +
                                "2021-01-17 22:44:59,360 INFO c.n.e.e.c.CommandServer [http-nio-28901-exec-10] About to execute command: pool - subcommand: list - options: \n" +
                                "2021-01-17 22:44:59,449 INFO c.n.e.e.c.CommandServer [http-nio-28901-exec-1] About to execute command: volume - subcommand: info - options: \n";
                    }
                }
                testSSHCommandScript.append(script).append("\n");
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public String runSSHCommand(SSHConnection connection, String command) {
                return runSSHCommand("192.168.10.11", command);
            }
            @Override
            public String runSSHCommand(String node, String command) {
                testSSHCommandScript.append(command).append("\n");
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public void copySCPFile(SSHConnection connection, String filePath) {
                copySCPFile("192.168.10.11", filePath);
            }
            @Override
            public void copySCPFile(String node, String filePath) {
                testSCPCommands.append(node).append("-").append(filePath).append("\n");
            }
        };

        setupService.setSystemService(systemService);

        notificationService = new NotificationService();

        operationsMonitoringService = new OperationsMonitoringService();
        operationsMonitoringService.setNotificationService(notificationService);
        operationsMonitoringService.setConfigurationService(configurationService);
        operationsMonitoringService.setNodeRangeResolver(nodeRangeResolver);

        setupService.setOperationsMonitoringService(operationsMonitoringService);

        systemOperationService = new SystemOperationService();
        systemOperationService.setNotificationService(notificationService);
        systemOperationService.setSystemService(systemService);
        systemOperationService.setConfigurationService(configurationService);
        systemOperationService.setOperationsMonitoringService(operationsMonitoringService);

        setupService.setSystemOperationService (systemOperationService);

        memoryComputer = new MemoryComputer();
        memoryComputer.setServicesDefinition(servicesDefinition);
        memoryComputer.setSshCommandService(sshCommandService);

        servicesInstallationSorter = new ServicesInstallationSorter ();
        servicesInstallationSorter.setServicesDefinition(servicesDefinition);
        servicesInstallationSorter.setConfigurationService (configurationService);

        operationsMonitoringService.setServicesInstallationSorter(servicesInstallationSorter);

        servicesSettingsService = new ServicesSettingsService();
        servicesSettingsService.setSystemService(systemService);
        servicesSettingsService.setSystemOperationService(systemOperationService);
        servicesSettingsService.setMemoryComputer(memoryComputer);
        servicesSettingsService.setOperationsMonitoringService(operationsMonitoringService);
        servicesSettingsService.setServicesInstallationSorter(servicesInstallationSorter);
        servicesSettingsService.setConfigurationService(configurationService);
        servicesSettingsService.setNodesConfigurationService(nodesConfigurationService);
        servicesSettingsService.setServicesDefinition(servicesDefinition);

        connectionManagerService = new ConnectionManagerService() {
            @Override
            public SSHConnection getPrivateConnection (String node) {
                return null;
            }

            @Override
            public SSHConnection getSharedConnection (String node) {
                return null;
            }
        };

        kubernetesService = createKubernetesService();
        kubernetesService.setServicesDefinition(servicesDefinition);
        kubernetesService.setConfigurationService (configurationService);
        kubernetesService.setSystemService(systemService);
        kubernetesService.setSshCommandService(sshCommandService);
        kubernetesService.setSystemOperationService(systemOperationService);
        kubernetesService.setProxyManagerService(proxyManagerService);
        kubernetesService.setMemoryComputer(memoryComputer);
        kubernetesService.setNotificationService(notificationService);
        kubernetesService.setConnectionManagerService (connectionManagerService);
        kubernetesService.setOperationsMonitoringService(operationsMonitoringService);

        systemService.setNodeRangeResolver(nodeRangeResolver);
        systemService.setSetupService(setupService);
        systemService.setProxyManagerService(proxyManagerService);
        systemService.setSshCommandService(sshCommandService);
        systemService.setServicesDefinition(servicesDefinition);
        systemService.setKubernetesService(kubernetesService);
        systemService.setNotificationService(notificationService);
        systemService.setConfigurationService(configurationService);
        systemService.setOperationsMonitoringService(operationsMonitoringService);

        nodesConfigurationService = createNodesConfigurationService();
        nodesConfigurationService.setConfigurationService(configurationService);
        nodesConfigurationService.setKubernetesService (kubernetesService);
        nodesConfigurationService.setMemoryComputer(memoryComputer);
        nodesConfigurationService.setNodeRangeResolver(nodeRangeResolver);
        nodesConfigurationService.setProxyManagerService(proxyManagerService);
        nodesConfigurationService.setServicesDefinition(servicesDefinition);
        nodesConfigurationService.setServicesInstallationSorter(servicesInstallationSorter);
        nodesConfigurationService.setSetupService(setupService);
        nodesConfigurationService.setSystemService (systemService);
        nodesConfigurationService.setSshCommandService(sshCommandService);
        nodesConfigurationService.setSystemOperationService(systemOperationService);
        nodesConfigurationService.setConnectionManagerService(connectionManagerService);
        nodesConfigurationService.setOperationsMonitoringService(operationsMonitoringService);
    }

    protected ProxyManagerService createProxyManagerService() {
        return new ProxyManagerService();
    }

    protected ConfigurationService createConfigurationService() {
        return new ConfigurationService();
    }

    protected SetupService createSetupService() {
        return new SetupService(){

            @Override
            protected void dowloadFile(MessageLogger ml, File destinationFile, URL downloadUrl, String message) throws IOException {
                destinationFile.createNewFile();
                try {
                    FileUtils.writeFile(destinationFile, "TEST DOWNLOADED CONTENT");
                } catch (FileException e) {
                    logger.debug (e, e);
                    throw new IOException(e);
                }
            }
        };
    }

    protected SystemService createSystemService() {
        return new SystemService(false);
    }

    protected NodesConfigurationService createNodesConfigurationService () {
        return new NodesConfigurationService();
    }

    protected KubernetesService createKubernetesService() {
        return new KubernetesService() {
            /* FIXME override kubectl command calling */
        };
    }

    protected void clearCommandScript() {
        testSSHCommandScript.delete(0, testSSHCommandScript.length());
    }

    protected void clearResultBuilder() {
        testSSHCommandResultBuilder.delete(0, testSSHCommandResultBuilder.length());
    }

}

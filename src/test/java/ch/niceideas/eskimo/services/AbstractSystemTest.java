/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.eskimo.model.MemoryModel;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.proxy.WebSocketProxyServer;
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

    protected MessagingService messagingService = null;

    protected ProxyManagerService proxyManagerService = null;

    protected ServicesDefinition servicesDefinition = null;

    protected SystemOperationService systemOperationService = null;

    protected NodeRangeResolver nodeRangeResolver = null;

    protected ServicesInstallationSorter servicesInstallationSorter = null;

    protected ServicesSettingsService servicesSettingsService = null;

    protected MemoryComputer memoryComputer = null;

    protected ConfigurationService configurationService = null;

    protected MarathonService marathonService = null;

    protected SSHCommandService sshCommandService = null;

    protected NodesConfigurationService nodesConfigurationService = null;

    protected ApplicationStatusService applicationStatusService = null;

    protected StringBuilder testSSHCommandResultBuilder = new StringBuilder();
    protected StringBuilder testSSHCommandScript = new StringBuilder();
    protected StringBuilder testSCPCommands = new StringBuilder();

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
                return "kafka zookeeper ntp mesos-master spark-executor kibana cerebro zeppelin kafka-manager gluster spark-history-server";
            }
            @Override
            public String[] listAllServices() {
                return new String[] {"kafka",  "zookeeper", "ntp", "mesos-master", "spark-executor", "kibana", "cerebro", "zeppelin", "kafka-manager", "gluster", "spark-history-server", "elasticsearch"};
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
            public String runSSHScript(String hostAddress, String script, boolean throwsException) {
                if (script.endsWith("cat /proc/meminfo | grep MemTotal")) {
                    switch (hostAddress) {
                        case "192.168.10.11":
                            return "MemTotal:        5969796 kB";
                        case "192.168.10.12":
                            return "MemTotal:        5799444 kB";
                        default:
                            return "MemTotal:        3999444 kB";
                    }
                }
                testSSHCommandScript.append(script).append("\n");
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public String runSSHCommand(String hostAddress, String command) {
                testSSHCommandScript.append(command).append("\n");
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public void copySCPFile(String hostAddress, String filePath) {
                testSCPCommands.append(hostAddress).append("-").append(filePath).append("\n");
            }
        };

        setupService.setSystemService(systemService);

        messagingService = new MessagingService();

        notificationService = new NotificationService();

        systemOperationService = new SystemOperationService();
        systemOperationService.setNotificationService(notificationService);
        systemOperationService.setMessagingService(messagingService);
        systemOperationService.setSystemService(systemService);
        systemOperationService.setConfigurationService(configurationService);

        setupService.setSystemOperationService (systemOperationService);

        memoryComputer = new MemoryComputer() {
            @Override
            public MemoryModel buildMemoryModel(NodesConfigWrapper nodesConfig, Set<String> deadIps) throws SystemException {
                return new MemoryModel(computeMemory(nodesConfig, deadIps));
            }
        };
        memoryComputer.setServicesDefinition(servicesDefinition);
        memoryComputer.setSshCommandService(sshCommandService);

        servicesInstallationSorter = new ServicesInstallationSorter ();
        servicesInstallationSorter.setServicesDefinition(servicesDefinition);

        servicesSettingsService = new ServicesSettingsService();
        servicesSettingsService.setNodeRangeResolver(nodeRangeResolver);
        servicesSettingsService.setServicesDefinition(servicesDefinition);

        marathonService = createMarathonService();
        marathonService.setServicesDefinition(servicesDefinition);
        marathonService.setConfigurationService (configurationService);
        marathonService.setSystemService(systemService);
        marathonService.setSshCommandService(sshCommandService);
        marathonService.setSystemOperationService(systemOperationService);
        marathonService.setProxyManagerService(proxyManagerService);
        marathonService.setMemoryComputer(memoryComputer);
        marathonService.setMessagingService(messagingService);
        marathonService.setNotificationService(notificationService);

        systemService.setNodeRangeResolver(nodeRangeResolver);
        systemService.setSetupService(setupService);
        systemService.setProxyManagerService(proxyManagerService);
        systemService.setSshCommandService(sshCommandService);
        systemService.setServicesDefinition(servicesDefinition);
        systemService.setMarathonService(marathonService);
        systemService.setMessagingService(messagingService);
        systemService.setNotificationService(notificationService);
        systemService.setConfigurationService(configurationService);

        nodesConfigurationService = createNodesConfigurationService();
        nodesConfigurationService.setConfigurationService(configurationService);
        nodesConfigurationService.setMarathonService(marathonService);
        nodesConfigurationService.setMemoryComputer(memoryComputer);
        nodesConfigurationService.setMessagingService(messagingService);
        nodesConfigurationService.setNodeRangeResolver(nodeRangeResolver);
        nodesConfigurationService.setProxyManagerService(proxyManagerService);
        nodesConfigurationService.setServicesSettingsService(servicesSettingsService);
        nodesConfigurationService.setServicesDefinition(servicesDefinition);
        nodesConfigurationService.setServicesInstallationSorter(servicesInstallationSorter);
        nodesConfigurationService.setSetupService(setupService);
        nodesConfigurationService.setSystemService (systemService);
        nodesConfigurationService.setSshCommandService(sshCommandService);
        nodesConfigurationService.setSystemOperationService(systemOperationService);
    }

    protected ProxyManagerService createProxyManagerService() {
        return new ProxyManagerService();
    }

    protected ConfigurationService createConfigurationService() {
        return new ConfigurationService();
    }

    protected SetupService createSetupService() {
        return new SetupService(){
            protected void dowloadFile(StringBuilder builder, File destinationFile, URL downloadUrl, String message) throws IOException {
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

    protected MarathonService createMarathonService() {
        return new MarathonService() {
            @Override
            protected String queryMarathon (String endpoint, String method) {
                return "{}";
            }
        };
    }

    protected void clearCommandScript() {
        testSSHCommandScript.delete(0, testSSHCommandScript.length());
    }

    protected void clearResultBuilder() {
        testSSHCommandResultBuilder.delete(0, testSSHCommandResultBuilder.length());
    }

}

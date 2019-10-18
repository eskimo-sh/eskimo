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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.proxy.WebSocketProxyServer;
import org.apache.log4j.Logger;
import org.junit.Before;

import java.util.Arrays;
import java.util.List;

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

    protected StringBuilder testSSHCommandResultBuilder = new StringBuilder();
    protected StringBuilder testSSHCommandScript = new StringBuilder();

    protected String systemStatusTest = null;
    protected String expectedPrevStatus = null;

    @Before
    public void setUp() throws Exception {


        systemStatusTest = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/systemStatusTest.log"), "UTF-8");
        expectedPrevStatus  = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedPrevStatus.json"), "UTF-8");

        clearResultBuilder();
        clearCommandScript();

        servicesDefinition = new ServicesDefinition() {
            @Override
            public String getAllServicesString() {
                return "kafka zookeeper ntp mesos-master spark-executor kibana cerebro zeppelin kafka-manager gluster gdash spark-history-server";
            }
            @Override
            public List<String> getAllServices() {
                return Arrays.asList(new String[] {"kafka",  "zookeeper", "ntp", "mesos-master", "spark-executor", "kibana", "cerebro", "zeppelin", "kafka-manager", "gluster", "gdash", "spark-history-server", "elasticsearch"});
            }
        };
        servicesDefinition.afterPropertiesSet();

        nodeRangeResolver = new NodeRangeResolver();

        proxyManagerService = new ProxyManagerService();
        proxyManagerService.setServicesDefinition(servicesDefinition);

        proxyManagerService.setConnectionManagerService(new ConnectionManagerService() {
            public void recreateTunnels(String host) throws ConnectionManagerException {
            }
        });
        proxyManagerService.setWebSocketProxyServer(new WebSocketProxyServer(proxyManagerService, servicesDefinition) {
            public void removeForwarders(String serviceId) {
            }
        });

        setupService = new SetupService();

        systemService = createSystemService();
        systemService.setSetupService(setupService);
        systemService.setProxyManagerService(proxyManagerService);

        systemService.setNodeRangeResolver(nodeRangeResolver);

        systemService.setSshCommandService(new SSHCommandService() {
            @Override
            public String runSSHScript(String hostAddress, String script, boolean throwsException) throws SSHCommandException {
                testSSHCommandScript.append(script + "\n");
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public String runSSHCommand(String hostAddress, String command) throws SSHCommandException {
                testSSHCommandScript.append(command + "\n");
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public void copySCPFile(String hostAddress, String filePath) throws SSHCommandException {
                // just do nothing
            }
        });

        messagingService = new MessagingService();
        systemService.setMessagingService(messagingService);

        notificationService = new NotificationService();
        systemService.setNotificationService(notificationService);

        systemOperationService = new SystemOperationService();
        systemOperationService.setNotificationService(notificationService);;
        systemOperationService.setMessagingService(messagingService);
        systemOperationService.setSystemService(systemService);
        systemService.setSystemOperationService (systemOperationService);

        systemService.setServicesDefinition(servicesDefinition);
    }

    protected SystemService createSystemService() {
        return new SystemService();
    }

    protected void clearCommandScript() {
        testSSHCommandScript.delete(0, testSSHCommandScript.length());
    }

    protected void clearResultBuilder() {
        testSSHCommandResultBuilder.delete(0, testSSHCommandResultBuilder.length());
    }

}

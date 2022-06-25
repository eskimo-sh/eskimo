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

package ch.niceideas.eskimo.proxy;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.services.*;
import org.apache.http.HttpHost;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ProxyManagerService {

    private static final Logger logger = Logger.getLogger(ProxyManagerService.class);

    public static final int LOCAL_PORT_RANGE_START = 39152;

    private static final int PORT_GENERATION_MAX_ATTEMPT_COUNT = 10000;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ConnectionManagerService connectionManagerService;

    @Autowired
    private WebSocketProxyServer webSocketProxyServer;

    @Autowired
    private ConfigurationService configurationService;

    private final Map<String, ProxyTunnelConfig> proxyTunnelConfigs = new ConcurrentHashMap<>();

    /** For tests */
    public void setServicesDefinition (ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    public void setConnectionManagerService (ConnectionManagerService connectionManagerService) {
        this.connectionManagerService = connectionManagerService;
    }
    public void setWebSocketProxyServer(WebSocketProxyServer webSocketProxyServer) {
        this.webSocketProxyServer = webSocketProxyServer;
    }
    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    private void __dumpProxyTunnelConfig() {
        proxyTunnelConfigs.keySet().forEach(key -> logger.debug(" - " + key + " -> " + proxyTunnelConfigs.get(key)));
        logger.debug("");
    }

    public HttpHost getServerHost(String serviceId) {
        ProxyTunnelConfig config =  proxyTunnelConfigs.get(serviceId);
        if (config == null) {
            throw new IllegalStateException("No config found for " + serviceId);
        }
        return new HttpHost("localhost", config.getLocalPort(), "http");
    }

    public String getServerURI(String serviceName, String pathInfo) {

        Service service = servicesDefinition.getService(serviceName);

        String serviceId = null;
        String targetHost = null;
        if (!service.isUnique()) {
            targetHost = extractHostFromPathInfo(pathInfo);
        }
        serviceId = service.getServiceId(targetHost);

        HttpHost serverHost = getServerHost(serviceId);
        if (serverHost == null) {
            throw new IllegalStateException("No host stored for " + serviceId);
        }
        return serverHost.getSchemeName() + "://" + serverHost.getHostName() + ":" + serverHost.getPort() + "/";
    }

    public String extractHostFromPathInfo(String pathInfo) {
        int startIndex = 0;
        if (pathInfo.startsWith("/")) {
            startIndex = 1;
        }
        int lastIndex = pathInfo.indexOf('/', startIndex + 1);
        if (lastIndex == -1) {
            lastIndex = pathInfo.length();
        }
        return pathInfo.substring(startIndex, lastIndex);
    }

    public List<ProxyTunnelConfig> getTunnelConfigForHost (String host) {
        return proxyTunnelConfigs.values().stream()
                .filter(config -> config.getNode().equals(host))
                .collect(Collectors.toList());
    }

    public void updateServerForService(String serviceName, String runtimeNode) throws ConnectionManagerException {

        Service service = servicesDefinition.getService(serviceName);
        if (service != null && service.isProxied()) {

            // need to make a distinction between unique and multiple services here !!
            String serviceId = service.getServiceId(runtimeNode);

            String effNode = getRuntimeNode(runtimeNode, service);

            ProxyTunnelConfig prevConfig = proxyTunnelConfigs.get(serviceId);

            if (prevConfig == null || !prevConfig.getNode().equals(effNode)) {

                if (logger.isDebugEnabled()) {
                    logger.debug("------ BEFORE ---- updateServerForService (" + serviceName + "," + runtimeNode + ") ----------- ");
                    __dumpProxyTunnelConfig();
                }

                // Handle host has changed !
                ProxyTunnelConfig newConfig = new ProxyTunnelConfig(serviceName, generateLocalPort(), effNode, service.getUiConfig().getProxyTargetPort());
                proxyTunnelConfigs.put(serviceId, newConfig);

                if (prevConfig != null) {
                    logger.info ("Updating server config for service " + serviceName + ". Will recreate tunnels to "
                            + effNode + " and " + prevConfig.getNode() + " (dropping service)");
                    try {
                        connectionManagerService.recreateTunnels(prevConfig.getNode());
                    } catch (ConnectionManagerException e) {
                        logger.error ("Couldn't drop former tunnels for " + serviceName + " on " + prevConfig.getNode() +
                                " - got " + e.getClass() + ":" + e.getMessage());
                    }
                } else {
                    logger.info ("Updating server config for service " + serviceName + ". Will recreate tunnels to " + effNode);
                }

                connectionManagerService.recreateTunnels (effNode);
                webSocketProxyServer.removeForwardersForService(serviceId); // just remove them, they will be recreated automagically

                if (logger.isDebugEnabled()) {
                    logger.debug("------ AFTER ---- updateServerForService (" + serviceName + "," + runtimeNode + ") ----------- ");
                    __dumpProxyTunnelConfig();
                }
            }
        }
    }

    private String getRuntimeNode(String runtimeNodeName, Service service) throws ConnectionManagerException {
        String effNode = runtimeNodeName;
        if (service.isUsingKubeProxy()) {

            // Kubernetes services are redirected to kubernetes master node if they are reached through the kubectl
            // proxy, this is mandatory

            ServicesInstallStatusWrapper servicesInstallationStatus = null;
            try {
                servicesInstallationStatus = configurationService.loadServicesInstallationStatus();
            } catch (FileException | SetupException e) {
                logger.error(e, e);
                throw new ConnectionManagerException(e.getMessage(), e);
            }
            effNode = servicesInstallationStatus.getFirstNode(KubernetesService.KUBE_MASTER);
            if (StringUtils.isBlank(effNode)) {
                throw new ConnectionManagerException("Asked for kube master runtime node, but it couldn't be found in installation status");
            }
        }
        return effNode;
    }

    public void removeServerForService(String serviceName, String runtimeNode) {

        if (logger.isDebugEnabled()) {
            logger.debug("------ BEFORE ---- removeServerForService (" + serviceName + "," + runtimeNode + ") ----------- ");
            __dumpProxyTunnelConfig();
        }

        Service service = servicesDefinition.getService(serviceName);
        if (service != null && service.isProxied()) {
            String serviceId = service.getServiceId(runtimeNode);

            ProxyTunnelConfig prevConfig = proxyTunnelConfigs.get(serviceId);

            if (prevConfig != null) {

                proxyTunnelConfigs.remove(serviceId);
                connectionManagerService.dropTunnelsToBeClosed(prevConfig.getNode());
                webSocketProxyServer.removeForwardersForService(serviceId);
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("------ AFTER ---- removeServerForService (" + serviceName + "," + runtimeNode + ") ----------- ");
            __dumpProxyTunnelConfig();
        }
    }

    /** get a port number from 49152 to 65535 */
    public static int generateLocalPort() {
        int portNumber = -1;
        int tryCount = 0;
        do {
            int randInc = ThreadLocalRandom.current().nextInt(65534 - LOCAL_PORT_RANGE_START);
            portNumber = LOCAL_PORT_RANGE_START + randInc;
            tryCount++;
        } while (isLocalPortInUse(portNumber) && tryCount < PORT_GENERATION_MAX_ATTEMPT_COUNT);
        if (tryCount >= PORT_GENERATION_MAX_ATTEMPT_COUNT) {
            throw new IllegalStateException();
        }
        return portNumber;
    }

    private static boolean isLocalPortInUse(int port) {
        try {
            // ServerSocket try to open a LOCAL port
            new ServerSocket(port).close();
            // local port can be opened, it's available
            return false;
        } catch(IOException e) {
            // local port cannot be opened, it's in use
            return true;
        }
    }


    public Collection<String> getAllTunnelConfigKeys() {
        return proxyTunnelConfigs.keySet();
    }

    public ProxyTunnelConfig getTunnelConfig(String serviceId) {
        return proxyTunnelConfigs.get(serviceId);
    }
}

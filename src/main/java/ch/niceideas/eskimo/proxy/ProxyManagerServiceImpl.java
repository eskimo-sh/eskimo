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

package ch.niceideas.eskimo.proxy;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.model.service.ServiceDefinition;
import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import ch.niceideas.eskimo.types.ServiceWebId;
import org.apache.hc.core5.http.HttpHost;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON, proxyMode = ScopedProxyMode.INTERFACES)
@Profile("!test-proxy")
public class ProxyManagerServiceImpl implements ProxyManagerService {

    private static final Logger logger = Logger.getLogger(ProxyManagerServiceImpl.class);

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ConnectionManagerService connectionManagerService;

    @Autowired
    private WebSocketProxyServer webSocketProxyServer;

    @Autowired
    private ConfigurationService configurationService;

    private final Map<ServiceWebId, ProxyTunnelConfig> proxyTunnelConfigs = new ConcurrentHashMap<>();

    private void __dumpProxyTunnelConfig() {
        proxyTunnelConfigs.keySet().forEach(key -> logger.debug(" - " + key + " -> " + proxyTunnelConfigs.get(key)));
        logger.debug("");
    }

    @Override
    public HttpHost getServerHost(ServiceWebId serviceId) {
        ProxyTunnelConfig config =  proxyTunnelConfigs.get(serviceId);
        if (config == null) {
            throw new IllegalStateException("No config found for " + serviceId);
        }
        return new HttpHost("http", "localhost", config.getLocalPort());
    }

    @Override
    public String getServerURI(Service service, String pathInfo) {

        ServiceDefinition serviceDef = servicesDefinition.getServiceDefinition(service);

        ServiceWebId serviceId;
        Node targetHost = null;
        if (!serviceDef.isUnique()) {
            targetHost = extractHostFromPathInfo(pathInfo);
        }
        serviceId = serviceDef.getServiceId(targetHost);

        HttpHost serverHost = getServerHost(serviceId);
        if (serverHost == null) {
            throw new IllegalStateException("No host stored for " + serviceId);
        }
        return serverHost.getSchemeName() + "://" + serverHost.getHostName() + ":" + serverHost.getPort() + "/";
    }

    @Override
    public Node extractHostFromPathInfo(String pathInfo) {
        int startIndex = 0;
        if (pathInfo.startsWith("/")) {
            startIndex = 1;
        }
        int lastIndex = pathInfo.indexOf('/', startIndex + 1);
        if (lastIndex == -1) {
            lastIndex = pathInfo.length();
        }
        return Node.fromName(pathInfo.substring(startIndex, lastIndex));
    }

    @Override
    public List<ProxyTunnelConfig> getTunnelConfigForHost (Node host) {
        return proxyTunnelConfigs.values().stream()
                .filter(config -> config.getNode().equals(host))
                .collect(Collectors.toList());
    }

    @Override
    public void updateServerForService(Service service, Node runtimeNode) throws ConnectionManagerException {

        ServiceDefinition serviceDef = servicesDefinition.getServiceDefinition(service);
        if (serviceDef != null && serviceDef.isProxied()) {

            // need to make a distinction between unique and multiple services here !!
            ServiceWebId serviceId = serviceDef.getServiceId(runtimeNode);

            Node effNode = getRuntimeNode(runtimeNode, serviceDef);

            ProxyTunnelConfig prevConfig = proxyTunnelConfigs.get(serviceId);

            if (prevConfig == null || !prevConfig.getNode().equals(effNode)) {

                if (logger.isDebugEnabled()) {
                    logger.debug("------ BEFORE ---- updateServerForService (" + service + "," + runtimeNode + ") ----------- ");
                    __dumpProxyTunnelConfig();
                }

                // Handle host has changed !
                ProxyTunnelConfig newConfig = new ProxyTunnelConfig(
                        service,
                        ProxyManagerService.generateLocalPort(),
                        effNode,
                        serviceDef.getUiConfig().getProxyTargetPort());

                proxyTunnelConfigs.put(serviceId, newConfig);

                if (prevConfig != null) {
                    logger.info ("Updating server config for service " + service + ". Will recreate tunnels to "
                            + effNode + " and " + prevConfig.getNode() + " (dropping service)");
                    try {
                        connectionManagerService.recreateTunnels(prevConfig.getNode());
                    } catch (ConnectionManagerException e) {
                        logger.error ("Couldn't drop former tunnels for " + service + " on " + prevConfig.getNode() +
                                " - got " + e.getClass() + ":" + e.getMessage());
                    }
                } else {
                    logger.info ("Updating server config for service " + service + ". Will recreate tunnels to " + effNode);
                }

                connectionManagerService.recreateTunnels (effNode);
                webSocketProxyServer.removeForwardersForService(serviceId); // just remove them, they will be recreated automagically

                if (logger.isDebugEnabled()) {
                    logger.debug("------ AFTER ---- updateServerForService (" + service + "," + runtimeNode + ") ----------- ");
                    __dumpProxyTunnelConfig();
                }
            }
        }
    }

    private Node getRuntimeNode(Node runtimeNode, ServiceDefinition serviceDef) throws ConnectionManagerException {
        Node effNode = runtimeNode;
        if (serviceDef.isUsingKubeProxy()) {

            // Kubernetes services are redirected to kubernetes master node if they are reached through the kubectl
            // proxy, this is mandatory

            ServicesInstallStatusWrapper servicesInstallationStatus;
            try {
                servicesInstallationStatus = configurationService.loadServicesInstallationStatus();
            } catch (FileException | SetupException e) {
                logger.error(e, e);
                throw new ConnectionManagerException(e.getMessage(), e);
            }
            effNode = servicesInstallationStatus.getFirstNode(servicesDefinition.getKubeMasterServiceDef());
            if (effNode == null) {
                throw new ConnectionManagerException("Asked for kube master runtime node, but it couldn't be found in installation status");
            }
        }
        return effNode;
    }

    @Override
    public void removeServerForService(Service service, Node runtimeNode) {

        if (logger.isDebugEnabled()) {
            logger.debug("------ BEFORE ---- removeServerForService (" + service + "," + runtimeNode + ") ----------- ");
            __dumpProxyTunnelConfig();
        }

        ServiceDefinition serviceDef = servicesDefinition.getServiceDefinition(service);
        if (serviceDef != null && serviceDef.isProxied()) {
            ServiceWebId serviceId = serviceDef.getServiceId(runtimeNode);

            ProxyTunnelConfig prevConfig = proxyTunnelConfigs.get(serviceId);

            if (prevConfig != null) {

                proxyTunnelConfigs.remove(serviceId);
                connectionManagerService.dropTunnelsToBeClosed(prevConfig.getNode());
                webSocketProxyServer.removeForwardersForService(serviceId);
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("------ AFTER ---- removeServerForService (" + service + "," + runtimeNode + ") ----------- ");
            __dumpProxyTunnelConfig();
        }
    }

    @Override
    public Collection<ServiceWebId> getAllTunnelConfigKeys() {
        return proxyTunnelConfigs.keySet();
    }

    @Override
    public ProxyTunnelConfig getTunnelConfig(ServiceWebId serviceId) {
        return proxyTunnelConfigs.get(serviceId);
    }
}

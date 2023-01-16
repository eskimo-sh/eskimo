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

import ch.niceideas.eskimo.configurations.ProxyConfiguration;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles establishment and tracking of next 'hop', and
 * copies data from the current session to the next hop.
 */
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Profile("!test-web-socket")
public class WebSocketProxyServerImpl extends AbstractWebSocketHandler implements WebSocketProxyServer {

    private static final Logger logger = Logger.getLogger(WebSocketProxyServerImpl.class);

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    protected final Map<String, Map<String, Map<String, WebSocketProxyForwarder>>> forwarders = new ConcurrentHashMap<>();

    /* For tests */
    protected Map<String, Map<String, Map<String, WebSocketProxyForwarder>>> getForwarders() {
        return Collections.unmodifiableMap(forwarders);
    }

    public WebSocketProxyServerImpl() {
    }

    /* Need hat one for tests */
    public WebSocketProxyServerImpl(ProxyManagerService proxyManagerService, ServicesDefinition servicesDefinition) {
        this.proxyManagerService = proxyManagerService;
        this.servicesDefinition = servicesDefinition;
    }

    @Override
    public void handleMessage(WebSocketSession webSocketServerSession, WebSocketMessage<?> webSocketMessage) throws Exception {

        String uri = Objects.requireNonNull(webSocketServerSession.getUri()).toString();

        int indexOfWs = uri.indexOf(ProxyConfiguration.ESKIMO_WEB_SOCKET_URL_PREFIX);
        int indexOfSlash = uri.indexOf('/', indexOfWs + 4);

        String serviceName = uri.substring(indexOfWs + 4, indexOfSlash > -1 ? indexOfSlash : uri.length());

        Service service = servicesDefinition.getService(serviceName);

        String targetPath = indexOfSlash > -1 ? uri.substring(indexOfSlash) : "";
        String serviceId = serviceName;
        if (!service.isUnique()) {
            String targetHost = proxyManagerService.extractHostFromPathInfo(uri.substring(indexOfSlash));
            serviceId = service.getServiceId(targetHost);
            targetPath = uri.substring(uri.indexOf(targetHost) + targetHost.length());
        }

        try {
            getForwarder(serviceId, webSocketServerSession, targetPath).forwardMessage(webSocketMessage);
        } catch (IllegalStateException | SocketException e) {
            logger.error (uri + " - got " + e.getClass() + ":" + e.getMessage());
            webSocketServerSession.close();
        }
    }

    protected WebSocketProxyForwarder getForwarder(String serviceId, WebSocketSession webSocketServerSession, String targetPath) {

        Map<String, Map<String, WebSocketProxyForwarder>> forwardersForService = forwarders.computeIfAbsent(serviceId, k -> new HashMap<>());

        Map<String, WebSocketProxyForwarder> forwardersForSession = forwardersForService.computeIfAbsent(webSocketServerSession.getId(), k -> new HashMap<>());

        return forwardersForSession.computeIfAbsent(targetPath, k  -> {
            logger.info ("Creating new forwarder for session : " + webSocketServerSession.getId() + " - service ID : " + serviceId + " - target path : " + targetPath);
            return createForwarder(serviceId, webSocketServerSession, targetPath);
        });
    }

    public WebSocketProxyForwarder createForwarder(String serviceId, WebSocketSession webSocketServerSession, String targetPath) {
        return new WebSocketProxyForwarder(serviceId, targetPath, proxyManagerService, webSocketServerSession);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

        logger.info ("Dropping all forwarders for session ID " + session.getId() + "");
        forwarders.values()
                .forEach(forwardersForService -> {
                            new ArrayList<>(forwardersForService.keySet()).stream()
                                    .filter(sessionId -> sessionId.equals(session.getId()))
                                    .map(forwardersForService::get)
                                    .forEach(forwardersForSession -> {
                                        forwardersForSession.values()
                                                .forEach(WebSocketProxyForwarder::close);
                                        forwardersForSession.clear();
                                    });
                            forwardersForService.remove(session.getId());
                        }
                );
    }

    @Override
    public void removeForwardersForService(String serviceId) {
        logger.info ("Dropping all forwarders for service ID " + serviceId + " (will be recreated lazily)");
        forwarders.keySet().stream()
                .filter(service -> service.equals(serviceId))
                .map(service -> forwarders.get(serviceId))
                .forEach(forwardersForService -> {
                    forwardersForService.values()
                            .forEach(forwardersForSession ->
                                forwardersForSession.values()
                                        .forEach(WebSocketProxyForwarder::close)
                            );
                    forwardersForService.clear();
                });

        forwarders.remove(serviceId);
    }
}
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

import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.types.ServiceWebId;
import org.apache.log4j.Logger;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WebSocketProxyForwarder {

    public static final String WS_LOCALHOST_PREFIX = "ws://localhost:";

    public static final int MESSAGE_SIZE_LIMIT = 10 * 1024 * 1024; // 10 Mb

    private final Logger logger = Logger.getLogger(this.getClass());

    private final ServiceWebId serviceId;

    private final String targetPath;

    private final WebSocketSession webSocketServerSession;

    private WebSocketSession webSocketClientSession;

    private final ProxyManagerService proxyManagerService;

    public WebSocketProxyForwarder(
            ServiceWebId serviceId, String targetPath, ProxyManagerService proxyManagerService, WebSocketSession webSocketServerSession) {
        this.serviceId = serviceId;
        this.targetPath = targetPath;
        this.proxyManagerService = proxyManagerService;
        this.webSocketServerSession = webSocketServerSession;
        webSocketClientSession = createWebSocketClientSession();
    }

    public boolean isClosed() {
        return !webSocketClientSession.isOpen();
    }

    private WebSocketHttpHeaders getWebSocketHttpHeaders(final WebSocketSession userAgentSession) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        /*
        Principal principal = userAgentSession.getPrincipal();
        if (principal != null && OAuth2Authentication.class.isAssignableFrom(principal.getClass())) {
            OAuth2Authentication oAuth2Authentication = (OAuth2Authentication) principal;
            OAuth2AuthenticationDetails details = (OAuth2AuthenticationDetails) oAuth2Authentication.getDetails();
            String accessToken = details.getTokenValue();
            headers.put(HttpHeaders.AUTHORIZATION, Collections.singletonList("Bearer " + accessToken));
            if(logger.isDebugEnabled()) {
                logger.debug("Added Oauth2 bearer token authentication header for user " +
                        principal.getName() + " to web sockets http headers");
            }
        }
        else {
            if(logger.isDebugEnabled()) {
                logger.debug("Skipped adding basic authentication header since user session principal is null");
            }
        }
        */
        return headers;
    }

    protected WebSocketSession createWebSocketClientSession() {
        String targetWsUri = "(undefined yet)";
        try {

            ProxyTunnelConfig config = Optional.ofNullable(proxyManagerService.getTunnelConfig(serviceId))
                    .orElseThrow(() -> new IllegalStateException("Tunnel configuration not created yet for service " + serviceId
                            + " - likely status is not initialized yet"));

            targetWsUri = WS_LOCALHOST_PREFIX + config.getLocalPort() + targetPath;

            WebSocketHttpHeaders headers = getWebSocketHttpHeaders(webSocketServerSession);

            WebSocketSession clientSession = new StandardWebSocketClient()
                    .doHandshake(new WebSocketServerHandshakeHandler(webSocketServerSession), headers, new URI(targetWsUri))
                    .get((long)30 * (long)1000, TimeUnit.MILLISECONDS);

            clientSession.setBinaryMessageSizeLimit(MESSAGE_SIZE_LIMIT); // 10Mb
            clientSession.setTextMessageSizeLimit(MESSAGE_SIZE_LIMIT); // 10Mb

            return clientSession;

        } catch (URISyntaxException | ExecutionException | TimeoutException e) {
            logger.error ("Caught " + e.getClass() + " - " + e.getMessage() + " while reaching " + targetWsUri);
            logger.error (e, e);
            throw new ProxyException(e);

        } catch (InterruptedException e) {
            logger.error (e, e);
            Thread.currentThread().interrupt();
            throw new ProxyException(e);
        }
    }

    public void forwardMessage(WebSocketMessage<?> webSocketMessage) throws IOException {
        if (!webSocketClientSession.isOpen()) {
            // recreate it if it has been closed
            webSocketClientSession = createWebSocketClientSession();
        }

        // Hack : message HELLO_ESKIMO is used to force create the connection to target service
        if (("" + webSocketMessage.getPayload()).equals("HELLO_ESKIMO")) {
            logger.debug ("Got session opening message for " + serviceId);

        } else {

            webSocketClientSession.sendMessage(webSocketMessage);
        }
    }

    public void close() {
        try {
            webSocketClientSession.close();
        } catch (IOException e) {
            logger.warn (e.getMessage());
            logger.debug (e, e);
            // ignored any further
        }
    }

    public static class WebSocketServerHandshakeHandler extends AbstractWebSocketHandler {

        private final WebSocketSession webSocketServerSession;

        public WebSocketServerHandshakeHandler(WebSocketSession webSocketServerSession) {
            this.webSocketServerSession = webSocketServerSession;
        }

        @Override
        public void handleMessage(WebSocketSession session, WebSocketMessage<?> webSocketMessage) throws Exception {
            webSocketServerSession.sendMessage(webSocketMessage);
        }
    }
}

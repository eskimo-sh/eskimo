package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.model.ProxyTunnelConfig;
import org.apache.log4j.Logger;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class WebSocketProxyForwarder {

    public static final String WS_LOCALHOST_PREFIX = "ws://localhost:";

    public static final int MESSAGE_SIZE_LIMIT = 10 * 1024 * 1024; // 10 Mb

    private final Logger logger = Logger.getLogger(this.getClass());

    private final String serviceId;

    private final String targetPath;

    private final WebSocketSession webSocketServerSession;

    private WebSocketSession webSocketClientSession;

    private final ProxyManagerService proxyManagerService;

    public WebSocketProxyForwarder(
            String serviceId, String targetPath, ProxyManagerService proxyManagerService, WebSocketSession webSocketServerSession) {
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

    WebSocketSession createWebSocketClientSession() {
        try {

            ProxyTunnelConfig config = proxyManagerService.getTunnelConfig(serviceId);

            String targetWsUri = WS_LOCALHOST_PREFIX + config.getLocalPort() + targetPath;

            WebSocketHttpHeaders headers = getWebSocketHttpHeaders(webSocketServerSession);

            WebSocketSession clientSession = new StandardWebSocketClient()
                    .doHandshake(new WebSocketServerHandshakeHandler(webSocketServerSession), headers, new URI(targetWsUri))
                    .get((long)30 * (long)1000, TimeUnit.MILLISECONDS);

            clientSession.setBinaryMessageSizeLimit(MESSAGE_SIZE_LIMIT); // 10Mb
            clientSession.setTextMessageSizeLimit(MESSAGE_SIZE_LIMIT); // 10Mb

            return clientSession;
        } catch (Exception e) {
            logger.error (e, e);
            throw new ProxyException(e);
        }
    }

    public void forwardMessage(WebSocketMessage<?> webSocketMessage) throws IOException {
        if (!webSocketClientSession.isOpen()) {
            // recreate it if it has been closed
            webSocketClientSession = createWebSocketClientSession();
        }
        webSocketClientSession.sendMessage(webSocketMessage);
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

    public class WebSocketServerHandshakeHandler extends AbstractWebSocketHandler {

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

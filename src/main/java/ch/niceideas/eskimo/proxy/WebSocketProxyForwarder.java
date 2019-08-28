package ch.niceideas.eskimo.proxy;

import org.apache.log4j.Logger;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.concurrent.TimeUnit;

public class WebSocketProxyForwarder {

    private final WebSocketSession webSocketClientSession;
    private final Logger logger = Logger.getLogger(this.getClass());

    public WebSocketProxyForwarder(WebSocketSession webSocketServerSession) {
        webSocketClientSession = createWebSocketClientSession(webSocketServerSession);
    }

    private WebSocketHttpHeaders getWebSocketHttpHeaders(final WebSocketSession userAgentSession) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        Principal principal = userAgentSession.getPrincipal();
        /*
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

    private WebSocketSession createWebSocketClientSession(WebSocketSession webSocketServerSession) {
        try {
            WebSocketHttpHeaders headers = getWebSocketHttpHeaders(webSocketServerSession);
            return new StandardWebSocketClient()
                    .doHandshake(new WebSocketProxyClientHandler(webSocketServerSession), headers, new URI("ws://localhost:9999"))
                    .get(1000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void sendMessageToNextHop(WebSocketMessage<?> webSocketMessage) throws IOException {
        webSocketClientSession.sendMessage(webSocketMessage);
    }

    public class WebSocketProxyClientHandler extends AbstractWebSocketHandler {

        private final WebSocketSession webSocketServerSession;

        public WebSocketProxyClientHandler(WebSocketSession webSocketServerSession) {
            this.webSocketServerSession = webSocketServerSession;
        }

        @Override
        public void handleMessage(WebSocketSession session, WebSocketMessage<?> webSocketMessage) throws Exception {
            webSocketServerSession.sendMessage(webSocketMessage);
        }
    }
}

package ch.niceideas.eskimo.proxy;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles establishment and tracking of next 'hop', and
 * copies data from the current session to the next hop.
 */
@Component
public class WebSocketProxyServer extends AbstractWebSocketHandler {

    private final Map<String, WebSocketProxyForwarder> nextHops = new ConcurrentHashMap<>();

    @Override
    public void handleMessage(WebSocketSession webSocketSession, WebSocketMessage<?> webSocketMessage) throws Exception {
        getNextHop(webSocketSession).sendMessageToNextHop(webSocketMessage);
    }

    private WebSocketProxyForwarder getNextHop(WebSocketSession webSocketSession) {
        WebSocketProxyForwarder nextHop = nextHops.get(webSocketSession.getId());
        if (nextHop == null) {
            nextHop = new WebSocketProxyForwarder(webSocketSession);
            nextHops.put(webSocketSession.getId(), nextHop);
        }
        return nextHop;
    }

}
package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.services.ServicesDefinition;
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

    private final ProxyManagerService proxyManagerService;

    private final ServicesDefinition servicesDefinition;

    private final Map<String, WebSocketProxyForwarder> forwarders = new ConcurrentHashMap<>();

    public WebSocketProxyServer(ProxyManagerService proxyManagerService, ServicesDefinition servicesDefinition) {
        this.proxyManagerService = proxyManagerService;
        this.servicesDefinition = servicesDefinition;
    }

    @Override
    public void handleMessage(WebSocketSession webSocketServerSession, WebSocketMessage<?> webSocketMessage) throws Exception {
        getForwarder(webSocketServerSession).sendMessageToNextHop(webSocketMessage);
    }

    private WebSocketProxyForwarder getForwarder(WebSocketSession webSocketServerSession) {
        WebSocketProxyForwarder forwarder = forwarders.get(webSocketServerSession.getId());
        if (forwarder == null) {
            forwarder = new WebSocketProxyForwarder(proxyManagerService, servicesDefinition, webSocketServerSession);
            forwarders.put(webSocketServerSession.getId(), forwarder);
        }
        return forwarder;
    }

}
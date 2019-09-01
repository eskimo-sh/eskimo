package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.model.Service;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles establishment and tracking of next 'hop', and
 * copies data from the current session to the next hop.
 */
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class WebSocketProxyServer extends AbstractWebSocketHandler {

    private static final Logger logger = Logger.getLogger(WebSocketProxyServer.class);

    private final ProxyManagerService proxyManagerService;

    private final ServicesDefinition servicesDefinition;

    private final Map<String, Map<String, Map<String, WebSocketProxyForwarder>>> forwarders = new ConcurrentHashMap<>();

    public WebSocketProxyServer(ProxyManagerService proxyManagerService, ServicesDefinition servicesDefinition) {
        this.proxyManagerService = proxyManagerService;
        this.servicesDefinition = servicesDefinition;
    }

    @Override
    public void handleMessage(WebSocketSession webSocketServerSession, WebSocketMessage<?> webSocketMessage) throws Exception {

        String uri = webSocketServerSession.getUri().toString();

        int indexOfWs = uri.indexOf("/ws");
        int indexOfSlash = uri.indexOf("/", indexOfWs + 4);

        String serviceName = uri.substring(indexOfWs + 4, indexOfSlash > -1 ? indexOfSlash : uri.length());

        Service service = servicesDefinition.getService(serviceName);

        String targetPath = indexOfSlash > -1 ? uri.substring(indexOfSlash) : "";
        String serviceId = serviceName;
        if (!service.isUnique()) {
            String targetHost = proxyManagerService.extractHostFromPathInfo(uri.substring(indexOfSlash));
            serviceId = service.getServiceId(targetHost);
            targetPath = uri.substring(uri.indexOf(targetHost) + targetHost.length());
        }

        getForwarder(serviceId, webSocketServerSession, targetPath).forwardMessage(webSocketMessage);

    }

    private WebSocketProxyForwarder getForwarder(String serviceId, WebSocketSession webSocketServerSession, String targetPath) {
        Map<String, Map<String, WebSocketProxyForwarder>> forwardersForService = forwarders.get(serviceId);
        if (forwardersForService == null) {
            forwardersForService = new HashMap<>();
            forwarders.put(serviceId, forwardersForService);
        }
        Map<String, WebSocketProxyForwarder> forwardersForSession = forwardersForService.get(webSocketServerSession.getId());
        if (forwardersForSession == null) {
            forwardersForSession = new HashMap<>();
            forwardersForService.put(webSocketServerSession.getId(), forwardersForSession);
        }
        WebSocketProxyForwarder forwarder = forwardersForSession.get(targetPath);
        if (forwarder == null) {
            logger.info ("Creating new forwarder for session : " + webSocketServerSession.getId() + " - service ID : " + serviceId + " - target path : " + targetPath);
            forwarder = new WebSocketProxyForwarder(serviceId, targetPath, proxyManagerService, servicesDefinition, webSocketServerSession);
            forwardersForSession.put(targetPath, forwarder);
        }
        return forwarder;
    }

    public void removeForwarders(String serviceId) {
        logger.info ("Dropping all forwarders for  service ID " + serviceId);
        Map<String, Map<String, WebSocketProxyForwarder>> forwardersForService = forwarders.get(serviceId);
        if (forwardersForService != null) {
            for (Map<String, WebSocketProxyForwarder> forwardersForSession : forwardersForService.values()) {
                for (WebSocketProxyForwarder forwarder : forwardersForSession.values()) {
                    forwarder.close();
                }
            }
            forwardersForService.clear();
        }
    }
}
package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.services.ConnectionManagerService;
import ch.niceideas.eskimo.services.ConnectionManagerServiceImpl;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.ServicesDefinitionImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class WebSocketProxyServerTest {

    private WebSocketProxyServerImpl server = null;

    private ProxyManagerServiceImpl pms;
    private ServicesDefinitionImpl sd;

    private WebSocketSession wss1;
    private WebSocketSession wss2;

    private AtomicInteger closedCalls;

    
    @BeforeEach
    public void setUp() throws Exception {

        pms = new ProxyManagerServiceImpl() {
            @Override
            public ProxyTunnelConfig getTunnelConfig(String serviceId) {
                return new ProxyTunnelConfig(serviceId, 12345, "192.168.10.11", 8080);
            }
        };
        sd = new ServicesDefinitionImpl();
        pms.setServicesDefinition(sd);
        sd.afterPropertiesSet();
        pms.setConnectionManagerService(new ConnectionManagerServiceImpl() {
        });
        server = new WebSocketProxyServerImpl(pms, sd) {
            @Override
            protected WebSocketProxyForwarder createForwarder(String serviceId, WebSocketSession webSocketServerSession, String targetPath) {
                return new WebSocketProxyForwarder(serviceId, targetPath, pms, webSocketServerSession) {
                    @Override
                    WebSocketSession createWebSocketClientSession() {
                        return null;
                    }
                    @Override
                    public void forwardMessage(WebSocketMessage<?> webSocketMessage) throws IOException {
                        // No-Op
                    }
                    @Override
                    public void close() {
                        closedCalls.incrementAndGet();
                    }
                };
            }
        };

        pms.setWebSocketProxyServer (server);

        wss1 = new StandardWebSocketSession(null, null, null, null) {
            @Override
            public URI getUri() {
                return URI.create("/ws/cerebro/test");
            }
        };

        wss2 = new StandardWebSocketSession(null, null, null, null) {
            @Override
            public URI getUri() {
                return URI.create("/ws/cerebro/test");
            }
        };

        closedCalls = new AtomicInteger(0);
    }

    @Test
    public void testHandleMessage() throws Exception {

        server.handleMessage(wss1, new TextMessage("hello"));

        Map<String, Map<String, Map<String, WebSocketProxyForwarder>>>  forwarders = server.getForwarders();

        assertEquals(1, forwarders.size());

        server.handleMessage(wss2, new TextMessage("hello"));

        forwarders = server.getForwarders();

        assertEquals(1, forwarders.size());

        assertEquals(2, forwarders.get("cerebro").size());

        assertEquals(1, forwarders.get("cerebro").get(wss2.getId()).size());

        assertNotNull(forwarders.get("cerebro").get(wss2.getId()).get("/test"));
    }

    @Test
    public void testRemoveForwardersForService() throws Exception {

        testHandleMessage();

        server.removeForwardersForService("cerebro");

        Map<String, Map<String, Map<String, WebSocketProxyForwarder>>>  forwarders = server.getForwarders();

        assertEquals(0, forwarders.size());

        assertEquals(2, closedCalls.get());
    }

    @Test
    public void testAfterConnectionClosed() throws Exception {

        testHandleMessage();

        server.afterConnectionClosed(wss2, CloseStatus.NORMAL);

        Map<String, Map<String, Map<String, WebSocketProxyForwarder>>>  forwarders = server.getForwarders();

        assertEquals(1, forwarders.size());

        assertEquals(1, forwarders.get("cerebro").size());

        assertEquals(1, forwarders.get("cerebro").get(wss1.getId()).size());

        assertNotNull(forwarders.get("cerebro").get(wss1.getId()).get("/test"));

        assertEquals(1, closedCalls.get());
    }
}

package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.model.ProxyTunnelConfig;
import ch.niceideas.eskimo.services.ConnectionManagerException;
import ch.niceideas.eskimo.services.ConnectionManagerService;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.junit.Before;
import org.junit.Test;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

public class WebSocketProxyServerTest {

    private WebSocketProxyServer server = null;

    private ProxyManagerService pms;
    private ServicesDefinition sd;

    private WebSocketSession wss1;
    private WebSocketSession wss2;

    private AtomicInteger closedCalls;

    
    @Before
    public void setUp() throws Exception {

        pms = new ProxyManagerService() {
            @Override
            public ProxyTunnelConfig getTunnelConfig(String serviceId) {
                return new ProxyTunnelConfig(serviceId, 12345, "192.168.10.11", 8080);
            }
        };
        sd = new ServicesDefinition();
        pms.setServicesDefinition(sd);
        sd.afterPropertiesSet();
        pms.setConnectionManagerService(new ConnectionManagerService() {
        });
        server = new WebSocketProxyServer(pms, sd) {
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

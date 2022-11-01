package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.services.ConnectionManagerService;
import ch.niceideas.eskimo.services.ConnectionManagerServiceImpl;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.ServicesDefinitionImpl;
import ch.niceideas.eskimo.test.services.ProxyManagerServiceTestImpl;
import ch.niceideas.eskimo.test.services.WebSocketProxyServerTestImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
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

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-conf", "test-proxy", "test-web-socket"}) // using test implementation that overrides default implementation
public class WebSocketProxyServerTest {

    @Autowired
    private WebSocketProxyServerTestImpl server = null;

    private WebSocketSession wss1;
    private WebSocketSession wss2;

    @BeforeEach
    public void setUp() throws Exception {

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

        server.reset();
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

        assertEquals(2, server.getClosedCallsCount());
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

        assertEquals(1, server.getClosedCallsCount());
    }
}

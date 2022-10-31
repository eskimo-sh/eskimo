package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.test.infrastructure.HttpObjectsHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WebSocketProxyForwarderTest {

    private WebSocketSession clientSession = null;
    private WebSocketSession serverSession = null;
    private WebSocketProxyForwarder forwarder = null;

    private List<Object> clientMessages = null;
    private List<Object> serverMessages = null;
    
    @BeforeEach
    public void setUp() throws Exception {

        clientMessages = new ArrayList<>();
        serverMessages = new ArrayList<>();

        clientSession = HttpObjectsHelper.createWebSocketSession(clientMessages);

        serverSession = HttpObjectsHelper.createWebSocketSession(serverMessages);

        forwarder = new WebSocketProxyForwarder("zeppelin", "/zeppelin", null, serverSession) {
            @Override
            WebSocketSession createWebSocketClientSession() {
                return clientSession;
            }
        };
    }

    @Test
    public void testForwardMessage() throws Exception {

        forwarder.forwardMessage(new WebSocketMessage<>() {

            @Override
            public Object getPayload() {
                return "ABC";
            }

            @Override
            public int getPayloadLength() {
                return "ABC".length();
            }

            @Override
            public boolean isLast() {
                return false;
            }
        });

        assertTrue (serverMessages.isEmpty());
        assertFalse (clientMessages.isEmpty());

        assertEquals("ABC", ((WebSocketMessage<?>)clientMessages.get(0)).getPayload());
    }
}

package ch.niceideas.eskimo.proxy;

import org.junit.Before;
import org.junit.Test;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

public class WebSocketProxyForwarderTest {

    private WebSocketSession clientSession = null;
    private WebSocketSession serverSession = null;
    private WebSocketProxyForwarder forwarder = null;

    private List<Object> clientMessages = null;
    private List<Object> serverMessages = null;
    
    @Before
    public void setUp() throws Exception {

        clientMessages = new ArrayList<>();
        serverMessages = new ArrayList<>();

        clientSession = (WebSocketSession) Proxy.newProxyInstance(
                WebSocketProxyForwarderTest.class.getClassLoader(),
                new Class[]{WebSocketSession.class},
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("isOpen")) {
                        return true;
                    } else if (method.getName().equals("sendMessage")) {
                        return clientMessages.add (methodArgs[0]);
                    } else {
                        throw new UnsupportedOperationException(
                                "Unsupported method: " + method.getName());
                    }
                });

        serverSession = (WebSocketSession) Proxy.newProxyInstance(
                WebSocketProxyForwarderTest.class.getClassLoader(),
                new Class[]{WebSocketSession.class},
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("isOpen")) {
                        return true;
                    } else if (method.getName().equals("sendMessage")) {
                        return serverMessages.add (methodArgs[0]);
                    } else {
                        throw new UnsupportedOperationException(
                                "Unsupported method: " + method.getName());
                    }
                });

        forwarder = new WebSocketProxyForwarder("zeppelin", "/zeppelin", null, serverSession) {
            @Override
            WebSocketSession createWebSocketClientSession() {
                return clientSession;
            }
        };
    }

    @Test
    public void testForwardMessage() throws Exception {

        forwarder.forwardMessage(new WebSocketMessage<Object>() {

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

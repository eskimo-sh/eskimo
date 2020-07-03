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
import static org.junit.Assert.fail;

public class WebSocketProxyServerTest {

    private WebSocketProxyServer server = null;

    
    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void testHandleMessage() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testRemoveForwardersForService() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testAfterConnectionClosed() throws Exception {
        fail ("To Be Implemented");
    }
}

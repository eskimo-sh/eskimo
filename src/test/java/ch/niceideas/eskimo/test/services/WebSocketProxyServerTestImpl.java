package ch.niceideas.eskimo.test.services;

import ch.niceideas.eskimo.proxy.WebSocketProxyServer;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Profile("test-web-socket")
public class WebSocketProxyServerTestImpl implements WebSocketProxyServer {

    private final AtomicBoolean removeForwardersCalled = new AtomicBoolean(false);

    public boolean isRemoveForwardersCalled() {
        return removeForwardersCalled.get();
    }

    public void reset() {
        removeForwardersCalled.set(false);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {

    }

    @Override
    public void handleMessage(WebSocketSession webSocketServerSession, WebSocketMessage<?> webSocketMessage) throws Exception {

    }

    @Override
    public void handleTransportError(WebSocketSession webSocketSession, Throwable throwable) throws Exception {

    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    @Override
    public void removeForwardersForService(String serviceId) {
        removeForwardersCalled.set(true);
    }
}

package ch.niceideas.eskimo.test.services;

import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.proxy.WebSocketProxyForwarder;
import ch.niceideas.eskimo.proxy.WebSocketProxyServer;
import ch.niceideas.eskimo.proxy.WebSocketProxyServerImpl;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile("test-web-socket")
public class WebSocketProxyServerTestImpl extends WebSocketProxyServerImpl implements WebSocketProxyServer {

    private final AtomicBoolean removeForwardersCalled = new AtomicBoolean(false);
    private final AtomicInteger closedCalls = new AtomicInteger(0);

    @Autowired
    private ProxyManagerService proxyManagerService;

    public WebSocketProxyServerTestImpl(ProxyManagerService proxyManagerService, ServicesDefinition servicesDefinition) {
        super(proxyManagerService, servicesDefinition);
    }

    public boolean isRemoveForwardersCalled() {
        return removeForwardersCalled.get();
    }

    public int getClosedCallsCount() {
        return closedCalls.get();
    }

    public void reset() {
        removeForwardersCalled.set(false);
        closedCalls.set(0);
        forwarders.clear();
    }

    @Override
    public WebSocketProxyForwarder createForwarder(String serviceId, WebSocketSession webSocketServerSession, String targetPath) {

        return new WebSocketProxyForwarder(serviceId, targetPath, proxyManagerService, webSocketServerSession) {
            @Override
            protected WebSocketSession createWebSocketClientSession() {
                return null;
            }
            @Override
            public void forwardMessage(WebSocketMessage<?> webSocketMessage) {
                // No-Op
            }
            @Override
            public void close() {
                closedCalls.incrementAndGet();
            }
        };
    }

    @Override
    public void removeForwardersForService(String serviceId) {
        removeForwardersCalled.set(true);
        super.removeForwardersForService (serviceId);
    }

}

package ch.niceideas.eskimo.proxy;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.log4j.Logger;
import org.apache.tomcat.websocket.server.WsContextListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.server.RequestUpgradeStrategy;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class WebSocketProxyTest {

    private static final Logger logger = Logger.getLogger(WebSocketProxyTest.class);

    private AtomicReference<WebSocketSession> clientSessionReference;
    private BlockingQueue<String> clientBlockingQueue;

    private static AtomicReference<WebSocketSession> serverSessionReference;
    private static BlockingQueue<String> serverBlockingQueue;

    private StandardWebSocketClient client;

    private TomcatWebSocketTestServer server;

    private AnnotationConfigWebApplicationContext wac;

    private static int tomcatServerLocalPort = -1;

    @BeforeEach
    public void setup() throws Exception{

        client = new StandardWebSocketClient();

        wac = new AnnotationConfigWebApplicationContext();
        //wac.register(TestConfig.class);
        wac.register(WebSocketConfiguration.class);
        wac.refresh();

        server = new TomcatWebSocketTestServer(wac);
        server.afterPropertiesSet();

        tomcatServerLocalPort = server.getWsPort();

        clientSessionReference = new AtomicReference<>();
        clientBlockingQueue = new LinkedBlockingDeque<>();

        serverSessionReference = new AtomicReference<>();
        serverBlockingQueue = new LinkedBlockingDeque<>();
    }

    @AfterEach
    public void tearDown() throws Exception {
        try {
            // this is the only way to avoid waiting 15 seconds eachtime
            this.server.tomcatServer.destroy();
            //this.server.tomcatServer.stop();
            //this.server.destroy();
        }
        catch (Throwable t) {
            logger.error("Failed to undeploy application config", t);
        }
    }

    @Test
    public void testNominalWebSocketForwarder() throws Exception {

        client.doHandshake(new AbstractWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                clientSessionReference.set(session);
            }
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                clientBlockingQueue.offer(message.getPayload());
            }
        }, server.getWsBaseUrl() + "ws/cerebro/application");

        waitAndGetSession(clientSessionReference).sendMessage(new TextMessage("HELLO"));

        assertEquals("PONG : HELLO", clientBlockingQueue.poll(10, TimeUnit.SECONDS));

        assertTrue (waitAndGetSession(clientSessionReference).isOpen());
    }

    @Test
    public void testServerInitiatedMessage() throws Exception {

        client.doHandshake(new AbstractWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                clientSessionReference.set(session);
            }
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                String msg = message.getPayload();
                if (StringUtils.isNotBlank(msg) && !msg.startsWith("PING") && !msg.startsWith("PONG")){
                    clientSessionReference.get().sendMessage(new TextMessage("PING : " + msg));
                }
            }
        }, server.getWsBaseUrl() + "ws/cerebro/application");

        waitAndGetSession(clientSessionReference).sendMessage(new TextMessage("CONNECT"));

        waitAndGetSession(serverSessionReference).sendMessage(new TextMessage("HELLO"));

        assertEquals("PING : HELLO", serverBlockingQueue.poll(10, TimeUnit.SECONDS));

        assertTrue (waitAndGetSession(clientSessionReference).isOpen());
    }

    @Test
    public void testNonConfiguredService() throws Exception {

        client.doHandshake(new AbstractWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                clientSessionReference.set(session);
            }
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                clientBlockingQueue.offer(message.getPayload());
            }
        }, server.getWsBaseUrl() + "ws/zeppelin/application");

        waitAndGetSession(clientSessionReference).sendMessage(new TextMessage("HELLO"));

        assertNull (clientBlockingQueue.poll(1, TimeUnit.SECONDS));

        // session must have been closed following error
        assertFalse (waitAndGetSession(clientSessionReference).isOpen());
    }

    WebSocketSession waitAndGetSession(AtomicReference<WebSocketSession> atomicSessionRef) throws InterruptedException {
        await().atMost(10, TimeUnit.SECONDS).until(() -> atomicSessionRef.get() != null);
        assertNotNull(atomicSessionRef.get());
        return atomicSessionRef.get();
    }

    @Configuration
    @EnableWebSocket
    static class WebSocketConfiguration implements WebSocketConfigurer {

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            ServicesDefinition sd = new ServicesDefinition();

            try {
                sd.afterPropertiesSet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            RequestUpgradeStrategy upgradeStrategy = new TomcatRequestUpgradeStrategy();

            // A dummy target application that just returns a text message with a "pong" prefix
            registry.addHandler(new AbstractWebSocketHandler() {
                        @Override
                        public void afterConnectionEstablished(WebSocketSession session) {
                            serverSessionReference.set (session);
                        }
                        @Override
                        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                            String msg = message.getPayload();
                            if (!msg.equals("CONNECT")) {
                                // store it
                                serverBlockingQueue.offer(msg);
                                // and send it back
                                session.sendMessage(new TextMessage("PONG : " + message.getPayload()));
                            }
                        }
                    },
                    "/application")
                .setHandshakeHandler(new DefaultHandshakeHandler(upgradeStrategy));

            // The Eskimo websocket proxy infrastructure
            registry.addHandler(new WebSocketProxyServer(new ProxyManagerService() {
                            @Override
                            public ProxyTunnelConfig getTunnelConfig(String serviceId) {
                                if (serviceId.equals("cerebro")) {
                                    return new ProxyTunnelConfig("cerebro", tomcatServerLocalPort, "dummy", -1);
                                }
                                if (serviceId.equals("grafana")) {
                                    return new ProxyTunnelConfig("grafana", tomcatServerLocalPort, "dummy", -1);
                                }
                                throw new IllegalStateException();
                            }
                        },
                        sd),
                        Arrays.stream(sd.listProxiedServices())
                        .map(sd::getService)
                        .map(service -> "/ws/" + service.getName() + "/**/*")
                        .toArray(String[]::new))
                    .setHandshakeHandler(new DefaultHandshakeHandler(upgradeStrategy));
        }
    }

    public static class TomcatWebSocketTestServer implements InitializingBean, DisposableBean {

        private final Tomcat tomcatServer;

        private final AnnotationConfigWebApplicationContext serverContext;

        private final Context webContext;

        public TomcatWebSocketTestServer(AnnotationConfigWebApplicationContext appContext) throws LifecycleException {
            tomcatServer = new Tomcat();
            tomcatServer.setPort(ProxyManagerService.generateLocalPort());
            tomcatServer.setBaseDir(createTempDir());
            tomcatServer.setSilent(true);

            serverContext = appContext;

            webContext = this.tomcatServer.addContext("", System.getProperty("java.io.tmpdir"));
            webContext.addApplicationListener(WsContextListener.class.getName());
            Tomcat.addServlet(webContext, "dispatcherServlet", new DispatcherServlet(this.serverContext))
                    .setAsyncSupported(true);
            webContext.addServletMappingDecoded("/", "dispatcherServlet");

        }

        private String createTempDir() {
            try {
                File tempFolder = File.createTempFile("tomcat.", ".workDir");
                tempFolder.delete();
                tempFolder.mkdir();
                tempFolder.deleteOnExit();
                return tempFolder.getAbsolutePath();
            }
            catch (IOException ex) {
                throw new RuntimeException("Unable to create temp directory", ex);
            }
        }

        public String getWsBaseUrl() {
            return "ws://localhost:" + tomcatServer.getConnector().getLocalPort() +"/";
        }

        public int getWsPort() {
            return tomcatServer.getConnector().getLocalPort();
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            webContext.start();
            tomcatServer.start();
        }

        @Override
        public void destroy() throws Exception {
            tomcatServer.destroy();
            //tomcatServer.stop();
        }
    }

}
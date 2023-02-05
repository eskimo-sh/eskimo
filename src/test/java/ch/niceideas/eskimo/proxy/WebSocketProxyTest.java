/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */


package ch.niceideas.eskimo.proxy;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.configurations.ProxyConfiguration;
import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.services.ServicesDefinitionImpl;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import ch.niceideas.eskimo.types.ServiceWebId;
import ch.niceideas.eskimo.utils.ActiveWaiter;
import org.apache.catalina.Context;
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
                if (!clientBlockingQueue.offer(message.getPayload())) {
                    logger.warn("AbstractWebSocketHandler - clientBlockingQueue.offer returned false");
                }
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
                if (!clientBlockingQueue.offer(message.getPayload())) {
                    logger.warn("AbstractWebSocketHandler - clientBlockingQueue.offer returned false");
                }
            }
        }, server.getWsBaseUrl() + "ws/zeppelin/application");

        waitAndGetSession(clientSessionReference).sendMessage(new TextMessage("HELLO"));

        assertNull (clientBlockingQueue.poll(1, TimeUnit.SECONDS));

        // session must have been closed following error
        assertFalse (waitAndGetSession(clientSessionReference).isOpen());
    }

    WebSocketSession waitAndGetSession(AtomicReference<WebSocketSession> atomicSessionRef) throws InterruptedException {
        ActiveWaiter.wait(() -> atomicSessionRef.get() != null);
        assertNotNull(atomicSessionRef.get());
        return atomicSessionRef.get();
    }

    @Configuration
    @EnableWebSocket
    static class WebSocketConfiguration implements WebSocketConfigurer {

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            ServicesDefinitionImpl sd = new ServicesDefinitionImpl();

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
                                if (!serverBlockingQueue.offer(msg)) {
                                    logger.warn("AbstractWebSocketHandler - serverBlockingQueue.offer returned false");
                                }
                                // and send it back
                                session.sendMessage(new TextMessage("PONG : " + message.getPayload()));
                            }
                        }
                    },
                    "/application")
                .setHandshakeHandler(new DefaultHandshakeHandler(upgradeStrategy));

            // The Eskimo websocket proxy infrastructure
            registry.addHandler(new WebSocketProxyServerImpl(new ProxyManagerServiceImpl() {
                            @Override
                            public ProxyTunnelConfig getTunnelConfig(ServiceWebId serviceId) {
                                if (serviceId.getService().getName().equals("cerebro")) {
                                    return new ProxyTunnelConfig(Service.from("cerebro"), tomcatServerLocalPort, Node.fromName("dummy"), -1);
                                }
                                if (serviceId.getService().getName().equals("grafana")) {
                                    return new ProxyTunnelConfig(Service.from("grafana"), tomcatServerLocalPort, Node.fromName("dummy"), -1);
                                }
                                throw new IllegalStateException();
                            }
                        },
                        sd),
                        Arrays.stream(sd.listProxiedServices())
                        .map(sd::getServiceDefinition)
                        .map(service -> ProxyConfiguration.ESKIMO_WEB_SOCKET_URL_PREFIX + "/" + service.getName() + "/**/*")
                        .toArray(String[]::new))
                    .setHandshakeHandler(new DefaultHandshakeHandler(upgradeStrategy));
        }
    }

    public static class TomcatWebSocketTestServer implements InitializingBean, DisposableBean {

        private final Tomcat tomcatServer;

        private final Context webContext;

        public TomcatWebSocketTestServer(AnnotationConfigWebApplicationContext appContext) {
            tomcatServer = new Tomcat();
            tomcatServer.setPort(ProxyManagerService.generateLocalPort());
            tomcatServer.setBaseDir(createTempDir());
            tomcatServer.setSilent(true);

            webContext = this.tomcatServer.addContext("", System.getProperty("java.io.tmpdir"));
            webContext.addApplicationListener(WsContextListener.class.getName());
            Tomcat.addServlet(webContext, "dispatcherServlet", new DispatcherServlet(appContext))
                    .setAsyncSupported(true);
            webContext.addServletMappingDecoded("/", "dispatcherServlet");

        }

        private String createTempDir() {
            try {
                File tempFolder = File.createTempFile("tomcat.", ".workDir");
                assertTrue (tempFolder.delete());
                assertTrue (tempFolder.mkdir());
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
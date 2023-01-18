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

package ch.niceideas.eskimo.configurations;

import ch.niceideas.eskimo.proxy.*;
import ch.niceideas.eskimo.services.ConfigurationService;
import ch.niceideas.eskimo.services.SSHCommandService;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class ProxyConfiguration implements WebSocketConfigurer {

    public static final String ESKIMO_WEB_SOCKET_URL_PREFIX = "/ws";

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private SSHCommandService sshCommandService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private WebSocketProxyServer webSocketProxyServer;

    @Autowired
    private Environment env;

    @Value("${server.servlet.context-path:#{null}}")
    private String configuredContextPath = "";

    @Value ("${proxy.maxConnections:50}")
    private int maxConnections = 50;

    @Value ("${proxy.readTimeout:30000}")
    private int readTimeout = 60000;

    @Value ("${proxy.connectTimeout:10000}")
    private int connectTimeout = 12000;

    @Value ("${proxy.connectionRequestTimeout:20000}")
    private int connectionRequestTimeout = 20000;

    /**
     * This is to avoid following problem with REST requests passed by grafana
     *
     * <code>
     *     2019-08-28T14:42:32,123 INFO  [http-nio-9090-exec-8] o.a.j.l.DirectJDKLog: Error parsing HTTP request header
     * Note: further occurrences of HTTP request parsing errors will be logged at DEBUG level.
     * java.lang.IllegalArgumentException: Invalid character found in the request target. The valid characters are defined in RFC 7230 and RFC 3986
     * at org.apache.coyote.http11.Http11InputBuffer.parseRequestLine(Http11InputBuffer.java:467)
     * at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:294)
     * at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:66)
     * at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:834)
     * at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1415)
     * at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:49)
     * at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
     * at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
     * at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:61)
     * at java.lang.Thread.run(Thread.java:748)
     * </code>
     */
    @Bean
    @Profile("!no-web-stack")
    public ConfigurableServletWebServerFactory webServerFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.addConnectorCustomizers(connector -> connector.setProperty("relaxedQueryChars", "|{}[]"));
        return factory;
    }

    @Bean
    @Profile("!no-web-stack")
    public ServletRegistrationBean<ServicesProxyServlet> proxyServletRegistrationBean(){

        ServletRegistrationBean<ServicesProxyServlet> servletRegistrationBean = new ServletRegistrationBean<>(
                new ServicesProxyServlet(
                        proxyManagerService,
                        servicesDefinition,
                        configuredContextPath,
                        maxConnections,
                        readTimeout,
                        connectTimeout,
                        connectionRequestTimeout),
                Arrays.stream(servicesDefinition.listProxiedServices())
                        .map(serviceName -> servicesDefinition.getService(serviceName))
                        .map(service -> "/" + service.getName() + "/*")
                        .toArray(String[]::new));

        servletRegistrationBean.addInitParameter(ProxyServlet.P_LOG, env.getProperty("logging_enabled", "false"));

        servletRegistrationBean.setName("eskimo-proxy");
        return servletRegistrationBean;
    }

    @Bean
    @Profile("!no-web-stack")
    public ServletRegistrationBean<WebCommandServlet> commandServletRegistrationBean(){

        ServletRegistrationBean<WebCommandServlet> servletRegistrationBean = new ServletRegistrationBean<>(
                new WebCommandServlet(
                        servicesDefinition, sshCommandService, configurationService),
                "/eskimo-command/*");

        servletRegistrationBean.setName("login-handler-command");
        return servletRegistrationBean;
    }

    @Bean
    @Profile("!no-web-stack")
    public ServletListenerRegistrationBean<ProxyServlet.ProxySessionListener> sessionListenerWithMetrics() {
        ServletListenerRegistrationBean<ProxyServlet.ProxySessionListener> listenerRegBean =
                new ServletListenerRegistrationBean<>();

        listenerRegBean.setListener(new ProxyServlet.ProxySessionListener());
        return listenerRegBean;
    }

    @Override
    @Profile("!no-web-stack")
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] allWsUrls = Arrays.stream(servicesDefinition.listProxiedServices())
                .map(serviceName -> servicesDefinition.getService(serviceName))
                .map(service -> ESKIMO_WEB_SOCKET_URL_PREFIX + "/" + service.getName() + "/**")
                .toArray(String[]::new);
        registry.addHandler(webSocketProxyServer, allWsUrls);
    }

    @Bean
    @Profile("!no-web-stack")
    public ServletServerContainerFactoryBean createServletServerContainerFactoryBean() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(WebSocketProxyForwarder.MESSAGE_SIZE_LIMIT);
        container.setMaxBinaryMessageBufferSize(WebSocketProxyForwarder.MESSAGE_SIZE_LIMIT);
        return container;
    }
}

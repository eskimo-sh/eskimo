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

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.service.ServiceDefinition;
import ch.niceideas.eskimo.model.service.proxy.ReplacementContext;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.test.infrastructure.HttpObjectsHelper;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import ch.niceideas.eskimo.test.services.ConnectionManagerServiceTestImpl;
import ch.niceideas.eskimo.test.services.WebSocketProxyServerTestImpl;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import ch.niceideas.eskimo.types.ServiceWebId;
import org.apache.catalina.ssi.ByteArrayServletOutputStream;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-web-socket", "test-conf", "test-connection-manager"})
public class ServicesProxyServletWithContextTest {

    @Autowired
    private ProxyManagerService pms;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private WebSocketProxyServerTestImpl webSocketProxyServerTest;

    @Autowired
    private ConnectionManagerServiceTestImpl connectionManagerServiceTest;

    private ServicesProxyServlet servlet;

    @BeforeEach
    public void setUp() throws Exception {
        configurationServiceTest.setStandard2NodesInstallStatus();

        pms.removeServerForService (Service.from("gluster"), Node.fromAddress("192.168.10.11"));
        pms.removeServerForService (Service.from("gluster"), Node.fromAddress("192.168.10.13"));

        pms.removeServerForService (Service.from("zeppelin"), Node.fromAddress("192.168.10.11"));

        pms.removeServerForService (Service.from("flink-runtime"), Node.fromAddress("192.168.10.11"));
        pms.removeServerForService (Service.from("flink-runtime"), Node.fromAddress("192.168.10.12"));
        pms.removeServerForService (Service.from("flink-runtime"), Node.fromAddress("192.168.10.13"));

        pms.removeServerForService (Service.from("kubernetes-dashboard"), Node.fromAddress("192.168.10.11"));
        pms.removeServerForService (Service.from("kubernetes-dashboard"), Node.fromAddress("192.168.10.13"));

        pms.removeServerForService (Service.from("kibana"), Node.fromAddress("192.168.10.11"));
        pms.removeServerForService (Service.from("kibana"), Node.fromAddress("192.168.10.13"));

        connectionManagerServiceTest.reset();
        connectionManagerServiceTest.dontConnect();
        webSocketProxyServerTest.reset();

        servlet = new ServicesProxyServlet(pms, servicesDefinition, "/test-context", 5, 10000, 10000, 10000);
    }

    @Test
    public void testGetTargetUri() throws Exception {

        HttpServletRequest request = HttpObjectsHelper.createHttpServletRequest("cerebro", "/test-context");

        pms.updateServerForService(Service.from("cerebro"), Node.fromAddress("192.168.10.11"));

        assertEquals ("http://localhost:"
                + pms.getTunnelConfig(ServiceWebId.fromService(Service.from("cerebro"))).getLocalPort()
                + "/",
                servlet.getTargetUri(request));
    }

    @Test
    public void testRewriteUrlFromRequest() throws Exception {

        HttpServletRequest request = HttpObjectsHelper.createHttpServletRequest("cerebro", "/test-context");

        pms.updateServerForService(Service.from("cerebro"), Node.fromAddress("192.168.10.11"));

        assertEquals("http://localhost:"
                + pms.getTunnelConfig(ServiceWebId.fromService(Service.from("cerebro"))).getLocalPort()
                + "/cerebro/statistics?server=192.168.10.13",
                servlet.rewriteUrlFromRequest(request));
    }

    @Test
    public void testRewriteUrlFromResponse() throws Exception {

        HttpServletRequest request = HttpObjectsHelper.createHttpServletRequest("cerebro", "/test-context");

        pms.updateServerForService(Service.from("cerebro"), Node.fromAddress("192.168.10.11"));

        assertEquals("http://localhost:9090/test-context/cerebro/nodeStats/statistics=192.168.10.13",
                servlet.rewriteUrlFromResponse(request, "http://localhost:" +
                     pms.getTunnelConfig(ServiceWebId.fromService(Service.from("cerebro"))).getLocalPort() +
                    "/nodeStats/statistics=192.168.10.13"));
    }

    @Test
    public void testRewriteUrlFromResponse_sparkHistoryCase() throws Exception {
        HttpServletRequest request = HttpObjectsHelper.createHttpServletRequest("spark-console", "/test-context");

        pms.updateServerForService(Service.from("spark-console"), Node.fromAddress("192.168.10.11"));

        // http://localhost:9191/history/spark-application-1653861510346/jobs/

        assertEquals("http://localhost:9191/test-context/spark-console/history/spark-application-1652639268719/jobs/",
                servlet.rewriteUrlFromResponse(request, "http://localhost:9191/history/spark-application-1652639268719/jobs/"));
    }

    @Test
    public void testCopyResponseEntityText() throws Exception {

        String testString = "src=\"/TEST ABC STRING";

        ByteArrayEntity proxyServedEntity = new ByteArrayEntity(testString.getBytes(), ContentType.create("plain/text"));

        ByteArrayServletOutputStream responseOutputStream = new ByteArrayServletOutputStream();

        Map<String, Object> headers = new HashMap<>();

        HttpRequest proxyRequest = HttpObjectsHelper.createHttpRequest();

        HttpResponse proxyResponse = HttpObjectsHelper.createHttpResponse(proxyServedEntity);

        HttpServletRequest servletRequest = HttpObjectsHelper.createHttpServletRequest("cerebro", "/test-context");

        HttpServletResponse servletResponse = HttpObjectsHelper.createHttpServletResponse(headers, responseOutputStream);

        servlet.copyResponseEntity(proxyResponse, servletResponse, proxyRequest, servletRequest);

        assertEquals ("src=\"/test-context/cerebro/TEST ABC STRING", new String (responseOutputStream.toByteArray()));

        assertEquals(42, headers.get(HttpHeaders.CONTENT_LENGTH));
    }

}

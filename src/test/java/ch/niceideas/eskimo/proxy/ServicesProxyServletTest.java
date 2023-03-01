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
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@ActiveProfiles({"no-web-stack", "test-web-socket", "test-conf", "test-connection-manager", "test-services"})
public class ServicesProxyServletTest {

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

        pms.removeServerForService (Service.from("distributed-storage"), Node.fromAddress("192.168.10.11"));
        pms.removeServerForService (Service.from("distributed-storage"), Node.fromAddress("192.168.10.13"));

        pms.removeServerForService (Service.from("user-console"), Node.fromAddress("192.168.10.11"));

        pms.removeServerForService (Service.from("calculator-runtime"), Node.fromAddress("192.168.10.11"));
        pms.removeServerForService (Service.from("calculator-runtime"), Node.fromAddress("192.168.10.12"));
        pms.removeServerForService (Service.from("calculator-runtime"), Node.fromAddress("192.168.10.13"));

        pms.removeServerForService (Service.from("cluster-dashboard"), Node.fromAddress("192.168.10.11"));
        pms.removeServerForService (Service.from("cluster-dashboard"), Node.fromAddress("192.168.10.13"));

        pms.removeServerForService (Service.from("database-manager"), Node.fromAddress("192.168.10.11"));
        pms.removeServerForService (Service.from("database-manager"), Node.fromAddress("192.168.10.13"));

        connectionManagerServiceTest.reset();
        connectionManagerServiceTest.dontConnect();
        webSocketProxyServerTest.reset();

        servlet = new ServicesProxyServlet(pms, servicesDefinition, null, 5, 10000, 10000, 10000);
    }

    @Test
    public void testGetTargetUri() throws Exception {

        HttpServletRequest request = HttpObjectsHelper.createHttpServletRequest("database-manager");
        pms.updateServerForService(Service.from("database-manager"), Node.fromAddress("192.168.10.11"));

        assertEquals ("http://localhost:"
                + pms.getTunnelConfig(ServiceWebId.fromService(Service.from("database-manager"))).getLocalPort()
                + "/",
                servlet.getTargetUri(request));

        request = HttpObjectsHelper.createHttpServletRequest("distributed-filesystem");
        pms.updateServerForService(Service.from("distributed-filesystem"), Node.fromAddress("192.168.56.21"));

        assertEquals ("http://localhost:"
                        + pms.getTunnelConfig(ServiceWebId.fromServiceAndNode(Service.from("distributed-filesystem"), Node.fromAddress("192.168.56.21"))).getLocalPort()
                        + "/",
                servlet.getTargetUri(request));
    }

    @Test
    public void testGetTargetHost() throws Exception {
        HttpServletRequest request = HttpObjectsHelper.createHttpServletRequest("database-manager");
        pms.updateServerForService(Service.from("database-manager"), Node.fromAddress("192.168.10.11"));

        HttpHost dbmHost = servlet.getTargetHost(request);
        assertNotNull(dbmHost);
        assertEquals ("localhost", dbmHost.getHostName());
        assertEquals (pms.getTunnelConfig(ServiceWebId.fromService(Service.from("database-manager"))).getLocalPort(), dbmHost.getPort());

        request = HttpObjectsHelper.createHttpServletRequest("distributed-filesystem");
        pms.updateServerForService(Service.from("distributed-filesystem"), Node.fromAddress("192.168.56.21"));

        HttpHost dfHost = servlet.getTargetHost(request);
        assertNotNull(dfHost);
        assertEquals ("localhost", dfHost.getHostName());
        assertEquals (pms.getTunnelConfig(ServiceWebId.fromServiceAndNode(Service.from("distributed-filesystem"), Node.fromAddress("192.168.56.21"))).getLocalPort(), dfHost.getPort());
    }

    @Test
    public void testRewriteUrlFromRequest() throws Exception {

        HttpServletRequest request = HttpObjectsHelper.createHttpServletRequest("database-manager");
        pms.updateServerForService(Service.from("database-manager"), Node.fromAddress("192.168.10.11"));

        assertEquals("http://localhost:"
                + pms.getTunnelConfig(ServiceWebId.fromService(Service.from("database-manager"))).getLocalPort()
                + "/statistics?server=192.168.10.13",
                servlet.rewriteUrlFromRequest(request));

        request = HttpObjectsHelper.createHttpServletRequest("distributed-filesystem");
        pms.updateServerForService(Service.from("distributed-filesystem"), Node.fromAddress("192.168.56.21"));

        assertEquals("http://localhost:"
                        + pms.getTunnelConfig(ServiceWebId.fromServiceAndNode(Service.from("distributed-filesystem"), Node.fromAddress("192.168.56.21"))).getLocalPort()
                        + "/egmi/app.html",
                servlet.rewriteUrlFromRequest(request));
    }

    @Test
    public void testRewriteUrlFromResponse() throws Exception {

        HttpServletRequest request = HttpObjectsHelper.createHttpServletRequest("database-manager");
        pms.updateServerForService(Service.from("database-manager"), Node.fromAddress("192.168.10.11"));

        assertEquals("http://localhost:9090/database-manager/nodeStats/statistics=192.168.10.13",
                servlet.rewriteUrlFromResponse(request, "http://localhost:" +
                     pms.getTunnelConfig(ServiceWebId.fromService(Service.from("database-manager"))).getLocalPort() +
                    "/nodeStats/statistics=192.168.10.13"));

        request = HttpObjectsHelper.createHttpServletRequest("distributed-filesystem");
        pms.updateServerForService(Service.from("distributed-filesystem"), Node.fromAddress("192.168.56.21"));

        assertEquals("http://localhost:9090/distributed-filesystem/192-168-56-21/egmi/app.html",
                servlet.rewriteUrlFromResponse(request, "http://localhost:" +
                        pms.getTunnelConfig(ServiceWebId.fromServiceAndNode(Service.from("distributed-filesystem"), Node.fromAddress("192.168.56.21"))).getLocalPort() +
                        "/egmi/app.html"));
    }

    @Test
    public void testRewriteUrlFromResponse_sparkHistoryCase() throws Exception {
        HttpServletRequest request = HttpObjectsHelper.createHttpServletRequest("user-console");

        pms.updateServerForService(Service.from("user-console"), Node.fromAddress("192.168.10.11"));

        // http://localhost:9191/history/spark-application-1653861510346/jobs/

        assertEquals("http://localhost:9191/user-console/history/spark-application-1652639268719/jobs/",
                servlet.rewriteUrlFromResponse(request, "http://localhost:9191/history/spark-application-1652639268719/jobs/"));
    }

    @Test
    public void testNominalReplacements() {

        ServiceDefinition kafkaManagerService = servicesDefinition.getServiceDefinition(Service.from("broker-manager"));
        assertNotNull(kafkaManagerService);

        String toReplace  = "\n <a href='/toto.txt'>\na/a>";
        ReplacementContext ctx = new ReplacementContext("", "test/test", "", "", "", "");
        String result = servlet.performReplacements(kafkaManagerService, "", ctx, toReplace );
        assertEquals("\n" +
                " <a href='/test/test/toto.txt'>\n" +
                "a/a>", result);
    }

    @Test
    public void testPageScripterInjection() {
        ServiceDefinition kubeDashboardService = servicesDefinition.getServiceDefinition(Service.from("cluster-dashboard"));
        assertNotNull(kubeDashboardService);

        String toReplace  = ""+"" +
                "<html>" +
                "<head>" +
                "<title>one title</title>" +
                "</head>" +
                "<body>" +
                "<div id=\"a\"></div>" +
                "</body>" +
                "</html>";

        ReplacementContext ctx = new ReplacementContext("", "test/test", "", "", "", "");
        String result = servlet.performReplacements(kubeDashboardService, "api/v1/namespaces/cluster-dashboard/services/https:cluster-dashboard:/proxy/", ctx, toReplace );

        //System.err.println (result);

        assertTrue(result.contains ("<html><head><title>one title</title></head><body><div id=\"a\"></div><script>function eskimoLoginChecker() {"));
        assertTrue(result.contains ("eskimoLoginChecker();</script></body></html>"));
    }

    @Test
    public void testuserConsoleReplacements() {

        ServiceDefinition userConsoleService = servicesDefinition.getServiceDefinition(Service.from("user-console"));

        String toReplace = "function(e, t, n) {\n" +
                "    \"use strict\";\n" +
                "    function r() {\n" +
                "        this.getPort = function() {\n" +
                "            var e = Number(location.port);\n" +
                "            return e || (e = 80,\n" +
                "            \"https:\" === location.protocol && (e = 443)),\n" +
                "            9e3 === e && (e = 8080),\n" +
                "            e\n" +
                "        }\n" +
                "        ,\n" +
                "        this.getWebsocketUrl = function() {\n" +
                "            var t = \"https:\" === location.protocol ? \"wss:\" : \"ws:\";\n" +
                "            return t+\"//\"+location.hostname+\":\"+this.getPort()+e(location.pathname)+\"/ws\"\n"+
                "        }\n" +
                "        ,\n" +
                "        this.getBase = function() {\n" +
                "            return location.protocol + \"//\" + location.hostname + \":\" + this.getPort() + location.pathname\n" +
                "        }\n" +
                "        ,\n" +
                "        this.getRestApiBase = function() {\n" +
                "            return e(this.getBase()) + \"/api\"\n" +
                "        }\n" +
                "        ;\n" +
                "        var e = function(e) {\n" +
                "            return e.replace(/\\/$/, \"\")\n" +
                "        }\n" +
                "    }\n" +
                "    angular.module(\"user-consoleWebApp\").service(\"baseUrlSrv\", r)\n" +
                "}";

        ReplacementContext ctx = new ReplacementContext("", "test/test", "", "", "", "");
        String result = servlet.performReplacements(userConsoleService, "controllers.js", ctx, toReplace );

        assertEquals("function(e, t, n) {\n" +
                "    \"use strict\";\n" +
                "    function r() {\n" +
                "        this.getPort = function() {\n" +
                "            var e = Number(location.port);\n" +
                "            return e || (e = 80,\n" +
                "            \"https:\" === location.protocol && (e = 443)),\n" +
                "            9e3 === e && (e = 8080),\n" +
                "            e\n" +
                "        }\n" +
                "        ,\n" +
                "        this.getWebsocketUrl = function() {\n" +
                "            var t = \"https:\" === location.protocol ? \"wss:\" : \"ws:\";\n" +
                "            return t + \"//\" + location.hostname + \":\" + this.getPort() + \"/ws\" + e(location.pathname).replace(\"\" != \"\" ? \"\" : \"dummy_not_matching_anything\", \"\") + \"/ws\"\n" +
                "        }\n" +
                "        ,\n" +
                "        this.getBase = function() {\n" +
                "            return location.protocol + \"//\" + location.hostname + \":\" + this.getPort() + location.pathname\n" +
                "        }\n" +
                "        ,\n" +
                "        this.getRestApiBase = function() {\n" +
                "            return e(this.getBase()) + \"/api\"\n" +
                "        }\n" +
                "        ;\n" +
                "        var e = function(e) {\n" +
                "            return e.replace(/\\/$/, \"\")\n" +
                "        }\n" +
                "    }\n" +
                "    angular.module(\"user-consoleWebApp\").service(\"baseUrlSrv\", r)\n" +
                "}", result);
    }

    @Test
    public void testCopyResponseEntityNotText() throws Exception {

        String testString = "TEST ABC STRING";

        ByteArrayEntity proxyServedEntity = new ByteArrayEntity(testString.getBytes(), ContentType.create("plain/text"));

        ByteArrayServletOutputStream responseOutputStream = new ByteArrayServletOutputStream();

        HttpRequest proxyRequest = HttpObjectsHelper.createHttpRequest();

        HttpResponse proxyResponse = HttpObjectsHelper.createHttpResponse(proxyServedEntity);

        HttpServletRequest servletRequest = HttpObjectsHelper.createHttpServletRequest("database-manager");

        HttpServletResponse servletResponse = HttpObjectsHelper.createHttpServletResponse(new HashMap<>(), responseOutputStream);

        servlet.copyResponseEntity(proxyResponse, servletResponse, proxyRequest, servletRequest);

        assertEquals (testString, new String (responseOutputStream.toByteArray()));
    }

    @Test
    public void testCopyResponseEntityText() throws Exception {

        String testString = "src=\"/TEST ABC STRING";

        ByteArrayEntity proxyServedEntity = new ByteArrayEntity(testString.getBytes(), ContentType.create("plain/text"));

        ByteArrayServletOutputStream responseOutputStream = new ByteArrayServletOutputStream();

        Map<String, Object> headers = new HashMap<>();

        HttpRequest proxyRequest = HttpObjectsHelper.createHttpRequest();

        HttpResponse proxyResponse = HttpObjectsHelper.createHttpResponse(proxyServedEntity);

        HttpServletRequest servletRequest = HttpObjectsHelper.createHttpServletRequest("database-manager");

        HttpServletResponse servletResponse = HttpObjectsHelper.createHttpServletResponse(headers, responseOutputStream);

        servlet.copyResponseEntity(proxyResponse, servletResponse, proxyRequest, servletRequest);

        assertEquals ("src=\"/database-manager/TEST ABC STRING", new String (responseOutputStream.toByteArray()));

        assertEquals(38, headers.get(HttpHeaders.CONTENT_LENGTH));
    }

    @Test
    public void testNewProxyRequestWithEntity() throws Exception {

        HttpServletRequest servletRequest = HttpObjectsHelper.createHttpServletRequest("database-manager");

        ClassicHttpRequest request = servlet.newProxyRequestWithEntity(
                "POST", "localhost:9191/test", servletRequest);

        assertNotNull (request);
        assertNotNull (request.getEntity());
        assertEquals ("InputStreamEntity", request.getEntity().getClass().getSimpleName());

        servletRequest = HttpObjectsHelper.createHttpServletRequest("distributed-filesystem");

        request = servlet.newProxyRequestWithEntity(
                "POST", "localhost:9191/test", servletRequest);

        assertNotNull (request);
        assertNotNull (request.getEntity());
        assertEquals ("UrlEncodedFormEntity", request.getEntity().getClass().getSimpleName());
    }

}

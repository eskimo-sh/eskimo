/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.Service;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.services.*;
import com.trilead.ssh2.Connection;
import org.apache.catalina.ssi.ByteArrayServletOutputStream;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.BasicHttpEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServicesProxyServletTest {

    private ProxyManagerService pms;
    private ServicesDefinition sd;

    private ServicesProxyServlet servlet;

    @BeforeEach
    public void setUp() throws Exception {
        pms = new ProxyManagerService();
        sd = new ServicesDefinition();
        sd.afterPropertiesSet();
        servlet = new ServicesProxyServlet(pms, sd, "");
        pms.setServicesDefinition(sd);

        pms.setConfigurationService(new ConfigurationService() {
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
        });

        pms.setConnectionManagerService(new ConnectionManagerService() {
            @Override
            protected void recreateTunnels(Connection connection, String node) throws ConnectionManagerException {
                // No Op
            }
            @Override
            public void recreateTunnels(String host) throws ConnectionManagerException {
                // No Op
            }
        });
        pms.setWebSocketProxyServer(new WebSocketProxyServer(pms, sd) {
            @Override
            public void removeForwardersForService(String serviceId) {
                // No Op
            }
        });
    }

    @Test
    public void testGetTargetUri() throws Exception {
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpServletRequest.class },
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("getRequestURI")) {
                        return "/cerebro/statistics";
                    } else if (method.getName().equals("getPathInfo")) {
                        return "/cerebro/statistics";
                    } else {
                        throw new UnsupportedOperationException(
                                "Unsupported method: " + method.getName());
                    }
                });
        pms.updateServerForService("cerebro", "192.168.10.11");

        assertEquals ("http://localhost:"
                + pms.getTunnelConfig("cerebro").getLocalPort()
                + "/",
                servlet.getTargetUri(request));
    }

    @Test
    public void testRewriteUrlFromRequest() throws Exception {
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpServletRequest.class },
                (proxy, method, methodArgs) -> {
                    switch (method.getName()) {
                        case "getRequestURI":
                            return "/cerebro/statistics?server=192.168.10.13";
                        case "getPathInfo":
                            return "/cerebro/statistics";
                        case "getQueryString":
                            return "server=192.168.10.13";
                        default:
                            throw new UnsupportedOperationException(
                                    "Unsupported method: " + method.getName());
                    }
                });
        pms.updateServerForService("cerebro", "192.168.10.11");

        assertEquals("http://localhost:"
                + pms.getTunnelConfig("cerebro").getLocalPort()
                + "/cerebro/statistics?server=192.168.10.13",
                servlet.rewriteUrlFromRequest(request));
    }

    @Test
    public void testRewriteUrlFromResponse() throws Exception {
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpServletRequest.class },
                (proxy, method, methodArgs) -> {
                    switch (method.getName()) {
                        case "getRequestURI":
                            return "/cerebro/statistics?server=192.168.10.13";
                        case "getPathInfo":
                            return "/cerebro/statistics";
                        case "getRequestURL":
                            return new StringBuffer("http://localhost:9090/cerebro/statistics");
                        case "getQueryString":
                            return "server=192.168.10.13";
                        case "getContextPath":
                            return null;
                        default:
                            throw new UnsupportedOperationException(
                                    "Unsupported method: " + method.getName());
                    }
                });
        pms.updateServerForService("cerebro", "192.168.10.11");

        assertEquals("http://localhost:9090/cerebro/nodeStats/statistics=192.168.10.13",
                servlet.rewriteUrlFromResponse(request, "http://localhost:" +
                     pms.getTunnelConfig("cerebro").getLocalPort() +
                    "/nodeStats/statistics=192.168.10.13"));
    }

    @Test
    public void testNominalReplacements() throws Exception {

        Service kafkaManagerService = sd.getService("kafka-manager");

        String toReplace  = "\n <a href='/toto.txt'>\na/a>";
        String result = servlet.performReplacements(kafkaManagerService, "", "", "test/test", toReplace );
        assertEquals("\n" +
                " <a href='/test/test/toto.txt'>\n" +
                "a/a>", result);
    }

    @Test
    public void testZeppelinReplacements() throws Exception {

        Service zeppelinService = sd.getService("zeppelin");

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
                "    angular.module(\"zeppelinWebApp\").service(\"baseUrlSrv\", r)\n" +
                "}";

        String result = servlet.performReplacements(zeppelinService, "controllers.js", "", "test/test", toReplace );

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
                "            return t + \"//\" + location.hostname + \":\" + this.getPort() + \"/ws\" + e(location.pathname) + \"/ws\"\n" +
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
                "    angular.module(\"zeppelinWebApp\").service(\"baseUrlSrv\", r)\n" +
                "}", result);
    }

    @Test
    public void testCopyResponseEntityNotText() throws Exception {

        String testString = "TEST ABC STRING";

        BasicHttpEntity proxyServedEntity = new BasicHttpEntity();
        proxyServedEntity.setContent(new ByteArrayInputStream(testString.getBytes()));

        ByteArrayServletOutputStream responseOutputStream = new ByteArrayServletOutputStream();

        HttpRequest proxyRequest = (HttpRequest) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpRequest.class },
                (proxy, method, methodArgs) -> {
                    throw new UnsupportedOperationException(
                            "Unsupported method: " + method.getName());
                });

        HttpResponse proxyResponse = (HttpResponse) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpResponse.class },
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("getEntity")) {
                        return proxyServedEntity;
                    } else {
                        throw new UnsupportedOperationException(
                            "Unsupported method: " + method.getName());
                    }
                });

        HttpServletRequest servletRequest = (HttpServletRequest) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpServletRequest.class },
                (proxy, method, methodArgs) -> {
                    switch (method.getName()) {
                        case "getRequestURI":
                            return "/cerebro/statistics?server=192.168.10.13";
                        case "getPathInfo":
                            return "/cerebro/statistics";
                        case "getRequestURL":
                            return new StringBuffer("http://localhost:9090/cerebro/statistics");
                        case "getQueryString":
                            return "server=192.168.10.13";
                        case "getContextPath":
                            return null;
                        default:
                            throw new UnsupportedOperationException(
                                    "Unsupported method: " + method.getName());
                    }
                });

        HttpServletResponse servletResponse = (HttpServletResponse) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpServletResponse.class },
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("getOutputStream")) {
                        return responseOutputStream;
                    } else {
                        throw new UnsupportedOperationException(
                            "Unsupported method: " + method.getName());
                    }
                });

        servlet.copyResponseEntity(proxyResponse, servletResponse, proxyRequest, servletRequest);

        assertEquals (testString, new String (responseOutputStream.toByteArray()));
    }

    @Test
    public void testCopyResponseEntityText() throws Exception {

        String testString = "src=\"/TEST ABC STRING";

        BasicHttpEntity proxyServedEntity = new BasicHttpEntity();
        proxyServedEntity.setContent(new ByteArrayInputStream(testString.getBytes()));
        proxyServedEntity.setContentType("plain/text");

        ByteArrayServletOutputStream responseOutputStream = new ByteArrayServletOutputStream();

        Map<String, Object> headers = new HashMap<>();

        HttpRequest proxyRequest = (HttpRequest) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpRequest.class },
                (proxy, method, methodArgs) -> {
                    throw new UnsupportedOperationException(
                            "Unsupported method: " + method.getName());
                });

        HttpResponse proxyResponse = (HttpResponse) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpResponse.class },
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("getEntity")) {
                        return proxyServedEntity;
                    } else {
                        throw new UnsupportedOperationException(
                                "Unsupported method: " + method.getName());
                    }
                });

        HttpServletRequest servletRequest = (HttpServletRequest) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpServletRequest.class },
                (proxy, method, methodArgs) -> {
                    switch (method.getName()) {
                        case "getRequestURI":
                            return "/cerebro/statistics?server=192.168.10.13";
                        case "getPathInfo":
                            return "/cerebro/statistics";
                        case "getRequestURL":
                            return new StringBuffer("http://localhost:9090/cerebro/statistics");
                        case "getQueryString":
                            return "server=192.168.10.13";
                        case "getContextPath":
                            return null;
                        default:
                            throw new UnsupportedOperationException(
                                    "Unsupported method: " + method.getName());
                    }
                });

        HttpServletResponse servletResponse = (HttpServletResponse) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpServletResponse.class },
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("getOutputStream")) {
                        return responseOutputStream;
                    } else if (method.getName().equals("setIntHeader")) {
                        return headers.put ((String)methodArgs[0], methodArgs[1]);
                    } else {
                        throw new UnsupportedOperationException(
                                "Unsupported method: " + method.getName());
                    }
                });

        servlet.copyResponseEntity(proxyResponse, servletResponse, proxyRequest, servletRequest);

        assertEquals ("src=\"/cerebro/TEST ABC STRING", new String (responseOutputStream.toByteArray()));

        assertEquals(29, headers.get(HttpHeaders.CONTENT_LENGTH));
    }
}

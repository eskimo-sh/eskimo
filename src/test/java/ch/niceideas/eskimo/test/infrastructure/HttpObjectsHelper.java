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


package ch.niceideas.eskimo.test.infrastructure;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.controlers.NodesConfigController;
import ch.niceideas.eskimo.proxy.ProxyServlet;
import ch.niceideas.eskimo.proxy.ServicesProxyServletTest;
import ch.niceideas.eskimo.proxy.WebSocketProxyForwarderTest;
import org.apache.hc.core5.http.*;
import org.springframework.web.socket.WebSocketSession;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

public class HttpObjectsHelper {

    public static WebSocketSession createWebSocketSession(List<Object> messages) {
        return (WebSocketSession) Proxy.newProxyInstance(
                WebSocketProxyForwarderTest.class.getClassLoader(),
                new Class[]{WebSocketSession.class},
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("isOpen")) {
                        return true;
                    } else if (method.getName().equals("sendMessage")) {
                        return messages.add (methodArgs[0]);
                    } else {
                        throw new UnsupportedOperationException(
                                "Unsupported method: " + method.getName());
                    }
                });
    }

    public static HttpRequest createHttpRequest() {
        return (HttpRequest) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { ClassicHttpRequest.class },
                (proxy, method, methodArgs) -> {
                    throw new UnsupportedOperationException(
                            "Unsupported method: " + method.getName());
                });
    }

    public static HttpResponse createHttpResponse(HttpEntity proxyServedEntity) {
        return (HttpResponse) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { ClassicHttpResponse.class },
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("getEntity")) {
                        return proxyServedEntity;
                    } else {
                        throw new UnsupportedOperationException(
                                "Unsupported method: " + method.getName());
                    }
                });
    }

    public static HttpServletRequest createHttpServletRequest(String service) {
        return createHttpServletRequest (service, "");
    }

    public static HttpServletRequest createHttpServletRequest(String service, String context) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpServletRequest.class },
                (proxy, method, methodArgs) -> {
                    switch (method.getName()) {
                        case "getRequestURI":
                            if ("cerebro".equals(service)) {
                                return context + "/cerebro/statistics?server=192.168.10.13";
                            } else if ("spark-console".equals(service)) {
                                return context + "/spark-console/history/spark-application-1653861510346/jobs/";
                            } else if ("eskimo-command".equals(service)) {
                                return context + "/eskimo-command/kubeDashboardLoginToken";
                            }else {
                                throw new UnsupportedOperationException(
                                        "Unsupported method: " + method.getName());
                            }
                        case "getPathInfo":
                            if ("cerebro".equals(service)) {
                                return "/cerebro/statistics";
                            } else if ("spark-console".equals(service)) {
                                return "/history/spark-application-1653861510346/jobs/";
                            } else {
                                throw new UnsupportedOperationException(
                                        "Unsupported method: " + method.getName());
                            }
                        case "getRequestURL":
                            if ("cerebro".equals(service)) {
                                return new StringBuffer("http://localhost:9090" + context + "/cerebro/statistics");
                            } else if ("spark-console".equals(service)) {
                                return new StringBuffer("http://localhost:9191" + context + "/history/spark-application-1652639268719/jobs/");
                            } else {
                                throw new UnsupportedOperationException(
                                        "Unsupported method: " + method.getName());
                            }
                        case "getQueryString":
                            if ("cerebro".equals(service)) {
                                return "server=192.168.10.13";
                            } else {
                                throw new UnsupportedOperationException(
                                        "Unsupported method: " + method.getName());
                            }
                        case "getServletPath":
                            if ("eskimo-command".equals(service)) {
                                return "/eskimo-command";
                            } else {
                                throw new UnsupportedOperationException(
                                        "Unsupported method: " + method.getName());
                            }
                        case "getContextPath":
                            return StringUtils.isNotBlank(context) ? context : null;
                        case "getScheme":
                            return "http";
                        case "getServerName":
                            return "localhost";
                        case "getServerPort":
                            return 9191;
                        case "getAttribute":
                            if (methodArgs[0].equals(ProxyServlet.class.getSimpleName() + ".targetUri")) {
                                if ("cerebro".equals(service)) {
                                    return "http://localhost:9090/";
                                } else if ("spark-console".equals(service)) {
                                    return "http://localhost:9191/";
                                } else {
                                    throw new UnsupportedOperationException(
                                            "Unsupported method: " + method.getName());
                                }
                            } else {
                                throw new UnsupportedOperationException(
                                        "Unsupported attribute: " + methodArgs[0]);
                            }

                        default:
                            throw new UnsupportedOperationException(
                                    "Unsupported method: " + method.getName());
                    }
                });
    }

    public static HttpServletResponse createHttpServletResponse (
            Map<String, Object> headers,
            OutputStream responseOutputStream) {
        return (HttpServletResponse) Proxy.newProxyInstance(
                NodesConfigController.class.getClassLoader(),
                new Class[]{HttpServletResponse.class},
                (proxy, method, methodArgs) -> {
                    switch (method.getName()) {
                        case "getOutputStream":
                            return responseOutputStream;
                        case "setContentType":
                            return headers.put("Content-Type", methodArgs[0]);
                        case "addHeader":
                            return headers.put((String) methodArgs[0], methodArgs[1]);
                        case "setIntHeader":
                            return headers.put((String) methodArgs[0], methodArgs[1]);
                        default:
                            throw new UnsupportedOperationException(
                                    "Unsupported method: " + method.getName());
                    }
                });

    }

    public static HttpSession createHttpSession(Map<String, Object> sessionContent) {
        return (HttpSession) Proxy.newProxyInstance(
                NodesConfigController.class.getClassLoader(),
                new Class[]{HttpSession.class},
                (proxy, method, methodArgs) -> {
                    switch (method.getName()) {
                        case "setAttribute":
                            return sessionContent.put((String) methodArgs[0], methodArgs[1]);
                        case "getAttribute":
                            return sessionContent.get(methodArgs[0]);
                        case "removeAttribute":
                            return sessionContent.remove(methodArgs[0]);
                        default:
                            throw new UnsupportedOperationException(
                                    "Unsupported method: " + method.getName());
                    }
                });
    }
}

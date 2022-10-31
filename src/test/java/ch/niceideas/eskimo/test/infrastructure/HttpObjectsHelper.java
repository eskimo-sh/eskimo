package ch.niceideas.eskimo.test.infrastructure;

import ch.niceideas.eskimo.controlers.NodesConfigController;
import ch.niceideas.eskimo.proxy.ServicesProxyServletTest;
import ch.niceideas.eskimo.proxy.WebSocketProxyForwarderTest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.BasicHttpEntity;
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
                new Class[] { HttpRequest.class },
                (proxy, method, methodArgs) -> {
                    throw new UnsupportedOperationException(
                            "Unsupported method: " + method.getName());
                });
    }

    public static HttpResponse createHttpResponse(BasicHttpEntity proxyServedEntity) {
        return (HttpResponse) Proxy.newProxyInstance(
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
    }

    public static HttpServletRequest createHttpServletRequest(String service) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpServletRequest.class },
                (proxy, method, methodArgs) -> {
                    switch (method.getName()) {
                        case "getRequestURI":
                            if ("cerebro".equals(service)) {
                                return "/cerebro/statistics?server=192.168.10.13";
                            } else if ("spark-console".equals(service)) {
                                return "/spark-console/history/spark-application-1653861510346/jobs/";
                            } else if ("eskimo-command".equals(service)) {
                                return "/eskimo-command/kubeDashboardLoginToken";
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
                                return new StringBuffer("http://localhost:9090/cerebro/statistics");
                            } else if ("spark-console".equals(service)) {
                                return new StringBuffer("http://localhost:9191/history/spark-application-1652639268719/jobs/");
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
                            return null;
                        case "getScheme":
                            return "http";
                        case "getServerName":
                            return "localhost";
                        case "getServerPort":
                            return 9191;
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
                    if (method.getName().equals("getOutputStream")) {
                        return responseOutputStream;
                    } else if (method.getName().equals("setContentType")) {
                        return headers.put ("Content-Type", methodArgs[0]);
                    } else if (method.getName().equals("addHeader")) {
                        return headers.put ((String)methodArgs[0], methodArgs[1]);
                    } else if (method.getName().equals("setIntHeader")) {
                        return headers.put ((String)methodArgs[0], methodArgs[1]);
                    } else {
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

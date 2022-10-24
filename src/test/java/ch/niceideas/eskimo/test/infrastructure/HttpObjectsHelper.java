package ch.niceideas.eskimo.test.infrastructure;

import ch.niceideas.eskimo.controlers.NodesConfigController;
import ch.niceideas.eskimo.proxy.ServicesProxyServletTest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.BasicHttpEntity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.util.Map;

public class HttpObjectsHelper {

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
                            } else {
                                throw new UnsupportedOperationException(
                                        "Unsupported method: " + method.getName());
                            }
                        case "getPathInfo":
                            if ("cerebro".equals(service)) {
                                return "/cerebro/statistics";
                            } else {
                                throw new UnsupportedOperationException(
                                        "Unsupported method: " + method.getName());
                            }
                        case "getRequestURL":
                            if ("cerebro".equals(service)) {
                                return new StringBuffer("http://localhost:9090/cerebro/statistics");
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
            Map<String, String> headers,
            OutputStream responseOutputStream) {
        return (HttpServletResponse) Proxy.newProxyInstance(
                NodesConfigController.class.getClassLoader(),
                new Class[]{HttpServletResponse.class},
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("getOutputStream")) {
                        return responseOutputStream;
                    } else if (method.getName().equals("setContentType")) {
                        return headers.put ("Content-Type", (String)methodArgs[0]);
                    } else if (method.getName().equals("addHeader")) {
                        return headers.put ((String)methodArgs[0], (String)methodArgs[1]);
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

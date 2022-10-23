package ch.niceideas.eskimo.test.infrastructure;

import ch.niceideas.eskimo.controlers.NodesConfigController;

import javax.servlet.http.HttpSession;
import java.lang.reflect.Proxy;
import java.util.Map;

public class HttpSessionHelper {

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

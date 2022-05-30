package ch.niceideas.eskimo.proxy;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.services.*;
import org.apache.catalina.ssi.ByteArrayServletOutputStream;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class WebCommandServletTest {

    private static final Logger logger = Logger.getLogger(WebCommandServletTest.class);

    @Test
    public void testKubernetesDashboardLoginCase () throws Exception {

        ServicesDefinition sd = new ServicesDefinition();
        sd.afterPropertiesSet();

        SSHCommandService scs = new SSHCommandService() {
            @Override
            public String runSSHCommand(String node, String command){
                return "TEST_TOKEN";
            }
        };

        ConfigurationService cs = new ConfigurationService() {

            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
        };

        HttpServletRequest servletRequest = (HttpServletRequest) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpServletRequest.class },
                (proxy, method, methodArgs) -> {
                    switch (method.getName()) {
                        case "getRequestURI":
                            return "/eskimo-command/kubeDashboardLoginToken";
                        case "getServletPath":
                            return "/eskimo-command";
                        default:
                            throw new UnsupportedOperationException(
                                    "Unsupported method: " + method.getName());
                    }
                });

        ByteArrayServletOutputStream responseOutputStream = new ByteArrayServletOutputStream();

        Map<String, Object> headers = new HashMap<>();
        AtomicReference<String> contentType = new AtomicReference<>();

        HttpServletResponse servletResponse = (HttpServletResponse) Proxy.newProxyInstance(
                ServicesProxyServletTest.class.getClassLoader(),
                new Class[] { HttpServletResponse.class },
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("getOutputStream")) {
                        return responseOutputStream;
                    } else if (method.getName().equals("setContentType")) {
                        contentType.set ((String)methodArgs[0]);
                        return null;
                    } else if (method.getName().equals("setIntHeader")) {
                        return headers.put ((String)methodArgs[0], methodArgs[1]);
                    } else {
                        throw new UnsupportedOperationException(
                                "Unsupported method: " + method.getName());
                    }
                });

        WebCommandServlet wcs = new WebCommandServlet(sd, scs, cs);
        wcs.service(servletRequest, servletResponse);

        String result = new String (responseOutputStream.toByteArray(), StandardCharsets.UTF_8);
        assertEquals("{\"value\": \"TEST_TOKEN\"}", result);
        assertEquals("application/json", contentType.get());
    }
}
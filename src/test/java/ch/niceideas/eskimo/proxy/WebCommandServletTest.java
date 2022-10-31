package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.test.infrastructure.HttpObjectsHelper;
import org.apache.catalina.ssi.ByteArrayServletOutputStream;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebCommandServletTest {

    private static final Logger logger = Logger.getLogger(WebCommandServletTest.class);

    @Test
    public void testKubernetesDashboardLoginCase () throws Exception {

        ServicesDefinitionImpl sd = new ServicesDefinitionImpl();
        sd.afterPropertiesSet();

        SSHCommandService scs = new SSHCommandService() {
            @Override
            public String runSSHCommand(String node, String command){
                return "TEST_TOKEN";
            }
        };

        ConfigurationService cs = new ConfigurationServiceImpl() {

            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
        };

        HttpServletRequest servletRequest = HttpObjectsHelper.createHttpServletRequest("eskimo-command");

        ByteArrayServletOutputStream responseOutputStream = new ByteArrayServletOutputStream();

        Map<String, Object> headers = new HashMap<>();

        HttpServletResponse servletResponse = HttpObjectsHelper.createHttpServletResponse(headers, null);

        WebCommandServlet wcs = new WebCommandServlet(sd, scs, cs);
        wcs.service(servletRequest, servletResponse);

        String result = new String (responseOutputStream.toByteArray(), StandardCharsets.UTF_8);
        assertEquals("{\"value\": \"TEST_TOKEN\"}", result);
        assertEquals("application/json", headers.get("Content-Type"));
    }
}
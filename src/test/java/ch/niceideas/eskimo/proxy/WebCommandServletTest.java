package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.test.infrastructure.HttpObjectsHelper;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import ch.niceideas.eskimo.test.services.SSHCommandServiceTestImpl;
import org.apache.catalina.ssi.ByteArrayServletOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-web-socket", "test-conf", "test-ssh"})
public class WebCommandServletTest {

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private SSHCommandServiceTestImpl sshCommandServiceTest;

    @Test
    public void testKubernetesDashboardLoginCase () throws Exception {

        configurationServiceTest.setStandard2NodesInstallStatus();

        sshCommandServiceTest.setResult ("TEST_TOKEN");

        HttpServletRequest servletRequest = HttpObjectsHelper.createHttpServletRequest("eskimo-command");

        ByteArrayServletOutputStream responseOutputStream = new ByteArrayServletOutputStream();

        Map<String, Object> headers = new HashMap<>();

        HttpServletResponse servletResponse = HttpObjectsHelper.createHttpServletResponse(headers, responseOutputStream);

        WebCommandServlet wcs = new WebCommandServlet(servicesDefinition, sshCommandServiceTest, configurationServiceTest);
        wcs.service(servletRequest, servletResponse);

        String result = new String (responseOutputStream.toByteArray(), StandardCharsets.UTF_8);
        assertEquals("{\"value\": \"TEST_TOKEN\"}", result);
        assertEquals("application/json", headers.get("Content-Type"));
    }
}
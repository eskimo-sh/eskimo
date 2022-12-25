package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.test.services.ServicesDefinitionTestImpl;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-services", "test-system", "test-setup"})
public class ServicesControllerTest {

    @Autowired
    private ServicesController sc;

    @Autowired
    private ServicesDefinitionTestImpl servicesDefinitionTest;

    @BeforeEach
    public void setUp() throws Exception {
        servicesDefinitionTest.reset();
    }

    @Test
    public void testListServices() {

        assertEquals ("{\n" +
                "  \"services\": [\n" +
                "    \"distributed-time\",\n" +
                "    \"cluster-manager\",\n" +
                "    \"distributed-filesystem\",\n" +
                "    \"cluster-master\",\n" +
                "    \"cluster-slave\",\n" +
                "    \"cluster-dashboard\",\n" +
                "    \"broker\",\n" +
                "    \"broker-cli\",\n" +
                "    \"broker-manager\",\n" +
                "    \"calculator-runtime\",\n" +
                "    \"calculator-cli\",\n" +
                "    \"database-manager\",\n" +
                "    \"database\",\n" +
                "    \"user-console\"\n" +
                "  ],\n" +
                "  \"status\": \"OK\"\n" +
                "}", sc.listServices());

        servicesDefinitionTest.setError();

        assertEquals ("{\n" +
                "  \"error\": \"Test error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", sc.listServices());
    }

    @Test
    public void testListUIServices() {

        assertEquals ("{\n" +
                "  \"uiServices\": [\n" +
                "    \"distributed-filesystem\",\n" +
                "    \"cluster-dashboard\",\n" +
                "    \"broker-manager\",\n" +
                "    \"database-manager\",\n" +
                "    \"user-console\"\n" +
                "  ],\n" +
                "  \"status\": \"OK\"\n" +
                "}", sc.listUIServices());

        servicesDefinitionTest.setError();

        assertEquals ("{\n" +
                "  \"error\": \"Test error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", sc.listUIServices());
    }

    @Test
    public void testGetUIServicesConfig() throws Exception {

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesControllerTest/expectedUIServicesConfig.json"), StandardCharsets.UTF_8);

        assertEquals (expectedResult, sc.getUIServicesConfig());

        servicesDefinitionTest.setError();

        assertEquals ("{\n" +
                "  \"error\": \"Test error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", sc.getUIServicesConfig());
    }

    @Test
    public void testGetUIServicesStatusConfig() throws Exception {

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesControllerTest/expectedUIServicesStatusConfig.json"), StandardCharsets.UTF_8);

        assertEquals (expectedResult, sc.getUIServicesStatusConfig());
    }

    @Test
    public void testGetServicesDependencies() throws Exception {

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesControllerTest/expectedServicesDependencies.json"), StandardCharsets.UTF_8);

        assertEquals (expectedResult, sc.getServicesDependencies());
    }

    @Test
    public void testGetServicesConfigurations() throws Exception {

        //System.err.println(sc.getServicesConfigurations());

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesControllerTest/expectedServicesConfiguration.json"), StandardCharsets.UTF_8);

        assertTrue (new JSONObject(expectedResult).similar(new JSONObject(sc.getServicesConfigurations())));
    }

    @Test
    public void testListConfigServices() throws Exception {

        //System.err.println(sc.listConfigServices());

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesControllerTest/expecteConfigServices.json"), StandardCharsets.UTF_8);

        assertTrue(new JSONObject(expectedResult).similar(new JSONObject(sc.listConfigServices())));
    }

}

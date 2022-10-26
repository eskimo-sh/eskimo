package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-conf"})
public class SettingsOperationsCommandTest extends AbstractServicesDefinitionTest {

    @Autowired
    private ServicesSettingsService scs;

    @Autowired
    protected ConfigurationServiceTestImpl configurationServiceTest;

    private String jsonConfig = null;
    private String testForm = null;

    private String expectedJson = null;



    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testConfig.json"), StandardCharsets.UTF_8);
        testForm = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testForm.json"), StandardCharsets.UTF_8);

        expectedJson = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SettingsOperationsCommandTest/expected.json"), StandardCharsets.UTF_8);

        SecurityContextHelper.loginAdmin();
    }

    @Test
    public void toJSON () throws Exception {

        configurationServiceTest.setStandard2NodesSetup();

        configurationServiceTest.saveServicesSettings(new ServicesSettingsWrapper(jsonConfig));

        SettingsOperationsCommand command = SettingsOperationsCommand.create(testForm, scs);

        //System.err.println (command.toJSON());
        assertTrue (new JSONObject(expectedJson).similar(command.toJSON()));
    }

}

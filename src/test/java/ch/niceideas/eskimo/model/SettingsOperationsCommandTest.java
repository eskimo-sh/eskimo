package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.services.*;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class SettingsOperationsCommandTest extends AbstractServicesDefinitionTest {

    protected ConfigurationServiceImpl configurationService = null;

    private String jsonConfig = null;
    private String testForm = null;

    private String expectedJson = null;

    private ServicesSettingsService scs;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        configurationService = new ConfigurationServiceImpl();

        configurationService.setSetupService(setupService);
        configurationService.setServicesDefinition (def);

        jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testConfig.json"), StandardCharsets.UTF_8);
        testForm = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testForm.json"), StandardCharsets.UTF_8);

        expectedJson = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SettingsOperationsCommandTest/expected.json"), StandardCharsets.UTF_8);

        scs = new ServicesSettingsService();

        scs.setConfigurationService(configurationService);

        scs.setServicesDefinition(def);
    }

    @Test
    public void toJSON () throws Exception {

        configurationService.saveNodesConfig(StandardSetupHelpers.getStandard2NodesSetup());

        configurationService.saveServicesSettings(new ServicesSettingsWrapper(jsonConfig));

        SettingsOperationsCommand command = SettingsOperationsCommand.create(testForm, scs);

        System.err.println (command.toJSON());
        assertTrue (new JSONObject(expectedJson).similar(command.toJSON()));
    }

}

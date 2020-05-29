package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.ServicesSettingsWrapper;
import ch.niceideas.eskimo.services.*;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

public class ServicesSettingsControllerTest {

    private ServicesSettingsController scc = new ServicesSettingsController();

    @Test
    public void testLoadServicesConfig() throws Exception {

        scc.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesSettingsWrapper loadServicesSettings() throws FileException, SetupException {
                try {
                    String jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testConfig.json"));
                    return new ServicesSettingsWrapper(jsonConfig);
                } catch (IOException e) {
                    throw new SetupException(e);
                }
            }
        });

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesConfigControllerTest/expectedResult.json"));
        assertTrue(StringUtils.isNotBlank(expectedResult));
        assertEquals (expectedResult, scc.loadServicesSettings());

        scc.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesSettingsWrapper loadServicesSettings() throws FileException, SetupException {
                throw new SetupException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", scc.loadServicesSettings());
    }

    @Test
    public void testSaveServicesConfig() throws Exception {

        fail ("To Be Implemented");

        /*
        scc.setServicesSettingsService(new ServicesSettingsService() {
            @Override
            public void saveAndApplyServicesSettings(String settingsFormAsString)  throws FileException, SetupException, SystemException  {
                // No Op
            }
        });

        assertEquals ("{\"status\": \"OK\"}", scc.saveAndApplyServicesSettings("{\"dummyJson\" : \"dummyJson\"}"));

        scc.setServicesSettingsService(new ServicesSettingsService() {
            @Override
            public void saveAndApplyServicesSettings(String settingsFormAsString)  throws FileException, SetupException, SystemException  {
                throw new SetupException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", scc.saveAndApplyServicesSettings("{\"dummyJson\" : \"dummyJson\"}"));

                */
    }

    @Test
    public void testApplyServicesConfig() throws Exception {

        fail ("To Be Implemented");

        /*
        scc.setServicesSettingsService(new ServicesSettingsService() {
            @Override
            public void saveAndApplyServicesSettings(String settingsFormAsString)  throws FileException, SetupException, SystemException  {
                // No Op
            }
        });

        assertEquals ("{\"status\": \"OK\"}", scc.saveAndApplyServicesSettings("{\"dummyJson\" : \"dummyJson\"}"));

        scc.setServicesSettingsService(new ServicesSettingsService() {
            @Override
            public void saveAndApplyServicesSettings(String settingsFormAsString)  throws FileException, SetupException, SystemException  {
                throw new SetupException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", scc.saveAndApplyServicesSettings("{\"dummyJson\" : \"dummyJson\"}"));

                */
    }
}

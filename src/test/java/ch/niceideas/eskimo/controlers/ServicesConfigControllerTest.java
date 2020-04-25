package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.OperationsCommand;
import ch.niceideas.eskimo.model.ServicesConfigWrapper;
import ch.niceideas.eskimo.services.*;
import org.json.JSONException;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class ServicesConfigControllerTest {

    private ServicesConfigController scc = new ServicesConfigController();

    @Test
    public void testLoadServicesConfig() throws Exception {

        scc.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesConfigWrapper loadServicesConfig() throws FileException, SetupException {
                try {
                    String jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesConfigTest/testConfig.json"));
                    return new ServicesConfigWrapper(jsonConfig);
                } catch (IOException e) {
                    throw new SetupException(e);
                }
            }
        });

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesConfigControllerTest/expectedResult.json"));
        assertTrue(StringUtils.isNotBlank(expectedResult));
        assertEquals (expectedResult, scc.loadServicesConfig());

        scc.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesConfigWrapper loadServicesConfig() throws FileException, SetupException {
                throw new SetupException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", scc.loadServicesConfig());
    }

    @Test
    public void testSaveAndApplyServicesConfig() throws Exception {

        scc.setServicesConfigService(new ServicesConfigService() {
            @Override
            public void saveAndApplyServicesConfig(String configFormAsString)  throws FileException, SetupException, SystemException  {
                // No Op
            }
        });

        assertEquals ("{\"status\": \"OK\"}", scc.saveAndApplyServicesConfig("{\"dummyJson\" : \"dummyJson\"}"));

        scc.setServicesConfigService(new ServicesConfigService() {
            @Override
            public void saveAndApplyServicesConfig(String configFormAsString)  throws FileException, SetupException, SystemException  {
                throw new SetupException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", scc.saveAndApplyServicesConfig("{\"dummyJson\" : \"dummyJson\"}"));
    }
}

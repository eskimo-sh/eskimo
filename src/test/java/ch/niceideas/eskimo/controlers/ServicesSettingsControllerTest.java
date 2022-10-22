package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.OperationId;
import ch.niceideas.eskimo.model.ServicesSettingsWrapper;
import ch.niceideas.eskimo.model.SettingsOperationsCommand;
import ch.niceideas.eskimo.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ServicesSettingsControllerTest {

    private ServicesSettingsController scc = new ServicesSettingsController();

    @BeforeEach
    public void testSetup() {
        scc.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public boolean isProcessingPending() {
                return false;
            }
        });
    }

    @Test
    public void testLoadServicesConfig() throws Exception {

        scc.setConfigurationService(new ConfigurationServiceImpl() {
            @Override
            public ServicesSettingsWrapper loadServicesSettings() throws FileException, SetupException {
                try {
                    String jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testConfig.json"), StandardCharsets.UTF_8);
                    return new ServicesSettingsWrapper(jsonConfig);
                } catch (IOException e) {
                    throw new SetupException(e);
                }
            }
        });

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesConfigControllerTest/expectedResult.json"), StandardCharsets.UTF_8);
        assertTrue(StringUtils.isNotBlank(expectedResult));
        assertEquals (
                expectedResult.replace("\r", "").trim(),
                scc.loadServicesSettings().replace("\r", "").trim());

        scc.setConfigurationService(new ConfigurationServiceImpl() {
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
    public void testPrepareAndSaveServicesConfig() {

        injectDummyService();

        StringBuilder notifications = new StringBuilder();

        scc.setNotificationService(new NotificationService() {
            @Override
            public void addError (String message) {
                notifications.append (message);
            }
        });

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = NodesConfigControllerTest.createHttpSession(sessionContent);

        assertEquals ("{\n" +
                "  \"command\": {\n" +
                "    \"settings\": {},\n" +
                "    \"restarts\": []\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.prepareSaveServicesSettings("{\"dummyJson\" : \"dummyJson\"}", session));

        assertEquals ("{\"status\": \"OK\"}", scc.saveServicesSettings(session));

        scc.setServicesSettingsService(new ServicesSettingsService() {
            @Override
            public void applyServicesSettings(SettingsOperationsCommand command) throws FileException, SetupException, SystemException  {
                throw new SetupException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"VGVzdCBFcnJvcg==\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", scc.saveServicesSettings(session));

        assertEquals("Setting application failed ! Test Error", notifications.toString());

        injectDummyService();

        scc.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public boolean isProcessingPending() {
                return true;
            }
        });

        assertEquals("{\n" +
                "  \"messages\": \"Some backend operations are currently running. Please retry after they are completed..\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.saveServicesSettings(session));
    }

    void injectDummyService() {
        scc.setServicesSettingsService(new ServicesSettingsService() {
            @Override
            public ServicesSettingsWrapper prepareSaveSettings (
                    String settingsFormAsString,
                    Map<String, Map<String, List<SettingsOperationsCommand.ChangedSettings>>> changedSettings,
                    List<String> restartedServices) throws FileException, SetupException {
                String jsonConfig = null;
                try {
                    jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testConfig.json"), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    fail (e.getMessage());
                }
                return new ServicesSettingsWrapper(jsonConfig);
            }
            @Override
            public void applyServicesSettings(SettingsOperationsCommand command) throws FileException, SetupException, SystemException {
                // No Op
            }
        });
    }
}

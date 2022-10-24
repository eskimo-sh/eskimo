package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.ServicesSettingsWrapper;
import ch.niceideas.eskimo.model.SettingsOperationsCommand;
import ch.niceideas.eskimo.model.SimpleOperationCommand;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.test.infrastructure.HttpSessionHelper;
import ch.niceideas.eskimo.test.infrastructure.NotificationHelper;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-services", "test-conf"})
public class ServicesSettingsControllerTest {

    @Autowired
    private ServicesSettingsController scc;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Autowired
    private NotificationService notificationService;

    @BeforeEach
    public void testSetup() {
        if (operationsMonitoringService.isProcessingPending()) {
            operationsMonitoringService.operationsFinished(true);
        }

        SecurityContextHelper.loginAdmin();
    }

    @Test
    public void testLoadServicesConfig() throws Exception {

        String jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testConfig.json"), StandardCharsets.UTF_8);
        configurationServiceTest.saveServicesSettings(new ServicesSettingsWrapper(jsonConfig));

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesConfigControllerTest/expectedResult.json"), StandardCharsets.UTF_8);
        assertTrue(StringUtils.isNotBlank(expectedResult));
        assertEquals (
                expectedResult.replace("\r", "").trim(),
                scc.loadServicesSettings().replace("\r", "").trim());

        configurationServiceTest.setServiceSettingsError();

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", scc.loadServicesSettings());
    }

    @Test
    public void testPrepareAndSaveServicesConfig() throws Exception {

        injectDummyService();

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpSessionHelper.createHttpSession(sessionContent);

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


        assertEquals("Setting application failed ! Test Error", NotificationHelper.getAssembledNotifications(notificationService));

        injectDummyService();

        operationsMonitoringService.operationsStarted(new SimpleOperationCommand("test", "test", "192.168.10.15"));

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

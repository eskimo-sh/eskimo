package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.test.services.OperationsMonitoringServiceTestImpl;
import ch.niceideas.eskimo.test.services.SystemServiceTestImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-app-status", "test-master", "test-operations", "test-system", "test-setup"})
public class SystemStatusControllerTest {

    @Autowired
    private SystemStatusController ssc;

    @Autowired
    private OperationsMonitoringServiceTestImpl operationsMonitoringServiceTest;

    @Autowired
    private SystemServiceTestImpl systemServiceTest;

    @BeforeEach
    public void setUp() {
        operationsMonitoringServiceTest.endCommand(true);
    }

    @Test
    public void testgetLastOperationResult() {

        assertEquals ("{\n" +
                "  \"success\": true,\n" +
                "  \"status\": \"OK\"\n" +
                "}", ssc.getLastOperationResult());

        operationsMonitoringServiceTest.setLastOperationSuccessError();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ssc.getLastOperationResult());

        assertEquals ("Test Error", exception.getMessage());
    }

    @Test
    public void testGetStatus() {

        systemServiceTest.setReturnOKSystemStatus();

        assertEquals ("{\n" +
                "  \"nodeServicesStatus\": {\"status\": \"OK\"},\n" +
                "  \"processingPending\": false,\n" +
                "  \"systemStatus\": {\"status\": \"OK\"},\n" +
                "  \"status\": \"OK\"\n" +
                "}", ssc.getStatus());

        systemServiceTest.setReturnEmptySystemStatus();

        assertEquals ("{\n" +
                "  \"clear\": \"nodes\",\n" +
                "  \"processingPending\": false,\n" +
                "  \"systemStatus\": {\"status\": \"OK\"},\n" +
                "  \"status\": \"OK\"\n" +
                "}", ssc.getStatus());
    }
}

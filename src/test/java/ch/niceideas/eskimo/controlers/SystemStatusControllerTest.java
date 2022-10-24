package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.eskimo.model.MasterStatusWrapper;
import ch.niceideas.eskimo.model.SystemStatusWrapper;
import ch.niceideas.eskimo.services.*;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SystemStatusControllerTest {

    private SystemStatusController ssc;

    @BeforeEach
    public void setUp() {
        ssc = new SystemStatusController();

        ssc.setOperationsMonitoringService(new OperationsMonitoringServiceImpl() {
            @Override
            public boolean getLastOperationSuccess() {
                return true;
            }
        });
    }

    @Test
    public void testgetLastOperationResult() {

        ssc.setMasterService(new MasterService() {
            public MasterStatusWrapper getMasterStatus() {
                return MasterStatusWrapper.empty();
            }
        });

        assertEquals ("{\n" +
                "  \"success\": true,\n" +
                "  \"status\": \"OK\"\n" +
                "}", ssc.getLastOperationResult());

        ssc.setOperationsMonitoringService(new OperationsMonitoringServiceImpl() {
            @Override
            public boolean getLastOperationSuccess() {
                throw new IllegalStateException("Test Error");
            }
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ssc.getLastOperationResult());

        assertEquals ("Test Error", exception.getMessage());
    }

    @Test
    public void testGetStatus() {

        ssc.setSetupService(new SetupServiceImpl() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                // No Op
            }
        });

        ssc.setSystemService(new SystemServiceImpl(false) {
            @Override
            public SystemStatusWrapper getStatus() {
                return new SystemStatusWrapper("{\"status\":\"OK\"}");
            }
        });

        ssc.setMasterService(new MasterService() {
            @Override
            public MasterStatusWrapper getMasterStatus() {
                return MasterStatusWrapper.empty();
            }
        });

        ssc.setStatusService(new ApplicationStatusService() {
            @Override
            public JsonWrapper getStatus() {
                return new JsonWrapper( new JSONObject("{\"status\":\"OK\"}"));
            }
        });

        assertEquals ("{\n" +
                "  \"nodeServicesStatus\": {\"status\": \"OK\"},\n" +
                "  \"processingPending\": false,\n" +
                "  \"systemStatus\": {\"status\": \"OK\"},\n" +
                "  \"status\": \"OK\"\n" +
                "}", ssc.getStatus());

        ssc.setSystemService(new SystemServiceImpl(false) {
            @Override
            public SystemStatusWrapper getStatus()  {
                return new SystemStatusWrapper("{}");
            }
        });

        assertEquals ("{\n" +
                "  \"clear\": \"nodes\",\n" +
                "  \"processingPending\": false,\n" +
                "  \"systemStatus\": {\"status\": \"OK\"},\n" +
                "  \"status\": \"OK\"\n" +
                "}", ssc.getStatus());
    }
}

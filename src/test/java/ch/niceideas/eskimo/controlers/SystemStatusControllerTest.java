package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.SystemStatusWrapper;
import ch.niceideas.eskimo.services.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertThrows;

public class SystemStatusControllerTest {

    private SystemStatusController ssc = new SystemStatusController();

    @Test
    public void testgetLastOperationResult() {

        ssc.setSystemService(new SystemService(false) {
            public boolean getLastOperationSuccess() {
                return true;
            }
        });

        assertEquals ("{\n" +
                "  \"success\": true,\n" +
                "  \"status\": \"OK\"\n" +
                "}", ssc.getLastOperationResult());

        ssc.setSystemService(new SystemService(false) {
            public boolean getLastOperationSuccess() {
                throw new JSONException("Test Error");
            }
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            ssc.getLastOperationResult();
        });

        assertEquals ("org.json.JSONException: Test Error", exception.getMessage());
    }

    @Test
    public void testGetStatus() {

        ssc.setSetupService(new SetupService() {
            @Override
            public void ensureSetupCompleted() throws SetupException {
                // No Op
            }
        });

        ssc.setSystemService(new SystemService(false) {
            public SystemStatusWrapper getStatus() {
                return new SystemStatusWrapper("{\"status\":\"OK\"}");
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

        ssc.setSystemService(new SystemService(false) {
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

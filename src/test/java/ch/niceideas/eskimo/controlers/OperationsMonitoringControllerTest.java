package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.model.OperationId;
import ch.niceideas.eskimo.model.OperationStatus;
import ch.niceideas.eskimo.model.OperationsMonitoringStatusWrapper;
import ch.niceideas.eskimo.services.MessagingManager;
import ch.niceideas.eskimo.services.OperationsMonitoringService;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OperationsMonitoringControllerTest {

    private OperationsMonitoringController operationsMonitoringController = new OperationsMonitoringController();

    @Test
    public void testFetchOperationsStatus() {

        operationsMonitoringController.setOperationsMonitoringService(new OperationsMonitoringService() {
            @Override
            public OperationsMonitoringStatusWrapper getOperationsMonitoringStatus (Map<String, Integer> lastLinePerOp) {
                return new OperationsMonitoringStatusWrapper(new JSONObject(new HashMap<String, Object>() {{
                    put("messages", new JSONObject(new HashMap<>() {{
                        for (String opId : lastLinePerOp.keySet()) {
                            put(opId, new JSONObject(new TreeMap<>() {{
                                put("lastLine", 5);
                                put("lines", Base64.getEncoder().encodeToString(("TEST " + opId).getBytes(StandardCharsets.UTF_8)));
                            }}));
                        }
                    }}));

                    put("status", new JSONObject(new HashMap<>() {{
                        for (String opId : lastLinePerOp.keySet()) {
                            put(opId.toString(), OperationStatus.INIT.toString());
                        }
                    }}));
                }}));
            }
        });

        assertEquals ("{\n" +
                "    \"result\": \"OK\",\n" +
                "    \"messages\": {\n" +
                "        \"test1\": {\n" +
                "            \"lines\": \"VEVTVCB0ZXN0MQ==\",\n" +
                "            \"lastLine\": 5\n" +
                "        },\n" +
                "        \"test2\": {\n" +
                "            \"lines\": \"VEVTVCB0ZXN0Mg==\",\n" +
                "            \"lastLine\": 5\n" +
                "        },\n" +
                "        \"test3\": {\n" +
                "            \"lines\": \"VEVTVCB0ZXN0Mw==\",\n" +
                "            \"lastLine\": 5\n" +
                "        }\n" +
                "    },\n" +
                "    \"status\": {\n" +
                "        \"test1\": \"INIT\",\n" +
                "        \"test2\": \"INIT\",\n" +
                "        \"test3\": \"INIT\"\n" +
                "    }\n" +
                "}",
                operationsMonitoringController.fetchOperationsStatus("{" +
                    "\"test1\" : 1," +
                    "\"test2\" : 2," +
                    "\"test3\" : 3," +
                    "}"));
    }
}

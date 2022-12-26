package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.OperationsContext;
import ch.niceideas.eskimo.model.SimpleOperationCommand;
import ch.niceideas.eskimo.services.OperationsMonitoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-system", "test-setup"})
public class OperationsMonitoringControllerTest {

    @Autowired
    private OperationsMonitoringController operationsMonitoringController;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Test
    public void testFetchOperationsStatus() throws Exception {

        operationsMonitoringService.startCommand(new SimpleOperationCommand("test", "test", "192.168.10.15") {
              @Override
              public List<SimpleOperationId> getAllOperationsInOrder(OperationsContext context) {
                  return new ArrayList<>() {{
                      add (new SimpleOperationId("test", "1", "192.168.10.15"));
                      add (new SimpleOperationId("test", "2", "192.168.10.15"));
                      add (new SimpleOperationId("test", "3", "192.168.10.15"));
                  }} ;
              }
        });

        operationsMonitoringService.addInfo(
                new SimpleOperationCommand.SimpleOperationId("test", "1", "192.168.10.15"),
                "TEST-1");

        operationsMonitoringService.addInfo(
                new SimpleOperationCommand.SimpleOperationId("test", "2", "192.168.10.15"),
                "TEST-2");

        operationsMonitoringService.addInfo(
                new SimpleOperationCommand.SimpleOperationId("test", "3", "192.168.10.15"),
                "TEST-3");

        assertEquals ("{\n" +
                        "    \"result\": \"OK\",\n" +
                        "    \"messages\": {\n" +
                        "        \"test_2_192-168-10-15\": {\n" +
                        "            \"lines\": \"\",\n" +
                        "            \"lastLine\": 1\n" +
                        "        },\n" +
                        "        \"test_1_192-168-10-15\": {\n" +
                        "            \"lines\": \"VEVTVC0xCg==\",\n" +
                        "            \"lastLine\": 1\n" +
                        "        },\n" +
                        "        \"test_3_192-168-10-15\": {\n" +
                        "            \"lines\": \"VEVTVC0zCg==\",\n" +
                        "            \"lastLine\": 1\n" +
                        "        }\n" +
                        "    },\n" +
                        "    \"globalMessages\": {\n" +
                        "        \"lines\": \"\",\n" +
                        "        \"lastLine\": 0\n" +
                        "    },\n" +
                        "    \"labels\": [\n" +
                        "        {\n" +
                        "            \"operation\": \"test_1_192-168-10-15\",\n" +
                        "            \"label\": \"Executing test on 1 on 192.168.10.15\"\n" +
                        "        },\n" +
                        "        {\n" +
                        "            \"operation\": \"test_2_192-168-10-15\",\n" +
                        "            \"label\": \"Executing test on 2 on 192.168.10.15\"\n" +
                        "        },\n" +
                        "        {\n" +
                        "            \"operation\": \"test_3_192-168-10-15\",\n" +
                        "            \"label\": \"Executing test on 3 on 192.168.10.15\"\n" +
                        "        }\n" +
                        "    ],\n" +
                        "    \"status\": {\n" +
                        "        \"test_2_192-168-10-15\": \"INIT\",\n" +
                        "        \"test_1_192-168-10-15\": \"INIT\",\n" +
                        "        \"test_3_192-168-10-15\": \"INIT\"\n" +
                        "    }\n" +
                        "}",
                operationsMonitoringController.fetchOperationsStatus("{" +
                    "\"test_1_192-168-10-15\" : 0," +
                    "\"test_2_192-168-10-15\" : 1," +
                    "\"test_3_192-168-10-15\" : 2," +
                    "}"));
    }
}

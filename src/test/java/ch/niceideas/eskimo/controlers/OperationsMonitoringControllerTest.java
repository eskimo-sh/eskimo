/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */


package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.OperationsContext;
import ch.niceideas.eskimo.model.SimpleOperationCommand;
import ch.niceideas.eskimo.services.OperationsMonitoringService;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-system", "test-setup"})
public class OperationsMonitoringControllerTest {

    @Autowired
    private OperationsMonitoringController operationsMonitoringController;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @AfterEach
    public void tearDown() {
        operationsMonitoringService.endCommand(true);
    }

    @BeforeEach
    public void setup() throws Exception {

        operationsMonitoringService.startCommand(new SimpleOperationCommand(
                SimpleOperationCommand.SimpleOperation.COMMAND, Service.from("test"), Node.fromAddress("192.168.10.15")) {
            @Override
            public List<SimpleOperationId> getAllOperationsInOrder(OperationsContext context) {
                return new ArrayList<>() {{
                    add (new SimpleOperationId(SimpleOperation.COMMAND, Service.from("1"), Node.fromAddress("192.168.10.15")));
                    add (new SimpleOperationId(SimpleOperation.COMMAND, Service.from("2"), Node.fromAddress("192.168.10.15")));
                    add (new SimpleOperationId(SimpleOperation.COMMAND, Service.from("3"), Node.fromAddress("192.168.10.15")));
                }} ;
            }
        });

        operationsMonitoringService.addInfo(
                new SimpleOperationCommand.SimpleOperationId(SimpleOperationCommand.SimpleOperation.COMMAND, Service.from("1"), Node.fromAddress("192.168.10.15")),
                "TEST-1");

        operationsMonitoringService.addInfo(
                new SimpleOperationCommand.SimpleOperationId(SimpleOperationCommand.SimpleOperation.COMMAND, Service.from("2"), Node.fromAddress("192.168.10.15")),
                "TEST-2");

        operationsMonitoringService.addInfo(
                new SimpleOperationCommand.SimpleOperationId(SimpleOperationCommand.SimpleOperation.COMMAND, Service.from("3"), Node.fromAddress("192.168.10.15")),
                "TEST-3");
    }

    @Test
    public void testFetchOperationsStatusInitial() {

        JSONObject expectedResult = new JSONObject("{\n" +
                "    \"result\": \"OK\",\n" +
                "    \"messages\": {\n" +
                "        \"command_2_192-168-10-15\": {\n" +
                "            \"lines\": \"VEVTVC0yCg==\",\n" +
                "            \"lastLine\": 1\n" +
                "        },\n" +
                "        \"command_3_192-168-10-15\": {\n" +
                "            \"lines\": \"VEVTVC0zCg==\",\n" +
                "            \"lastLine\": 1\n" +
                "        },\n" +
                "        \"command_1_192-168-10-15\": {\n" +
                "            \"lines\": \"VEVTVC0xCg==\",\n" +
                "            \"lastLine\": 1\n" +
                "        }\n" +
                "    },\n" +
                "    \"globalMessages\": {\n" +
                "        \"lines\": \"\",\n" +
                "        \"lastLine\": 0\n" +
                "    },\n" +
                "    \"labels\": [\n" +
                "        {\n" +
                "            \"operation\": \"command_1_192-168-10-15\",\n" +
                "            \"label\": \"Executing command of 1 on 192.168.10.15\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"operation\": \"command_2_192-168-10-15\",\n" +
                "            \"label\": \"Executing command of 2 on 192.168.10.15\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"operation\": \"command_3_192-168-10-15\",\n" +
                "            \"label\": \"Executing command of 3 on 192.168.10.15\"\n" +
                "        }\n" +
                "    ],\n" +
                "    \"status\": {\n" +
                "        \"command_2_192-168-10-15\": \"INIT\",\n" +
                "        \"command_3_192-168-10-15\": \"INIT\",\n" +
                "        \"command_1_192-168-10-15\": \"INIT\"\n" +
                "    }\n" +
                "}");


        //assertEquals (expectedResult.toString(2), operationsMonitoringController.fetchOperationsStatus("{}"));
        assertTrue (expectedResult.similar(new JSONObject(operationsMonitoringController.fetchOperationsStatus("{}"))));
    }

    @Test
    public void testFetchOperationsStatus() {

        JSONObject expectedResult = new JSONObject("{\n" +
                "    \"result\": \"OK\",\n" +
                "    \"messages\": {\n" +
                "        \"command_2_192-168-10-15\": {\n" +
                "            \"lines\": \"\",\n" +
                "            \"lastLine\": 1\n" +
                "        },\n" +
                "        \"command_3_192-168-10-15\": {\n" +
                "            \"lines\": \"VEVTVC0zCg==\",\n" +
                "            \"lastLine\": 1\n" +
                "        },\n" +
                "        \"command_1_192-168-10-15\": {\n" +
                "            \"lines\": \"VEVTVC0xCg==\",\n" +
                "            \"lastLine\": 1\n" +
                "        }\n" +
                "    },\n" +
                "    \"globalMessages\": {\n" +
                "        \"lines\": \"\",\n" +
                "        \"lastLine\": 0\n" +
                "    },\n" +
                "    \"labels\": [\n" +
                "        {\n" +
                "            \"operation\": \"command_1_192-168-10-15\",\n" +
                "            \"label\": \"Executing command of 1 on 192.168.10.15\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"operation\": \"command_2_192-168-10-15\",\n" +
                "            \"label\": \"Executing command of 2 on 192.168.10.15\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"operation\": \"command_3_192-168-10-15\",\n" +
                "            \"label\": \"Executing command of 3 on 192.168.10.15\"\n" +
                "        }\n" +
                "    ],\n" +
                "    \"status\": {\n" +
                "        \"command_2_192-168-10-15\": \"INIT\",\n" +
                "        \"command_3_192-168-10-15\": \"INIT\",\n" +
                "        \"command_1_192-168-10-15\": \"INIT\"\n" +
                "    }\n" +
                "}");

        String lastLinePerOp = "{" +
                "\"command_1_192-168-10-15\" : 0," +
                "\"command_2_192-168-10-15\" : 1," +
                "\"command_3_192-168-10-15\" : 2," +
                "}";

        //assertEquals (expectedResult.toString(2), operationsMonitoringController.fetchOperationsStatus(lastLinePerOp));
        assertTrue (expectedResult.similar(new JSONObject(operationsMonitoringController.fetchOperationsStatus(lastLinePerOp))));
    }
}

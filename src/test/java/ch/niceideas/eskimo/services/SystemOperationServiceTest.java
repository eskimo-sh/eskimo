/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.OperationId;
import ch.niceideas.eskimo.model.SimpleOperationCommand;
import ch.niceideas.eskimo.test.services.OperationsMonitoringServiceTestImpl;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-setup", "test-conf", "test-system", "test-operations", "test-proxy", "test-kube", "test-ssh", "test-connection-manager"})
public class SystemOperationServiceTest {

    @Autowired
    private OperationsMonitoringServiceTestImpl operationsMonitoringServiceTest;

    @Autowired
    private SystemOperationService systemOperationService;

    @Autowired
    private NotificationService notificationService;

    @BeforeEach
    public void setUp() throws Exception {
        operationsMonitoringServiceTest.startCommand(new SimpleOperationCommand("test", "test", "test"));
    }

    @AfterEach
    public void tearDown() {
        operationsMonitoringServiceTest.endCommand(true);
    }

    @Test
    public void testApplySystemOperation() throws Exception {

        OperationId operation = new SimpleOperationCommand.SimpleOperationId("test", "test", "test");

        systemOperationService.applySystemOperation(operation,
                ml -> ml.addInfo("In operation"),
                null);

        Pair<Integer, String> messages = operationsMonitoringServiceTest.fetchNewMessages(operation, 0);
        //Pair<Integer, String> messages = messagingService.fetchElements(0);
        assertNotNull (messages);
        assertEquals(Integer.valueOf (3), messages.getKey());
        assertEquals("\n" +
                "Executing test on test on test\n" +
                "In operation\n" +
                "--> Done : Executing test on test on test\n" +
                "-------------------------------------------------------------------------------\n" +
                "--> Completed Successfuly.\n", messages.getValue());

        Pair<Integer, List<JSONObject>> notifications = notificationService.fetchElements(0);
        assertNotNull (notifications);
        assertEquals(Integer.valueOf(2), notifications.getKey());
        assertEquals("{\n" +
                "  \"type\": \"Doing\",\n" +
                "  \"message\": \"Executing test on test on test\"\n" +
                "}\n" +
                "{\n" +
                "  \"type\": \"Info\",\n" +
                "  \"message\": \"Executing test on test on test succeeded\"\n" +
                "}", notifications.getValue().stream()
                .map(object -> {
                    try {
                        return object.toString(2);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.joining("\n")));
    }
}

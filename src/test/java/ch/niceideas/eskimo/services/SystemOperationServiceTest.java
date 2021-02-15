/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.eskimo.model.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SystemOperationServiceTest extends AbstractSystemTest {

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        operationsMonitoringService.operationsStarted(new SimpleOperationCommand("test", "test", "test"));
    }

    @AfterEach
    public void tearDown() throws Exception {
        operationsMonitoringService.operationsFinished(true);
    }

    @Test
    public void testApplySystemOperation() throws Exception {

        OperationId operation = new SimpleOperationCommand.SimpleOperationId("test", "test", "test");

        systemOperationService.applySystemOperation(operation,
                ml -> ml.addInfo("In operation"),
                null);

        Pair<Integer, String> messages = operationsMonitoringService.fetchNewMessages(operation, 0);
        //Pair<Integer, String> messages = messagingService.fetchElements(0);
        assertNotNull (messages);
        assertEquals(Integer.valueOf (5), messages.getKey());
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

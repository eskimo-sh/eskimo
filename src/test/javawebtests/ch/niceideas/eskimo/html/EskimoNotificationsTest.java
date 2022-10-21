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

package ch.niceideas.eskimo.html;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EskimoNotificationsTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript("eskimoUtils.js");
        loadScript("eskimoNotifications.js");

        js("window.errorHandler = function () {};");

        // instantiate test object
        js("eskimoNotifications = new eskimo.Notifications();");
        js("eskimoNotifications.initialize()");
    }

    @Test
    public void testAddNotifications() throws Exception {
        js("eskimoNotifications.addNotification({\n" +
                "      \"type\": \"Info\",\n" +
                "      \"message\": \"Installation of Topology and settings on 192.168.10.11 succeeded\"\n" +
                "    });");

        assertJavascriptEquals("1", "$('#new-notifications-count').html()");

        // debugging
        //String notificationsHTML = js("return $('#notifications-container').html()").toString();
        //System.err.println (notificationsHTML);

        assertJavascriptEquals("1", "$('#notifications-container .card-body .d-flex .flex-grow-1 .noti-item-title').length");
        assertJavascriptEquals("Installation of Topology and settings on 192.168.10.11 succeeded",
                "$('#notifications-container .card-body .d-flex .flex-grow-1 .noti-item-subtitle').text()");
    }

    @Test
    public void testClearNotificationsWithLink() throws Exception {

        testAddNotifications();

        js("$.ajaxGet = function(object) {" +
                "    object.success();" +
                "}");

        js("eskimoNotifications.clearNotifications()");

        // debugging
        //String notificationsHTML = js("return $('#notifications-container').html()").toString();
        //System.err.println (notificationsHTML);

        assertJavascriptEquals("0", "$('#notifications-container .card-body .d-flex .flex-grow-1 .noti-item-title').length");
    }

}


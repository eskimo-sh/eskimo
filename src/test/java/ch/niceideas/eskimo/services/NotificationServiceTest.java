/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import junit.framework.TestCase;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class NotificationServiceTest extends TestCase {

    private NotificationService ns = null;

    @Before
    public void setUp() throws Exception {
        ns = new NotificationService();
        ns.addEvent("Error", "Test");
        ns.addEvent("Info", "Test2");
    }

    @Test
    public void testNominal() throws Exception {

        Pair<Integer,List<JSONObject>> result = ns.fetchElements(0);

        assertEquals(2, (int) result.getKey());
        assertEquals("{\"type\":\"Error\",\"message\":\"Test\"}", result.getValue().get(0).toString());
        assertEquals("{\"type\":\"Info\",\"message\":\"Test2\"}", result.getValue().get(1).toString());

        Pair<Integer,List<JSONObject>> result2 = ns.fetchElements(2);

        assertEquals(2, (int) result2.getKey());
        assertEquals(0, result2.getValue().size());
    }

    @Test
    public void testCorruptedIndex() throws Exception {

        Pair<Integer, List<JSONObject>> result = ns.fetchElements(20);

        assertEquals(2, (int) result.getKey());
        assertEquals(0, result.getValue().size());
    }

    @Test
    public void testClear() throws Exception {

        ns.clear();

        Pair<Integer, List<JSONObject>> result = ns.fetchElements(0);

        assertEquals(0, (int) result.getKey());
        assertEquals(0, result.getValue().size());
    }
}

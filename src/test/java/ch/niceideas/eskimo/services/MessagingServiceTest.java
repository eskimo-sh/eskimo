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
import ch.niceideas.common.utils.StringUtils;
import junit.framework.TestCase;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class MessagingServiceTest {

    private MessagingService ms = null;

    @Before
    public void setUp() throws Exception {
        ms = new MessagingService();
        ms.addLines("Test");
        ms.addLines(new String[] {"Test1", "Test2"});
    }

    @Test
    public void testNominal() throws Exception {

        Pair<Integer,String> result = ms.fetchElements(0);

        assertEquals(3, (int) result.getKey());
        assertEquals("Test\nTest1\nTest2\n", result.getValue());

        Pair<Integer,String> result2 = ms.fetchElements(3);

        assertEquals(3, (int) result2.getKey());
        assertEquals("", result2.getValue());

        Pair<Integer,String> result3 = ms.fetchElements(3);

        assertEquals(3, (int) result3.getKey());
        assertEquals("", result3.getValue());

    }

    @Test
    public void testCorruptedIndex() throws Exception {

        Pair<Integer,String> result = ms.fetchElements(20);

        assertEquals(3, (int) result.getKey());
        assertEquals("Test\nTest1\nTest2\n", result.getValue());
    }

    /*
    @Test
    public void testFetchLastMessagesBack() throws Exception {
        fail ("To Be Implemented");
    }
    */

    @Test
    public void testClear() throws Exception {

        ms.clear();

        Pair<Integer,String> result = ms.fetchElements(0);

        assertEquals(0, (int) result.getKey());
        assertEquals("", result.getValue());
    }

    @Test
    public void testMultipleUsers() throws Exception {

        ms.clear();

        int lastLineUser1 = 0;
        int lastLineUser2 = 0;

        ms.addLines(new String[] {"1", "2", "3"});

        Pair<Integer,String> result1_1 = ms.fetchElements(lastLineUser1);

        assertEquals(3, (int) result1_1.getKey());
        assertEquals("1\n2\n3\n", result1_1.getValue());
        lastLineUser1 = result1_1.getKey();

        ms.addLines(new String[] {"4", "5", "6"});

        Pair<Integer,String> result1_2 = ms.fetchElements(lastLineUser1);

        assertEquals(6, (int) result1_2.getKey());
        assertEquals("4\n5\n6\n", result1_2.getValue());
        lastLineUser1 = result1_2.getKey();

        Pair<Integer,String> result2_1 = ms.fetchElements(lastLineUser2);

        assertEquals(6, (int) result2_1.getKey());
        assertEquals("1\n2\n3\n4\n5\n6\n", result2_1.getValue());
        lastLineUser2 = result2_1.getKey();

        // first user clears
        ms.clear();
        lastLineUser1 = 0;

        ms.addLines(new String[] {"A", "B", "C"});

        Pair<Integer,String> result1_3 = ms.fetchElements(lastLineUser1);

        assertEquals(3, (int) result1_3.getKey());
        assertEquals("A\nB\nC\n", result1_3.getValue());
        lastLineUser1 = result1_3.getKey();

        Pair<Integer,String> result2_2 = ms.fetchElements(lastLineUser2);

        assertEquals(3, (int) result2_2.getKey());
        assertEquals("A\nB\nC\n", result2_2.getValue());
        lastLineUser2 = result2_2.getKey();

        // server is restarted
        ms = new MessagingService();

        Pair<Integer,String> result1_4 = ms.fetchElements(lastLineUser1);

        assertEquals(0, (int) result1_4.getKey());
        assertEquals("", result1_4.getValue());
        lastLineUser1 = result1_4.getKey();

        Pair<Integer,String> result2_3 = ms.fetchElements(lastLineUser2);

        assertEquals(0, (int) result2_3.getKey());
        assertEquals("", result2_3.getValue());
        lastLineUser2 = result2_3.getKey();
    }

    @Test
    public void testCycling() throws Exception {

        testNominal();

        for (int i = 0; i < 100000; i++) {
            ms.addLines("Test__"+i);
        }

        Pair<Integer,String> result2 = ms.fetchElements(2);

        assertEquals(100003, (int) result2.getKey());

        Pair<Integer,String> result3 = ms.fetchElements(100003);

        assertEquals(100003, (int) result3.getKey());
        assertTrue(StringUtils.isBlank(result3.getValue()));

        ms.addLines("TestX");
        ms.addLines("TestY");

        Pair<Integer,String> result4 = ms.fetchElements(100003);

        assertEquals(100005, (int) result4.getKey());
        assertEquals(2, result4.getValue().split("\n").length);

        Pair<Integer,String> result5 = ms.fetchElements(100005);

        assertEquals(100005, (int) result5.getKey());
        assertTrue(StringUtils.isBlank(result5.getValue()));
    }

}

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


package ch.niceideas.common.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;


public class FixedSizeListTest {

    private FixedSizeList<String> testList = null;

    @BeforeEach
    public void setUp() throws Exception {
        testList = new FixedSizeList<>(10);
    }

    @Test
    public void testNominal() throws Exception {

        testList.add("0");
        testList.add("1");
        testList.add("2");
        testList.add("3");
        testList.add("4");
        testList.add("5");
        testList.add("6");
        testList.add("7");
        testList.add("8");
        testList.add("9");
        testList.add("10");

        // should have remove first element
        assertEquals("10", testList.get(9));
        assertEquals("1", testList.get(0));
    }

    @Test
    public void testAddAll() {
        testList.addAll(new ArrayList<>(){{
            add("0");
            add("1");
            add("2");
            add("3");
            add("4");
            add("5");
            add("6");
            add("7");
            add("8");
            add("9");
            add("10");
        }});

        // should have remove first element
        assertEquals("10", testList.get(9));
        assertEquals("1", testList.get(0));
    }

    @Test
    public void testEqualsTestList() {
        List<String> oneList = new ArrayList<>(){{
            add("0");
            add("1");
            add("2");
            add("3");
            add("4");
            add("5");
            add("6");
            add("7");
            add("8");
            add("9");
            add("10");
        }};

        testList.addAll(oneList);

        FixedSizeList<String> testList2  = new FixedSizeList<>(10);
        testList2.addAll(oneList);

        assertEquals(testList2, testList);
    }

    @Test
    public void testAddWithIndex() {

        testList.add(0, "10");
        testList.add(0, "9");
        testList.add(0, "8");
        testList.add(0, "7");
        testList.add(0, "6");
        testList.add(0, "5");
        testList.add(0, "4");
        testList.add(0, "3");
        testList.add(0, "2");
        testList.add(0, "1");
        testList.add(0, "0");

        // should have remove first element
        assertEquals("10", testList.get(9));
        assertEquals("1", testList.get(0));
    }

    @Test
    public void testEquals() {
        List<String> oneList = new ArrayList<>(){{
            add("0");
            add("1");
            add("2");
            add("3");
            add("4");
            add("5");
            add("6");
            add("7");
            add("8");
            add("9");
            add("10");
        }};

        testList.addAll(oneList);
        oneList.remove("0");

        assertEquals(oneList, testList);
    }

    @Test
    public void testToArray() {
        List<String> oneList = new ArrayList<>(){{
            add("0");
            add("1");
            add("2");
            add("3");
            add("4");
            add("5");
            add("6");
            add("7");
            add("8");
            add("9");
            add("10");
        }};

        testList.addAll(oneList);
        oneList.remove("0");

        assertArrayEquals(oneList.toArray(), testList.toArray());
    }

    @Test
    public void testMaxSize() {

        for (int i  = 0; i < 100; i++) {
            testList.add(""+ThreadLocalRandom.current().nextInt(100000000));
        }

        assertEquals(10, testList.size());
    }

    @Test
    public void testRemoveAll() throws Exception {
        testNominal();
        testList.removeAll(Arrays.asList("5", "6", "7", "8"));
        assertEquals ("1,2,3,4,9,10", String.join(",", testList));
    }

    @Test
    public void testRetainAll() throws Exception {
        testNominal();
        testList.retainAll(Arrays.asList("5", "6", "7", "8"));
        assertEquals ("5,6,7,8", String.join(",", Arrays.asList (testList.toArray(new String[0]))));
    }

    @Test
    public void testClear() throws Exception {
        testNominal();
        assertEquals(10, testList.size());
        testList.clear();
        assertEquals(0, testList.size());
    }

    @Test
    public void testEqualsHashCode() {
        FixedSizeList<String> testList2 = new FixedSizeList<>(10);

        testList.add("0");
        testList.add("1");
        testList.add("2");
        testList.add("3");

        testList2.add("0");
        testList2.add("1");
        testList2.add("2");
        testList2.add("3");

        assertEquals(testList2, testList);
        assertEquals(testList2.hashCode(), testList.hashCode());

        testList2.remove("3");

        assertNotSame(testList2, testList);
        assertNotSame(testList2.hashCode(), testList.hashCode());
    }
}

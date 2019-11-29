package ch.niceideas.common.utils;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotSame;

public class FixedSizeListTest {

    private FixedSizeList<String> testList = null;

    @Before
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
    public void testMaxSize() throws Exception {

        for (int i  = 0; i < 100; i++) {
            testList.add(""+(Math.random()*100000000));
        }

        assertEquals(10, testList.size());
    }

    @Test
    public void testRemoveAll() throws Exception {
        testNominal();
        testList.removeAll(Arrays.asList(new String[] {"5", "6", "7", "8"}));
        assertEquals ("1,2,3,4,9,10", String.join(",", testList));
    }

    @Test
    public void testRetainAll() throws Exception {
        testNominal();
        testList.retainAll(Arrays.asList(new String[] {"5", "6", "7", "8"}));
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
    public void testEqualsHashCode() throws Exception {
        FixedSizeList testList2 = new FixedSizeList<>(10);

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

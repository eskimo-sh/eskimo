package ch.niceideas.common.utils;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

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
}

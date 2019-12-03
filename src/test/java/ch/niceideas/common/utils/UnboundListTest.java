package ch.niceideas.common.utils;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class UnboundListTest {

    private UnboundList<String> testList = null;

    @Before
    public void setUp() throws Exception {
        testList = new UnboundList<>(10);
    }

    @Test
    public void testNominalAddAndGet() throws Exception {

        addElementsFirst();

        assertEquals("0", testList.get(0));
        assertEquals("4", testList.get(4));

        addElementsSecond();

        // should have cycle
        assertEquals("10", testList.get(10));

        // this one generates a warning
        assertEquals("1", testList.get(0));

        // this one is fine
        assertEquals("1", testList.get(1));

        testList.add("11");
        testList.add("12");
        testList.add("13");

        assertEquals("13", testList.get(13));

        assertEquals("12", testList.get(12));

        assertEquals("10", testList.get(10));

        // previous are gone, first element servable is now 4
        assertEquals("4", testList.get(0));
    }

    void addElementsFirst() {
        testList.add("0");
        testList.add("1");
        testList.add("2");
        testList.add("3");
        testList.add("4");
    }

    void addElementsSecond() {
        testList.add("5");
        testList.add("6");
        testList.add("7");
        testList.add("8");
        testList.add("9");
        testList.add("10");
    }

    @Test
    public void testGetIndexOutOfBounds() throws Exception {
        addElementsFirst();

        testList.get(4);
        IndexOutOfBoundsException exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            testList.get(5);
        });
        assertEquals("Index: 5, Size: 5", exception.getMessage());

        addElementsSecond();
        testList.get(10);
        exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            testList.get(11);
        });
        assertEquals("11 is beyond last element index 10", exception.getMessage());
    }

    @Test
    public void testSubList() throws Exception {
        addElementsFirst();

        // test few cases
        assertEquals("0,1", String.join(",", testList.subList(0, 2)));
        assertEquals("0,1,2,3", String.join(",", testList.subList(0, 4)));

        IndexOutOfBoundsException exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            testList.subList(4, 6);
        });
        assertEquals("toIndex = 6", exception.getMessage());

        addElementsSecond();

        // test few cases
        assertEquals("10", String.join(",", testList.subList(10, 11)));
        assertEquals("5,6,7", String.join(",", testList.subList(5, 8)));
        assertEquals("1", String.join(",", testList.subList(0, 2)));
        assertEquals("1,2,3,4", String.join(",", testList.subList(0, 5)));

        exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            testList.subList(-1, 2);
        });
        assertEquals("fromIndex = -1", exception.getMessage());

        exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            testList.subList(10, 12);
        });
        assertEquals("toIndex = 12", exception.getMessage());

        exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            testList.subList(11, 15);
        });
        assertEquals("toIndex = 15", exception.getMessage());

        // add some more elements
        testList.add("11");
        testList.add("12");
        testList.add("13");

        // test few cases
        assertEquals("10", String.join(",", testList.subList(10, 11)));
        assertEquals("5,6,7", String.join(",", testList.subList(5, 8)));

        assertEquals("13", String.join(",", testList.subList(13, 14)));
        assertEquals("11,12,13", String.join(",", testList.subList(11, 14)));

        assertEquals("4", String.join(",", testList.subList(0, 5)));
    }

    @Test
    public void testSize() throws Exception {
        addElementsFirst();

        assertEquals(5, testList.size());

        addElementsSecond();
        assertEquals(11, testList.size());

        // add some more elements
        testList.add("11");
        testList.add("12");
        testList.add("13");

        assertEquals(14, testList.size());

        for (int i = 0; i < 100; i++) {
            testList.add("new" + 1);
        }

        assertEquals(114, testList.size());
    }

    @Test
    public void testIsEmptyAndClear() throws Exception {
        testSize();
        assertEquals(114, testList.size());

        assertFalse(testList.isEmpty());

        testList.clear();
        assertEquals(0, testList.size());
        assertTrue(testList.isEmpty());

        IndexOutOfBoundsException exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            testList.get(0);
        });
        assertEquals("Index: 0, Size: 0", exception.getMessage());

        exception = assertThrows(IndexOutOfBoundsException.class, () -> {
            testList.subList(0, 1);
        });
        assertEquals("toIndex = 1", exception.getMessage());
    }

    @Test
    public void testContains() throws Exception {
        addElementsFirst();

        assertTrue(testList.contains("0"));
        assertTrue(testList.contains("4"));
        assertFalse(testList.contains("5"));

        addElementsSecond();

        assertFalse(testList.contains("0"));

        assertTrue(testList.contains("1"));
        assertTrue(testList.contains("10"));

        // add some more elements
        testList.add("11");
        testList.add("12");
        testList.add("13");

        assertFalse(testList.contains("3"));

        assertTrue(testList.contains("4"));
        assertTrue(testList.contains("10"));

        assertTrue(testList.contains("13"));

    }

    @Test
    public void testRemove() throws Exception {
        addElementsFirst();

        addElementsSecond();

        assertTrue(testList.contains("4"));
        assertTrue(testList.contains("5"));
        assertTrue(testList.contains("6"));
        assertEquals(11, testList.size());

        testList.remove("5");

        assertTrue(testList.contains("4"));
        assertFalse(testList.contains("5"));
        assertTrue(testList.contains("6"));
        assertEquals(10, testList.size());

        assertEquals("3,4,6,7", String.join(",", testList.subList(2, 6)));

    }

    @Test
    public void testAddAll() throws Exception {
        addElementsFirst();

        addElementsSecond();

        assertEquals(11, testList.size());

        //System.err.println (String.join(",", testList));
        testList.addAll(Arrays.asList(new String[] {"11", "12", "13"}));
        //System.err.println (String.join(",", testList));

        assertFalse(testList.contains("3"));

        assertTrue(testList.contains("4"));
        assertTrue(testList.contains("10"));

        assertTrue(testList.contains("13"));

        assertEquals(14, testList.size());
    }

    @Test
    public void testIteratot() throws Exception {
        addElementsFirst();
        addElementsSecond();
        assertEquals(11, testList.size());

        assertEquals("1,2,3,4,5,6,7,8,9,10", String.join(",", testList));
        testList.addAll(Arrays.asList(new String[] {"11", "12", "13"}));
        assertEquals ("4,5,6,7,8,9,10,11,12,13", String.join(",", testList));
    }
}

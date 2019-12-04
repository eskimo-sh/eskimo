package ch.niceideas.common.utils;

import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotSame;

public class PairTest {

    @Test
    public void testNominal() throws Exception {

        Pair<String, Integer> pair1 = new Pair<>("A", 1);
        Pair<String, Integer> pair2 = new Pair<>("A", 1);

        Pair<String, Integer> diff1 = new Pair<>("B", 1);
        Pair<String, Integer> diff2 = new Pair<>("A", 2);

        assertEquals(pair1, pair2);

        assertNotSame(pair1, diff1);
        assertNotSame(pair1, diff2);

        assertEquals(pair1.hashCode(), pair2.hashCode());

        assertNotSame(pair1.hashCode(), diff1.hashCode());
        assertNotSame(pair1.hashCode(), diff2.hashCode());
    }

}

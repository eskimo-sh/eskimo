package ch.niceideas.eskimo.utils;

import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class ErrorStatusHelperTest {

    @Test
    public void testCreateErrorStatus() {
        assertEquals("{\n" +
                "  \"error\": \"test\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", ErrorStatusHelper.createErrorStatus("test"));
    }

    @Test
    public void testCreateEncodedErrorStatus() {
        assertEquals("{\n" +
                "  \"error\": \"dGVzdA==\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", ErrorStatusHelper.createEncodedErrorStatus(new RuntimeException("test")));
    }

    @Test
    public void testCreateClearStatus() {
        assertEquals("{\n" +
                "  \"clear\": \"test\",\n" +
                "  \"processingPending\": true,\n" +
                "  \"status\": \"OK\"\n" +
                "}", ErrorStatusHelper.createClearStatus("test", true));
    }

    @Test
    public void testCreateClearStatusWithMessage() {
        assertEquals("{\n" +
                "  \"clear\": \"test\",\n" +
                "  \"message\": \"test-message\",\n" +
                "  \"processingPending\": true,\n" +
                "  \"status\": \"OK\"\n" +
                "}", ErrorStatusHelper.createClearStatusWithMessage("test", true, "test-message"));
    }
}

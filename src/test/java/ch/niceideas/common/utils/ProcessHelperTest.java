package ch.niceideas.common.utils;


import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProcessHelperTest {

    /** Run Test on Linux only */
    @BeforeEach
    public void beforeMethod() {
        Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    @Test
    public void testExecSimple() {

        String result = ProcessHelper.exec("ls /etc");
        assertTrue(result.contains("passwd"));

        result = ProcessHelper.exec("ls /zuasdzugzugsuzdg");
        assertTrue (result.contains("No such file or directory"));
    }

    @Test
    public void testExecSimpleWithExceptions() throws Exception {

        String result = ProcessHelper.exec("ls /etc", true);
        assertTrue(result.contains("passwd"));

        ProcessHelper.ProcessHelperException exception = assertThrows(ProcessHelper.ProcessHelperException.class,
                () -> ProcessHelper.exec("ls /zuasdzugzugsuzdg", true));

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("No such file or directory"));
    }

    @Test
    public void testExecArrayWithExceptions() throws Exception {

        String result = ProcessHelper.exec(new String[] {"ls", "/etc"}, true);
        assertTrue(result.contains("passwd"));

        ProcessHelper.ProcessHelperException exception = assertThrows(ProcessHelper.ProcessHelperException.class,
                () -> ProcessHelper.exec(new String[] {"ls", "/zuasdzugzugsuzdg"}, true));

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("No such file or directory"));
    }
}

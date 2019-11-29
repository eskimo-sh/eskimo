package ch.niceideas.common.utils;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertThrows;

public class ProcessHelperTest {


    /** Run Test on Linux only */
    @Before
    public void beforeMethod() {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    @Test
    public void testExecSimple() throws Exception {

        String result = ProcessHelper.exec("ls /etc");
        assertTrue(result.contains("passwd"));

        result = ProcessHelper.exec("ls /zuasdzugzugsuzdg");
        assertTrue (result.contains("No such file or directory"));
    }

    @Test
    public void testExecSimpleWithExceptions() throws Exception {

        String result = ProcessHelper.exec("ls /etc", true);
        assertTrue(result.contains("passwd"));

        ProcessHelper.ProcessHelperException exception = assertThrows(ProcessHelper.ProcessHelperException.class, () -> {
            ProcessHelper.exec("ls /zuasdzugzugsuzdg", true);
        });

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("No such file or directory"));
    }

    @Test
    public void testExecArrayWithExceptions() throws Exception {

        String result = ProcessHelper.exec(new String[] {"ls", "/etc"}, true);
        assertTrue(result.contains("passwd"));

        ProcessHelper.ProcessHelperException exception = assertThrows(ProcessHelper.ProcessHelperException.class, () -> {
            ProcessHelper.exec(new String[] {"ls", "/zuasdzugzugsuzdg"}, true);
        });

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("No such file or directory"));
    }
}

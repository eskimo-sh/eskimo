package ch.niceideas.common.utils;

import ch.niceideas.eskimo.utils.OSDetector;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

public class ResourceUtilsTest {

    @Test
    public void testIsUrl() throws Exception {
        assertTrue(ResourceUtils.isUrl("http://www.eskimo.sh"));
        assertTrue(ResourceUtils.isUrl("https://www.eskimo.sh"));
        assertTrue(ResourceUtils.isUrl("file:///etc/passwd"));
        assertTrue(ResourceUtils.isUrl("classpath:application.properties"));

        assertFalse(ResourceUtils.isUrl("resource.properties"));
        assertFalse(ResourceUtils.isUrl("abc"));
        assertFalse(ResourceUtils.isUrl(null));
    }

    @Test
    public void testGetURL() throws Exception {
        assertNotNull(ResourceUtils.getURL("http://www.eskimo.sh"));
        assertNotNull(ResourceUtils.getURL("https://www.eskimo.sh"));
        assertNotNull(ResourceUtils.getURL("file:///etc/passwd"));
        assertNotNull(ResourceUtils.getURL("classpath:application.properties"));

        FileNotFoundException exception = assertThrows(FileNotFoundException.class,
                () -> ResourceUtils.getURL("classpath:testInexistent.properties"));
        assertNotNull(exception);
    }

    @Test
    public void testGetFile() throws Exception {
        assertNotNull(ResourceUtils.getFile("classpath:application.properties"));
        assertTrue(ResourceUtils.getFile("classpath:application.properties").exists());

        if (OSDetector.isUnix()) {

            assertNotNull(ResourceUtils.getFile("file:///etc/passwd"));
            assertTrue(ResourceUtils.getFile("file:///etc/passwd").exists());

            assertNotNull(ResourceUtils.getFile("/etc/passwd"));
            assertTrue(ResourceUtils.getFile("/etc/passwd").exists());

            assertNotNull(ResourceUtils.getFile("/etc/nonexistent"));
            assertFalse(ResourceUtils.getFile("/etc/nonexistent").exists());

        }
    }
}

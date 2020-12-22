package ch.niceideas.common.utils;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class FileUtilsTest {

    @Test
    public void testDeleteFile() throws Exception {

        File fileToDelete = File.createTempFile("test", "delete");
        FileUtils.writeFile(fileToDelete, "content");

        assertTrue (fileToDelete.exists());
        FileUtils.delete(fileToDelete);
        assertFalse (fileToDelete.exists());

        fileToDelete.mkdirs();
        assertTrue(fileToDelete.exists());

        File ftd1 = File.createTempFile("test", "delete", fileToDelete);
        FileUtils.writeFile(ftd1, "content");
        assertTrue(ftd1.exists());

        File ftd2 = File.createTempFile("test", "delete", fileToDelete);
        FileUtils.writeFile(ftd2, "content");
        assertTrue(ftd2.exists());

        FileUtils.delete(fileToDelete);

        assertFalse(fileToDelete.exists());
        assertFalse(ftd1.exists());
        assertFalse(ftd2.exists());
    }

    @Test
    public void testCopy() throws Exception {

        File file1 = File.createTempFile("test", "source");
        FileUtils.writeFile(file1, "content");

        File file2 = File.createTempFile("test", "dest");
        FileUtils.copy(file1, file2);

        assertTrue (file2.exists());
        assertEquals ("content", FileUtils.readFile(file2));

    }
}

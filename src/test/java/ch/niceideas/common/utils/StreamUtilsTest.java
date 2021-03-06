package ch.niceideas.common.utils;

import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;


public class StreamUtilsTest {

    @Test
    public void testCopyStreamToStream() throws Exception {
        String source = "content";
        ByteArrayInputStream bais = new ByteArrayInputStream(source.getBytes());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamUtils.copy(bais, baos);
        assertEquals(source, baos.toString());

        assertThrows(NullPointerException.class, () -> StreamUtils.copy(null, (OutputStream)null) );
        assertThrows(IOException.class, () -> StreamUtils.copyThenClose(null, null) );
    }

    @Test
    public void testReaderToWriter() throws Exception {
        String source = "content";
        StringReader reader = new StringReader(source);
        StringWriter writer = new StringWriter();
        StreamUtils.copy(reader, writer);
        assertEquals(source, writer.toString());

        assertThrows(IOException.class, () -> StreamUtils.copy((Reader) null, null) );
    }

    @Test
    public void testGetAsString() throws Exception {
        String source = "content";
        ByteArrayInputStream bais = new ByteArrayInputStream(source.getBytes());
        assertEquals(source, StreamUtils.getAsString(bais));

        assertEquals("", StreamUtils.getAsString(null));
    }

    @Test
    public void testClose() {
        try {
            StreamUtils.close(new BufferedReader(new InputStreamReader(new ByteArrayInputStream("aaa".getBytes()))));
            StreamUtils.close(new ByteArrayInputStream("aaa".getBytes()));
            StreamUtils.close((InputStream) null);
            StreamUtils.close((OutputStream) null);
            StreamUtils.close(new ByteArrayOutputStream());
        } catch (Exception e) {
            fail ("No exception expected");
        }
    }
}

package ch.niceideas.common.utils;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;

public class StreamUtilsTest {

    @Test
    public void testCopyStreamToStream() throws Exception {
        String source = "content";
        ByteArrayInputStream bais = new ByteArrayInputStream(source.getBytes());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamUtils.copy(bais, baos);
        assertEquals(source, baos.toString());
    }

    @Test
    public void testReaderToWriter() throws Exception {
        String source = "content";
        StringReader reader = new StringReader(source);
        StringWriter writer = new StringWriter();
        StreamUtils.copy(reader, writer);
        assertEquals(source, writer.toString());
    }

    @Test
    public void testGetAsString() throws Exception {
        String source = "content";
        ByteArrayInputStream bais = new ByteArrayInputStream(source.getBytes());
        assertEquals(source, StreamUtils.getAsString(bais));
    }
}

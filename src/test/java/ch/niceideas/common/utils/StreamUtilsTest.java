/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */


package ch.niceideas.common.utils;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;


public class StreamUtilsTest {

    @Test
    public void testCopyStreamToStream() throws Exception {
        String source = "content";
        ByteArrayInputStream bais = new ByteArrayInputStream(source.getBytes());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamUtils.copy(bais, baos);
        assertEquals(source, baos.toString());

        assertThrows(NullPointerException.class, () -> StreamUtils.copy(null, (OutputStream) null));
    }

    @Test
    public void testCopyThenClose() throws Exception {

        AtomicBoolean baisClosedCall = new AtomicBoolean();
        AtomicBoolean baosClosedCall = new AtomicBoolean();

        String source = "content";
        ByteArrayInputStream bais = new ByteArrayInputStream(source.getBytes()){
            @Override
            public void close() throws IOException {
                baisClosedCall.set(true);
                super.close();
            }
        };
        ByteArrayOutputStream baos = new ByteArrayOutputStream(){
            @Override
            public void close() throws IOException {
                baosClosedCall.set(true);
                super.close();
            }
        };
        StreamUtils.copyThenClose(bais, baos);
        assertEquals(source, baos.toString());

        assertTrue (baisClosedCall.get());
        assertTrue (baosClosedCall.get());

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
        boolean success = false;
        try {
            StreamUtils.close((BufferedReader) null);
            StreamUtils.close(new BufferedReader(new InputStreamReader(new ByteArrayInputStream("aaa".getBytes()))));
            StreamUtils.close(new BufferedReader(new InputStreamReader(new ByteArrayInputStream("aaa".getBytes()))){
                @Override
                public void close() throws IOException {
                    throw new IOException("test");
                }
            });

            StreamUtils.close((InputStream) null);
            StreamUtils.close(new ByteArrayInputStream("aaa".getBytes()));
            StreamUtils.close(new ByteArrayInputStream("aaa".getBytes()){
                @Override
                public void close() throws IOException {
                    throw new IOException("test");
                }
            });

            StreamUtils.close((OutputStream) null);
            StreamUtils.close(new ByteArrayOutputStream());
            StreamUtils.close(new ByteArrayOutputStream(){
                @Override
                public void close() throws IOException {
                    throw new IOException("test");
                }
            });

            StreamUtils.close((BufferedWriter) null);
            StreamUtils.close(new BufferedWriter(new OutputStreamWriter(new ByteArrayOutputStream())));
            StreamUtils.close(new BufferedWriter(new OutputStreamWriter(new ByteArrayOutputStream())){
                @Override
                public void close() throws IOException {
                    throw new IOException("test");
                }
            });

            success = true;
        } finally {
            assertTrue (success);
        }
    }
}

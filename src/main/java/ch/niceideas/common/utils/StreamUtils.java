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

import org.apache.log4j.Logger;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Optional;

/**
 * Various utility methods aimed at helping with stream handling.
 */
public abstract class StreamUtils {

    private static final Logger logger = Logger.getLogger(StreamUtils.class);

    /**
     * Default value is 2048.
     */
    public static final int DEFAULT_BUFFER_SIZE = 2048;

    private StreamUtils() {}

    /**
     * Copies information from the input stream to the output stream using the specified buffer size
     */
    public static void copy(InputStream input, OutputStream output) throws IOException {
        Optional.ofNullable(input)
                .orElseThrow(() -> new NullPointerException("Input is null"))
                .transferTo(Optional.ofNullable(output)
                        .orElseThrow(() -> new NullPointerException("Output is null")));
    }

    /**
     * Copy chars from a <code>Reader</code> to a <code>Writer</code>.
     * 
     * @param input the <code>Reader</code> to read from
     * @param output the <code>Writer</code> to write to
     * @return the number of characters copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException if an I/O error occurs
     */
    public static long copy(Reader input, Writer output) throws IOException {
        if (input == null) {
            throw new IOException ("Passed reader is null");
        }
        long count = 0;
        if (output != null) {
            char[] buffer = new char[DEFAULT_BUFFER_SIZE];
            int n;
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
                count += n;
            }
        }
        return count;
    }

    /**
     * Copy chars from a <code>InputStream</code> to a <code>Writer</code>.
     */
    public static long copy(InputStream is, Writer output) throws IOException {
        return copy(new InputStreamReader(is), output);
    }

    /**
     * Copies information between specified streams and then closes both of the streams.
     */
    public static void copyThenClose(InputStream input, OutputStream output) throws IOException {
        if (input == null) {
            throw new IOException ("Passed input stream is null");
        }
        if (output == null) {
            throw new IOException ("Passed output stream is null");
        }
        copy(input, output);
        input.close();
        output.close();
    }

    /**
     * @return a byte[] containing the information contained in the specified InputStream.
     */
    public static byte[] getBytes(InputStream input) throws IOException {
        if (input == null) {
            return null;
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();        
        copy(input, result);
        result.close();
        return result.toByteArray();
    }

    /**
     * Get the Input stream as a String using the given encoding
     * 
     * @param input the InputStream
     * @param encoding the stream encoding
     * @return the input stream as a string
     */
    public static String getAsString(InputStream input, Charset encoding) throws IOException {

        if (input == null) {
            return "";
        }

        try {
            return new String (getBytes(input), encoding);
        } catch (IOException e) {
            logger.error("getAsString", e);
            throw (e);
        } finally {
            FileUtils.close(input, null);
        }
    }

    /**
     * Get the Input stream as a String
     * 
     * @param input the InputStream
     * @return the input stream as a string
     */
    public static String getAsString(InputStream input) throws IOException {
        return getAsString(input, Charset.defaultCharset());
    }

    /**
     * Close the <code> in </code> stream and ignored any exception.
     * 
     * @param in may be null (ignored)
     */
    public static void close(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (Exception ex) {
                logger.debug (ex, ex);
            }
        }
    }

    /**
     * Close the <code> out </code> stream and ignored any exception.
     * 
     * @param out may be null (ignored)
     */
    public static void close(OutputStream out) {
        if (out != null) {
            try {
                out.close();
            } catch (Exception ex) {
                logger.debug (ex, ex);
            }
        }
    }

    /**
     * Close the <code> in </code> reader and ignored any exception.
     * 
     * @param in may be null (ignored)
     */
    public static void close(Reader in) {
        if (in != null) {
            try {
                in.close();
            } catch (Exception ex) {
                logger.debug (ex, ex);
            }
        }
    }

    /**
     * Close the <code> out </code> writer and ignored any exception.
     * 
     * @param out may be null (ignored)
     */
    public static void close(Writer out) {
        if (out != null) {
            try {
                out.close();
            } catch (Exception ex) {
                logger.debug (ex, ex);
            }
        }
    }

}

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

import ch.niceideas.eskimo.utils.OSDetector;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

public class ResourceUtilsTest {

    @Test
    public void testIsUrl() {
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

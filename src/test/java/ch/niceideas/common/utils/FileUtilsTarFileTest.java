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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class FileUtilsTarFileTest {

    /** Run Test on Linux only */
    @BeforeEach
    public void beforeMethod() {
        Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    @Test
    public void testNominal() throws Exception {

        File tempFolder = File.createTempFile("eskimo-file-utils-tar-test", "folder");
        FileUtils.delete(tempFolder);
        assertTrue (tempFolder.mkdirs());

        FileUtils.writeFile(new File (tempFolder, "file1"), "file1");
        FileUtils.writeFile(new File (tempFolder, "file2"), "file2");

        File archive = File.createTempFile("eskimo-file-utils-tar-test", "archive");
        FileUtils.delete( archive);

        FileUtils.createTarFile(tempFolder.getAbsolutePath(), archive);

        File tempDest = File.createTempFile("eskimo-file-utils-tar-test", "dest");
        FileUtils.delete (tempDest);
        assertTrue (tempDest.mkdirs());

        ProcessHelper.exec("/usr/bin/tar xvfz " + archive.getAbsolutePath() + " -C " + tempDest.getAbsolutePath(), true);

        assertNotNull(tempDest.listFiles());
        assertEquals (1, Objects.requireNonNull(tempDest.listFiles()).length);

        assertNotNull(Objects.requireNonNull(tempDest.listFiles())[0].listFiles());

        File[] archiveFiles = Objects.requireNonNull(Objects.requireNonNull(tempDest.listFiles())[0].listFiles());
        assertEquals (2, archiveFiles.length);

        boolean file1Found = false;
        boolean file2Found = false;
        for (File file : archiveFiles) {
            if (file.getName().equals("file1")) {
                file1Found = true;
                assertEquals ("file1", FileUtils.readFile(file));
            }
            if (file.getName().equals("file2")) {
                file2Found = true;
                assertEquals ("file2", FileUtils.readFile(file));
            }
        }

        assertTrue (file1Found);
        assertTrue (file2Found);
    }
}

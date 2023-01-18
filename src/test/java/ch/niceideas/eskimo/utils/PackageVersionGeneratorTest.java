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


package ch.niceideas.eskimo.utils;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PackageVersionGeneratorTest {

    private File tmpFolder = null;

    @BeforeEach
    public void setUp() throws Exception {
        tmpFolder = File.createTempFile("packagetest", "distrib");
        assertTrue(tmpFolder.delete());
        assertTrue (tmpFolder.mkdir());

        assertTrue (new File (tmpFolder, "docker_template_base-eskimo_0.2_1.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "docker_template_base-eskimo_0.2_2.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "docker_template_base-eskimo_0.2_3.tar.gz").createNewFile());

        assertTrue (new File (tmpFolder, "docker_template_flink_1.9.1_1.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "docker_template_flink_1.10.0_1.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "docker_template_flink_1.10.1_1.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "docker_template_flink_1.10.1_2.tar.gz").createNewFile());

        assertTrue (new File (tmpFolder, "docker_template_kibana_7.6.2_1.tar.gz").createNewFile());

        assertTrue (new File (tmpFolder, "docker_template_spark_2.4.5_2.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "docker_template_spark_2.4.6_2.tar.gz").createNewFile());

        assertTrue (new File (tmpFolder, "eskimo_mesos-debian_1.8.1_1.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "eskimo_mesos-debian_1.8.1_2.tar.gz").createNewFile());

        assertTrue (new File (tmpFolder, "eskimo_mesos-suse_1.8.1_1.tar.gz").createNewFile());
    }

    @AfterEach
    public void tearDown() throws Exception {
        FileUtils.delete(tmpFolder);
    }

    @Test
    public void testPackageGeneration() throws Exception {
        new PackageVersionGenerator().generatePackageVersionFile(tmpFolder.getAbsolutePath());
        checkGeneratedFile();
    }

    void checkGeneratedFile() throws FileException {
        String generatedPackageFile = FileUtils.readFile(new File(tmpFolder, "eskimo_packages_versions.json"));
        assertEquals("{\n" +
                "    \"flink\": {\n" +
                "        \"software\": \"1.10.1\",\n" +
                "        \"distribution\": \"2\"\n" +
                "    },\n" +
                "    \"spark\": {\n" +
                "        \"software\": \"2.4.6\",\n" +
                "        \"distribution\": \"2\"\n" +
                "    },\n" +
                "    \"base-eskimo\": {\n" +
                "        \"software\": \"0.2\",\n" +
                "        \"distribution\": \"3\"\n" +
                "    },\n" +
                "    \"mesos-debian\": {\n" +
                "        \"software\": \"1.8.1\",\n" +
                "        \"distribution\": \"2\"\n" +
                "    },\n" +
                "    \"kibana\": {\n" +
                "        \"software\": \"7.6.2\",\n" +
                "        \"distribution\": \"1\"\n" +
                "    },\n" +
                "    \"mesos-suse\": {\n" +
                "        \"software\": \"1.8.1\",\n" +
                "        \"distribution\": \"1\"\n" +
                "    }\n" +
                "}", generatedPackageFile);
    }

    @Test
    public void testPackageGenerationFromMain() throws Exception {
        PackageVersionGenerator.main(new String[] {tmpFolder.getAbsolutePath()});
        checkGeneratedFile();
    }
}

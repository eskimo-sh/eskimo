/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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


package ch.niceideas.eskimo.shell.dev;

import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.shell.setup.AbstractSetupShellTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class CommonDevShellTest {

    protected String jailPath = null;

    /** Run Test on Linux only */
    @BeforeEach
    public void beforeMethod() {
        Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    @BeforeEach
    public void setUp() throws Exception {
        jailPath = AbstractSetupShellTest.createJail();

        FileUtils.copy(
                new File("./packages_dev/common/common.sh"),
                new File (jailPath + "/common.sh"));

    }

    @AfterEach
    public void tearDownClass() throws Exception {
        if (StringUtils.isNotBlank(jailPath)) {
            FileUtils.delete(new File(jailPath));
        }
    }

    private void createTestScript(String scriptName, String command) throws FileException {

        String script = "#!/bin/bash\n" + "\n" +
                "SCRIPT_DIR=\"$( cd \"$( dirname \"${BASH_SOURCE[0]}\" )\" && pwd )\"\n" +
                "\n" +
                "# Change current folder to script dir (important !)\n" +
                "cd $SCRIPT_DIR\n" +
                "\n" +
                "# Avoid sleeps everywhere\n" +
                "export NO_SLEEP=true\n" +
                "\n" +
                "# Set test mode\n" +
                "export TEST_MODE=true\n" +
                "\n" +
                "# Set no base image load\n" +
                "export NO_BASE_IMAGE=true\n" +
                "\n" +
                "# Using local commands\n" +
                "export PATH=$SCRIPT_DIR:$PATH\n" +
                "\n" +
                ". $SCRIPT_DIR/common.sh\n" +
                "\n" +
                "# Call command\n" +
                command;
        FileUtils.writeFile(new File (jailPath + "/" + scriptName), script);
    }

    @Test
    public void testCheckForInternet() throws Exception {
        createTestScript("check_for_internet.sh", "check_for_internet");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/check_for_internet.sh"}, true);

        // no error reported
        assertEquals ("", result);

        String wgetLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_wget"), StandardCharsets.UTF_8);
        if (StringUtils.isNotBlank(wgetLogs)) {

            //System.err.println (wgetLogs);
            assertEquals("https://www.google.com -O /tmp/test.html\n", wgetLogs);

        } else {
            fail ("No wget manipulations found");
        }
    }

    @Test
    public void testCheckForDocker() throws Exception {
        createTestScript("check_for_docker.sh", "check_for_docker");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/check_for_docker.sh"}, true);

        // no error reported
        assertEquals ("Found docker : \n", result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_docker"), StandardCharsets.UTF_8);
        if (StringUtils.isNotBlank(dockerLogs)) {

            //System.err.println (dockerLogs);
            assertEquals("-v\n", dockerLogs);

        } else {
            fail ("No docker manipulations found");
        }
    }

    @Test
    public void testCloseAndSaveImage() throws Exception {
        createTestScript("close_and_save_image.sh", "close_and_save_image cerebro_template /tmp/cerebro_install.log 1.0");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/close_and_save_image.sh"}, true);

        // no error reported
        assertEquals (" - Running apt-autoremove\n" +
                        " - Cleaning apt cache\n" +
                        " - Cleanup doc and man pages\n" +
                        " - Recreating required folders\n" +
                        " - Comitting changes on container cerebro_template\n" +
                        " - Stopping container cerebro_template\n" +
                        " - removing container cerebro_template\n" +
                        " - Saving image cerebro_template\n" +
                        " - versioning image\n" +
                        " - removing image cerebro_template\n",
                result.replaceAll("/[^\\n]+\\n", "") // remove error
        );

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_docker"), StandardCharsets.UTF_8);
        if (StringUtils.isNotBlank(dockerLogs)) {

            //System.err.println (dockerLogs);
            assertEquals("exec -i cerebro_template apt -y autoremove\n" +
                    "exec -i cerebro_template apt-get clean -q\n" +
                    "exec -i cerebro_template rm -Rf /usr/share/doc/ /usr/share/man /usr/share/doc-base/ /usr/share/info/\n" +
                    "exec -i cerebro_template mkdir -p /usr/share/man/man1/\n" +
                    "commit cerebro_template eskimo:cerebro_template\n" +
                    "stop cerebro_template\n" +
                    "container rm cerebro_template\n" +
                    "save eskimo:cerebro_template\n" +
                    "image rm eskimo:cerebro_template\n", dockerLogs);

        } else {
            fail ("No docker manipulations found");
        }
    }

    @Test
    public void testBuildImage() throws Exception {
        createTestScript("build_image.sh", "build_image cerebro_template /tmp/cerebro_install.log");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/build_image.sh"}, true);

        // no error reported
        assertEquals (" - Deleting any previous containers\n" +
                        " - building docker image cerebro_template\n" +
                        " - Starting container cerebro_template\n" +
                        " - Ensuring image was well started\n",
                result.replaceAll("ls[^\\n]+\\n", "") // remove error
        );

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_docker"), StandardCharsets.UTF_8);
        System.err.println (dockerLogs);
        if (StringUtils.isNotBlank(dockerLogs)) {

            int indexOfPs = dockerLogs.indexOf("ps -a -q -f name=cerebro");
            assertTrue(indexOfPs > -1);

            int indexOfBuild = dockerLogs.indexOf("build --iidfile id_file --tag eskimo:cerebro_template", indexOfPs);
            assertTrue(indexOfBuild > -1);

            int indexOfRun = dockerLogs.indexOf("run --privileged -v", indexOfBuild);
            assertTrue(indexOfRun > -1);

        } else {
            fail ("No docker manipulations found");
        }
    }

    @Test
    public void testCreateBinaryWrapper() throws Exception {
        createTestScript("create_binary_wrapper.sh", "" +
                "create_binary_wrapper /bin/pwd $SCRIPT_DIR/pwd.sh \n" +
                ". $SCRIPT_DIR/pwd.sh;\n"
                );

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/create_binary_wrapper.sh"}, true);

        // no error reported
        assertEquals(jailPath+"\n", result);
    }
}

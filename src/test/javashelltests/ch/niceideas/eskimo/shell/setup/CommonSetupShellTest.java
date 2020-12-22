/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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


package ch.niceideas.eskimo.shell.setup;

import ch.niceideas.common.utils.*;
import org.junit.Assume;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class CommonSetupShellTest {

    protected String jailPath = null;

    /** Run Test on Linux only */
    @BeforeEach
    public void beforeMethod() {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    @BeforeEach
    public void setUp() throws Exception {
        jailPath = AbstractSetupShellTest.createJail();

        FileUtils.copy(
                new File("./services_setup/common/common.sh"),
                new File (jailPath + "/common.sh"));

    }

    @AfterAll
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
    public void testDockerCpScript() throws Exception {
        createTestScript("docker_cp_script.sh", "docker_cp_script /bin/bash bin cerebro /tmp/test.log");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/docker_cp_script.sh"}, true);

        // no error reported
        assertEquals (" - Copying /bin/bash Script to cerebro\n", result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_docker"));
        if (StringUtils.isNotBlank(dockerLogs)) {

            //System.err.println (dockerLogs);

            int indexOfCp = dockerLogs.indexOf("cp ");
            assertTrue(indexOfCp > -1);

            int indexOfExec = dockerLogs.indexOf("exec --user root cerebro bash -c chmod 755 /usr/local/bin//bin/bash", indexOfCp);
            assertTrue(indexOfExec > -1);

        } else {
            fail ("No docker manipulations found");
        }
    }

    @Test
    public void testHandleTopologySettings() throws Exception {
        createTestScript("handle_topology_settings.sh", "handle_topology_settings cerebro /tmp/test.log");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/handle_topology_settings.sh"}, true);

        // no error reported
        assertEquals (" - Copying Topology Injection Script\n" +
                " - Copying Service Start Script\n" +
                " - Copying settingsInjector.sh Script\n", result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_docker"));
        if (StringUtils.isNotBlank(dockerLogs)) {

            //System.err.println (dockerLogs);

            int indexOfCpFirst = dockerLogs.indexOf("cp ");
            assertTrue(indexOfCpFirst > -1);

            int indexOfExecFirst = dockerLogs.indexOf("exec --user root cerebro bash -c chmod 755 /usr/local/sbin/inContainerInjectTopology.sh", indexOfCpFirst);
            assertTrue(indexOfExecFirst > -1);

            int indexOfCpSecond = dockerLogs.indexOf("cp ");
            assertTrue(indexOfCpSecond > -1);

            int indexOfExecSecond = dockerLogs.indexOf("exec --user root cerebro bash -c chmod 755 /usr/local/sbin/inContainerStartService.sh", indexOfCpSecond);
            assertTrue(indexOfExecSecond > -1);

            int indexOfCpThird = dockerLogs.indexOf("cp ");
            assertTrue(indexOfCpThird > -1);

            int indexOfExecThird = dockerLogs.indexOf("exec --user root cerebro bash -c chmod 755 /usr/local/sbin/settingsInjector.sh", indexOfCpThird);
            assertTrue(indexOfExecThird > -1);

        } else {
            fail ("No docker manipulations found");
        }
    }

    @Test
    public void testDeployMarathon() throws Exception {
        createTestScript("deploy_marathon.sh", "deploy_marathon cerebro /tmp/test.log");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/deploy_marathon.sh"}, true);

        // no error reported
        assertEquals (" - Deploying cerebro Service in docker registry for marathon\n" +
                " - removing local image\n" +
                " - Removing any previously deployed cerebro service from marathon\n" +
                " - Deploying cerebro service in marathon\n" +
                "   + Attempt 1\n", result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_docker"));
        if (StringUtils.isNotBlank(dockerLogs)) {

            //System.err.println (dockerLogs);

            int indexOfTag = dockerLogs.indexOf("tag eskimo:cerebro marathon.registry:5000/cerebro");
            assertTrue(indexOfTag > -1);

            int indexOfPush = dockerLogs.indexOf("push marathon.registry:5000/cerebro", indexOfTag);
            assertTrue(indexOfPush > -1);

            int indexOfImage = dockerLogs.indexOf("image rm eskimo:cerebro", indexOfPush);
            assertTrue(indexOfImage > -1);

        } else {
            fail ("No docker manipulations found");
        }

        String curlLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_curl"));
        if (StringUtils.isNotBlank(curlLogs)) {

            //System.err.println (curlLogs);

            int indexOfDelete = curlLogs.indexOf("-XDELETE http://:28080/v2/apps/cerebro");
            assertTrue(indexOfDelete > -1);

            int indexOfPost = curlLogs.indexOf("-X POST -H Content-Type: application/json -d @cerebro.marathon.json http://:28080/v2/apps", indexOfDelete);
            assertTrue(indexOfPost > -1);

        } else {
            fail ("No curl manipulations found");
        }
    }

    @Test
    public void testInstallAndCheckServiceFile() throws Exception {
        createTestScript("install_and_check_service_file.sh", "install_and_check_service_file cerebro /tmp/test.log");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/install_and_check_service_file.sh"}, true);

        // no error reported
        assertEquals (" - Copying cerebro systemd file\n" +
                " - Reloading systemd config\n" +
                " - Checking Systemd file\n" +
                " - Testing systemd startup - starting cerebro\n" +
                " - Testing systemd startup - Checking startup\n" +
                " - Testing systemd startup - Make sure service is really running\n" +
                " - Enabling cerebro on startup\n", result);

        String sudoLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_sudo"));
        if (StringUtils.isNotBlank(sudoLogs)) {

            //System.err.println (sudoLogs);

            int indexOfCp = sudoLogs.indexOf("cp ");
            assertTrue(indexOfCp > -1);

            int indexOfChmod = sudoLogs.indexOf("chmod 644 /lib/systemd/system//cerebro.service", indexOfCp);
            assertTrue(indexOfChmod > -1);

            int indexOfReload = sudoLogs.indexOf("systemctl daemon-reload", indexOfChmod);
            assertTrue(indexOfReload > -1);

            int indexOfStatusFirst = sudoLogs.indexOf("systemctl status cerebro", indexOfReload + 1);
            assertTrue(indexOfStatusFirst > -1);

            int indexOfStatusSecond = sudoLogs.indexOf("systemctl status cerebro", indexOfStatusFirst + 1);
            assertTrue(indexOfStatusSecond > -1);

            int indexOfStart = sudoLogs.indexOf("systemctl start cerebro", indexOfStatusSecond);
            assertTrue(indexOfStart > -1);

            int indexOfStatusThird = sudoLogs.indexOf("systemctl status cerebro", indexOfStart);
            assertTrue(indexOfStatusThird > -1);

            int indexOfEnable = sudoLogs.indexOf("systemctl enable cerebro", indexOfStatusThird);
            assertTrue(indexOfEnable > -1);

        } else {
            fail ("No sudo manipulations found");
        }
    }

    @Test
    public void testCommitContainer() throws Exception {
        createTestScript("commit_container.sh", "commit_container cerebro /tmp/test.log");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/commit_container.sh"}, true);

        // no error reported
        assertEquals (" - Commiting the changes to the local template\n", result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_docker"));
        if (StringUtils.isNotBlank(dockerLogs)) {

            //System.err.println (dockerLogs);

            int indexOfCommit = dockerLogs.indexOf("commit cerebro eskimo:cerebro");
            assertTrue(indexOfCommit > -1);

            int indexOfStop = dockerLogs.indexOf("stop cerebro", indexOfCommit);
            assertTrue(indexOfStop > -1);

            int indexOfRm = dockerLogs.indexOf("container rm cerebro", indexOfStop);
            assertTrue(indexOfRm > -1);

        } else {
            fail ("No docker manipulations found");
        }
    }

    @Test
    public void testBuildContainer() throws Exception {
        createTestScript("build_container.sh", "build_container cerebro cerebro /tmp/test.log");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/build_container.sh"}, true);

        // no error reported
        assertEquals (" - Deleting previous docker template for cerebro if exist\n" +
                " - Importing latest docker template for cerebro\n" +
                " - Killing any previous containers\n" +
                " - Building docker container\n", result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_docker"));
        if (StringUtils.isNotBlank(dockerLogs)) {

            //System.err.println (dockerLogs);

            int indexOfImages = dockerLogs.indexOf("images -q eskimo:cerebro_template");
            assertTrue(indexOfImages > -1);

            int indexOfLoad = dockerLogs.indexOf("load", indexOfImages);
            assertTrue(indexOfLoad > -1);

            int indexOfPs = dockerLogs.indexOf("ps -a -q -f name=cerebro", indexOfLoad);
            assertTrue(indexOfPs > -1);

            int indexOfBuild = dockerLogs.indexOf("build --iidfile id_file --tag eskimo:cerebro .", indexOfPs);
            assertTrue(indexOfBuild > -1);

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

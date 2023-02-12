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


package ch.niceideas.eskimo.shell.base;

import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.shell.setup.AbstractSetupShellTest;
import ch.niceideas.eskimo.utils.OSDetector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class InstallBaseSystemTest {

    protected String jailPath = null;

    /**
     * Run Test on Linux only
     */
    @BeforeEach
    public void beforeMethod() {
        Assumptions.assumeTrue(OSDetector.isPosix());
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (StringUtils.isNotBlank(jailPath)) {
            FileUtils.delete(new File(jailPath));
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        jailPath = AbstractSetupShellTest.createJail();

        FileUtils.copy(
                new File("./services_setup/base-eskimo/install-eskimo-base-system.sh"),
                new File(jailPath + "/install-eskimo-base-system.sh"));

        ProcessHelper.exec(new String[]{"bash", "-c", "chmod 777 " + jailPath + "/install-eskimo-base-system.sh"}, true);

        // I need the real bash
        assertTrue(new File(jailPath + "/bash").delete());
    }

    private void createTestScript(String scriptName) throws FileException {

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
                "# Call command\n" +
                "$SCRIPT_DIR/install-eskimo-base-system.sh";
        FileUtils.writeFile(new File(jailPath + "/" + scriptName), script);
    }


    @Test
    public void testRunScript() throws Exception {

        createTestScript("calling_script.sh");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);

        assertExpectedScriptOutput(result);

        assertExpectedSudoCommands();
    }

    private void assertExpectedSudoCommands() throws Exception {
        String sudoLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_sudo"), StandardCharsets.UTF_8);
        if (StringUtils.isNotBlank(sudoLogs)) {

            int indexOfMkDirEskimo = sudoLogs.indexOf("mkdir -p /var/lib/eskimo");
            assertTrue(indexOfMkDirEskimo > -1);

            int indexOfEnableDocker = sudoLogs.indexOf("systemctl enable docker", indexOfMkDirEskimo);
            assertTrue(indexOfEnableDocker > -1);

            int indexOfStardDocker = sudoLogs.indexOf("systemctl start docker", indexOfEnableDocker);
            assertTrue(indexOfStardDocker > -1);

            int indexOfRestartDocker = sudoLogs.indexOf("systemctl restart docker", indexOfStardDocker);
            assertTrue(indexOfRestartDocker > -1);

            int indexOfMaxMapCount = sudoLogs.indexOf("sysctl -w vm.max_map_count=262144", indexOfRestartDocker);
            assertTrue(indexOfMaxMapCount > -1);

            int indexOfDaemonReload = sudoLogs.indexOf("systemctl daemon-reload", indexOfMaxMapCount);
            assertTrue(indexOfDaemonReload > -1);

            int indexOfStartEskimoChecks = sudoLogs.indexOf("systemctl start eskimo-startup-checks", indexOfDaemonReload);
            assertTrue(indexOfStartEskimoChecks > -1);

            int indexOfEnableEskimoChecks = sudoLogs.indexOf("systemctl enable eskimo-startup-checks", indexOfStartEskimoChecks);
            assertTrue(indexOfEnableEskimoChecks > -1);

        } else {
            fail("Expected Sudo Logs");
        }
    }

    private void assertExpectedScriptOutput(String result) {

        int indexOfCheckSystemd = result.indexOf("checking if systemd is running");
        assertTrue(indexOfCheckSystemd > -1);

        int indexOfLinuxDistro = result.indexOf("Linux distribution is ", indexOfCheckSystemd);
        assertTrue(indexOfLinuxDistro > -1);

        int indexOfUpdateApt = result.indexOf("updating apt package index", indexOfLinuxDistro);
        assertTrue(indexOfUpdateApt > -1);

        int indexOfRequiredDep = result.indexOf("installing some required dependencies", indexOfUpdateApt);
        assertTrue(indexOfRequiredDep > -1);

        int indexOfOtherDep = result.indexOf("Installing other eskimo dependencies", indexOfRequiredDep);
        assertTrue(indexOfOtherDep > -1);

        int indexOfGlusterCli = result.indexOf("Installing gluster client", indexOfOtherDep);
        assertTrue(indexOfGlusterCli > -1);

        int indexOfCheckDocker = result.indexOf("checking if docker is installed", indexOfGlusterCli);
        assertTrue(indexOfCheckDocker > -1);

        int indexOfEnableDocker = result.indexOf("Enabling docker service", indexOfCheckDocker);
        assertTrue(indexOfEnableDocker > -1);

        int indexOfStartDocker = result.indexOf("Starting docker service", indexOfEnableDocker);
        assertTrue(indexOfStartDocker > -1);

        int indexOfAddUserDocker = result.indexOf("Adding current user to docker group", indexOfStartDocker);
        assertTrue(indexOfAddUserDocker > -1);

        int indexOfRegisterRegistry = result.indexOf("Registering kubernetes.registry as insecure registry", indexOfAddUserDocker);
        assertTrue(indexOfRegisterRegistry > -1);

        int indexOfRestartDocker = result.indexOf("Restart docker", indexOfRegisterRegistry);
        assertTrue(indexOfRestartDocker > -1);

        int indexOfDisableIpV6 = result.indexOf("Disabling IPv6", indexOfRestartDocker);
        assertTrue(indexOfDisableIpV6 > -1);

        int indexOfIncreaseMaxMapCount = result.indexOf("Increasing system vm.max_map_count setting", indexOfDisableIpV6);
        assertTrue(indexOfIncreaseMaxMapCount > -1);

        int indexOfDisableSELinux = result.indexOf("Disable selinux if enabled", indexOfIncreaseMaxMapCount);
        assertTrue(indexOfDisableSELinux > -1);

        int indexOfCGroupHack = result.indexOf("cgroup creation hack", indexOfDisableSELinux);
        assertTrue(indexOfCGroupHack > -1);

    }
}
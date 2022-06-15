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


package ch.niceideas.eskimo.shell.base;

import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.shell.setup.AbstractSetupShellTest;
import org.junit.Assume;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class EskimoKubeCtlTest {

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
                new File("./services_setup/base-eskimo/eskimo-kubectl"),
                new File (jailPath + "/eskimo-kubectl"));

        ProcessHelper.exec(new String[]{"bash", "-c", "chmod 777 " + jailPath + "/eskimo-kubectl"}, true);

        String kubectlMockCommandContent = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoKubeCtlTest/kubectl"), "UTF-8");
        FileUtils.writeFile(new File (jailPath + "/kubectl"), kubectlMockCommandContent);

        ProcessHelper.exec(new String[]{"bash", "-c", "chmod 777 " + jailPath + "/kubectl"}, true);

        // I need the real sed
        new File (jailPath + "/sed").delete();

    }

    @AfterEach
    public void tearDownClass() throws Exception {
        if (StringUtils.isNotBlank(jailPath)) {
            FileUtils.delete(new File(jailPath));
        }
    }

    private void createTestScript(String scriptName, String command) throws FileException, ProcessHelper.ProcessHelperException {

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
                "$SCRIPT_DIR/eskimo-kubectl " + command;
        FileUtils.writeFile(new File (jailPath + "/" + scriptName), script);
    }

    @Test
    public void testShowJournal_multiple() throws Exception {
        createTestScript("calling_script.sh", "logs elasticsearch 192.168.56.31");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);

        //System.err.println (result);
        assertTrue (result.contains("-n default logs elasticsearch-0"));
    }

    @Test
    public void testShowJournal_single() throws Exception {
        createTestScript("calling_script.sh", "logs cerebro 192.168.56.31");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);

        //System.err.println (result);
        assertTrue (result.contains("-n default logs cerebro-5bc7f5874b-w9x88"));
    }

    @Test
    public void testStop_deployment() throws Exception {

        createTestScript("calling_script.sh", "stop cerebro 192.168.56.31");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);

        //System.err.println (result);
        assertTrue (result.contains("-n default delete deployment cerebro"));
    }

    @Test
    public void testStop_statefulSet() throws Exception {
        createTestScript("calling_script.sh", "stop kafka 192.168.56.31");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);

        System.err.println (result);
        assertTrue (result.contains("-n default delete statefulset kafka"));
    }

    @Test
    public void testStart_deployment() throws Exception {
        createTestScript("calling_script.sh", "start cerebro 192.168.56.31");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);

        //System.err.println (result);
        assertTrue (result.contains("apply -f -"));
    }

    @Test
    public void testStart_statefulSet() throws Exception {
        createTestScript("calling_script.sh", "start logstash 192.168.56.31");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);

        //System.err.println (result);
        assertTrue (result.contains("apply -f -"));
    }

    @Test
    public void testRestart_deployment() throws Exception {
        createTestScript("calling_script.sh", "restart cerebro 192.168.56.31");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);

        //System.err.println (result);
        //assertTrue (result.contains("delete -f -"));
        assertTrue (result.contains("apply -f -"));
    }

    @Test
    public void testRestart_statefulSet() throws Exception {
        createTestScript("calling_script.sh", "restart elasticsearch 192.168.56.31");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);

        //System.err.println (result);
        //assertTrue (result.contains("delete -f -"));
        assertTrue (result.contains("apply -f -"));
    }

    @Test
    public void testUninstall_deployment() throws Exception {
        createTestScript("calling_script.sh", "uninstall cerebro 192.168.56.31");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);

        //System.err.println (result);
        assertTrue (result.contains("delete -f -"));
    }

    @Test
    public void testUninstall_statefulSet() throws Exception {
        createTestScript("calling_script.sh", "uninstall kafka 192.168.56.31");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);

        //System.err.println (result);
        assertTrue (result.contains("delete -f -"));
    }

}

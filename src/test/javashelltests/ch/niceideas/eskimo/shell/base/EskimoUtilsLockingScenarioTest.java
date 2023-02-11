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

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.ProcessHelper;
import ch.niceideas.eskimo.shell.setup.AbstractSetupShellTest;
import ch.niceideas.eskimo.utils.ActiveWaiter;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EskimoUtilsLockingScenarioTest {

    private static final Logger logger = Logger.getLogger(EskimoUtilsLockingScenarioTest.class);

    protected String jailPath = null;

    /** Run Test on Linux only */
    @BeforeEach
    public void beforeMethod() {
        Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
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
                new File("./services_setup/base-eskimo/eskimo-utils.sh"),
                new File (jailPath + "/eskimo-utils.sh"));

        ProcessHelper.exec(new String[]{"bash", "-c", "chmod 777 " + jailPath + "/eskimo-utils.sh"}, true);

        // I need some real commands
        assertTrue (new File (jailPath + "/bash").delete());
        assertTrue (new File (jailPath + "/sed").delete());
    }

    private void createTestScript(String scriptName, boolean nonblock) throws FileException {

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
                "# Source eskimo-utils.sh\n" +
                ". $SCRIPT_DIR/eskimo-utils.sh\n" +
                "\n" +
                "take_lock test_lock $SCRIPT_DIR " + (nonblock ? "nonblock" : "") + "\n" +
                "if [[ $? != 0 ]]; then echo 'error flocking !'; fi\n " +
                "export lock_handle=$LAST_LOCK_HANDLE\n" +
                "echo $lock_handle\n" +
                (!nonblock ? "/bin/touch $SCRIPT_DIR/blocker_running\nsleep 20" : "");

        FileUtils.writeFile(new File (jailPath + "/" + scriptName), script);
    }

    @Test
    public void testScenario() throws Exception {

        createTestScript("blocker.sh", false);

        createTestScript("failer.sh", true);

        new Thread( () -> {
            try {
                ProcessHelper.exec(new String[]{"bash", jailPath + "/blocker.sh"}, true);
            } catch (Exception e) {
                logger.warn (e, e);
            }
        }).start();

        File blockerMark = new File (jailPath, "blocker_running");
        ActiveWaiter.wait(blockerMark::exists);
        assertTrue (blockerMark.exists());

        String failerResult = ProcessHelper.exec(new String[]{"bash", jailPath + "/failer.sh"}, true);

        logger.debug (failerResult);

        assertTrue(failerResult.contains("Couldn't flock file handle (immediate / non-block) - test_lock /tmp/"));
        assertTrue(failerResult.contains("error flocking !"));
    }
}

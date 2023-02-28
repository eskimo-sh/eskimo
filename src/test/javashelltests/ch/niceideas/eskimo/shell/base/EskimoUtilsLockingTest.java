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
import ch.niceideas.eskimo.utils.OSDetector;
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

public class EskimoUtilsLockingTest {

    private static final Logger logger = Logger.getLogger(EskimoUtilsLockingTest.class);

    protected String jailPath = null;

    /** Run Test on Linux only */
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
                new File("./services_setup/base-eskimo/eskimo-utils.sh"),
                new File (jailPath + "/eskimo-utils.sh"));

        ProcessHelper.exec(new String[]{"bash", "-c", "chmod 777 " + jailPath + "/eskimo-utils.sh"}, true);

        // I need some real commands
        assertTrue (new File (jailPath + "/bash").delete());
        assertTrue (new File (jailPath + "/sed").delete());
    }

    private void createTestScript(String scriptName, File tempFileForIncrement, boolean global) throws FileException {

        String script = "#!/bin/bash\n" + "\n" +
                AbstractSetupShellTest.COMMON_SCRIPT_HACKS +
                "\n" +
                (global ?
                    "take_global_lock test_lock /tmp\n"
                        :
                    "take_lock test_lock /tmp\n"
                ) +
                "if [[ $? != 0 ]]; then echo 'error flocking !'; fi\n " +
                "export lock_handle=$LAST_LOCK_HANDLE\n" +
                "\n" +
                "if [[ ! -f " + tempFileForIncrement.getAbsolutePath() + " ]]; then\n" +
                "    echo '0' > " + tempFileForIncrement.getAbsolutePath() + "\n" +
                "fi" +
                "\n" +
                "let i=`cat " + tempFileForIncrement.getAbsolutePath() + "`+1\n" +
                "echo $i > " + tempFileForIncrement.getAbsolutePath() + "\n" +
                "\n" +
                (global ? "" :
                        "release_lock $lock_handle\n");

        FileUtils.writeFile(new File (jailPath + "/" + scriptName), script);
    }

    private File createCallingScript(boolean global) throws IOException, FileException {
        File tempFileForIncrement = File.createTempFile("eskimo-utils-test", "inc-file");
        assertTrue (tempFileForIncrement.delete());
        tempFileForIncrement.deleteOnExit();

        createTestScript("calling_script.sh", tempFileForIncrement, global);
        return tempFileForIncrement;
    }

    @Test
    public void testNominalLocking() throws Exception {

        File tempFileForIncrement = createCallingScript(false);

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);
        logger.debug (result);

        assertEquals ("1", FileUtils.readFile(tempFileForIncrement));
    }

    @Test
    public void testMultithreadedLockingBehaviourSingle() throws Exception {
        File tempFileForIncrement = createCallingScript(false);
        testThreadedIncrement(tempFileForIncrement);
    }

    @Test
    public void testMultithreadedLockingBehaviourGlobal() throws Exception {
        File tempFileForIncrement = createCallingScript(true);
        testThreadedIncrement(tempFileForIncrement);
    }

    private void testThreadedIncrement(File tempFileForIncrement) throws Exception {
        AtomicReference<Exception> t1Error = new AtomicReference<>();
        Thread t1 = createThread(t1Error);

        AtomicReference<Exception> t2Error = new AtomicReference<>();
        Thread t2 = createThread(t2Error);

        AtomicReference<Exception> t3Error = new AtomicReference<>();
        Thread t3 = createThread(t3Error);

        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();
        t3.join();

        if (t1Error.get() != null) {
            throw new Exception (t1Error.get());
        }
        if (t2Error.get() != null) {
            throw new Exception (t2Error.get());
        }
        if (t3Error.get() != null) {
            throw new Exception (t3Error.get());
        }

        assertEquals ("600", FileUtils.readFile(tempFileForIncrement));
    }

    private Thread createThread(AtomicReference<Exception> errorTracker) {
        return new Thread(() -> {
            try {
                logger.debug (ProcessHelper.exec(new String[]{"bash", "-c", "for i in `seq 1 200`; do echo $i; bash " + jailPath + "/calling_script.sh; done"}, true));
            } catch (ProcessHelper.ProcessHelperException e) {
                logger.error(e, e);
                errorTracker.set(e);
            }
        });
    }

}

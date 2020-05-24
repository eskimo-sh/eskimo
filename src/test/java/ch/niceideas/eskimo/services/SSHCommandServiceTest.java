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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.eskimo.AbstractBaseSSHTest;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SSHCommandServiceTest extends AbstractBaseSSHTest {

    @Override
    protected CommandFactory getSShSubsystemToUse() {
        return new ProcessShellCommandFactory();
    }

    /** Run Test on Linux only */
    @Before
    public void beforeMethod() {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    private ConnectionManagerService cm = null;

    private ProxyManagerService pms = null;

    private SSHCommandService scs = null;

    private SetupService setupService = null;

    private ConfigurationService cs = null;

    @Before
    public void setUp() throws Exception {
        setupService = new SetupService();
        String tempPath = SystemServiceTest.createTempStoragePath();
        setupService.setConfigStoragePathInternal(tempPath);
        FileUtils.writeFile(new File(tempPath + "/config.json"), "{ \"ssh_username\" : \"test\" }");

        cm = new ConnectionManagerService(privateKeyRaw, getSShPort());
        cm.setSetupService (setupService);

        scs = new SSHCommandService();

        scs.setConnectionManagerService(cm);

        pms = new ProxyManagerService();
        pms.setConnectionManagerService(cm);
        cm.setProxyManagerService(pms);
        pms.setConnectionManagerService(cm);

        cs = new ConfigurationService();
        cs.setSetupService(setupService);

        cm.setConfigurationService(cs);
    }

    @Test
    public void testRunSSHCommandStdOut() throws Exception {
        assertNotNull (sshd);
        assertNotNull (cm);
        assertNotNull (scs);

        assertEquals ("1\n", scs.runSSHCommand("localhost", "echo 1"));
    }

    @Test
    public void testRunSSHCommandStdErr() throws Exception {
        assertNotNull (sshd);
        assertNotNull (cm);
        assertNotNull (scs);

        try {
            scs.runSSHCommand("localhost", "/bin/bash -c /bin/tada");
            fail ("Exception expected");
        } catch (SSHCommandException e) {
            assertNotNull(e);
            assertEquals ("Command exited with return code 127\n" +
                    "/bin/bash: /bin/tada: No such file or directory\n", e.getMessage());
        }
    }

    @Test
    public void testRunSSHScriptStdOut() throws Exception {
        assertNotNull (sshd);
        assertNotNull (cm);
        assertNotNull (scs);

        assertEquals ("1\n" +
                "2\n" +
                "3\n", scs.runSSHScript("localhost", "echo 1; echo 2 && echo 3;"));
    }

    @Test
    public void testRunSSHScriptNewLinesStdOut() throws Exception {
        assertNotNull (sshd);
        assertNotNull (cm);
        assertNotNull (scs);

        assertEquals ("1\n" +
                "2\n" +
                "3\n", scs.runSSHScript("localhost", "echo 1\necho 2\necho 3;"));
    }

    @Test
    public void testRunSSHScriptErr() throws Exception {
        assertNotNull (sshd);
        assertNotNull (cm);
        assertNotNull (scs);

        try {
            scs.runSSHScript("localhost", "/bin/tada");
            fail ("Exception expected");
        } catch (SSHCommandException e) {
            assertNotNull(e);
            assertEquals ("bash: line 1: /bin/tada: No such file or directory\n", e.getMessage());
        }
    }
}

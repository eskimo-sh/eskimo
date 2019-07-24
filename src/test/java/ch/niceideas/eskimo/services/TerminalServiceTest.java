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
import ch.niceideas.eskimo.terminal.ScreenImage;
import ch.niceideas.common.utils.Pair;
import org.apache.log4j.Logger;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.*;

public class TerminalServiceTest extends AbstractBaseSSHTest {

    private static final Logger logger = Logger.getLogger(TerminalServiceTest.class);

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

    private TerminalService ts = null;

    private SetupService setupService = null;

    @Before
    public void setUp() throws Exception {
        setupService = new SetupService();
        String tempPath = SystemServiceTest.createTempStoragePath();
        setupService.setConfigStoragePathInternal(tempPath);
        FileUtils.writeFile(new File(tempPath + "/config.json"), "{ \"ssh_username\" : \"test\" }");

        cm = new ConnectionManagerService(privateKeyRaw, SSH_PORT);
        cm.setSetupService (setupService);

        ts = new TerminalService();

        ts.setConnectionManagerService(cm);
    }

    @Test
    public void testNominal() throws Exception {
        assertNotNull (sshd);
        assertNotNull (cm);

        ScreenImage si = ts.postUpdate("node=localhost&s=699156997&w=80&h=25&c=1&k=&t=0");
        assertTrue (si.screen.startsWith("<pre class='term '><span class='"));

        /* FIXME Test nexts
        si = ts.postUpdate("node=localhost&s=699156997&w=80&h=25&c=1&k=&t=2001");
        assertTrue (si.screen.startsWith("<idem/>"));

        si = ts.postUpdate("node=localhost&s=699156997&w=80&h=25&c=1&k=&t=2001");
        assertTrue (si.screen.startsWith("<idem/>"));

        si = ts.postUpdate("node=localhost&s=699156997&w=80&h=25&c=1&k=l&t=2003");
        assertTrue (si.screen.startsWith("<pre class='term '><span class='"));

        si = ts.postUpdate("node=localhost&s=699156997&w=80&h=25&c=1&k=s&t=2004");
        assertTrue (si.screen.startsWith("<pre class='term '><span class='"));

        si = ts.postUpdate("node=localhost&s=699156997&w=80&h=25&c=1&k=%0D&t=200");
        assertTrue (si.screen.startsWith("<pre class='term '><span class='"));
        */

        ts.removeTerminal("699156997");
    }

}

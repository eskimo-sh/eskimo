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


package ch.niceideas.eskimo.shell.setup;

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class KubeSlaveSetupTest extends AbstractSetupShellTest {

    protected static String jailPath = null;

    private static boolean initialized = false;

    @BeforeEach
    public void setUp() throws Exception {
        if (!initialized) {
            jailPath = setupJail(getServiceName());
            initialized = true;
        }
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
        if (initialized && StringUtils.isNotBlank(jailPath)) {
            FileUtils.delete(new File(jailPath));
        }
    }

    @Override
    protected String getJailPath() {
        return jailPath;
    }

    @Override
    protected String getServiceName() {
        return "kube-slave";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh and common.sh are automatic
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh"
        };
    }

    @Test
    public void testSystemDInstallation() throws Exception {

        String sudoLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_sudo"));
        if (StringUtils.isNotBlank(sudoLogs)) {

            System.err.println(sudoLogs);
            assertTrue(sudoLogs.contains("cp " + getJailPath() + "/" + getServiceName() + ".service /lib/systemd/system/"));

            int indexOfDaemonReload = sudoLogs.indexOf("systemctl daemon-reload");
            assertTrue(indexOfDaemonReload > -1);

            int indexOfStatusFirst = sudoLogs.indexOf("systemctl status " + getServiceName(), indexOfDaemonReload + 1);
            assertTrue(indexOfStatusFirst > -1);

            int indexOfStart = sudoLogs.indexOf("systemctl start " + getServiceName(), indexOfStatusFirst + 1);
            assertTrue(indexOfStart > -1);

            int indexOfStatusSecond = sudoLogs.indexOf("systemctl status " + getServiceName(), indexOfStart + 1);
            assertTrue(indexOfStatusSecond > -1);

            int indexOfEnable = sudoLogs.indexOf("systemctl enable " + getServiceName(), indexOfStatusSecond + 1);
            assertTrue(indexOfEnable > -1);



        } else {
            fail ("Expected to find sudo logs in .log_sudo");
        }

        String systemctlLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_systemctl"));
        if (StringUtils.isNotBlank(systemctlLogs)) {

            //System.err.println(systemctlLogs);

            assertTrue(systemctlLogs.contains("show -p SubState " + getServiceName()));

        } else {
            fail ("Expected to find systemctl logs in .log_systemctl");
        }
    }

}

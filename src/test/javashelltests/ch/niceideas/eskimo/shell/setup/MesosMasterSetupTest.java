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

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

public class MesosMasterSetupTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(MesosMasterSetupTest.class);

    protected static String jailPath = null;

    private static boolean initialized = false;

    @Before
    public void setUp() throws Exception {
        if (!initialized) {
            jailPath = setupJail(getServiceName());
            initialized = true;
        }
    }

    @AfterClass
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
        return "mesos-master";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh and common.sh are automatic
        copyFile(jailPath, "inContainerSetupMesosMaster.sh");
        copyFile(jailPath, "inContainerStartService.sh");
        copyFile(jailPath, "inContainerInjectTopology.sh");
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupMesosMaster.sh",
                "inContainerInjectTopology.sh"
        };
    }

    @Test
    public void testSystemDInstallation() throws Exception {
        assertSystemDInstallation();
    }

    @Test
    public void testSystemDockerManipulations() throws Exception {
        assertSystemDServiceDockerCommands();
    }

    @Test
    public void testConfigurationFileUpdate() throws Exception {

        String sudoLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_sudo"));
        if (StringUtils.isNotBlank(sudoLogs)) {

            //System.err.println (sudoLogs);

            assertTrue(sudoLogs.contains("bash -c echo -e \"export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/lib/:/usr/local/lib/mesos/lib\" >> /etc/profile"));
            assertTrue(sudoLogs.contains("bash -c echo -e \"export PYTHONPATH=$PYTHONPATH:/usr/lib/python2.7/:/usr/local/lib/mesos/lib/python2.7/site-packages/\" >> /etc/profile"));
            assertTrue(sudoLogs.contains("bash -c echo -e \"export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/lib/:/usr/local/lib/mesos/lib\" >> /etc/bash.bashrc"));
            assertTrue(sudoLogs.contains("bash -c echo -e \"export PYTHONPATH=$PYTHONPATH:/usr/lib/python2.7/:/usr/local/lib/mesos/lib/python2.7/site-packages/\" >> /etc/bash.bashrc"));
            assertTrue(sudoLogs.contains("bash -c echo \"export MESOS_ip=192.168.10.11\" >> /usr/local/etc/mesos/mesos-master-env.sh"));
            assertTrue(sudoLogs.contains("bash -c echo \"export MESOS_hostname=192.168.10.11\" >> /usr/local/etc/mesos/mesos-master-env.sh"));
            assertTrue(sudoLogs.contains("bash -c echo \"export MESOS_work_dir=/var/lib/mesos/master\" >> /usr/local/etc/mesos/mesos-master-env.sh"));
            assertTrue(sudoLogs.contains("bash -c echo \"export MESOS_quorum=1\" >> /usr/local/etc/mesos/mesos-master-env.sh"));
            assertTrue(sudoLogs.contains("bash -c echo \"export MESOS_acls=/usr/local/lib/mesos/etc/mesos/mesos-acls.json\" >> /usr/local/etc/mesos/mesos-master-env.sh"));
        } else {
            fail ("Expected to find sudo logs in .log_sudo");
        }
    }
}

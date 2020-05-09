package ch.niceideas.eskimo.shell.setup;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

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

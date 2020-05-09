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

public class MesosAgentSearchSetupTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(MesosAgentSearchSetupTest.class);

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
        return "mesos-agent";
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

            //System.err.println(sudoLogs);
            assertTrue(sudoLogs.contains("cp " + getJailPath() + "/" + getServiceName() + ".service /lib/systemd/system/"));

            int indexOfDaemonReload = sudoLogs.indexOf("systemctl daemon-reload");
            assertTrue(indexOfDaemonReload > -1);

            int indexOfStatusFirst = sudoLogs.indexOf("systemctl status " + getServiceName(), indexOfDaemonReload + 1);
            assertTrue(indexOfStatusFirst > -1);

            int indexOfStart = sudoLogs.indexOf("systemctl start " + getServiceName(), indexOfStatusFirst + 1);
            assertTrue(indexOfStart > -1);

            int indexOfStatusSecond = sudoLogs.indexOf("systemctl status " + getServiceName(), indexOfStart + 1);
            assertTrue(indexOfStatusSecond > -1);

            int indexOfStop = sudoLogs.indexOf("systemctl stop " + getServiceName(), indexOfStatusSecond + 1);
            assertTrue(indexOfStop > -1);

            int indexOfEnable = sudoLogs.indexOf("systemctl enable " + getServiceName(), indexOfStop + 1);
            assertTrue(indexOfEnable > -1);

            int indexOfStartSecond = sudoLogs.indexOf("systemctl start " + getServiceName(), indexOfEnable + 1);
            assertTrue(indexOfStartSecond > -1);


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

    @Test
    public void testConfigurationFileUpdate() throws Exception {

        String sudoLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_sudo"));
        if (StringUtils.isNotBlank(sudoLogs)) {

            //System.err.println (sudoLogs);

            assertTrue(sudoLogs.contains("" +
                    "bash -c echo -e \"\\n#Path of the slave work directory. \"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"#This is where executor sandboxes will be placed, as well as the agent's checkpointed state.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo \"export MESOS_work_dir=/var/lib/mesos/slave\" >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"\\n#we need the Slave to discover the Master.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"#This is accomplished by updating the master argument to the master Zookeeper URL\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"#For this we first need to source the eskimo topology\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    ""));

            assertTrue(sudoLogs.contains("" +
                    "bash -c echo 'export MESOS_master=zk://$MASTER_ZOOKEEPER_1:2181/mesos\\' >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"\\n# file path containing the JSON-formatted Total consumable resources per agent.\" >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo \"export MESOS_resources=file:///usr/local/lib/mesos/etc/mesos/mesos-resources.json\" >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"\\n#Avoid issues with systems that have multiple ethernet interfaces when the Master or Slave\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"#registers with a loopback or otherwise undesirable interface.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo \"export MESOS_ip=192.168.10.11\" >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"\\n#By default, the Master will use the system hostname which can result in issues in the \"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"#event the system name isn’t resolvable via your DNS server.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo \"export MESOS_hostname=192.168.10.11\" >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"\\n#Enabling docker image provider.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"\\n# Comma-separated list of containerizer implementations to compose in order to provide containerization\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"# Available options are mesos and docker\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"export MESOS_containerizers=docker,mesos\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"\\n# isolation mechanisms to use\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"export MESOS_isolation=docker/runtime,filesystem/linux\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"\\n# Giving it a little time do download and extract large docker images\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"export MESOS_executor_registration_timeout=5mins\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"\\n# This flag controls which agent configuration changes are considered acceptable when recovering the previous agent state.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"# additive: The new state must be a superset of the old state: it is permitted to add additional resources, attributes \"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"# and domains but not to remove or to modify existing ones.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh\n" +
                    "bash -c echo -e \"export MESOS_reconfiguration_policy=additive\"  >> /usr/local/etc/mesos/mesos-slave-env.sh" +
                    ""));

        } else {
            fail ("Expected to find sudo logs in .log_sudo");
        }
    }
}

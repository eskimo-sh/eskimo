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

public class GlusterSetupTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(GlusterSetupTest.class);

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
        return "gluster";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh and common.sh are automatic
        copyFile(jailPath, "inContainerSetupGluster.sh");
        copyFile(jailPath, "inContainerStartService.sh");
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupGluster.sh"
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

            assertTrue(sudoLogs.contains("" +
                    "bash -c echo \"volume management\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"    type mgmt/glusterd\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"    option working-directory /var/lib/gluster/working_directory\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"    option transport-type socket,rdma\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"    option transport.socket.keepalive-time 10\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"    option transport.socket.keepalive-interval 2\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"    option transport.socket.read-fail-log off\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"    option ping-timeout 0\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"    option event-threads 1\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"    option transport.rdma.bind-address 192.168.10.11\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"    option transport.socket.bind-address 192.168.10.11\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"    option transport.tcp.bind-address 192.168.10.11\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"#   option transport.address-family inet6\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"#   option base-port 49152\" >> /var/lib/gluster/glusterfs.VOLUME_FILE\n" +
                    "bash -c echo \"end-volume\" >> /var/lib/gluster/glusterfs.VOLUME_FILE"));

        } else {
            fail ("Expected to find bash logs in .log_bash");
        }
    }
}

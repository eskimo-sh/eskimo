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

public class NtpSetupTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(NtpSetupTest.class);

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
        return "ntp";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh is automatic
        copyFile(jailPath, "common.sh");
        copyFile(jailPath, "inContainerSetupNtp.sh");
        copyFile(jailPath, "inContainerStartService.sh");
        copyFile(jailPath, "inContainerInjectTopology.sh");
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupNtp.sh",
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

        assertTestConfFileUpdate();

        String bashLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_bash"));
        if (StringUtils.isNotBlank(bashLogs)) {

            System.err.println (bashLogs);

            /*
            bash -c "echo -e \"\nlogfile /var/log/ntp/ntp.log\" >> /etc/ntp.conf"
bash -c "echo -e \"logconfig =syncevents +peerevents +sysevents +allclock\" >> /etc/ntp.conf"
             */

            assertTrue(bashLogs.contains("" +
                    "-c echo -e \"\\nlogfile /var/log/ntp/ntp.log\" >> /etc/ntp.conf\n" +
                    "-c echo -e \"logconfig =syncevents +peerevents +sysevents +allclock\" >> /etc/ntp.conf"));

        } else {
            fail ("Expected to find bash logs in .log_bash");
        }
    }
}

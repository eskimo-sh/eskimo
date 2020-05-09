package ch.niceideas.eskimo.shell.setup;

import ch.niceideas.common.utils.*;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

public class MarathonSetupTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(MarathonSetupTest.class);

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
        return "marathon";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh and common.sh are automatic
        copyFile(jailPath, "inContainerStartService.sh");
        copyFile(jailPath, "inContainerInjectTopology.sh");

        // create a wrapper passing arguments to inContainerSetupGrafana.sh
        try {
            String setupScript = FileUtils.readFile(new File("./services_setup/marathon/inContainerSetupMarathon.sh"));

            setupScript = setupScript.replace("MARATHON_USER_ID=$1", "MARATHON_USER_ID=3306");

            FileUtils.writeFile(new File(jailPath + "/inContainerSetupMarathon.sh"), setupScript);
        } catch (FileException e) {
            throw new IOException(e);
        }
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupMarathon.sh",
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
    }
}

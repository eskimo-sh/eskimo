package ch.niceideas.eskimo.shell.setup;

import ch.niceideas.common.utils.FileUtils;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class KibanaSetupTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(KibanaSetupTest.class);

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
        return "kibana";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh is automatic
        copyFile(jailPath, "common.sh");
        copyFile(jailPath, "setupESCommon.sh");
        copyFile(jailPath, "inContainerSetupKibana.sh");
        copyFile(jailPath, "inContainerSetupESCommon.sh");
        copyFile(jailPath, "inContainerStartService.sh");
        copyFile(jailPath, "inContainerInjectTopology.sh");
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupKibana.sh",
                "inContainerInjectTopology.sh"};
    }

    @Test
    public void testMarathonInstallation() throws Exception {
        assertMarathonCommands();
    }

    @Test
    public void testSystemDockerManipulations() throws Exception {
        assertMarathonServiceDockerCommands();

    }

    @Test
    public void testConfigurationFileUpdate() throws Exception {
        assertTestConfFileUpdate();
    }
}

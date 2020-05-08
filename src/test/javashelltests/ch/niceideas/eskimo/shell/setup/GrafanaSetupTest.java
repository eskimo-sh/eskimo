package ch.niceideas.eskimo.shell.setup;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class GrafanaSetupTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(GrafanaSetupTest.class);

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
        return "grafana";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh is automatic
        copyFile(jailPath, "common.sh");
        copyFile(jailPath, "inContainerSetupGrafana.sh");
        copyFile(jailPath, "inContainerInjectTopology.sh");

        // create a wrapper passing arguments to inContainerSetupGrafana.sh
        try {
            FileUtils.writeFile(new File(jailPath + "/inContainerSetupGrafanaWrapper.sh"), "" +
                    "#!/bin/bash\n" +
                    "\n" +
                    "/bin/bash " + jailPath + "/inContainerSetupGrafana.sh 3304\n");
        } catch (FileException e) {
            throw new IOException(e);
        }
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupGrafanaWrapper.sh",
                "inContainerInjectTopology.sh"
        };
    }

    @Override
    protected String[] getScriptsToEnhance() {
        return new String[] {
                "setup.sh",
                "inContainerSetupGrafana.sh",
                "inContainerInjectTopology.sh"
        };
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

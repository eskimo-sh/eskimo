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
        copyFile("kibana", jailPath, "common.sh");
        copyFile("kibana", jailPath, "setupESCommon.sh");
        copyFile("kibana", jailPath, "inContainerSetupKibana.sh");
        copyFile("kibana", jailPath, "inContainerSetupESCommon.sh");
        copyFile("kibana", jailPath, "inContainerStartService.sh");
        copyFile("kibana", jailPath, "inContainerInjectTopology.sh");
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupKibana.sh",
                "inContainerInjectTopology.sh"};
    }

    private void copyFile(String service,  String jailPath, String source) throws IOException {
        FileUtils.copy(
                new File ("./services_setup/" + service + "/" + source),
                new File (jailPath + "/" + source));
    }

    // none on kibana
    /*
    @Test
    public void testSystemDInstallation() throws Exception {
        System.err.println (setupLogs);
    }
    */

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

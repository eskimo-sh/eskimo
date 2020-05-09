package ch.niceideas.eskimo.shell.setup;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class ZeppelinSetupTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(ZeppelinSetupTest.class);

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
        return "zeppelin";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh and common.sh are automatic
        copyFile(jailPath, "setupESCommon.sh");
        copyFile(jailPath, "setupFlinkCommon.sh");
        copyFile(jailPath, "setupSparkCommon.sh");
        copyFile(jailPath, "setupLogstashCommon.sh");
        copyFile(jailPath, "inContainerSetupFlinkCommon.sh");
        copyFile(jailPath, "inContainerSetupSparkCommon.sh");
        copyFile(jailPath, "inContainerStartService.sh");
        copyFile(jailPath, "inContainerInjectTopologyZeppelin.sh");
        copyFile(jailPath, "inContainerInjectTopologyFlink.sh");
        copyFile(jailPath, "inContainerInjectTopologySpark.sh");

        // create a wrapper passing arguments to inContainerSetupGrafana.sh
        try {
            String setupScript = FileUtils.readFile(new File("./services_setup/zeppelin/inContainerSetupZeppelin.sh"));

            setupScript = setupScript.replace(". /run/zeppelin_mesos_environment", "");
            setupScript = setupScript.replace("ln -s /usr/local/host_lib/mesos-$AMESOS_VERSION /usr/local/lib/mesos-$AMESOS_VERSION", "");
            setupScript = setupScript.replace("ln -s /usr/local/lib/mesos-$AMESOS_VERSION /usr/local/lib/mesos", "");

            FileUtils.writeFile(new File(jailPath + "/inContainerSetupZeppelin.sh"), setupScript);
        } catch (FileException e) {
            throw new IOException(e);
        }
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupZeppelin.sh",
                "inContainerInjectTopologyZeppelin.sh"};
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

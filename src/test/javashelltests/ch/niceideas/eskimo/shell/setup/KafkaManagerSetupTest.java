package ch.niceideas.eskimo.shell.setup;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class KafkaManagerSetupTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(KafkaManagerSetupTest.class);

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
        return "kafka-manager";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh and common.sh are automatic
        copyFile(jailPath, "setupCommon.sh");
        //copyFile(jailPath, "inContainerSetupKafkaManager.sh");
        copyFile(jailPath, "inContainerSetupKafkaCommon.sh");
        copyFile(jailPath, "inContainerStartService.sh");
        copyFile(jailPath, "inContainerInjectTopology.sh");

        // create a wrapper passing arguments to inContainerSetupGrafana.sh
        try {
            String setupScript = FileUtils.readFile(new File("./services_setup/kafka-manager/inContainerSetupKafkaManager.sh"));

            setupScript = setupScript.replace("/usr/local/lib/kafka-manager/bin/kafka-manager \\", "sleep 200 &");
            setupScript = setupScript.replace("-Dapplication.home=/usr/local/lib/kafka-manager/ \\", "# (replaced)");
            setupScript = setupScript.replace("-Dpidfile.path=/tmp/kafka-manager-temp.pid \\", "# (replaced)");
            setupScript = setupScript.replace("-Dconfig.file=/usr/local/lib/kafka-manager/conf/application.conf \\", "# (replaced)");
            setupScript = setupScript.replace("-Dhttp.port=22080 >> kafka-manager_install_log 2>&1 &", "# (replaced)");

            FileUtils.writeFile(new File(jailPath + "/inContainerSetupKafkaManager.sh"), setupScript);
        } catch (FileException e) {
            throw new IOException(e);
        }
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupKafkaManager.sh",
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

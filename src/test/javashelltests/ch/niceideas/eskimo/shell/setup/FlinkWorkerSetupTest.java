package ch.niceideas.eskimo.shell.setup;

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

public class FlinkWorkerSetupTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(FlinkWorkerSetupTest.class);

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
        return "flink-worker";
    }

    @Override
    protected String getTemplateName() {
        return "flink";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh is automatic
        copyFile(jailPath, "common.sh");
        copyFile(jailPath, "setupCommon.sh");
        copyFile(jailPath, "inContainerSetupFlinkWorker.sh");
        copyFile(jailPath, "inContainerSetupFlinkCommon.sh");
        copyFile(jailPath, "inContainerInjectTopology.sh");
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupFlinkWorker.sh",
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

        String sudoLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_sudo"));
        if (StringUtils.isNotBlank(sudoLogs)) {

            //System.err.println (sudoLogs);

            assertTrue(sudoLogs.contains("bash -c echo -e \"taskmanager.host: 192.168.10.11\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"));
        } else {
            fail ("Expected to find bash logs in .log_bash");
        }
    }
}

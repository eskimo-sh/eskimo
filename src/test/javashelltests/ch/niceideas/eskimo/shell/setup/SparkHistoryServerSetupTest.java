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

public class SparkHistoryServerSetupTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(SparkHistoryServerSetupTest.class);

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
        return "spark-history-server";
    }

    @Override
    protected String getTemplateName() {
        return "spark";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh and common.sh are automatic
        copyFile(jailPath, "setupCommon.sh");
        copyFile(jailPath, "inContainerSetupSparkHistoryServer.sh");
        copyFile(jailPath, "inContainerSetupSparkCommon.sh");
        copyFile(jailPath, "inContainerStartService.sh");
        copyFile(jailPath, "inContainerInjectTopology.sh");
        copyFile(jailPath, "inContainerInjectTopologySparkHistory.sh");
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupSparkHistoryServer.sh",
                "inContainerInjectTopology.sh",
                "inContainerInjectTopologySparkHistory.sh"};
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

        String sudohLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_sudo"));
        if (StringUtils.isNotBlank(sudohLogs)) {

            System.err.println (sudohLogs);

            assertTrue(sudohLogs.contains("bash -c echo -e \"spark.history.fs.logDirectory=file:///var/lib/spark/eventlog\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"));
            assertTrue(sudohLogs.contains("bash -c echo -e \"spark.history.fs.update.interval=5s\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"));
        } else {
            fail ("Expected to find sudo logs in .log_sudo");
        }
    }
}

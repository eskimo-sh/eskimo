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

public class SparkWorkerSetupTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(SparkWorkerSetupTest.class);

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
        return "spark-executor";
    }

    @Override
    protected String getTemplateName() {
        return "spark";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh and common.sh are automatic
        copyFile(jailPath, "setupCommon.sh");
        copyFile(jailPath, "inContainerSetupSpark.sh");
        copyFile(jailPath, "inContainerSetupSparkCommon.sh");
        copyFile(jailPath, "inContainerSetupSparkMesosShuffleService.sh");
        copyFile(jailPath, "inContainerStartMesosShuffleService.sh");
        copyFile(jailPath, "inContainerInjectTopology.sh");
        copyFile(jailPath, "inContainerInjectTopologyMesosShuffle.sh");
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupSpark.sh",
                "inContainerSetupSparkMesosShuffleService.sh",
                "inContainerInjectTopology.sh",
                "inContainerInjectTopologyMesosShuffle.sh"
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

            assertTrue(sudoLogs.contains("bash -c echo -e \"export LIBPROCESS_IP=\"  >> /usr/local/lib/spark/conf/spark-env.sh"));
            assertTrue(sudoLogs.contains("bash -c echo -e \"export SPARK_LOCAL_IP=\"  >> /usr/local/lib/spark/conf/spark-env.sh"));

        } else {
            fail ("Expected to find sudo logs in .log_sudo");
        }
    }
}

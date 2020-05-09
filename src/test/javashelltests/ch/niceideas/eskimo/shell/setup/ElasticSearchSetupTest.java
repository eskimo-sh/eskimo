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

public class ElasticSearchSetupTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(ElasticSearchSetupTest.class);

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
        return "elasticsearch";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh and common.sh are automatic
        copyFile(jailPath, "setupESCommon.sh");
        copyFile(jailPath, "inContainerSetupElasticSearch.sh");
        copyFile(jailPath, "inContainerSetupESCommon.sh");
        copyFile(jailPath, "inContainerStartService.sh");
        copyFile(jailPath, "inContainerInjectTopology.sh");
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupElasticSearch.sh",
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

            //System.err.println (bashLogs);

            assertTrue(bashLogs.contains("-c echo \"discovery.zen.fd.ping_interval: 1s\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"));
            assertTrue(bashLogs.contains("-c echo \"discovery.zen.fd.ping_timeout: 60s\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"));
            assertTrue(bashLogs.contains("-c echo \"discovery.zen.fd.ping_retries: 8\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"));
            assertTrue(bashLogs.contains("-c echo \"network.publish_host: 192.168.10.11\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"));
        } else {
            fail ("Expected to find bash logs in .log_bash");
        }
    }
}

package ch.niceideas.eskimo.shell.setup;

import afu.org.checkerframework.checker.oigj.qual.O;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.ProcessHelper;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.MarathonServicesConfigWrapper;
import ch.niceideas.eskimo.model.MemoryModel;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.Topology;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.SetupService;
import ch.niceideas.eskimo.services.StandardSetupHelpers;
import ch.niceideas.eskimo.services.SystemServiceTest;
import org.apache.log4j.Logger;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

public class CerebroSetupTest extends AbstractSetuoShellTest {

    private static final Logger logger = Logger.getLogger(CerebroSetupTest.class);

    @Override
    protected String getServiceName() {
        return "cerebro";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        copyFile("cerebro", jailPath, "common.sh");
        copyFile("cerebro", jailPath, "setupESCommon.sh");
        copyFile("cerebro", jailPath, "inContainerSetupCerebro.sh");
        copyFile("cerebro", jailPath, "inContainerSetupESCommon.sh");
        copyFile("cerebro", jailPath, "inContainerStartService.sh");
    }

    @Override
    protected String executeScripts(String jailPath) throws ProcessHelper.ProcessHelperException {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append(ProcessHelper.exec(new String[]{"bash", jailPath + "/setup.sh"}, true));
        resultBuilder.append(ProcessHelper.exec(new String[]{"bash", jailPath + "/inContainerInjectTopology.sh"}, true));
        return resultBuilder.toString();
    }

    private void copyFile(String service,  String jailPath, String source) throws IOException {
        FileUtils.copy(
                new File ("./services_setup/" + service + "/" + source),
                new File (jailPath + "/" + source));
    }

    @Test
    public void testSystemDInstallation() throws Exception {
        System.err.println (setupLogs);
    }

    @Test
    public void testConfigurationFileUpdateSetupTime() throws Exception {

    }

    @Test
    public void testConfigurationFileUpdateRuntimeTime() throws Exception {

    }
}

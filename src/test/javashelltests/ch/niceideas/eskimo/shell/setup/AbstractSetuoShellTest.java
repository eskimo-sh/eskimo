package ch.niceideas.eskimo.shell.setup;

import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.MarathonServicesConfigWrapper;
import ch.niceideas.eskimo.model.MemoryModel;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.Topology;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.SetupService;
import ch.niceideas.eskimo.services.StandardSetupHelpers;
import ch.niceideas.eskimo.services.SystemServiceTest;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

public abstract class AbstractSetuoShellTest {

    private static final Logger logger = Logger.getLogger(AbstractSetuoShellTest.class);

    private static boolean initialized = false;

    protected String setupLogs = null;

    @Before
    public void setUp() throws Exception {
        if (!initialized) {
            setupLogs = setupJail(getServiceName());
            initialized = true;
        }
    }

    protected final String setupJail(String serviceName) throws Exception {
        String jailPath = createJail();

        String setupScript = FileUtils.readFile(new File("./services_setup/" + serviceName + "/setup.sh"));

        // inject overriding of path to use jail commands
        setupScript = setupScript.replace("#!/usr/bin/env bash\n", "#!/usr/bin/env bash\nexport PATH=.:$PATH\n");

        // inject custom topology loading
        setupScript = setupScript.replace("loadTopology", ". ./eskimo-topology.sh");

        FileUtils.writeFile(new File(jailPath + "/setup.sh"), setupScript);

        // generate custom topology file
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        MarathonServicesConfigWrapper marathonServicesConfig = StandardSetupHelpers.getStandardMarathonConfig();

        ServicesDefinition def = new ServicesDefinition();
        SetupService setupService = new SetupService();
        setupService.setConfigStoragePathInternal(SystemServiceTest.createTempStoragePath());
        def.setSetupService(setupService);
        def.afterPropertiesSet();

        Topology topology = Topology.create(nodesConfig, marathonServicesConfig, new HashSet<>(), def, null, "192.168.10.11");

        FileUtils.writeFile(new File (jailPath + "/eskimo-topology.sh"), topology.getTopologyScriptForNode(nodesConfig, new MemoryModel(new HashMap<>()), 1));
        ProcessHelper.exec(new String[]{"chmod", "755", jailPath + "/eskimo-topology.sh"}, true);

        String injectTopologyScript = FileUtils.readFile(new File("./services_setup/" + serviceName + "/inContainerInjectTopology.sh"));
        if (StringUtils.isNotBlank(injectTopologyScript)) {

            // inject overriding of path to use jail commands
            injectTopologyScript = injectTopologyScript.replace("#!/usr/bin/env bash\n", "" +
                    "#!/usr/bin/env bash\n" +
                    "export PATH=.:$PATH\n" +
                    "SCRIPT_DIR=\"$( cd \"$( dirname \"${BASH_SOURCE[0]}\" )\" && pwd )\"\n" +
                    "\n" +
                    "# CHange current folder to script dir (important !)\n" +
                    "cd $SCRIPT_DIR");

            // inject custom topology loading
            injectTopologyScript = injectTopologyScript.replace(". /etc/eskimo_topology.sh", ". " + jailPath + "/eskimo-topology.sh");
        }

        //System.err.println(injectTopologyScript);

        FileUtils.writeFile(new File(jailPath + "/inContainerInjectTopology.sh"), injectTopologyScript);

        copyScripts(jailPath);

        return executeScripts(jailPath);
    }

    protected abstract String getServiceName();

    protected abstract void copyScripts(String jailPath) throws IOException;

    protected abstract String executeScripts(String jailPath) throws ProcessHelper.ProcessHelperException;

    /**
     * @return the path of the new jail where setup has to be executed
     */
    String createJail() throws Exception {
        File tempFile = File.createTempFile("setupshell_", "_test");
        tempFile.delete();
        tempFile.mkdir();

        // copy bash and everything bash requires to jail bin
        createLoggingExecutable("cp", tempFile.getAbsolutePath());
        createLoggingExecutable("sudo", tempFile.getAbsolutePath());
        createLoggingExecutable("gunzip", tempFile.getAbsolutePath());
        createLoggingExecutable("curl", tempFile.getAbsolutePath());
        createLoggingExecutable("mkdir", tempFile.getAbsolutePath());
        createLoggingExecutable("useradd", tempFile.getAbsolutePath());
        createLoggingExecutable("chown", tempFile.getAbsolutePath());
        createLoggingExecutable("curl", tempFile.getAbsolutePath());

        createDummyExecutable("id", tempFile.getAbsolutePath());
        createDummyExecutable("docker", tempFile.getAbsolutePath());
        createDummyExecutable("sed", tempFile.getAbsolutePath());

        return tempFile.getAbsolutePath();
    }

    private void createLoggingExecutable(String command, String targetDir) throws Exception {

        File targetPath = new File (targetDir + "/" + command);
        FileUtils.writeFile(targetPath, "" +
                "#/bin/bash\n" +
                "\n" +
                "echo \"$@\" >> .log_" + command + "\n");

        logger.info (ProcessHelper.exec("chmod 755 " + targetPath, true));
    }

    private void createDummyExecutable(String script, String targetDir) throws Exception {

        String idScript = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("AbstractSetupShellTest/" + script));

        File targetPath = new File (targetDir + "/" + script);
        FileUtils.writeFile(targetPath, idScript);

        logger.info (ProcessHelper.exec("chmod 755 " + targetPath, true));
    }

}



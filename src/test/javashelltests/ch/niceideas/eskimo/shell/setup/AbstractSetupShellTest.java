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
import org.junit.Assume;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

public abstract class AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(AbstractSetupShellTest.class);

    /** Run Test on Linux only */
    @Before
    public void beforeMethod() {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    /*
    @AfterClass
    public static void tearDownClass() throws Exception {
        if (initialized && StringUtils.isNotBlank(jailPath)) {
            FileUtils.delete(new File (jailPath));
        }
    }
    */

    protected void copyFile(String jailPath, String source) throws IOException {
        FileUtils.copy(
                new File ("./services_setup/" + getServiceName() + "/" + source),
                new File (jailPath + "/" + source));
    }

    protected final String setupJail(String serviceName) throws Exception {
        String jailPath = createJail();

        // Enhance setup script
        String setupScript = FileUtils.readFile(new File("./services_setup/" + serviceName + "/setup.sh"));

        // inject custom topology loading
        setupScript = setupScript.replace("loadTopology", ". ./eskimo-topology.sh");
        FileUtils.writeFile(new File(jailPath + "/setup.sh"), setupScript);


        // Enhance common script
        String commonScript = FileUtils.readFile(new File("./services_setup/" + serviceName + "/common.sh"));

        // inject custom topology loading
        commonScript = commonScript.replace(
                "function create_binary_wrapper(){",
                "function create_binary_wrapper(){\n" +
                        "return\n");

        FileUtils.writeFile(new File(jailPath + "/common.sh"), commonScript);


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

        String testFileConf = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getCaemlCaseServiceName()+"SetupShellTest/testFile.conf"));
        if (StringUtils.isNotBlank(testFileConf)) {
            copyResource("testFile.conf", jailPath, testFileConf);
        }

        copyScripts(jailPath);

        enhanceScripts (jailPath);

        logger.debug ( executeScripts(jailPath));

        return jailPath;
    }

    protected abstract String getJailPath();

    private void enhanceScripts(String jailPath) throws FileException {

        for (String scriptToExecute: getScriptsToEnhance()) {

            String scriptContent = FileUtils.readFile(new File(jailPath + "/" + scriptToExecute));
            if (StringUtils.isNotBlank(scriptContent)) {

                // inject overriding of path to use jail commands
                scriptContent = scriptContent.replace("#!/usr/bin/env bash\n", "" +
                        "#!/usr/bin/env bash\n" +
                        "\n" +
                        "SCRIPT_DIR=\"$( cd \"$( dirname \"${BASH_SOURCE[0]}\" )\" && pwd )\"\n" +
                        "\n" +
                        "# Change current folder to script dir (important !)\n" +
                        "cd $SCRIPT_DIR\n"+
                        "\n" +
                        "# Avoid sleeps everywhere\n" +
                        "export NO_SLEEP=true\n" +
                        "\n"+
                        "# Set test mode\n" +
                        "export TEST_MODE=true\n" +
                        "\n"+
                        "# Using local commands\n" +
                        "export PATH=$SCRIPT_DIR:$PATH\n");

                // inject custom topology loading
                scriptContent = scriptContent.replace(". /etc/eskimo_topology.sh", ". " + jailPath + "/eskimo-topology.sh");

                scriptContent = scriptContent.replace(". /host_etc/eskimo_topology.sh", ". " + jailPath + "/eskimo-topology.sh");

                copyResource(scriptToExecute, jailPath, scriptContent);
            }
        }

    }

    protected final void assertMarathonCommands() throws IOException {
        //System.err.println (setupLogs);

        String curlLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_curl"));
        if (StringUtils.isNotBlank(curlLogs)) {

            //System.err.println (curlLogs);

            int indexOfDelete = curlLogs.indexOf("-XDELETE http://192.168.10.11:28080/v2/apps/" + getServiceName());
            assertTrue(indexOfDelete > -1);

            int indexOfDeploy = curlLogs.indexOf("-X POST -H Content-Type: application/json -d @" + getServiceName() + ".marathon.json http://192.168.10.11:28080/v2/apps", indexOfDelete);
            assertTrue(indexOfDeploy > -1);

        } else {
            fail ("No curl manipulations found");
        }
    }

    protected final void assertSystemDInstallation() throws IOException {
        //System.err.println (setupLogs);

        String sudoLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_sudo"));
        if (StringUtils.isNotBlank(sudoLogs)) {

            //System.err.println(sudoLogs);
            assertTrue(sudoLogs.contains("cp " + getJailPath() + "/" + getServiceName() + ".service /lib/systemd/system/"));

            int indexOfDaemonReload = sudoLogs.indexOf("systemctl daemon-reload");
            assertTrue(indexOfDaemonReload > -1);

            int indexOfStatusFirst = sudoLogs.indexOf("systemctl status " + getServiceName(), indexOfDaemonReload + 1);
            assertTrue(indexOfStatusFirst > -1);

            int indexOfStatusSecond = sudoLogs.indexOf("systemctl status " + getServiceName(), indexOfStatusFirst + 1);
            assertTrue(indexOfStatusSecond > -1);

            int indexOfStart = sudoLogs.indexOf("systemctl start " + getServiceName(), indexOfStatusSecond + 1);
            assertTrue(indexOfStart > -1);

            int indexOfStatusThird = sudoLogs.indexOf("systemctl status " + getServiceName(), indexOfStart + 1);
            assertTrue(indexOfStatusThird > -1);

            int indexOfEnable = sudoLogs.indexOf("systemctl enable " + getServiceName(), indexOfStatusThird + 1);
            assertTrue(indexOfEnable > -1);


        } else {
            fail ("Expected to find sudo logs in .log_sudo");
        }

        String systemctlLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_systemctl"));
        if (StringUtils.isNotBlank(systemctlLogs)) {

            //System.err.println(systemctlLogs);

            assertTrue(systemctlLogs.contains("show -p SubState " + getServiceName()));

        } else {
            fail ("Expected to find systemctl logs in .log_systemctl");
        }
    }

    protected final void assertSystemDServiceDockerCommands() throws IOException {
        //System.err.println(setupLogs);
        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"));
        if (StringUtils.isNotBlank(dockerLogs)) {

            //System.err.println (dockerLogs);

            int indexOfImagesQ = dockerLogs.indexOf("images -q eskimo:" + getTemplateName() + "_template");
            assertTrue(indexOfImagesQ > -1);

            int indexOfLoad = dockerLogs.indexOf("load", indexOfImagesQ + 1);
            assertTrue(indexOfLoad > -1);

            int indexOfPs = dockerLogs.indexOf("ps -a -q -f name=" + getServiceName(), indexOfLoad + 1);
            assertTrue(indexOfPs > -1);

            int indexOfBuild = dockerLogs.indexOf("build --iidfile id_file --tag eskimo:" + getServiceName() + " .", indexOfPs + 1);
            assertTrue(indexOfBuild > -1);

            int indexOfCommit = dockerLogs.indexOf("commit " + getServiceName() + " eskimo:" + getServiceName(), indexOfBuild + 1);
            assertTrue(indexOfCommit > -1);

            int indexOfStop = dockerLogs.indexOf("stop " + getServiceName(), indexOfCommit + 1);
            assertTrue(indexOfStop > -1);

            int indexOfRm = dockerLogs.indexOf("container rm " + getServiceName(), indexOfStop + 1);
            assertTrue(indexOfRm > -1);

        } else {
            fail ("No docker manipulations found");
        }
    }

    protected final void assertMarathonServiceDockerCommands() throws IOException {
        //System.err.println(setupLogs);
        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"));
        if (StringUtils.isNotBlank(dockerLogs)) {

            //System.err.println (dockerLogs);

            int indexOfImagesQ = dockerLogs.indexOf("images -q eskimo:" + getTemplateName() + "_template");
            assertTrue(indexOfImagesQ > -1);

            int indexOfLoad = dockerLogs.indexOf("load", indexOfImagesQ + 1);
            assertTrue(indexOfLoad > -1);

            int indexOfPs = dockerLogs.indexOf("ps -a -q -f name=" + getServiceName(), indexOfLoad + 1);
            assertTrue(indexOfPs > -1);

            int indexOfBuild = dockerLogs.indexOf("build --iidfile id_file --tag eskimo:" + getServiceName() + " .", indexOfPs + 1);
            assertTrue(indexOfBuild > -1);

            int indexOfCommit = dockerLogs.indexOf("commit " + getServiceName() + " eskimo:" + getServiceName(), indexOfBuild + 1);
            assertTrue(indexOfCommit > -1);

            int indexOfStop = dockerLogs.indexOf("stop " + getServiceName(), indexOfCommit + 1);
            assertTrue(indexOfStop > -1);

            int indexOfRm = dockerLogs.indexOf("container rm " + getServiceName(), indexOfStop + 1);
            assertTrue(indexOfRm > -1);

            int indexOfTag = dockerLogs.indexOf("tag eskimo:" + getServiceName() + " marathon.registry:5000/" + getServiceName(), indexOfRm + 1);
            assertTrue(indexOfTag > -1);

            int indexOfPush = dockerLogs.indexOf("push marathon.registry:5000/" + getServiceName(), indexOfTag + 1);
            assertTrue(indexOfPush > -1);

            int indexOfImageRm = dockerLogs.indexOf("image rm eskimo:" + getServiceName(), indexOfTag + 1);
            assertTrue(indexOfImageRm > -1);


        } else {
            fail ("No docker manipulations found");
        }
    }

    protected final void assertTestConfFileUpdate() throws Exception {
        String testFileConfResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getCaemlCaseServiceName()+"SetupShellTest/testFile.conf.result"));
        if (StringUtils.isNotBlank(testFileConfResult)) {

            File updatedTestConfFilePath = new File (getJailPath() + "/testFile.conf");
            String updatedTestConfFile = FileUtils.readFile(updatedTestConfFilePath);

            assertEquals(testFileConfResult.trim(), updatedTestConfFile.trim());

        } else {
            fail ("file 'testFile.conf.result' is missing in " + getCaemlCaseServiceName()+"SetupShellTest/");
        }
    }

    protected abstract String getServiceName();

    protected String getTemplateName() {
        return getServiceName();
    }

    protected abstract void copyScripts(String jailPath) throws IOException;

    protected abstract String[] getScriptsToExecute();

    protected String[] getScriptsToEnhance() {
        return getScriptsToExecute();
    }

    protected final String executeScripts(String jailPath) throws ProcessHelper.ProcessHelperException {
        StringBuilder resultBuilder = new StringBuilder();
        for (String scriptToExecute: getScriptsToExecute()) {
            logger.info ("Executing " + scriptToExecute);
            resultBuilder.append(ProcessHelper.exec(new String[]{"bash", jailPath + "/" + scriptToExecute}, true));
        }
        return resultBuilder.toString();
    }

    /**
     * @return the path of the new jail where setup has to be executed
     */
    String createJail() throws Exception {
        File tempFile = File.createTempFile("setupshell_", "_test");
        tempFile.delete();
        tempFile.mkdir();

        // copy bash and everything bash requires to jail bin
        createLoggingExecutable("cp", tempFile.getAbsolutePath());
        createLoggingExecutable("gunzip", tempFile.getAbsolutePath());
        createLoggingExecutable("curl", tempFile.getAbsolutePath());
        createLoggingExecutable("mkdir", tempFile.getAbsolutePath());
        createLoggingExecutable("useradd", tempFile.getAbsolutePath());
        createLoggingExecutable("chown", tempFile.getAbsolutePath());
        createLoggingExecutable("chmod", tempFile.getAbsolutePath());
        createLoggingExecutable("curl", tempFile.getAbsolutePath());
        createLoggingExecutable("ln", tempFile.getAbsolutePath());
        createLoggingExecutable("rm", tempFile.getAbsolutePath());
        createLoggingExecutable("bash", tempFile.getAbsolutePath());
        createLoggingExecutable("systemctl", tempFile.getAbsolutePath());
        createLoggingExecutable("echo", tempFile.getAbsolutePath());
        createLoggingExecutable("touch", tempFile.getAbsolutePath());

        createDummyExecutable("id", tempFile.getAbsolutePath());
        createDummyExecutable("docker", tempFile.getAbsolutePath());
        createDummyExecutable("sed", tempFile.getAbsolutePath());
        createDummyExecutable("sudo", tempFile.getAbsolutePath());

        return tempFile.getAbsolutePath();
    }

    private void createLoggingExecutable(String command, String targetDir) throws Exception {

        File targetPath = new File (targetDir + "/" + command);
        FileUtils.writeFile(targetPath, "" +
                "#/bin/bash\n" +
                "\n" +
                "echo \"$@\" >> .log_" + command + "\n");

        ProcessHelper.exec("chmod 755 " + targetPath, true);
    }

    private void createDummyExecutable(String script, String targetDir) throws Exception {
        File targetPath = createResourceFile(script, targetDir);
        ProcessHelper.exec("chmod 755 " + targetPath, true);
    }

    private File createResourceFile(String resourceFile, String targetDir) throws IOException, FileException {
        String resourceString = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("AbstractSetupShellTest/" + resourceFile));

        return copyResource(resourceFile, targetDir, resourceString);
    }

    private File copyResource(String resourceFile, String targetDir, String resourceString) throws FileException {
        File targetPath = new File (targetDir + "/" + resourceFile);
        FileUtils.writeFile(targetPath, resourceString);

        return targetPath;
    }

    public String getCaemlCaseServiceName() {
        return StringUtils.toCamelCase(getServiceName());
    }

}



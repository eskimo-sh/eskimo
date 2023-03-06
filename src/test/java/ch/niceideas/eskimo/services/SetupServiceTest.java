/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */

package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.SetupCommand;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.ApplicationStatusServiceTestImpl;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import ch.niceideas.eskimo.test.services.OperationsMonitoringServiceTestImpl;
import ch.niceideas.eskimo.test.testwrappers.SetupServiceUnderTest;
import ch.niceideas.eskimo.utils.OSDetector;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({
        "no-web-stack",
        "setup-under-test",
        "test-conf",
        "test-system",
        "test-operation",
        "test-operations",
        "test-app-status",
        "test-services"})
public class SetupServiceTest {

    private static final Logger logger = Logger.getLogger(SetupServiceTest.class);

    @Autowired
    private SetupServiceUnderTest setupService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ApplicationStatusServiceTestImpl applicationStatusServiceTest;

    @Autowired
    private OperationsMonitoringServiceTestImpl operationsMonitoringServiceTest;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private NotificationService notificationService;

    private String setupConfig = null;

    private File tempConfigStoragePath = null;
    private File tempPackagesDistribPath = null;
    private File tempPackagesDevPath = null;

    private static final String kubePackages = "kube";

    @BeforeEach
    public void setUp() throws Exception {

        SecurityContextHelper.loginAdmin();
        operationsMonitoringServiceTest.endCommand(true);

        setupConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SetupServiceTest/setupConfig.json"), StandardCharsets.UTF_8);

        String packagesVersionFile = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SetupServiceTest/eskimo_packages_versions.json"), StandardCharsets.UTF_8);
        setupService.setPackagesVersionFile(packagesVersionFile);

        FileUtils.delete(new File ("/tmp/setupConfigTest"));

        tempConfigStoragePath = File.createTempFile("test_setup_service", "folder");
        assertTrue (tempConfigStoragePath.delete());

        tempPackagesDistribPath = File.createTempFile("test_setup_service_distrib", "folder");
        assertTrue (tempPackagesDistribPath.delete());

        tempPackagesDevPath = File.createTempFile("test_setup_service_dev", "folder");
        assertTrue (tempPackagesDevPath.delete());
        assertTrue (tempPackagesDevPath.mkdir());

        setupService.setConfigStoragePathInternal(tempConfigStoragePath.getCanonicalPath());
        setupService.setPackageDistributionPath(tempPackagesDistribPath.getCanonicalPath());
        setupService.setPackagesDevPathForTests(tempPackagesDevPath.getCanonicalPath());

        setupService.reset();
    }

    @AfterEach
    public void tearDown() throws Exception {
        FileUtils.delete(tempConfigStoragePath);
        FileUtils.delete(tempPackagesDistribPath);
        FileUtils.delete(tempPackagesDevPath);
    }

    @Test
    public void testParseVersion() {

        Pair<String, String> version = setupService.parseVersion("docker_template_base-eskimo_0.2_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<> ("0.2", "1"), version);

        version = setupService.parseVersion("docker_template_gluster_debian_09_stretch_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<> ("debian_09_stretch", "1"), version);

        version = setupService.parseVersion("docker_template_logstash_6.8.3_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<> ("6.8.3", "1"), version);

        version = setupService.parseVersion("docker_template_kube-master_1.8.1_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<> ("1.8.1", "1"), version);

        version = setupService.parseVersion("eskimo_kube-debian_1.11.0_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<> ("1.11.0", "1"), version);

        version = setupService.parseVersion("eskimo_kube_1.23.5_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<> ("1.23.5", "1"), version);
    }

    @Test
    public void testSaveAndPrepareSetup_build() throws Exception {

        SetupCommand command = setupService.saveAndPrepareSetup(setupConfig);

        JsonWrapper setupConfigWrapper = configurationServiceTest.loadSetupConfig();

        assertEquals("/tmp/setupConfigTest", setupConfigWrapper.getValueForPathAsString("setup_storage"));
        assertEquals("eskimo", setupConfigWrapper.getValueForPathAsString(SetupService.SSH_USERNAME_FIELD));
        assertEquals("ssh_key", setupConfigWrapper.getValueForPathAsString("filename-ssh-key"));
        assertEquals("build", setupConfigWrapper.getValueForPathAsString("setup-kube-origin"));
        assertEquals("build", setupConfigWrapper.getValueForPathAsString("setup-services-origin"));

        assertNotNull(command);

        assertEquals(1, command.getBuildKube().size());
        assertEquals(12, command.getBuildPackage().size());

        assertEquals("kube", String.join(",", command.getBuildKube()));
        assertEquals("base-eskimo," +
                "cluster-manager," +
                "distributed-time," +
                "distributed-filesystem," +
                "cluster-master," +
                "cluster-dashboard," +
                "calculator," +
                "database," +
                "database-manager," +
                "broker," +
                "broker-manager," +
                "user-console", String.join(",", command.getBuildPackage()));

        assertEquals(0, command.getDownloadKube().size());
        assertEquals(0, command.getDownloadPackages().size());
    }

    @Test
    public void testSaveAndPrepareSetup_download() throws Exception {

        JsonWrapper initConfig = new JsonWrapper(setupConfig);
        initConfig.setValueForPath("setup-kube-origin", "download");
        initConfig.setValueForPath("setup-services-origin", "download");

        setupService.setBuildVersion("1.0");

        SetupCommand command = setupService.saveAndPrepareSetup(initConfig.getFormattedValue());

        JsonWrapper setupConfigWrapper = configurationServiceTest.loadSetupConfig();

        assertEquals("/tmp/setupConfigTest", setupConfigWrapper.getValueForPathAsString("setup_storage"));
        assertEquals("eskimo", setupConfigWrapper.getValueForPathAsString(SetupService.SSH_USERNAME_FIELD));
        assertEquals("ssh_key", setupConfigWrapper.getValueForPathAsString("filename-ssh-key"));
        assertEquals("download", setupConfigWrapper.getValueForPathAsString("setup-kube-origin"));
        assertEquals("download", setupConfigWrapper.getValueForPathAsString("setup-services-origin"));

        assertNotNull(command);

        assertEquals(0, command.getBuildKube().size());
        assertEquals(0, command.getBuildPackage().size());

        assertEquals(1, command.getDownloadKube().size());
        assertEquals(12, command.getDownloadPackages().size());

        assertEquals("kube_1.23.5_1", String.join(",", command.getDownloadKube()));
        assertEquals("base-eskimo_0.2_1," +
                "cluster-manager_debian_09_stretch_1," +
                "distributed-time_debian_09_stretch_1," +
                "distributed-filesystem_debian_09_stretch_1," +
                "cluster-master_1.8.1_1," +
                "cluster-dashboard_2.5.1_1," +
                "calculator_2.4.4_1," +
                "database_6.8.3_1," +
                "database-manager_0.8.4_1," +
                "broker_2.2.0_1," +
                "broker-manager_2.0.0.2_1," +
                "user-console_0.9.0_1",
                String.join(",", command.getDownloadPackages()));
    }

    @Test
    public void testEnsureSetupCompleted() throws Exception {

        setupService.saveAndPrepareSetup(setupConfig);

        SetupException exception = assertThrows(SetupException.class, setupService::ensureSetupCompleted);
        assertEquals(
                "Following services are missing and need to be downloaded or built " +
                        "base-eskimo, " +
                        "broker, " +
                        "broker-manager, " +
                        "calculator, " +
                        "cluster-dashboard, " +
                        "cluster-manager, " +
                        "cluster-master, " +
                        "database, " +
                        "database-manager, " +
                        "distributed-filesystem, " +
                        "distributed-time, " +
                        "kube, " +
                        "user-console",
                exception.getMessage());

        // Create docker images packages
        for (String service : setupService.getPackagesToBuild().split(",")) {
            FileUtils.writeFile(new File(tempPackagesDistribPath + "/docker_template_" + service + "_0.0.1_1.tar.gz"), "DUMMY");
        }

        exception = assertThrows(SetupException.class, setupService::ensureSetupCompleted);
        assertEquals(
                "Following services are missing and need to be downloaded or built kube",
                exception.getMessage());

        // Create Kubernetes packages
        for (String kubePackage : kubePackages.split(",")) {
            FileUtils.writeFile(new File(tempPackagesDistribPath + "/eskimo_" + kubePackage + "_1.23_1.tar.gz"), "DUMMY");
        }

        // no exception expected anymore
        setupService.ensureSetupCompleted();
    }

    @Test
    public void testBuildPackage() throws Exception {

        assumeTrue(OSDetector.isUnix());

        File packageDevPathTest = File.createTempFile("test_setup_service_package_dev", "folder");
        assertTrue (packageDevPathTest.delete());
        assertTrue (packageDevPathTest.mkdirs());

        setupService.setPackagesDevPathForTests(packageDevPathTest.getAbsolutePath());
        setupService.setPackageDistributionPath(packageDevPathTest.getAbsolutePath());

        FileUtils.writeFile(new File (packageDevPathTest.getAbsolutePath() + "/build.sh"),
                "#!/bin/bash\n" +
                "echo $@\n");

        SetupCommand setupCommand = SetupCommand.create(new JsonWrapper(setupConfig), setupService, servicesDefinition);
        operationsMonitoringServiceTest.startCommand(setupCommand);

        setupService.buildPackage("cerebro");

        List<String> messages = operationsMonitoringServiceTest.getNewMessages(
                new SetupCommand.SetupOperationId(SetupCommand.SetupOperation.BUILD, "cerebro"), 0);

        assertEquals("--> Done : Build of package cerebro\n" +
                        "-------------------------------------------------------------------------------\n" +
                        "--> Completed Successfuly.",
                String.join(",", messages));

        FileUtils.delete(packageDevPathTest);

        operationsMonitoringServiceTest.endCommand(true);
    }

    @Test
    public void testDownloadPackage() throws Exception {

        File packageDevPathTest = File.createTempFile("test_setup_service_package_dev", "folder");
        assertTrue (packageDevPathTest.delete());
        assertTrue (packageDevPathTest.mkdirs());

        setupService.setPackageDistributionPath(packageDevPathTest.getAbsolutePath());

        JsonWrapper setupConfigJson = new JsonWrapper(setupConfig);
        setupConfigJson.setValueForPath("setup-kube-origin", "download");
        setupConfigJson.setValueForPath("setup-services-origin", "download");

        setupService.setBuildVersion("1.0");

        SetupCommand setupCommand = SetupCommand.create(setupConfigJson, setupService, servicesDefinition);
        operationsMonitoringServiceTest.startCommand(setupCommand);

        setupService.downloadPackage("cerebro_0.8.4_1", "cerebro_0.8.4_1.tgz");

        File[] files = packageDevPathTest.listFiles();
        assertNotNull(files);
        assertEquals (1, files.length);
        assertEquals("cerebro_0.8.4_1.tgz", files[0].getName());
        assertEquals("TEST DOWNLOADED CONTENT", FileUtils.readFile(files[0]));

        FileUtils.delete(packageDevPathTest);
    }

    @Test
    public void testReadConfigStoragePath() throws Exception {

        File storagePathTest = File.createTempFile("test_setup_storage", "folder");
        assertTrue (storagePathTest.delete());
        assertTrue (storagePathTest.mkdirs());

        setupService.setStoragePathConfDir(storagePathTest.getAbsolutePath());

        assertNull (setupService.readConfigStoragePath());

        FileUtils.writeFile(new File (storagePathTest, "storagePath.conf"), "/tmp/test");

        assertEquals ("/tmp/test", setupService.readConfigStoragePath());

        FileUtils.delete(storagePathTest);
    }

    @Test
    public void testFillInPackages() {

        JsonWrapper packagesVersion = new JsonWrapper(new JSONObject(new HashMap<String, Object>(){{
            put ("cerebro", new JSONObject(new HashMap<String, Object>(){{
                put ("software", "1.1");
                put ("distribution", "1");
            }}));
            put ("elasticsearch", new JSONObject(new HashMap<String, Object>(){{
                put ("software", "2.2");
                put ("distribution", "2");
            }}));
            put ("kibana", new JSONObject(new HashMap<String, Object>(){{
                put ("software", "3.3");
                put ("distribution", "3");
            }}));
        }}));
        Set<String> downloadPackages = new HashSet<>();
        Set<String> missingServices = new HashSet<>(){{
            add("cerebro");
            add("elasticsearch");
            add("kibana");
        }};

        setupService.fillInPackages(downloadPackages, packagesVersion, missingServices);

        assertEquals(3, downloadPackages.size());

        List<String> sorted = new ArrayList<>(downloadPackages);
        Collections.sort(sorted);

        assertEquals("cerebro_1.1_1", sorted.get(0));
        assertEquals("elasticsearch_2.2_2", sorted.get(1));
        assertEquals("kibana_3.3_3", sorted.get(2));
    }

    @Test
    public void testPrepareSetup() throws Exception {

        JsonWrapper setupConfigWrapper =  new JsonWrapper(setupConfig);

        // 1. test build strategy

        Set<String> downloadPackages = new HashSet<>();
        Set<String> buildPackage = new HashSet<>();
        Set<String> downloadKube = new HashSet<>();
        Set<String> buildKube = new HashSet<>();
        Set<String> packageUpdate = new HashSet<>();

        setupService.prepareSetup(setupConfigWrapper, downloadPackages, buildPackage, downloadKube, buildKube, packageUpdate);

        assertEquals(12, buildPackage.size());
        assertEquals(1, buildKube.size());

        // 2. test download strategy
        setupConfigWrapper.setValueForPath("setup-kube-origin", "download");
        setupConfigWrapper.setValueForPath("setup-services-origin", "download");

        downloadPackages = new HashSet<>();
        buildPackage = new HashSet<>();
        downloadKube = new HashSet<>();
        buildKube = new HashSet<>();
        packageUpdate = new HashSet<>();

        setupService.setBuildVersion("1.0");

        setupService.prepareSetup(setupConfigWrapper, downloadPackages, buildPackage, downloadKube, buildKube, packageUpdate);

        assertEquals(12, downloadPackages.size());
        assertEquals(1, downloadKube.size());
    }

    @Test
    public void testCompareSoftwareVersion() {

        assertEquals(0, setupService.compareSoftwareVersion("1.1.1", "1.1.1"));
        assertEquals(0, setupService.compareSoftwareVersion("1.1_1", "1.1_1"));
        assertEquals(0, setupService.compareSoftwareVersion("1.1_a", "1.1_a"));
        assertEquals(0, setupService.compareSoftwareVersion("8.a1", "8.a1"));
        assertEquals(0, setupService.compareSoftwareVersion("abc", "abc"));
        assertEquals(0, setupService.compareSoftwareVersion("a.b.1", "a.b.1"));
        assertEquals(0, setupService.compareSoftwareVersion("1", "1"));

        assertEquals(1, setupService.compareSoftwareVersion("1.1.2", "1.1.1"));
        assertEquals(1, setupService.compareSoftwareVersion("1.1.10", "1.1.9"));
        assertEquals(1, setupService.compareSoftwareVersion("1.10.1", "1.9.1"));
        assertEquals(1, setupService.compareSoftwareVersion("abd", "abc"));
        assertEquals(1, setupService.compareSoftwareVersion("1.1-abd", "1.1-abc"));
        assertEquals(1, setupService.compareSoftwareVersion("2", "1"));
        assertEquals(1, setupService.compareSoftwareVersion("1.1", "1"));

        assertEquals(-1, setupService.compareSoftwareVersion("1.1.1", "1.1.2"));
        assertEquals(-1, setupService.compareSoftwareVersion("1.1.9", "1.1.10"));
        assertEquals(-1, setupService.compareSoftwareVersion("1.9.1", "1.10.1"));
        assertEquals(-1, setupService.compareSoftwareVersion("abc", "abd"));
        assertEquals(-1, setupService.compareSoftwareVersion("1.1-abc", "1.1-abd"));
        assertEquals(-1, setupService.compareSoftwareVersion("1", "2"));
        assertEquals(-1, setupService.compareSoftwareVersion("1", "1.1"));
    }

    @Test
    public void testApplySetupHandleUpdates() throws Exception {

        setupService.tweakLastVersionForTests();

        applicationStatusServiceTest.setSnapshot(true);

        JsonWrapper setupConfigWrapper =  new JsonWrapper(setupConfig);
        setupConfigWrapper.setValueForPath("setup-kube-origin", "download");
        setupConfigWrapper.setValueForPath("setup-services-origin", "download");

        setupService.setBuildVersion("2.0");

        setupService.saveAndPrepareSetup(setupConfigWrapper.getFormattedValue());

        setupService.applySetup(SetupCommand.create(setupConfigWrapper, setupService, servicesDefinition));

        assertEquals(9, setupService.getDownloadPackageList().size()); // all software version below 1.0 are not updated (base-eskimo, etc.)
        assertEquals(0, setupService.getBuiltPackageList().size());

        Collections.sort(setupService.getDownloadPackageList());
        assertEquals(
                "docker_template_broker-manager_2.0.0.2_1.tar.gz," +
                        " docker_template_broker_2.2.0_1.tar.gz," +
                        " docker_template_calculator_2.4.4_1.tar.gz," +
                        " docker_template_cluster-dashboard_2.5.1_1.tar.gz," +
                        " docker_template_cluster-manager_debian_09_stretch_1.tar.gz," +
                        " docker_template_cluster-master_1.8.1_1.tar.gz," +
                        " docker_template_database_6.8.3_1.tar.gz," +
                        " docker_template_distributed-filesystem_debian_09_stretch_1.tar.gz," +
                        " docker_template_distributed-time_debian_09_stretch_1.tar.gz",
            String.join(", ", setupService.getDownloadPackageList()));
    }

    @Test
    public void testApplySetupHandleUpdates_emptyVersionFile() throws Exception {

        setupService.setPackagesVersionFile("{}");

        setupService.tweakLastVersionForTests();

        applicationStatusServiceTest.setSnapshot(true);

        JsonWrapper setupConfigWrapper =  new JsonWrapper(setupConfig);
        setupConfigWrapper.setValueForPath("setup-kube-origin", "download");
        setupConfigWrapper.setValueForPath("setup-services-origin", "download");

        setupService.setBuildVersion("2.0");

        setupService.saveAndPrepareSetup(setupConfigWrapper.getFormattedValue());

        setupService.applySetup(SetupCommand.create(setupConfigWrapper, setupService, servicesDefinition));

        // 13 updated packages
        assertEquals(0, setupService.getDownloadPackageList().size()); // all software version below 1.0 are not updated (base-eskimo, etc.)
        assertEquals(0, setupService.getBuiltPackageList().size());

        // the 13 warnings should have been posted
        assertEquals (36, notificationService.fetchElements(0).getKey());
    }

    @Test
    public void testApplySetupDownload_unsupportedSnapshot() throws Exception {

        JsonWrapper setupConfigWrapper =  new JsonWrapper(setupConfig);
        setupConfigWrapper.setValueForPath("setup-kube-origin", "download");
        setupConfigWrapper.setValueForPath("setup-services-origin", "download");

        setupService.setBuildVersion("1.0");
        SetupCommand command = SetupCommand.create(setupConfigWrapper, setupService, servicesDefinition);

        setupService.setBuildVersion("1.0-SNAPSHOT");

        setupService.saveAndPrepareSetup(setupConfig);

        assertThrows(SetupException.class,
                () -> setupService.applySetup(command),
                "Downloading packages is not supported on development version (SNAPSHOT)");
    }

    @Test
    public void testApplySetupDownload() throws Exception {

        JsonWrapper setupConfigWrapper =  new JsonWrapper(setupConfig);
        setupConfigWrapper.setValueForPath("setup-kube-origin", "download");
        setupConfigWrapper.setValueForPath("setup-services-origin", "download");

        setupService.dontBuild();

        setupService.setBuildVersion("1.0");

        setupService.saveAndPrepareSetup(setupConfig);

        setupService.applySetup(SetupCommand.create(setupConfigWrapper, setupService, servicesDefinition));

        assertEquals(13, setupService.getDownloadPackageList().size());
        assertEquals(0, setupService.getBuiltPackageList().size());

        Collections.sort(setupService.getDownloadPackageList());
        assertEquals(
                    "docker_template_base-eskimo_0.2_1.tar.gz, " +
                            "docker_template_broker-manager_2.0.0.2_1.tar.gz," +
                            " docker_template_broker_2.2.0_1.tar.gz," +
                            " docker_template_calculator_2.4.4_1.tar.gz," +
                            " docker_template_cluster-dashboard_2.5.1_1.tar.gz," +
                            " docker_template_cluster-manager_debian_09_stretch_1.tar.gz," +
                            " docker_template_cluster-master_1.8.1_1.tar.gz," +
                            " docker_template_database-manager_0.8.4_1.tar.gz," +
                            " docker_template_database_6.8.3_1.tar.gz," +
                            " docker_template_distributed-filesystem_debian_09_stretch_1.tar.gz," +
                            " docker_template_distributed-time_debian_09_stretch_1.tar.gz," +
                            " docker_template_user-console_0.9.0_1.tar.gz," +
                            " eskimo_kube_1.23.5_1.tar.gz",
                String.join(", ", setupService.getDownloadPackageList()));
    }

    @Test
    public void testApplySetupBuild() throws Exception {

        SetupCommand command = setupService.saveAndPrepareSetup(setupConfig);

        setupService.dontBuild();

        setupService.applySetup(command);

        assertEquals(13, setupService.getBuiltPackageList().size());
        assertEquals(0, setupService.getDownloadPackageList().size());

        Collections.sort(setupService.getBuiltPackageList());
        assertEquals(
                    "base-eskimo, broker, broker-manager, calculator, cluster-dashboard, cluster-manager, " +
                            "cluster-master, database, database-manager, distributed-filesystem, distributed-time, " +
                            "kube, user-console",
                String.join(", ", setupService.getBuiltPackageList()));

    }

    @Test
    public void testFindLastPackageFile() throws Exception {

        File packagePath = File.createTempFile("package_path", "test");
        assertTrue (packagePath.delete());
        assertTrue (packagePath.mkdirs());

        assertTrue (new File (packagePath, "docker_template_grafana_1.2.0_1.tar.gz").createNewFile());
        assertTrue (new File (packagePath, "docker_template_grafana_1.2.0_2.tar.gz").createNewFile());

        assertTrue (new File (packagePath, "docker_template_grafana_1.3.1_1.tar.gz").createNewFile());
        assertTrue (new File (packagePath, "docker_template_grafana_1.3.1_2.tar.gz").createNewFile());
        assertTrue (new File (packagePath, "docker_template_grafana_1.3.1_3.tar.gz").createNewFile());

        setupService.setPackageDistributionPath(packagePath.getAbsolutePath());

        String lastPackage = setupService.findLastPackageFile("docker_template_", "grafana");

        assertEquals ("docker_template_grafana_1.3.1_3.tar.gz", lastPackage);

        FileUtils.delete(packagePath);
    }

}

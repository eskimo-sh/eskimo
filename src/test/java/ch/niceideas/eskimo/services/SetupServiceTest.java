/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.MessageLogger;
import ch.niceideas.eskimo.model.SetupCommand;
import ch.niceideas.eskimo.utils.OSDetector;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static org.junit.Assume.assumeTrue;
import static org.junit.jupiter.api.Assertions.*;

public class SetupServiceTest extends AbstractSystemTest {

    private static final Logger logger = Logger.getLogger(SetupServiceTest.class);

    private String setupConfig = null;

    private String packagesVersionFile = null;

    private File tempConfigStoragePath = null;

    private File tempPackagesDistribPath = null;

    private String packagesToBuild = "base-eskimo,ntp,zookeeper,gluster,flink,elasticsearch,cerebro,kibana,logstash,prometheus,grafana,kafka,kafka-manager,kube-master,k8s-dashboad,spark,zeppelin";

    private String k8sPackages = "kube";

    private ServicesDefinition sd = new ServicesDefinition();

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        sd.afterPropertiesSet();

        setupConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SetupServiceTest/setupConfig.json"));
        packagesVersionFile = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SetupServiceTest/eskimo_packages_versions.json"));
        FileUtils.delete(new File ("/tmp/setupConfigTest"));

        tempConfigStoragePath = File.createTempFile("test_setup_service", "folder");
        tempConfigStoragePath.delete();

        tempPackagesDistribPath = File.createTempFile("test_setup_service_distrib", "folder");
        tempPackagesDistribPath.delete();
    }

    SetupService createSetupService(SetupService setupService) throws IOException {

        File storagePathConfDir = File.createTempFile("eskimo_storage", "");
        storagePathConfDir.delete();
        storagePathConfDir.mkdirs();
        setupService.setStoragePathConfDir(storagePathConfDir.getCanonicalPath());

        setupService.setConfigStoragePathInternal(tempConfigStoragePath.getCanonicalPath());
        setupService.setPackageDistributionPath(tempPackagesDistribPath.getCanonicalPath());

        setupService.setSystemService(systemService);
        setupService.setServicesDefinition(sd);

        setupService.setPackagesToBuild(packagesToBuild);
        setupService.setK8sPackages(k8sPackages);

        setupService.setConfigurationService(configurationService);
        configurationService.setSetupService(setupService);

        setupService.setSystemOperationService(systemOperationService);

        setupService.setBuildVersion("1.0");

        setupService.setOperationsMonitoringService(operationsMonitoringService);

        setupService.setNotificationService(notificationService);

        return setupService;
    }

    @Test
    public void testParseVersion() throws Exception {

        Pair<String, String> version = setupService.parseVersion("docker_template_base-eskimo_0.2_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<> ("0.2", "1"), version);

        version = setupService.parseVersion("docker_template_gluster_debian_09_stretch_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<> ("debian_09_stretch", "1"), version);

        version = setupService.parseVersion("docker_template_logstash_6.8.3_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<> ("6.8.3", "1"), version);

        version = setupService.parseVersion("docker_template_mesos-master_1.8.1_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<> ("1.8.1", "1"), version);

        version = setupService.parseVersion("eskimo_mesos-debian_1.11.0_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<> ("1.11.0", "1"), version);

        version = setupService.parseVersion("eskimo_kube_1.23.5_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<> ("1.23.5", "1"), version);
    }

    @Test
    public void testSaveAndPrepareSetup_build() throws Exception {

        SetupService setupService = createSetupService(new SetupService());
        setupService.setApplicationStatusService(applicationStatusService);

        SetupCommand command = setupService.saveAndPrepareSetup(setupConfig);

        JsonWrapper setupConfigWrapper = new JsonWrapper(configurationService.loadSetupConfig());

        assertEquals("/tmp/setupConfigTest", setupConfigWrapper.getValueForPathAsString("setup_storage"));
        assertEquals("eskimo", setupConfigWrapper.getValueForPathAsString("ssh_username"));
        assertEquals("ssh_key", setupConfigWrapper.getValueForPathAsString("filename-ssh-key"));
        assertEquals("build", setupConfigWrapper.getValueForPathAsString("setup-k8s-origin"));
        assertEquals("build", setupConfigWrapper.getValueForPathAsString("setup-services-origin"));

        assertNotNull(command);

        assertEquals(3, command.getBuildMesos().size());
        assertEquals(16, command.getBuildPackage().size());

        assertEquals("mesos-debian,mesos-redhat,mesos-suse", String.join(",", command.getBuildMesos()));
        assertEquals("base-eskimo,elasticsearch,ntp,prometheus,zookeeper,gluster,kafka,mesos-master,logstash,flink,spark,cerebro,grafana,kibana,kafka-manager,zeppelin", String.join(",", command.getBuildPackage()));

        assertEquals(0, command.getDownloadMesos().size());
        assertEquals(0, command.getDownloadPackages().size());
    }

    @Test
    public void testSaveAndPrepareSetup_download() throws Exception {

        SetupService setupService = createSetupService(new SetupService() {
            @Override
            protected JsonWrapper loadRemotePackagesVersionFile() {
                return new JsonWrapper(packagesVersionFile);
            }
            @Override
            protected void dowloadFile(MessageLogger ml, File destinationFile, URL downloadUrl, String message) throws IOException {
                destinationFile.createNewFile();
                try {
                    FileUtils.writeFile(destinationFile, "TEST DOWNLOADED CONTENT");
                } catch (FileException e) {
                    logger.debug (e, e);
                    throw new IOException(e);
                }
            }
        });
        setupService.setApplicationStatusService(applicationStatusService);

        setupService.setBuildVersion("1.0");

        JsonWrapper initConfig = new JsonWrapper(setupConfig);
        initConfig.setValueForPath("setup-k8s-origin", "download");
        initConfig.setValueForPath("setup-services-origin", "download");

        SetupCommand command = setupService.saveAndPrepareSetup(initConfig.getFormattedValue());

        JsonWrapper setupConfigWrapper = new JsonWrapper(configurationService.loadSetupConfig());

        assertEquals("/tmp/setupConfigTest", setupConfigWrapper.getValueForPathAsString("setup_storage"));
        assertEquals("eskimo", setupConfigWrapper.getValueForPathAsString("ssh_username"));
        assertEquals("ssh_key", setupConfigWrapper.getValueForPathAsString("filename-ssh-key"));
        assertEquals("download", setupConfigWrapper.getValueForPathAsString("setup-k8s-origin"));
        assertEquals("download", setupConfigWrapper.getValueForPathAsString("setup-services-origin"));

        assertNotNull(command);

        assertEquals(0, command.getBuildMesos().size());
        assertEquals(0, command.getBuildPackage().size());

        assertEquals(3, command.getDownloadMesos().size());
        assertEquals(16, command.getDownloadPackages().size());

        assertEquals("mesos-debian_1.8.1_1,mesos-redhat_1.8.1_1,mesos-suse_1.8.1_1", String.join(",", command.getDownloadMesos()));
        assertEquals("base-eskimo_0.2_1,elasticsearch_6.8.3_1,ntp_debian_09_stretch_1,prometheus_2.10.0_1,zookeeper_debian_09_stretch_1,gluster_debian_09_stretch_1,kafka_2.2.0_1,mesos-master_1.8.1_1,logstash_6.8.3_1,flink_1.9.1_1,spark_2.4.4_1,cerebro_0.8.4_1,grafana_6.3.3_1,kibana_6.8.3_1,kafka-manager_2.0.0.2_1,zeppelin_0.9.0_1", String.join(",", command.getDownloadPackages()));
    }

    @Test
    public void testEnsureSetupCompleted() throws Exception {

        SetupService setupService = createSetupService(new SetupService());
        setupService.setApplicationStatusService(applicationStatusService);

        setupService.saveAndPrepareSetup(setupConfig);

        SetupException exception = assertThrows(SetupException.class, setupService::ensureSetupCompleted);
        assertEquals(
                "Following services are missing and need to be downloaded or built base-eskimo, cerebro, elasticsearch, " +
                        "flink, gluster, grafana, kafka, kafka-manager, kibana, logstash, mesos-debian, " +
                        "mesos-master, mesos-redhat, mesos-suse, ntp, prometheus, spark, zeppelin, zookeeper",
                exception.getMessage());

        // Create docker images packages
        for (String service : packagesToBuild.split(",")) {
            FileUtils.writeFile(new File(tempPackagesDistribPath + "/docker_template_" + service + "_0.0.1_1.tar.gz"), "DUMMY");
        }

        exception = assertThrows(SetupException.class, setupService::ensureSetupCompleted);
        assertEquals(
                "Following services are missing and need to be downloaded or built mesos-debian, mesos-redhat, mesos-suse",
                exception.getMessage());

        // Create Kubernetes packages
        for (String k8sPackage : k8sPackages.split(",")) {
            FileUtils.writeFile(new File(tempPackagesDistribPath + "/eskimo_" + k8sPackage + "_1.23_1.tar.gz"), "DUMMY");
        }

        // no exception expected anymore
        setupService.ensureSetupCompleted();
    }

    @Test
    public void testBuildPackage() throws Exception {

        assumeTrue(OSDetector.isUnix());

        File packageDevPathTest = File.createTempFile("test_setup_service_package_dev", "folder");
        packageDevPathTest.delete();
        packageDevPathTest.mkdirs();

        setupService.setPackagesDevPathForTests(packageDevPathTest.getAbsolutePath());
        setupService.setPackageDistributionPath(packageDevPathTest.getAbsolutePath());

        FileUtils.writeFile(new File (packageDevPathTest.getAbsolutePath() + "/build.sh"),
                "#!/bin/bash\n" +
                "echo $@\n");

        SetupCommand setupCommand = SetupCommand.create(new JsonWrapper(setupConfig), setupService, servicesDefinition);
        operationsMonitoringService.operationsStarted(setupCommand);

        setupService.buildPackage("cerebro");

        List<String> messages = operationsMonitoringService.getNewMessages(
                new SetupCommand.SetupOperationId(SetupCommand.TYPE_BUILD, "cerebro"), 0);
        //List<String> messages = messagingService.getSubList(0);
        assertEquals (5, messages.size());

        assertEquals("\n" +
                        "Build of package cerebro," +
                        "cerebro," +
                        "--> Done : Build of package cerebro," +
                        "-------------------------------------------------------------------------------," +
                        "--> Completed Successfuly.",
                String.join(",", messages));

        FileUtils.delete(packageDevPathTest);

        operationsMonitoringService.operationsFinished(true);
    }

    @Test
    public void testDownloadPackage() throws Exception {

        SetupService setupService = createSetupService(new SetupService() {
            @Override
            protected JsonWrapper loadRemotePackagesVersionFile() throws SetupException {
                return new JsonWrapper(packagesVersionFile);
            }
            @Override
            protected void dowloadFile(MessageLogger ml, File destinationFile, URL downloadUrl, String message) throws IOException {
                destinationFile.createNewFile();
                try {
                    FileUtils.writeFile(destinationFile, "TEST DOWNLOADED CONTENT");
                } catch (FileException e) {
                    logger.debug (e, e);
                    throw new IOException(e);
                }
            }
        });

        File packageDevPathTest = File.createTempFile("test_setup_service_package_dev", "folder");
        packageDevPathTest.delete();
        packageDevPathTest.mkdirs();

        setupService.setPackageDistributionPath(packageDevPathTest.getAbsolutePath());
        setupService.setBuildVersion("1.0");

        JsonWrapper setupConfigJson = new JsonWrapper(setupConfig);
        setupConfigJson.setValueForPath("setup-k8s-origin", "download");
        setupConfigJson.setValueForPath("setup-services-origin", "download");
        SetupCommand setupCommand = SetupCommand.create(setupConfigJson, setupService, servicesDefinition);
        operationsMonitoringService.operationsStarted(setupCommand);

        setupService.downloadPackage("cerebro_0.8.4_1");

        assertEquals (1, packageDevPathTest.listFiles().length);
        assertEquals("cerebro_0.8.4_1", packageDevPathTest.listFiles()[0].getName());
        assertEquals("TEST DOWNLOADED CONTENT", FileUtils.readFile(packageDevPathTest.listFiles()[0]));

        FileUtils.delete(packageDevPathTest);
    }

    @Test
    public void testReadConfigStoragePath() throws Exception {

        File storagePathTest = File.createTempFile("test_setup_storage", "folder");
        storagePathTest.delete();
        storagePathTest.mkdirs();

        setupService.setStoragePathConfDir(storagePathTest.getAbsolutePath());

        assertNull (setupService.readConfigStoragePath());

        FileUtils.writeFile(new File (storagePathTest, "storagePath.conf"), "/tmp/test");

        assertEquals ("/tmp/test", setupService.readConfigStoragePath());
    }

    @Test
    public void testFillInPackages() throws Exception {

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
        Set<String> missingServices = new HashSet<String>(){{
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

        SetupService setupService = createSetupService(new SetupService() {
            @Override
            protected JsonWrapper loadRemotePackagesVersionFile() throws SetupException {
                return new JsonWrapper(packagesVersionFile);
            }
        });

        setupService.setApplicationStatusService(applicationStatusService);

        JsonWrapper setupConfigWrapper =  new JsonWrapper(setupConfig);

        // 1. test build strategy

        Set<String> downloadPackages = new HashSet<>();
        Set<String> buildPackage = new HashSet<>();
        Set<String> downloadMesos = new HashSet<>();
        Set<String> buildMesos = new HashSet<>();
        Set<String> packageUpdate = new HashSet<>();

        setupService.prepareSetup(setupConfigWrapper, downloadPackages, buildPackage, downloadMesos, buildMesos, packageUpdate);

        assertEquals(16, buildPackage.size());
        assertEquals(3, buildMesos.size());

        // 2. test download strategy
        setupConfigWrapper.setValueForPath("setup-k8s-origin", "download");
        setupConfigWrapper.setValueForPath("setup-services-origin", "download");

        downloadPackages = new HashSet<>();
        buildPackage = new HashSet<>();
        downloadMesos = new HashSet<>();
        buildMesos = new HashSet<>();
        packageUpdate = new HashSet<>();

        setupService.prepareSetup(setupConfigWrapper, downloadPackages, buildPackage, downloadMesos, buildMesos, packageUpdate);

        assertEquals(16, downloadPackages.size());
        assertEquals(3, downloadMesos.size());
    }

    @Test
    public void testCompareSoftwareVersion() throws Exception {

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
    public void testApplySetupHandleUpdates_NumericOrder() throws Exception {

        final List<String> builtPackageList = new ArrayList<>();
        final List<String> downloadPackageList = new ArrayList<>();

        SetupService setupService = createSetupService(new SetupService() {
            @Override
            protected void buildPackage(String image) throws SetupException {
                builtPackageList.add (image);
            }
            @Override
            protected void downloadPackage(String fileName) throws SetupException {
                downloadPackageList.add (fileName);
            }
            @Override
            protected JsonWrapper loadRemotePackagesVersionFile() throws SetupException {
                JsonWrapper updateFile = new JsonWrapper(packagesVersionFile);
                updateFile.setValueForPath("flink.software", "1.9.0");
                return updateFile;
            }
            @Override
            void findMissingPackages(File packagesDistribFolder, Set<String> missingServices) {
                // No-Op
            }
            @Override
            void findMissingK8s(File packagesDistribFolder, Set<String> missingServices) {
                // No-Op
            }
            @Override
            Pair<File, Pair<String, String>> findLastVersion(String prefix, String packageName, File packagesDistribFolder) {
                return new Pair<>(new File ("package_" + packageName + ".tgz"), new Pair<>("1.10.1", "1"));
            }
        });

        setupService.setApplicationStatusService(new ApplicationStatusService() {
            public boolean isSnapshot() {
                return false;
            }
        });

        JsonWrapper setupConfigWrapper =  new JsonWrapper(setupConfig);
        setupConfigWrapper.setValueForPath("setup-k8s-origin", "download");
        setupConfigWrapper.setValueForPath("setup-services-origin", "download");

        setupService.setPackagesToBuild("flink");

        setupService.saveAndPrepareSetup(setupConfigWrapper.getFormattedValue());

        ServicesDefinition servicesDefinition = new ServicesDefinition();
        servicesDefinition.afterPropertiesSet();

        setupService.setServicesDefinition(servicesDefinition);

        setupService.applySetup(SetupCommand.create(setupConfigWrapper, setupService, servicesDefinition));

        // no update (installed flink is latest version !)
        assertEquals(0, downloadPackageList.size());
        assertEquals(0, builtPackageList.size());
    }

    @Test
    public void testApplySetupHandleUpdates() throws Exception {

        final List<String> builtPackageList = new ArrayList<>();
        final List<String> downloadPackageList = new ArrayList<>();

        SetupService setupService = createSetupService(new SetupService() {
            @Override
            protected void buildPackage(String image) throws SetupException {
                builtPackageList.add (image);
            }
            @Override
            protected void downloadPackage(String fileName) throws SetupException {
                downloadPackageList.add (fileName);
            }
            @Override
            protected JsonWrapper loadRemotePackagesVersionFile() throws SetupException {
                return new JsonWrapper(packagesVersionFile);
            }
            @Override
            void findMissingPackages(File packagesDistribFolder, Set<String> missingServices) {
                // No-Op
            }
            @Override
            void findMissingK8s(File packagesDistribFolder, Set<String> missingServices) {
                // No-Op
            }
            @Override
            Pair<File, Pair<String, String>> findLastVersion(String prefix, String packageName, File packagesDistribFolder) {
                return new Pair<>(new File ("package_" + packageName + ".tgz"), new Pair<>("1.0", "0"));
            }
        });

        setupService.setApplicationStatusService(new ApplicationStatusService() {
            public boolean isSnapshot() {
                return false;
            }
        });

        JsonWrapper setupConfigWrapper =  new JsonWrapper(setupConfig);
        setupConfigWrapper.setValueForPath("setup-k8s-origin", "download");
        setupConfigWrapper.setValueForPath("setup-services-origin", "download");

        setupService.saveAndPrepareSetup(setupConfigWrapper.getFormattedValue());

        ServicesDefinition servicesDefinition = new ServicesDefinition();
        servicesDefinition.afterPropertiesSet();

        setupService.setServicesDefinition(servicesDefinition);

        setupService.applySetup(SetupCommand.create(setupConfigWrapper, setupService, servicesDefinition));

        // 13 updated packages
        assertEquals(13, downloadPackageList.size()); // all software version below 1.0 are not updated (base-eskimo, etc.)
        assertEquals(0, builtPackageList.size());

        Collections.sort(downloadPackageList);
        assertEquals(
                "docker_template_elasticsearch_6.8.3_1.tar.gz, " +
                "docker_template_flink_1.9.1_1.tar.gz, " +
                "docker_template_gluster_debian_09_stretch_1.tar.gz, " +
                "docker_template_grafana_6.3.3_1.tar.gz, " +
                "docker_template_kafka-manager_2.0.0.2_1.tar.gz, " +
                "docker_template_kafka_2.2.0_1.tar.gz, " +
                "docker_template_kibana_6.8.3_1.tar.gz, " +
                "docker_template_logstash_6.8.3_1.tar.gz, " +
                "docker_template_mesos-master_1.8.1_1.tar.gz, " +
                "docker_template_ntp_debian_09_stretch_1.tar.gz, " +
                "docker_template_prometheus_2.10.0_1.tar.gz, " +
                "docker_template_spark_2.4.4_1.tar.gz, " +
                "docker_template_zookeeper_debian_09_stretch_1.tar.gz",
            String.join(", ", downloadPackageList));
    }


    @Test
    public void testApplySetupDownload_unsupportedSnapshot() throws Exception {

        final List<String> builtPackageList = new ArrayList<>();
        final List<String> downloadPackageList = new ArrayList<>();

        SetupService setupService = createSetupService(new SetupService() {
            @Override
            protected void buildPackage(String image) {
                builtPackageList.add (image);
            }
            @Override
            protected void downloadPackage(String fileName) {
                downloadPackageList.add (fileName);
            }
            @Override
            protected JsonWrapper loadRemotePackagesVersionFile() {
                return new JsonWrapper(packagesVersionFile);
            }
        });

        JsonWrapper setupConfigWrapper =  new JsonWrapper(setupConfig);
        setupConfigWrapper.setValueForPath("setup-k8s-origin", "download");
        setupConfigWrapper.setValueForPath("setup-services-origin", "download");

        setupService.setBuildVersion("1.0");
        SetupCommand command = SetupCommand.create(setupConfigWrapper, setupService, servicesDefinition);

        setupService.setBuildVersion("1.0-SNAPSHOT");

        setupService.saveAndPrepareSetup(setupConfig);

        ServicesDefinition servicesDefinition = new ServicesDefinition();
        servicesDefinition.afterPropertiesSet();

        setupService.setServicesDefinition(servicesDefinition);

        String errorResult = setupService.applySetup(command);

        assertEquals("{\n" +
                "  \"error\": \"Downloading packages is not supported on development version (SNAPSHOT)\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", errorResult);
    }

    @Test
    public void testApplySetupDownload() throws Exception {

        final List<String> builtPackageList = new ArrayList<>();
        final List<String> downloadPackageList = new ArrayList<>();

        SetupService setupService = createSetupService(new SetupService() {
            @Override
            protected void buildPackage(String image) {
                builtPackageList.add (image);
            }
            @Override
            protected void downloadPackage(String fileName) {
                downloadPackageList.add (fileName);
            }
            @Override
            protected JsonWrapper loadRemotePackagesVersionFile() {
                return new JsonWrapper(packagesVersionFile);
            }
        });

        JsonWrapper setupConfigWrapper =  new JsonWrapper(setupConfig);
        setupConfigWrapper.setValueForPath("setup-k8s-origin", "download");
        setupConfigWrapper.setValueForPath("setup-services-origin", "download");

        setupService.saveAndPrepareSetup(setupConfig);

        ServicesDefinition servicesDefinition = new ServicesDefinition();
        servicesDefinition.afterPropertiesSet();

        setupService.setServicesDefinition(servicesDefinition);

        setupService.applySetup(SetupCommand.create(setupConfigWrapper, setupService, servicesDefinition));

        assertEquals(19, downloadPackageList.size());
        assertEquals(0, builtPackageList.size());

        Collections.sort(downloadPackageList);
        assertEquals(
                    "docker_template_base-eskimo_0.2_1.tar.gz, " +
                    "docker_template_cerebro_0.8.4_1.tar.gz, " +
                    "docker_template_elasticsearch_6.8.3_1.tar.gz, " +
                    "docker_template_flink_1.9.1_1.tar.gz, " +
                    "docker_template_gluster_debian_09_stretch_1.tar.gz, " +
                    "docker_template_grafana_6.3.3_1.tar.gz, " +
                    "docker_template_kafka-manager_2.0.0.2_1.tar.gz, " +
                    "docker_template_kafka_2.2.0_1.tar.gz, " +
                    "docker_template_kibana_6.8.3_1.tar.gz, " +
                    "docker_template_logstash_6.8.3_1.tar.gz, " +
                    "docker_template_mesos-master_1.8.1_1.tar.gz, " +
                    "docker_template_ntp_debian_09_stretch_1.tar.gz, " +
                    "docker_template_prometheus_2.10.0_1.tar.gz, " +
                    "docker_template_spark_2.4.4_1.tar.gz, " +
                    "docker_template_zeppelin_0.9.0_1.tar.gz, " +
                    "docker_template_zookeeper_debian_09_stretch_1.tar.gz, " +
                    "eskimo_mesos-debian_1.8.1_1.tar.gz, " +
                    "eskimo_mesos-redhat_1.8.1_1.tar.gz, " +
                    "eskimo_mesos-suse_1.8.1_1.tar.gz",
                String.join(", ", downloadPackageList));
    }

    @Test
    public void testApplySetupBuild() throws Exception {

        final List<String> builtPackageList = new ArrayList<>();
        final List<String> downloadPackageList = new ArrayList<>();

        SetupService setupService = createSetupService(new SetupService() {
            @Override
            protected void buildPackage(String image) {
                builtPackageList.add (image);
            }
            @Override
            protected void downloadPackage(String fileName) {
                downloadPackageList.add (fileName);
            }
            @Override
            protected JsonWrapper loadRemotePackagesVersionFile() {
                return new JsonWrapper(packagesVersionFile);
            }
        });

        setupService.setApplicationStatusService(applicationStatusService);

        setupService.saveAndPrepareSetup(setupConfig);

        ServicesDefinition servicesDefinition = new ServicesDefinition();
        servicesDefinition.afterPropertiesSet();

        setupService.setServicesDefinition(servicesDefinition);

        setupService.applySetup(SetupCommand.create(new JsonWrapper(setupConfig), setupService, servicesDefinition));

        assertEquals(19, builtPackageList.size());
        assertEquals(0, downloadPackageList.size());

        Collections.sort(builtPackageList);
        assertEquals(
                    "base-eskimo, cerebro, elasticsearch, flink, gluster, grafana, kafka, kafka-manager, kibana, " +
                    "logstash, mesos-debian, mesos-master, mesos-redhat, mesos-suse, ntp, prometheus, spark, zeppelin, zookeeper",
                String.join(", ", builtPackageList));

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

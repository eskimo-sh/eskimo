/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.model.SetupCommand;
import ch.niceideas.eskimo.model.SystemStatusWrapper;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

public class SetupServiceTest extends AbstractSystemTest {

    private static final Logger logger = Logger.getLogger(SetupServiceTest.class);

    private String setupConfig = null;

    private String packagesVersionFile = null;

    private File tempConfigStoragePath = null;

    private File tempPackagesDistribPath = null;

    private String packagesToBuild = "base-eskimo,ntp,zookeeper,gluster,gdash,elasticsearch,cerebro,kibana,logstash,prometheus,grafana,kafka,kafka-manager,mesos-master,spark,zeppelin";

    private String mesosPackages = "mesos-debian,mesos-redhat";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setupConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SetupServiceTest/setupConfig.json"));
        packagesVersionFile = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SetupServiceTest/eskimo_packages_versions.json"));
        FileUtils.delete(new File ("/tmp/setupConfigTest"));

        tempConfigStoragePath = File.createTempFile("test_setup_service", "folder");
        tempConfigStoragePath.delete();

        tempPackagesDistribPath = File.createTempFile("test_setup_service_distrib", "folder");
        tempPackagesDistribPath.delete();
    }

    @Test
    public void testParseVersion() throws Exception {

        Pair<String, String> version = setupService.parseVersion("docker_template_base-eskimo_0.2_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<String, String> ("0.2", "1"), version);

        version = setupService.parseVersion("docker_template_gluster_debian_09_stretch_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<String, String> ("debian_09_stretch", "1"), version);

        version = setupService.parseVersion("docker_template_logstash_6.8.3_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<String, String> ("6.8.3", "1"), version);

        version = setupService.parseVersion("docker_template_mesos-master_1.8.1_1.tar.gz");
        assertNotNull(version);
        assertEquals(new Pair<String, String> ("1.8.1", "1"), version);
    }


    @Test
    public void testSaveAndPrepareSetup() throws Exception {

        SetupService setupService = createSetupService(new SetupService());

        SetupCommand command = setupService.saveAndPrepareSetup(setupConfig);

        JsonWrapper setupConfigWrapper = new JsonWrapper(setupService.loadSetupConfig());

        assertEquals("/tmp/setupConfigTest", setupConfigWrapper.getValueForPathAsString("setup_storage"));
        assertEquals("eskimo", setupConfigWrapper.getValueForPathAsString("ssh_username"));
        assertEquals("ssh_key", setupConfigWrapper.getValueForPathAsString("filename-ssh-key"));
        assertEquals("download", setupConfigWrapper.getValueForPathAsString("setup-mesos-origin"));
        assertEquals("download", setupConfigWrapper.getValueForPathAsString("setup-services-origin"));

        assertNotNull(command);

        assertEquals(0, command.getBuildMesos().size());
        assertEquals(0, command.getBuildPackage().size());

        assertEquals(2, command.getDownloadMesos().size());
        assertEquals(16, command.getDownloadPackages().size());

    }

    SetupService createSetupService(SetupService setupService) throws IOException {

        File storagePathConfDir = File.createTempFile("eskimo_storage", "");
        storagePathConfDir.delete();
        storagePathConfDir.mkdirs();
        setupService.setStoragePathConfDir(storagePathConfDir.getCanonicalPath());

        setupService.setConfigStoragePathInternal(tempConfigStoragePath.getCanonicalPath());
        setupService.setPackageDistributionPath(tempPackagesDistribPath.getCanonicalPath());

        setupService.setSystemService(systemService);

        setupService.setPackagesToBuild(packagesToBuild);
        setupService.setMesosPackages(mesosPackages);

        return setupService;
    }

    @Test
    public void testEnsureSetupCompleted() throws Exception {

        SetupService setupService = createSetupService(new SetupService());
        setupService.saveAndPrepareSetup(setupConfig);

        SetupException exception = assertThrows(SetupException.class, () -> {
            setupService.ensureSetupCompleted();
        });
        assertEquals(
                    "Following services are missing and need to be downloaded or built base-eskimo, cerebro, " +
                    "elasticsearch, gdash, gluster, grafana, kafka, kafka-manager, kibana, logstash, mesos-debian, " +
                    "mesos-master, mesos-redhat, ntp, prometheus, spark, zeppelin, zookeeper",
                exception.getMessage());

        // Create docker images packages
        for (String service : packagesToBuild.split(",")) {
            FileUtils.writeFile(new File(tempPackagesDistribPath + "/docker_template_" + service + "_0.0.1_1.tar.gz"), "DUMMY");
        }

        exception = assertThrows(SetupException.class, () -> {
            setupService.ensureSetupCompleted();
        });
        assertEquals(
                "Following services are missing and need to be downloaded or built mesos-debian, mesos-redhat",
                exception.getMessage());

        // Create mesos packages
        for (String mesosPackage : mesosPackages.split(",")) {
            FileUtils.writeFile(new File(tempPackagesDistribPath + "/" + mesosPackage), "DUMMY");
        }

        // no exception expected anymore
        setupService.ensureSetupCompleted();
    }


    @Test
    public void testApplySetup() throws Exception {

        final List<String> builtPackageList = new ArrayList<>();
        final List<String> downloadPackageList = new ArrayList<>();

        SetupService setupService = createSetupService(new SetupService() {
            protected void buildPackage(String image) throws SetupException {
                builtPackageList.add (image);
            }
            protected void downloadPackage(String fileName) throws SetupException {
                downloadPackageList.add (fileName);
            }
            protected JsonWrapper loadRemotePackagesVersionFile() throws SetupException {
                return new JsonWrapper(packagesVersionFile);
            }
        });
        setupService.saveAndPrepareSetup(setupConfig);

        setupService.applySetup(new JsonWrapper(setupConfig));

        assertEquals(0, builtPackageList.size());
        assertEquals(18, downloadPackageList.size());

        Collections.sort(downloadPackageList);
        assertEquals(
                "docker_template_base-eskimo_0.2_1.tar.gz, docker_template_cerebro_0.8.4_1.tar.gz, " +
                        "docker_template_elasticsearch_6.8.3_1.tar.gz, docker_template_gdash_0.0.1_1.tar.gz, " +
                        "docker_template_gluster_debian_09_stretch_1.tar.gz, docker_template_grafana_6.3.3_1.tar.gz, " +
                        "docker_template_kafka-manager_2.0.0.2_1.tar.gz, docker_template_kafka_2.2.0_1.tar.gz, " +
                        "docker_template_kibana_6.8.3_1.tar.gz, docker_template_logstash_6.8.3_1.tar.gz, " +
                        "docker_template_mesos-master_1.8.1_1.tar.gz, docker_template_ntp_debian_09_stretch_1.tar.gz, " +
                        "docker_template_prometheus_2.10.0_1.tar.gz, docker_template_spark_2.4.4_1.tar.gz, " +
                        "docker_template_zeppelin_0.9.0_1.tar.gz, docker_template_zookeeper_debian_09_stretch_1.tar.gz, " +
                        "niceideas_mesos-debian_1.8.1_1.tar.gz, niceideas_mesos-redhat_1.8.1_1.tar.gz",
                String.join(", ", downloadPackageList));


    }

}

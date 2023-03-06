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

package ch.niceideas.eskimo.services.satellite;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-conf", "test-setup", "test-services"})
public class KubernetesServicesConfigCheckerTest {

    @Autowired
    private KubernetesServicesConfigChecker kubernetesConfigChecker;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;


    @BeforeEach
    public void setUp() throws Exception {
        configurationServiceTest.reset();
        configurationServiceTest.saveNodesConfig(StandardSetupHelpers.getStandard2NodesSetup());
    }

    @Test
    public void testCheckKubernetesSetupCustomDeploymentWong() {
        KubernetesServicesConfigWrapper kubeConfigStd = StandardSetupHelpers.getStandardKubernetesConfig();
        kubeConfigStd.setValueForPath("database_deployment_strategy", "off");
        KubernetesServicesConfigException exp = assertThrows(KubernetesServicesConfigException.class,
                () -> kubernetesConfigChecker.checkKubernetesServicesSetup(kubeConfigStd));
        assertEquals("Service database defines a deployment strategy as 'custom' and yet it is missing replicas configuration", exp.getMessage());

        KubernetesServicesConfigWrapper kubeConfigCust = StandardSetupHelpers.getKubernetesConfigCustomDeployment();
        kubeConfigCust.setValueForPath("database_deployment_strategy", "on");
        exp = assertThrows(KubernetesServicesConfigException.class,
                () -> kubernetesConfigChecker.checkKubernetesServicesSetup(kubeConfigCust));
        assertEquals("Service database defines a deployment strategy as 'cluster wide' and yet it has replicas configured", exp.getMessage());
    }

    @Test
    public void testCheckKubernetesSetupCustomDeploymentOK() throws Exception {
        kubernetesConfigChecker.checkKubernetesServicesSetup(StandardSetupHelpers.getKubernetesConfigCustomDeployment());
    }

    @Test
    public void testCheckKubernetesSetupOK() throws Exception {
        kubernetesConfigChecker.checkKubernetesServicesSetup(StandardSetupHelpers.getStandardKubernetesConfig());
    }

    @Test
    public void testCheckKubernetesSetup_missingCpuSetting() {

        KubernetesServicesConfigWrapper kubeConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubeConfig.removeRootKey("database-manager_cpu");

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class,
                () -> kubernetesConfigChecker.checkKubernetesServicesSetup(kubeConfig));

        assertEquals("Inconsistency found : Kubernetes Service database-manager is enabled but misses CPU request setting", exception.getMessage());
    }

    @Test
    public void testCheckKubernetesSetup_missingRamSetting() {

        KubernetesServicesConfigWrapper kubeConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubeConfig.removeRootKey("database-manager_ram");

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class,
                () -> kubernetesConfigChecker.checkKubernetesServicesSetup(kubeConfig));

        assertEquals("Inconsistency found : Kubernetes Service database-manager is enabled but misses RAM request setting", exception.getMessage());
    }

    @Test
    public void testCheckKubernetesSetup_missingCpuWrong() {

        KubernetesServicesConfigWrapper kubeConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubeConfig.setValueForPath("database-manager_cpu", "1l");

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class,
                () -> kubernetesConfigChecker.checkKubernetesServicesSetup(kubeConfig));

        assertEquals("CPU definition for database-manager doesn't match expected REGEX - [0-9\\\\.]+[m]{0,1}", exception.getMessage());
    }

    @Test
    public void testCheckKubernetesSetup_missingRamWrong() {

        KubernetesServicesConfigWrapper kubeConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubeConfig.setValueForPath("database-manager_ram", "100ui");

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class,
                () -> kubernetesConfigChecker.checkKubernetesServicesSetup(kubeConfig));

        assertEquals("RAM definition for database-manager doesn't match expected REGEX - [0-9\\.]+[EPTGMk]{0,1}", exception.getMessage());
    }

    @Test
    public void testOneCerebroButNoES() {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("distributed-filesystem", "1");
            put("distributed-time1", "on");
        }});
        configurationServiceTest.saveNodesConfig(nodesConfig);

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class, () -> {

            KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(new HashMap<>() {{
                put("database-manager_installed", "on");
            }});

            kubernetesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);
        });

        assertEquals(
                "Inconsistency found : Service database-manager expects a installaton of  database. But it's not going to be installed",
                exception.getMessage());
    }

    @Test
    public void testSparkButNoKube() {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("distributed-filesystem1", "on");
            put("distributed-time1", "on");
        }});
        configurationServiceTest.saveNodesConfig(nodesConfig);

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class, () -> {

            KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(new HashMap<>() {{
                put("calculator-runtime_install", "on");
            }});

            kubernetesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);
        });

        assertEquals("Inconsistency found : Service calculator-runtime expects 1 cluster-master instance(s). But only 0 has been found !", exception.getMessage());
    }

    @Test
    public void testZeppelinButNoZookeeper() {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("distributed-filesystem", "1");
            put("distributed-time1", "on");
            //put("cluster-manager", "1");
            put("cluster-master", "1");
            put("cluster-slave1", "on");
        }});

        configurationServiceTest.saveNodesConfig(nodesConfig);

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class, () -> {

            KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(new HashMap<>() {{
                put("database_install", "on");
                put("user-console_installed", "on");
            }});

            kubernetesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);
        });

        assertEquals("Inconsistency found : Service user-console expects 1 cluster-manager instance(s). But only 0 has been found !", exception.getMessage());
    }

    @Test
    public void testNonKubernetesServiceCanBeSelected() {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("distributed-filesystem", "1");
            put("distributed-time1", "on");
            //put("cluster-manager", "1");
            put("cluster-master", "1");
            put("cluster-slave1", "on");
        }});

        configurationServiceTest.saveNodesConfig(nodesConfig);

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class, () -> {

            KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(new HashMap<>() {{
                put("cluster-manager_installed", "on");
            }});

            kubernetesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);
        });

        assertEquals("Inconsistency found : service cluster-manager is not a kubernetes service", exception.getMessage());
    }
}

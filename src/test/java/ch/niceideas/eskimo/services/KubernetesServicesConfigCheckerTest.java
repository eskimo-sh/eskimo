/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class KubernetesServicesConfigCheckerTest {

    private KubernetesServicesConfigChecker kubernetesConfigChecker = new KubernetesServicesConfigChecker();
    private ConfigurationService configurationService = new ConfigurationService();
    private SetupService setupService = new SetupService();

    NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
        put("node_id1", "192.168.10.11");
        put("kube-master", "1");
        put("kube-slave", "1");
        put("prometheus1", "on");
    }});

    KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(new HashMap<>() {{
        put("elasticsearch_install", "on");
        put("elasticsearch_cpu", "1");
        put("elasticsearch_ram", "1G");

        put("cerebro_install", "on");
        put("cerebro_cpu", "1");
        put("cerebro_ram", "1G");

        put("kibana_install", "on");
        put("kibana_cpu", "500m");
        put("kibana_ram", "500M");

        put("grafana_install", "on");
        put("grafana_cpu", "1.5");
        put("grafana_ram", "1G");
    }});

    @BeforeEach
    public void setUp() throws Exception {

        ServicesDefinition def = new ServicesDefinition();
        def.afterPropertiesSet();

        setupService = new SetupService();
        configurationService.setSetupService(setupService);

        setupService.setConfigurationService (configurationService);

        configurationService.setSetupService(setupService);

        kubernetesConfigChecker.setServicesDefinition(def);

        setupService.setConfigStoragePathInternal(SystemServiceTest.createTempStoragePath());
        kubernetesConfigChecker.setConfigurationService(configurationService);

        ServicesDefinition servicesDefinition = new ServicesDefinition();
        servicesDefinition.afterPropertiesSet();
        kubernetesConfigChecker.setServicesDefinition(servicesDefinition);
    }

    @Test
    public void testOneCerebroButNoES() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("cerebro", "1");
            put("ntp1", "on");
            put("prometheus1", "on");
        }});
        configurationService.saveNodesConfig(nodesConfig);

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class, () -> {

            KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(new HashMap<String, Object>() {{
                put("cerebro_installed", "on");
            }});

            kubernetesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);
        });

        assertEquals("Inconsistency found : Service cerebro expects a installaton of  elasticsearch. But it's not going to be installed", exception.getMessage());
    }

    @Test
    public void testSparkButNoKube() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("ntp1", "on");
            put("prometheus1", "on");

        }});
        configurationService.saveNodesConfig(nodesConfig);

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class, () -> {

            KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(new HashMap<String, Object>() {{
                put("spark-runtime_install", "on");
            }});

            kubernetesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);
        });

        assertEquals("Inconsistency found : Service spark-runtime expects 1 kube-master instance(s). But only 0 has been found !", exception.getMessage());
    }

    @Test
    public void testZeppelinButNoZookeeper() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("ntp1", "on");
            put("prometheus1", "on");
            put("kube-master1", "on");
            put("kube-slave1", "on");

        }});
        configurationService.saveNodesConfig(nodesConfig);

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class, () -> {

            KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(new HashMap<String, Object>() {{
                put("elasticsearch_install", "on");
                put("zeppelin_installed", "on");
            }});

            kubernetesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);
        });

        assertEquals("Inconsistency found : Service zeppelin expects 1 zookeeper instance(s). But only 0 has been found !", exception.getMessage());
    }

    @Test
    public void testNonKubernetesServiceCanBeSelected() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("ntp1", "on");
            put("prometheus1", "on");
            put("elasticsearch1", "on");
            put("zookeeper", "1");
        }});
        configurationService.saveNodesConfig(nodesConfig);

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class, () -> {

            KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(new HashMap<String, Object>() {{
                put("zookeeper_installed", "on");
            }});

            kubernetesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);
        });

        assertEquals("Inconsistency found : service zookeeper is not a kubernetes service", exception.getMessage());
    }

    @Test
    public void testCheckKubernetesSetupOK() throws Exception {

        configurationService.saveNodesConfig(nodesConfig);

        kubernetesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);
    }

    @Test
    public void testCheckKubernetesSetup_missingCpuSetting() throws Exception {

        configurationService.saveNodesConfig(nodesConfig);

        kubeServicesConfig.removeRootKey("cerebro_cpu");

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class, () -> {
            kubernetesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);
        });

        assertEquals("Inconsistency found : Kubernetes Service cerebro is enabled but misses CPU request setting", exception.getMessage());
    }

    @Test
    public void testCheckKubernetesSetup_missingRamSetting() throws Exception {

        configurationService.saveNodesConfig(nodesConfig);

        kubeServicesConfig.removeRootKey("cerebro_ram");

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class, () -> {
            kubernetesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);
        });

        assertEquals("Inconsistency found : Kubernetes Service cerebro is enabled but misses RAM request setting", exception.getMessage());
    }

    @Test
    public void testCheckKubernetesSetup_missingCpuWrong() throws Exception {

        configurationService.saveNodesConfig(nodesConfig);

        kubeServicesConfig.setValueForPath("cerebro_cpu", "1l");

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class, () -> {
            kubernetesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);
        });

        assertEquals("CPU definition for cerebro doesn't match expected REGEX - [0-9\\\\.]+[m]{0,1}", exception.getMessage());
    }

    @Test
    public void testCheckKubernetesSetup_missingRamWrong() throws Exception {

        configurationService.saveNodesConfig(nodesConfig);

        kubeServicesConfig.setValueForPath("cerebro_ram", "100ui");

        KubernetesServicesConfigException exception = assertThrows(KubernetesServicesConfigException.class, () -> {
            kubernetesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);
        });

        assertEquals("RAM definition for cerebro doesn't match expected REGEX - [0-9\\.]+[EPTGMk]{0,1}", exception.getMessage());
    }
}

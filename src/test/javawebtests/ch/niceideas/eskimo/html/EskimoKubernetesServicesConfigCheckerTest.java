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

package ch.niceideas.eskimo.html;

import ch.niceideas.eskimo.controlers.ServicesController;
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import ch.niceideas.eskimo.test.services.ServicesDefinitionTestImpl;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class EskimoKubernetesServicesConfigCheckerTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript("eskimoNodesConfigurationChecker.js");
        loadScript("eskimoKubernetesServicesConfigChecker.js");

        waitForDefinition("window.doCheckKubernetesSetup");

        ServicesController sc = new ServicesController();
        ServicesDefinitionTestImpl sd = new ServicesDefinitionTestImpl();
        sd.afterPropertiesSet();
        sc.setServicesDefinition(sd);

        String servicesDependencies = sc.getServicesDependencies();
        String kubernetesServiceConfig = sc.getKubernetesServices();

        js("window.SERVICES_DEPENDENCIES_WRAPPER = " + servicesDependencies + ";");

        js("window.KUBERNETES_SERVICES_CONFIG = " + kubernetesServiceConfig + ";");

        js("window.callCheckKubernetesSetup = function (nodesConfig, kubernetesConfig) {\n" +
                "   return doCheckKubernetesSetup(nodesConfig, kubernetesConfig, SERVICES_DEPENDENCIES_WRAPPER.servicesDependencies, KUBERNETES_SERVICES_CONFIG.kubernetesServicesConfigurations);\n" +
                "}");
    }

    @Test
    public void testCheckKubernetesSetupCustomDeploymentWong() {
        KubernetesServicesConfigWrapper kubeConfigStd = StandardSetupHelpers.getStandardKubernetesConfig();
        kubeConfigStd.setValueForPath("database_deployment_strategy", "off");
        Exception exp = assertThrows(Exception.class,
                () -> js("callCheckKubernetesSetup("
                        + StandardSetupHelpers.getStandard2NodesSetup().getFormattedValue() + ","
                        + kubeConfigStd.getFormattedValue() + ")"));
        assertTrue(exp.getMessage().contains("Service database defines a deployment strategy as 'custom' and yet it is missing replicas configuration"));

        KubernetesServicesConfigWrapper kubeConfigCust = StandardSetupHelpers.getKubernetesConfigCustomDeployment();
        kubeConfigCust.setValueForPath("database_deployment_strategy", "on");
        exp = assertThrows(Exception.class,
                () -> js("callCheckKubernetesSetup("
                        + StandardSetupHelpers.getStandard2NodesSetup().getFormattedValue() + ","
                        + kubeConfigCust.getFormattedValue() + ")"));
        assertTrue(exp.getMessage().contains("Service database defines a deployment strategy as 'cluster wide' and yet it has replicas configured"));
    }

    @Test
    public void testCheckKubernetesSetupCustomDeploymentOK() {
        assertDoesNotThrow(() ->
            js("callCheckKubernetesSetup("
                    + StandardSetupHelpers.getStandard2NodesSetup().getFormattedValue() + ","
                    + StandardSetupHelpers.getKubernetesConfigCustomDeployment().getFormattedValue() + ")"));
    }

    @Test
    public void testWithKubeConfigRequest() {
        assertDoesNotThrow(() ->
            js("callCheckKubernetesSetup("
                    + StandardSetupHelpers.getStandard2NodesSetup().getFormattedValue() + ","
                    + StandardSetupHelpers.getStandardKubernetesConfig().getFormattedValue() + ")"));
    }

    @Test
    public void testWithKubeConfigRequest_BuggyCPU() {

        KubernetesServicesConfigWrapper kubernetesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubernetesConfig.removeRootKey("calculator-runtime_install");

        Exception exception = assertThrows(Exception.class,
                () -> js("callCheckKubernetesSetup("
                        + StandardSetupHelpers.getStandard2NodesSetup().getFormattedValue() + ","
                        + kubernetesConfig.getFormattedValue() + ")"));

        //System.err.println (exception.getMessage());

        assertTrue(exception.getMessage().contains("Inconsistency found : Found a CPU definition for calculator-runtime_cpu. But corresponding service installation is not enabled"));
    }

    @Test
    public void testWithKubeConfigRequest_BuggyRAM() {

        KubernetesServicesConfigWrapper kubernetesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubernetesConfig.removeRootKey("calculator-runtime_install");
        kubernetesConfig.removeRootKey("calculator-runtime_cpu");

        Exception exception = assertThrows(Exception.class,
                () -> js("callCheckKubernetesSetup("
                        + StandardSetupHelpers.getStandard2NodesSetup().getFormattedValue() + ","
                        + kubernetesConfig.getFormattedValue() + ")"));

        //System.err.println (exception.getMessage());

        assertTrue(exception.getMessage().contains("Inconsistency found : Found a RAM definition for calculator-runtime_ram. But corresponding service installation is not enabled"));
    }

    @Test
    public void testWithKubeConfigRequest_missingCPU() {

        KubernetesServicesConfigWrapper kubernetesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubernetesConfig.removeRootKey("calculator-runtime_cpu");

        Exception exception = assertThrows(Exception.class,
                () -> js("callCheckKubernetesSetup("
                        + StandardSetupHelpers.getStandard2NodesSetup().getFormattedValue() + ","
                        + kubernetesConfig.getFormattedValue() + ")"));

        //System.err.println (exception.getMessage());

        assertTrue(exception.getMessage().contains("Inconsistency found : Kubernetes Service calculator-runtime is enabled but misses CPU request setting"));
    }

    @Test
    public void testWithKubeConfigRequest_missingRAM() {
        KubernetesServicesConfigWrapper kubernetesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubernetesConfig.removeRootKey("calculator-runtime_ram");

        Exception exception = assertThrows(Exception.class,
                () -> js("callCheckKubernetesSetup("
                        + StandardSetupHelpers.getStandard2NodesSetup().getFormattedValue() + ","
                        + kubernetesConfig.getFormattedValue() + ")"));

        //System.err.println (exception.getMessage());

        assertTrue(exception.getMessage().contains("Inconsistency found : Kubernetes Service calculator-runtime is enabled but misses RAM request setting"));
    }

    @Test
    public void testWithKubeConfigRequest_wrongCPU() {

        KubernetesServicesConfigWrapper kubernetesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubernetesConfig.setValueForPath("calculator-runtime_cpu", "0.5z");

        Exception exception = assertThrows(Exception.class,
                () -> js("callCheckKubernetesSetup("
                         + StandardSetupHelpers.getStandard2NodesSetup().getFormattedValue() + ","
                         + kubernetesConfig.getFormattedValue() + ")"));

        //System.err.println (exception.getMessage());

        assertTrue(exception.getMessage().contains("CPU definition for calculator-runtime_cpu doesn't match expected REGEX - /^[0-9\\\\.]+[m]?$/"));
    }

    @Test
    public void testWithKubeConfigRequest_wrongRAM() {

        KubernetesServicesConfigWrapper kubernetesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubernetesConfig.setValueForPath("calculator-runtime_ram", "500Gb");

        Exception exception = assertThrows(Exception.class,
                () -> js("callCheckKubernetesSetup("
                        + StandardSetupHelpers.getStandard2NodesSetup().getFormattedValue() + ","
                        + kubernetesConfig.getFormattedValue() + ")"));

        //System.err.println (exception.getMessage());

        assertTrue(exception.getMessage().contains("RAM definition for calculator-runtime_ram doesn't match expected REGEX - /^[0-9\\\\.]+[EPTGMk]?$/"));
    }

    @Test
    public void testCheckKubernetesSetupOK() {

        JSONObject nodesConfig = new JSONObject(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("cluster-manager", "1");
            put("cluster-master", "1");
            put("cluster-slave1", "on");
            put("distributed-time1", "on");
            put("distributed-filesystem1", "on");
        }});

        JSONObject kubernetesConfig = new JSONObject(new HashMap<>() {{
            put("database_install", "on");
            put("database_cpu", "1");
            put("database_ram", "500M");
            put("database_deployment_strategy", "on");
            put("database-manager_install", "on");
            put("database-manager_cpu", "1");
            put("database-manager_ram", "600M");
            put("database-manager_deployment_strategy", "on");
            put("user-console_install", "on");
            put("user-console_cpu", "1");
            put("user-console_ram", "800M");
            put("user-console_deployment_strategy", "on");
        }});

        assertDoesNotThrow(() ->
            js("callCheckKubernetesSetup(" + nodesConfig.toString(2) + "," + kubernetesConfig.toString(2) + ")"));
    }

    @Test
    public void testOneCerebroButNoES() {

        KubernetesServicesConfigWrapper kubernetesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubernetesConfig.removeRootKey("database_install");

        Exception exception = assertThrows(Exception.class, () -> js("callCheckKubernetesSetup("
                + StandardSetupHelpers.getStandard2NodesSetup().getFormattedValue() + ","
                + kubernetesConfig.getFormattedValue() + ")"));

        //System.err.println (exception.getMessage());

        assertTrue(exception.getMessage().contains("Inconsistency found : Service database-manager expects a installaton of  database. But it's not going to be installed"));
    }

    @Test
    public void testSparkButNoKube() {

        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.removeRootKey("cluster-master");

        Exception exception = assertThrows(Exception.class, () -> js("callCheckKubernetesSetup("
                + nodesConfig.getFormattedValue() + ","
                + StandardSetupHelpers.getStandardKubernetesConfig().getFormattedValue() + ")"));

        //System.err.println (exception.getMessage());

        assertTrue(exception.getMessage().contains("Inconsistency found : Service user-console expects 1 cluster-master instance(s). But only 0 has been found !"));
    }

    @Test
    public void testZeppelinButNoZookeeper() {
        NodesConfigWrapper nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
        nodesConfig.removeRootKey("cluster-manager");

        Exception exception = assertThrows(Exception.class, () -> js("callCheckKubernetesSetup("
                + nodesConfig.getFormattedValue() + ","
                + StandardSetupHelpers.getStandardKubernetesConfig().getFormattedValue() + ")"));

        //System.err.println (exception.getMessage());

        assertTrue(exception.getMessage().contains("Inconsistency found : Service user-console expects 1 cluster-manager instance(s). But only 0 has been found !"));
    }

}

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

package ch.niceideas.eskimo.model;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import ch.niceideas.eskimo.test.services.SystemServiceTestImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-system", "test-setup"})
public class KubernetesOperationsCommandTest {

    @Autowired
    private SystemServiceTestImpl systemServiceTest;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @BeforeEach
    public void setUp() throws Exception {
        systemServiceTest.reset();
    }

    @Test
    public void testNoChanges() {

        systemServiceTest.setStandard2NodesStatus();

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();

        KubernetesOperationsCommand koc = KubernetesOperationsCommand.create(servicesDefinition, systemServiceTest,
                KubernetesServicesConfigWrapper.empty(), savedServicesInstallStatus, kubeServicesConfig);

        assertEquals(0, koc.getInstallations().size());
        assertEquals(0, koc.getUninstallations().size());
    }

    @Test
    public void testInstallation() {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("kafka-manager_installed_on_IP_KUBERNETES_NODE");

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();

        systemServiceTest.setStandard2NodesStatus();

        KubernetesOperationsCommand koc = KubernetesOperationsCommand.create(servicesDefinition, systemServiceTest,
                KubernetesServicesConfigWrapper.empty(), savedServicesInstallStatus, kubeServicesConfig);

        assertEquals(1, koc.getInstallations().size());
        assertEquals(0, koc.getUninstallations().size());

        assertEquals ("kafka-manager", koc.getInstallations().stream()
                .map(KubernetesOperationsCommand.KubernetesOperationId::getService)
                .collect(Collectors.joining(",")));
    }

    @Test
    public void testUninstallation() {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubeServicesConfig.getJSONObject().remove("kafka-manager_install");

        systemServiceTest.setStandard2NodesStatus();

        KubernetesOperationsCommand koc = KubernetesOperationsCommand.create(servicesDefinition, systemServiceTest,
                KubernetesServicesConfigWrapper.empty(), savedServicesInstallStatus, kubeServicesConfig);

        assertEquals(0, koc.getInstallations().size());
        assertEquals(1, koc.getUninstallations().size());

        assertEquals ("kafka-manager", koc.getUninstallations().stream()
                .map(KubernetesOperationsCommand.KubernetesOperationId::getService)
                .collect(Collectors.joining(",")));
    }

    @Test
    public void toJSON () {

        KubernetesOperationsCommand moc = prepareFourOps();

        assertEquals("{\n" +
                "  \"restarts\": [\n" +
                "    \"spark-console\",\n" +
                "    \"elasticsearch\",\n" +
                "    \"spark-runtime\",\n" +
                "    \"logstash\",\n" +
                "    \"kafka\"\n" +
                "  ],\n" +
                "  \"uninstallations\": [\n" +
                "    \"kafka-manager\",\n" +
                "    \"zeppelin\"\n" +
                "  ],\n" +
                "  \"installations\": [\n" +
                "    \"cerebro\",\n" +
                "    \"kibana\"\n" +
                "  ]\n" +
                "}", moc.toJSON().toString(2));
    }

    private KubernetesOperationsCommand prepareFourOps() {
        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("cerebro_installed_on_IP_KUBERNETES_NODE");
        savedServicesInstallStatus.getJSONObject().remove("kibana_installed_on_IP_KUBERNETES_NODE");

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubeServicesConfig.getJSONObject().remove("kafka-manager_install");
        kubeServicesConfig.getJSONObject().remove("zeppelin_install");

        systemServiceTest.setStandard2NodesStatus();

        KubernetesOperationsCommand koc = KubernetesOperationsCommand.create(servicesDefinition, systemServiceTest,
                KubernetesServicesConfigWrapper.empty(), savedServicesInstallStatus, kubeServicesConfig);

        assertEquals(2, koc.getInstallations().size());
        assertEquals(2, koc.getUninstallations().size());
        return koc;
    }

    @Test
    public void testGetAllOperationsInOrder() {

        KubernetesOperationsCommand moc = prepareFourOps();

        List<KubernetesOperationsCommand.KubernetesOperationId> opInOrder = moc.getAllOperationsInOrder(null);

        assertEquals ("Installation_Topology-All-Nodes,installation_cerebro,installation_kibana," +
                        "uninstallation_kafka-manager,uninstallation_zeppelin," +
                        "restart_spark-console,restart_elasticsearch,restart_spark-runtime,restart_logstash,restart_kafka",
                opInOrder.stream().map(KubernetesOperationsCommand.KubernetesOperationId::toString).collect(Collectors.joining(",")));
    }

    @Test
    public void testRestarts() {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubeServicesConfig.setValueForPath("kafka_cpu", "2");
        kubeServicesConfig.setValueForPath("zeppelin_ram", "3000m");

        systemServiceTest.setStandard2NodesStatus();

        KubernetesOperationsCommand koc = KubernetesOperationsCommand.create(servicesDefinition, systemServiceTest,
                StandardSetupHelpers.getStandardKubernetesConfig(), savedServicesInstallStatus, kubeServicesConfig);

        assertEquals(0, koc.getInstallations().size());
        assertEquals(0, koc.getUninstallations().size());
        assertEquals(2, koc.getRestarts().size());

        assertEquals ("kafka", koc.getRestarts().get(0).getService());
        assertEquals ("restart", koc.getRestarts().get(0).getType());

        assertEquals ("zeppelin", koc.getRestarts().get(1).getService());
        assertEquals ("restart", koc.getRestarts().get(1).getType());

    }

}

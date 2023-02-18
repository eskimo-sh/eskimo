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

package ch.niceideas.eskimo.model;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import ch.niceideas.eskimo.test.services.SystemServiceTestImpl;
import ch.niceideas.eskimo.types.Service;
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

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-system", "test-setup", "test-services"})
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
        savedServicesInstallStatus.getJSONObject().remove("broker-manager_installed_on_IP_KUBERNETES_NODE");

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();

        systemServiceTest.setStandard2NodesStatus();

        KubernetesOperationsCommand koc = KubernetesOperationsCommand.create(servicesDefinition, systemServiceTest,
                KubernetesServicesConfigWrapper.empty(), savedServicesInstallStatus, kubeServicesConfig);

        assertEquals(1, koc.getInstallations().size());
        assertEquals(0, koc.getUninstallations().size());

        assertEquals ("broker-manager", koc.getInstallations().stream()
                .map(KubernetesOperationsCommand.KubernetesOperationId::getService)
                .map(Service::getName)
                .collect(Collectors.joining(",")));
    }

    @Test
    public void testUninstallation() {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubeServicesConfig.getJSONObject().remove("broker-manager_install");

        systemServiceTest.setStandard2NodesStatus();

        KubernetesOperationsCommand koc = KubernetesOperationsCommand.create(servicesDefinition, systemServiceTest,
                KubernetesServicesConfigWrapper.empty(), savedServicesInstallStatus, kubeServicesConfig);

        assertEquals(0, koc.getInstallations().size());
        assertEquals(1, koc.getUninstallations().size());

        assertEquals ("broker-manager", koc.getUninstallations().stream()
                .map(KubernetesOperationsCommand.KubernetesOperationId::getService)
                .map(Service::getName)
                .collect(Collectors.joining(",")));
    }

    @Test
    public void toJSON () {

        KubernetesOperationsCommand moc = prepareThreeOps();

        assertEquals("{\n" +
                "  \"restarts\": [\n" +
                "    \"cluster-dashboard\",\n" +
                "    \"database\",\n" +
                "    \"calculator-runtime\",\n" +
                "    \"broker\"\n" +
                "  ],\n" +
                "  \"uninstallations\": [\n" +
                "    \"broker-manager\",\n" +
                "    \"user-console\"\n" +
                "  ],\n" +
                "  \"installations\": [\"database-manager\"]\n" +
                "}", moc.toJSON().toString(2));
    }

    private KubernetesOperationsCommand prepareThreeOps() {
        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("database-manager_installed_on_IP_KUBERNETES_NODE");

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubeServicesConfig.getJSONObject().remove("broker-manager_install");
        kubeServicesConfig.getJSONObject().remove("user-console_install");

        systemServiceTest.setStandard2NodesStatus();

        KubernetesOperationsCommand koc = KubernetesOperationsCommand.create(servicesDefinition, systemServiceTest,
                KubernetesServicesConfigWrapper.empty(), savedServicesInstallStatus, kubeServicesConfig);

        assertEquals(1, koc.getInstallations().size());
        assertEquals(2, koc.getUninstallations().size());
        return koc;
    }

    @Test
    public void testGetAllOperationsInOrder() {

        KubernetesOperationsCommand moc = prepareThreeOps();

        List<KubernetesOperationsCommand.KubernetesOperationId> opInOrder = moc.getAllOperationsInOrder(null);

        assertEquals ("installation_Topology-All-Nodes\n" +
                        "installation_database-manager\n" +
                        "uninstallation_broker-manager\n" +
                        "uninstallation_user-console\n" +
                        "restart_cluster-dashboard\n" +
                        "restart_database\n" +
                        "restart_calculator-runtime\n" +
                        "restart_broker",
                opInOrder.stream().map(KubernetesOperationsCommand.KubernetesOperationId::toString).collect(Collectors.joining("\n")));
    }

    @Test
    public void testRestarts() {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        kubeServicesConfig.setValueForPath("broker_cpu", "2");
        kubeServicesConfig.setValueForPath("user-console_ram", "3000m");

        systemServiceTest.setStandard2NodesStatus();

        KubernetesOperationsCommand koc = KubernetesOperationsCommand.create(servicesDefinition, systemServiceTest,
                StandardSetupHelpers.getStandardKubernetesConfig(), savedServicesInstallStatus, kubeServicesConfig);

        assertEquals(0, koc.getInstallations().size());
        assertEquals(0, koc.getUninstallations().size());
        assertEquals(2, koc.getRestarts().size());

        assertEquals (Service.from("broker"), koc.getRestarts().get(0).getService());
        assertEquals (KubernetesOperationsCommand.KuberneteOperation.RESTART, koc.getRestarts().get(0).getOperation());

        assertEquals (Service.from("user-console"), koc.getRestarts().get(1).getService());
        assertEquals (KubernetesOperationsCommand.KuberneteOperation.RESTART, koc.getRestarts().get(1).getOperation());

    }

}

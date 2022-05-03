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

import ch.niceideas.eskimo.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class KubernetesOperationsCommandTest extends AbstractServicesDefinitionTest {

    private SystemService systemService = new SystemService() {

    };

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testNoChanges() {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardMarathonConfig();

        KubernetesOperationsCommand koc = KubernetesOperationsCommand.create(def, systemService, savedServicesInstallStatus, kubeServicesConfig);

        assertEquals(0, koc.getInstallations().size());
        assertEquals(0, koc.getUninstallations().size());
    }

    @Test
    public void testInstallation() {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("kafka-manager_installed_on_IP_MARATHON_NODE");

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardMarathonConfig();

        SystemService ss = new SystemService() {
            @Override
            public SystemStatusWrapper getStatus() {
                return StandardSetupHelpers.getStandard2NodesSystemStatus();
            }
        };

        KubernetesOperationsCommand koc = KubernetesOperationsCommand.create(def, ss, savedServicesInstallStatus, kubeServicesConfig);

        assertEquals(1, koc.getInstallations().size());
        assertEquals(0, koc.getUninstallations().size());

        assertEquals ("kafka-manager", koc.getInstallations().stream()
                .map(KubernetesOperationsCommand.KubernetesOperationId::getService)
                .collect(Collectors.joining(",")));
    }

    @Test
    public void testUninstallation() {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardMarathonConfig();
        kubeServicesConfig.getJSONObject().remove("kafka-manager_install");

        SystemService ss = new SystemService() {
            @Override
            public SystemStatusWrapper getStatus() throws StatusExceptionWrapperException {
                return StandardSetupHelpers.getStandard2NodesSystemStatus();
            }
        };


        KubernetesOperationsCommand koc = KubernetesOperationsCommand.create(def, ss, savedServicesInstallStatus, kubeServicesConfig);

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
        savedServicesInstallStatus.getJSONObject().remove("cerebro_installed_on_IP_MARATHON_NODE");
        savedServicesInstallStatus.getJSONObject().remove("kibana_installed_on_IP_MARATHON_NODE");

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardMarathonConfig();
        kubeServicesConfig.getJSONObject().remove("kafka-manager_install");
        kubeServicesConfig.getJSONObject().remove("zeppelin_install");

        SystemService ss = new SystemService() {
            @Override
            public SystemStatusWrapper getStatus() {
                return StandardSetupHelpers.getStandard2NodesSystemStatus();
            }
        };

        KubernetesOperationsCommand koc = KubernetesOperationsCommand.create(def, ss, savedServicesInstallStatus, kubeServicesConfig);

        assertEquals(2, koc.getInstallations().size());
        assertEquals(2, koc.getUninstallations().size());
        return koc;
    }

    @Test
    public void testGetAllOperationsInOrder() {

        KubernetesOperationsCommand moc = prepareFourOps();

        List<KubernetesOperationsCommand.KubernetesOperationId> opInOrder = moc.getAllOperationsInOrder(null);

        assertEquals ("Installation_Topology-All-Nodes,installation_cerebro,installation_kibana,uninstallation_kafka-manager,uninstallation_zeppelin", opInOrder.stream().map(KubernetesOperationsCommand.KubernetesOperationId::toString).collect(Collectors.joining(",")));
    }

}

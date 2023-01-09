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

package ch.niceideas.eskimo.services.satellite;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServiceOperationsCommand;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-setup"})
public class ServiceInstallationSorterTest {

    @Autowired
    private ServicesInstallationSorter sis;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    NodesConfigWrapper nodesConfig = null;

    @BeforeEach
    public void setUp() throws Exception {
        nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
    }

    @Test
    public void testNoMixUpOfKubeAndNonKube() throws Exception {

        ServiceOperationsCommand restartCommand = ServiceOperationsCommand.createForRestartsOnly(
                servicesDefinition,
                nodeRangeResolver,
                new String[] {"kube-master", "kube-slave", "gluster", "cerebro", "kibana"},
                StandardSetupHelpers.getStandard2NodesInstallStatus(),
                StandardSetupHelpers.getStandard2NodesSetup()
        );

        List<List<ServiceOperationsCommand.ServiceOperationId>> orderedRestart = restartCommand.getRestartsInOrder(
                sis, StandardSetupHelpers.getStandard2NodesSetup()
        );

        List<ServiceOperationsCommand.ServiceOperationId> group1 = orderedRestart.get(0);
        assertEquals(2, group1.size());
        assertEquals("gluster", group1.get(0).getService());
        assertEquals("192.168.10.11", group1.get(0).getNode());

        assertEquals("gluster", group1.get(1).getService());
        assertEquals("192.168.10.13", group1.get(1).getNode());

        List<ServiceOperationsCommand.ServiceOperationId> group2 = orderedRestart.get(1);
        assertEquals(1, group2.size());
        assertEquals("kube-master", group2.get(0).getService());
        assertEquals("192.168.10.11", group2.get(0).getNode());

        List<ServiceOperationsCommand.ServiceOperationId> group3 = orderedRestart.get(2);
        assertEquals(2, group3.size());
        assertEquals("kube-slave", group3.get(0).getService());
        assertEquals("192.168.10.11", group3.get(0).getNode());

        assertEquals("kube-slave", group3.get(1).getService());
        assertEquals("192.168.10.13", group3.get(1).getNode());

        List<ServiceOperationsCommand.ServiceOperationId> group4 = orderedRestart.get(3);
        assertEquals(1, group4.size());
        assertEquals("cerebro", group4.get(0).getService());
        assertEquals("(kubernetes)", group4.get(0).getNode());

    }

    @Test
    public void testNominal() throws Exception {

        ServicesInstallStatusWrapper savesServicesInstallStatus = new ServicesInstallStatusWrapper (new HashMap<>());

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(
                servicesDefinition, nodeRangeResolver, savesServicesInstallStatus, nodesConfig);

        List<List<ServiceOperationsCommand.ServiceOperationId>> orderedInstall = sis.orderOperations (oc.getInstallations(), nodesConfig);

        assertNotNull(orderedInstall);

        assertEquals(6, orderedInstall.size());

        // Test first, third and last group
        List<ServiceOperationsCommand.ServiceOperationId> group1 = orderedInstall.get(0);
        assertEquals(2, group1.size());
        assertEquals("zookeeper", group1.get(0).getService());
        assertEquals("192.168.10.13", group1.get(0).getNode());

        assertEquals("ntp", group1.get(1).getService());
        assertEquals("192.168.10.11", group1.get(1).getNode());

        List<ServiceOperationsCommand.ServiceOperationId> group4 = orderedInstall.get(4);
        assertEquals(1, group4.size());
        assertEquals("kube-master", group4.get(0).getService());
        assertEquals("192.168.10.11", group4.get(0).getNode());

        List<ServiceOperationsCommand.ServiceOperationId> group5 = orderedInstall.get(5);
        assertEquals(2, group5.size());
        assertEquals("kube-slave", group5.get(0).getService());
        assertEquals("192.168.10.11", group5.get(0).getNode());
        assertEquals("kube-slave", group5.get(1).getService());
        assertEquals("192.168.10.13", group5.get(1).getNode());


    }

}

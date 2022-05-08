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

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServiceOperationsCommand;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ServiceInstallationSorterTest extends  AbstractServicesDefinitionTest  {

    private ServicesInstallationSorter sio = null;

    NodesConfigWrapper nodesConfig = null;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        sio = new ServicesInstallationSorter();
        sio.setServicesDefinition(def);

        nodesConfig = StandardSetupHelpers.getStandard2NodesSetup();
    }

    @Test
    public void testNominal() throws Exception {

        ServicesInstallStatusWrapper savesServicesInstallStatus = new ServicesInstallStatusWrapper (new HashMap<>());

        ServiceOperationsCommand oc = ServiceOperationsCommand.create(def, nrr, savesServicesInstallStatus, nodesConfig);

        List<List<ServiceOperationsCommand.ServiceOperationId>> orderedInstall = sio.orderOperations (oc.getInstallations(), nodesConfig);

        assertNotNull(orderedInstall);

        assertEquals(10, orderedInstall.size());

        // Test first, third and last group
        List<ServiceOperationsCommand.ServiceOperationId> group1 = orderedInstall.get(0);
        assertEquals(2, group1.size());
        assertEquals("elasticsearch", group1.get(0).getService());
        assertEquals("192.168.10.11", group1.get(0).getNode());

        assertEquals("elasticsearch", group1.get(1).getService());
        assertEquals("192.168.10.13", group1.get(1).getNode());

        List<ServiceOperationsCommand.ServiceOperationId> group6 = orderedInstall.get(5);
        assertEquals(2, group6.size());
        assertEquals("gluster", group6.get(0).getService());
        assertEquals("192.168.10.11", group6.get(0).getNode());
        assertEquals("gluster", group6.get(1).getService());

        List<ServiceOperationsCommand.ServiceOperationId> group8 = orderedInstall.get(8);
        assertEquals(2, group8.size());
        assertEquals("spark-runtime", group8.get(0).getService());
        assertEquals("192.168.10.11", group8.get(0).getNode());
        assertEquals("spark-runtime", group8.get(1).getService());
        assertEquals("192.168.10.13", group8.get(1).getNode());


    }

}

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

package ch.niceideas.eskimo.model;

import ch.niceideas.eskimo.services.AbstractServicesDefinitionTest;
import ch.niceideas.eskimo.services.NodeRangeResolver;
import ch.niceideas.eskimo.services.StandardSetupHelpers;
import ch.niceideas.eskimo.services.SystemService;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class MarathonOperationsCommandTest extends AbstractServicesDefinitionTest {

    private NodeRangeResolver nrr;

    private SystemService systemService = new SystemService() {

    };

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        nrr = new NodeRangeResolver();
    }

    @Test
    public void testNoChanges() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        MarathonServicesConfigWrapper marathonConfig = StandardSetupHelpers.getStandardMarathonConfig();

        MarathonOperationsCommand moc = MarathonOperationsCommand.create(def, systemService, savedServicesInstallStatus, marathonConfig);

        assertEquals(0, moc.getInstallations().size());
        assertEquals(0, moc.getUninstallations().size());
    }

    @Test
    public void testInstallation() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        savedServicesInstallStatus.getJSONObject().remove("gdash_installed_on_IP_MARATHON_NODE");
        savedServicesInstallStatus.getJSONObject().remove("kafka-manager_installed_on_IP_MARATHON_NODE");

        MarathonServicesConfigWrapper marathonConfig = StandardSetupHelpers.getStandardMarathonConfig();

        SystemService ss = new SystemService() {
            @Override
            public SystemStatusWrapper getStatus() throws StatusExceptionWrapperException {
                return StandardSetupHelpers.getStandard2NodesSystemStatus();
            }
        };

        MarathonOperationsCommand moc = MarathonOperationsCommand.create(def, ss, savedServicesInstallStatus, marathonConfig);

        assertEquals(2, moc.getInstallations().size());
        assertEquals(0, moc.getUninstallations().size());

        assertEquals ("[gdash, kafka-manager]", Arrays.toString(moc.getInstallations().toArray()));
    }

    @Test
    public void testUninstallation() throws Exception {

        ServicesInstallStatusWrapper savedServicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();

        MarathonServicesConfigWrapper marathonConfig = StandardSetupHelpers.getStandardMarathonConfig();
        marathonConfig.getJSONObject().remove("gdash_install");
        marathonConfig.getJSONObject().remove("kafka-manager_install");

        SystemService ss = new SystemService() {
            @Override
            public SystemStatusWrapper getStatus() throws StatusExceptionWrapperException {
                return StandardSetupHelpers.getStandard2NodesSystemStatus();
            }
        };


        MarathonOperationsCommand moc = MarathonOperationsCommand.create(def, ss, savedServicesInstallStatus, marathonConfig);

        assertEquals(0, moc.getInstallations().size());
        assertEquals(2, moc.getUninstallations().size());

        assertEquals ("[gdash, kafka-manager]", Arrays.toString(moc.getUninstallations().toArray()));
    }
}

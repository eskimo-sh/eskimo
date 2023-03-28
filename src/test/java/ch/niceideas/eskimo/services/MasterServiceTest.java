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

package ch.niceideas.eskimo.services;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import ch.niceideas.eskimo.test.services.SSHCommandServiceTestImpl;
import ch.niceideas.eskimo.test.services.SystemServiceTestImpl;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({
        "no-web-stack",
        "test-conf",
        "test-system",
        "test-operation",
        "test-proxy",
        "test-nodes-conf",
        "test-ssh",
        "test-connection-manager",
        "test-services"})
public class MasterServiceTest {

    @Autowired
    private MasterService masterService;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private SystemServiceTestImpl systemServiceTest;

    @Autowired
    private SSHCommandServiceTestImpl sshCommandServiceTest;

    @BeforeEach
    public void setUp() throws Exception {
        configurationServiceTest.setStandard2NodesSetup();
        configurationServiceTest.setStandard2NodesInstallStatus();
        configurationServiceTest.setStandardKubernetesConfig();

        systemServiceTest.setStandard2NodesStatus();

        sshCommandServiceTest.setNodeResultBuilder((node, script) -> {
            if (script.equals("echo OK")) {
                return "OK";
            }
            if (script.endsWith("cat /proc/meminfo | grep MemTotal")) {
                switch (node.getAddress()) {
                    case "192.168.10.11":
                        return "MemTotal:        5969796 kB";
                    case "192.168.10.12":
                        return "MemTotal:        5799444 kB";
                    default:
                        return "MemTotal:        3999444 kB";
                }
            }
            if (script.equals("grep 'I am the new leader' /var/log/distributed-filesystem/management/management.log")) {
                if (node.getAddress().equals("192.168.10.11")) {
                    return "2021-01-17 22:44:05,633 INFO c.n.e.e.c.CommandServer [http-nio-28901-exec-4] About to execute command: volume - subcommand: status - options: all detail\n" +
                            "2021-01-17 22:44:36,564 INFO c.n.e.e.c.CommandServer [http-nio-28901-exec-6] About to execute command: pool - subcommand: list - options: \n" +
                            "2021-01-17 22:44:36,682 INFO c.n.e.e.c.CommandServer [http-nio-28901-exec-7] About to execute command: volume - subcommand: info - options: \n" +
                            "2021-01-17 22:44:36,746 INFO c.n.e.e.c.CommandServer [http-nio-28901-exec-8] About to execute command: volume - subcommand: status - options: all detail\n" +
                            "2021-01-17 22:44:52,559 INFO c.n.e.e.z.ElectionProcess$ProcessNodeWatcher [Thread-1-EventThread] [Process: test-node1] Event received: WatchedEvent state:SyncConnected type:NodeDeleted path:/management/management_election/p_0000000000\n" +
                            "2021-01-17 22:44:52,561 INFO c.n.e.e.z.ElectionProcess [Thread-1-EventThread] [Process: test-node1] I am the new leader!\n" +
                            "2021-01-17 22:44:52,566 INFO c.n.e.e.z.ElectionProcess$ProcessNodeWatcher [Thread-1-EventThread] [Process: test-node1] Event received: WatchedEvent state:SyncConnected type:NodeDataChanged path:/management/master_id\n" +
                            "2021-01-17 22:44:52,567 INFO c.n.e.e.z.ElectionProcess$ProcessNodeWatcher [Thread-1-EventThread] [Process: test-node1] Master changed: test-node1\n" +
                            "2021-01-17 22:44:59,136 INFO c.n.e.e.m.ManagementService [pool-2-thread-1] - Updating System Status\n" +
                            "2021-01-17 22:44:59,360 INFO c.n.e.e.c.CommandServer [http-nio-28901-exec-10] About to execute command: pool - subcommand: list - options: \n" +
                            "2021-01-17 22:44:59,449 INFO c.n.e.e.c.CommandServer [http-nio-28901-exec-1] About to execute command: volume - subcommand: info - options: \n";
                }
            }
            return null;
        });
    }

    @Test
    @DirtiesContext
    public void testUpdateStatus() {

        assertTrue (masterService instanceof MasterServiceImpl);
        MasterServiceImpl impl = (MasterServiceImpl) masterService;

        assertEquals(0, impl.getServiceMasterNodes().size());
        assertEquals(0, impl.getServiceMasterTimestamps().size());

        masterService.updateStatus();

        assertEquals(1, impl.getServiceMasterNodes().size());
        assertEquals(1, impl.getServiceMasterTimestamps().size());

        assertEquals(Node.fromAddress("192.168.10.11"), impl.getServiceMasterNodes().get(Service.from("distributed-filesystem")));
        assertNotNull(impl.getServiceMasterTimestamps().get(Service.from("distributed-filesystem")));
    }

    @Test
    @DirtiesContext
    public void testGetMasterStatus() throws Exception {

        assertTrue (masterService instanceof MasterServiceImpl);
        MasterServiceImpl impl = (MasterServiceImpl) masterService;

        assertEquals(0, impl.getServiceMasterNodes().size());
        assertEquals(0, impl.getServiceMasterTimestamps().size());

        // this sets a default master
        masterService.getMasterStatus();

        assertEquals(1, impl.getServiceMasterNodes().size());
        assertEquals(1, impl.getServiceMasterTimestamps().size());

        assertEquals(Node.fromAddress("192.168.10.11"), impl.getServiceMasterNodes().get(Service.from("distributed-filesystem")));
        assertNotNull(impl.getServiceMasterTimestamps().get(Service.from("distributed-filesystem")));
    }

}

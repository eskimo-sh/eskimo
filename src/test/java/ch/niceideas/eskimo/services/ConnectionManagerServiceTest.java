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

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.eskimo.AbstractBaseSSHTest;
import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.model.SSHConnection;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import com.trilead.ssh2.LocalPortForwarder;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionManagerServiceTest extends AbstractBaseSSHTest {

    @Override
    protected CommandFactory getSShSubsystemToUse() {
        return new ProcessShellCommandFactory();
    }

    private ProxyManagerService pms = null;

    private ConnectionManagerService cm = null;

    private SetupService setupService = null;

    private ConfigurationService cs = null;

    @BeforeEach
    public void setUp() throws Exception {
        setupService = new SetupService();
        String tempPath = SystemServiceTest.createTempStoragePath();

        setupService.setConfigStoragePathInternal(tempPath);
        FileUtils.writeFile(new File(tempPath + "/config.json"), "{ \"ssh_username\" : \"test\" }");

        cm = new ConnectionManagerService(privateKeyRaw, getSShPort());

        pms = new ProxyManagerService();
        pms.setConnectionManagerService(cm);
        cm.setProxyManagerService(pms);
        pms.setConnectionManagerService(cm);

        cs = new ConfigurationService();
        cs.setSetupService(setupService);

        cm.setConfigurationService(cs);
    }

    @Test
    public void testNominal() throws Exception {
        assertNotNull (sshd);

        // create a connection to localhost
        SSHConnection connection = cm.getSharedConnection("localhost");
        assertNotNull(connection);

        // get a second connection and make sure it matches
        SSHConnection second = cm.getSharedConnection("localhost");
        assertNotNull(second);
        assertSame(connection, second);

        // close connection and make sure it gets properly recreated
        second.close();

        SSHConnection newOne = cm.getSharedConnection("localhost");
        assertNotNull(newOne);
        assertNotSame (newOne, second);
    }

    @Test
    public void testLocalPortForwarderWrapper() throws Exception {

        final List<ProxyTunnelConfig> forwarderConfig = new ArrayList<ProxyTunnelConfig>(){{
            add (new ProxyTunnelConfig("dummyService", 6123, "localhost", 123));
            add (new ProxyTunnelConfig("dummyService",6124, "localhost", 124));
            add (new ProxyTunnelConfig("dummyService",6125, "localhost", 125));
        }};

        final List<String> createCalledFor = new ArrayList<>();
        final List<String> dropCalledFor = new ArrayList<>();

        ConnectionManagerService cm = new ConnectionManagerService(privateKeyRaw, getSShPort()) {
            @Override
            protected SSHConnection createConnectionInternal(String node, int operationTimeout) {
                return new SSHConnection(node, getSShPort()) {
                    public synchronized LocalPortForwarder createLocalPortForwarder(int local_port, String host_to_connect, int port_to_connect) {
                        createCalledFor.add(""+port_to_connect);
                        return null;
                    }
                    public synchronized void sendIgnorePacket() {
                        // NO-OP
                    }
                };
            }
            @Override
            protected void dropTunnelsToBeClosed(SSHConnection connection, String node)  {
                super.dropTunnelsToBeClosed(connection, node);
                dropCalledFor.add(node);
            }
        };

        ProxyManagerService pms = new ProxyManagerService() {
            public List<ProxyTunnelConfig> getTunnelConfigForHost (String host) {
                return forwarderConfig;
            }
        };
        pms.setConnectionManagerService(cm);
        cm.setProxyManagerService(pms);
        pms.setConnectionManagerService(cm);

        SSHConnection connection = cm.getSharedConnection("localhost");

        assertEquals(3, createCalledFor.size());
        assertEquals("123,124,125", String.join(",", createCalledFor));

        assertEquals(1, dropCalledFor.size());

        assertNotNull(connection);

        createCalledFor.clear();
        dropCalledFor.clear();

        cm.recreateTunnels("localhost");

        // nothing actually recreated since nothing changed
        assertEquals(0, createCalledFor.size());
        assertEquals(1, dropCalledFor.size());

        forwarderConfig.clear();
        forwarderConfig.add (new ProxyTunnelConfig("dummyService", 20123, "localhost", 11123));
        forwarderConfig.add (new ProxyTunnelConfig("dummyService",20124, "localhost", 11124));
        forwarderConfig.add (new ProxyTunnelConfig("dummyService",20125, "localhost", 11125));

        createCalledFor.clear();
        dropCalledFor.clear();

        cm.recreateTunnels("localhost");

        assertEquals(3, createCalledFor.size());


        assertEquals(1, dropCalledFor.size());

    }
}

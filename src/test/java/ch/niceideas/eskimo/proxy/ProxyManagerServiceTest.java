/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.services.ConnectionManagerException;
import ch.niceideas.eskimo.services.ConnectionManagerService;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class ProxyManagerServiceTest {

    private ProxyManagerService pms;
    private ServicesDefinition sd;

    private AtomicBoolean recreateTunnelsCalled = new AtomicBoolean(false);
    private AtomicBoolean removeForwardersCalled = new AtomicBoolean(false);

    @BeforeEach
    public void setUp() throws Exception {
        pms = new ProxyManagerService();
        sd = new ServicesDefinition();
        pms.setServicesDefinition(sd);
        sd.afterPropertiesSet();
        pms.setConnectionManagerService(new ConnectionManagerService() {
            @Override
            public void recreateTunnels(String host) throws ConnectionManagerException {
                recreateTunnelsCalled.set(true);
            }
        });
        pms.setWebSocketProxyServer(new WebSocketProxyServer(pms, sd) {
            @Override
            public void removeForwardersForService(String serviceId) {
                removeForwardersCalled.set(true);
            }
        });
    }

    @Test
    public void testGetServerURI() throws Exception {
        pms.updateServerForService("zeppelin", "192.168.10.11");

        assertEquals("http://localhost:"+pms.getTunnelConfig("zeppelin").getLocalPort()+"/", pms.getServerURI("zeppelin", "/localhost:8080/zeppelin"));
        assertEquals("http://localhost:"+pms.getTunnelConfig("zeppelin").getLocalPort()+"/", pms.getServerURI("zeppelin", "/localhost:8080/zeppelin/tugudu"));

        assertTrue (pms.getTunnelConfig("zeppelin").getLocalPort() >= ProxyManagerService.LOCAL_PORT_RANGE_START && pms.getTunnelConfig("zeppelin").getLocalPort() <= 65535);
    }

    @Test
    public void testExtractHostFromPathInfo() throws Exception {
        assertEquals("192-168-10-11", pms.extractHostFromPathInfo("192-168-10-11//slave(1)/monitor/statistics"));
        assertEquals("192-168-10-11", pms.extractHostFromPathInfo("/192-168-10-11//slave(1)/monitor/statistics"));
        assertEquals("192-168-10-11", pms.extractHostFromPathInfo("/192-168-10-11"));
    }

    @Test
    public void testServerForServiceManagemement() throws Exception {

        assertFalse(recreateTunnelsCalled.get());
        assertFalse(removeForwardersCalled.get());

        pms.updateServerForService("kibana", "192.168.10.11");

        assertTrue(recreateTunnelsCalled.get());
        assertTrue(removeForwardersCalled.get());

        recreateTunnelsCalled.set(false);
        removeForwardersCalled.set(false);

        pms.updateServerForService("kibana", "192.168.10.11");

        // should not have been recreated
        assertFalse(recreateTunnelsCalled.get());
        assertFalse(removeForwardersCalled.get());

        pms.updateServerForService("kibana", "192.168.10.13");

        assertTrue(recreateTunnelsCalled.get());
        assertTrue(removeForwardersCalled.get());
    }
}

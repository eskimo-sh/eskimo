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


package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.configurations.ProxyConfiguration;
import ch.niceideas.eskimo.test.services.WebSocketProxyServerTestImpl;
import ch.niceideas.eskimo.types.Service;
import ch.niceideas.eskimo.types.ServiceWebId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-conf", "test-proxy", "test-web-socket"}) // using test implementation that overrides default implementation
public class WebSocketProxyServerTest {

    @Autowired
    private WebSocketProxyServerTestImpl server = null;

    private WebSocketSession wss1;
    private WebSocketSession wss2;

    @BeforeEach
    public void setUp() throws Exception {

        wss1 = new StandardWebSocketSession(null, null, null, null) {
            @Override
            public URI getUri() {
                return URI.create(ProxyConfiguration.ESKIMO_WEB_SOCKET_URL_PREFIX + "/cerebro/test");
            }
        };

        wss2 = new StandardWebSocketSession(null, null, null, null) {
            @Override
            public URI getUri() {
                return URI.create(ProxyConfiguration.ESKIMO_WEB_SOCKET_URL_PREFIX + "/cerebro/test");
            }
        };

        server.reset();
    }

    @Test
    public void testHandleMessage() throws Exception {

        server.handleMessage(wss1, new TextMessage("hello"));

        Map<ServiceWebId, Map<String, Map<String, WebSocketProxyForwarder>>>  forwarders = server.getForwarders();

        assertEquals(1, forwarders.size());

        server.handleMessage(wss2, new TextMessage("hello"));

        forwarders = server.getForwarders();

        assertEquals(1, forwarders.size());

        assertEquals(2, forwarders.get(ServiceWebId.fromService(Service.from("cerebro"))).size());

        assertEquals(1, forwarders.get(ServiceWebId.fromService(Service.from("cerebro"))).get(wss2.getId()).size());

        assertNotNull(forwarders.get(ServiceWebId.fromService(Service.from("cerebro"))).get(wss2.getId()).get("/test"));
    }

    @Test
    public void testRemoveForwardersForService() throws Exception {

        testHandleMessage();

        server.removeForwardersForService(ServiceWebId.fromService(Service.from("cerebro")));

        Map<ServiceWebId, Map<String, Map<String, WebSocketProxyForwarder>>>  forwarders = server.getForwarders();

        assertEquals(0, forwarders.size());

        assertEquals(2, server.getClosedCallsCount());
    }

    @Test
    public void testAfterConnectionClosed() throws Exception {

        testHandleMessage();

        server.afterConnectionClosed(wss2, CloseStatus.NORMAL);

        Map<ServiceWebId, Map<String, Map<String, WebSocketProxyForwarder>>>  forwarders = server.getForwarders();

        assertEquals(1, forwarders.size());

        assertEquals(1, forwarders.get(ServiceWebId.fromService(Service.from("cerebro"))).size());

        assertEquals(1, forwarders.get(ServiceWebId.fromService(Service.from("cerebro"))).get(wss1.getId()).size());

        assertNotNull(forwarders.get(ServiceWebId.fromService(Service.from("cerebro"))).get(wss1.getId()).get("/test"));

        assertEquals(1, server.getClosedCallsCount());
    }
}

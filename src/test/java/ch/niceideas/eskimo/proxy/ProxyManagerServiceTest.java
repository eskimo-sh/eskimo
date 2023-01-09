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

package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import ch.niceideas.eskimo.test.services.ConnectionManagerServiceTestImpl;
import ch.niceideas.eskimo.test.services.WebSocketProxyServerTestImpl;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Proxy;

import static org.apache.logging.log4j.core.config.Configurator.setLevel;
import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-web-socket", "test-conf", "test-connection-manager"})
public class ProxyManagerServiceTest {

    private static final Logger logger = LogManager.getLogger(ProxyManagerServiceTest.class.getName());

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private WebSocketProxyServerTestImpl webSocketProxyServerTest;

    @Autowired
    private ConnectionManagerServiceTestImpl connectionManagerServiceTest;

    @BeforeEach
    public void setUp() throws Exception {

        configurationServiceTest.setStandard2NodesInstallStatus();

        proxyManagerService.removeServerForService ("gluster", "192.168.10.11");
        proxyManagerService.removeServerForService ("gluster", "192.168.10.13");

        proxyManagerService.removeServerForService ("zeppelin", "192.168.10.11");

        proxyManagerService.removeServerForService ("flink-runtime", "192.168.10.11");
        proxyManagerService.removeServerForService ("flink-runtime", "192.168.10.12");
        proxyManagerService.removeServerForService ("flink-runtime", "192.168.10.13");

        proxyManagerService.removeServerForService ("kubernetes-dashboard", "192.168.10.11");
        proxyManagerService.removeServerForService ("kubernetes-dashboard", "192.168.10.13");

        proxyManagerService.removeServerForService ("kibana", "192.168.10.11");
        proxyManagerService.removeServerForService ("kibana", "192.168.10.13");

        connectionManagerServiceTest.reset();
        webSocketProxyServerTest.reset();

        connectionManagerServiceTest.dontConnect();
    }

    @Test
    @DirtiesContext
    public void testDumpProxyTunnelConfig() throws Exception {

        Logger testLogger = LogManager.getLogger(ProxyManagerServiceImpl.class.getName());
        try {
            setLevel(ProxyManagerServiceImpl.class.getName(), Level.DEBUG);

            StringBuilder builder = new StringBuilder();

            Appender testAppender = (Appender) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Appender.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "isStarted":
                        return true;
                    case "getName":
                        return "test";
                    case "append":
                        org.apache.logging.log4j.core.impl.Log4jLogEvent event = (org.apache.logging.log4j.core.impl.Log4jLogEvent) args[0];
                        builder.append(event.getMessage().getFormattedMessage());
                        builder.append("\n");
                        // no break
                    default:
                        return null;
                }
            });

            ((org.apache.logging.log4j.core.Logger) testLogger).addAppender(testAppender);

            proxyManagerService.updateServerForService("gluster", "192.168.10.11");

            String result = builder.toString();

            logger.info(result);

            assertTrue(result.contains("------ BEFORE ---- updateServerForService (gluster,192.168.10.11) ----------- "));
            assertTrue(result.contains("Updating server config for service gluster. Will recreate tunnels to 192.168.10.11"));
            assertTrue(result.contains("------ AFTER ---- updateServerForService (gluster,192.168.10.11) -----------"));
            assertTrue(result.contains(" - gluster/192-168-10-11 -> gluster - "));
        } finally {
            setLevel(ProxyManagerService.class.getName(), Level.INFO);
        }
    }

    @Test
    @DirtiesContext
    public void testGetServerURI() throws Exception {
        proxyManagerService.updateServerForService("zeppelin", "192.168.10.11");

        assertEquals("http://localhost:"+proxyManagerService.getTunnelConfig("zeppelin").getLocalPort()+"/", proxyManagerService.getServerURI("zeppelin", "/localhost:8080/zeppelin"));
        assertEquals("http://localhost:"+proxyManagerService.getTunnelConfig("zeppelin").getLocalPort()+"/", proxyManagerService.getServerURI("zeppelin", "/localhost:8080/zeppelin/tugudu"));

        assertTrue (proxyManagerService.getTunnelConfig("zeppelin").getLocalPort() >= ProxyManagerService.LOCAL_PORT_RANGE_START && proxyManagerService.getTunnelConfig("zeppelin").getLocalPort() <= 65535);
    }

    @Test
    public void testExtractHostFromPathInfo() {
        assertEquals("192-168-10-11", proxyManagerService.extractHostFromPathInfo("192-168-10-11//slave(1)/monitor/statistics"));
        assertEquals("192-168-10-11", proxyManagerService.extractHostFromPathInfo("/192-168-10-11//slave(1)/monitor/statistics"));
        assertEquals("192-168-10-11", proxyManagerService.extractHostFromPathInfo("/192-168-10-11"));
    }

    @Test
    @DirtiesContext
    public void testServerForServiceManagemement_reproduceFlinkRuntimeProblem() throws Exception {

        logger.info (" ---- flink-runtime detected on 192.168.10.12");
        proxyManagerService.updateServerForService ("flink-runtime", "192.168.10.12");

        logger.info (" ---- flink-runtime removed from 192.168.10.12");
        proxyManagerService.removeServerForService("flink-runtime", "192.168.10.12");

        logger.info (" ---- now flink-runtime detected on 192.168.10.13");
        proxyManagerService.updateServerForService ("flink-runtime", "192.168.10.13");

        assertEquals(2, connectionManagerServiceTest.getOpenedForwarders().size());
        assertEquals(1, connectionManagerServiceTest.getClosedForwarders().size());

        String firstForwarder = connectionManagerServiceTest.getOpenedForwarders().get(0);
        String secondForwarder = connectionManagerServiceTest.getOpenedForwarders().get(1);

        String closedForwarder = connectionManagerServiceTest.getClosedForwarders().get(0);

        assertTrue(firstForwarder.endsWith("192.168.10.11/8001"));
        assertTrue(secondForwarder.endsWith("192.168.10.11/8001"));

        assertEquals(closedForwarder, firstForwarder);
    }

    @Test
    @DirtiesContext
    public void testServerForServiceManagemement_Kubernetes_kbeProxy() throws Exception {

        assertFalse(connectionManagerServiceTest.isRecreateTunnelsCalled());
        assertFalse(webSocketProxyServerTest.isRemoveForwardersCalled());

        proxyManagerService.updateServerForService("kubernetes-dashboard", "192.168.10.11");

        assertTrue(connectionManagerServiceTest.isRecreateTunnelsCalled());
        assertTrue(webSocketProxyServerTest.isRemoveForwardersCalled());

        connectionManagerServiceTest.reset();
        webSocketProxyServerTest.reset();

        proxyManagerService.updateServerForService("kubernetes-dashboard", "192.168.10.11");

        // should not have been recreated
        assertFalse(connectionManagerServiceTest.isRecreateTunnelsCalled());
        assertFalse(webSocketProxyServerTest.isRemoveForwardersCalled());

        proxyManagerService.updateServerForService("kubernetes-dashboard", "192.168.10.13");

        // since kub service are redirected to poxy on kube master, no tunnel recreation should occur when service moves
        assertFalse(connectionManagerServiceTest.isRecreateTunnelsCalled());
        assertFalse(webSocketProxyServerTest.isRemoveForwardersCalled());
    }

    @Test
    @DirtiesContext
    public void testServerForServiceManagemement_Kubernetes_noKubeProxy() throws Exception {

        assertFalse(connectionManagerServiceTest.isRecreateTunnelsCalled());
        assertFalse(webSocketProxyServerTest.isRemoveForwardersCalled());

        proxyManagerService.updateServerForService("kibana", "192.168.10.11");

        assertTrue(connectionManagerServiceTest.isRecreateTunnelsCalled());
        assertTrue(webSocketProxyServerTest.isRemoveForwardersCalled());

        connectionManagerServiceTest.reset();
        connectionManagerServiceTest.dontConnect();
        webSocketProxyServerTest.reset();

        proxyManagerService.updateServerForService("kibana", "192.168.10.11");

        // should not have been recreated
        assertFalse(connectionManagerServiceTest.isRecreateTunnelsCalled());
        assertFalse(webSocketProxyServerTest.isRemoveForwardersCalled());

        proxyManagerService.updateServerForService("kibana", "192.168.10.13");

        assertTrue(connectionManagerServiceTest.isRecreateTunnelsCalled());
        assertTrue(webSocketProxyServerTest.isRemoveForwardersCalled());
    }

    @Test
    @DirtiesContext
    public void testServerForServiceManagemement() throws Exception {

        assertFalse(connectionManagerServiceTest.isRecreateTunnelsCalled());
        assertFalse(webSocketProxyServerTest.isRemoveForwardersCalled());

        proxyManagerService.updateServerForService("gluster", "192.168.10.11");

        assertTrue(connectionManagerServiceTest.isRecreateTunnelsCalled());
        assertTrue(webSocketProxyServerTest.isRemoveForwardersCalled());

        connectionManagerServiceTest.reset();
        connectionManagerServiceTest.dontConnect();
        webSocketProxyServerTest.reset();

        proxyManagerService.updateServerForService("gluster", "192.168.10.11");

        // should not have been recreated
        assertFalse(connectionManagerServiceTest.isRecreateTunnelsCalled());
        assertFalse(webSocketProxyServerTest.isRemoveForwardersCalled());

        proxyManagerService.updateServerForService("gluster", "192.168.10.13");

        assertTrue(connectionManagerServiceTest.isRecreateTunnelsCalled());
        assertTrue(webSocketProxyServerTest.isRemoveForwardersCalled());
    }
}

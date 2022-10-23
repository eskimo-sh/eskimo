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

import ch.niceideas.eskimo.model.SSHConnection;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.services.*;
import com.trilead.ssh2.LocalPortForwarder;
import org.apache.logging.log4j.core.Appender;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.logging.log4j.core.config.Configurator.setLevel;
import static org.apache.logging.log4j.core.config.Configurator.setRootLevel;
import static org.junit.jupiter.api.Assertions.*;

public class ProxyManagerServiceTest {

    private static final Logger logger = LogManager.getLogger(ProxyManagerServiceTest.class.getName());

    private ProxyManagerService pms;
    private ServicesDefinitionImpl sd;

    private final AtomicBoolean recreateTunnelsCalled = new AtomicBoolean(false);
    private final AtomicBoolean removeForwardersCalled = new AtomicBoolean(false);

    @BeforeEach
    public void setUp() throws Exception {
        pms = new ProxyManagerService();
        sd = new ServicesDefinitionImpl();
        pms.setServicesDefinition(sd);
        sd.afterPropertiesSet();
        pms.setConnectionManagerService(new ConnectionManagerService() {
            @Override
            public void recreateTunnels(String host) {
                recreateTunnelsCalled.set(true);
            }
        });
        pms.setConfigurationService(new ConfigurationServiceImpl() {
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
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
    public void testDumpProxyTunnelConfig() throws Exception {

        Logger testLogger = LogManager.getLogger(ProxyManagerService.class.getName());
        try {
            setLevel(ProxyManagerService.class.getName(), Level.DEBUG);

            StringBuilder builder = new StringBuilder();

            Appender testAppender = (Appender) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Appender.class}, (proxy, method, args) -> {
                if (method.getName().equals("isStarted")) {
                    return true;
                } else if (method.getName().equals("getName")) {
                    return "test";
                } else if (method.getName().equals("append")) {
                    org.apache.logging.log4j.core.impl.Log4jLogEvent event = (org.apache.logging.log4j.core.impl.Log4jLogEvent) args[0];
                    builder.append(event.getMessage().getFormattedMessage());
                    builder.append("\n");
                }
                return null;
            });

            ((org.apache.logging.log4j.core.Logger) testLogger).addAppender(testAppender);

            pms.updateServerForService("gluster", "192.168.10.11");

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
    public void testServerForServiceManagemement_reproduceFlinRuntimeProblem() throws Exception {

        final List<String> openedForwarders = new ArrayList<>();
        final List<String> closedForwarders = new ArrayList<>();

        ConnectionManagerService cms = new ConnectionManagerService() {

            @Override
            protected LocalPortForwarderWrapper createPortForwarder(SSHConnection connection, ProxyTunnelConfig config) throws ConnectionManagerException {
                openedForwarders.add (config.getLocalPort() + "/" + config.getNode() + "/" + config.getRemotePort());
                return new LocalPortForwarderWrapper(
                        config.getServiceName(), connection, config.getLocalPort(), config.getNode(), config.getRemotePort()) {
                    @Override
                    public void close() {
                        closedForwarders.add (config.getLocalPort() + "/" + config.getNode() + "/" + config.getRemotePort());
                    }
                };
            }
            @Override
            protected SSHConnection createConnectionInternal(String node, int operationTimeout){
                return new SSHConnection(node, operationTimeout) {
                    @Override
                    public LocalPortForwarder createLocalPortForwarder(int local_port, String host_to_connect, int port_to_connect){
                        return null;
                    }
                };
            }
        };
        pms.setConnectionManagerService(cms);
        cms.setProxyManagerService(pms);


        WebSocketProxyServer wsps = new WebSocketProxyServer(pms, sd) {
        };
        pms.setWebSocketProxyServer(wsps);

        logger.info (" ---- flink-runtime detected on 192.168.10.12");
        pms.updateServerForService ("flink-runtime", "192.168.10.12");

        logger.info (" ---- flink-runtime removed from 192.168.10.12");
        pms.removeServerForService("flink-runtime", "192.168.10.12");

        logger.info (" ---- now flink-runtime detected on 192.168.10.13");
        pms.updateServerForService ("flink-runtime", "192.168.10.13");

        assertEquals(2, openedForwarders.size());
        assertEquals(1, closedForwarders.size());

        String firstForwarder = openedForwarders.get(0);
        String secondForwarder = openedForwarders.get(1);

        String closedForwarder = closedForwarders.get(0);

        assertTrue(firstForwarder.endsWith("192.168.10.11/8001"));
        assertTrue(secondForwarder.endsWith("192.168.10.11/8001"));

        assertEquals(closedForwarder, firstForwarder);
    }

    @Test
    public void testServerForServiceManagemement_Kubernetes_kbeProxy() throws Exception {

        assertFalse(recreateTunnelsCalled.get());
        assertFalse(removeForwardersCalled.get());

        pms.updateServerForService("kubernetes-dashboard", "192.168.10.11");

        assertTrue(recreateTunnelsCalled.get());
        assertTrue(removeForwardersCalled.get());

        recreateTunnelsCalled.set(false);
        removeForwardersCalled.set(false);

        pms.updateServerForService("kubernetes-dashboard", "192.168.10.11");

        // should not have been recreated
        assertFalse(recreateTunnelsCalled.get());
        assertFalse(removeForwardersCalled.get());

        pms.updateServerForService("kubernetes-dashboard", "192.168.10.13");

        // since kub service are redirected to poxy on kube master, no tunnel recreation should occur when service moves
        assertFalse(recreateTunnelsCalled.get());
        assertFalse(removeForwardersCalled.get());
    }

    @Test
    public void testServerForServiceManagemement_Kubernetes_noKubeProxy() throws Exception {

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

    @Test
    public void testServerForServiceManagemement() throws Exception {

        assertFalse(recreateTunnelsCalled.get());
        assertFalse(removeForwardersCalled.get());

        pms.updateServerForService("gluster", "192.168.10.11");

        assertTrue(recreateTunnelsCalled.get());
        assertTrue(removeForwardersCalled.get());

        recreateTunnelsCalled.set(false);
        removeForwardersCalled.set(false);

        pms.updateServerForService("gluster", "192.168.10.11");

        // should not have been recreated
        assertFalse(recreateTunnelsCalled.get());
        assertFalse(removeForwardersCalled.get());

        pms.updateServerForService("gluster", "192.168.10.13");

        assertTrue(recreateTunnelsCalled.get());
        assertTrue(removeForwardersCalled.get());
    }
}

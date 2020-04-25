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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.MarathonServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ProxyTunnelConfig;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class MarathonServiceTest extends AbstractSystemTest {

    private static final Logger logger = Logger.getLogger(MarathonServiceTest.class);


    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setupService.setConfigStoragePathInternal(createTempStoragePath());
    }

    public static String createTempStoragePath() throws Exception {
        File dtempFileName = File.createTempFile("test_marathonservice_", "config_storage");
        FileUtils.delete (dtempFileName); // delete file to create directory below

        File configStoragePathFile = new File (dtempFileName.getAbsolutePath() + "/");
        configStoragePathFile.mkdirs();
        return configStoragePathFile.getAbsolutePath();
    }

    @Override
    protected ProxyManagerService createProxyManagerService() {
        return new ProxyManagerService() {
            @Override
            public ProxyTunnelConfig getTunnelConfig(String serviceId) {
                return new ProxyTunnelConfig("dummyService", 12345, "192.178.10.11", 5050);
            }
        };
    }

    @Override
    protected SystemService createSystemService() {
        return new SystemService(false) {
            @Override
            String sendPing(String ipAddress) throws SSHCommandException {
                super.sendPing(ipAddress);
                return "OK";
            }
        };
    }

    private MarathonService resetupMarathonService (MarathonService marathonService) {
        marathonService.setServicesDefinition(servicesDefinition);
        marathonService.setConfigurationService (configurationService);
        marathonService.setSystemService(systemService);
        marathonService.setSshCommandService(sshCommandService);
        marathonService.setSystemOperationService(systemOperationService);
        marathonService.setProxyManagerService(proxyManagerService);
        marathonService.setMemoryComputer(memoryComputer);
        marathonService.setMessagingService(messagingService);
        marathonService.setNotificationService(notificationService);

        systemService.setMarathonService(marathonService);
        return marathonService;
    }

    @Test
    public void testQueryMarathon () throws Exception {

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
                // just return the URI
                return request.getRequestLine().getUri();
            }
        });

        assertEquals("http://localhost:12345/v2/apps/cerebro", marathonService.queryMarathon("apps/cerebro"));

        assertEquals("http://localhost:12345/v2/apps/cerebro", marathonService.queryMarathon("apps/cerebro", "POST"));
    }

    @Test
    public void testUpdateMarathon () throws Exception {

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
                if (request instanceof BasicHttpEntityEnclosingRequest) {
                    return StreamUtils.getAsString(((BasicHttpEntityEnclosingRequest)request).getEntity().getContent());
                }
                return request.getRequestLine().getUri();
            }
        });

        assertEquals("TEST", marathonService.updateMarathon("apps/cerebro", "POST", "TEST"));
    }

    @Test
    public void testApplyMarathonServicesConfig () throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testFindUniqueServiceIP () throws Exception {
        marathonService.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
                return new ServicesInstallStatusWrapper("{\"cerebro_installed_on_IP_192-168-10-11\": \"OK\"}");
            }
        });

        assertEquals ("192.168.10.11", marathonService.findUniqueServiceIP("cerebro"));
    }

    @Test
    public void testUninstallMarathonService () throws Exception {

        final List<String> marathonApiCalls = new ArrayList<>();

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected Pair<String, String> getAndWaitServiceRuntimeNode (String service, int numberOfAttempts) throws MarathonException  {
                return new Pair<>("192.168.10.13", "running");
            }
            @Override
            protected String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
                marathonApiCalls.add(request.getRequestLine().getUri());
                return "{\"deploymentId\": \"1234\"}";
            }
            @Override
            protected void waitForServiceShutdown(String service) throws MarathonException {
                // No Op
            }
        });

        marathonService.uninstallMarathonService("cerebro", "192.168.10.11");

        assertEquals(1, marathonApiCalls.size());
        assertEquals("http://localhost:12345/v2/apps/cerebro", marathonApiCalls.get(0));

        assertTrue(testSSHCommandScript.toString().contains("docker exec -i --user root marathon bash -c \"rm -Rf /var/lib/marathon/docker_registry/docker/registry/v2/repositories/cerebro\""));
        assertTrue(testSSHCommandScript.toString().contains("docker exec -i --user root marathon bash -c \"docker-registry garbage-collect /etc/docker/registry/config.yml\""));

        /*
        System.out.println(testSSHCommandResultBuilder);
        System.err.println(testSSHCommandScript);
        System.err.println(String.join(",", marathonApiCalls));
        */
    }

    @Test
    public void testInstallMarathonService () throws Exception {

        final List<String> marathonApiCalls = new ArrayList<>();

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected Pair<String, String> getAndWaitServiceRuntimeNode (String service, int numberOfAttempts) throws MarathonException  {
                return new Pair<>("192.168.10.13", "running");
            }
            @Override
            protected String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
                marathonApiCalls.add(request.getRequestLine().getUri());
                return "{\"deploymentId\": \"1234\"}";
            }
            @Override
            protected void waitForServiceShutdown(String service) throws MarathonException {
                // No Op
            }
        });

        marathonService.installMarathonService("cerebro", "192.168.10.11");

        // Just testing a few commands
        assertTrue(testSSHCommandScript.toString().contains("tar xfz /tmp/cerebro.tgz --directory=/tmp/"));
        assertTrue(testSSHCommandScript.toString().contains("chmod 755 /tmp/cerebro/setup.sh"));
        assertTrue(testSSHCommandScript.toString().contains("bash /tmp/cerebro/setup.sh 192.168.10.11"));
        assertTrue(testSSHCommandScript.toString().contains("docker image rm eskimo:cerebro_template"));

        // no API calls from backend (it's actually done by setup script)
        assertEquals(0, marathonApiCalls.size());
    }

    @Test
    public void testFetchMarathonServicesStatus () throws Exception {

        final List<String> marathonApiCalls = new ArrayList<>();

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected Pair<String, String> getAndWaitServiceRuntimeNode (String service, int numberOfAttempts) throws MarathonException  {
                return new Pair<>("192.168.10.13", "running");
            }
            @Override
            protected String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
                marathonApiCalls.add(request.getRequestLine().getUri());
                return "{\"deploymentId\": \"1234\"}";
            }
            @Override
            protected void waitForServiceShutdown(String service) throws MarathonException {
                // No Op
            }
        });

        final ConcurrentHashMap<String, String> statusMap = new ConcurrentHashMap<>();

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesStatus();
        configurationService.saveServicesInstallationStatus(servicesInstallStatus);

        MarathonServicesConfigWrapper marathonServicesConfig = StandardSetupHelpers.getStandardMarathonConfig();
        configurationService.saveMarathonServicesConfig(marathonServicesConfig);

        marathonService.fetchMarathonServicesStatus(statusMap, servicesInstallStatus);

        assertEquals(7, statusMap.size());
        assertEquals("OK", statusMap.get("service_cerebro_192-168-10-13"));
        assertEquals("OK", statusMap.get("service_kibana_192-168-10-13"));
        assertEquals("OK", statusMap.get("service_spark-history-server_192-168-10-13"));
        assertEquals("OK", statusMap.get("service_gdash_192-168-10-13"));
        assertEquals("TD", statusMap.get("service_grafana_192-168-10-13"));
        assertEquals("OK", statusMap.get("service_zeppelin_192-168-10-13"));
        assertEquals("OK", statusMap.get("service_kafka-manager_192-168-10-13"));
    }

    @Test
    public void testShouldInstall () throws Exception {

        MarathonServicesConfigWrapper marathonServicesConfig = StandardSetupHelpers.getStandardMarathonConfig();

        assertTrue (marathonService.shouldInstall(marathonServicesConfig, "cerebro"));
        assertTrue (marathonService.shouldInstall(marathonServicesConfig, "kibana"));
        assertTrue (marathonService.shouldInstall(marathonServicesConfig, "spark-history-server"));
        assertTrue (marathonService.shouldInstall(marathonServicesConfig, "gdash"));
        assertFalse (marathonService.shouldInstall(marathonServicesConfig, "grafana"));
        assertTrue (marathonService.shouldInstall(marathonServicesConfig, "zeppelin"));
        assertTrue (marathonService.shouldInstall(marathonServicesConfig, "kafka-manager"));
    }

    @Test
    public void testShowJournalMarathon () throws Exception {

        final List<String> marathonApiCalls = new ArrayList<>();

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected String queryMarathon (String endpoint) throws MarathonException {
                try {
                    if (endpoint.equals("info")) {
                        return StreamUtils.getAsString(ResourceUtils.getResourceAsStream("MarathonServiceTest/marathon-info.json"));
                    } else if (endpoint.equals("apps/zeppelin")) {
                        return StreamUtils.getAsString(ResourceUtils.getResourceAsStream("MarathonServiceTest/zeppelin-framework-info.json"));
                    }
                } catch (IOException e) {
                    throw new MarathonException(e);
                }
                return null;
            }
            @Override
            protected String queryMesosAgent (String host, String endpoint) throws MarathonException {
                try {
                    if (endpoint.equals("state")) {
                        return StreamUtils.getAsString(ResourceUtils.getResourceAsStream("MarathonServiceTest/mesos-agent-info.json"));
                    } else if (endpoint.endsWith("stderr")) {
                        return "(STDOUT)";
                    } else if (endpoint.endsWith("stdout")) {
                        return "(STDERR)";
                    }
                } catch (IOException e) {
                    throw new MarathonException(e);
                }
                return null;
            }
        });

        String expectedResults = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("MarathonServiceTest/expected-result.txt"));
        String result = marathonService.showJournalMarathonInternal(servicesDefinition.getService("zeppelin"));
        assertEquals(expectedResults, result);
    }

    @Test
    public void testStartServiceMarathon () throws Exception {

        final List<String> marathonApiCalls = new ArrayList<>();

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected Pair<String, String> getAndWaitServiceRuntimeNode (String service, int numberOfAttempts) throws MarathonException  {
                return new Pair<>("192.168.10.13", "OK");
            }
            @Override
            protected String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
                marathonApiCalls.add(request.getRequestLine().getUri());
                return "{\"deploymentId\": \"1234\"}";
            }
            @Override
            protected void waitForServiceShutdown(String service) throws MarathonException {
                // No Op
            }
        });

        marathonService.startServiceMarathon(servicesDefinition.getService("cerebro"));

        System.out.println(testSSHCommandResultBuilder);
        System.err.println(testSSHCommandScript);
        System.err.println(String.join(",", marathonApiCalls));

        assertEquals(1, marathonApiCalls.size());
        assertEquals("http://localhost:12345/v2/apps/cerebro", marathonApiCalls.get(0));

    }

    @Test
    public void testStopServiceMarathon () throws Exception {

        final List<String> marathonApiCalls = new ArrayList<>();

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected Pair<String, String> getAndWaitServiceRuntimeNode (String service, int numberOfAttempts) throws MarathonException  {
                return new Pair<>("192.168.10.13", "running");
            }
            @Override
            protected String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
                marathonApiCalls.add(request.getRequestLine().getUri());
                return "{\"deploymentId\": \"1234\"}";
            }
            @Override
            protected void waitForServiceShutdown(String service) throws MarathonException {
                // No Op
            }
        });

        marathonService.stopServiceMarathon(servicesDefinition.getService("cerebro"));

        assertEquals(1, marathonApiCalls.size());
        assertEquals("http://localhost:12345/v2/apps/cerebro/tasks?scale=true", marathonApiCalls.get(0));
    }

    @Test
    public void testRestartServiceMarathon() throws Exception {


        final List<String> marathonApiCalls = new ArrayList<>();
        final AtomicInteger callCounter = new AtomicInteger(0);

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected Pair<String, String> getAndWaitServiceRuntimeNode (String service, int numberOfAttempts) throws MarathonException  {
                if (callCounter.incrementAndGet() > 2) {
                    return new Pair<>("192.168.10.13", "OK");
                }
                return new Pair<>("192.168.10.13", "running");

            }
            @Override
            protected String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
                marathonApiCalls.add(request.getRequestLine().getUri());
                return "{\"deploymentId\": \"1234\"}";
            }
            @Override
            protected void waitForServiceShutdown(String service) throws MarathonException {
                // No Op
            }
        });

        marathonService.restartServiceMarathon(servicesDefinition.getService("cerebro"));

        assertEquals(2, marathonApiCalls.size());
        assertEquals("http://localhost:12345/v2/apps/cerebro/tasks?scale=true", marathonApiCalls.get(0));
        assertEquals("http://localhost:12345/v2/apps/cerebro", marathonApiCalls.get(1));
    }
}

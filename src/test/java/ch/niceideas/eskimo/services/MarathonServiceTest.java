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
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

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

    @Override
    protected SetupService createSetupService() {
        return new SetupService() {
            @Override
            public String findLastPackageFile(String prefix, String packageName) {
                return prefix+"_"+packageName+"_dummy_1.dummy";
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

        ServicesInstallStatusWrapper serviceInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        serviceInstallStatus.removeInstallationFlag("cerebro", "MARATHON_NODE");
        serviceInstallStatus.removeInstallationFlag("gdash", "MARATHON_NODE");
        serviceInstallStatus.removeInstallationFlag("kafka-manager", "MARATHON_NODE");
        serviceInstallStatus.removeInstallationFlag("kibana", "MARATHON_NODE");
        serviceInstallStatus.removeInstallationFlag("spark-history-server", "MARATHON_NODE");
        serviceInstallStatus.removeInstallationFlag("zeppelin", "MARATHON_NODE");
        serviceInstallStatus.setValueForPath("grafana_installed_on_IP_MARATHON_NODE", "OK");

        MarathonOperationsCommand command = MarathonOperationsCommand.create (
                servicesDefinition,
                systemService,
                serviceInstallStatus,
                new MarathonServicesConfigWrapper(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("MarathonServiceTest/marathon-services-config.json"), "UTF-8"))
        );

        final List<String> installList = new LinkedList<>();
        final List<String> uninstallList = new LinkedList<>();
        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
                if (request instanceof BasicHttpEntityEnclosingRequest) {
                    return StreamUtils.getAsString(((BasicHttpEntityEnclosingRequest)request).getEntity().getContent());
                }
                return request.getRequestLine().getUri();
            }
            @Override
            void installMarathonService(String service, String marathonIpAddress) {
                installList.add (service+"-"+marathonIpAddress);
            }
            @Override
            void uninstallMarathonService(String service, String marathonIpAddress) throws SystemException {
                uninstallList.add (service+"-"+marathonIpAddress);
            }
        });

        MarathonException exception = assertThrows(MarathonException.class, () -> {
            marathonService.applyMarathonServicesConfig(command);
        });
        assertNotNull(exception);
        assertEquals("ch.niceideas.eskimo.services.SystemException: Marathon doesn't seem to be installed", exception.getMessage());

        configurationService.saveServicesInstallationStatus(serviceInstallStatus);

        configurationService.saveNodesConfig(StandardSetupHelpers.getStandard2NodesSetup());

        systemService.setLastStatusForTest(StandardSetupHelpers.getStandard2NodesSystemStatus());

        marathonService.setNodesConfigurationService(new NodesConfigurationService() {
            @Override
            String installTopologyAndSettings(NodesConfigWrapper nodesConfig, MarathonServicesConfigWrapper marathonConfig,
                                              MemoryModel memoryModel, String ipAddress, Set<String> deadIps) {
                // No-Op
                return "OK";
            }
        });

        marathonService.applyMarathonServicesConfig(command);

        assertEquals(6, installList.size());
        assertEquals("cerebro-192.168.10.11," +
                "gdash-192.168.10.11," +
                "kafka-manager-192.168.10.11," +
                "kibana-192.168.10.11," +
                "spark-history-server-192.168.10.11," +
                "zeppelin-192.168.10.11", String.join(",", installList));

        assertEquals(1, uninstallList.size());
        assertEquals("grafana-192.168.10.11", String.join(",", uninstallList));
    }

    @Test
    public void testGetAndWaitServiceRuntimeNode() throws Exception {

        // 1. marathon down
        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected String queryMarathon(String endpoint) throws MarathonException {
                throw new MarathonException("Marathon Down");
            }
        });
        marathonService.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
        });

        Pair<String, String> status = marathonService.getAndWaitServiceRuntimeNode ("cerebro", 1);
        assertNotNull(status);
        assertEquals("MARATHON_NA", status.getKey());
        assertEquals("NA", status.getValue());

        // 2. service does not exist
        marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected String queryMarathon(String endpoint) throws MarathonException {
                return "{ \"message\" : \"cerebro does not exist\"} ";
            }
        });
        marathonService.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
        });

        status = marathonService.getAndWaitServiceRuntimeNode ("cerebro", 1);
        assertNotNull(status);
        assertEquals(null, status.getKey());
        assertEquals("NA", status.getValue());

        /* FIXME Find out why I cann't make this work
        BUT DON'T CHANGE ANYTHOING IN THE CODE : WHAT I HAVE NOW WORKS !!
        // 3. Service not up and running
        marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected String queryMarathon(String endpoint) throws MarathonException {
                return "{ \"message\" : \"cerebro OK but no node\"} ";
            }
        });
        marathonService.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
        });

        status = marathonService.getAndWaitServiceRuntimeNode ("cerebro", 2); // 2 HERE IS IMPORTANT !!
        assertNotNull(status);
        assertEquals(null, status.getKey());
        assertEquals("notOK", status.getValue());
        */

        // 4. Service in deployment
        marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected String queryMarathon(String endpoint) throws MarathonException {
                return "{ \"app\" : { \"tasks\" : [ { \"host\" : \"192.168.10.20\" } ] } } ";
            }
        });
        marathonService.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
        });

        status = marathonService.getAndWaitServiceRuntimeNode ("cerebro", 1);
        assertNotNull(status);
        assertEquals("192.168.10.20", status.getKey());
        assertEquals("notOK", status.getValue());

        // 5. service running
        marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected String queryMarathon(String endpoint) throws MarathonException {
                return "{ \"app\" : { \"tasks\" : [ { \"host\" : \"192.168.10.20\" } ], \"tasksRunning\": 1 } } ";
            }
        });
        marathonService.setConfigurationService(new ConfigurationService() {
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
        });

        status = marathonService.getAndWaitServiceRuntimeNode ("cerebro", 1);
        assertNotNull(status);
        assertEquals("192.168.10.20", status.getKey());
        assertEquals("running", status.getValue());
    }

    @Test
    public void testEnsureMarathonAvailability() throws Exception {

        // 1. no status available (exception)
        marathonService.setSystemService(new SystemService() {
            @Override
            public SystemStatusWrapper getStatus() throws StatusExceptionWrapperException {
                throw new StatusExceptionWrapperException (new Exception("none"));
            }
        });

        MarathonException exception = assertThrows(MarathonException.class, () -> {
            marathonService.ensureMarathonAvailability();
        });
        assertNotNull(exception);
        assertEquals("Couldn't get last marathon Service status", exception.getMessage());

        // 2. Marathon not available (not installed)
        marathonService.setSystemService(new SystemService() {
            @Override
            public SystemStatusWrapper getStatus() throws StatusExceptionWrapperException {
                return new SystemStatusWrapper("{}");
            }
        });

        exception = assertThrows(MarathonException.class, () -> {
            marathonService.ensureMarathonAvailability();
        });
        assertNotNull(exception);
        assertEquals("Marathon is not available", exception.getMessage());

        // 3. Marathon not up and running
        marathonService.setSystemService(new SystemService() {
            @Override
            public SystemStatusWrapper getStatus() throws StatusExceptionWrapperException {
                return new SystemStatusWrapper("{\"service_marathon_192-168-10-11\": \"KO\"}");
            }
        });

        exception = assertThrows(MarathonException.class, () -> {
            marathonService.ensureMarathonAvailability();
        });
        assertNotNull(exception);
        assertEquals("Marathon is not properly running", exception.getMessage());

        // 4. Service OK
        marathonService.setSystemService(new SystemService() {
            @Override
            public SystemStatusWrapper getStatus() throws StatusExceptionWrapperException {
                return new SystemStatusWrapper("{\"service_marathon_192-168-10-11\": \"OK\"}");
            }
        });

        marathonService.ensureMarathonAvailability();
    }

    @Test
    public void testWaitForServiceShutdown() throws Exception {

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected Pair<String, String> getAndWaitServiceRuntimeNode (String service, int numberOfAttempts) throws MarathonException  {
                return new Pair<>("192.168.10.11", "notOk");
            }
        });

        Exception error = null;
        try {
            marathonService.waitForServiceShutdown("cerebro");
        } catch (Exception e) {
            error = e;
        }
        assertNull (error);
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
    public void testFetchMarathonServicesStatusNominal () throws Exception {

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected Pair<String, String> getAndWaitServiceRuntimeNode (String service, int numberOfAttempts) throws MarathonException  {
                return new Pair<>("192.168.10.13", "running");
            }
            @Override
            protected String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
                return "{\"deploymentId\": \"1234\"}";
            }
            @Override
            protected void waitForServiceShutdown(String service) throws MarathonException {
                // No Op
            }
        });

        final ConcurrentHashMap<String, String> statusMap = new ConcurrentHashMap<>();

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
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
    public void testFetchMarathonServicesStatusServiceDown () throws Exception {

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected Pair<String, String> getAndWaitServiceRuntimeNode (String service, int numberOfAttempts) throws MarathonException  {
                if (service.equals("cerebro") || service.equals("gdash")) {
                    return new Pair<>("192.168.10.13", "notOK");
                } else {
                    return new Pair<>("192.168.10.13", "running");
                }
            }
            @Override
            protected String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
                return "{\"deploymentId\": \"1234\"}";
            }
            @Override
            protected void waitForServiceShutdown(String service) throws MarathonException {
                // No Op
            }
        });

        final ConcurrentHashMap<String, String> statusMap = new ConcurrentHashMap<>();

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        configurationService.saveServicesInstallationStatus(servicesInstallStatus);

        MarathonServicesConfigWrapper marathonServicesConfig = StandardSetupHelpers.getStandardMarathonConfig();
        configurationService.saveMarathonServicesConfig(marathonServicesConfig);

        marathonService.fetchMarathonServicesStatus(statusMap, servicesInstallStatus);

        assertEquals(7, statusMap.size());
        assertEquals("KO", statusMap.get("service_cerebro_192-168-10-13"));
        assertEquals("OK", statusMap.get("service_kibana_192-168-10-13"));
        assertEquals("OK", statusMap.get("service_spark-history-server_192-168-10-13"));
        assertEquals("KO", statusMap.get("service_gdash_192-168-10-13"));
        assertEquals("TD", statusMap.get("service_grafana_192-168-10-13"));
        assertEquals("OK", statusMap.get("service_zeppelin_192-168-10-13"));
        assertEquals("OK", statusMap.get("service_kafka-manager_192-168-10-13"));
    }

    @Test
    public void testFetchMarathonServicesStatusMarathonServiceDown () throws Exception {

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected Pair<String, String> getAndWaitServiceRuntimeNode (String service, int numberOfAttempts) throws MarathonException  {
                return new Pair<>(MarathonService.MARATHON_NA_FLAG, "NA");
            }
            @Override
            protected String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
                return "{\"deploymentId\": \"1234\"}";
            }
            @Override
            protected void waitForServiceShutdown(String service) throws MarathonException {
                // No Op
            }
        });

        final ConcurrentHashMap<String, String> statusMap = new ConcurrentHashMap<>();

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        configurationService.saveServicesInstallationStatus(servicesInstallStatus);

        MarathonServicesConfigWrapper marathonServicesConfig = StandardSetupHelpers.getStandardMarathonConfig();
        configurationService.saveMarathonServicesConfig(marathonServicesConfig);

        marathonService.fetchMarathonServicesStatus(statusMap, servicesInstallStatus);

        // grafana is away !
        //assertEquals(7, statusMap.size());
        assertEquals(6, statusMap.size());

        assertEquals("KO", statusMap.get("service_cerebro_192-168-10-11"));
        assertEquals("KO", statusMap.get("service_kibana_192-168-10-11"));
        assertEquals("KO", statusMap.get("service_spark-history-server_192-168-10-11"));
        assertEquals("KO", statusMap.get("service_gdash_192-168-10-11"));
        // This one is not referenced anymore in this case
        //assertEquals("KO", statusMap.get("service_grafana_192-168-10-13"));
        assertEquals("KO", statusMap.get("service_zeppelin_192-168-10-11"));
        assertEquals("KO", statusMap.get("service_kafka-manager_192-168-10-11"));
    }

    @Test
    public void testFetchMarathonServicesStatusMarathonNodeDown () throws Exception {

        MarathonService marathonService = resetupMarathonService(new MarathonService() {
            @Override
            protected Pair<String, String> getAndWaitServiceRuntimeNode (String service, int numberOfAttempts) throws MarathonException  {
                return new Pair<>("192.168.10.13", "running");
            }
            @Override
            protected String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
                return "{\"deploymentId\": \"1234\"}";
            }
            @Override
            protected void waitForServiceShutdown(String service) throws MarathonException {
                // No Op
            }
        });

        systemService = new SystemService(false) {
            @Override
            String sendPing(String ipAddress) throws SSHCommandException {
                super.sendPing(ipAddress);
                throw new SSHCommandException("Node dead");
            }
        };
        systemService.setNodeRangeResolver(nodeRangeResolver);
        systemService.setSetupService(setupService);
        systemService.setProxyManagerService(proxyManagerService);
        systemService.setSshCommandService(sshCommandService);
        systemService.setServicesDefinition(servicesDefinition);
        systemService.setMarathonService(marathonService);
        systemService.setMessagingService(messagingService);
        systemService.setNotificationService(notificationService);
        marathonService.setSystemService(systemService);

        final ConcurrentHashMap<String, String> statusMap = new ConcurrentHashMap<>();

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        configurationService.saveServicesInstallationStatus(servicesInstallStatus);

        MarathonServicesConfigWrapper marathonServicesConfig = StandardSetupHelpers.getStandardMarathonConfig();
        configurationService.saveMarathonServicesConfig(marathonServicesConfig);

        marathonService.fetchMarathonServicesStatus(statusMap, servicesInstallStatus);

        // grafana is away !
        //assertEquals(7, statusMap.size());
        assertEquals(6, statusMap.size());

        assertEquals("KO", statusMap.get("service_cerebro_192-168-10-11"));
        assertEquals("KO", statusMap.get("service_kibana_192-168-10-11"));
        assertEquals("KO", statusMap.get("service_spark-history-server_192-168-10-11"));
        assertEquals("KO", statusMap.get("service_gdash_192-168-10-11"));
        // This one is not referenced anymore in this case
        //assertEquals("KO", statusMap.get("service_grafana_192-168-10-13"));
        assertEquals("KO", statusMap.get("service_zeppelin_192-168-10-11"));
        assertEquals("KO", statusMap.get("service_kafka-manager_192-168-10-11"));
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

        SystemService ss = new SystemService() {
            @Override
            public SystemStatusWrapper getStatus() throws SystemService.StatusExceptionWrapperException {
                SystemStatusWrapper retStatus = new SystemStatusWrapper("{}");
                retStatus.setValueForPath(SystemStatusWrapper.SERVICE_PREFIX + "marathon_192-168-10-11", "OK");
                return retStatus;
            }
        };
        ss.setNotificationService(notificationService);
        ss.setMessagingService(messagingService);
        marathonService.setSystemService(ss);

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

        SystemService ss = new SystemService() {
            @Override
            public SystemStatusWrapper getStatus() throws SystemService.StatusExceptionWrapperException {
                SystemStatusWrapper retStatus = new SystemStatusWrapper("{}");
                retStatus.setValueForPath(SystemStatusWrapper.SERVICE_PREFIX + "marathon_192-168-10-11", "OK");
                return retStatus;
            }
        };
        ss.setNotificationService(notificationService);
        ss.setMessagingService(messagingService);
        marathonService.setSystemService(ss);

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
        SystemService ss = new SystemService() {
            @Override
            public SystemStatusWrapper getStatus() throws SystemService.StatusExceptionWrapperException {
                SystemStatusWrapper retStatus = new SystemStatusWrapper("{}");
                retStatus.setValueForPath(SystemStatusWrapper.SERVICE_PREFIX + "marathon_192-168-10-11", "OK");
                return retStatus;
            }
        };
        ss.setNotificationService(notificationService);
        ss.setMessagingService(messagingService);
        marathonService.setSystemService(ss);

        marathonService.restartServiceMarathon(servicesDefinition.getService("cerebro"));

        assertEquals(2, marathonApiCalls.size());
        assertEquals("http://localhost:12345/v2/apps/cerebro/tasks?scale=true", marathonApiCalls.get(0));
        assertEquals("http://localhost:12345/v2/apps/cerebro", marathonApiCalls.get(1));
    }
}

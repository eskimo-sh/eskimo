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

import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.model.service.MemoryModel;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.utils.KubeStatusParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class KubernetesServiceTest extends AbstractSystemTest {

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setupService.setConfigStoragePathInternal(createTempStoragePath());
    }

    public static String createTempStoragePath() throws Exception {
        File dtempFileName = File.createTempFile("test_kubeservice_", "config_storage");
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

            @Override
            public void updateServerForService(String serviceName, String runtimeNode) {
                // noOp
            }
        };
    }

    @Override
    protected SystemService createSystemService() {
        return new SystemService(false) {
            @Override
            String sendPing(String node) throws SSHCommandException {
                super.sendPing(node);
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

    private KubernetesService resetupKubernetesService(KubernetesService kubernetesService) {
        kubernetesService.setServicesDefinition(servicesDefinition);
        kubernetesService.setConfigurationService (configurationService);
        kubernetesService.setSystemService(systemService);
        kubernetesService.setSshCommandService(sshCommandService);
        kubernetesService.setSystemOperationService(systemOperationService);
        kubernetesService.setProxyManagerService(proxyManagerService);
        kubernetesService.setMemoryComputer(memoryComputer);
        kubernetesService.setNotificationService(notificationService);
        kubernetesService.setConnectionManagerService(connectionManagerService);
        kubernetesService.setOperationsMonitoringService(operationsMonitoringService);
        kubernetesService.setNodeRangeResolver(nodeRangeResolver);

        systemService.setKubernetesService(kubernetesService);
        return kubernetesService;
    }

    @Test
    public void testApplyKubernetesServicesConfig () throws Exception {

        ServicesInstallStatusWrapper serviceInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        serviceInstallStatus.removeInstallationFlag("cerebro", "KUBERNETES_NODE");
        serviceInstallStatus.removeInstallationFlag("kafka-manager", "KUBERNETES_NODE");
        serviceInstallStatus.removeInstallationFlag("kibana", "KUBERNETES_NODE");
        serviceInstallStatus.removeInstallationFlag("spark-history-server", "KUBERNETES_NODE");
        serviceInstallStatus.removeInstallationFlag("zeppelin", "KUBERNETES_NODE");
        serviceInstallStatus.setValueForPath("grafana_installed_on_IP_KUBERNETES_NODE", "OK");

        KubernetesOperationsCommand command = KubernetesOperationsCommand.create (
                servicesDefinition,
                systemService,
                StandardSetupHelpers.getStandardKubernetesConfig(),
                serviceInstallStatus,
                new KubernetesServicesConfigWrapper(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("KubernetesServiceTest/kubernetes-services-config.json"), "UTF-8"))
        );

        final List<String> installList = new LinkedList<>();
        final List<String> uninstallList = new LinkedList<>();
        KubernetesService kubernetesService = resetupKubernetesService(new KubernetesService() {

            @Override
            void installService(KubernetesOperationsCommand.KubernetesOperationId operationId, String kubernetesNode) {
                installList.add (operationId.getService()+"-"+ kubernetesNode);
            }
            @Override
            void uninstallService(KubernetesOperationsCommand.KubernetesOperationId operationId, String kubernetesNode) throws SystemException {
                uninstallList.add (operationId.getService()+"-"+ kubernetesNode);
            }
        });

        KubernetesException exception = assertThrows(KubernetesException.class, () -> kubernetesService.applyServicesConfig(command));
        assertNotNull(exception);
        assertEquals("ch.niceideas.eskimo.services.SystemException: Kubernetes doesn't seem to be installed. Kubernetes services configuration is saved but will need to be re-applied when k8s-master is available.", exception.getMessage());

        configurationService.saveServicesInstallationStatus(serviceInstallStatus);

        configurationService.saveNodesConfig(StandardSetupHelpers.getStandard2NodesSetup());

        systemService.setLastStatusForTest(StandardSetupHelpers.getStandard2NodesSystemStatus());

        kubernetesService.setNodesConfigurationService(new NodesConfigurationService() {
            @Override
            void installTopologyAndSettings(NodesConfigWrapper nodesConfig, KubernetesServicesConfigWrapper kubeServicesConfig,
                                            MemoryModel memoryModel, String ipAddress) {
                // No-Op
            }
        });

        kubernetesService.applyServicesConfig(command);

        assertEquals(5, installList.size());
        assertEquals("" +
                "cerebro-192.168.10.11," +
                "spark-history-server-192.168.10.11," +
                "kafka-manager-192.168.10.11," +
                "kibana-192.168.10.11," +
                "zeppelin-192.168.10.11", String.join(",", installList));

        assertEquals(5, uninstallList.size());
        assertEquals("" +
                "elasticsearch-192.168.10.11," +
                "grafana-192.168.10.11," +
                "kafka-192.168.10.11," +
                "logstash-192.168.10.11," +
                "spark-runtime-192.168.10.11", String.join(",", uninstallList));
    }


    @Test
    public void testUninstallKubernetesService () throws Exception {

        final List<String> kubernetesApiCalls = new ArrayList<>();

        KubernetesService kubernetesService = resetupKubernetesService(new KubernetesService() {

        });

        ServicesInstallStatusWrapper serviceInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        serviceInstallStatus.setValueForPath("grafana_installed_on_IP_KUBERNETES_NODE", "OK");

        KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("KubernetesServiceTest/kubernetes-services-config.json"), "UTF-8"));
        kubeServicesConfig.setValueForPath("cerebro_install", "off");

        KubernetesOperationsCommand command = KubernetesOperationsCommand.create (
                servicesDefinition,
                systemService,
                StandardSetupHelpers.getStandardKubernetesConfig(),
                serviceInstallStatus,
                kubeServicesConfig
        );

        operationsMonitoringService.operationsStarted(command);

        kubernetesService.uninstallService(new KubernetesOperationsCommand.KubernetesOperationId("uninstallation", "cerebro"), "192.168.10.11");

        //System.err.println (testSSHCommandScript.toString());

        assertEquals("eskimo-kubectl uninstall cerebro 192.168.10.11\n", testSSHCommandScript.toString());

        operationsMonitoringService.operationsFinished(true);
    }

    @Test
    public void testInstallKubernetesService () throws Exception {

        final List<String> kubernetesApiCalls = new ArrayList<>();

        KubernetesService kubernetesService = resetupKubernetesService(new KubernetesService() {

        });

        ServicesInstallStatusWrapper serviceInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        serviceInstallStatus.removeInstallationFlag("cerebro", "KUBERNETES_NODE");
        serviceInstallStatus.setValueForPath("grafana_installed_on_IP_KUBERNETES_NODE", "OK");

        KubernetesOperationsCommand command = KubernetesOperationsCommand.create (
                servicesDefinition,
                systemService,
                StandardSetupHelpers.getStandardKubernetesConfig(),
                serviceInstallStatus,
                new KubernetesServicesConfigWrapper(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("KubernetesServiceTest/kubernetes-services-config.json"), "UTF-8"))
        );

        operationsMonitoringService.operationsStarted(command);

        kubernetesService.installService(new KubernetesOperationsCommand.KubernetesOperationId("installation", "cerebro"), "192.168.10.11");

        // Just testing a few commands
        assertTrue(testSSHCommandScript.toString().contains("tar xfz /tmp/cerebro.tgz --directory=/tmp/"));
        assertTrue(testSSHCommandScript.toString().contains("chmod 755 /tmp/cerebro/setup.sh"));
        assertTrue(testSSHCommandScript.toString().contains("bash /tmp/cerebro/setup.sh 192.168.10.11"));
        assertTrue(testSSHCommandScript.toString().contains("docker image rm eskimo:cerebro_template"));

        // no API calls from backend (it's actually done by setup script)
        assertEquals(0, kubernetesApiCalls.size());

        operationsMonitoringService.operationsFinished(true);
    }

    @Test
    public void testFetchKubernetesServicesStatusNominal () throws Exception {

        KubernetesService kubernetesService = resetupKubernetesService(new KubernetesService() {
            @Override
            protected KubeStatusParser getKubeStatusParser() throws KubernetesException {
                String allPodStatus = "" +
                        "NAMESPACE              NAME                                         READY   STATUS    RESTARTS      AGE   IP              NODE            NOMINATED NODE   READINESS GATES\n" +
                        "default                cerebro-65d5556459-fjwh9                     1/1     Running     1 (47m ago)   54m   192.168.10.11   192.168.10.11   <none>           <none>\n" +
                        "default                kibana-65d55564519-fjwh8                     1/1     Error       1 (47m ago)   54m   192.168.10.11   192.168.10.11   <none>           <none>\n";
                String allServicesStatus = "" +
                        "NAMESPACE              NAME                        TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)                  AGE   SELECTOR\n" +
                        "default                cerebro                     ClusterIP   10.254.38.33     <none>        31900/TCP                13h   k8s-app=cerebro\n" +
                        "default                kibana                      ClusterIP   10.254.41.25     <none>        32000/TCP                13h   k8s-app=kibana\n";
                String registryServices = "" +
                        "cerebro\n" +
                        "kibana\n";
                return new KubeStatusParser(allPodStatus, allServicesStatus, registryServices, servicesDefinition);
            }
        });

        final ConcurrentHashMap<String, String> statusMap = new ConcurrentHashMap<>();

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        configurationService.saveServicesInstallationStatus(servicesInstallStatus);

        KubernetesServicesConfigWrapper kubernetesServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        configurationService.saveKubernetesServicesConfig(kubernetesServicesConfig);

        kubernetesService.fetchKubernetesServicesStatus(statusMap, servicesInstallStatus);

        assertEquals(9, statusMap.size());
        assertEquals("OK", statusMap.get("service_cerebro_192-168-10-11"));
        assertEquals("KO", statusMap.get("service_kibana_192-168-10-11"));
        assertEquals("NA", statusMap.get("service_spark-history-server_192-168-10-11"));
    }

    @Test
    public void testFetchKubernetesServicesStatusKubernetesNodeDown () throws Exception {

        KubernetesService kubernetesService = resetupKubernetesService(new KubernetesService() {

        });

        systemService = new SystemService(false) {
            @Override
            String sendPing(String node) throws SSHCommandException {
                super.sendPing(node);
                throw new SSHCommandException("Node dead");
            }
        };
        systemService.setNodeRangeResolver(nodeRangeResolver);
        systemService.setSetupService(setupService);
        systemService.setProxyManagerService(proxyManagerService);
        systemService.setSshCommandService(sshCommandService);
        systemService.setServicesDefinition(servicesDefinition);
        systemService.setKubernetesService(kubernetesService);
        systemService.setNotificationService(notificationService);
        kubernetesService.setSystemService(systemService);

        final ConcurrentHashMap<String, String> statusMap = new ConcurrentHashMap<>();

        ServicesInstallStatusWrapper servicesInstallStatus = StandardSetupHelpers.getStandard2NodesInstallStatus();
        configurationService.saveServicesInstallationStatus(servicesInstallStatus);

        KubernetesServicesConfigWrapper kubernetesServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();
        configurationService.saveKubernetesServicesConfig(kubernetesServicesConfig);

        kubernetesService.fetchKubernetesServicesStatus(statusMap, servicesInstallStatus);

        // grafana is away !
        //assertEquals(7, statusMap.size());
        assertEquals(9, statusMap.size());

        assertEquals("KO", statusMap.get("service_cerebro_192-168-10-11"));
        assertEquals("KO", statusMap.get("service_kibana_192-168-10-11"));
        assertEquals("KO", statusMap.get("service_spark-history-server_192-168-10-11"));
        // This one is not referenced anymore in this case
        //assertEquals("KO", statusMap.get("service_grafana_192-168-10-13"));
        assertEquals("KO", statusMap.get("service_zeppelin_192-168-10-11"));
        assertEquals("KO", statusMap.get("service_kafka-manager_192-168-10-11"));
    }

    @Test
    public void testShouldInstall () throws Exception {

        KubernetesServicesConfigWrapper kubeServicesConfig = StandardSetupHelpers.getStandardKubernetesConfig();

        assertTrue (kubernetesService.shouldInstall(kubeServicesConfig, "cerebro"));
        assertTrue (kubernetesService.shouldInstall(kubeServicesConfig, "kibana"));
        assertTrue (kubernetesService.shouldInstall(kubeServicesConfig, "spark-history-server"));
        assertFalse (kubernetesService.shouldInstall(kubeServicesConfig, "grafana"));
        assertTrue (kubernetesService.shouldInstall(kubeServicesConfig, "zeppelin"));
        assertTrue (kubernetesService.shouldInstall(kubeServicesConfig, "kafka-manager"));
    }

    @Test
    public void testShowJournalKubernetes () throws Exception {
        configurationService.saveServicesInstallationStatus(StandardSetupHelpers.getStandard2NodesInstallStatus());
        kubernetesService.showJournal(servicesDefinition.getService("cerebro"), "192.168.10.11");
        assertEquals ("eskimo-kubectl logs cerebro 192.168.10.11", testSSHCommandScript.toString().trim());
    }

    @Test
    public void testStartServiceKubernetes () throws Exception {
        configurationService.saveServicesInstallationStatus(StandardSetupHelpers.getStandard2NodesInstallStatus());
        kubernetesService.startService(servicesDefinition.getService("cerebro"), "192.168.10.11");
        assertEquals ("eskimo-kubectl start cerebro 192.168.10.11", testSSHCommandScript.toString().trim());
    }

    @Test
    public void testStopServiceKubernetes () throws Exception {
        configurationService.saveServicesInstallationStatus(StandardSetupHelpers.getStandard2NodesInstallStatus());
        kubernetesService.stopService(servicesDefinition.getService("cerebro"), "192.168.10.11");
        assertEquals ("eskimo-kubectl stop cerebro 192.168.10.11", testSSHCommandScript.toString().trim());
    }

    @Test
    public void testRestartServiceKubernetes() throws Exception {
        configurationService.saveServicesInstallationStatus(StandardSetupHelpers.getStandard2NodesInstallStatus());
        kubernetesService.restartService(servicesDefinition.getService("cerebro"), "192.168.10.11");
        assertEquals ("eskimo-kubectl restart cerebro 192.168.10.11", testSSHCommandScript.toString().trim());
    }

}

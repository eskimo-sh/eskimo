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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.model.ServiceOperationsCommand;
import ch.niceideas.eskimo.model.ServicesSettingsWrapper;
import ch.niceideas.eskimo.model.SettingsOperationsCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ServicesSettingsServiceTest extends AbstractSystemTest {

    private String jsonConfig = null;
    private String testForm = null;

    private ServicesSettingsService scs;

    private AtomicReference<ServiceOperationsCommand> processedCommand = new AtomicReference<>();

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testConfig.json"));
        testForm = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testForm.json"));

        scs = new ServicesSettingsService();

        scs.setSystemService(systemService);
        scs.setSystemOperationService(systemOperationService);
        scs.setMemoryComputer(memoryComputer);
        scs.setOperationsMonitoringService(operationsMonitoringService);
        scs.setServicesInstallationSorter(servicesInstallationSorter);
        scs.setConfigurationService(configurationService);
        scs.setServicesDefinition(servicesDefinition);
        scs.setNodesConfigurationService(nodesConfigurationService);

        servicesDefinition = new ServicesDefinition() {
            @Override
            public String getAllServicesString() {
                return "kafka zookeeper ntp mesos-master spark-runtime kibana cerebro zeppelin kafka-manager gluster spark-history-server prometheus grafana";
            }
            @Override
            public String[] listAllServices() {
                return new String[] {"kafka",  "zookeeper", "ntp", "mesos-master", "spark-runtime", "kibana", "cerebro", "zeppelin", "kafka-manager", "gluster", "spark-history-server", "elasticsearch", "prometheus", "grafana"};
            }
        };
        servicesDefinition.afterPropertiesSet();
        scs.setServicesDefinition(servicesDefinition);

        setupService.setConfigStoragePathInternal(SystemServiceTest.createTempStoragePath());
    }

    @Override
    protected NodesConfigurationService createNodesConfigurationService() {
        return new NodesConfigurationService() {
            @Override
            public void applyNodesConfig(ServiceOperationsCommand command) {
                processedCommand.set(command);
            }
        };
    }

    @Test
    public void testSaveAndApplyServicesConfig() throws Exception {
        //fail ("To Be Implemented");

        configurationService.saveNodesConfig(StandardSetupHelpers.getStandard2NodesSetup());
        configurationService.saveKubernetesServicesConfig(StandardSetupHelpers.getStandardKubernetesConfig());
        configurationService.saveServicesInstallationStatus(StandardSetupHelpers.getStandard2NodesInstallStatus());

        configurationService.saveServicesSettings(new ServicesSettingsWrapper(jsonConfig));

        SettingsOperationsCommand command = SettingsOperationsCommand.create(testForm, scs);

        assertEquals (4, command.getRestartedServices().size());

        assertEquals ("elasticsearch", command.getRestartedServices().get(0));
        assertEquals ("grafana", command.getRestartedServices().get(1));
        assertEquals ("kafka", command.getRestartedServices().get(2));
        assertEquals ("spark-runtime", command.getRestartedServices().get(3));

        scs.applyServicesSettings(command);

        ServicesSettingsWrapper newConfig = configurationService.loadServicesSettings();

        // test elasticsearch config
        assertEquals ("bootstrap.memory_lock", newConfig.getValueForPath("settings.8.settings.0.properties.0.name"));
        assertEquals ("false", newConfig.getValueForPath("settings.8.settings.0.properties.0.defaultValue"));
        assertNull(newConfig.getValueForPath("settings.8.settings.0.properties.0.value"));

        assertEquals ("action.destructive_requires_name", newConfig.getValueForPath("settings.8.settings.0.properties.1.name"));
        assertEquals ("true", newConfig.getValueForPath("settings.8.settings.0.properties.1.defaultValue"));
        assertEquals ("false", newConfig.getValueForPath("settings.8.settings.0.properties.1.value"));

        // test kafka config
        assertEquals ("num.network.threads", newConfig.getValueForPath("settings.11.settings.0.properties.0.name"));
        assertEquals ("3", newConfig.getValueForPath("settings.11.settings.0.properties.0.defaultValue"));
        assertEquals ("5", newConfig.getValueForPath("settings.11.settings.0.properties.0.value"));

        assertEquals ("num.io.threads", newConfig.getValueForPath("settings.11.settings.0.properties.1.name"));
        assertEquals ("8", newConfig.getValueForPath("settings.11.settings.0.properties.1.defaultValue"));
        assertEquals ("10", newConfig.getValueForPath("settings.11.settings.0.properties.1.value"));

        assertEquals ("socket.send.buffer.bytes", newConfig.getValueForPath("settings.11.settings.0.properties.2.name"));
        assertEquals ("102400", newConfig.getValueForPath("settings.11.settings.0.properties.2.defaultValue"));
        assertNull(newConfig.getValueForPath("settings.11.settings.0.properties.2.value"));

        assertEquals ("socket.receive.buffer.bytes", newConfig.getValueForPath("settings.11.settings.0.properties.3.name"));
        assertEquals ("102400", newConfig.getValueForPath("settings.11.settings.0.properties.3.defaultValue"));
        assertNull(newConfig.getValueForPath("settings.11.settings.0.properties.3.value"));

        assertEquals ("socket.request.max.bytes", newConfig.getValueForPath("settings.11.settings.0.properties.4.name"));
        assertEquals ("104857600", newConfig.getValueForPath("settings.11.settings.0.properties.4.defaultValue"));
        assertNull(newConfig.getValueForPath("settings.11.settings.0.properties.4.value"));

        assertEquals ("num.partitions", newConfig.getValueForPath("settings.11.settings.0.properties.5.name"));
        assertEquals ("1", newConfig.getValueForPath("settings.11.settings.0.properties.5.defaultValue"));
        assertNull(newConfig.getValueForPath("settings.11.settings.0.properties.5.value"));

        assertEquals ("log.retention.hours", newConfig.getValueForPath("settings.11.settings.0.properties.6.name"));
        assertEquals ("168", newConfig.getValueForPath("settings.11.settings.0.properties.6.defaultValue"));
        assertNull(newConfig.getValueForPath("settings.11.settings.0.properties.6.value"));

        // test spark config
        assertEquals ("spark.driver.memory", newConfig.getValueForPath("settings.12.settings.0.properties.0.name"));
        assertEquals ("800m", newConfig.getValueForPath("settings.12.settings.0.properties.0.defaultValue"));
        assertEquals ("500m", newConfig.getValueForPath("settings.12.settings.0.properties.0.value"));

        assertEquals ("spark.rpc.numRetries", newConfig.getValueForPath("settings.12.settings.0.properties.1.name"));
        assertEquals ("5", newConfig.getValueForPath("settings.12.settings.0.properties.1.defaultValue"));
        assertEquals ("10", newConfig.getValueForPath("settings.12.settings.0.properties.1.value"));

        assertEquals ("spark.rpc.retry.wait", newConfig.getValueForPath("settings.12.settings.0.properties.2.name"));
        assertEquals ("5s", newConfig.getValueForPath("settings.12.settings.0.properties.2.defaultValue"));
        assertNull(newConfig.getValueForPath("settings.12.settings.0.properties.2.value"));

        assertEquals ("spark.scheduler.mode", newConfig.getValueForPath("settings.12.settings.0.properties.3.name"));
        assertEquals ("FAIR", newConfig.getValueForPath("settings.12.settings.0.properties.3.defaultValue"));
        assertEquals ("FIFO", newConfig.getValueForPath("settings.12.settings.0.properties.3.value"));

        assertEquals ("spark.locality.wait", newConfig.getValueForPath("settings.12.settings.0.properties.4.name"));
        assertEquals ("20s", newConfig.getValueForPath("settings.12.settings.0.properties.4.defaultValue"));
        assertNull(newConfig.getValueForPath("settings.12.settings.0.properties.4.value"));

        assertEquals ("spark.dynamicAllocation.executorIdleTimeout", newConfig.getValueForPath("settings.12.settings.0.properties.5.name"));
        assertEquals ("200s", newConfig.getValueForPath("settings.12.settings.0.properties.5.defaultValue"));
        assertNull(newConfig.getValueForPath("settings.12.settings.0.properties.5.value"));

        assertEquals ("spark.dynamicAllocation.cachedExecutorIdleTimeout", newConfig.getValueForPath("settings.12.settings.0.properties.6.name"));
        assertEquals ("300s", newConfig.getValueForPath("settings.12.settings.0.properties.6.defaultValue"));
        assertEquals ("400s", newConfig.getValueForPath("settings.12.settings.0.properties.6.value"));

        assertEquals ("spark.executor.memory", newConfig.getValueForPath("settings.12.settings.0.properties.7.name"));
        assertEquals ("[ESKIMO_DEFAULT]", newConfig.getValueForPath("settings.12.settings.0.properties.7.defaultValue"));
        assertNull(newConfig.getValueForPath("settings.12.settings.0.properties.7.value"));

        StringBuilder sb = new StringBuilder();
        notificationService.fetchElements(1).getValue().stream()
                .map (msg -> msg.get("message"))
                .forEach(msg -> sb.append (msg).append("\n"));

        String notifications = sb.toString();

        System.err.println (notifications);

        assertTrue(notifications.contains("Check / Install of Settings on 192.168.10.11 succeeded"));
        assertTrue(notifications.contains("Check / Install of Settings on 192.168.10.13 succeeded"));

        assertTrue (notifications.contains("Executing restart on elasticsearch on (kubernetes)\n" +
                "Executing restart on elasticsearch on (kubernetes) succeeded\n" +
                "Executing restart on grafana on (kubernetes)\n" +
                "Executing restart on grafana on (kubernetes) succeeded\n" +
                "Executing restart on spark-runtime on (kubernetes)\n" +
                "Executing restart on spark-runtime on (kubernetes) succeeded\n" +
                "Executing restart on kafka on (kubernetes)\n" +
                "Executing restart on kafka on (kubernetes) succeeded"));
    }

}

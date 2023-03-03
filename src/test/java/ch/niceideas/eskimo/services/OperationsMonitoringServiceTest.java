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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.*;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import ch.niceideas.eskimo.utils.ActiveWaiter;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({
        "no-web-stack",
        "test-setup",
        "test-conf",
        "test-system",
        "test-operation",
        "test-proxy",
        "test-kube",
        "test-ssh",
        "test-conf",
        "test-connection-manager",
        "test-services"})
public class OperationsMonitoringServiceTest {

    private static final Logger logger = Logger.getLogger(OperationsMonitoringServiceTest.class);

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Autowired
    private NodesConfigurationService nodesConfigurationService;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private SSHCommandServiceTestImpl sshCommandServiceTest;

    @Autowired
    private ConnectionManagerServiceTestImpl connectionManagerServiceTest;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Autowired
    private SystemOperationServiceTestImpl systemOperationServiceTest;

    @Autowired
    private SystemServiceTestImpl systemServiceTest;

    @BeforeEach
    public void setUp() throws Exception {
        SecurityContextHelper.loginAdmin();
        connectionManagerServiceTest.reset();
        connectionManagerServiceTest.dontConnect();
        sshCommandServiceTest.reset();
        systemOperationServiceTest.setMockCalls(false);
        systemServiceTest.setMockCalls(false);
        try {
            operationsMonitoringService.endCommand(true);
        } catch (Exception e) {
            logger.debug (e, e);
        }
    }

    @Test
    public void testInterruption() throws Exception {

        // no processing pending => no interruption
        operationsMonitoringService.interruptProcessing();

        assertFalse(operationsMonitoringService.isInterrupted());

        // test interruption
        operationsMonitoringService.startCommand(new SimpleOperationCommand(
                SimpleOperationCommand.SimpleOperation.COMMAND,
                Service.from ("test"),
                Node.fromName("test")));

        operationsMonitoringService.interruptProcessing();

        assertTrue(operationsMonitoringService.isInterrupted());

        operationsMonitoringService.endCommand(true);

        // no processing anymore
        assertFalse(operationsMonitoringService.isInterrupted());
    }

    @Test
    @DirtiesContext
    public void testGetOperationsMonitoringStatus() throws Exception {

        ServicesInstallStatusWrapper servicesInstallStatus = new ServicesInstallStatusWrapper(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/serviceInstallStatus.json"), StandardCharsets.UTF_8));
        configurationServiceTest.saveServicesInstallationStatus(servicesInstallStatus);

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper (StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/rawNodesConfig.json"), StandardCharsets.UTF_8));
        configurationServiceTest.saveNodesConfig(nodesConfig);

        configurationServiceTest.saveKubernetesServicesConfig(StandardSetupHelpers.getStandardKubernetesConfig());

        NodeServiceOperationsCommand command = NodeServiceOperationsCommand.create(
                servicesDefinition,
                nodeRangeResolver,
                servicesInstallStatus,
                nodesConfig
        );

        sshCommandServiceTest.setNodeResultBuilder((node, script) -> {
            if (script.equals("echo OK")) {
                return "OK";
            }
            if (script.equals("if [[ -f /etc/debian_version ]]; then echo debian; fi")) {
                return "debian";
            }
            if (script.endsWith("cat /proc/meminfo | grep MemTotal")) {
                return "MemTotal:        9982656 kB";
            }
            if (script.equals("cat /etc/eskimo_flag_base_system_installed")) {
                return "OK";
            }
            return null;
        });

        nodesConfigurationService.applyNodesConfig(command);

        // FIXME How can I do this one with ActiveWaiter.wait() ?
        Thread.sleep (1000);

        OperationsMonitoringStatusWrapper operationsMonitoringStatus = operationsMonitoringService.getOperationsMonitoringStatus(new HashMap<>());
        assertNotNull (operationsMonitoringStatus);

        System.err.println (operationsMonitoringStatus.getFormattedValue());

        OperationsMonitoringStatusWrapper expectedStatus = new OperationsMonitoringStatusWrapper (
                StreamUtils.getAsString(ResourceUtils.getResourceAsStream("OperationsMonitoringServiceTest/expected-status.json"), StandardCharsets.UTF_8));

        //assertEquals (expectedStatus.getFormattedValue(), operationsMonitoringStatus.getFormattedValue());
        assertTrue (expectedStatus.getJSONObject().similar(operationsMonitoringStatus.getJSONObject()));
    }

    @Test
    public void testInterruptionReporting() throws Exception {

        final Object monitor = new Object();
        final AtomicBoolean doWait = new AtomicBoolean(true);
        final AtomicInteger waitCount = new AtomicInteger();

        ServicesInstallStatusWrapper servicesInstallStatus = new ServicesInstallStatusWrapper(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/serviceInstallStatus.json"), StandardCharsets.UTF_8));
        configurationServiceTest.saveServicesInstallationStatus(servicesInstallStatus);

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper (StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/rawNodesConfig.json"), StandardCharsets.UTF_8));
        configurationServiceTest.saveNodesConfig(nodesConfig);

        configurationServiceTest.saveKubernetesServicesConfig(StandardSetupHelpers.getStandardKubernetesConfig());

        NodeServiceOperationsCommand command = NodeServiceOperationsCommand.create(
                servicesDefinition,
                nodeRangeResolver,
                servicesInstallStatus,
                nodesConfig
        );

        sshCommandServiceTest.setNodeResultBuilder((node, script) -> {

            if (script.equals("echo OK")) {
                return "OK";
            }
            if (script.equals("if [[ -f /etc/debian_version ]]; then echo debian; fi")) {
                return "debian";
            }
            if (script.endsWith("cat /proc/meminfo | grep MemTotal")) {
                return "MemTotal:        9982656 kB";
            }
            if (script.equals("cat /etc/eskimo_flag_base_system_installed")) {
                return "OK";
            }

            synchronized (monitor) {
                try {
                    waitCount.incrementAndGet();
                    if (doWait.get()) {
                        monitor.wait();
                    }
                } catch (InterruptedException e) {
                    logger.error (e, e);
                }
            }

            return null;
        });

        Thread t = new Thread(() -> {
            try {
                SecurityContextHelper.loginAdmin();
                nodesConfigurationService.applyNodesConfig(command);
            } catch (NodesConfigurationException e) {
                logger.error (e, e);
            }
        });
        t.start();

        AtomicInteger previousCount = new AtomicInteger();
        ActiveWaiter.wait(() -> {
            if (waitCount.get() <= 0) {
                return false;
            }
            if (waitCount.get() == previousCount.get()) {
                return true;
            }
            previousCount.set(waitCount.get());
            return false;

        });

        operationsMonitoringService.interruptProcessing();

        doWait.set(false);

        // FIXME How can I do this one with ActiveWaiter.wait() ?
        Thread.sleep(1000);
        synchronized (monitor) {

            monitor.notifyAll();
        }

        OperationsMonitoringStatusWrapper operationsMonitoringStatus = operationsMonitoringService.getOperationsMonitoringStatus(new HashMap<>());
        assertNotNull (operationsMonitoringStatus);

        System.err.println (operationsMonitoringStatus.getFormattedValue());
        
        assertEquals("CANCELLED", operationsMonitoringStatus.getValueForPathAsString("status.restart_cluster-slave_192-168-10-13"));
        assertEquals("CANCELLED", operationsMonitoringStatus.getValueForPathAsString("status.installation_calculator-runtime_192-168-10-13"));
        assertEquals("CANCELLED", operationsMonitoringStatus.getValueForPathAsString("status.restart_cluster-slave_192-168-10-15"));
        assertEquals("CANCELLED", operationsMonitoringStatus.getValueForPathAsString("status.installation_user-console_192-168-10-15"));
        assertEquals("CANCELLED", operationsMonitoringStatus.getValueForPathAsString("status.installation_cluster-master_192-168-10-15"));
        assertEquals("CANCELLED", operationsMonitoringStatus.getValueForPathAsString("status.uninstallation_cluster-master_192-168-10-13"));
        assertEquals("CANCELLED", operationsMonitoringStatus.getValueForPathAsString("status.installation_database_192-168-10-13"));
        assertEquals("CANCELLED", operationsMonitoringStatus.getValueForPathAsString("status.installation_database_192-168-10-15"));
        assertEquals("CANCELLED", operationsMonitoringStatus.getValueForPathAsString("status.installation_calculator-runtime_192-168-10-15"));

        // same test as above but keep all operations pending (object.wait selectively / then object.notifyAll())
        // test that the operations selectively blocked are kept running and all the ones not started are cancelled

        t.join();
    }


}

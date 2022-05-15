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

import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.model.*;
import com.trilead.ssh2.Connection;
import org.apache.log4j.Logger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class OperationsMonitoringServiceTest extends AbstractSystemTest {

    private static final Logger logger = Logger.getLogger(OperationsMonitoringServiceTest.class);

    private String testRunUUID = UUID.randomUUID().toString();

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setupService.setConfigStoragePathInternal(SystemServiceTest.createTempStoragePath());
    }

    @Override
    protected SystemService createSystemService() {
        SystemService ss = new SystemService(false) {
            @Override
            protected File createTempFile(String serviceOrFlag, String node, String extension) throws IOException {
                File retFile = new File (System.getProperty("java.io.tmpdir") + "/" + serviceOrFlag+"-"+testRunUUID+"-"+ node +extension);
                retFile.createNewFile();
                return retFile;
            }
        };
        ss.setConfigurationService(configurationService);
        return ss;
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

    @Test
    public void testInterruption() throws Exception {

        // no processing pending => no interruption
        operationsMonitoringService.interruptProcessing();

        assertFalse(operationsMonitoringService.isInterrupted());

        // test interruption
        operationsMonitoringService.operationsStarted(new SimpleOperationCommand("test", "test", "test"));

        operationsMonitoringService.interruptProcessing();

        assertTrue(operationsMonitoringService.isInterrupted());

        operationsMonitoringService.operationsFinished(true);

        // no processing anymore
        assertFalse(operationsMonitoringService.isInterrupted());
    }

    @Test
    public void testGetOperationsMonitoringStatus() throws Exception {

        ServicesInstallStatusWrapper servicesInstallStatus = new ServicesInstallStatusWrapper(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/serviceInstallStatus.json"), "UTF-8"));
        configurationService.saveServicesInstallationStatus(servicesInstallStatus);

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper (StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/rawNodesConfig.json"), "UTF-8"));
        configurationService.saveNodesConfig(nodesConfig);

        configurationService.saveKubernetesServicesConfig(StandardSetupHelpers.getStandardKubernetesConfig());

        ServiceOperationsCommand command = ServiceOperationsCommand.create(
                servicesDefinition,
                nodeRangeResolver,
                servicesInstallStatus,
                nodesConfig
        );

        SSHCommandService sshCommandService = new SSHCommandService() {
            @Override
            public synchronized String runSSHScript(SSHConnection connection, String script, boolean throwsException) {
                return runSSHScript((String)null, script, throwsException);
            }
            @Override
            public synchronized String runSSHScript(String node, String script, boolean throwsException) {
                testSSHCommandScript.append(script).append("\n");
                if (script.equals("echo OK")) {
                    return "OK";
                }
                if (script.equals("if [[ -f /etc/debian_version ]]; then echo debian; fi")) {
                    return "debian";
                }
                if (script.endsWith("cat /proc/meminfo | grep MemTotal")) {
                    return "MemTotal:        9982656 kB";
                }

                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public synchronized String runSSHCommand(SSHConnection connection, String command) {
                return runSSHCommand((String)null, command);
            }
            @Override
            public synchronized String runSSHCommand(String node, String command) {
                testSSHCommandScript.append(command).append("\n");
                if (command.equals("cat /etc/eskimo_flag_base_system_installed")) {
                    return "OK";
                }

                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public synchronized void copySCPFile(SSHConnection connection, String filePath) {
                // just do nothing
            }
            @Override
            public synchronized void copySCPFile(String node, String filePath)  {
                // just do nothing
            }
        };

        systemService.setSshCommandService(sshCommandService);

        memoryComputer.setSshCommandService(sshCommandService);

        nodesConfigurationService.setSshCommandService(sshCommandService);

        nodesConfigurationService.setConnectionManagerService(new ConnectionManagerService() {
            @Override
            public void forceRecreateConnection(String node) {
                // no Op
            }
            @Override
            public SSHConnection getPrivateConnection (String node) {
                return null;
            }
            @Override
            public SSHConnection getSharedConnection (String node) {
                return null;
            }
        });

        nodesConfigurationService.setKubernetesService(new KubernetesService() {

            @Override
            protected String restartServiceInternal(Service service, String node) throws KubernetesException, SSHCommandException {
                // No Op
                return null;
            }
        });

        nodesConfigurationService.applyNodesConfig(command);

        OperationsMonitoringStatusWrapper operationsMonitoringStatus = operationsMonitoringService.getOperationsMonitoringStatus(new HashMap<>());
        assertNotNull (operationsMonitoringStatus);

        //System.err.println (operationsMonitoringStatus.getFormattedValue());

        OperationsMonitoringStatusWrapper expectedStatus = new OperationsMonitoringStatusWrapper (
                StreamUtils.getAsString(ResourceUtils.getResourceAsStream("OperationsMonitoringServiceTest/expected-status.json"), "UTF-8"));

        //assertEquals (expectedStatus.getFormattedValue(), operationsMonitoringStatus.getFormattedValue());
        assertTrue (expectedStatus.getJSONObject().similar(operationsMonitoringStatus.getJSONObject()));
    }

    @Test
    public void testInterruptionReporting() throws Exception {

        final Object monitor = new Object();
        final AtomicInteger waitCount = new AtomicInteger();

        ServicesInstallStatusWrapper servicesInstallStatus = new ServicesInstallStatusWrapper(StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/serviceInstallStatus.json"), "UTF-8"));
        configurationService.saveServicesInstallationStatus(servicesInstallStatus);

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper (StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/rawNodesConfig.json"), "UTF-8"));
        configurationService.saveNodesConfig(nodesConfig);

        configurationService.saveKubernetesServicesConfig(StandardSetupHelpers.getStandardKubernetesConfig());

        ServiceOperationsCommand command = ServiceOperationsCommand.create(
                servicesDefinition,
                nodeRangeResolver,
                servicesInstallStatus,
                nodesConfig
        );

        SSHCommandService sshCommandService = new SSHCommandService() {
            @Override
            public synchronized String runSSHScript(SSHConnection connection, String script, boolean throwsException) throws SSHCommandException {
                return runSSHScript((String)null, script, throwsException);
            }
            @Override
            public synchronized String runSSHScript(String node, String script, boolean throwsException) {
                testSSHCommandScript.append(script).append("\n");
                if (script.equals("echo OK")) {
                    return "OK";
                }
                if (script.equals("if [[ -f /etc/debian_version ]]; then echo debian; fi")) {
                    return "debian";
                }
                if (script.endsWith("cat /proc/meminfo | grep MemTotal")) {
                    return "MemTotal:        9982656 kB";
                }

                synchronized (monitor) {
                    try {
                        waitCount.incrementAndGet();
                        monitor.wait();
                    } catch (InterruptedException e) {
                        logger.error (e, e);
                    }
                }

                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public synchronized String runSSHCommand(SSHConnection connection, String command) throws SSHCommandException {
                return runSSHCommand((String)null, command);
            }
            @Override
            public synchronized String runSSHCommand(String node, String command) {
                testSSHCommandScript.append(command).append("\n");
                if (command.equals("cat /etc/eskimo_flag_base_system_installed")) {
                    return "OK";
                }

                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public synchronized void copySCPFile(SSHConnection connection, String filePath) {
                // just do nothing
            }
            @Override
            public synchronized void copySCPFile(String node, String filePath)  {
                // just do nothing
            }
        };

        systemService.setSshCommandService(sshCommandService);

        memoryComputer.setSshCommandService(sshCommandService);

        nodesConfigurationService.setSshCommandService(sshCommandService);

        nodesConfigurationService.setConnectionManagerService(new ConnectionManagerService() {
            @Override
            public void forceRecreateConnection(String node) {
                // no Op
            }
            @Override
            public SSHConnection getPrivateConnection (String node) {
                return null;
            }
            @Override
            public SSHConnection getSharedConnection (String node) {
                return null;
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    nodesConfigurationService.applyNodesConfig(command);
                } catch (SystemException | ServiceDefinitionException | NodesConfigurationException e) {
                    logger.error (e, e);
                }
            }
        }).start();

        AtomicInteger previousCount = new AtomicInteger();
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
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

        synchronized (monitor) {
            monitor.notifyAll();
        }

        OperationsMonitoringStatusWrapper operationsMonitoringStatus = operationsMonitoringService.getOperationsMonitoringStatus(new HashMap<>());
        assertNotNull (operationsMonitoringStatus);

        //System.err.println (operationsMonitoringStatus.getFormattedValue());

        OperationsMonitoringStatusWrapper expectedStatus = new OperationsMonitoringStatusWrapper (
                StreamUtils.getAsString(ResourceUtils.getResourceAsStream("OperationsMonitoringServiceTest/expected-status-interrupted.json"), "UTF-8"));

        //assertEquals (expectedStatus.getFormattedValue(), operationsMonitoringStatus.getFormattedValue());
        assertTrue (expectedStatus.getJSONObject().similar(operationsMonitoringStatus.getJSONObject()));

        // same test as above but keep all operations pending (object.wait selectively / then object.notifyAll())
        // test that the operations selectively blocked are kept running and all the ones not started are cancelled
    }


}

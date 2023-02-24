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


package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.SimpleOperationCommand;
import ch.niceideas.eskimo.services.OperationsMonitoringService;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import ch.niceideas.eskimo.test.infrastructure.HttpObjectsHelper;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.*;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({
        "no-web-stack",
        "test-system",
        "test-setup",
        "test-conf",
        "test-connection-manager",
        "test-ssh",
        "test-services"})
public class KubernetesServicesConfigControllerTest {

    @Autowired
    private KubernetesServicesConfigController kscc;

    @Autowired
    private SystemServiceTestImpl systemSeviceTest;

    @Autowired
    private SetupServiceTestImpl setupServiceTest;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Autowired
    private ConnectionManagerServiceTestImpl connectionManagerServiceTest;

    @Autowired
    private SSHCommandServiceTestImpl sshCommandServiceTest;

    @BeforeEach
    public void testSetup() {

        systemSeviceTest.setReturnEmptySystemStatus();

        if (operationsMonitoringService.isProcessingPending()) {
            operationsMonitoringService.endCommand(true);
        }

        SecurityContextHelper.loginAdmin();

        configurationServiceTest.reset();
        setupServiceTest.reset();

        connectionManagerServiceTest.dontConnect();

        kscc.setDemoMode(false);

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
            return null;
        });
    }

    @Test
    public void testLoadKubernetesServicesConfig() {

        setupServiceTest.setSetupCompleted();

        configurationServiceTest.setStandardKubernetesConfig();

        System.err.println (kscc.loadKubernetesServicesConfig());
        assertTrue (new JSONObject("{\n" +
                "    \"broker_cpu\": \"1\",\n" +
                "    \"broker-manager_cpu\": \"1\",\n" +
                "    \"cluster-dashboard_ram\": \"800M\",\n" +
                "    \"database-manager_cpu\": \"1\",\n" +
                "    \"user-console_install\": \"on\",\n" +
                "    \"cluster-dashboard_cpu\": \"1\",\n" +
                "    \"calculator-runtime_cpu\": \"1\",\n" +
                "    \"database_cpu\": \"1\",\n" +
                "    \"database_install\": \"on\",\n" +
                "    \"database_ram\": \"800M\",\n" +
                "    \"calculator-runtime_ram\": \"800M\",\n" +
                "    \"cluster-dashboard_install\": \"on\",\n" +
                "    \"user-console_ram\": \"800M\",\n" +
                "    \"database-manager_install\": \"on\",\n" +
                "    \"database-manager_ram\": \"800M\",\n" +
                "    \"broker_ram\": \"800M\",\n" +
                "    \"broker-manager_ram\": \"800M\",\n" +
                "    \"calculator-runtime_install\": \"on\",\n" +
                "    \"broker-manager_install\": \"on\",\n" +
                "    \"broker_install\": \"on\",\n" +
                "    \"database_deployment_strategy\": \"on\",\n" +
                "    \"broker_deployment_strategy\": \"on\",\n" +
                "    \"user-console_cpu\": \"1\"\n" +
                "}").similar(new JSONObject (kscc.loadKubernetesServicesConfig())));

        setupServiceTest.setSetupError();

        assertEquals ("{\n" +
                "  \"clear\": \"setup\",\n" +
                "  \"processingPending\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", kscc.loadKubernetesServicesConfig());

        setupServiceTest.setSetupCompleted();

        configurationServiceTest.setKubernetesConfigError();

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", kscc.loadKubernetesServicesConfig());
    }

    @Test
    public void testReinstallKubernetesServicesConfig() {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        configurationServiceTest.setStandard2NodesInstallStatus();
        configurationServiceTest.setStandard2NodesSetup();
        configurationServiceTest.setStandardKubernetesConfig();

        assertEquals ("{\n" +
                "  \"command\": {\n" +
                "    \"uninstallations\": [],\n" +
                "    \"restarts\": [],\n" +
                "    \"installations\": [\n" +
                "      \"database\",\n" +
                "      \"broker\",\n" +
                "      \"user-console\"\n" +
                "    ],\n" +
                "    \"warnings\": \"Kubernetes is not available. The changes in kubernetes services configuration and deployments will be saved but they will <strong>need to be applied again<\\/strong> another time when Kubernetes Master is available\"\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", kscc.reinstallKubernetesServiceConfig("{" +
                "\"calculator_install\":\"on\"," +
                "\"broker_install\":\"on\"," +
                "\"database_install\":\"on\"," +
                "\"user-console_install\":\"on\"}", session));

        assertEquals ("{\"status\": \"OK\"}", kscc.applyKubernetesServicesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }

    @Test
    public void testApplyNodesConfig_demoMode() {

        Map<String, Object> sessionContent = new HashMap<>();
        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        kscc.setDemoMode(true);

        assertEquals ("{\n" +
                "  \"messages\": \"Unfortunately, re-applying kubernetes configuration or changing kubernetes configuration is not possible in DEMO mode.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", kscc.applyKubernetesServicesConfig(session));
    }

    @Test
    public void testApplyNodesConfig_processingPending() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();
        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        operationsMonitoringService.startCommand(new SimpleOperationCommand(
                SimpleOperationCommand.SimpleOperation.COMMAND, Service.from("test"), Node.fromAddress("192.168.10.15")));

        assertEquals ("{\n" +
                "  \"messages\": \"Some backend operations are currently running. Please retry after they are completed.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", kscc.applyKubernetesServicesConfig(session));
    }

    @Test
    public void testSaveKubernetesServicesConfig() {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        configurationServiceTest.setStandard2NodesInstallStatus();
        configurationServiceTest.setStandard2NodesSetup();

        assertEquals ("{\n" +
                        "  \"command\": {\n" +
                        "    \"uninstallations\": [],\n" +
                        "    \"restarts\": [\n" +
                        "      \"cluster-dashboard\",\n" +
                        "      \"database\",\n" +
                        "      \"database-manager\",\n" +
                        "      \"calculator-runtime\",\n" +
                        "      \"broker\",\n" +
                        "      \"broker-manager\",\n" +
                        "      \"user-console\"\n" +
                        "    ],\n" +
                        "    \"installations\": [],\n" +
                        "    \"warnings\": \"Kubernetes is not available. The changes in kubernetes services configuration and deployments will be saved but they will <strong>need to be applied again<\\/strong> another time when Kubernetes Master is available\"\n" +
                        "  },\n" +
                        "  \"status\": \"OK\"\n" +
                        "}",
                kscc.saveKubernetesServicesConfig(StandardSetupHelpers.getStandardKubernetesConfig().getFormattedValue(), session));

        assertEquals ("{\"status\": \"OK\"}", kscc.applyKubernetesServicesConfig(session));

        assertTrue(sessionContent.isEmpty());
    }
}

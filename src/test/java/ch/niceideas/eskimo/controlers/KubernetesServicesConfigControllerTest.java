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
@ActiveProfiles({"no-web-stack", "test-system", "test-setup", "test-conf", "test-connection-manager", "test-ssh"})
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

        //System.err.println (kscc.loadKubernetesServicesConfig());
        assertTrue (new JSONObject("{\n" +
                "    \"spark-runtime_ram\": \"800M\",\n" +
                "    \"zeppelin_ram\": \"800M\",\n" +
                "    \"kibana_ram\": \"800M\",\n" +
                "    \"kafka-manager_ram\": \"800M\",\n" +
                "    \"kafka-manager_install\": \"on\",\n" +
                "    \"kafka_ram\": \"800M\",\n" +
                "    \"kafka_cpu\": \"1\",\n" +
                "    \"elasticsearch_cpu\": \"1\",\n" +
                "    \"kafka-manager_cpu\": \"1\",\n" +
                "    \"elasticsearch_install\": \"on\",\n" +
                "    \"kafka_install\": \"on\",\n" +
                "    \"kibana_cpu\": \"1\",\n" +
                "    \"zeppelin_install\": \"on\",\n" +
                "    \"spark-runtime_cpu\": \"1\",\n" +
                "    \"logstash_cpu\": \"1\",\n" +
                "    \"logstash_ram\": \"800M\",\n" +
                "    \"spark-runtime_install\": \"on\",\n" +
                "    \"cerebro_ram\": \"800M\",\n" +
                "    \"spark-console_cpu\": \"1\",\n" +
                "    \"spark-console_install\": \"on\",\n" +
                "    \"elasticsearch_ram\": \"800M\",\n" +
                "    \"spark-console_ram\": \"800M\",\n" +
                "    \"cerebro_install\": \"on\",\n" +
                "    \"zeppelin_cpu\": \"1\",\n" +
                "    \"kibana_install\": \"on\",\n" +
                "    \"logstash_install\": \"on\",\n" +
                "    \"cerebro_cpu\": \"1\"\n" +
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
                "      \"elasticsearch\",\n" +
                "      \"spark-runtime\",\n" +
                "      \"cerebro\",\n" +
                "      \"kafka\",\n" +
                "      \"zeppelin\"\n" +
                "    ],\n" +
                "    \"warnings\": \"Kubernetes is not available. The changes in kubernetes services configuration and deployments will be saved but they will <strong>need to be applied again<\\/strong> another time when Kubernetes Master is available\"\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", kscc.reinstallKubernetesServiceConfig("{\"spark-runtime_install\":\"on\",\"kafka_install\":\"on\",\"elasticsearch_install\":\"on\",\"cerebro_install\":\"on\",\"grafana_install\":\"on\",\"zeppelin_install\":\"on\"}", session));

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
                        "      \"elasticsearch\",\n" +
                        "      \"spark-runtime\",\n" +
                        "      \"spark-console\",\n" +
                        "      \"cerebro\",\n" +
                        "      \"kibana\",\n" +
                        "      \"logstash\",\n" +
                        "      \"kafka\",\n" +
                        "      \"kafka-manager\",\n" +
                        "      \"zeppelin\"\n" +
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

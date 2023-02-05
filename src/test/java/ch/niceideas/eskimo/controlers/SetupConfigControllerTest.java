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
import ch.niceideas.eskimo.test.infrastructure.HttpObjectsHelper;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import ch.niceideas.eskimo.test.services.SetupServiceTestImpl;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
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
@ActiveProfiles({"no-web-stack", "test-services", "test-conf", "test-setup", "test-system"})
public class SetupConfigControllerTest {

    @Autowired
    private SetupConfigController scc;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private SetupServiceTestImpl setupServiceTest;

    @BeforeEach
    public void testSetup() {
        if (operationsMonitoringService.isProcessingPending()) {
            operationsMonitoringService.endCommand(true);
        }

        configurationServiceTest.reset();
        setupServiceTest.reset();

        SecurityContextHelper.loginAdmin();

        scc.setDemoMode (false);
    }

    @Test
    public void testLoadSetupConfig() throws Exception {

        configurationServiceTest.setSetupConfigNotCompletedError();

        assertEquals ("{\n" +
                "  \"clear\": \"missing\",\n" +
                "  \"isSnapshot\": false,\n" +
                "  \"version\": \"@project.version@\",\n" +
                "  \"processingPending\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.loadSetupConfig());

        configurationServiceTest.saveSetupConfig("{\"config\": \"dummy\"}");

        assertEquals ("{\n" +
                "    \"processingPending\": false,\n" +
                "    \"isSnapshot\": false,\n" +
                "    \"config\": \"dummy\",\n" +
                "    \"version\": \"@project.version@\"\n" +
                "}", scc.loadSetupConfig());

        setupServiceTest.setSetupError();

        assertEquals ("{\n" +
                "    \"processingPending\": false,\n" +
                "    \"clear\": \"setup\",\n" +
                "    \"message\": \"Test Error\",\n" +
                "    \"isSnapshot\": false,\n" +
                "    \"config\": \"dummy\",\n" +
                "    \"version\": \"@project.version@\"\n" +
                "}", scc.loadSetupConfig());

        configurationServiceTest.setSetupConfigNotCompletedError();

        assertEquals ("{\n" +
                "  \"clear\": \"missing\",\n" +
                "  \"isSnapshot\": false,\n" +
                "  \"version\": \"@project.version@\",\n" +
                "  \"processingPending\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.loadSetupConfig());

        setupServiceTest.setSetupCompleted();
        configurationServiceTest.setSetupCompleted();

        operationsMonitoringService.startCommand(new SimpleOperationCommand(
                SimpleOperationCommand.SimpleOperation.COMMAND, Service.from("test"), Node.fromAddress("192.168.10.15")));

        assertEquals ("{\n" +
                "    \"processingPending\": true,\n" +
                "    \"isSnapshot\": false,\n" +
                "    \"config\": \"dummy\",\n" +
                "    \"version\": \"@project.version@\"\n" +
                "}", scc.loadSetupConfig());
    }

    @Test
    public void testSaveSetup_demoMode() {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        scc.setDemoMode (true);

        assertEquals ("{\n" +
                "  \"messages\": \"Unfortunately, changing setup configuration is not possible in DEMO mode.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.applySetup(session));
    }

    @Test
    public void testSaveSetup_processingPending() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        operationsMonitoringService.startCommand(new SimpleOperationCommand(
                SimpleOperationCommand.SimpleOperation.COMMAND, Service.from("test"), Node.fromAddress("192.168.10.15")));

        assertEquals ("{\n" +
                "  \"messages\": \"Some backend operations are currently running. Please retry after they are completed.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.applySetup(session));
    }

    @Test
    public void testSaveSetup() {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        assertEquals ("{\n" +
                        "  \"command\": {\n" +
                        "    \"buildPackage\": [],\n" +
                        "    \"buildKube\": [],\n" +
                        "    \"downloadKube\": [],\n" +
                        "    \"none\": true,\n" +
                        "    \"downloadPackages\": [],\n" +
                        "    \"packageUpdates\": [],\n" +
                        "    \"packageDownloadUrl\": \"dummy\"\n" +
                        "  },\n" +
                        "  \"status\": \"OK\"\n" +
                        "}",
                scc.saveSetup("" +
                    "{\"setup_storage\":\"/data/eskimo_config\"," +
                    "\"ssh_username\":\"eskimo\"," +
                    "\"filename-ssh-key\":\"ssh_key\"," +
                    "\"content-ssh-key\":\"DUMMY\"," +
                    "\"setup-kube-origin\":\"download\"," +
                    "\"setup-services-origin\":\"download\"}", session));

        assertEquals ("{\"status\": \"OK\"}", scc.applySetup(session));

        assertTrue(sessionContent.isEmpty());
    }


}

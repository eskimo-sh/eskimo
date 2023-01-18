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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.ServicesSettingsWrapper;
import ch.niceideas.eskimo.model.SimpleOperationCommand;
import ch.niceideas.eskimo.services.NotificationService;
import ch.niceideas.eskimo.services.OperationsMonitoringService;
import ch.niceideas.eskimo.test.infrastructure.HttpObjectsHelper;
import ch.niceideas.eskimo.test.infrastructure.NotificationHelper;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.ConfigurationServiceTestImpl;
import ch.niceideas.eskimo.test.services.ServicesSettingsServiceTestImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import javax.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-services", "test-conf", "test-services-settings", "test-system", "test-setup"})
public class ServicesSettingsControllerTest {

    @Autowired
    private ServicesSettingsController scc;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ServicesSettingsServiceTestImpl servicesSettingsServiceTest;

    @BeforeEach
    public void testSetup() {
        if (operationsMonitoringService.isProcessingPending()) {
            operationsMonitoringService.endCommand(true);
        }

        SecurityContextHelper.loginAdmin();

        servicesSettingsServiceTest.reset();
    }

    @Test
    public void testLoadServicesConfig() throws Exception {

        String jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testConfig.json"), StandardCharsets.UTF_8);
        configurationServiceTest.saveServicesSettings(new ServicesSettingsWrapper(jsonConfig));

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesConfigControllerTest/expectedResult.json"), StandardCharsets.UTF_8);
        assertTrue(StringUtils.isNotBlank(expectedResult));
        assertEquals (
                expectedResult.replace("\r", "").trim(),
                scc.loadServicesSettings().replace("\r", "").trim());

        configurationServiceTest.setServiceSettingsError();

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", scc.loadServicesSettings());
    }

    @Test
    public void testPrepareAndSaveServicesConfig() throws Exception {

        Map<String, Object> sessionContent = new HashMap<>();

        HttpSession session = HttpObjectsHelper.createHttpSession(sessionContent);

        String jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testConfig.json"), StandardCharsets.UTF_8);

        assertEquals ("{\n" +
                "  \"command\": {\n" +
                "    \"settings\": {},\n" +
                "    \"restarts\": []\n" +
                "  },\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.prepareSaveServicesSettings(jsonConfig, session));

        assertEquals ("{\"status\": \"OK\"}", scc.saveServicesSettings(session));

        servicesSettingsServiceTest.setApplyServicesSettingsError();

        assertEquals ("{\n" +
                "  \"error\": \"VGVzdCBFcnJvcg==\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", scc.saveServicesSettings(session));


        assertEquals("Setting application failed ! Test Error", NotificationHelper.getAssembledNotifications(notificationService));

        servicesSettingsServiceTest.reset();

        operationsMonitoringService.startCommand(new SimpleOperationCommand("test", "test", "192.168.10.15"));

        assertEquals("{\n" +
                "  \"messages\": \"Some backend operations are currently running. Please retry after they are completed..\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", scc.saveServicesSettings(session));
    }
}

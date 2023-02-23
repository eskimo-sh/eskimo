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
import ch.niceideas.eskimo.model.ServicesSettingsWrapper;
import ch.niceideas.eskimo.model.SettingsOperationsCommand;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.*;
import ch.niceideas.eskimo.types.Service;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

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
        "test-operations",
        "test-proxy",
        "test-kube",
        "test-ssh",
        "test-connection-manager",
        "test-services"})
public class ServicesSettingsServiceTest {

    private String jsonConfig = null;
    private String testForm = null;
    private String testFormWithValidation = null;
    private String expectedJsonString = null;
    private String expectedNewConfig = null;

    @Autowired
    private ServicesSettingsService servicesSettingsService;

    @Autowired
    private ConfigurationServiceTestImpl configurationServiceTest;

    @Autowired
    private SystemOperationServiceTestImpl systemOperationServiceTest;

    @Autowired
    private SystemServiceTestImpl systemServiceTest;

    @Autowired
    private SSHCommandServiceTestImpl sshCommandServiceTest;

    @Autowired
    private OperationsMonitoringServiceTestImpl operationsMonitoringServiceTest;

    @Autowired
    private ConnectionManagerServiceTestImpl connectionManagerServiceTest;

    @BeforeEach
    public void setUp() throws Exception {

        jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testConfig.json"), StandardCharsets.UTF_8);
        testForm = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testForm.json"), StandardCharsets.UTF_8);
        testFormWithValidation = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesSettingsTest/testFormWithValidation.json"), StandardCharsets.UTF_8);
        expectedJsonString = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesSettingsServiceTest/expectedCommand.json"), StandardCharsets.UTF_8);
        expectedNewConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesSettingsServiceTest/expectedNewConfig.json"), StandardCharsets.UTF_8);

        SecurityContextHelper.loginAdmin();
        operationsMonitoringServiceTest.endCommand(true);
        connectionManagerServiceTest.dontConnect();
        sshCommandServiceTest.reset();
        systemServiceTest.reset();
        sshCommandServiceTest.setResult("");
        systemOperationServiceTest.setMockCalls(false);
        systemServiceTest.setMockCalls(false);

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
    public void testSomeRegex() {
        Pattern validationRegex = Pattern.compile("^([0-9\\.]+[kmgt]?)$|^(\\[ESKIMO_DEFAULT\\])$");
        assertTrue(validationRegex.matcher("1024m").matches());
        assertTrue(validationRegex.matcher("1024").matches());
        assertTrue(validationRegex.matcher("10241024").matches());
        assertTrue(validationRegex.matcher("1g").matches());
        assertTrue(validationRegex.matcher("[ESKIMO_DEFAULT]").matches());

        assertFalse(validationRegex.matcher("1024x").matches());
        assertFalse(validationRegex.matcher("tada").matches());
        assertFalse(validationRegex.matcher("").matches());
        assertFalse(validationRegex.matcher("none").matches());
        assertFalse(validationRegex.matcher("false").matches());
    }

    @Test
    public void testCheckServicesSettings() throws Exception {

        configurationServiceTest.saveNodesConfig(StandardSetupHelpers.getStandard2NodesSetup());
        configurationServiceTest.saveKubernetesServicesConfig(StandardSetupHelpers.getStandardKubernetesConfig());
        configurationServiceTest.saveServicesInstallationStatus(StandardSetupHelpers.getStandard2NodesInstallStatus());

        configurationServiceTest.saveServicesSettings(new ServicesSettingsWrapper(jsonConfig));

        SettingsOperationsCommand.create(testFormWithValidation, servicesSettingsService);

        JSONObject tamperedForm = new JSONObject(testFormWithValidation);

        tamperedForm.put("broker-socket---send---buffer---bytes", "abc");
        tamperedForm.put("calculator-runtime-calculator---driver---memory", "1024x");

        ServicesSettingsException exp = assertThrows(
                ServicesSettingsException.class,
                () -> SettingsOperationsCommand.create(tamperedForm.toString(2), servicesSettingsService));

        assertNotNull (exp);

        assertEquals("Value abc for broker / socket.send.buffer.bytes doesn't comply to format ^[0-9\\.]+$<br>" +
                "Value 1024x for calculator-runtime / calculator.driver.memory doesn't comply to format ^([0-9\\.]+[kmgt]?)$|^(\\[ESKIMO_DEFAULT\\])$<br>", exp.getMessage());
    }

    @Test
    public void testSaveAndApplyServicesConfig() throws Exception {
        configurationServiceTest.saveNodesConfig(StandardSetupHelpers.getStandard2NodesSetup());
        configurationServiceTest.saveKubernetesServicesConfig(StandardSetupHelpers.getStandardKubernetesConfig());
        configurationServiceTest.saveServicesInstallationStatus(StandardSetupHelpers.getStandard2NodesInstallStatus());

        configurationServiceTest.saveServicesSettings(new ServicesSettingsWrapper(jsonConfig));

        SettingsOperationsCommand command = SettingsOperationsCommand.create(testForm, servicesSettingsService);

        //assertEquals(expectedJsonString, command.toJSON().toString(2));
        assertTrue (new JSONObject(expectedJsonString).similar(command.toJSON()));

        servicesSettingsService.applyServicesSettings(command);

        ServicesSettingsWrapper newConfig = configurationServiceTest.loadServicesSettings();

        //assertEquals(expectedNewConfig, newConfig.getFormattedValue());
        assertTrue (new JSONObject(expectedNewConfig).similar(newConfig.getJSONObject()));

        String notifications = operationsMonitoringServiceTest.getAllMessages();

        System.err.println (notifications);

        assertTrue(notifications.contains("Check--Install_settings_192-168-10-13 : --> Done : Executing Check / Install of settings on 192.168.10.13"));
        assertTrue(notifications.contains("Check--Install_settings_192-168-10-11 : --> Done : Executing Check / Install of settings on 192.168.10.11"));

        assertTrue(notifications.contains("Done : Executing restart of database on (kubernetes)"));
        assertTrue(notifications.contains("Done : Executing restart of calculator-runtime on (kubernetes)"));
        assertTrue(notifications.contains("Done : Executing restart of broker on (kubernetes)"));
    }

}

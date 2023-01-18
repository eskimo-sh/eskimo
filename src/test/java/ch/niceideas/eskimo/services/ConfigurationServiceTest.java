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

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.model.ServicesSettingsWrapper;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.SetupServiceTestImpl;
import ch.niceideas.eskimo.utils.OSDetector;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-setup"})
public class ConfigurationServiceTest {

    private static final Logger logger = Logger.getLogger(ConfigurationServiceTest.class);

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private SetupServiceTestImpl setupServiceTest;

    private String configStoragePath = null;

    @BeforeEach
    public void setUp() throws Exception {

        configStoragePath = setupServiceTest.getConfigStoragePath();

        setupServiceTest.reset();

        SecurityContextHelper.loginAdmin();
    }

    @Test
    public void testLoadAndSaveServicesConfig() throws Exception {

        ServicesSettingsWrapper sc = configurationService.loadServicesSettings();
        assertNotNull(sc);
        assertFalse(sc.isEmpty()); // services config is initialized with defaults

        sc = new ServicesSettingsWrapper("{\"test\": \"OK\"}");
        configurationService.saveServicesSettings(sc);

        ServicesSettingsWrapper sc2 = configurationService.loadServicesSettings();
        assertTrue(sc.getJSONObject().similar(sc2.getJSONObject()));
    }

    @Test
    public void testLoadAndSaveKubernetesServicesConfig() throws Exception {

        KubernetesServicesConfigWrapper ksc = configurationService.loadKubernetesServicesConfig();
        assertNull(ksc);

        ksc = new KubernetesServicesConfigWrapper("{\"test\": \"OK\"}");
        configurationService.saveKubernetesServicesConfig(ksc);

        KubernetesServicesConfigWrapper ksc2 = configurationService.loadKubernetesServicesConfig();
        assertTrue(ksc.getJSONObject().similar(ksc2.getJSONObject()));
    }

    @Test
    public void testLoadAndSaveSetupConfig() throws Exception {

        SetupException exception = assertThrows(SetupException.class, () -> configurationService.loadSetupConfig());

        assertEquals("Application is not initialized properly. Missing file '/config.json' with system configuration", exception.getMessage());

        final JsonWrapper sc = new JsonWrapper("{\"test\": \"OK\"}");
        exception = assertThrows(SetupException.class, () -> configurationService.createSetupConfigAndSaveStoragePath(sc.getFormattedValue()));

        assertEquals("config Storage path cannot be empty.", exception.getMessage());

        if (OSDetector.isUnix()) {

            JsonWrapper sc2 = configurationService.createSetupConfigAndSaveStoragePath("{\"test\": \"OK\", \"setup_storage\": \"" + configStoragePath + "\"}");
            configurationService.saveSetupConfig(sc2.getFormattedValue());

            JsonWrapper sc3 = configurationService.loadSetupConfig();
            assertTrue(sc2.getJSONObject().similar(sc3.getJSONObject()));
        }
    }

    @Test
    public void testLoadAndSaveNodesConfig() throws Exception {

        NodesConfigWrapper nc = configurationService.loadNodesConfig();
        assertNull(nc);

        nc = new NodesConfigWrapper("{\"test\": \"OK\"}");
        configurationService.saveNodesConfig(nc);

        NodesConfigWrapper nc2 = configurationService.loadNodesConfig();
        assertTrue(nc.getJSONObject().similar(nc2.getJSONObject()));
    }

    @Test
    public void testLoadAndSaveServicesInstallationStatus() throws Exception {

        ServicesInstallStatusWrapper sis = configurationService.loadServicesInstallationStatus();
        assertTrue(sis.isEmpty());

        sis = new ServicesInstallStatusWrapper("{\"test\": \"OK\"}");
        configurationService.saveServicesInstallationStatus(sis);

        ServicesInstallStatusWrapper sis2 = configurationService.loadServicesInstallationStatus();
        assertTrue(sis.getJSONObject().similar(sis2.getJSONObject()));
    }

    @Test
    public void testUpdateAndSaveServicesInstallationStatus() throws Exception {

        ServicesInstallStatusWrapper sis = configurationService.loadServicesInstallationStatus();
        assertTrue(sis.isEmpty());

        configurationService.updateAndSaveServicesInstallationStatus(
                servicesInstallationStatus -> servicesInstallationStatus.setValueForPath("test", "OK"));

        ServicesInstallStatusWrapper sis2 = configurationService.loadServicesInstallationStatus();
        assertEquals("OK", sis2.getValueForPathAsString("test"));
    }
}

package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.eskimo.model.MarathonServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class ConfigurationServiceTest {

    private static final Logger logger = Logger.getLogger(ConfigurationServiceTest.class);

    private File tmpFile = null;

    private ConfigurationService configurationService = null;


    @Before
    public void setUp() throws Exception {
        try {
            tmpFile = File.createTempFile("test_", "_configurationService");
            tmpFile.delete();
            tmpFile.mkdirs();
        } catch (IOException e) {
            logger.error (e, e);
            throw new SetupException(e);
        }
        configurationService = new ConfigurationService();
        configurationService.setSetupService(new SetupService() {
            @Override
            public String getConfigStoragePath() throws SetupException {
                return tmpFile.getAbsolutePath();
            }
        });
    }

    @Test
    public void testLoadAndSaveMarathonServicesConfig() throws Exception {

        MarathonServicesConfigWrapper mc = configurationService.loadMarathonServicesConfig();
        assertNull(mc);

        mc = new MarathonServicesConfigWrapper("{\"test\": \"OK\"}");
        configurationService.saveMarathonServicesConfig(mc);

        MarathonServicesConfigWrapper mc2 = configurationService.loadMarathonServicesConfig();
        assertTrue(mc.getJSONObject().similar(mc2.getJSONObject()));
    }

    @Test
    public void testLoadAndSaveSetupConfig() throws Exception {

        SetupException exception = assertThrows(SetupException.class, () -> {
            configurationService.loadSetupConfig();
        });

        assertEquals("Application is not initialized properly. Missing file 'config.conf' system configuration", exception.getMessage());

        final JsonWrapper sc = new JsonWrapper("{\"test\": \"OK\"}");
        exception = assertThrows(SetupException.class, () -> {
            configurationService.createSetupConfigAndSaveStoragePath(sc.getFormattedValue());
        });

        assertEquals("config Storage path cannot be empty.", exception.getMessage());

        JsonWrapper sc2 = configurationService.createSetupConfigAndSaveStoragePath("{\"test\": \"OK\", \"setup_storage\": \""+tmpFile.getAbsolutePath()+"\"}");
        configurationService.saveSetupConfig(sc2.getFormattedValue());

        JsonWrapper sc3 = new JsonWrapper(configurationService.loadSetupConfig());
        assertTrue(sc2.getJSONObject().similar(sc3.getJSONObject()));
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

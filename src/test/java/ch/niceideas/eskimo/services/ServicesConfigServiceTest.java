/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.eskimo.model.OperationsCommand;
import ch.niceideas.eskimo.model.ServicesConfigWrapper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class ServicesConfigServiceTest extends AbstractSystemTest {

    private static final Logger logger = Logger.getLogger(ServicesConfigServiceTest.class);

    private String jsonConfig = null;
    private String testForm = null;

    private ServicesConfigService scs;

    private AtomicReference<OperationsCommand> processedCommand = new AtomicReference<>();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesConfigTest/testConfig.json"));
        testForm = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesConfigTest/testForm.json"));

        scs = new ServicesConfigService();

        scs.setServicesDefinition(servicesDefinition);

        scs.setSystemService(systemService);

        scs.setSetupService(setupService);

        scs.setNodeRangeResolver(nodeRangeResolver);

        scs.setConfigurationService(configurationService);

        setupService.setConfigStoragePathInternal(SystemServiceTest.createTempStoragePath());
    }

    protected SystemService createSystemService() {
        return new SystemService() {
            public void applyNodesConfig(OperationsCommand command)
                    throws SystemException, JSONException, ServiceDefinitionException, NodesConfigurationException {
                processedCommand.set(command);
            }
        };
    }

    @Test
    public void testSaveAndReload() throws Exception {

        scs.saveServicesConfig(new ServicesConfigWrapper(jsonConfig));

        ServicesConfigWrapper newConfig = scs.loadServicesConfig();

        // test elasticsearch config
        assertEquals ("elasticsearch.yml", newConfig.getValueForPath("configs.9.configs.0.filename"));
        assertEquals ("elasticsearch", newConfig.getValueForPath("configs.9.configs.0.service"));
        assertEquals ("bootstrap.memory_lock", newConfig.getValueForPath("configs.9.configs.0.properties.0.name"));

        // test kafka config
        assertEquals ("server.properties", newConfig.getValueForPath("configs.12.configs.0.filename"));
        assertEquals ("kafka", newConfig.getValueForPath("configs.12.configs.0.service"));
        assertEquals ("num.network.threads", newConfig.getValueForPath("configs.12.configs.0.properties.0.name"));
    }

    @Test
    public void testSaveAndApplyServicesConfig() throws Exception {
        //fail ("To Be Implemented");

        configurationService.saveNodesConfig(StandardSetupHelpers.getStandard2NodesSetup());

        scs.saveServicesConfig(new ServicesConfigWrapper(jsonConfig));

        scs.saveAndApplyServicesConfig(testForm);

        ServicesConfigWrapper newConfig = scs.loadServicesConfig();

        // test elasticsearch config
        assertEquals ("bootstrap.memory_lock", newConfig.getValueForPath("configs.9.configs.0.properties.0.name"));
        assertEquals ("false", newConfig.getValueForPath("configs.9.configs.0.properties.0.defaultValue"));
        assertEquals (null, newConfig.getValueForPath("configs.9.configs.0.properties.0.value"));

        assertEquals ("action.destructive_requires_name", newConfig.getValueForPath("configs.9.configs.0.properties.1.name"));
        assertEquals ("true", newConfig.getValueForPath("configs.9.configs.0.properties.1.defaultValue"));
        assertEquals ("false", newConfig.getValueForPath("configs.9.configs.0.properties.1.value"));

        // test kafka config
        assertEquals ("num.network.threads", newConfig.getValueForPath("configs.12.configs.0.properties.0.name"));
        assertEquals ("3", newConfig.getValueForPath("configs.12.configs.0.properties.0.defaultValue"));
        assertEquals ("5", newConfig.getValueForPath("configs.12.configs.0.properties.0.value"));

        assertEquals ("num.io.threads", newConfig.getValueForPath("configs.12.configs.0.properties.1.name"));
        assertEquals ("8", newConfig.getValueForPath("configs.12.configs.0.properties.1.defaultValue"));
        assertEquals ("10", newConfig.getValueForPath("configs.12.configs.0.properties.1.value"));

        assertEquals ("socket.send.buffer.bytes", newConfig.getValueForPath("configs.12.configs.0.properties.2.name"));
        assertEquals ("102400", newConfig.getValueForPath("configs.12.configs.0.properties.2.defaultValue"));
        assertEquals (null, newConfig.getValueForPath("configs.12.configs.0.properties.2.value"));

        assertEquals ("socket.receive.buffer.bytes", newConfig.getValueForPath("configs.12.configs.0.properties.3.name"));
        assertEquals ("102400", newConfig.getValueForPath("configs.12.configs.0.properties.3.defaultValue"));
        assertEquals (null, newConfig.getValueForPath("configs.12.configs.0.properties.3.value"));

        assertEquals ("socket.request.max.bytes", newConfig.getValueForPath("configs.12.configs.0.properties.4.name"));
        assertEquals ("104857600", newConfig.getValueForPath("configs.12.configs.0.properties.4.defaultValue"));
        assertEquals (null, newConfig.getValueForPath("configs.12.configs.0.properties.4.value"));

        assertEquals ("num.partitions", newConfig.getValueForPath("configs.12.configs.0.properties.5.name"));
        assertEquals ("1", newConfig.getValueForPath("configs.12.configs.0.properties.5.defaultValue"));
        assertEquals (null, newConfig.getValueForPath("configs.12.configs.0.properties.5.value"));

        assertEquals ("log.retention.hours", newConfig.getValueForPath("configs.12.configs.0.properties.6.name"));
        assertEquals ("168", newConfig.getValueForPath("configs.12.configs.0.properties.6.defaultValue"));
        assertEquals (null, newConfig.getValueForPath("configs.12.configs.0.properties.6.value"));

        // test spark config
        assertEquals ("spark.driver.memory", newConfig.getValueForPath("configs.13.configs.0.properties.0.name"));
        assertEquals ("800m", newConfig.getValueForPath("configs.13.configs.0.properties.0.defaultValue"));
        assertEquals ("500m", newConfig.getValueForPath("configs.13.configs.0.properties.0.value"));

        assertEquals ("spark.rpc.numRetries", newConfig.getValueForPath("configs.13.configs.0.properties.1.name"));
        assertEquals ("5", newConfig.getValueForPath("configs.13.configs.0.properties.1.defaultValue"));
        assertEquals ("10", newConfig.getValueForPath("configs.13.configs.0.properties.1.value"));

        assertEquals ("spark.rpc.retry.wait", newConfig.getValueForPath("configs.13.configs.0.properties.2.name"));
        assertEquals ("5s", newConfig.getValueForPath("configs.13.configs.0.properties.2.defaultValue"));
        assertEquals (null, newConfig.getValueForPath("configs.13.configs.0.properties.2.value"));

        assertEquals ("spark.scheduler.mode", newConfig.getValueForPath("configs.13.configs.0.properties.3.name"));
        assertEquals ("FAIR", newConfig.getValueForPath("configs.13.configs.0.properties.3.defaultValue"));
        assertEquals ("FIFO", newConfig.getValueForPath("configs.13.configs.0.properties.3.value"));

        assertEquals ("spark.locality.wait", newConfig.getValueForPath("configs.13.configs.0.properties.4.name"));
        assertEquals ("20s", newConfig.getValueForPath("configs.13.configs.0.properties.4.defaultValue"));
        assertEquals (null, newConfig.getValueForPath("configs.13.configs.0.properties.4.value"));

        assertEquals ("spark.dynamicAllocation.executorIdleTimeout", newConfig.getValueForPath("configs.13.configs.0.properties.5.name"));
        assertEquals ("200s", newConfig.getValueForPath("configs.13.configs.0.properties.5.defaultValue"));
        assertEquals (null, newConfig.getValueForPath("configs.13.configs.0.properties.5.value"));

        assertEquals ("spark.dynamicAllocation.cachedExecutorIdleTimeout", newConfig.getValueForPath("configs.13.configs.0.properties.6.name"));
        assertEquals ("300s", newConfig.getValueForPath("configs.13.configs.0.properties.6.defaultValue"));
        assertEquals ("400s", newConfig.getValueForPath("configs.13.configs.0.properties.6.value"));

        assertEquals ("spark.executor.memory", newConfig.getValueForPath("configs.13.configs.0.properties.7.name"));
        assertEquals ("[ESKIMO_DEFAULT]", newConfig.getValueForPath("configs.13.configs.0.properties.7.defaultValue"));
        assertEquals (null, newConfig.getValueForPath("configs.13.configs.0.properties.7.value"));

    }

}

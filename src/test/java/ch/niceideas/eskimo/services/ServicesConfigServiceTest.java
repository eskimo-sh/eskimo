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

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.model.*;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.*;

import static org.junit.Assert.*;

public class ServicesConfigServiceTest extends AbstractSystemTest {

    private static final Logger logger = Logger.getLogger(ServicesConfigServiceTest.class);

    private String jsonConfig = null;

    private ServicesConfigService scs;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        jsonConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoServicesConfigTest/testConfig.json"));

        scs = new ServicesConfigService();

        scs.setServicesDefinition(servicesDefinition);

        scs.setSystemService(systemService);

        scs.setSetupService(setupService);

        setupService.setConfigStoragePathInternal(SystemServiceTest.createTempStoragePath());
    }

    @Test
    public void testSaveAndReload() throws Exception {

        scs.saveServicesConfig(new ServicesConfigWrapper(jsonConfig));

        ServicesConfigWrapper newConfig = scs.loadServicesConfig();

        // test a few configs
        assertEquals ("elasticsearch.yml", newConfig.getValueForPath("configs.9.configs.0.filename"));
        assertEquals ("elasticsearch", newConfig.getValueForPath("configs.9.configs.0.service"));
        assertEquals ("bootstrap.memory_lock", newConfig.getValueForPath("configs.9.configs.0.properties.0.name"));

        assertEquals ("server.properties", newConfig.getValueForPath("configs.12.configs.0.filename"));
        assertEquals ("kafka", newConfig.getValueForPath("configs.12.configs.0.service"));
        assertEquals ("num.network.threads", newConfig.getValueForPath("configs.12.configs.0.properties.0.name"));
    }

    @Test
    public void testInjectFormValues() throws Exception {
        fail ("To Be Implemented");
    }

}

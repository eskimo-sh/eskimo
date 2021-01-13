/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

import ch.niceideas.eskimo.model.MarathonServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MarathonServicesConfigCheckerTest {

    private MarathonServicesConfigChecker marathonConfigChecker = new MarathonServicesConfigChecker();
    private ConfigurationService configurationService = new ConfigurationService();
    private SetupService setupService = new SetupService();

    @BeforeEach
    public void setUp() throws Exception {

        ServicesDefinition def = new ServicesDefinition();
        def.afterPropertiesSet();

        setupService = new SetupService();
        configurationService.setSetupService(setupService);

        setupService.setConfigurationService (configurationService);

        configurationService.setSetupService(setupService);

        marathonConfigChecker.setServicesDefinition(def);

        setupService.setConfigStoragePathInternal(SystemServiceTest.createTempStoragePath());
        marathonConfigChecker.setConfigurationService(configurationService);

        ServicesDefinition servicesDefinition = new ServicesDefinition();
        servicesDefinition.afterPropertiesSet();
        marathonConfigChecker.setServicesDefinition(servicesDefinition);
    }

    @Test
    public void testCheckMarathonSetupOK() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("marathon", "1");
            put("ntp1", "on");
            put("prometheus1", "on");
            put("elasticsearch1", "on");
        }});
        configurationService.saveNodesConfig(nodesConfig);

        MarathonServicesConfigWrapper marathonConfig = new MarathonServicesConfigWrapper(new HashMap<String, Object>() {{
                put("cerebro_installed", "on");
                put("kibana_install", "on");
                put("grafana_install", "on");
        }});

        marathonConfigChecker.checkMarathonServicesSetup(marathonConfig);
    }

    @Test
    public void testOneCerebroButNoES() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("cerebro", "1");
            put("ntp1", "on");
            put("prometheus1", "on");
        }});
        configurationService.saveNodesConfig(nodesConfig);

        MarathonServicesConfigException exception = assertThrows(MarathonServicesConfigException.class, () -> {

            MarathonServicesConfigWrapper marathonConfig = new MarathonServicesConfigWrapper(new HashMap<String, Object>() {{
                put("cerebro_installed", "on");
            }});

            marathonConfigChecker.checkMarathonServicesSetup(marathonConfig);
        });

        assertEquals("Inconsistency found : Service cerebro expects 1 elasticsearch instance(s). But only 0 has been found !", exception.getMessage());
    }

    @Test
    public void testZeppelinButNoZookeeper() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("ntp1", "on");
            put("prometheus1", "on");
            put("elasticsearch1", "on");

        }});
        configurationService.saveNodesConfig(nodesConfig);

        MarathonServicesConfigException exception = assertThrows(MarathonServicesConfigException.class, () -> {

            MarathonServicesConfigWrapper marathonConfig = new MarathonServicesConfigWrapper(new HashMap<String, Object>() {{
                put("zeppelin_installed", "on");
            }});

            marathonConfigChecker.checkMarathonServicesSetup(marathonConfig);
        });

        assertEquals("Inconsistency found : Service zeppelin expects 1 zookeeper instance(s). But only 0 has been found !", exception.getMessage());
    }

    @Test
    public void testNonMarathonServiceCanBeSelected() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("node_id1", "192.168.10.11");
            put("ntp1", "on");
            put("prometheus1", "on");
            put("elasticsearch1", "on");
            put("zookeeper", "1");
        }});
        configurationService.saveNodesConfig(nodesConfig);

        MarathonServicesConfigException exception = assertThrows(MarathonServicesConfigException.class, () -> {

            MarathonServicesConfigWrapper marathonConfig = new MarathonServicesConfigWrapper(new HashMap<String, Object>() {{
                put("zookeeper_installed", "on");
            }});

            marathonConfigChecker.checkMarathonServicesSetup(marathonConfig);
        });

        assertEquals("Inconsistency found : service zookeeper is not a marathon service", exception.getMessage());
    }

}

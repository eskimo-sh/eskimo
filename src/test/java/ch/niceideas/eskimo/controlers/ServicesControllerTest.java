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
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.test.services.ServicesDefinitionTestImpl;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-services", "test-system", "test-setup"})
public class ServicesControllerTest {

    @Autowired
    private ServicesController sc;

    @Autowired
    private ServicesDefinitionTestImpl servicesDefinitionTest;

    @BeforeEach
    public void setUp() throws Exception {
        servicesDefinitionTest.reset();
    }

    @Test
    public void testListServices() {

        assertEquals ("{\n" +
                "  \"services\": [\n" +
                "    \"distributed-time\",\n" +
                "    \"cluster-manager\",\n" +
                "    \"distributed-filesystem\",\n" +
                "    \"cluster-master\",\n" +
                "    \"cluster-slave\",\n" +
                "    \"cluster-dashboard\",\n" +
                "    \"broker\",\n" +
                "    \"broker-cli\",\n" +
                "    \"broker-manager\",\n" +
                "    \"calculator-runtime\",\n" +
                "    \"calculator-cli\",\n" +
                "    \"database-manager\",\n" +
                "    \"database\",\n" +
                "    \"user-console\"\n" +
                "  ],\n" +
                "  \"status\": \"OK\"\n" +
                "}", sc.listServices());

        servicesDefinitionTest.setError();

        assertEquals ("{\n" +
                "  \"error\": \"Test error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", sc.listServices());
    }

    @Test
    public void testListUIServices() {

        assertEquals ("{\n" +
                "  \"uiServices\": [\n" +
                "    \"distributed-filesystem\",\n" +
                "    \"cluster-dashboard\",\n" +
                "    \"broker-manager\",\n" +
                "    \"database-manager\",\n" +
                "    \"user-console\"\n" +
                "  ],\n" +
                "  \"status\": \"OK\"\n" +
                "}", sc.listUIServices());

        servicesDefinitionTest.setError();

        assertEquals ("{\n" +
                "  \"error\": \"Test error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", sc.listUIServices());
    }

    @Test
    public void testGetUIServicesConfig() throws Exception {

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesControllerTest/expectedUIServicesConfig.json"), StandardCharsets.UTF_8);

        //System.err.println (sc.getUIServicesConfig());

        assertTrue (new JSONObject(expectedResult).similar(new JSONObject(sc.getUIServicesConfig())));

        servicesDefinitionTest.setError();

        assertEquals ("{\n" +
                "  \"error\": \"Test error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", sc.getUIServicesConfig());
    }

    @Test
    public void testGetUIServicesStatusConfig() throws Exception {

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesControllerTest/expectedUIServicesStatusConfig.json"), StandardCharsets.UTF_8);

        //System.err.println(sc.getUIServicesStatusConfig());

        assertTrue (new JSONObject(expectedResult).similar(new JSONObject(sc.getUIServicesStatusConfig())));
    }

    @Test
    public void testGetServicesDependencies() throws Exception {

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesControllerTest/expectedServicesDependencies.json"), StandardCharsets.UTF_8);

        //System.err.println(sc.getServicesDependencies());

        assertTrue (new JSONObject(expectedResult).similar(new JSONObject(sc.getServicesDependencies())));
    }

    @Test
    public void testGetServicesConfigurations() throws Exception {

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesControllerTest/expectedServicesConfiguration.json"), StandardCharsets.UTF_8);

        //System.err.println(sc.getServicesConfigurations());

        assertTrue (new JSONObject(expectedResult).similar(new JSONObject(sc.getServicesConfigurations())));
    }

    @Test
    public void testListConfigServices() throws Exception {

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesControllerTest/expecteConfigServices.json"), StandardCharsets.UTF_8);

        //System.err.println(sc.listConfigServices());

        assertTrue(new JSONObject(expectedResult).similar(new JSONObject(sc.listConfigServices())));
    }

    @Test
    public void testGetKubernetesServices() throws Exception {

        String expectedResult = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("ServicesControllerTest/expectedKubeServices.json"), StandardCharsets.UTF_8);

        //System.err.println(sc.getKubernetesServices());

        assertTrue(new JSONObject(expectedResult).similar(new JSONObject(sc.getKubernetesServices())));
    }

}

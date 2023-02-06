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


package ch.niceideas.eskimo.model;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.SetupServiceTestImpl;
import ch.niceideas.eskimo.utils.OSDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-setup", "test-services"})
public class SetupCommandTest {

    @Autowired
    private SetupServiceTestImpl setupServiceTest;

    @Autowired
    private ServicesDefinition servicesDefinition;

    private String setupConfig = null;

    @BeforeEach
    public void setUp() throws Exception {
        setupConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SetupServiceTest/setupConfig.json"), StandardCharsets.UTF_8);

        SecurityContextHelper.loginAdmin();

        setupServiceTest.reset();
    }

    @Test
    public void toJSON () throws Exception {

        SetupCommand setupCommand = createCommand();

        assertEquals ("{\n" +
                "  \"buildPackage\": [\n" +
                "    \"base-eskimo\",\n" +
                "    \"cluster-manager\",\n" +
                "    \"distributed-time\",\n" +
                "    \"distributed-filesystem\",\n" +
                "    \"cluster-master\",\n" +
                "    \"cluster-dashboard\",\n" +
                "    \"calculator\",\n" +
                "    \"database\",\n" +
                "    \"database-manager\",\n" +
                "    \"broker\",\n" +
                "    \"broker-manager\",\n" +
                "    \"user-console\"\n" +
                "  ],\n" +
                "  \"buildKube\": [\"kube\"],\n" +
                "  \"downloadKube\": [],\n" +
                "  \"none\": false,\n" +
                "  \"downloadPackages\": [],\n" +
                "  \"packageUpdates\": [],\n" +
                "  \"packageDownloadUrl\": \"dummy\"\n" +
                "}", setupCommand.toJSON().toString(2));
    }

    private SetupCommand createCommand() throws IOException, FileException, SetupException {
        assumeTrue(OSDetector.isUnix());

        File packageDevPathTest = File.createTempFile("test_setup_service_package_dev", "folder");
        assertTrue (packageDevPathTest.delete());
        assertTrue (packageDevPathTest.mkdirs());

        setupServiceTest.setPackageDistributionPath(packageDevPathTest.getAbsolutePath());

        FileUtils.writeFile(new File (packageDevPathTest.getAbsolutePath() + "/build.sh"),
                "#!/bin/bash\n" +
                        "echo $@\n");

        packageDevPathTest.deleteOnExit();

        return SetupCommand.create(new JsonWrapper(setupConfig), setupServiceTest, servicesDefinition);
    }

    @Test
    public void testGetAllOperationsInOrder() throws Exception {

        SetupCommand setupCommand = createCommand();

        List<SetupCommand.SetupOperationId> opInOrder = setupCommand.getAllOperationsInOrder(null);

        assertEquals ("Build_base-eskimo,Build_cluster-manager,Build_distributed-time,Build_distributed-filesystem,Build_cluster-master,Build_cluster-dashboard,Build_calculator,Build_database,Build_database-manager,Build_broker,Build_broker-manager,Build_user-console,Build_kube",
                opInOrder.stream().map(SetupCommand.SetupOperationId::toString).collect(Collectors.joining(",")));
    }

}

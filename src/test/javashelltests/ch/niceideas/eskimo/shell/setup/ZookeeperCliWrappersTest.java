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


package ch.niceideas.eskimo.shell.setup;

import ch.niceideas.common.utils.*;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZookeeperCliWrappersTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(ZookeeperCliWrappersTest.class);

    protected static String jailPath = null;

    private static boolean initialized = false;

    @BeforeEach
    public void setUp() throws Exception {
        if (!initialized) {
            jailPath = setupJail(getServiceName());

            // I need some real commands
            assertTrue (new File (jailPath + "/bash").delete());
            assertTrue (new File (jailPath + "/sed").delete());
            initialized = true;
        }
        File dockerLogs = new File (getJailPath() + "/.log_docker");
        if (dockerLogs.exists()) {
            assertTrue (dockerLogs.delete());
        }
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
        if (initialized && StringUtils.isNotBlank(jailPath)) {
            FileUtils.delete(new File(jailPath));
        }
    }

    @Override
    protected String getJailPath() {
        return jailPath;
    }

    @Override
    protected String getServiceName() {
        return "zookeeper";
    }

    @Override
    protected String getImageName() {
        return null;
    }

    @Override
    protected String getTemplateName() {
        return null;
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh and common.sh are automatic
        copyFile(jailPath, "../base-eskimo/eskimo-utils.sh");
        copyFile(jailPath, "zookeeper_wrappers/zkCli.sh");
    }

    @Test
    public void testZkCli() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-zkCli.sh", "zkCli.sh");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-zkCli.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                    "-i " +
                    "--rm " +
                    "--network host " +
                    "--user zookeeper " +
                    "-v /var/log/zookeeper:/var/log/zookeeper:shared " +
                    "-v /var/lib/zookeeper:/var/lib/zookeeper:shared " +
                    "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                    "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                    "-e NODE_NAME=testhost " +
                    "eskimo/zookeeper:1 " +
                    "/usr/share/zookeeper/bin/zkCli.sh\n", dockerLogs);
    }
}

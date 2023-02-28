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


package ch.niceideas.eskimo.shell.base;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.ProcessHelper;
import ch.niceideas.eskimo.shell.setup.AbstractSetupShellTest;
import ch.niceideas.eskimo.utils.OSDetector;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EskimoUtilsDockerVolumeParserTest {

    private static final Logger logger = Logger.getLogger(EskimoUtilsDockerVolumeParserTest.class);

    protected String jailPath = null;

    /** Run Test on Linux only */
    @BeforeEach
    public void beforeMethod() {
        Assumptions.assumeTrue(OSDetector.isPosix());
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (StringUtils.isNotBlank(jailPath)) {
            FileUtils.delete(new File(jailPath));
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        jailPath = AbstractSetupShellTest.createJail();

        FileUtils.copy(
                new File("./services_setup/base-eskimo/eskimo-utils.sh"),
                new File (jailPath + "/eskimo-utils.sh"));

        ProcessHelper.exec(new String[]{"bash", "-c", "chmod 777 " + jailPath + "/eskimo-utils.sh"}, true);

        // I need some real commands
        assertTrue (new File (jailPath + "/bash").delete());
        assertTrue (new File (jailPath + "/sed").delete());
    }

    private void createTestScript(String scriptName, String args) throws FileException {

        String script = "#!/bin/bash\n" + "\n" +
                AbstractSetupShellTest.COMMON_SCRIPT_HACKS +
                "parse_cli_docker_volume_mounts " + args + "\n" +
                "\n" +
                "echo $DOCKER_VOLUMES_ARGS";

        FileUtils.writeFile(new File (jailPath + "/" + scriptName), script);
    }

    @Test
    public void testMultipleComaSeparated() throws Exception {

        createTestScript("calling_script.sh", "--jars,--files multiple , --jars /var/lib/spark.jar,/var/lib/spark2.jar,/var/cache/test.jar --files /tmp/test.properties");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);
        logger.debug(result);

        assertEquals ("-v /tmp:/tmp:slave -v /var/cache:/var/cache:slave -v /var/lib:/var/lib:slave\n", result);
    }

    @Test
    public void testMultipleSemiColonSeparated() throws Exception {

        createTestScript("calling_script.sh", "-driver-class-path multiple : -driver-class-path /var/lib/spark.jar:/var/lib/spark2.jar:/var/cache/test.jar");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);
        logger.debug(result);

        assertEquals ("-v /var/cache:/var/cache:slave -v /var/lib:/var/lib:slave\n", result);
    }

    @Test
    public void testSingle() throws Exception {

        createTestScript("calling_script.sh", "--properties-file single --properties-file /usr/local/spark/props/test.properties");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);
        logger.debug(result);

        assertEquals ("-v /usr/local/spark/props:/usr/local/spark/props:slave\n", result);
    }

}

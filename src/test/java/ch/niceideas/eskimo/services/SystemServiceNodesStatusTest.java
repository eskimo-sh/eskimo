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

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.model.MarathonServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.model.SystemStatusWrapper;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

public class SystemServiceNodesStatusTest extends AbstractSystemTest {

    private static final Logger logger = Logger.getLogger(SystemServiceNodesStatusTest.class);

    private String testRunUUID = UUID.randomUUID().toString();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setupService.setConfigStoragePathInternal(SystemServiceTest.createTempStoragePath());
    }

    @Override
    protected SystemService createSystemService() {
        SystemService ss = new SystemService() {
            @Override
            protected File createTempFile(String serviceOrFlag, String ipAddress, String extension) throws IOException {
                File retFile = new File ("/tmp/"+serviceOrFlag+"-"+testRunUUID+"-"+ipAddress+extension);
                retFile.createNewFile();
                return retFile;
            }
        };
        ss.setConfigurationService(configurationService);
        return ss;
    }

    @Override
    protected ConfigurationService createConfigurationService() {
        return new ConfigurationService() {
            @Override
            public NodesConfigWrapper loadNodesConfig() throws SystemException, SetupException {
                return StandardSetupHelpers.getStandard2NodesSetup();
            }
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
                return StandardSetupHelpers.getStandard2NodesStatus();
            }
            @Override
            public MarathonServicesConfigWrapper loadMarathonServicesConfig() throws SystemException  {
                return StandardSetupHelpers.getStandardMarathonConfig();
            }
        };
    }


    @Test
    public void testGetStatus() throws Exception {

        systemService.setSshCommandService(new SSHCommandService() {
            @Override
            public String runSSHScript(String hostAddress, String script, boolean throwsException) throws SSHCommandException {
                testSSHCommandScript.append(script).append("\n");
                if (script.equals("echo OK")) {
                    return "OK";
                }
                if (script.startsWith("sudo systemctl status --no-pager -al")) {
                    return systemStatusTest;
                }
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public String runSSHCommand(String hostAddress, String command) throws SSHCommandException {
                testSSHCommandScript.append(command + "\n");
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public void copySCPFile(String hostAddress, String filePath) throws SSHCommandException {
                // just do nothing
            }
        });

        SystemStatusWrapper systemStatus = systemService.getStatus();

        String expectedStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedSystemStatus.json"), "UTF-8");

        //assertEquals(expectedStatus, systemStatus.getFormattedValue());
        assertTrue(new JsonWrapper(expectedStatus).getJSONObject().similar(systemStatus.getJSONObject()));
    }
}

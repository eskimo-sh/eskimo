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

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.model.SystemStatusWrapper;
import com.trilead.ssh2.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SystemServiceNodesStatusTest extends AbstractSystemTest {

    private String testRunUUID = UUID.randomUUID().toString();

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setupService.setConfigStoragePathInternal(SystemServiceTest.createTempStoragePath());
    }

    @Override
    protected SystemService createSystemService() {
        SystemService ss = new SystemService(false) {
            @Override
            protected File createTempFile(String serviceOrFlag, String node, String extension) throws IOException {
                File retFile = new File ("/tmp/"+serviceOrFlag+"-"+testRunUUID+"-"+ node +extension);
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
            public NodesConfigWrapper loadNodesConfig() {
                return StandardSetupHelpers.getStandard2NodesSetup();
            }
            @Override
            public ServicesInstallStatusWrapper loadServicesInstallationStatus() {
                return StandardSetupHelpers.getStandard2NodesInstallStatus();
            }
            @Override
            public KubernetesServicesConfigWrapper loadKubernetesServicesConfig() {
                return StandardSetupHelpers.getStandardMarathonConfig();
            }
        };
    }


    @Test
    public void testGetStatus() throws Exception {

        systemService.setSshCommandService(new SSHCommandService() {
            @Override
            public String runSSHScript(Connection connection, String script, boolean throwsException) {
                return runSSHScript((String)null, script, throwsException);
            }
            @Override
            public String runSSHScript(String node, String script, boolean throwsException) {
                testSSHCommandScript.append(script).append("\n");
                if (script.equals("echo OK")) {
                    return "OK";
                }
                if (script.startsWith("sudo systemctl status --no-pager")) {
                    return systemStatusTest;
                }
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public String runSSHCommand(Connection connection, String command) {
                return runSSHCommand((String)null, command);
            }
            @Override
            public String runSSHCommand(String node, String command) {
                testSSHCommandScript.append(command).append("\n");
                return testSSHCommandResultBuilder.toString();
            }
            @Override
            public void copySCPFile(Connection connection, String filePath) {
                // just do nothing
            }
            @Override
            public void copySCPFile(String node, String filePath)  {
                // just do nothing
            }
        });

        systemService.updateStatus();
        SystemStatusWrapper systemStatus = systemService.getStatus();

        String expectedStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SystemServiceTest/expectedSystemStatus.json"), "UTF-8");

        //assertEquals(expectedStatus, systemStatus.getFormattedValue());
        assertTrue(new JsonWrapper(expectedStatus).getJSONObject().similar(systemStatus.getJSONObject()));
    }
}

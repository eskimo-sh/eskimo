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

package ch.niceideas.eskimo.utils;

import ch.niceideas.common.utils.ProcessHelper;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.test.services.SSHCommandServiceTestImpl;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-setup", "test-ssh"})
public class SystemStatusParserTest {

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private SSHCommandServiceTestImpl sshCommandServiceTest;

    @Test
    public void testPattern() {

        assertTrue (SystemStatusParser.pattern.matcher("Active: active (exited) since Fri 2019-05-31 13:55:26 UTC; 1 day 20h ago").matches());
        assertTrue (SystemStatusParser.pattern.matcher("Active: active (running) since Fri 2019-05-31 13:55:31 UTC; 1 day 20h ago").matches());
        assertTrue (SystemStatusParser.pattern.matcher("Active: failed (Result: exit-code) since Sun 2019-06-02 10:28:47 UTC; 5min ago").matches());
    }

    @Test
    public void testKubeSlaveCase () throws Exception {

        loadTestConfig("kube-slave-case.log");
        SystemStatusParser parser = new SystemStatusParser(Node.fromAddress("192.168.56.24"), sshCommandServiceTest, servicesDefinition);

        String serviceStatus = parser.getServiceStatus(Service.from("kube-slave"));

        assertEquals ("Result: exit-code", serviceStatus);
    }

    @Test
    public void testParseFileDeb() throws Exception {

        loadTestConfig("systemctl-out-debnode1.log");
        SystemStatusParser parser = new SystemStatusParser(Node.fromAddress("192.168.10.13"), sshCommandServiceTest, servicesDefinition);

        assertEquals ("NA", parser.getServiceStatus(Service.from("tada")));
        assertEquals ("running", parser.getServiceStatus(Service.from("elasticsearch")));
        assertEquals ("running", parser.getServiceStatus(Service.from("mesos-agent")));
        assertEquals ("dead", parser.getServiceStatus(Service.from("emergency")));
    }

    @Test
    public void testParseFileCent() throws Exception {

        loadTestConfig("systemctl-out-centnode1.log");
        SystemStatusParser parser = new SystemStatusParser(Node.fromAddress("192.168.10.1"), sshCommandServiceTest, servicesDefinition);

        assertEquals ("NA", parser.getServiceStatus(Service.from("tada")));
        assertEquals ("running", parser.getServiceStatus(Service.from("elasticsearch")));
        assertEquals ("running", parser.getServiceStatus(Service.from("cerebro")));
        assertEquals ("dead", parser.getServiceStatus(Service.from("emergency")));
    }

    @Test
    public void testParseFileOther() throws Exception {

        loadTestConfig("systemctl-out-other.log");
        SystemStatusParser parser = new SystemStatusParser(Node.fromAddress("192.168.10.1"), sshCommandServiceTest, servicesDefinition);

        assertEquals ("NA", parser.getServiceStatus(Service.from("tada")));
        assertEquals ("running", parser.getServiceStatus(Service.from("elasticsearch")));
        assertEquals ("running", parser.getServiceStatus(Service.from("cerebro")));
        assertEquals ("Result: exit-code", parser.getServiceStatus(Service.from("kafka-manager")));
    }

    @Test
    public void testParseFileActual() throws Exception {

        loadTestConfig("systemctl-out-debnode2.log");
        SystemStatusParser parser = new SystemStatusParser(Node.fromAddress("192.168.10.11"), sshCommandServiceTest, servicesDefinition);

        assertEquals ("NA", parser.getServiceStatus(Service.from("tada")));
        assertEquals ("running", parser.getServiceStatus(Service.from("zookeeper")));
        assertEquals ("running", parser.getServiceStatus(Service.from("ntp")));
        assertEquals ("NA", parser.getServiceStatus(Service.from("kafka-manager")));
        assertEquals ("NA", parser.getServiceStatus(Service.from("elasticsearch")));
        assertEquals ("NA", parser.getServiceStatus(Service.from("cerebro")));
        assertEquals ("NA", parser.getServiceStatus(Service.from("kibama")));
    }

    private void loadTestConfig (String fileName) throws IOException, ProcessHelper.ProcessHelperException{
        InputStream scriptIs = Optional.ofNullable(ResourceUtils.getResourceAsStream("SystemStatusParserTest/" + fileName))
                .orElseThrow(() -> new ProcessHelper.ProcessHelperException("Impossible to load file " + fileName));

        String content = StreamUtils.getAsString(scriptIs, StandardCharsets.UTF_8);
        assertNotNull(content);
        assertTrue (StringUtils.isNotBlank(content));

        sshCommandServiceTest.reset();

        sshCommandServiceTest.setNodeResultBuilder((node, script) -> {
            if (script.startsWith("sudo systemctl status --no-pager --no-block -al")) {
                return content;
            }
            return "";
        });
    }
}

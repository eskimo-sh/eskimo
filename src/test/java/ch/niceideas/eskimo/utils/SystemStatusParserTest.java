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

package ch.niceideas.eskimo.utils;

import ch.niceideas.common.utils.ProcessHelper;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StringUtils;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.*;

public class SystemStatusParserTest {

    @Test
    public void testPattern() throws Exception {

        assertTrue (SystemStatusParser.pattern.matcher("Active: active (exited) since Fri 2019-05-31 13:55:26 UTC; 1 day 20h ago").matches());
        assertTrue (SystemStatusParser.pattern.matcher("Active: active (running) since Fri 2019-05-31 13:55:31 UTC; 1 day 20h ago").matches());
        assertTrue (SystemStatusParser.pattern.matcher("Active: failed (Result: exit-code) since Sun 2019-06-02 10:28:47 UTC; 5min ago").matches());
    }

    @Test
    public void testParseFileDeb() throws Exception {

        String debConfig = loadTestConfig("systemctl-out-debnode1.log");
        assertNotNull (debConfig);
        assertTrue (StringUtils.isNotBlank(debConfig));

        SystemStatusParser parser = new SystemStatusParser(debConfig);

        assertEquals ("NA", parser.getServiceStatus("tada"));
        assertEquals ("running", parser.getServiceStatus("elasticsearch"));
        assertEquals ("running", parser.getServiceStatus("mesos-agent"));
        assertEquals ("dead", parser.getServiceStatus("emergency"));
    }

    @Test
    public void testParseFileCent() throws Exception {

        String centConfig = loadTestConfig("systemctl-out-centnode1.log");
        assertNotNull (centConfig);
        assertTrue (StringUtils.isNotBlank(centConfig));

        SystemStatusParser parser = new SystemStatusParser(centConfig);

        assertEquals ("NA", parser.getServiceStatus("tada"));
        assertEquals ("running", parser.getServiceStatus("elasticsearch"));
        assertEquals ("running", parser.getServiceStatus("cerebro"));
        assertEquals ("dead", parser.getServiceStatus("emergency"));
    }

    @Test
    public void testParseFileOther() throws Exception {

        String centConfig = loadTestConfig("systemctl-out-other.log");
        assertNotNull (centConfig);
        assertTrue (StringUtils.isNotBlank(centConfig));

        SystemStatusParser parser = new SystemStatusParser(centConfig);

        assertEquals ("NA", parser.getServiceStatus("tada"));
        assertEquals ("running", parser.getServiceStatus("elasticsearch"));
        assertEquals ("running", parser.getServiceStatus("cerebro"));
        assertEquals ("Result: exit-code", parser.getServiceStatus("kafka-manager"));
    }

    @Test
    public void testParseFileActual() throws Exception {

        String debConfig = loadTestConfig("systemctl-out-debnode2.log");
        assertNotNull (debConfig);
        assertTrue (StringUtils.isNotBlank(debConfig));

        SystemStatusParser parser = new SystemStatusParser(debConfig);

        assertEquals ("NA", parser.getServiceStatus("tada"));
        assertEquals ("running", parser.getServiceStatus("zookeeper"));
        assertEquals ("running", parser.getServiceStatus("ntp"));
        assertEquals ("NA", parser.getServiceStatus("kafka-manager"));
        assertEquals ("NA", parser.getServiceStatus("elasticsearch"));
        assertEquals ("NA", parser.getServiceStatus("cerebro"));
        assertEquals ("NA", parser.getServiceStatus("kibama"));
    }

    private String loadTestConfig (String fileName) throws IOException, ProcessHelper.ProcessHelperException{
        InputStream scriptIs = ResourceUtils.getResourceAsStream("SystemStatusParserTest/" + fileName);
        if (scriptIs == null) {
            throw new ProcessHelper.ProcessHelperException("Impossible to load file " + fileName);
        }

        BufferedReader reader = new BufferedReader( new InputStreamReader(scriptIs, "UTF-8"));
        String line = null;
        StringBuilder sb = new StringBuilder();
        while ( (line = reader.readLine()) != null) {
            sb.append (line);
            sb.append ("\n");
        }
        reader.close();

        return sb.toString();
    }
}

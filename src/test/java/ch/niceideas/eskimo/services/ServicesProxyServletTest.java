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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.model.MemoryModel;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.Service;
import ch.niceideas.eskimo.model.Topology;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.Assert.*;

public class ServicesProxyServletTest {

    private ProxyManagerService pms;
    private ServicesDefinition sd;

    private ServicesProxyServlet servlet;

    @Before
    public void setUp() throws Exception {
        pms = new ProxyManagerService();
        sd = new ServicesDefinition();
        sd.afterPropertiesSet();
        servlet = new ServicesProxyServlet(pms, sd);
    }

    @Test
    public void testNominalReplacements() throws Exception {

        Service kafkaManagerService = sd.getService("kafka-manager");

        String toReplace  = "\n <a href='/toto.txt'>\na/a>";
        String result = servlet.performReplacements(kafkaManagerService, "", "test/test", toReplace );
        assertEquals("\n" +
                " <a href='/test/test/toto.txt'>\n" +
                "a/a>", result);
    }

    @Test
    public void testMesosSpecificReplacements() throws Exception {

        Service mesosMasterService = sd.getService("mesos-master");

        String toReplace  = "return '//' + leader_info.hostname + ':' + leader_info.port;";
        String result = servlet.performReplacements(mesosMasterService, "", "test/test", toReplace );
        assertEquals("return '/test/test';", result);

        toReplace = "    // time we are retrieving state), fallback to the current master.\n" +
                "    return '';";
        result = servlet.performReplacements(mesosMasterService, "controllers.js", "test/test", toReplace );
        assertEquals("    // time we are retrieving state), fallback to the current master.\n" +
                "    return '/test/test';", result);
    }
}

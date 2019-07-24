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

package ch.niceideas.eskimo.html;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EskimoSystemStatusTest extends AbstractWebTest {

    private String jsonStatus = null;
    private String jsonStatusConfig = null;

    @Before
    public void setUp() throws Exception {

        jsonStatus = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoNodesStatusTest/testStatus.json"));
        jsonStatusConfig = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoNodesStatusTest/testStatusConfig.json"));

        page.executeJavaScript("loadScript('../../src/main/webapp/scripts/jquery-3.3.1.js')");
        page.executeJavaScript("loadScript('../../src/main/webapp/scripts/eskimoSystemStatus.js')");

        page.executeJavaScript("$('#inner-content-nodes').html('" +
                "<div id=\"status-management\"" +
                "     class=\"panel theme-panel inner-content-inner \">" +
                "    <div id=\"nodeContainer-status\" style=\"position: relative;\">" +
                "        <div class=\"col-xs-12\">" +
                "            <div class=\"col-md-12\" id=\"status-message-title\">" +
                "                <h3>Eskimo Cluster Node Status</h3>" +
                "            </div>" +
                "        </div>" +
                "        <div class=\"col-xs-12 col-md-12\" id=\"status-node-container-carousel\" style=\"visibility: hidden;\">" +
                "            <div id=\"nodes-status-carousel-content\">" +
                "            </div>" +
                "            <div class=\"col-md-12 btn-toolbar\">" +
                "                <div class=\"btn-group\">" +
                "                    <button id=\"show-table-view-btn\" name=\"show-table-view-btn\" class=\"btn btn-primary\">" +
                "                        Show Table View" +
                "                    </button>" +
                "                </div>" +
                "            </div>" +
                "        </div>"+
                "        <div class=\"col-xs-12 col-md-12\" id=\"status-node-container\" style=\"overflow-x: auto;\">" +
                "            <div class=\"col-xs-12 col-md-12\" id=\"status-node-container-table\" style=\"visibility: hidden;\">" +
                "                <div class=\"table-responsive status-table\" >" +
                "                <table id=\"status-node-table\" class=\"table table-bordered table-hover\">" +
                "                <thead id=\"status-node-table-head\">" +
                "                </thead>" +
                "                <tbody id=\"status-node-table-body\">" +
                "                </tbody>" +
                "                </table>" +
                "            </div>" +
                "            <div class=\"col-md-12 btn-toolbar\">" +
                "            <div class=\"btn-group\">" +
                "                <button id=\"show-machine-view-btn\" name=\"show-machine-view-btn\" class=\"btn btn-primary\">" +
                "                Show Node View" +
                "                </button>" +
                "            </div>" +
                "            <br>" +
                "        </div>" +
                "    </div>" +
                "    <div class=\"col-xs-12 col-md-12\" id=\"service-status-warning\" style=\"visibility: hidden; display: none;\">" +
                "        <div class=\"alert alert-warning\" role=\"alert\" id=\"service-status-warning-message\">" +
                "        </div>" +
                "    </div>" +
                "</div>')");

        page.executeJavaScript("STATUS_SERVICES = [\"ntp\",\"zookeeper\",\"gluster\",\"gdash\",\"mesos-master\",\"mesos-agent\",\"kafka\",\"kafka-manager\",\"spark-history-server\",\"spark-executor\",\"logstash\",\"cerebro\",\"elasticsearch\",\"kibana\",\"zeppelin\"];");

        page.executeJavaScript("SERVICES_STATUS_CONFIG = " + jsonStatusConfig + ";");

        // redefine constructor
        page.executeJavaScript("eskimo.SystemStatus.initialize = function() {};");

        // instantiate test object
        page.executeJavaScript("eskimoNodesStatus = new eskimo.SystemStatus();");

        // set services for tests
        page.executeJavaScript("eskimoNodesStatus.setStatusServices (STATUS_SERVICES);");
        page.executeJavaScript("eskimoNodesStatus.setServicesStatusConfig (SERVICES_STATUS_CONFIG);");
    }

    @Test
    public void testRenderStatusFlat() throws Exception {

        page.executeJavaScript("eskimoNodesStatus.setRenderInTable(false);");

        page.executeJavaScript("eskimoNodesStatus.renderStatus(" + jsonStatus + ", false)");

        String flatString = page.executeJavaScript("$('#nodes-status-carousel-content').html()").getJavaScriptResult().toString();

        assertNotNull (flatString);

        assertTrue (flatString.contains("192.168.10.11"));
        assertTrue (flatString.contains("192.168.10.13"));
    }

    @Test
    public void testRenderStatusTable() throws Exception {

        page.executeJavaScript("eskimoNodesStatus.setRenderInTable(true);");

        page.executeJavaScript("eskimoNodesStatus.renderStatus(" + jsonStatus + ", false)");

        String tableString = page.executeJavaScript("$('#status-node-table-body').html()").getJavaScriptResult().toString();

        assertNotNull (tableString);

        assertTrue (tableString.contains("192.168.10.11"));
        assertTrue (tableString.contains("192.168.10.13"));

    }
}

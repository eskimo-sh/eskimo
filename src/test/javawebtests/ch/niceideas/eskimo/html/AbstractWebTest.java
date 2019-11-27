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
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import java.net.URL;

public abstract class AbstractWebTest {

    protected WebClient webClient;
    protected HtmlPage page;

    @Before
    public void init() throws Exception {
        webClient = new WebClient();
        URL testPage = ResourceUtils.getURL("classpath:GenericTestPage.html");
        page = webClient.getPage(testPage);
        Assert.assertEquals("Generic Test Page", page.getTitleText());

        // create common mocks
        // create mock functions
        page.executeJavaScript("var eskimoServices = {};");
        page.executeJavaScript("eskimoServices.serviceMenuServiceFoundHook = function (){};");
        page.executeJavaScript("eskimoServices.getServiceIcon = function (service) { return service + '-icon.png'; };");

        page.executeJavaScript("var eskimoConsoles = {}");
        page.executeJavaScript("eskimoConsoles.setAvailableNodes = function () {};");

        page.executeJavaScript("var eskimoMessaging = {}");
        page.executeJavaScript("eskimoMessaging.isOperationInProgress = function() { return false; };");
        page.executeJavaScript("eskimoMessaging.setOperationInProgress = function() {};");

        page.executeJavaScript("var eskimoFileManagers = {};");
        page.executeJavaScript("eskimoFileManagers.setAvailableNodes = function() {};");

        page.executeJavaScript("var nodesConfig = {};");
        page.executeJavaScript("nodesConfig.getServiceLogoPath = function (serviceName){ return serviceName + '-logo.png'; };");
        page.executeJavaScript("nodesConfig.getServiceIconPath = function (serviceName){ return serviceName + '-icon.png'; };");
        page.executeJavaScript("nodesConfig.isServiceUnique = function (serviceName){ " +
                "return (serviceName == 'mesos-master' " +
                "    || serviceName == 'zookeeper' " +
                "    || serviceName == 'grafana' " +
                "    || serviceName == 'gdash' " +
                "    || serviceName == 'kafka-manager' " +
                "    || serviceName == 'spark-history-server' " +
                "    || serviceName == 'flink-app-master' " +
                "    || serviceName == 'cerebro' " +
                "    || serviceName == 'kibana' " +
                "    || serviceName == 'zeppelin' ); " +
                "};");


        page.executeJavaScript("var eskimoMain = {};");
        page.executeJavaScript("eskimoMain.handleSetupCompleted = function (){};");
        page.executeJavaScript("eskimoMain.getServices = function (){ return eskimoServices; };");
        page.executeJavaScript("eskimoMain.getMessaging = function (){ return eskimoMessaging; };");
        page.executeJavaScript("eskimoMain.getFileManagers = function (){ return eskimoFileManagers; };");
        page.executeJavaScript("eskimoMain.getConsoles = function (){ return eskimoConsoles; };");
        page.executeJavaScript("eskimoMain.getNodesConfig = function () { return nodesConfig; };");
        page.executeJavaScript("eskimoMain.isOperationInProgress = function() { return false; };");
        page.executeJavaScript("eskimoMain.setAvailableNodes = function () {};");
        page.executeJavaScript("eskimoMain.menuResize = function () {};");

        page.executeJavaScript("loadScript('../../src/main/webapp/scripts/jquery-3.3.1.js')");

        // override jquery load
        page.executeJavaScript("$.fn._internalLoad = $.fn.load;");
        page.executeJavaScript("$.fn.load = function (resource, callback) { return this._internalLoad ('../../src/main/webapp/'+resource, callback); };");

    }

    @After
    public void close() throws Exception {
        webClient.close();
    }

    protected void waitForElementIdinDOM(String elementId) throws InterruptedException {
        int attempt = 0;
        while (page.getElementById(elementId) == null && attempt < 10) {
            Thread.sleep(500);
            attempt++;
        }
    }
}

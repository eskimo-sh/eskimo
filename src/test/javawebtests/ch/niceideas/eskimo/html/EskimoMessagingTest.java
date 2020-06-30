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

import org.junit.Before;
import org.junit.Test;

public class EskimoMessagingTest extends AbstractWebTest {

    @Before
    public void init() throws Exception {
        super.init();

        loadScript(page, "eskimoMessaging.js");

        js("function errorHandler() {};");

        // instantiate test object
        js("eskimoMessaging = new eskimo.Messaging()");
        js("eskimoMessaging.fetchLastMessages = function(callback) {" +
                "    if (callback != null) {callback(); }" +
                "};");

        waitForElementIdInDOM("progress-bar-pending-wrapper");
    }

    @Test
    public void testStartStopOperationInprogress() throws Exception {

        js("eskimoMessaging.startOperationInProgress();");

        assertCssValue("#progress-bar-pending-wrapper", "visibility", "inherit");
        assertCssValue("#progress-bar-pending-wrapper", "display", "block");

        assertJavascriptEquals("<h3>Processing pending on Eskimo backend ....</h3>", "$('#pending-message-title').html()");

        js("eskimoMessaging.stopOperationInProgress(false);");

        assertCssValue("#progress-bar-pending-wrapper", "visibility", "hidden");
        assertCssValue("#progress-bar-pending-wrapper", "display", "none");

        assertJavascriptEquals("<h3><span class=\"processing-error\">Processing completed in error !</span></h3>",
                "$('#pending-message-title').html()");


        js("eskimoMessaging.startOperationInProgress();");

        assertCssValue("#progress-bar-pending-wrapper", "visibility", "inherit");
        assertCssValue("#progress-bar-pending-wrapper", "display", "block");

        assertJavascriptEquals("<h3>Processing pending on Eskimo backend ....</h3>", "$('#pending-message-title').html()");

        js("eskimoMessaging.stopOperationInProgress(true);");

        assertCssValue("#progress-bar-pending-wrapper", "visibility", "hidden");
        assertCssValue("#progress-bar-pending-wrapper", "display", "none");

        assertJavascriptEquals("<h3>Processing completed successfully.</h3>",
                "$('#pending-message-title').html()");

    }

}


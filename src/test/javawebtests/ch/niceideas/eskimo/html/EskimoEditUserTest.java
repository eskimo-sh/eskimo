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

package ch.niceideas.eskimo.html;

import ch.niceideas.eskimo.utils.ActiveWaiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class EskimoEditUserTest extends AbstractWebTest {

    @BeforeEach
    public void setUp() throws Exception {

        loadScript(findVendorLib ("bootstrap"));

        loadScript("eskimoUtils.js");
        loadScript("eskimoEditUser.js");

        waitForDefinition("window.eskimo");
        waitForDefinition("window.eskimo.EditUser");

        js("eskimoEditUser = new eskimo.EditUser();");
        js("eskimoEditUser.initialize()");

        js("eskimoEditUser.eskimoMain = {" +
                "    getUserId: function() { return 'testUser'; }," +
                "    alert: function (level, message) { window.lastAlert = level + ' : ' + message; }" +
                "}");

        waitForElementIdInDOM("edit-user-input-button-validate");

        assertCssEquals("block", "#edit-user-modal", "display");
    }

    @Test
    public void testNominal() {

        js("eskimoEditUser.showEditUser()");

        ActiveWaiter.wait(() -> {
            Object display = js("return $('#edit-user-modal').css('display')");
            return display != null && display.equals("block");
        });
        assertCssEquals("block", "#edit-user-modal", "display");

        assertJavascriptEquals("testUser", "$('#edit-user-user-id').val()");

        assertJavascriptEquals("", "$('#edit-user-current-password').val()");
        assertJavascriptEquals("", "$('#edit-user-new-password').val()");
        assertJavascriptEquals("", "$('#edit-user-repeat-password').val()");

        js("eskimoEditUser.cancelEditUser()");

        ActiveWaiter.wait(() -> {
            Object display = js("return $('#edit-user-modal').css('display')");
            return display != null && display.equals("none");
        });
        assertCssEquals("none", "#edit-user-modal", "display");
    }

    @Test
    public void testValidate() {

        js("eskimoEditUser.showEditUser()");

        ActiveWaiter.wait(() -> {
            Object display = js("return $('#edit-user-modal').css('display')");
            return display != null && display.equals("block");
        });
        assertCssEquals("block", "#edit-user-modal", "display");

        js("$('#edit-user-current-password').val('testOld')");
        js("$('#edit-user-new-password').val('testNew')");
        js("$('#edit-user-repeat-password').val('testWrong')");

        js("eskimoEditUser.validateEditUser()");

        assertJavascriptEquals("3 : both passwords don't match!", "window.lastAlert");

        js("window.lastAlert = null;");

        js("$('#edit-user-repeat-password').val('testNew')");

        js("$.ajaxPost = function (dataObj) {" +
                "    dataObj.success({" +
                "        'message' : 'OK'" +
                "    });" +
                "}");

        js("eskimoEditUser.validateEditUser()");

        assertJavascriptEquals("1 : OK", "window.lastAlert");

        ActiveWaiter.wait(() -> {
            Object display = js("return $('#edit-user-modal').css('display')");
            return display != null && display.equals("none");
        });
        assertCssEquals("none", "#edit-user-modal", "display");
    }
}


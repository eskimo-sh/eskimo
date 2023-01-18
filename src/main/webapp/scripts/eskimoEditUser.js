/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
Author : eskimo.sh / https://www.eskimo.sh

Eskimo is available under a dual licensing model : commercial and GNU AGPL.
If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
any later version.
Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
commercial license.

Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Affero Public License for more details.

You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
Boston, MA, 02110-1301 USA.

You can be released from the requirements of the license by purchasing a commercial license. Buying such a
commercial license is mandatory as soon as :
- you develop activities involving Eskimo without disclosing the source code of your own product, software, 
  platform, use cases or scripts.
- you deploy eskimo as part of a commercial product, platform or software.
For more information, please contact eskimo.sh at https://www.eskimo.sh

The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
Software.
*/

if (typeof eskimo === "undefined" || eskimo == null) {
    window.eskimo = {}
}
eskimo.EditUser = function() {

    this.eskimoMain = null;

    const that = this;

    this.initialize = function() {
        // Initialize HTML Div from Template
        $("#edit-user-modal-wrapper").load("html/eskimoEditUser.html", (responseTxt, statusTxt, jqXHR) => {

            if (statusTxt === "success") {

                $("#edit-user-link").click(() => { showEditUser(); });

                $("#edit-user-header-close").click(() => { cancelEditUser(); });

                $("#edit-user-input-button-cancel").click(() => {cancelEditUser(); });

                $("#edit-user-input-button-validate").click(() => { validateEditUser();  });

            } else if (statusTxt === "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });
    };

    function showEditUser () {
        $("#edit-user-user-id").val(that.eskimoMain.getUserId());
        $("#edit-user-current-password").val("");
        $("#edit-user-new-password").val("");
        $("#edit-user-repeat-password").val("");
        $('#edit-user-modal').modal("show");
    }
    this.showEditUser = showEditUser;

    function cancelEditUser() {
        $('#edit-user-modal').modal("hide");
    }
    this.cancelEditUser = cancelEditUser;

    function validateEditUser() {

        let newUser = $("form#edit-user-form").serializeObject();
        let userJSON =  JSON.stringify(newUser);

        if (newUser["edit-user-new-password"] !== newUser["edit-user-repeat-password"]) {
            eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, "both passwords don't match!");
            return;
        }

        $('#edit-user-input-button-validate').prop('disabled', true);
        $('#edit-user-overlay').css('display', 'block');

        $.ajaxPost({
            timeout: 1000 * 10,
            url: "edit-user",
            data: userJSON,
            success: (data, status, jqXHR) => {

                if (!data || data.error) {
                    console.error(data.error);

                    $('#edit-user-modal').modal("hide");
                    eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error, () => {
                        $('#edit-user-modal').modal("show");
                    });

                } else {

                    eskimoMain.alert(ESKIMO_ALERT_LEVEL.INFO, data.message);
                    $('#edit-user-modal').modal("hide");
                }

                $('#edit-user-input-button-validate').prop('disabled', false);
                $('#edit-user-overlay').css('display', 'none');
            },

            error: (jqXHR, status) => {
                $('#edit-user-input-button-validate').prop('disabled', false);
                $('#edit-user-overlay').css('display', 'none');
                errorHandler (jqXHR, status);
            }
        });
    }
    this.validateEditUser = validateEditUser;
};
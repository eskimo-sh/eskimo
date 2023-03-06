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
eskimo.Alert = function() {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;
    this.eskimoOperations = null;
    this.eskimoMenu = null;

    const that = this;

    let currentLevel = 0;
    let currentMessage = null;

    let callback = null;
    let confirmCallback = null;

    this.initialize = function() {
        // Initialize HTML Div from Template
        $("#alert-modal-wrapper").load("html/eskimoAlert.html", (responseTxt, statusTxt, jqXHR) => {

            if (statusTxt === "success") {

                $('#alert-header-cancel').click(closeAlert);
                $("#alert-button-cancel").click(closeAlert);
                $('#alert-button-validate').click(validateAlert);

                // this is done is EskimoAlert to be sure it's done last in the init process
                if (isFunction (that.eskimoMenu.enforceMenuConsisteny)) {
                    that.eskimoMenu.enforceMenuConsisteny();
                }

            } else if (statusTxt === "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });
    };

    function adaptLevel (level) {
        currentLevel = level;
        if (level >= that.level.CONFIRM) {
            $("#alert-header").attr("class", "modal-header bg-info text-white");
        } else if (level === that.level.ERROR) {
            $("#alert-header").attr("class", "modal-header bg-danger text-white");
        } else if (level === that.level.WARNING) {
            $("#alert-header").attr("class", "modal-header bg-warning text-white");
        } else {
            $("#alert-header").attr("class", "modal-header");
        }
    }

    function confirm (message, confirmCallbackParam, closeCallback) {

        confirmCallback = confirmCallbackParam;
        callback = closeCallback;

        const level = this.level.CONFIRM;

        if (level > currentLevel) {
            adaptLevel (level);
        }

        currentMessage = message;

        $("#alert-body").html (currentMessage);

        $("#alert-button-cancel").css("display", "inherit");
        $("#alert-title").html("Comfirmation");
        $("#alert-button-validate").html("Validate");

        $('#alert-modal').modal("show");
    }
    this.confirm = confirm;

    function showAlert (level, message, closeCallback) {

        callback = closeCallback;

        if (!Number.isInteger(level) || level < 1 || level > 3) {
            alert("eskimoAlert : expected level for alert as an integer between 1 and 3. Use window.ESKIMO_ALERT_LEVEL.[ERROR, WARNING, INFO]");
        } else {

            if (level > currentLevel) {
                adaptLevel (level);
            }

            if (currentMessage != null) {
                currentMessage = currentMessage + "<br>" + message;
            } else {
                currentMessage = message;
            }
            $("#alert-body").html (currentMessage);

            $("#alert-button-cancel").css("display", "none");
            $("#alert-title").html("Message");
            $("#alert-button-validate").html("Close");

            $('#alert-modal').modal("show");
        }
    }
    this.showAlert = showAlert;

    function validateAlert() {

        if (isFunction (confirmCallback)) {
            confirmCallback();
        }

        closeAlert();
    }
    this.validateAlert = validateAlert;

    function closeAlert() {

        currentLevel = 0;
        currentMessage = null;

        $('#alert-modal').modal("hide");

        if (isFunction (callback)) {
            callback();
        }
    }
    this.closeAlert = closeAlert;

    this.level = {
        CONFIRM: 4,
        ERROR: 3,
        WARNING: 2,
        INFO: 1
    }
    window.ESKIMO_ALERT_LEVEL = this.level;
};

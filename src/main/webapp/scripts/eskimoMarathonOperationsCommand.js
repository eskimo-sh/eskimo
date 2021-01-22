/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
eskimo.MarathonOperationsCommand = function() {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;
    this.eskimoMessaging = null;

    const that = this;

    this.initialize = function() {
        // Initialize HTML Div from Template
        $("#marathon-operations-command-modal-wrapper").load("html/eskimoMarathonOperationsCommand.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt == "success") {

                $('#marathon-operations-command-header-cancel').click(cancelMarathonOperationsCommand);
                $('#marathon-operations-command-button-cancel').click(cancelMarathonOperationsCommand);
                $('#marathon-operations-command-button-validate').click(validateMarathonOperationsCommand);

            } else if (statusTxt == "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });
    };

    function renderOperation(operation, commandDescription) {

        commandDescription = commandDescription +
            '<i class="fa fa-arrow-right"></i>&nbsp;' + operation + "<br>";

        return commandDescription;
    }

    function showCommand (command) {

        console.log (command);

        let commandDescription = "<strong>Following Operations are about to be applied</strong><br><br>";

        // installations
        commandDescription += "<strong>Marathon Services Installation</strong><br><br>";

        for (let i = 0; i < command.installations.length; i++) {
            commandDescription = renderOperation(command.installations[i], commandDescription);
        }

        commandDescription += "<br>";

        // uninstallations
        commandDescription += "<strong>Marathon Services Uninstallation</strong><br><br>";

        for (let i = 0; i < command.uninstallations.length; i++) {
            commandDescription = renderOperation(command.uninstallations[i], commandDescription);
        }

        commandDescription += "<br>";

        /*
        // reinstallations
        commandDescription += "<b>Services Restart</b><br><br>";

        for (let i = 0; i < command.restarts.length; i++) {
            commandDescription = renderOperation(command.restarts[i], commandDescription);
        }

        commandDescription += "<br>";
        */

        if (command.warnings && command.warnings.trim() != "") {

            commandDescription = commandDescription +
                '<div class="col-xs-12 col-md-12">' +
                '<div class="alert alert-warning" role="alert">' +
                command.warnings +
                '</div>' +
                '</div>';

        }

        $("#marathon-operations-command-body").html(commandDescription);

        $('#marathon-operations-command-modal').modal("show");

        // re-enable button
        $('#marathon-operations-command-button-validate').prop('disabled', false);
    }
    this.showCommand = showCommand;

    function validateMarathonOperationsCommand() {

        if (that.eskimoMain.isOperationInProgress()) {
            alert ("There is already some operations in progress on backend. Skipping.");

        } else {

            // first thing : disable button to prevent double submit
            $('#marathon-operations-command-button-validate').prop('disabled', true);

            that.eskimoMessaging.showMessages();

            that.eskimoMain.startOperationInProgress();

            // 1 hour timeout
            $.ajax({
                type: "POST",
                dataType: "json",
                timeout: 1000 * 3600,
                contentType: "application/json; charset=utf-8",
                url: "apply-marathon-services-config",
                success: function (data, status, jqXHR) {

                    // OK
                    console.log(data);

                    if (!data || data.error) {
                        console.error(atob(data.error));
                        that.eskimoMain.scheduleStopOperationInProgress(false);
                    } else {
                        that.eskimoMain.scheduleStopOperationInProgress(true);
                    }
                },

                error: function (jqXHR, status) {
                    errorHandler(jqXHR, status);
                    that.eskimoMain.scheduleStopOperationInProgress(false);
                }
            });
        }

        $('#marathon-operations-command-modal').modal("hide");
    }
    this.validateMarathonOperationsCommand = validateMarathonOperationsCommand;

    function cancelMarathonOperationsCommand() {
        $('#marathon-operations-command-modal').modal("hide");
    }
    this.cancelMarathonOperationsCommand = cancelMarathonOperationsCommand;
};
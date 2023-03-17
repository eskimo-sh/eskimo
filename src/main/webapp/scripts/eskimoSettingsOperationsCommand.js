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
eskimo.SettingsOperationsCommand = function() {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;
    this.eskimoOperations = null;
    this.eskimoServicesSettings = null;

    const that = this;

    this.initialize = function() {
        // Initialize HTML Div from Template
        $("#settings-operations-command-modal-wrapper").load("html/eskimoSettingsOperationsCommand.html", (responseTxt, statusTxt, jqXHR) => {

            if (statusTxt === "success") {

                $('#settings-operations-command-header-cancel').click(cancelSettingsOperationsCommand);
                $('#settings-operations-command-button-cancel').click(cancelSettingsOperationsCommand);
                $('#settings-operations-command-button-validate').click(validateSettingsOperationsCommand);

            } else if (statusTxt === "error") {
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
        commandDescription += "<strong>Settings Changes</strong><br><br>";

        for (let service in command.settings) {

            let serviceSetting = command.settings[service];

            for (let file in serviceSetting) {

                let fileSettings = serviceSetting[file];

                commandDescription = commandDescription +
                    '<strong>' + service+ ' / ' + file + "</strong><br>";

                for (let i = 0; i < fileSettings.length; i++) {
                    commandDescription = renderOperation(
                        "Changing " + fileSettings[i].key
                            + (fileSettings[i].oldValue != null && fileSettings[i].oldValue != "" ? (" from " + fileSettings[i].oldValue) : "")
                            + (fileSettings[i].value != null && fileSettings[i].value != "" ? (" to " + fileSettings[i].value) : "back to default")
                        , commandDescription);
                }
            }

        }

        commandDescription += "<br>";

        // restarts
        commandDescription += "<strong>Services to be restarted</strong><br><br>";

        for (let i = 0; i < command.restarts.length; i++) {
            commandDescription = renderOperation(command.restarts[i], commandDescription);
        }

        commandDescription += "<br>";

        if (command.warnings && command.warnings.trim() !== "") {

            commandDescription = commandDescription +
                '<div class="col-xs-12 col-md-12">' +
                '<div class="alert alert-warning bg-warning text-white border-0" role="alert">' +
                command.warnings +
                '</div>' +
                '</div>';

        }

        $("#settings-operations-command-body").html(commandDescription);

        $('#settings-operations-command-modal').modal("show");

        // re-enable button
        $('#settings-operations-command-button-validate').prop('disabled', false);
    }
    this.showCommand = showCommand;

    function validateSettingsOperationsCommand() {

        if (that.eskimoMain.isOperationInProgress()) {
            eskimoMain.alert(ESKIMO_ALERT_LEVEL.WARNING, "There is already some operations in progress on backend. Skipping.");

        } else {

            // first thing : disable button to prevent double submit
            $('#settings-operations-command-button-validate').prop('disabled', true);

            that.eskimoOperations.showOperations();

            that.eskimoMain.startOperationInProgress();

            // 1 hour timeout
            $.ajaxPost({
                timeout: 1000 * 3600,
                url: "apply-services-settings",
                success: (data, status, jqXHR) => {

                    // OK
                    console.log(data);

                    if (data && data.status) {
                        if (data.status == "KO") {
                            that.eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, atob(data.error));
                        }
                    } else {
                        that.eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, "No status received back from backend.");
                    }

                    if (!data || data.error) {
                        that.eskimoMain.scheduleStopOperationInProgress(false);
                        console.error(atob(data.error));
                    } else {
                        that.eskimoMain.scheduleStopOperationInProgress(true);
                    }
                },

                error: (jqXHR, status) => {
                    that.eskimoMain.scheduleStopOperationInProgress(false);
                    errorHandler(jqXHR, status);
                }
            });
        }

        $('#settings-operations-command-modal').modal("hide");
    }
    this.validateSettingsOperationsCommand = validateSettingsOperationsCommand;

    function cancelSettingsOperationsCommand() {
        $('#settings-operations-command-modal').modal("hide");
    }
    this.cancelSettingsOperationsCommand = cancelSettingsOperationsCommand;
};
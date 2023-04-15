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
eskimo.SetupCommand = function() {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;
    this.eskimoOperations = null;
    this.eskimoSetup = null;

    const that = this;

    this.initialize = function() {
        // Initialize HTML Div from Template
        $("#setup-command-modal-wrapper").load("html/eskimoSetupCommand.html", (responseTxt, statusTxt, jqXHR) => {

            if (statusTxt === "success") {

                $("#setup-command-header-close").click(cancelSetupCommand);
                $("#setup-command-button-close").click(cancelSetupCommand);
                $("#setup-command-button-validate").click(validateSetupCommand);

            } else if (statusTxt === "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });
    };


    function showCommand (command) {

        console.log (command);

        let commandDescription = "<strong>Following Operations are about to be applied</strong>";

        // Build of packages
        if (command.buildPackage != null && command.buildPackage.length > 0) {

            commandDescription += '<br><br><strong>Packages are about to be built.</strong>' +
                '<br>'+
                'Building packages happens in the folder "packages_dev" under the root folder of your eskimo installation<br>'+
                'Packages are built using shell script and docker.<br>'+
                'Building packages can take several dozen of minutes<br>'+
                '<strong>List of packages to be built</strong><br>';


            for (let i = 0; i < command.buildPackage.length; i++) {
                commandDescription += '<strong><i class="fa fa-arrow-right"></i>&nbsp;' + command.buildPackage[i] + '</strong><br>';
            }
        }

        // Download of packages
        if (command.downloadPackages != null && command.downloadPackages.length > 0) {

            commandDescription += '<br><br><strong>Packages are about to be downloaded from '+command.packageDownloadUrl+'.</strong>' +
                '<br>'+
                'Downloading of packages can take several dozen of minutes depending on your internet connection<br>'+
                '<strong>List of packages to be downloaded</strong><br>';


            for (let i = 0; i < command.downloadPackages.length; i++) {
                commandDescription += '<strong><i class="fa fa-arrow-right"></i>&nbsp;' + command.downloadPackages[i] + '</strong><br>';
            }
        }

        // Build of kube
        if (command.buildKube != null && command.buildKube.length > 0) {

            commandDescription += '<br><br><strong>Kube Packages are about to be built.</strong>' +
                '<br>'+
                'Building kube happens in the folder "packages_dev" under the root folder of your eskimo installation<br>'+
                'Kube Packages are built using shell script and either vagrant or libvirt.<br>'+
                '<strong>Building packages can take several hours.</strong><br>'+
                '<strong>List of Kube packages to be built</strong><br>';


            for (let i = 0; i < command.buildKube.length; i++) {
                commandDescription += '<strong><i class="fa fa-arrow-right"></i>&nbsp;' + command.buildKube[i] + '</strong><br>';
            }
        }

        // Download of kube
        if (command.downloadKube != null && command.downloadKube.length > 0) {

            commandDescription += '<br><br><strong>Kube Packages are about to be downloaded from '+command.packageDownloadUrl+'.</strong>' +
                '<br>'+
                'Downloading of kube packages can take several dozen of minutes depending on your internet connection<br>'+
                '<strong>List of Kube packages to be downloaded</strong><br>';


            for (let i = 0; i < command.downloadKube.length; i++) {
                commandDescription += '<strong><i class="fa fa-arrow-right"></i>&nbsp;' + command.downloadKube[i] + '</strong><br>';
            }
        }

        // Package Updates
        if (command.packageUpdates != null && command.packageUpdates.length > 0) {

            commandDescription += '<br><br><strong>Following packages updates are available:</strong>' +
                '<br>';


            for (let i = 0; i < command.packageUpdates.length; i++) {
                commandDescription += '<strong><i class="fa fa-arrow-right"></i>&nbsp;' + command.packageUpdates[i] + '</strong><br>';
            }
        }

        $("#setup-command-body").html(commandDescription);

        $('#setup-command-modal').modal("show");

        // re-enable button
        $('#setup-command-button-validate').prop('disabled', false);
    }
    this.showCommand = showCommand;

    function validateSetupCommand() {

        if (that.eskimoMain.isOperationInProgress()) {
            eskimoMain.alert(ESKIMO_ALERT_LEVEL.WARNING, "There is already some operations in progress on backend. Skipping.");

        } else {

            // first thing : disable button to prevent double submit
            $('#setup-command-button-validate').prop('disabled', true);

            that.eskimoOperations.showOperations();

            that.eskimoMain.startOperationInProgress();

            // 5 hours timeout
            $.ajaxPost({
                timeout: 1000 * 3600 * 5,
                url: "apply-setup",
                success: (data, status, jqXHR) => {

                    console.log(data);
                    if (data && data.status) {
                        if (data.status == "KO") {
                            that.eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error);
                        } else {
                            that.eskimoMain.handleSetupCompleted();
                        }
                    } else {
                        that.eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, "No status received back from backend.");
                    }

                    if (data.error) {
                        that.eskimoMain.scheduleStopOperationInProgress(false);
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

        $('#setup-command-modal').modal("hide");
    }
    this.validateSetupCommand = validateSetupCommand;

    function cancelSetupCommand() {
        $('#setup-command-modal').modal("hide");
    }
    this.cancelSetupCommand = cancelSetupCommand;

};
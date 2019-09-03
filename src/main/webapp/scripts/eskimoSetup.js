/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
    eskimo = {}
}
eskimo.Setup = function() {

    var that = this;

    // constants
    var MESSAGES_POLLING_STOP_DELAY = 10000;

    // Initialize HTML Div from Template
    this.initialize = function() {
        $("#inner-content-setup").load("html/eskimoSetup.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt == "success") {

                $('#btn-file-ssh-key').on('fileselect', function (event, numFiles, label) {

                    var input = $(this).parents('.input-group').find(':text'),
                        content = numFiles > 1 ? numFiles + ' files selected' : label;

                    if (input.length) {
                        input.val(content);
                    } else {
                        if (content) alert(content);
                    }

                    // file is content

                });

                $("#save-setup-btn").click(function (e) {
                    saveSetup();

                    e.preventDefault();
                    return false;
                });

                $(document).on('change', '#btn-file-ssh-key', function () {
                    var input = $(this),
                        numFiles = input.get(0).files ? input.get(0).files.length : 1,
                        label = input.val().replace(/\\/g, '/').replace(/.*\//, '');
                    input.trigger('fileselect', [numFiles, label]);

                    var files = $(this).get(0).files; // FileList object

                    var reader = new FileReader();


                    reader.onload = function () {
                        $("#content-ssh-key").val(reader.result);
                    };

                    // Read in the image file as a data URL.
                    reader.readAsText($(this).get(0).files[0]);
                });


            } else if (statusTxt == "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });
    };

    function loadSetup(initializationTime) {
        $.ajax({
            type: "GET",
            dataType: "json",
            url: "load-setup",
            success: function (data, status, jqXHR) {

                eskimoMain.setSetupLoaded();

                console.log (data);

                if (data.setup_storage != null) {
                    $("#setup_storage").val(data.setup_storage);
                }

                if (data.ssh_username != null) {
                    $("#ssh_username").val(data.ssh_username);
                }

                if (data['filename-ssh-key'] != null) {
                    $("#filename-ssh-key").val(data['filename-ssh-key']);
                }

                if (data['content-ssh-key'] != null) {
                    $("#content-ssh-key").val(data['content-ssh-key']);
                }

                if (data['setup-mesos-origin'] == "build") {
                    $('#setup-mesos-origin-build').get(0).checked = true;
                } else {
                    $('#setup-mesos-origin-download').get(0).checked = true; // default
                }

                if (data['setup-services-origin'] == "download") {
                    $('#setup-services-origin-download').get(0).checked = true;
                } else {
                    $('#setup-services-origin-build').get(0).checked = true; // default
                }

                if (!data.clear || data.clear == "services") {

                    eskimoMain.handleSetupCompleted();

                    if (initializationTime) {
                        eskimoMain.getSystemStatus().showStatus(true);
                    }

                } else {

                    eskimoMain.handleSetupNotCompleted();

                    if (initializationTime) { // only at initialization time
                        eskimoMain.showOnlyContent("setup");
                        eskimoMain.getSystemStatus().updateStatus(false);
                    }

                    if (data.message && data.message != null) {
                        showSetupMessage(data.message, true);
                    }
                }

                if (data.processingPending) {  // if backend says there is some provessing going on
                    eskimoMain.recoverOperationInProgress();
                }
            },
            error: errorHandler
        });
    }
    this.loadSetup = loadSetup;

    function showSetup () {

        if (eskimoMain.isOperationInProgress()) {
            eskimoMain.showProgressbar();
        }

        loadSetup();

        eskimoMain.showOnlyContent("setup");
    }
    this.showSetup = showSetup;

    function showSetupMessage(message, success) {
        var setupWarning = $("#setup-warning");
        setupWarning.css("display", "inherit");
        setupWarning.css("visibility", "inherit");

        var setupWarningMessage = $("#setup-warning-message");
        setupWarningMessage.attr("class", "alert alert-" + (success ? "info" : "danger"));
        setupWarningMessage.html(message);

        setTimeout(function() {
            setupWarning.css("display", "none");
            setupWarning.css("visibility", "hidden");
        }, 5000);
    }
    this.showSetupMessage = showSetupMessage;

    function saveSetup () {

        // coherence checks
        var setupStorage = $("#setup_storage");
        if (setupStorage.val() == null || setupStorage.val() == "") {
            showSetupMessage("Configuration Storage path should be input", false);
            return false;
        }

        var sshUserName = $("#ssh_username");
        if (sshUserName.val() == null || sshUserName.val() == "") {
            showSetupMessage("SSH Username to use to reach cluster nodes should be input", false);
            return false;
        }

        var contentSShKey = $("#content-ssh-key");
        if (contentSShKey.val() == null || contentSShKey.val() == "") {
            showSetupMessage("SSH Identity Private Key to use to reach cluster nodes should be input", false);
            return false;
        }

        eskimoMain.getMessaging().showMessages();

        eskimoMain.startOperationInProgress();

        var setupConfig = $("form#setup-config").serializeObject();

        $.ajax({
            type: "POST",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            timeout: 1000 * 7200,
            url: "apply-setup",
            data: JSON.stringify(setupConfig),
            success: function (data, status, jqXHR) {

                // OK
                console.log(data);
                if (data && data.status) {
                    if (data.status == "KO") {
                        showSetupMessage(data.error, false);
                    } else {
                        showSetupMessage("Configuration applied successfully", true);
                        eskimoMain.handleSetupCompleted();
                    }
                } else {
                    showSetupMessage("No status received back from backend.", false);
                }

                if (data.error) {
                    eskimoMain.scheduleStopOperationInProgress (false);
                } else {
                    eskimoMain.scheduleStopOperationInProgress (true);
                }
            },

            error: function (jqXHR, status) {
                // error handler
                console.log(jqXHR);
                console.log(status);
                showSetupMessage('fail : ' + status, false);

                eskimoMain.scheduleStopOperationInProgress (false);
                eskimoMain.hideProgressbar();
            }
        });
    }


    // call constructor
    this.initialize();
};

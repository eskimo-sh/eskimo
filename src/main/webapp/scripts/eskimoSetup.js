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
eskimo.Setup = function() {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;
    this.eskimoSystemStatus = null;
    this.eskimoSetupCommand = null;

    const that = this;

    // Initialize HTML Div from Template
    this.initialize = function() {
        $("#inner-content-setup").load("html/eskimoSetup.html", (responseTxt, statusTxt, jqXHR) => {

            if (statusTxt === "success") {

                $('#btn-file-ssh-key').on('fileselect', function (event, numFiles, label) {

                    let input = $(this).parents('.input-group').find(':text'),
                        content = numFiles > 1 ? numFiles + ' files selected' : label;

                    if (input.length) {
                        input.val(content);
                    } else {
                        if (content) console.log(content);
                    }

                    // file is content

                });

                $("#save-setup-btn").click(e => {
                    saveSetup();

                    e.preventDefault();
                    return false;
                });

                $("#reset-setup-btn").click(e => {
                    showSetup();

                    e.preventDefault();
                    return false;
                });

                $(document).on('change', '#btn-file-ssh-key', function () {
                    let input = $(this),
                        numFiles = input.get(0).files ? input.get(0).files.length : 1,
                        label = input.val().replace(/\\/g, '/').replace(/.*\//, '');
                    input.trigger('fileselect', [numFiles, label]);

                    let files = $(this).get(0).files; // FileList object

                    let reader = new FileReader();

                    reader.onload = () => { $("#content-ssh-key").val(reader.result); };

                    // Read in the image file as a data URL.
                    reader.readAsText(files[0]);
                });


            } else if (statusTxt === "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });
    };

    function switchDownloadBuild(target, data) {
        const $setupOriginBuild = $('#setup-'+target+'-origin-build');
        const $setupOriginDownload = $('#setup-'+target+'-origin-download');
        const $setupOriginDownloadLabel = $('#setup-'+target+'-origin-download-label');
        const $downloadExplainDisabled = $('#download-'+target+'-explain-disabled');
        if (    !$setupOriginBuild.length
             || !$setupOriginDownload.length) {
            console.log ("Couldn't find " + target + " in setup");
            return;
        }
        if (data.isSnapshot) {
            $setupOriginBuild.get(0).checked = true;
            $setupOriginDownloadLabel.addClass("disabled");
            $downloadExplainDisabled.css('display', 'block');
        } else {
            $setupOriginDownloadLabel.removeClass("disabled");
            $downloadExplainDisabled.css('display', 'none');
            if (data['setup-'+target+'-origin'] == "build") {
                $setupOriginBuild.get(0).checked = true;
            } else {
                $setupOriginDownload.get(0).checked = true; // default
            }
        }
    }

    function handleSetup(data, initializationTime) {
        that.eskimoMain.setSetupLoaded();

        //console.log(data);

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

        if (data.version == null) {
            eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, "Couldn't get Eskimo version");
        }

        switchDownloadBuild ("kube", data);

        switchDownloadBuild ("services", data);

        if (!data.clear || data.clear == "services") {

            that.eskimoMain.handleSetupCompleted();

            if (initializationTime) {
                that.eskimoSystemStatus.showStatus(true);
            }

        } else {

            that.eskimoMain.handleSetupNotCompleted();

            if (initializationTime) { // only at initialization time
                that.eskimoMain.showOnlyContent("setup");
                that.eskimoSystemStatus.updateStatus(false);
            }

            if (data.message) {
                eskimoMain.alert(ESKIMO_ALERT_LEVEL.INFO, data.message);
            }
        }
    }
    this.handleSetup = handleSetup;

    function loadSetup(initializationTime) {
        $.ajaxGet({
            url: "load-setup",
            success: (data, status, jqXHR) => {
                handleSetup(data, initializationTime);

                if (data.processingPending) {  // if backend says there is some provessing going on
                    that.eskimoMain.recoverOperationInProgress();
                }
            },
            error: errorHandler
        });
    }
    this.loadSetup = loadSetup;

    function showSetup () {

        if (that.eskimoMain.isOperationInProgress()) {
            that.eskimoMain.showProgressbar();
        }

        loadSetup();

        that.eskimoMain.showOnlyContent("setup");
    }
    this.showSetup = showSetup;

    function doSaveSetup (setupConfig) {
        $.ajaxPost({
            timeout: 1000 * 120,
            url: "save-setup",
            data: JSON.stringify(setupConfig),
            success: (data, status, jqXHR) => {

                that.eskimoMain.hideProgressbar();

                // OK
                console.log(data);

                if (!data || data.error) {
                    console.error(atob(data.error));
                    eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, atob(data.error));
                } else {

                    if (!data.command) {
                        eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, "Expected pending operations command but got none !");
                    } else {

                        if (!data.command.none) {
                            that.eskimoSetupCommand.showCommand(data.command);

                        } else {
                            eskimoMain.alert(ESKIMO_ALERT_LEVEL.INFO, "Configuration applied successfully");
                            that.eskimoMain.handleSetupCompleted();
                        }
                    }
                }

            },

            error: (jqXHR, status) => {
                that.eskimoMain.hideProgressbar();
                errorHandler(jqXHR, status);
            }
        });
    }
    this.doSaveSetup = doSaveSetup;

    function saveSetup () {

        // coherence checks
        let setupStorage = $("#setup_storage");
        if (setupStorage.val() == null || setupStorage.val() == "") {
            eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, "Configuration Storage path should be set");
            return false;
        }

        let sshUserName = $("#ssh_username");
        if (sshUserName.val() == null || sshUserName.val() == "") {
            eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, "SSH Username to use to reach cluster nodes should be set");
            return false;
        }

        let contentSShKey = $("#content-ssh-key");
        if (contentSShKey.val() == null || contentSShKey.val() == "") {
            eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, "SSH Identity Private Key to use to reach cluster nodes should be set");
            return false;
        }

        that.eskimoMain.showProgressbar();

        let setupConfig = $("form#setup-config").serializeObject();

        doSaveSetup(setupConfig);
    }
    this.saveSetup = saveSetup;

};

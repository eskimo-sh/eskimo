/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
eskimo.ServicesSettings = function () {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;
    this.eskimoMessaging = null;
    this.eskimoSettingsOperationsCommand = null;
    this.eskimoNodesConfig = null;

    const that = this;

    let SERVICES_SETTINGS = [];

    // Initialize HTML Div from Template
    this.initialize = function() {
        $("#inner-content-services-settings").load("html/eskimoServicesSettings.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt == "success") {

                $("#save-services-settings-btn").click(function (e) {
                    saveServicesSettings();

                    e.preventDefault();
                    return false;
                });

                $("#reset-services-settings-btn").click(function (e) {
                    showServicesConfig();

                    e.preventDefault();
                    return false;
                });

            } else if (statusTxt == "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }

        });
    };

    function loadServicesSettings() {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "load-services-settings",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    SERVICES_SETTINGS = data.settings;
                    layoutServicesSettings();


                } else {
                    alert(data.error);
                }
            },
            error: errorHandler
        });
    }
    this.setServicesSettingsForTest = function (servicesConfig) {
        SERVICES_SETTINGS = servicesConfig;
    };

    function saveServicesSettings() {

        let servicesConfigForm = $("form#services-settings").serializeObject();

        that.eskimoMain.showProgressbar();

        $.ajax({
            type: "POST",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            timeout: 1000 * 7200,
            url: "save-services-settings",
            data: JSON.stringify(servicesConfigForm),
            success: function (data, status, jqXHR) {

                that.eskimoMain.hideProgressbar();

                // OK
                console.log(data);

                if (!data || data.error) {
                    console.error(atob(data.error));
                    alert(atob(data.error));
                } else {

                    if (!data.command) {
                        alert ("Expected pending operations command but got none !");
                    } else {
                        that.eskimoSettingsOperationsCommand.showCommand (data.command);
                    }
                }
            },

            error: function (jqXHR, status) {
                // error handler
                console.log(jqXHR);
                console.log(status);
                showServicesSettingsMessage('fail : ' + status, false);

                that.eskimoMain.hideProgressbar();
            }
        });
    }

    function showServicesSettingsMessage(message, success) {
        let servicesSettingsWarning = $("#services-settings-warning");
        servicesSettingsWarning.css("display", "inherit");
        servicesSettingsWarning.css("visibility", "inherit");

        let servicesSettingsWarningMessage = $("#services-settings-warning-message");
        servicesSettingsWarningMessage.attr("class", "alert alert-" + (success ? "info" : "danger"));
        servicesSettingsWarningMessage.html(message);

        setTimeout(function() {
            servicesSettingsWarning.css("display", "none");
            servicesSettingsWarning.css("visibility", "hidden");
        }, 5000);
    }
    this.showServicesSettingsMessage = showServicesSettingsMessage;

    function showServicesConfig () {

        if (!that.eskimoMain.isSetupDone()) {
            that.eskimoMain.showSetupNotDone("Cannot configure nodes as long as initial setup is not completed");
            return;
        }

        if (that.eskimoMain.isOperationInProgress()) {
            that.eskimoMain.showProgressbar();
        }

        loadServicesSettings();

        that.eskimoMain.showOnlyContent("services-settings");
    }
    this.showServicesSettings = showServicesConfig;

    function layoutServicesSettings() {

        let servicesSettingsContent = '<div class="panel panel-default">';

        for (let i = 0; i < SERVICES_SETTINGS.length; i++) {
            let serviceEditableSettings = SERVICES_SETTINGS[i];
            let serviceName = serviceEditableSettings.name;
            let serviceEditableSettingsArray = serviceEditableSettings.settings;
            if (serviceEditableSettingsArray.length > 0) {

                servicesSettingsContent = servicesSettingsContent +
                    '<a class="collapsed" data-toggle="collapse" data-parent="#accordion" href="#collapse-'+serviceName+'" aria-expanded="false" aria-controls="collapse1">'+
                    '<div class="panel-heading" role="tab" id="heading-panel-'+serviceName+'"><table><tr>'+
                    '<td><img class="nodes-config-logo" src="' + that.eskimoNodesConfig.getServiceLogoPath(serviceName) + '" /></td>'+
                    '<td><h5>' +
                    serviceName +
                    '</h5></td>' +
                    '</tr></table></div>'+
                    '</a>'+
                    '<div id="collapse-'+serviceName+'" class="panel-collapse collapse" role="tabpanel" aria-labelledby="heading-panel-'+serviceName+'">'+
                    '<div class="panel-body">';

                for (let j = 0; j < serviceEditableSettingsArray.length; j++) {

                    let serviceEditableSettingsFile = serviceEditableSettingsArray[j];
                    console.log (serviceEditableSettingsFile);

                    servicesSettingsContent = servicesSettingsContent +

                        '<div class="col-md-12 col-sd-12">' +
                        '<h5><strong>Configuration file</strong> : ' + serviceEditableSettingsFile.filename + '</h5>' +
                        '</div>';

                    for (let k = 0; k < serviceEditableSettingsFile.properties.length; k++) {

                        let property = serviceEditableSettingsFile.properties[k];

                        let inputName = serviceName + "-" + property.name.replace(/\./g, "-");

                        servicesSettingsContent = servicesSettingsContent +
                            '<div class="col-md-12 col-sd-12">\n' +
                            '     <label class="col-md-12 control-label">'+
                            '         <strong>'+
                            property.name+
                            '         </strong> : '+
                            '     </label>\n' +
                            '     <div class="col-md-12">\n' +
                            property.comment.replace("\n", "<br>") +
                            ' (default value : ' + property.defaultValue + ')'+
                            '     </div>'+
                            '     <div class="col-lg-8 col-md-10 col-sm-12" style="margin-bottom: 5px;">\n' +
                            '         <input id="' + inputName + '" name="' + inputName + '" type="text"\n' +
                            '                placeholder="' + property.defaultValue + '" class="form-control input-md"' +
                            '                value="' + (property.value != null ? property.value : '') + '"'+
                            '         >\n' +
                            '     </div>\n' +
                            '     <br>\n' +
                            '</div>'
                    }
                }

                servicesSettingsContent += '</div></div>';

            }
        }

        servicesSettingsContent += '</div>';

        $("#services-settings-placeholder").html(servicesSettingsContent)
    }
    this.layoutServicesSettings = layoutServicesSettings;

};
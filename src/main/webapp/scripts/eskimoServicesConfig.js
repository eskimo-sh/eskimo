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
eskimo.ServicesConfig = function() {

    var that = this;

    var SERVICES_CONFIGURATION = [];

    // Initialize HTML Div from Template
    this.initialize = function() {
        $("#inner-content-services-config").load("html/eskimoServicesConfig.html", function (responseTxt, statusTxt, jqXHR) {

            loadServicesConfig(statusTxt, jqXHR);

            $("#save-services-config-btn").click(function (e) {
                saveServicesConfig();

                e.preventDefault();
                return false;
            });

        });
    };

    function loadServicesConfig(statusTxt, jqXHR) {
        if (statusTxt == "success") {

            $.ajax({
                type: "GET",
                dataType: "json",
                contentType: "application/json; charset=utf-8",
                url: "load-services-config",
                success: function (data, status, jqXHR) {

                    if (data.status == "OK") {

                        SERVICES_CONFIGURATION = data.configs;


                    } else {
                        alert(data.error);
                    }
                },
                error: errorHandler
            });

        } else if (statusTxt == "error") {
            alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
        }
    }

    function saveServicesConfig() {
        alert ("TODO save");
    }

    function showServicesConfig () {

        if (!eskimoMain.isSetupDone()) {
            showSetupNotDone("Cannot configure nodes as long as initial setup is not completed");
            return;
        }

        var re = /([a-zA-Z\-_]+)([0-9]*)/;

        if (eskimoMain.isOperationInProgress()) {
            eskimoMain.showProgressbar();
        }


        layoutServicesConfig();

        eskimoMain.showOnlyContent("services-config");
    }
    this.showServicesConfig = showServicesConfig;

    function layoutServicesConfig() {

        var servicesConfigContent = "";

        for (var i = 0; i < SERVICES_CONFIGURATION.length; i++) {
            var serviceEditableConfigs = SERVICES_CONFIGURATION[i];
            var serviceName = serviceEditableConfigs.name;
            var serviceEditableConfigsArray = serviceEditableConfigs.configs;
            if (serviceEditableConfigsArray.length > 0) {

                servicesConfigContent = servicesConfigContent +
                    '<div class="col-md-12 col-sd-12">' +
                    '<h4>' + serviceName + '</h4>' +
                    '</div>';

                for (var j = 0; j < serviceEditableConfigsArray.length; j++) {

                    var serviceEditableConfig = serviceEditableConfigsArray[j];
                    console.log (serviceEditableConfig);

                    servicesConfigContent = servicesConfigContent +
                        '<div class="col-md-12 col-sd-12">' +
                        '<h5><b>Configuration file</b> : ' + serviceEditableConfig.filename + '</h5>' +
                        '</div>';

                    for (var k = 0; k < serviceEditableConfig.properties.length; k++) {

                        var property = serviceEditableConfig.properties[k];

                        servicesConfigContent = servicesConfigContent +
                            '<div class="col-md-12 col-sd-12">\n' +
                            '     <label class="col-md-12 control-label" id="label_storage">'+
                            '         <b>'+
                            property.name+
                            '         </b> : '+
                            '     </label>\n' +
                            '     <div class="col-md-12">\n' +
                            property.comment.replace("\n", "<br>") +
                            '     </div>'+
                            '     <div class="col-md-12" style="margin-bottom: 5px;">\n' +
                            '         <input id="' + property.name + '" name="' + property.name + '" type="text"\n' +
                            '                placeholder="" class="form-control input-md"' +
                            '                value="' + property.defaultValue + '"'+
                            '         >\n' +
                            '     </div>\n' +
                            '     <br>\n' +
                            '</div>'
                    }

                }

            }
        }

        $("#services-config-placeholder").html(servicesConfigContent)

    }

    // call constructor
    this.initialize();
};
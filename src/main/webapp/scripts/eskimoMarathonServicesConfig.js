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
    window.eskimo = {}
}
eskimo.MarathonServicesConfig = function(constructorObject) {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;

    var that = this;

    // initialized by backend
    var MARATHON_SERVICES = [];

    this.initialize = function() {
        // Initialize HTML Div from Template
        $("#inner-content-marathon-services-config").load("html/eskimoMarathonServicesConfig.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt == "success") {

                $("#save-marathon-servicesbtn").click(function (e) {

                    var setupConfig = $("form#marathon-servicesconfig").serializeObject();

                    console.log(setupConfig);

                    try {
                        checkMarathonSetup(setupConfig, that.eskimoMain.getNodesConfig().getServicesDependencies(),
                            function () {
                                // callback if setup is OK
                                proceedWithMarathonInstallation(setupConfig);
                            });
                    } catch (error) {
                        alert ("error : " + error);
                    }

                    e.preventDefault();
                    return false;
                });

                $("#reinstall-marathon-servicesbtn").click(function (e) {
                    showReinstallSelection();
                    e.preventDefault();
                    return false;
                });

                $("#select-all-marathon-servicesconfig").click(function (e) {
                    selectAll();
                    e.preventDefault();
                    return false;
                });

                $("#reset-marathon-servicesconfig").click(function (e) {
                    showMarathonServicesConfig();
                    e.preventDefault();
                    return false;
                });

                loadMarathonServices();


            } else if (statusTxt == "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }

        });
    };

    function loadMarathonServices() {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "get-marathon-services",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    MARATHON_SERVICES = data.marathonServices;

                    //console.log (MARATHON_SERVICES);

                } else {
                    alert(data.error);
                }
            },
            error: errorHandler
        });
    }

    this.setMarathonServicesForTest = function(testServics) {
        MARATHON_SERVICES = testServics;
    };

    this.getMarathonServices = function() {
        return MARATHON_SERVICES;
    };

    function selectAll(){

        var allSelected = true;

        // are they all selected already
        for (var i = 0; i < MARATHON_SERVICES.length; i++) {
            if (!$('#' + MARATHON_SERVICES[i] + "_install").get(0).checked) {
                allSelected = false;
            }
        }

        // select all boxes
        for (var i = 0; i < MARATHON_SERVICES.length; i++) {
            $('#' + MARATHON_SERVICES[i] + "_install").get(0).checked = !allSelected;
        }
    }
    this.selectAll = selectAll;

    this.renderMarathonConfig = function (marathonConfig) {

        var marathonServicesTableBody = $("#marathon-services-table-body");

        for (var i = 0; i < MARATHON_SERVICES.length; i++) {

            var marathonServiceRow = '<tr>';

            marathonServiceRow += ''+
                '<td>' +
                '<img class="nodes-config-logo" src="' + that.eskimoMain.getNodesConfig().getServiceLogoPath(MARATHON_SERVICES[i]) + '" />' +
                '</td>'+
                '<td>'+
                MARATHON_SERVICES[i]+
                '</td>'+
                '<td>' +
                '    <input  type="checkbox" class="input-md" name="' + MARATHON_SERVICES[i] +'_install" id="'+MARATHON_SERVICES[i] +'_install"></input>' +
                '</td>';



            marathonServiceRow += '<tr>';
            marathonServicesTableBody.append (marathonServiceRow);
        }

        if (marathonConfig) {

            for (var installFlag in marathonConfig) {
                var indexOfInstall = installFlag.indexOf("_install");
                if (indexOfInstall > -1) {
                    var serviceName = installFlag.substring(0,indexOfInstall);
                    var flag = marathonConfig[installFlag];

                    console.log (serviceName + " - " + flag);

                    if (flag == "on") {
                        $('#' + serviceName + '_install').get(0).checked = true;
                    }

                }
            }
        }
    };

    function showReinstallSelection() {

        that.eskimoMain.getMarathonServicesSelection().showMarathonServiceSelection();

        var marathonServicesSelectionHTML = $('#marathon-services-container-table').html();
        marathonServicesSelectionHTML = marathonServicesSelectionHTML.replace(/marathon\-services/g, "marathon-services-selection");
        marathonServicesSelectionHTML = marathonServicesSelectionHTML.replace(/_install/g, "_reinstall");

        $('#marathon-services-selection-body').html(
            '<form id="marathon-servicesreinstall">' +
            marathonServicesSelectionHTML +
            '</form>');
    }
    this.showReinstallSelection = showReinstallSelection;

    function showMarathonServicesConfig () {

        if (!that.eskimoMain.isSetupDone()) {
            that.eskimoMain.showSetupNotDone("Cannot configure marathon services as long as initial setup is not completed");
            return;
        }

        if (that.eskimoMain.isOperationInProgress()) {
            that.eskimoMain.showProgressbar();
        }

        $.ajax({
            type: "GET",
            dataType: "json",
            url: "load-marathon-services-config",
            success: function (data, status, jqXHR) {

                console.log (data);

                $("#marathon-services-table-body").html("");

                if (!data.clear) {

                    that.renderMarathonConfig(data);
                    //alert ("TODO");

                } else if (data.clear == "missing") {

                    // render with no selections
                    that.renderMarathonConfig();

                } else if (data.clear == "setup"){

                    that.eskimoMain.handleSetupNotCompleted();

                }

                //alert(data);
            },
            error: errorHandler
        });

        that.eskimoMain.showOnlyContent("marathon-services-config");
    }
    this.showMarathonServicesConfig = showMarathonServicesConfig;

    this.checkMarathonSetup = checkMarathonSetup;

    this.proceedWithReinstall = function (reinstallConfig) {

        // rename _reinstall to _install in reinstallConfig
        var model = {};

        for (var reinstallKey in reinstallConfig) {

            var installKey = reinstallKey.substring(0, reinstallKey.indexOf("_reinstall")) + "_install";
            model[installKey] = reinstallConfig[reinstallKey];
        }

        proceedWithMarathonInstallation (model, true);
    };

    function proceedWithMarathonInstallation(model, reinstall) {

        that.eskimoMain.showProgressbar();

        // 1 hour timeout
        $.ajax({
            type: "POST",
            dataType: "json",
            timeout: 1000 * 120,
            contentType: "application/json; charset=utf-8",
            url: reinstall ? "reinstall-marathon-services-config" : "save-marathon-services-config",
            data: JSON.stringify(model),
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
                        that.eskimoMain.getMarathonOperationsCommand().showCommand (data.command);
                    }
                }
            },

            error: function (jqXHR, status) {
                that.eskimoMain.hideProgressbar();
                errorHandler (jqXHR, status);
            }
        });
    }
    this.proceedWithMarathonInstallation = proceedWithMarathonInstallation;

    // inject constructor object in the end
    if (constructorObject != null) {
        $.extend(this, constructorObject);
    }

    // call constructor
    this.initialize();
};

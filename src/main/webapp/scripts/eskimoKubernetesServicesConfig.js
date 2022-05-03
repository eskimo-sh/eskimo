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
/* @Deprecated To Be renamed */
eskimo.KubernetesServicesConfig = function() {

    // will be injected from glue
    this.eskimoMain = null;
    this.eskimoKubernetesServicesSelection = null;
    this.eskimoKubernetesOperationsCommand = null;
    this.eskimoNodesConfig = null;

    const that = this;

    // initialized by backend
    let KUBERNETES_SERVICES = [];
    let KUBERNETES_SERVICES_CONFIG = {};

    this.initialize = function() {
        // Initialize HTML Div from Template
        $("#inner-content-kubernetes-services-config").load("html/eskimoKubernetesServicesConfig.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt == "success") {

                $("#save-kubernetes-servicesbtn").click(function (e) {

                    let setupConfig = $("form#kubernetes-servicesconfig").serializeObject();

                    console.log(setupConfig);

                    try {
                        checkKubernetesSetup(setupConfig, that.eskimoNodesConfig.getServicesDependencies(), KUBERNETES_SERVICES_CONFIG,
                            function () {
                                // callback if setup is OK
                                proceedWithKubernetesInstallation(setupConfig);
                            });
                    } catch (error) {
                        alert ("error : " + error);
                    }

                    e.preventDefault();
                    return false;
                });

                $("#reinstall-kubernetes-servicesbtn").click(function (e) {
                    showReinstallSelection();
                    e.preventDefault();
                    return false;
                });

                $("#select-all-kubernetes-servicesconfig").click(function (e) {
                    selectAll();
                    e.preventDefault();
                    return false;
                });

                $("#reset-kubernetes-servicesconfig").click(function (e) {
                    showKubernetesServicesConfig();
                    e.preventDefault();
                    return false;
                });

                loadKubernetesServices();


            } else if (statusTxt == "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }

        });
    };

    function loadKubernetesServices() {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "get-kubernetes-services",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    KUBERNETES_SERVICES = data.kubernetesServices;
                    KUBERNETES_SERVICES_CONFIG = data.kubernetesServicesConfigurations;

                    //console.log (KUBERNETES_SERVICES);

                } else {
                    alert(data.error);
                }
            },
            error: errorHandler
        });
    }

    this.setKubernetesServicesForTest = function(testServices) {
        KUBERNETES_SERVICES = testServices;
    };
    this.setKubernetesServicesConfigForTest = function(testServicesConfig) {
        KUBERNETES_SERVICES_CONFIG = testServicesConfig;
    };

    this.getKubernetesServices = function() {
        return KUBERNETES_SERVICES;
    };

    function selectAll(){

        let allSelected = true;

        // are they all selected already
        for (let i = 0; i < KUBERNETES_SERVICES.length; i++) {
            if (!$('#' + KUBERNETES_SERVICES[i] + "_install").get(0).checked) {
                allSelected = false;
            }
        }

        // select all boxes
        for (let i = 0; i < KUBERNETES_SERVICES.length; i++) {
            $('#' + KUBERNETES_SERVICES[i] + "_install").get(0).checked = !allSelected;
        }
    }
    this.selectAll = selectAll;

    this.onKubernetesServiceSelected = function (serviceName) {

        $('#' + serviceName + '_cpu_setting').html(
            '    <input style="width: 80px;" type="text" class="input-md" name="' + serviceName +'_cpu" id="' + serviceName +'_cpu"></input>');

        $('#' + serviceName + '_ram_setting').html(
            '    <input style="width: 80px;" type="text" class="input-md" name="' + serviceName +'_ram" id="' + serviceName +'_ram"></input>');

        // FIXME fill with either previously configured settings or default settings from service definition
    };

    this.onKubernetesServiceUnselected = function (serviceName) {

        $('#' + serviceName + '_cpu_setting').html("");

        $('#' + serviceName + '_ram_setting').html("");
    };

    this.renderKubernetesConfig = function (kubernetesConfig) {

        let kubernetesServicesTableBody = $("#kubernetes-services-table-body");

        for (let i = 0; i < KUBERNETES_SERVICES.length; i++) {

            let kubernetesServiceRow = '<tr>';

            kubernetesServiceRow += ''+
                '<td>' +
                '<img class="nodes-config-logo" src="' + that.eskimoNodesConfig.getServiceLogoPath(KUBERNETES_SERVICES[i]) + '" />' +
                '</td>'+
                '<td>'+
                KUBERNETES_SERVICES[i]+
                '</td>'+
                '<td style="text-align: center;">' +
                '    <input  type="checkbox" class="input-md" name="' + KUBERNETES_SERVICES[i] +'_install" id="'+KUBERNETES_SERVICES[i] +'_install"></input>' +
                '</td>' +
                '<td id="' + KUBERNETES_SERVICES[i] + '_cpu_setting" style="text-align: center;">' +
                '</td>' +
                '<td id="' + KUBERNETES_SERVICES[i] + '_ram_setting" style="text-align: center;">' +
                '</td>';


            kubernetesServiceRow += '<tr>';
            kubernetesServicesTableBody.append (kubernetesServiceRow);

            $('#' + KUBERNETES_SERVICES[i] +'_install').change (function() {
                //alert(KUBERNETES_SERVICES[i] +'_install' + " - " + $('#' + KUBERNETES_SERVICES[i] +'_install').is(":checked"));
                if ($('#' + KUBERNETES_SERVICES[i] +'_install').is(":checked")) {
                    that.onKubernetesServiceSelected(KUBERNETES_SERVICES[i]);
                } else {
                    that.onKubernetesServiceUnselected(KUBERNETES_SERVICES[i]);
                }
            });
        }

        if (kubernetesConfig) {

            for (let installFlag in kubernetesConfig) {
                let indexOfInstall = installFlag.indexOf("_install");
                if (indexOfInstall > -1) {
                    let serviceName = installFlag.substring(0,indexOfInstall);
                    let flag = kubernetesConfig[installFlag];

                    console.log (serviceName + " - " + flag);

                    if (flag == "on") {
                        $('#' + serviceName + '_install').get(0).checked = true;

                        that.onKubernetesServiceSelected(serviceName);
                    }

                }
            }
        }
    };

    function showReinstallSelection() {

        that.eskimoKubernetesServicesSelection.showKubernetesServiceSelection();

        let kubernetesServicesSelectionHTML = $('#kubernetes-services-container-table').html();
        kubernetesServicesSelectionHTML = kubernetesServicesSelectionHTML.replace(/kubernetes\-services/g, "kubernetes-services-selection");
        kubernetesServicesSelectionHTML = kubernetesServicesSelectionHTML.replace(/_install/g, "_reinstall");

        $('#kubernetes-services-selection-body').html(
            '<form id="kubernetes-servicesreinstall">' +
            kubernetesServicesSelectionHTML +
            '</form>');
    }
    this.showReinstallSelection = showReinstallSelection;

    function showKubernetesServicesConfig () {

        if (!that.eskimoMain.isSetupDone()) {
            that.eskimoMain.showSetupNotDone("Cannot configure kubernetes services as long as initial setup is not completed");
            return;
        }

        if (that.eskimoMain.isOperationInProgress()) {
            that.eskimoMain.showProgressbar();
        }

        $.ajax({
            type: "GET",
            dataType: "json",
            url: "load-kubernetes-services-config",
            success: function (data, status, jqXHR) {

                console.log (data);

                $("#kubernetes-services-table-body").html("");

                if (!data.clear) {

                    that.renderKubernetesConfig(data);
                    //alert ("TODO");

                } else if (data.clear == "missing") {

                    // render with no selections
                    that.renderKubernetesConfig();

                } else if (data.clear == "setup"){

                    that.eskimoMain.handleSetupNotCompleted();

                }

                //alert(data);
            },
            error: errorHandler
        });

        that.eskimoMain.showOnlyContent("kubernetes-services-config");
    }
    this.showKubernetesServicesConfig = showKubernetesServicesConfig;

    this.checkKubernetesSetup = checkKubernetesSetup;

    this.proceedWithReinstall = function (reinstallConfig) {

        // rename _reinstall to _install in reinstallConfig
        let model = {};

        for (let reinstallKey in reinstallConfig) {

            let installKey = reinstallKey.substring(0, reinstallKey.indexOf("_reinstall")) + "_install";
            model[installKey] = reinstallConfig[reinstallKey];
        }

        proceedWithKubernetesInstallation (model, true);
    };

    function proceedWithKubernetesInstallation(model, reinstall) {

        that.eskimoMain.showProgressbar();

        // 1 hour timeout
        $.ajax({
            type: "POST",
            dataType: "json",
            timeout: 1000 * 120,
            contentType: "application/json; charset=utf-8",
            url: reinstall ? "reinstall-kubernetes-services-config" : "save-kubernetes-services-config",
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
                        that.eskimoKubernetesOperationsCommand.showCommand (data.command);
                    }
                }
            },

            error: function (jqXHR, status) {
                that.eskimoMain.hideProgressbar();
                errorHandler (jqXHR, status);
            }
        });
    }
    this.proceedWithKubernetesInstallation = proceedWithKubernetesInstallation;
};

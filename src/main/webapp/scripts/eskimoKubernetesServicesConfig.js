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

eskimo.KubernetesServicesConfig = function() {

    const INSTALL_FLAG = "_install";

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
        $("#inner-content-kubernetes-services-config").load("html/eskimoKubernetesServicesConfig.html", (responseTxt, statusTxt, jqXHR) => {

            if (statusTxt === "success") {

                $("#save-kubernetes-servicesbtn").click(e => {

                    let kubernetesConfig = $("form#kubernetes-servicesconfig").serializeObject();

                    console.log(kubernetesConfig);

                    try {
                        checkKubernetesSetup(kubernetesConfig, that.eskimoNodesConfig.getServicesDependencies(), KUBERNETES_SERVICES_CONFIG,
                            () => { proceedWithKubernetesInstallation(kubernetesConfig); });
                    } catch (error) {
                        eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, error);
                    }

                    e.preventDefault();
                    return false;
                });

                $("#reinstall-kubernetes-servicesbtn").click(e => {
                    showReinstallSelection();
                    e.preventDefault();
                    return false;
                });

                $("#select-all-kubernetes-servicesconfig").click(e => {
                    selectAll();
                    e.preventDefault();
                    return false;
                });

                $("#reset-kubernetes-servicesconfig").click(e => {
                    showKubernetesServicesConfig();
                    e.preventDefault();
                    return false;
                });

                loadKubernetesServices();


            } else if (statusTxt === "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }

        });
    };

    function loadKubernetesServices() {
        $.ajaxGet({
            url: "get-kubernetes-services",
            success: (data, status, jqXHR) => {

                if (data.status == "OK") {

                    KUBERNETES_SERVICES = data.kubernetesServices;
                    KUBERNETES_SERVICES_CONFIG = data.kubernetesServicesConfigurations;

                    //console.log (KUBERNETES_SERVICES);

                } else {
                    eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error);
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

        // gind out if are they all selected already
        for (let i = 0; i < KUBERNETES_SERVICES.length; i++) {
            if (!$('#' + KUBERNETES_SERVICES[i] + INSTALL_FLAG).get(0).checked) {
                allSelected = false;
            }
        }

        // select / deselect all boxes
        for (let i = 0; i < KUBERNETES_SERVICES.length; i++) {
            $('#' + KUBERNETES_SERVICES[i] + INSTALL_FLAG).get(0).checked = !allSelected;

            if (!allSelected) {
                that.onKubernetesServiceSelected(KUBERNETES_SERVICES[i]);
            } else {
                that.onKubernetesServiceUnselected(KUBERNETES_SERVICES[i]);
            }
        }

    }
    this.selectAll = selectAll;

    function reqValueChangeHandler (regex, inputId) {
        const thisInput = $("#" + inputId);
        const value = thisInput.val();
        if (value && value !== "") {
            if (!regex.test(value)) {
                thisInput.addClass("invalid")
            } else {
                thisInput.removeClass("invalid")
            }
        }
    }

    function deploymentStrategyChanged() {
        const thisInput = $("#" + this.id);
        const serviceName = this.id.substring(0, this.id.indexOf("_deployment_strategy"))
        if (thisInput.get(0).checked) {
            $('#' + serviceName + '_replicas_setting').html("");
        } else {
            $('#' + serviceName + '_replicas_setting').html(
                '<input style="width: 80px;" type="text" class="form-control" name="' + serviceName + '_replicas" id="' + serviceName + '_replicas">');
            $("#" + serviceName + "_replicas").change(function() {
                reqValueChangeHandler (eskimoKubeReplicasCheckRE, this.id);
            });
        }
    }

    this.onKubernetesServiceSelected = function (serviceName, kubernetesConfig) {

        $('#' + serviceName + '_cpu_setting').html(
            '    <input style="width: 80px;" type="text" class="form-control" name="' + serviceName +'_cpu" id="' + serviceName +'_cpu">');

        $('#' + serviceName + '_ram_setting').html(
            '    <input style="width: 80px;" type="text" class="form-control" name="' + serviceName +'_ram" id="' + serviceName +'_ram">');

        $("#" + serviceName + "_cpu").change(function() {
            reqValueChangeHandler (eskimoKubeCpuCheckRE, this.id);
        });

        $("#" + serviceName + "_ram").change(function() {
            reqValueChangeHandler (eskimoKubeRamCheckRE, this.id);
        });

        if (!KUBERNETES_SERVICES_CONFIG[serviceName].unique && !KUBERNETES_SERVICES_CONFIG[serviceName].registryOnly) {
            $('#' + serviceName + '_depl_strat').html(
                '<input type="checkbox" class="form-check-input" name="' + serviceName + '_deployment_strategy" id="' + serviceName + '_deployment_strategy">');
        }

        $("#" + serviceName + "_deployment_strategy").change(deploymentStrategyChanged);

        let cpuSet = false;
        let ramSet = false;
        let deplStrategySet = false;
        let replicasSet = false;

        // Trying to get previouly configured value
        if (kubernetesConfig) {

            let cpuConf = kubernetesConfig[serviceName + "_cpu"];
            if (cpuConf) {
                $('#' + serviceName + '_cpu').val (cpuConf);
                cpuSet = true;
            }

            let ramConf = kubernetesConfig[serviceName + "_ram"];
            if (ramConf) {
                $('#' + serviceName + '_ram').val (ramConf);
                ramSet = true;
            }

            let deplStrategy = kubernetesConfig[serviceName + "_deployment_strategy"];
            const $deploymentStrategy = $('#' + serviceName + '_deployment_strategy');
            if (deplStrategy && deplStrategy === "on") {
                $deploymentStrategy.attr("checked", true);
                $deploymentStrategy.change();
                deplStrategySet = true;
            } else {
                $deploymentStrategy.attr("checked", false);
                $deploymentStrategy.change();
                let replicas = kubernetesConfig[serviceName + "_replicas"];
                if (replicas) {
                    $('#' + serviceName + '_replicas').val (replicas);
                    replicasSet = true;
                }
            }

        }

        // If no previously configured values have been found, take value from service definition
        let serviceKubeConfig = KUBERNETES_SERVICES_CONFIG[serviceName].kubeConfig;
        if (KUBERNETES_SERVICES_CONFIG[serviceName].kubeConfig) {
            let request = serviceKubeConfig.request;
            if (request) {
                let cpuString = request.cpu;
                if (!cpuSet && cpuString) {
                    $('#' + serviceName + '_cpu').val (cpuString);
                    cpuSet = true;
                }
                let ramString = request.ram;
                if (!ramSet && ramString) {
                    $('#' + serviceName + '_ram').val (ramString);
                    ramSet = true;
                }
            }
        }

        // if no previous deploymemnt strategy was set, neither replicas, default is cluster wide
        if (!deplStrategySet && !replicasSet) {
            const $deploymentStrategy = $('#' + serviceName + '_deployment_strategy');
            $deploymentStrategy.attr("checked", true);
            $deploymentStrategy.change();
        }

        // if node could be found there either, take hardcoded default values
        if (!cpuSet) {
            $('#' + serviceName + '_cpu').val ("1");
        }
        if (!ramSet) {
            $('#' + serviceName + '_ram').val ("1G");
        }
    };

    this.onKubernetesServiceUnselected = function (serviceName) {

        $('#' + serviceName + '_cpu_setting').html("");
        $('#' + serviceName + '_ram_setting').html("");
        $('#' + serviceName + '_depl_strat').html("");
        $('#' + serviceName + '_replicas_setting').html("");
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
                '    <input  type="checkbox" class="form-check-input" name="' + KUBERNETES_SERVICES[i] + INSTALL_FLAG + '" id="'+KUBERNETES_SERVICES[i] + INSTALL_FLAG + '"></input>' +
                '</td>' +
                '<td id="' + KUBERNETES_SERVICES[i] + '_cpu_setting" style="text-align: center;">' +
                '</td>' +
                '<td id="' + KUBERNETES_SERVICES[i] + '_ram_setting" style="text-align: center;">' +
                '</td>' +
                '<td id="' + KUBERNETES_SERVICES[i] + '_depl_strat" style="text-align: center;">' +
                '</td>' +
                '<td id="' + KUBERNETES_SERVICES[i] + '_replicas_setting" style="text-align: center;">' +
                '</td>';


            kubernetesServiceRow += '<tr>';
            kubernetesServicesTableBody.append (kubernetesServiceRow);

            $('#' + KUBERNETES_SERVICES[i] + INSTALL_FLAG).change (() => {
                //alert(KUBERNETES_SERVICES[i] + INSTALL_FLAG + " - " + $('#' + KUBERNETES_SERVICES[i] + INSTALL_FLAG).is(":checked"));
                if ($('#' + KUBERNETES_SERVICES[i] + INSTALL_FLAG).is(":checked")) {
                    that.onKubernetesServiceSelected(KUBERNETES_SERVICES[i]);
                } else {
                    that.onKubernetesServiceUnselected(KUBERNETES_SERVICES[i]);
                }
            });
        }

        if (kubernetesConfig) {

            for (let installFlag in kubernetesConfig) {
                let indexOfInstall = installFlag.indexOf(INSTALL_FLAG);
                if (indexOfInstall > -1) {
                    let serviceName = installFlag.substring(0,indexOfInstall);
                    let flag = kubernetesConfig[installFlag];

                    console.log (serviceName + " - " + flag);

                    if (flag == "on") {
                        $('#' + serviceName + INSTALL_FLAG).get(0).checked = true;

                        that.onKubernetesServiceSelected(serviceName, kubernetesConfig);
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
        kubernetesServicesSelectionHTML = kubernetesServicesSelectionHTML.replace(/Enabled on K8s/g, "Reinstall on K8s");

        $('#kubernetes-services-selection-body').html(
            '<form id="kubernetes-servicesreinstall">' +
            kubernetesServicesSelectionHTML +
            '</form>');

        // removing last columns
        for (let i = 7 ; i >= 4; i--) {
            $("#kubernetes-services-selection-table").find("thead tr td:nth-child(" + i + "), tbody tr td:nth-child(" + i + ")").remove();
        }
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

        $.ajaxGet({
            url: "load-kubernetes-services-config",
            success: (data, status, jqXHR) => {

                console.log (data);

                $("#kubernetes-services-table-body").html("");

                if (!data.clear) {

                    that.renderKubernetesConfig(data);

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

            let installKey = reinstallKey.substring(0, reinstallKey.indexOf("_reinstall")) + INSTALL_FLAG;
            model[installKey] = reinstallConfig[reinstallKey];
        }

        proceedWithKubernetesInstallation (model, true);
    };

    function proceedWithKubernetesInstallation(model, reinstall) {

        that.eskimoMain.showProgressbar();

        // 1 hour timeout
        $.ajaxPost({
            timeout: 1000 * 120,
            url: reinstall ? "reinstall-kubernetes-services-config" : "save-kubernetes-services-config",
            data: JSON.stringify(model),
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
                        that.eskimoKubernetesOperationsCommand.showCommand (data.command);
                    }
                }
            },

            error: (jqXHR, status) => {
                that.eskimoMain.hideProgressbar();
                errorHandler (jqXHR, status);
            }
        });
    }
    this.proceedWithKubernetesInstallation = proceedWithKubernetesInstallation;
};

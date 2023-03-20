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
eskimo.ServicesSelection = function() {

    const SERVICE_CHOICE_REGEX = /([a-zA-Z0-9\-_]*[a-zA-Z\-_]+)([0-9]*)-(choice)/;
    const SERVICE_NAME_REGEX   = /([a-zA-Z0-9\-_]*[a-zA-Z\-_]+)([0-9]*)/;

    // will be injected eventually from constructorObject
    this.eskimoMain = null;
    this.eskimoNodesConfig = null;

    const that = this;

    const NODE_ID_FIELD = "node_id";

    let nodeNbrInConfiguration = -1;
    let servicesSelectedcallback = null;

    let SERVICES_CONFIGURATION = [];

    // Initialize HTML Div from Template
    this.initialize = function() {
        $("#services-selection-modal-wrapper").load("html/eskimoServicesSelection.html", (responseTxt, statusTxt, jqXHR) => {

            if (statusTxt === "success") {

                loadServicesConfig();

                $('#services-selection-header-close').click(cancelServicesSelection);
                $('#services-selection-button-select-all').click(servicesSelectionSelectAll);
                $('#services-selection-button-close').click(cancelServicesSelection);
                $('#services-selection-header-validate').click(validateServicesSelection);

            } else if (statusTxt === "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });
    };

    function loadServicesConfig() {
        $.ajaxGet({
            url: "get-services-config",
            success: (data, status, jqXHR2) => {

                if (data.status == "OK") {

                    SERVICES_CONFIGURATION = data.servicesConfigurations;

                    initModalServicesConfig();

                } else {
                    eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error);
                }
            },
            error: errorHandler
        });
    }

    this.getCurrentNodesConfig = function() {
        let setupConfig = $("form#nodes-config").serializeObject();
        //console.log (setupConfig);
        return setupConfig;
    };

    function showServiceSelection(nodeNbr, callback, isRange) {

        servicesSelectedcallback = callback;

        let configuredServices = that.eskimoNodesConfig.getConfiguredServices();
        let nbrOfNodes = that.eskimoNodesConfig.getNodesCount();

        // clear all boxes
        for (let i = 0; i < configuredServices.length; i++) {

            let choice = $('#' + configuredServices[i] + "-choice");

            if (!choice.get(0) || choice.get(0) == null) {
                throw "Couldn't find choice for " + configuredServices[i];
            }

            choice.get(0).checked = false;
            choice.removeClass("disabled")

            let label = $('#' + configuredServices[i] + "-label");
            label.removeClass("disabled-label");
            label.removeClass("forced-label");
        }

        nodeNbrInConfiguration = nodeNbr;

        if (nodeNbr != null && nodeNbr != "empty") {

            // check and disable mandatory services
            for (let serviceName in SERVICES_CONFIGURATION) {
                let serviceConfig = SERVICES_CONFIGURATION[serviceName];
                if (serviceConfig.mandatory) {

                    let choice = $('#' + serviceName + '-choice');
                    let label = $('#' + serviceName + '-label');

                    if (serviceConfig.conditional == "MULTIPLE_NODES") {

                        if (nbrOfNodes > 1) {
                            $('#' + serviceName + '-choice').get(0).checked = true;
                        }
                        choice.addClass("disabled");
                        label.addClass("forced-label");

                    } else {

                        choice.get(0).checked = true;
                        choice.addClass("disabled");
                        label.addClass("forced-label");
                    }
                }
            }

            // check and disable unique services
            if (isRange) {
                for (let serviceName in SERVICES_CONFIGURATION) {
                    let serviceConfig = SERVICES_CONFIGURATION[serviceName];
                    if (serviceConfig.unique) {

                        let choice = $('#' + serviceName + '-choice');
                        let label = $('#' + serviceName + '-label');

                        choice.get(0).checked = false;
                        choice.addClass("disabled");
                        label.addClass("disabled-label");
                    }
                }
            }

            $('#ntp-choice').get(0).checked = true;

            // TODO
            //$("#services-selection-button-select-all").css("visibility", "hidden");

            // enabling node ntp to nake it selectaBLE
            let setupConfig = that.getCurrentNodesConfig();

            for (let key in setupConfig) {

                let match = key.match(SERVICE_NAME_REGEX);

                let serviceName = null;
                let nbr = -1;
                if (match[2] != null && match[2] != "") {
                    nbr = parseInt(match[2]);
                    serviceName = match[1]
                } else {
                    nbr = setupConfig[key];
                    serviceName = key;
                }

                if (nbr == nodeNbrInConfiguration && serviceName != NODE_ID_FIELD) {

                    // I have only enabled (on) services in setupConfig, so I can simply enable it if I get to this stage
                    $('#' + serviceName + "-choice").get(0).checked = true;
                }
            }

        } else {

            // TODO
            //$("#services-selection-button-select-all").css("visibility", "visible");
        }

        $('#services-selection-modal').modal("show");
    }
    this.showServiceSelection = showServiceSelection;

    function servicesSelectionSelectAll() {

        let shouldSelectAll = false;

        let configuredServices = that.eskimoNodesConfig.getConfiguredServices();

        // are they all selected already ?
        for (let i = 0; i < configuredServices.length; i++) {
            if (!$('#' + configuredServices[i] + "-choice").get(0).checked) {
                // Nope. Lets select them all then
                shouldSelectAll = true;
            }
        }

        // select all boxes
        for (let i = 0; i < configuredServices.length; i++) {
            $('#' + configuredServices[i] + "-choice").get(0).checked = shouldSelectAll;
        }

        // if we unselect, then of we are on a node conf we hould keep the mandatory ones
        if (!shouldSelectAll && nodeNbrInConfiguration != null && nodeNbrInConfiguration != "empty") {
            for (let serviceName in SERVICES_CONFIGURATION) {
                let serviceConfig = SERVICES_CONFIGURATION[serviceName];
                if (serviceConfig.mandatory) {
                    $('#' + serviceName + "-choice").get(0).checked = true;
                }
            }
        }
    }
    this.servicesSelectionSelectAll = servicesSelectionSelectAll;

    function cancelServicesSelection() {
        $('#services-selection-modal').modal("hide");
    }
    this.cancelServicesSelection = cancelServicesSelection;

    function validateServicesSelection() {

        $('#services-selection-modal').modal("hide");

        let serviceSelection = $("#services-selection-form").serializeObject();

        let retModel = {};

        for (let key in serviceSelection) {

            let serviceName = key.substring(0, key.length - "-choice".length);

            let value = serviceSelection[key];

            if (nodeNbrInConfiguration != null && nodeNbrInConfiguration != "empty") {
                if (value == "choice") {
                    retModel[serviceName] = "" + nodeNbrInConfiguration;
                } else {
                    retModel[serviceName + nodeNbrInConfiguration] = "on";
                }
            } else {
                retModel[serviceName] = "on";
            }
        }

        if (servicesSelectedcallback != null) {
            servicesSelectedcallback(retModel, nodeNbrInConfiguration);
        }
    }
    this.validateServicesSelection = validateServicesSelection;

    function servicesSelectionRadioMouseDown() {

        //console.log (radioButton.id);

        let match = this.id.match(SERVICE_CHOICE_REGEX);

        console.log(this.id + " - " + match[1] + " - " + match[2] + " - " + match[3]);
        let radioName = match[1];

        let value = (match[3] != null && match[3] != "") ? "choice" : match[2];

        if ($('form#services-selection-form input[name=' + radioName + (value == "choice" ? "-choice" : "") + ']:checked').val() == value) {
            setTimeout(() => {
                $('#' + radioName + (value == "choice" ? "-choice" : value)).get(0).checked = false;
            }, 100);

        }
    }
    this.servicesSelectionRadioMouseDown = servicesSelectionRadioMouseDown;

    function getService (row, col) {
        for (let serviceName in SERVICES_CONFIGURATION) {
            let serviceConfig = SERVICES_CONFIGURATION[serviceName];

            if (serviceConfig.row == row && serviceConfig.col == col) {
                return serviceConfig;
            }
        }
        return null;
    }
    this.getService = getService;


    function initModalServicesConfig() {

        let newIn = '<form id="services-selection-form">';

        for (let row = 1; row <= 20; row++) {

            let oneFound = false;
            for (let col = 1; col <= 3; col++) {
                let serviceConfig = getService(row, col);
                if (serviceConfig != null) {
                    oneFound = true;
                }
            }
            if (!oneFound) {
                continue; // done
            }

            let rowHTML =  '<div class="col-md-12 row"> ';
            for (let col = 1; col <= 3; col++) {

                let serviceConfig = getService (row, col);
                if (serviceConfig != null) {

                    if (serviceConfig.unique) {
                        rowHTML +=
                            '<div class="radio col-md-4 service-selection no-padding">' +
                            '    <label id="'+serviceConfig.name + '-label" class="control-label">'+serviceConfig.title+'</label>'+
                            '    <label id="'+serviceConfig.name + '-icon" class="control-logo"><img class="control-logo-logo" src="' + serviceConfig.logo+'"/></label>'+
                            '    <label class="radio-inline">' +
                            '        <input  type="radio" ' +
                            '                class="form-check-input service-selection-radio-choice" name="'+serviceConfig.name +'-choice" id="'+serviceConfig.name +'-choice" value="choice"></input>' +
                            '    </label>' +
                            '</div>';
                    } else {
                        rowHTML +=
                            '<div class="checkbox col-md-4 service-selection no-padding">' +
                            '    <label id="'+serviceConfig.name + '-label" class="control-label">'+serviceConfig.title+'</label>' +
                            '    <label id="'+serviceConfig.name + '-icon" class="control-logo"><img class="control-logo-logo" src="' + serviceConfig.logo+'"/></label>'+
                            '    <label class="checkbox-inline">' +
                            '        <input  type="checkbox" class="form-check-input" name="'+serviceConfig.name +'-choice" id="'+serviceConfig.name +'-choice"></input>' +
                            '    </label>' +
                            '</div>';
                    }
                } else {
                    rowHTML +=
                        '<div class="col-md-4 no-padding">' +
                        '</div>';
                }
            }

            rowHTML += '</div>';
            newIn += rowHTML;
        }

        newIn += '</form>';

        $("#services-selection-body").html($(newIn));

        $(".service-selection-radio-choice").mousedown(servicesSelectionRadioMouseDown);
    }
    this.initModalServicesConfig = initModalServicesConfig;
};
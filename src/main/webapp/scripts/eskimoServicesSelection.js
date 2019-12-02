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
eskimo.ServicesSelection = function() {

    var that = this;

    var nodeNbrInConfiguration = -1;
    var servicesSelectedcallback = null;

    var SERVICES_CONFIGURATION = [];

    // Initialize HTML Div from Template
    this.initialize = function() {
        $("#services-selection-modal-wrapper").load("html/eskimoServicesSelection.html", function (responseTxt, statusTxt, jqXHR) {

            loadServicesConfig(statusTxt, jqXHR);
        });
    };

    function loadServicesConfig(statusTxt, jqXHR) {
        if (statusTxt == "success") {

            $.ajax({
                type: "GET",
                dataType: "json",
                contentType: "application/json; charset=utf-8",
                url: "get-services-config",
                success: function (data, status, jqXHR) {

                    if (data.status == "OK") {

                        SERVICES_CONFIGURATION = data.servicesConfigurations;

                        initModalServicesConfig();

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

    this.setServicesConfigForTest = function (servicesConfig) {
        SERVICES_CONFIGURATION = servicesConfig;
    };

    function showServiceSelection(nodeNbr, callback, isRange) {

        servicesSelectedcallback = callback;

        var configuredServices = eskimoMain.getNodesConfig().getConfiguredServices();
        var nbrOfNodes = eskimoMain.getNodesConfig().getNodesCount();

        // clear all boxes
        for (var i = 0; i < configuredServices.length; i++) {

            var choice = $('#' + configuredServices[i] + "-choice");
            choice.get(0).checked = false;
            choice.removeClass("disabled")

            var label = $('#' + configuredServices[i] + "-label");
            label.removeClass("disabled-label");
            label.removeClass("forced-label");
        }

        nodeNbrInConfiguration = nodeNbr;

        if (nodeNbr != null && nodeNbr != "empty") {

            // check and disable mandatory services
            for (var serviceName in SERVICES_CONFIGURATION) {
                var serviceConfig = SERVICES_CONFIGURATION[serviceName];
                if (serviceConfig.mandatory) {

                    var choice = $('#' + serviceName + '-choice');
                    var label = $('#' + serviceName + '-label');

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
                for (var serviceName in SERVICES_CONFIGURATION) {
                    var serviceConfig = SERVICES_CONFIGURATION[serviceName];
                    if (serviceConfig.unique) {

                        var choice = $('#' + serviceName + '-choice');
                        var label = $('#' + serviceName + '-label');

                        choice.get(0).checked = false;
                        choice.addClass("disabled");
                        label.addClass("disabled-label");
                    }
                }
            }

            $('#ntp-choice').get(0).checked = true;

            $("#select-all-services-button").css("visibility", "hidden");

            // enabling node ntp to nake it selectaBLE
            var setupConfig = $("form#nodes-config").serializeObject();

            for (var key in setupConfig) {
                //var re = /([a-zA-Z\-_]+)([0-9]+)/;
                var re = /([a-zA-Z\-_]+)([0-9]*)/;
                var match = key.match(re);

                var serviceName = null;
                var nbr = -1;
                if (match[2] != null && match[2] != "") {
                    nbr = parseInt(match[2]);
                    serviceName = match[1]
                } else {
                    nbr = setupConfig[key];
                    serviceName = key;
                }

                if (nbr == nodeNbrInConfiguration && serviceName != "action_id") {

                    // I have only enabled (on) services in setupConfig, so I can simply enable it if I get to this stage
                    $('#' + serviceName + "-choice").get(0).checked = true;
                }
            }

        } else {

            $("#select-all-services-button").css("visibility", "visible");
        }

        $('#services-selection-modal').modal("show");
    }
    this.showServiceSelection = showServiceSelection;

    function servicesSelectionSelectAll() {

        var allSelected = true;

        var configuredServices = eskimoMain.getNodesConfig().getConfiguredServices();

        // are they all selected already
        for (var i = 0; i < configuredServices.length; i++) {
            if (!$('#' + configuredServices[i] + "-choice").get(0).checked) {
                allSelected = false;
            }
        }

        // select all boxes
        for (var i = 0; i < configuredServices.length; i++) {
            $('#' + configuredServices[i] + "-choice").get(0).checked = !allSelected;
        }
    }
    this.servicesSelectionSelectAll = servicesSelectionSelectAll;

    function cancelServicesSelection() {
        $('#services-selection-modal').modal("hide");
    }
    this.cancelServicesSelection = cancelServicesSelection;

    function validateServicesSelection() {

        $('#services-selection-modal').modal("hide");

        var serviceSelection = $("#services-selection-form").serializeObject();

        console.log (serviceSelection);

        var retModel = {};

        for (key in serviceSelection) {

            var serviceName = key.substring(0, key.length - "-choice".length);

            var value = serviceSelection[key];

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

    function servicesSelectionRadioMouseDown(radioButton) {

        //console.log (radioButton.id);

        var re = /([a-zA-Z\-_]+)([0-9]*)-(choice)/;
        var match = radioButton.id.match(re);


        console.log(radioButton.id + " - " + match[1] + " - " + match[2] + " - " + match[3]);
        var radioName = match[1];

        var value = (match[3] != null && match[3] != "") ? "choice" : match[2];
        console.log(value);

        if ($('form#services-selection-form input[name=' + radioName + (value == "choice" ? "-choice" : "") + ']:checked').val() == value) {
            //console.log("KK");
            setTimeout(
                function () {
                    $('#' + radioName + (value == "choice" ? "-choice" : value)).get(0).checked = false;
                }, 200);

        }
    }
    this.servicesSelectionRadioMouseDown = servicesSelectionRadioMouseDown;

    function getService (row, col) {
        for (var serviceName in SERVICES_CONFIGURATION) {
            var serviceConfig = SERVICES_CONFIGURATION[serviceName];

            if (serviceConfig.row == row && serviceConfig.col == col) {
                return serviceConfig;
            }
        }
        return null;
    }
    this.getService = getService;


    function initModalServicesConfig() {

        var newIn = '<form id="services-selection-form">';

        for (var row = 1; row <= 100; row++) {

            var oneFound = false;
            for (var col = 1; col <= 3; col++) {
                var serviceConfig = getService(row, col);
                //alert (serviceConfig.name + " - " + row + " - " + col);
                if (serviceConfig != null) {
                    oneFound = true;
                }
            }
            if (!oneFound) {
                break; // done
            }

            var rowHTML =  '<div class="col-md-12"> ';
            for (var col = 1; col <= 3; col++) {

                var serviceConfig = getService (row, col);
                if (serviceConfig != null) {

                    if (serviceConfig.unique) {
                        rowHTML +=
                            '<div class="radio col-md-4 text-right no-padding">' +
                            '    <label id="'+serviceConfig.name + '-label" class="control-label">'+serviceConfig.title+'</label>'+
                            '    <label id="'+serviceConfig.name + '-icon" class="control-logo"><img class="control-logo-logo" src="' + serviceConfig.logo+'"/></label>'+
                            '    <label class="radio-inline">' +
                            '        <input  type="radio" ' +
                            '                onmousedown="javascript:eskimoMain.getServicesSelection().servicesSelectionRadioMouseDown(this);" ' +
                            '                class="input-md" name="'+serviceConfig.name +'-choice" id="'+serviceConfig.name +'-choice" value="choice"></input>' +
                            '    </label>' +
                            '</div>';
                    } else {
                        rowHTML +=
                            '<div class="checkbox col-md-4 text-right no-padding">' +
                            '    <label id="'+serviceConfig.name + '-label" class="control-label">'+serviceConfig.title+'</label>' +
                            '    <label id="'+serviceConfig.name + '-icon" class="control-logo"><img class="control-logo-logo" src="' + serviceConfig.logo+'"/></label>'+
                            '    <label class="checkbox-inline">' +
                            '        <input  type="checkbox" class="input-md" name="'+serviceConfig.name +'-choice" id="'+serviceConfig.name +'-choice"></input>' +
                            '    </label>' +
                            '</div>';
                    }
                } else {
                    rowHTML +=
                        '<div class="col-md-4 no-padding">' +
                        '</div>';
                }
            }

            rowHTML += '</div><br>';
            newIn += rowHTML;
        }

        newIn += '</form>';

        $("#services-selection-body").html($(newIn));
    }
    this.initModalServicesConfig = initModalServicesConfig;


    // call constructor
    this.initialize();
};
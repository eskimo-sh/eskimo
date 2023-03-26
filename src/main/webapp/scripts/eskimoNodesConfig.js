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
eskimo.NodesConfig = function() {

    const SERVICE_NAME_REGEX = /([a-zA-Z0-9\-_]*[a-zA-Z\-_]+)([0-9]*)/;

    // will be injected eventually from constructorObject
    this.eskimoMain = null;
    this.eskimoServicesSelection = null;
    this.eskimoServices = null;
    this.eskimoOperationsCommand = null;

    const that = this;

    const NODE_ID_FIELD = "node_id";

    // initialized by backend
    let UNIQUE_SERVICES = [];
    let MULTIPLE_SERVICES = [];
    let MANDATORY_SERVICES = [];
    let CONFIGURED_SERVICES = [];

    let SERVICES_CONFIGURATION = [];

    let SERVICES_DEPENDENCIES = [];

    let nodes = [];

    this.initialize = function() {
        // Initialize HTML Div from Template
        $("#inner-content-nodes-config").load("html/eskimoNodesConfig.html", (responseTxt, statusTxt, jqXHR) => {

            if (statusTxt === "success") {

                $("#add-more-nodes").click(e => {
                    addNode();
                    e.preventDefault();
                    return false;
                });

                $("#add-more-ranges").click(e => {
                    addRange();
                    e.preventDefault();
                    return false;
                });

                $("#save-nodes-btn").click(e => {

                    let setupConfig = $("form#nodes-config").serializeObject();

                    //console.log(setupConfig);

                    try {
                        checkNodesSetup(setupConfig, UNIQUE_SERVICES, MANDATORY_SERVICES, SERVICES_CONFIGURATION, SERVICES_DEPENDENCIES);
                        proceedWithInstallation(false, setupConfig);
                    } catch (error) {
                        eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, error);
                    }

                    e.preventDefault();
                    return false;
                });

                $("#reinstall-nodes-btn").click(e => {
                    that.eskimoServicesSelection.showServiceSelection('empty', onServicesSelectedForReinstallation)
                    e.preventDefault();
                    return false;
                });

                $("#reset-nodes-config").click(e => {
                    showNodesConfig()
                    e.preventDefault();
                    return false;
                });

                loadConfigServices();

                loadServiceDependencies();

            } else if (statusTxt === "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }

        });
    };

    function loadServiceDependencies() {
        $.ajaxGet({
            url: "get-services-dependencies",
            success: (data, status, jqXHR) => {

                if (data.status == "OK") {

                    SERVICES_DEPENDENCIES = data.servicesDependencies;

                } else {
                    eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error);
                }
            },
            error: errorHandler
        });
    }

    function loadConfigServices() {
        $.ajaxGet({
            url: "list-config-services",
            success: (data, status, jqXHR) => {

                if (data.status == "OK") {

                    UNIQUE_SERVICES = data.uniqueServices;
                    MULTIPLE_SERVICES = data.multipleServices;
                    MANDATORY_SERVICES = data.mandatoryServices;
                    CONFIGURED_SERVICES = UNIQUE_SERVICES.concat(MULTIPLE_SERVICES);

                    SERVICES_CONFIGURATION = data.servicesConfigurations;

                    that.eskimoServices.initialize();

                } else {
                    eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error);
                }
            },
            error: errorHandler
        });
    }

    function getServiceLogoPath(service) {
        let serviceConfig = SERVICES_CONFIGURATION[service];
        if (serviceConfig == null) {
            console.error ("Could not find logo for service " + service);
            return "undefined";
        }
        return serviceConfig.logo;
    }
    this.getServiceLogoPath = getServiceLogoPath;

    function getServiceIconPath(service) {
        let serviceConfig = SERVICES_CONFIGURATION[service];
        if (serviceConfig == null) {
            console.error ("Could not find icon for service " + service);
            return "undefined";
        }
        return serviceConfig.icon;
    }
    this.getServiceIconPath = getServiceIconPath;

    this.isServiceUnique = function (service) {
        let serviceConfig = SERVICES_CONFIGURATION[service];
        if (serviceConfig == null) {
            console.error ("Could not definition for service " + service);
            return false;
        }
        return serviceConfig.unique;
    };

    this.getConfiguredServices = function() {
        return CONFIGURED_SERVICES;
    };

    this.getNodesCount = function() {
        return !nodes ? 0 : nodes.length;
    };

    this.getServicesDependencies = function() {
        return SERVICES_DEPENDENCIES;
    };

    this.renderNodesConfig = function (data) {

        // render nodes and range containers
        let nodeIds = [];
        for (let serviceConfig in data) {
            //console.log(attr);
            if (serviceConfig.indexOf(NODE_ID_FIELD) > -1) {
                nodeIds.push(serviceConfig);
            }
        }
        nodeIds.sort();
        //console.log(nodeIds);
        for (let i = 0; i < nodeIds.length; i++) {
            let node = data[nodeIds[i]];
            if (node.indexOf("-") > -1) { // range
                addRange()
            } else {
                addNode();
            }
        }

        // then empty service placeholders
        for (let serviceConfig in data) {

            if (serviceConfig.indexOf(NODE_ID_FIELD) > -1) {
                $("#" + serviceConfig).val(data[serviceConfig]);

                let match = serviceConfig.match(SERVICE_NAME_REGEX);

                let nbr = match[2];

                let field = $("#field" + nbr);
                let placeHolderMs = field.find(".configured-multiple-services-placeholder");
                placeHolderMs.html("");

                let placeHolderUs = field.find(".configured-unique-services-placeholder");
                placeHolderUs.html("");
            }
        }

        // finally fill them up !
        for (let serviceConfig in data) {

            if (serviceConfig.indexOf(NODE_ID_FIELD) == -1) {

                let match = serviceConfig.match(SERVICE_NAME_REGEX);

                let serviceName = null;
                let nbr = -1;
                if (match[2] != null && match[2] != "") {
                    nbr = parseInt(match[2]);
                    serviceName = match[1]

                    let placeHolder = $("#field" + nbr).find(".configured-multiple-services-placeholder");
                    placeHolder.html(placeHolder.html() +
                        '<div class="nodes-config-entry">' +
                        '     <img class="nodes-config-logo" alt="' + serviceName + '_logo" src="' + getServiceLogoPath(serviceName) + '" />' +
                        serviceName +
                        '     <br>' +
                        '</div>');

                } else {
                    nbr = data[serviceConfig];
                    serviceName = serviceConfig;

                    let placeHolder = $("#field" + nbr).find(".configured-unique-services-placeholder");
                    placeHolder.html(placeHolder.html() +
                        '<div class="nodes-config-entry">' +
                        '     <img class="nodes-config-logo" alt="' + serviceName + '_logo" src="' + getServiceLogoPath(serviceName) + '" />' +
                        serviceName +
                        '     <br>' +
                        '</div>');
                }

                //console.log ('#'+serviceName+nbr);
                if ($('#' + serviceName + nbr).get(0) == null) {
                    throw "No node found for service name " + serviceName + " and node nbr " + nbr;
                }
                $('#' + serviceName + nbr).get(0).checked = true;

            }
        }
    };

    function showNodesConfig () {

        if (!that.eskimoMain.isSetupDone()) {
            that.eskimoMain.showSetupNotDone("Cannot configure nodes as long as initial setup is not completed");
            return;
        }

        if (that.eskimoMain.isOperationInProgress()) {
            that.eskimoMain.showProgressbar();
        }

        $.ajaxGet({
            url: "load-nodes-config",
            success: (data, status, jqXHR) => {

                console.log (data);

                const $nodesPlaceholder = $("#nodes-placeholder");

                $nodesPlaceholder.html("");
                nodes = [];

                if (!data.clear) {

                    that.renderNodesConfig(data);

                } else if (data.clear == "missing") {
                    $nodesPlaceholder.html(''+
                        '<div class="col-lg-4 col-md-6 col-sm-8 col-xs-12">\n' +
                        '    <address>(No nodes / services configured yet)</address>\n' +
                        '</div>');

                } else if (data.clear == "setup"){

                    that.eskimoMain.handleSetupNotCompleted();

                }
            },
            error: errorHandler
        });

        that.eskimoMain.showOnlyContent("nodes-config");
    }
    this.showNodesConfig = showNodesConfig;

    function onServicesSelectedForReinstallation (model) {
        console.log (model);

        proceedWithInstallation(true, model);
    }
    this.onServicesSelectedForReinstallation = onServicesSelectedForReinstallation;

    function onServicesSelectedForNode (model, nodeNbr) {
        console.log (nodeNbr, model);

        // clear all boxes
        for (let i = 0; i < CONFIGURED_SERVICES.length; i++) {
            $('#'+CONFIGURED_SERVICES[i]+nodeNbr).get(0).checked = false;
        }

        for (let key in model) {
            let match = key.match(SERVICE_NAME_REGEX);

            let serviceName = null;
            if (match[2] != null && match[2] != "") {
                serviceName = match[1]

            } else {
                serviceName = key;
            }

            $('#'+serviceName+nodeNbr).get(0).checked = true;
        }

        //console.log (nodes.length);
        for (let i = 1; i <= nodes.length; i++) {

            let field = $("#field" + i);
            let placeHolderMs = field.find(".configured-multiple-services-placeholder");
            placeHolderMs.html("");

            let placeHolderUs = field.find(".configured-unique-services-placeholder");
            placeHolderUs.html("");

            for (let j = 0; j < UNIQUE_SERVICES.length; j++) {
                let effServiceName = UNIQUE_SERVICES[j];
                const $effService = $('#' + effServiceName + i);
                if ($effService.length) {
                    if ($effService.get(0).checked) {
                        placeHolderUs.html(placeHolderUs.html() +
                            '<div class="nodes-config-entry">' +
                            '     <img class="nodes-config-logo"  alt="' + effServiceName + '_logo" src="' + getServiceLogoPath(effServiceName) + '" />'+
                            effServiceName +
                            '     <br>' +
                            '</div>');

                    }
                }
            }

            for (let j = 0; j < MULTIPLE_SERVICES.length; j++) {
                let effServiceName = MULTIPLE_SERVICES[j];
                const $effService = $('#' + effServiceName + i);
                if ($effService.length) {
                    if ($effService.get(0).checked) {
                        placeHolderMs.html(placeHolderMs.html() +
                            '<div class="nodes-config-entry">' +
                            '    <img class="nodes-config-logo" alt="' + effServiceName + '_logo" src="' + getServiceLogoPath(effServiceName) + '" />'+
                            effServiceName +
                            '    <br>' +
                            '</div>');
                    }
                }
            }
        }
    }
    this.onServicesSelectedForNode = onServicesSelectedForNode;

    this.checkNodesSetup = checkNodesSetup;

    function removeNode (removeId) {
        let fieldNum = removeId.substring(6);

        console.log ("  - splicing nodes with " + removeId + " - " + fieldNum);
        nodes.splice(fieldNum - 1, 1);

        for (let i = fieldNum - 1; i < nodes.length; i++) {
            console.log("  - shiffting field  " + i);
            $(nodes[i]["field"]).attr("name", "field" + (i + 1));
            $(nodes[i]["field"]).attr("id", "field" + (i + 1));
            $(nodes[i]["input"]).attr("name", NODE_ID_FIELD + (i + 1));
            $(nodes[i]["input"]).attr("id", NODE_ID_FIELD + (i + 1));
            $(nodes[i]["configure"]).attr("id", "configure" + (i + 1));
            $(nodes[i]["remove"]).attr("id", "remove" + (i + 1));
            $(nodes[i]["label"]).html(getNodeTitle(nodes[i]["type"] == "range") + '<div class="server-title-text">' + (i + 1) + '</div>');
            $(nodes[i]["label"]).attr("id", "label" + (i + 1));
            for (let j = 0; j < UNIQUE_SERVICES.length; j++) {

                $(nodes[i][UNIQUE_SERVICES[j]]).attr("value", (i + 1));

                // keep this last
                $(nodes[i][UNIQUE_SERVICES[j]]).attr("id", UNIQUE_SERVICES[j] + (i + 1));
            }

            for (let j = 0; j < MULTIPLE_SERVICES.length; j++) {

                // need to rewrite name as well for multiple services
                $(nodes[i][MULTIPLE_SERVICES[j]]).attr("name", MULTIPLE_SERVICES[j] + (i + 1));

                // keep this last
                $(nodes[i][MULTIPLE_SERVICES[j]]).attr("id", MULTIPLE_SERVICES[j] + (i + 1));

            }

            nodes[i]["field"] = "#field"+(i + 1);
            nodes[i]["input"] = "#"+NODE_ID_FIELD+(i + 1);
            nodes[i]["configure"] = "#configure"+(i + 1);
            nodes[i]["remove"] = "#remove"+(i + 1);
            nodes[i]["label"] = "#label"+(i + 1);
            for (let j = 0; j < CONFIGURED_SERVICES.length; j++) {
                nodes[i][CONFIGURED_SERVICES[j]] = "#" + CONFIGURED_SERVICES[j] + (i + 1);
            }
        }

        let fieldID = "#field" + fieldNum;
        $(this).remove();
        $(fieldID).remove();
    }
    this.removeNode = removeNode;

    function addNode () {
        addNewElement(false);
    }
    /* For tests */
    this.addNode = addNode;

    function addRange () {
        addNewElement(true);
    }

    function getNodeTitle(isRange) {
        if (isRange) {
            return ''+
                '<div class="server-title-icon">' +
                '    <img class="server-icon" alt="range-selection-icon"  src="images/range-icon.png" /> ' +
                '</div>' +
                '<div class="server-title-text">&nbsp;Range no&nbsp;</div>';
        } else {
            return ''+
                '<div class="server-title-icon">' +
                '    <img class="server-icon" alt="single-selection-icon" src="images/node-icon.png" /> ' +
                '</div>' +
                '<div class="server-title-text">&nbsp;Node no&nbsp;</div>';
        }
    }

    function showServiceSelection(e) {

        let configureButtonId = $(e.target).attr("id");
        let nodeNbr = configureButtonId.substring("configure".length);

        let isRange = $(e.target).data("is-range");

        //console.log (nodeNbr + " - " + isRange);

        that.eskimoServicesSelection.showServiceSelection(nodeNbr, onServicesSelectedForNode, isRange == "true");
    }
    this.showServiceSelection = showServiceSelection;

    function addNewElement (isRange) {

        const $nodesPlaceholder = $("#nodes-placeholder");
        if (nodes.length === 0) {
            // remove placeholder
            $nodesPlaceholder.html('');
        }

        let next = nodes.length + 1;
        //console.log ("Node node ID : " + next);

        let uniqueServicesDiv = '' +
            '<div class="col-md-3 configured-unique-services-placeholder"></div>' +
            '<div style="visibility: hidden; display: none;">';

        for (let i = 0; i < UNIQUE_SERVICES.length; i++) {

            let uniqueService = UNIQUE_SERVICES[i];

            uniqueServicesDiv +='  <input  type="radio" class="form-check-input" name="' + uniqueService + '" id="' + uniqueService + next + '" value="'+next+'" />';
        }

        uniqueServicesDiv += "</div>";

        let multipleServicesDiv = '' +
            '<div class="col-md-3 configured-multiple-services-placeholder"></div>' +
            '<div style="visibility: hidden; display: none;">';

        for (let i = 0; i < MULTIPLE_SERVICES.length; i++) {

            let multipleService = MULTIPLE_SERVICES[i];

            multipleServicesDiv +=' <input  type="checkbox" class="form-check-input" name="' + multipleService + next + '" id="' + multipleService + next + '" />'
        }

        multipleServicesDiv = multipleServicesDiv + "</div>";

        let newIn = ' '+
            '<div id="field'+ next +'" class="input-group row col-md-12 node-config-element" >'+
            '    <div class="row node-config-element-wrapper"> '+
            '        <label class="col-md-3 control-label" id="label'+next+'">'+getNodeTitle(isRange)+' <div class="server-title-text">' + next + '</div></label> '+
            '        <div class="col-md-6"> '+
            '            <input id="'+NODE_ID_FIELD + next+'" name="'+NODE_ID_FIELD+next+'" type="text" placeholder="'+
                                (isRange ? 'IP addresses range, e.g 192.168.1.10-192.168.1.25' : 'IP address, e.g. 192.168.10.10')+
            '                   " class="form-control"> '+
            '        </div>'+
            '        <div class="btn-toolbar col-md-3">'+
            '            <div class="btn-group">'+
            '                <button data-is-range="' + isRange + '" id="configure' + next + '" class="btn btn-info ms-2">Configure</button>'+
            '            </div>'+
            '            <div class="btn-group">'+
            '                <button id="remove' + next + '" class="btn btn-danger ms-2 remove-me" >Remove</button>'+
            '            </div>'+
            '        </div>'+
            '    </div><br>'+
            '    <div class="row"> '+
            '        <div class="col-md-1"></div> '+
            '        <label class="col-md-2">Installed Services:</label> '+
             uniqueServicesDiv+
             multipleServicesDiv+
            '    </div><br><br>'+
            '</div>';

        $nodesPlaceholder.append(newIn);

        nodes[next-1] = {};
        nodes[next-1]["type"] = isRange ? "range" : "node";
        nodes[next-1]["field"] = "#field"+next;
        nodes[next-1]["input"] = "#"+NODE_ID_FIELD+next;
        nodes[next-1]["remove"] = "#remove"+next;
        nodes[next-1]["configure"] = "#configure"+next;
        nodes[next-1]["label"] = "#label"+next;

        for (let j = 0; j < CONFIGURED_SERVICES.length; j++) {
            nodes[next - 1][CONFIGURED_SERVICES[j]] = "#" + CONFIGURED_SERVICES[j] + next;
        }

        $('#remove'+next).click(function(e){
            removeNode (this.id);
            e.stopPropagation();
            e.preventDefault();
            return false;
        });

        $('#configure'+next).click(e => {
            showServiceSelection (e);
            e.stopPropagation();
            e.preventDefault();
            return false;
        });
    }

    function proceedWithInstallation(reinstall, model) {

        that.eskimoMain.showProgressbar();

        // 1 hour timeout
        $.ajaxPost({
            timeout: 1000 * 120,
            url: reinstall ? "reinstall-nodes-config" : "save-nodes-config",
            data: JSON.stringify(model),
            success: (data, status, jqXHR) => {

                that.eskimoMain.hideProgressbar();

                //console.log(data);

                if (!data || data.error) {
                    console.error(atob(data.error));
                    eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, atob(data.error));
                } else {

                    if (!data.command) {
                        eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, "Expected pending operations command but got none !");
                    } else {
                        that.eskimoOperationsCommand.showCommand (data.command);
                    }
                }
            },

            error: (jqXHR, status) => {
                that.eskimoMain.hideProgressbar();
                errorHandler (jqXHR, status);
            }
        });
    }
};

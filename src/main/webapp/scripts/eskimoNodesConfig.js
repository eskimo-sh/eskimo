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
eskimo.NodesConfig = function(constructorObject) {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;
    this.eskimoServicesSelection = null;
    this.eskimoServices = null;
    this.eskimoOperationsCommand = null;

    var that = this;

    var NODE_ID_FIELD = "node_id";

    // initialized by backend
    var UNIQUE_SERVICES = [];
    var MULTIPLE_SERVICES = [];
    var MANDATORY_SERVICES = [];
    var CONFIGURED_SERVICES = [];

    var SERVICES_CONFIGURATION = [];

    var SERVICES_DEPENDENCIES = [];

    var nodes = [];

    this.initialize = function() {
        // Initialize HTML Div from Template
        $("#inner-content-nodes").load("html/eskimoNodesConfig.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt == "success") {

                $("#add-more-nodes").click(function (e) {
                    addNode();
                    e.preventDefault();
                    return false;
                });

                $("#add-more-ranges").click(function (e) {
                    addRange();
                    e.preventDefault();
                    return false;
                });

                $("#save-nodes-btn").click(function (e) {

                    var setupConfig = $("form#nodes-config").serializeObject();

                    console.log(setupConfig);

                    try {
                        checkNodesSetup(setupConfig, UNIQUE_SERVICES, MANDATORY_SERVICES, SERVICES_CONFIGURATION, SERVICES_DEPENDENCIES);
                        proceedWithInstallation(false, setupConfig);
                    } catch (error) {
                        alert (error);
                    }

                    e.preventDefault();
                    return false;
                });

                $("#reinstall-nodes-btn").click(function (e) {
                    that.eskimoServicesSelection.showServiceSelection('empty', onServicesSelectedForReinstallation)
                    e.preventDefault();
                    return false;
                });

                $("#reset-nodes-config").click(function (e) {
                    showNodesConfig()
                    e.preventDefault();
                    return false;
                });

                loadConfigServices();

                loadServiceDependencies();

            } else if (statusTxt == "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }

        });
    };

    function loadServiceDependencies() {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "get-services-dependencies",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    SERVICES_DEPENDENCIES = data.servicesDependencies;

                } else {
                    alert(data.error);
                }
            },
            error: errorHandler
        });
    }

    function loadConfigServices() {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "list-config-services",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    UNIQUE_SERVICES = data.uniqueServices;
                    MULTIPLE_SERVICES = data.multipleServices;
                    MANDATORY_SERVICES = data.mandatoryServices;
                    CONFIGURED_SERVICES = UNIQUE_SERVICES.concat(MULTIPLE_SERVICES);

                    SERVICES_CONFIGURATION = data.servicesConfigurations;

                    that.eskimoServices.initialize();

                } else {
                    alert(data.error);
                }
            },
            error: errorHandler
        });
    }

    function getServiceLogoPath(service) {
        var serviceConfig = SERVICES_CONFIGURATION[service];
        if (serviceConfig == null) {
            console.error ("Could not find logo for service " + service);
            return "undefined";
        }
        return serviceConfig.logo;
    }
    this.getServiceLogoPath = getServiceLogoPath;

    function getServiceIconPath(service) {
        var serviceConfig = SERVICES_CONFIGURATION[service];
        if (serviceConfig == null) {
            console.error ("Could not find icon for service " + service);
            return "undefined";
        }
        return serviceConfig.icon;
    }
    this.getServiceIconPath = getServiceIconPath;

    this.isServiceUnique = function (service) {
        var serviceConfig = SERVICES_CONFIGURATION[service];
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
        return !nodes || nodes == null ? 0 : nodes.length;
    };

    this.setServicesDependenciesForTest = function(testServiceDeps) {
        SERVICES_DEPENDENCIES = testServiceDeps;
    };
    this.getServicesDependencies = function() {
        return SERVICES_DEPENDENCIES;
    };

    this.setServicesConfigForTest = function (uniqueServices, multiplesServices, configuredServices, mandatoryServices) {
        UNIQUE_SERVICES = uniqueServices;
        MULTIPLE_SERVICES = multiplesServices;
        CONFIGURED_SERVICES = configuredServices;
        MANDATORY_SERVICES = mandatoryServices;
    };

    this.setServicesConfig = function (servicesConfig) {
        SERVICES_CONFIGURATION = servicesConfig;
    };

    this.renderNodesConfig = function (data) {

        var re = /([a-zA-Z\-_]+)([0-9]*)/;

        // render nodes and range containers
        var nodeIds = [];
        for (var serviceConfig in data) {
            //console.log(attr);
            if (serviceConfig.indexOf(NODE_ID_FIELD) > -1) {
                nodeIds.push(serviceConfig);
            }
        }
        nodeIds.sort();
        console.log(nodeIds);
        for (var i = 0; i < nodeIds.length; i++) {
            var ipAddress = data[nodeIds[i]];
            if (ipAddress.indexOf("-") > -1) { // range
                addRange()
            } else {
                addNode();
            }
        }

        // then empty service placeholders
        for (var serviceConfig in data) {

            if (serviceConfig.indexOf(NODE_ID_FIELD) > -1) {
                $("#" + serviceConfig).val(data[serviceConfig]);

                var match = serviceConfig.match(re);

                var nbr = match[2];

                var placeHolderMs = $("#field" + nbr).find(".configured-multiple-services-placeholder");
                placeHolderMs.html("");

                var placeHolderUs = $("#field" + nbr).find(".configured-unique-services-placeholder");
                placeHolderUs.html("");
            }
        }

        // finally fill them up !
        for (var serviceConfig in data) {

            if (serviceConfig.indexOf(NODE_ID_FIELD) == -1) {

                var match = serviceConfig.match(re);

                var serviceName = null;
                var nbr = -1;
                if (match[2] != null && match[2] != "") {
                    nbr = parseInt(match[2]);
                    serviceName = match[1]

                    var placeHolder = $("#field" + nbr).find(".configured-multiple-services-placeholder");
                    placeHolder.html(placeHolder.html() +
                        '<div class="nodes-config-entry">' +
                        '<img class="nodes-config-logo" src="' + getServiceLogoPath(serviceName) + '" />' +
                        serviceName +
                        '<br>' +
                        '</div>');

                } else {
                    nbr = data[serviceConfig];
                    serviceName = serviceConfig;

                    var placeHolder = $("#field" + nbr).find(".configured-unique-services-placeholder");
                    placeHolder.html(placeHolder.html() +
                        '<div class="nodes-config-entry">' +
                        '<img class="nodes-config-logo" src="' + getServiceLogoPath(serviceName) + '" />' +
                        serviceName +
                        '<br>' +
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

        $.ajax({
            type: "GET",
            dataType: "json",
            url: "load-nodes-config",
            success: function (data, status, jqXHR) {

                console.log (data);

                $("#nodes-placeholder").html("");
                nodes = [];

                if (!data.clear) {

                    that.renderNodesConfig(data);

                } else if (data.clear == "missing") {
                    $("#nodes-placeholder").html(''+
                        '<div class="col-lg-4 col-md-6 col-sm-8 col-xs-12">\n' +
                        '    <address>(No nodes / services configured yet)</address>\n' +
                        '</div>');

                } else if (data.clear == "setup"){

                    that.eskimoMain.handleSetupNotCompleted();

                }

                //alert(data);
            },
            error: errorHandler
        });

        that.eskimoMain.showOnlyContent("nodes");
    }
    this.showNodesConfig = showNodesConfig;

    function onServicesSelectedForReinstallation (model) {
        console.log (model);

        proceedWithInstallation(true, model);
    }
    this.onServicesSelectedForReinstallation = onServicesSelectedForReinstallation;

    function onServicesSelectedForNode (model, nodeNbr) {
        console.log (nodeNbr, model);

        var re = /([a-zA-Z\-_]+)([0-9]*)/;

        // clear all boxes
        for (var i = 0; i < CONFIGURED_SERVICES.length; i++) {
            $('#'+CONFIGURED_SERVICES[i]+nodeNbr).get(0).checked = false;
        }

        for (var key in model) {
            var match = key.match(re);

            var serviceName = null;
            if (match[2] != null && match[2] != "") {
                serviceName = match[1]

            } else {
                serviceName = key;
            }

            $('#'+serviceName+nodeNbr).get(0).checked = true;
        }

        //console.log (nodes.length);
        for (var i = 1; i <= nodes.length; i++) {

            var field = $("#field" + i);
            var placeHolderMs = field.find(".configured-multiple-services-placeholder");
            placeHolderMs.html("");

            var placeHolderUs = field.find(".configured-unique-services-placeholder");
            placeHolderUs.html("");

            for (var j = 0; j < UNIQUE_SERVICES.length; j++) {

                var effServiceName = UNIQUE_SERVICES[j];
                if ($('#' + effServiceName + i).length) {
                    if ($('#' + effServiceName + i).get(0).checked) {
                        placeHolderUs.html(placeHolderUs.html() +
                            '<div class="nodes-config-entry">' +
                            '<img class="nodes-config-logo" src="' + getServiceLogoPath(effServiceName) + '" />'+
                            effServiceName +
                            '<br>' +
                            '</div>');

                    }
                }
            }

            for (var j = 0; j < MULTIPLE_SERVICES.length; j++) {
                var effServiceName = MULTIPLE_SERVICES[j];
                if ($('#'+effServiceName+i).length) {
                    if ($('#'+effServiceName+i).get(0).checked) {
                        placeHolderMs.html(placeHolderMs.html() +
                            '<div class="nodes-config-entry">' +
                            '<img class="nodes-config-logo" src="' + getServiceLogoPath(effServiceName) + '" />'+
                            effServiceName +
                            '<br>' +
                            '</div>');
                    }
                }
            }
        }
    }
    this.onServicesSelectedForNode = onServicesSelectedForNode;

    this.checkNodesSetup = checkNodesSetup;

    function removeNode (removeId) {
        var fieldNum = removeId.substring(6);

        console.log ("  - splicing nodes with " + removeId + " - " + fieldNum);
        nodes.splice(fieldNum - 1, 1);

        for (var i = fieldNum - 1; i < nodes.length; i++) {
            console.log("  - shiffting field  " + i);
            $(nodes[i]["field"]).attr("name", "field" + (i + 1));
            $(nodes[i]["field"]).attr("id", "field" + (i + 1));
            $(nodes[i]["input"]).attr("name", NODE_ID_FIELD + (i + 1));
            $(nodes[i]["input"]).attr("id", NODE_ID_FIELD + (i + 1));
            $(nodes[i]["configure"]).attr("id", "configure" + (i + 1));
            $(nodes[i]["remove"]).attr("id", "remove" + (i + 1));
            $(nodes[i]["label"]).html(getNodeTitle(nodes[i]["type"] == "range") + '<div class="server-title-text">' + (i + 1) + '</div>');
            $(nodes[i]["label"]).attr("id", "label" + (i + 1));
            for (var j = 0; j < UNIQUE_SERVICES.length; j++) {

                $(nodes[i][UNIQUE_SERVICES[j]]).attr("value", (i + 1));

                // keep this last
                $(nodes[i][UNIQUE_SERVICES[j]]).attr("id", UNIQUE_SERVICES[j] + (i + 1));
            }

            for (var j = 0; j < MULTIPLE_SERVICES.length; j++) {

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
            for (var j = 0; j < CONFIGURED_SERVICES.length; j++) {
                nodes[i][CONFIGURED_SERVICES[j]] = "#" + CONFIGURED_SERVICES[j] + (i + 1);
            }
        }

        var fieldID = "#field" + fieldNum;
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
                '    <img class="server-icon" src="images/range-icon.png" /> ' +
                '</div>' +
                '<div class="server-title-text">&nbsp;Range no&nbsp;</div>';
        } else {
            return ''+
                '<div class="server-title-icon">' +
                '    <img class="server-icon" src="images/node-icon.png" /> ' +
                '</div>' +
                '<div class="server-title-text">&nbsp;Node no&nbsp;</div>';
        }
    }

    function showServiceSelection(e) {

        var configureButtonId = $(e.target).attr("id");
        var nodeNbr = configureButtonId.substring("configure".length);

        var isRange = $(e.target).data("is-range");

        //console.log (nodeNbr + " - " + isRange);

        that.eskimoServicesSelection.showServiceSelection(nodeNbr, onServicesSelectedForNode, isRange == "true");
    }
    this.showServiceSelection = showServiceSelection;

    function addNewElement (isRange) {

        if (nodes.length == 0) {
            // remove placeholder
            $("#nodes-placeholder").html('');
        }

        var next = nodes.length + 1;
        console.log ("Node node ID : " + next);

        var uniqueServicesDiv = '' +
            '<div class="col-md-3 configured-unique-services-placeholder"></div>' +
            '<div style="visibility: hidden; display: none;">';

        for (var i = 0; i < UNIQUE_SERVICES.length; i++) {

            var uniqueService = UNIQUE_SERVICES[i];

            uniqueServicesDiv +='  <input  type="radio" class="input-md" name="' + uniqueService + '" id="' + uniqueService + next + '" value="'+next+'"></input>';
        }

        uniqueServicesDiv += "</div>";

        var multipleServicesDiv = '' +
            '<div class="col-md-3 configured-multiple-services-placeholder"></div>' +
            '<div style="visibility: hidden; display: none;">';

        for (var i = 0; i < MULTIPLE_SERVICES.length; i++) {

            var multipleService = MULTIPLE_SERVICES[i];

            multipleServicesDiv +=' <input  type="checkbox" class="input-md" name="' + multipleService + next + '" id="' + multipleService + next + '" ></input>'
        }

        multipleServicesDiv = multipleServicesDiv + "</div>";

        var newIn = ' '+
            '<div id="field'+ next +'" class="form-group col-md-12 node-config-element" >'+
            '    <div class="col-md-12 node-config-element-wrapper"> '+
            '        <label class="col-md-3 control-label" id="label'+next+'">'+getNodeTitle(isRange)+' <div class="server-title-text">' + next + '</div></label> '+
            '        <div class="col-md-6"> '+
            '            <input id="'+NODE_ID_FIELD + next+'" name="'+NODE_ID_FIELD+next+'" type="text" placeholder="'+
                                (isRange ? 'IP addresses range, e.g 192.168.1.10-192.168.1.25' : 'IP address, e.g. 192.168.10.10')+
            '                   " class="form-control input-md"> '+
            '        </div>'+
            '        <div class="btn-toolbar col-md-3">'+
            '            <div class="btn-group">'+
            '                <button data-is-range="' + isRange + '" id="configure' + next + '" class="btn btn-primary">Configure</button>'+
            '            </div>'+
            '            <div class="btn-group">'+
            '                <button id="remove' + next + '" class="btn btn-danger remove-me" >Remove</button>'+
            '            </div>'+
            '        </div>'+
            '    </div><br>'+
            '    <div class="col-md-12"> '+
            '        <div class="col-md-1"></div> '+
            '        <label class="col-md-2">Installed Services:</label> '+
             uniqueServicesDiv+
             multipleServicesDiv+
            '    </div><br><br>'+
            '</div>';

        $("#nodes-placeholder").append(newIn);

        nodes[next-1] = new Object();
        nodes[next-1]["type"] = isRange ? "range" : "node";
        nodes[next-1]["field"] = "#field"+next;
        nodes[next-1]["input"] = "#"+NODE_ID_FIELD+next;
        nodes[next-1]["remove"] = "#remove"+next;
        nodes[next-1]["configure"] = "#configure"+next;
        nodes[next-1]["label"] = "#label"+next;

        for (var j = 0; j < CONFIGURED_SERVICES.length; j++) {
            nodes[next - 1][CONFIGURED_SERVICES[j]] = "#" + CONFIGURED_SERVICES[j] + next;
        }

        $('#remove'+next).click(function(e){
            removeNode (this.id);
            e.stopPropagation();
            e.preventDefault();
            return false;
        });

        $('#configure'+next).click(function(e){
            showServiceSelection (e);
            e.stopPropagation();
            e.preventDefault();
            return false;
        });
    }

    function proceedWithInstallation(reinstall, model) {

        that.eskimoMain.showProgressbar();

        // 1 hour timeout
        $.ajax({
            type: "POST",
            dataType: "json",
            timeout: 1000 * 120,
            contentType: "application/json; charset=utf-8",
            url: reinstall ? "reinstall-nodes-config" : "save-nodes-config",
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
                        that.eskimoOperationsCommand.showCommand (data.command);
                    }
                }
            },

            error: function (jqXHR, status) {
                that.eskimoMain.hideProgressbar();
                errorHandler (jqXHR, status);
            }
        });
    }


    // inject constructor object in the end
    if (constructorObject != null) {
        $.extend(this, constructorObject);
    }

    // call constructor
    this.initialize();
};

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
eskimo.SystemStatus = function() {

    var that = this;

    // constants
    var STATUS_UPDATE_INTERVAL = 4000;

    // initialized by backend
    var STATUS_SERVICES = [];
    var SERVICES_STATUS_CONFIG = {};

    var renderInTable = true;

    var statusUpdateTimeoutHandler = null;

    var prevHidingMessageTimeout = null;

    var monitoringDashboardFrameTamperTimeout = null;

    this.initialize = function () {
        // Initialize HTML Div from Template
        $("#inner-content-status").load("html/eskimoSystemStatus.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt == "success") {

                loadUIStatusServicesConfig();

                $('#show-machine-view-btn').click($.proxy (function () {
                    setRenderInTable (false);
                    showStatus(true);
                }, this));

                $('#show-table-view-btn').click($.proxy (function () {
                    setRenderInTable (true);
                    showStatus(true);
                }, this));

            } else if (statusTxt == "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });
    };

    function loadUIStatusServicesConfig() {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "get-ui-services-status-config",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    SERVICES_STATUS_CONFIG = data.uiServicesStatusConfig;

                } else {
                    alert(data.error);
                }

                loadListServices();
            },
            error: errorHandler
        });
    }

    function loadListServices () {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "list-services",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    STATUS_SERVICES = data.services;

                    eskimoMain.getSetup().loadSetup(true);

                } else {
                    alert(data.error);
                }
            },
            error: errorHandler
        });
    }

    function shouldRenderInTable () {
        return renderInTable;
    }
    this.shouldRenderInTable = shouldRenderInTable;

    function setRenderInTable (doTable) {
        renderInTable = doTable;
    }
    this.setRenderInTable = setRenderInTable;

    /** For tests */
    this.setStatusServices = function (statusServices) {
        STATUS_SERVICES = statusServices;
    };
    this.setServicesStatusConfig = function (servicesStatusConfig) {
        SERVICES_STATUS_CONFIG = servicesStatusConfig;
    };

    function showStatus (blocking) {

        if (!eskimoMain.isSetupLoaded()) {

            eskimoMain.getSetup().loadSetup();

            // retry after a ŵhile
            setTimeout ("eskimoMain.getSystemStatus().showStatus(" + (blocking ? "true" : "false") + ");", 100);

        } else {
            if (!eskimoMain.isSetupDone()) {

                showSetupNotDone(blocking ? "" : "Cannot show nodes status as long as initial setup is not completed");

                // Still initialize the status update timeer (also used for notifications)
                updateStatus(false);

            } else {

                // maybe Progress bar was shown previously and we don't show it on status page
                eskimoMain.hideProgressbar();

                eskimoMain.showOnlyContent("status");

                updateStatus(blocking);
            }
        }
    }
    this.showStatus = showStatus;

    function showMessage (message) {

        if (prevHidingMessageTimeout != null) {
            clearTimeout(prevHidingMessageTimeout);
        }

        var serviceStatusWarning = $("#service-status-warning");
        serviceStatusWarning.css("display", "block");
        serviceStatusWarning.css("visibility", "visible");

        $("#service-status-warning-message").html(message);

        prevHidingMessageTimeout = setTimeout(function () {
            serviceStatusWarning.css("display", "none");
            serviceStatusWarning.css("visibility", "hidden");
        }, 5000);

    }
    this.showMessage = showMessage;

    function showStatusWhenServiceUnavailable (service) {
        showMessage (service + " is not up and running");
    }
    this.showStatusWhenServiceUnavailable = showStatusWhenServiceUnavailable;

    function serviceAction (action, service, nodeAddress) {

        eskimoMain.getMessaging().showMessages();

        eskimoMain.startOperationInProgress();

        // 1 hour timeout
        $.ajax({
            type: "GET",
            dataType: "json",
            timeout: 1000 * 3600,
            contentType: "application/json; charset=utf-8",
            url: action + "?service=" + service + "&address=" + nodeAddress,
            success: function (data, status, jqXHR) {

                // OK
                console.log(data);
                var success = false;

                if (!data || data.error) {
                    console.error(atob(data.error));
                    eskimoMain.scheduleStopOperationInProgress (false);
                } else {
                    eskimoMain.scheduleStopOperationInProgress (true);

                    if (data.message != null) {
                        showMessage (data.message);
                    }
                }
            },

            error: function (jqXHR, status) {
                errorHandler (jqXHR, status);
                eskimoMain.scheduleStopOperationInProgress (false);
            }
        });
    }

    function showJournal (service, nodeAddress) {
        console.log("showJournal", service, nodeAddress);

        serviceAction("show-journal", service, nodeAddress);
    }
    this.showJournal = showJournal;

    function startService (service, nodeAddress) {
        console.log("startService ", service, nodeAddress);

        serviceAction("start-service", service, nodeAddress);
    }
    this.startService = startService;

    function stopService (service, nodeAddress) {
        console.log("stoptService ", service, nodeAddress);

        serviceAction("stop-service", service, nodeAddress);
    }
    this.stopService = stopService;

    function restartService (service, nodeAddress) {
        console.log("restartService ", service, nodeAddress);

        serviceAction("restart-service", service, nodeAddress);
    }
    this.restartService = restartService;

    function reinstallService (service, nodeAddress) {
        console.log("reinstallService ", service, nodeAddress);
        if (confirm ("Are you sure you want to reinstall " + service + " on " + nodeAddress + " ?")) {
            serviceAction("reinstall-service", service, nodeAddress);
        }
    }
    this.reinstallService = reinstallService;

    this.handleSystemStatus = function (nodeServicesStatus, systemStatus, blocking) {

        // A. Handle Grafana Dashboard ID display

        // A.1 Find out if grafana is available
        var grafanaAvailable = false;
        for (key in nodeServicesStatus) {
            if (key.indexOf("service_grafana_") > -1) {
                var grafanaStatus = nodeServicesStatus[key];
                if (grafanaStatus == "OK") {
                    grafanaAvailable = true;
                }
            }
        }

        var monitoringDashboardId = systemStatus.monitoringDashboardId;
        var refreshPeriod = systemStatus.monitoringDashboardRefreshPeriod;

        // no dashboard configured
        if (!grafanaAvailable || monitoringDashboardId == null || monitoringDashboardId == "" || monitoringDashboardId == "null") {

            $("#status-monitoring-no-dashboard").css("display", "inherit");
            $("#status-monitoring-dashboard-frame").css("display", "none");

            $("#status-monitoring-dashboard-frame").attr('src', "html/emptyPage.html");

        }
        // render iframe with refresh period (default 30s)
        else {

            var forceRefresh = false;
            if ($("#status-monitoring-dashboard-frame").css("display") == "none") {


                setTimeout (function() {
                    $("#status-monitoring-dashboard-frame").css("display", "inherit");
                    $("#status-monitoring-no-dashboard").css("display", "none");
                }, 500);

                forceRefresh = true;
            }


            var url = "grafana/d/" + monitoringDashboardId + "/eskimo-system-wide-monitoring?orgId=1&&kiosk&refresh="
                + (refreshPeriod == null || refreshPeriod == "" ? "30s" : refreshPeriod);

            var prevUrl = $("#status-monitoring-dashboard-frame").attr('src');
            if (prevUrl == null || prevUrl == "" || prevUrl != url || forceRefresh) {
                $("#status-monitoring-dashboard-frame").attr('src', url);

                setTimeout (that.monitoringDashboardFrameTamper, 5000);

            }
        }

        // B. Inject information

        $("#system-information-version").html(systemStatus.buildVersion);

        $("#system-information-timestamp").html(systemStatus.buildTimestamp);

        $("#system-information-user").html(systemStatus.sshUsername);

        $("#system-information-start-timestamp").html (systemStatus.startTimestamp);
    };

    this.monitoringDashboardFrameTamper = function() {
        // remove widgets menus from iframe DOM
        $("#status-monitoring-dashboard-frame").contents().find(".panel-menu").remove();
        setTimeout (that.monitoringDashboardFrameTamper, 10000);
    };

    this.renderNodesStatus = function (nodeServicesStatus, blocking) {

        var nodeNamesByNbr = [];

        eskimoMain.handleSetupCompleted();

        var availableNodes = [];

        // loop on node nbrs and get Node Name + create table row
        for (key in nodeServicesStatus) {
            if (key.indexOf("node_nbr_") > -1) {
                var nodeName = key.substring(9);
                var nbr = nodeServicesStatus[key];
                nodeNamesByNbr [parseInt(nbr)] = nodeName;
            }
        }

        for (var nbr = 1; nbr < nodeNamesByNbr.length; nbr++) { // 0 is empty

            var nodeName = nodeNamesByNbr[nbr];

            var nodeAddress = nodeServicesStatus["node_address_" + nodeName];
            var nodeAlive = nodeServicesStatus["node_alive_" + nodeName];

            // if at least one node is up, show the consoles menu
            if (nodeAlive == 'OK') {

                // Show SFTP and Terminal Menu entries
                $("#folderMenuConsoles").attr("class", "folder-menu-items");
                $("#folderMenuFileManagers").attr("class", "folder-menu-items");

                availableNodes.push({"nbr": nbr, "nodeName": nodeName, "nodeAddress": nodeAddress});
            }

            for (var sNb = 0; sNb < STATUS_SERVICES.length; sNb++) {
                var service = STATUS_SERVICES[sNb];
                if (nodeAlive == 'OK') {

                    var serviceStatus = nodeServicesStatus["service_" + service + "_" + nodeName];

                    if (serviceStatus) {

                        if (serviceStatus == "NA" || serviceStatus == "KO") {

                            eskimoMain.getServices().serviceMenuServiceFoundHook(nodeName, nodeAddress, service, false, blocking);

                        } else if (serviceStatus == "OK") {

                            eskimoMain.getServices().serviceMenuServiceFoundHook(nodeName, nodeAddress, service, true, blocking);
                        }
                    }
                }
            }
        }

        if (nodeNamesByNbr.length == 0) {

            this.renderNodesStatusEmpty();

        } else {

            if (this.shouldRenderInTable()) {

                this.renderNodesStatusTable(nodeServicesStatus, blocking, availableNodes, nodeNamesByNbr);

            } else {

                this.renderNodesStatusCarousel(nodeServicesStatus, blocking, availableNodes, nodeNamesByNbr);
            }
        }

        eskimoMain.setAvailableNodes(availableNodes);
    };

    this.renderNodesStatusEmpty = function() {

        var statusRenderOptions = $(".status-render-options");
        statusRenderOptions.css("visibility", "hidden");
        statusRenderOptions.css("display", "none");

        var statusContainerEmpty = $("#status-node-container-empty");
        statusContainerEmpty.css("visibility", "inherit");
        statusContainerEmpty.css("display", "inherit");
    };

    this.renderNodesStatusCarousel = function (data, blocking, availableNodes, nodeNamesByNbr) {

        var statusRenderOptions = $(".status-render-options");
        statusRenderOptions.css("visibility", "hidden");
        statusRenderOptions.css("display", "none");

        var statusContainerCarousel = $("#status-node-container-carousel");
        statusContainerCarousel.css("visibility", "inherit");
        statusContainerCarousel.css("display", "inherit");

        var carouselContent = $("#nodes-status-carousel-content");
        carouselContent.html("");

        for (var nbr = 1; nbr < nodeNamesByNbr.length; nbr++) { // 0 is empty

            var nodeName = nodeNamesByNbr[nbr];

            var nodeAddress = data["node_address_" + nodeName];
            var nodeAlive = data["node_alive_" + nodeName];

            var arrayRow = ' ' +
                '<div class="col-lg-3 col-md-4 col-sm-6 col-xs-12 status-node-carousel" >\n' +
                '    <div class="pad15"><div class="status-node-node-rep">\n';

            if (nodeAlive == 'OK') {
                arrayRow += '<div class="text-center"><p><image src="images/node-icon-white.png" class="status-node-image"></image></p></div>\n';
            } else {
                arrayRow += '<div class="text-center"><p><image src="images/node-icon-red.png" class="status-node-image"></image></p></div>\n';
            }

            arrayRow += '   <div class="text-center"> <p>' + nbr + ' : ' + nodeAddress + '</p></div>\n'

            arrayRow += '    <p>\n';

            for (var sNb = 0; sNb < STATUS_SERVICES.length; sNb++) {
                var service = STATUS_SERVICES[sNb];
                if (nodeAlive == 'OK') {

                    var serviceStatus = data["service_" + service + "_" + nodeName];
                    //console.log ("For service '" + service + "' on node '" + nodeName + "' got '"+ serviceStatus + "'");
                    if (!serviceStatus) {

                        arrayRow +=
                            '<table class="node-status-carousel-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td class="nodes-status-carousel-status"><span class="font-weight-bold">&nbsp;</span></td>\n' +
                            '        <td class="nodes-status-carousel-actions">'+
                            '</td></tr></tbody></table>';

                    } else if (serviceStatus == "NA") {

                        arrayRow +=
                            '<table class="node-status-carousel-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td class="nodes-status-carousel-status"><span class="font-weight-bold service-status-error '+
                            '        '+(eskimoMain.isOperationInProgress() ? 'blinking-status' : '') +
                            '        ">' +
                            service +
                            '        </span></td>\n' +
                            '        <td class="nodes-status-carousel-actions">'+
                            '</td></tr></tbody></table>';

                    } else if (serviceStatus == "KO") {

                        arrayRow +=
                            '<table class="node-status-carousel-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td class="nodes-status-carousel-status"><span class="font-weight-bold service-status-error '+
                            '        '+(eskimoMain.isOperationInProgress() ? 'blinking-status' : '') +
                            '        ">' +
                            service +
                            '        </span></td>\n' +
                            fillInActions (service, nodeAddress, false, "nodes-status-carousel-actions") +
                            '    </tr>\n' +
                            '</tbody></table>\n';

                    } else {

                        var color = "#EEEEEE;";
                        if (serviceStatus == "TD") {
                            color = "violet";
                        }

                        arrayRow +=
                            '<table class="node-status-carousel-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td class="nodes-status-carousel-status"><span class="font-weight-bold '+
                            '        '+(eskimoMain.isOperationInProgress() && color == "violet" ? 'blinking-status' : '') +
                            '         " style="color: '+color+';">' +
                            '            <div class="status-service-icon">' +
                            '                <img class="status-service-icon-image" src="images/' + service + '-icon.png"/> ' +
                            '            </div>' +
                            '            <div class="status-service-text">' +
                            '&nbsp;' + service +
                            '            </div>' +
                            '        </span></td>\n' +
                            fillInActions (service, nodeAddress, true, "nodes-status-carousel-actions") +
                            '    </tr>\n' +
                            '</tbody></table>\n';

                    }
                } else {
                    arrayRow +=
                        '<table class="node-status-carousel-table">\n' +
                        '    <tbody><tr>\n' +
                        '        <td class="nodes-status-carousel-status"><span class="font-weight-bold">-</span></td>\n' +
                        '        <td class="nodes-status-carousel-actions">'+
                        '</td></tr></tbody></table>';
                }
            }

            arrayRow += '</p></div></div>';

            var newRow = $(arrayRow);

            carouselContent.append(newRow);
        }

    };

    this.generateTableHeader = function() {

        var tableHeaderHtml = ''+
            '<tr id="header_1" class="status-node-table-header">\n'+
            '<td class="status-node-cell" rowspan="2">Status</td>\n' +
            '<td class="status-node-cell" rowspan="2">No</td>\n' +
            '<td class="status-node-cell" rowspan="2">IP Address</td>\n';

        // Phase 1 : render first row
        var prevGroup = null;
        for (var i = 0; i < STATUS_SERVICES.length; i++) {

            var serviceName = STATUS_SERVICES[i];
            var serviceStatusConfig = SERVICES_STATUS_CONFIG[serviceName];

            if (serviceStatusConfig.group != null && serviceStatusConfig.group != "") {

                if (prevGroup == null || serviceStatusConfig.group != prevGroup) {

                    // first need to know size of group
                    var sizeOfGroup = 1;
                    for (var j = i + 1; j < STATUS_SERVICES.length; j++) {
                        var nextGroup = SERVICES_STATUS_CONFIG[STATUS_SERVICES[j]].group;
                        if (nextGroup != null && nextGroup == serviceStatusConfig.group) {
                            sizeOfGroup++;
                        } else {
                            break;
                        }
                    }

                    tableHeaderHtml +=
                            '<td class="status-node-cell" colspan="' + sizeOfGroup + '">' + serviceStatusConfig.group + '</td>\n';

                    prevGroup = serviceStatusConfig.group;
                }
            } else {

                tableHeaderHtml +=
                    '<td class="status-node-cell" rowspan="2">' + serviceStatusConfig.name + '</td>\n';
            }
        }

        tableHeaderHtml +=
                '</tr>\n' +
                '<tr id="header_2" class="status-node-table-header">\n';

        // Phase 1 : render first row
        for (var i = 0; i < STATUS_SERVICES.length; i++) {

            var serviceName = STATUS_SERVICES[i];
            var serviceStatusConfig = SERVICES_STATUS_CONFIG[serviceName];

            if (serviceStatusConfig.group && serviceStatusConfig.group != null && serviceStatusConfig.group != "") {
                tableHeaderHtml = tableHeaderHtml +
                    '<td class="status-node-cell">' + serviceStatusConfig.name + '</td>\n';
            }
        }

        tableHeaderHtml += "</tr>";

        return tableHeaderHtml;
    };

    this.renderNodesStatusTable = function (data, blocking, availableNodes, nodeNamesByNbr) {

        var statusRenderOptions = $(".status-render-options");
        statusRenderOptions.css("visibility", "hidden");
        statusRenderOptions.css("display", "none");

        var statucContainerTable = $("#status-node-container-table");
        statucContainerTable.css("visibility", "inherit");
        statucContainerTable.css("display", "inherit");

        // clear table
        $("#status-node-table-head").html(this.generateTableHeader());

        var statusContainerTableBody = $("#status-node-table-body");
        statusContainerTableBody.html("");

        for (var nbr = 1; nbr < nodeNamesByNbr.length; nbr++) { // 0 is empty

            var nodeName = nodeNamesByNbr[nbr];

            var nodeAddress = data["node_address_" + nodeName];
            var nodeAlive = data["node_alive_" + nodeName];

            var arrayRow = ' ' +
                '<tr id="' + nodeName + '">\n' +
                '    <td class="status-node-cell">\n';

            if (nodeAlive == 'OK') {
                arrayRow +=
                    '        <image src="images/node-icon.png" class="status-node-image"></image>\n';
            } else {
                arrayRow +=
                    '        <image src="images/node-icon-red.png" class="status-node-image"></image>\n';
            }

            arrayRow +=
                '    </td>\n' +
                '    <td class="status-node-cell">' + nbr + '</td>\n' +
                '    <td class="status-node-cell">' + nodeAddress + '</td>\n';

            for (var sNb = 0; sNb < STATUS_SERVICES.length; sNb++) {
                var service = STATUS_SERVICES[sNb];
                if (nodeAlive == 'OK') {

                    var serviceStatus = data["service_" + service + "_" + nodeName];
                    //console.log ("For service '" + service + "' on node '" + nodeName + "' got '"+ serviceStatus + "'");
                    if (!serviceStatus) {

                        arrayRow += '    <td class="status-node-cell"></td>\n'

                    } else if (serviceStatus == "NA") {

                        arrayRow +=
                            '    <td class="status-node-cell"><span class="service-status-error '+
                            '        '+(eskimoMain.isOperationInProgress() ? 'blinking-status' : '') +
                            '      ">NA</span></td>\n'

                    } else if (serviceStatus == "KO") {

                        arrayRow +=
                            '    <td class="status-node-cell">' +
                            '<span class="service-status-error">\n' +
                            '<table class="node-status-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td colspan="5" class="nodes-status-status"><span class="font-weight-bold ' +
                            '        '+(eskimoMain.isOperationInProgress() ? 'blinking-status' : '') +
                            '        ">KO</span></td>\n' +
                            '    </tr>\n' +
                            '    <tr class="">\n' +
                            fillInActions (service, nodeAddress, false, "nodes-status-actions") +
                            '    </tr>\n' +
                            '</tbody></table>\n' +
                            '\n' +
                            '</span>' +
                            '</td>\n'

                    } else {

                        var color = "darkgreen;";
                        if (serviceStatus == "TD") {
                            color = "violet";
                        }

                        arrayRow +=
                            '    <td class="status-node-cell">' +
                            '<span style="color: '+color+';">\n' +
                            '<table class="node-status-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td colspan="5" class="nodes-status-status"><span class="font-weight-bold '+
                            '        '+(eskimoMain.isOperationInProgress() && color == "violet" ? 'blinking-status' : '') +
                            '        ">OK</span></td>\n' +
                            '    </tr>\n' +
                            '    <tr>\n' +
                            fillInActions (service, nodeAddress, true, "nodes-status-actions") +
                            '    </tr>\n' +
                            '</tbody></table>\n' +
                            '\n' +
                            '</span>' +
                            '</td>\n'

                    }
                } else {
                    arrayRow += '    <td class="status-node-cell">-</td>\n'
                }
            }

            arrayRow += '</tr>';

            var newRow = $(arrayRow);

            statusContainerTableBody.append(newRow);
        }
    };

    function fillInActions(service, nodeAddress, up, tdClassName) {

        var retActionsHtml = '';

        retActionsHtml +=
            '        <td class="' + tdClassName + '">\n' +
            '            <a href="javascript:void(0);" title="Show journal" ' +
            (!eskimoMain.isOperationInProgress() ?
                'onclick="eskimoMain.getSystemStatus().showJournal(\'' + service + '\', \'' + nodeAddress + '\'); event.preventDefault(); return false;"':
                'onclick="event.preventDefault(); return false;"') +
            '                >\n' +
            '                <span class="service-status-action service-status-action-journal ' +
            '        '+(eskimoMain.isOperationInProgress() ? 'invisible disabled' : '') +
            '                "><i class="fa fa-file"></i></span>\n' +
            '            </a>\n' +
            '        </td>\n';

        if (up) {
            retActionsHtml +=
                '        <td class="'+tdClassName+'">\n' +
                '            <a href="javascript:void(0);" title="Stop Service" ' +
                (!eskimoMain.isOperationInProgress() ?
                    'onclick="eskimoMain.getSystemStatus().stopService(\'' + service + '\', \'' + nodeAddress + '\'); event.preventDefault(); return false;"':
                    'onclick="event.preventDefault(); return false;"') +
                '                >\n' +
                '                <span class="service-status-action service-status-action-stop ' +
                '        '+(eskimoMain.isOperationInProgress() ? 'invisible disabled' : '') +
                '                "><i class="fa fa-close"></i></span>\n' +
                '            </a>\n' +
                '        </td>\n';
        } else {
            retActionsHtml +=
                '        <td class="' + tdClassName + '">\n' +
                '            <a href="javascript:void(0);" title="Start Service" ' +
                (!eskimoMain.isOperationInProgress() ?
                    'onclick="eskimoMain.getSystemStatus().startService(\'' + service + '\', \'' + nodeAddress + '\'); event.preventDefault(); return false;"':
                    'onclick="event.preventDefault(); return false;"') +
                '                >\n' +
                '                <span class="service-status-action service-status-action-start ' +
                '        '+(eskimoMain.isOperationInProgress() ? 'invisible disabled' : '') +
                '                "><i class="fa fa-play"></i></span>\n' +
                '            </a>\n' +
                '        </td>\n';

        }

        retActionsHtml +=
            '        <td class="' + tdClassName + '">\n' +
            '            <a href="javascript:void(0);" title="Restart Service" ' +
            (!eskimoMain.isOperationInProgress() ?
                'onclick="eskimoMain.getSystemStatus().restartService(\'' + service + '\', \'' + nodeAddress + '\'); event.preventDefault(); return false;"':
                'onclick="event.preventDefault(); return false;"') +
            '                >\n' +
            '                <span class="service-status-action service-status-action-restart ' +
            '        '+(eskimoMain.isOperationInProgress() ? 'invisible disabled' : '') +
            '                "><i class="fa fa-refresh"></i></span>\n' +
            '            </a>\n' +
            '        </td>\n' +
            '        <td class="' + tdClassName + '">\n' +
            '            <a href="javascript:void(0);" title="Reinstall Service" ' +
            (!eskimoMain.isOperationInProgress() ?
                'onclick="eskimoMain.getSystemStatus().reinstallService(\'' + service + '\', \'' + nodeAddress + '\'); event.preventDefault(); return false;"':
                'onclick="event.preventDefault(); return false;"') +
            '                >\n' +
            '                <span class="service-status-action service-status-action-reinstall ' +
            '        '+(eskimoMain.isOperationInProgress() ? 'invisible disabled' : '') +
            '                "><i class="fa fa-undo"></i></span>\n' +
            '            </a>\n' +
            '        </td>\n' +
            '        <td></td>\n';

        return retActionsHtml;
    }

    this.fetchOperationResult = function() {
        $.ajax({
            type: "GET",
            dataType: "json",
            url: "get-last-operation-result",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {
                    eskimoMain.scheduleStopOperationInProgress (data.success);
                } else {
                    alert (data.error);
                }
            },
            error: errorHandler
        });
    };

    var inUpdateStatus = false;
    function updateStatus(blocking) {

        if (inUpdateStatus) {
            return;
        }
        inUpdateStatus = true;

        // cancel previous timer. update status will be rescheduled at the end of this method
        if (statusUpdateTimeoutHandler != null) {
            clearTimeout(statusUpdateTimeoutHandler);
        }

        if (blocking) {
            eskimoMain.showProgressbar();
        }

        $.ajax({
            type: "GET",
            dataType: "json",
            context: that,
            url: "get-status",
            success: function (data, status, jqXHR) {

                eskimoMain.serviceMenuClear();

                //console.log (data);

                if (!data.clear) {

                    this.handleSystemStatus(data.nodeServicesStatus, data.systemStatus, blocking);

                    this.renderNodesStatus (data.nodeServicesStatus, blocking);

                } else if (data.clear == "setup"){

                    eskimoMain.handleSetupNotCompleted();

                } else if (data.clear == "nodes"){

                    this.renderNodesStatusEmpty();
                }

                if (data.processingPending) {  // if backend says there is some processing going on
                    eskimoMain.recoverOperationInProgress();

                } else {                         // if backend says there is nothing going on
                    if (eskimoMain.isOperationInProgress()  // but frontend still things there is ...
                            && eskimoMain.isOperationInProgressOwner()) {  // ... and if that is my fault
                        this.fetchOperationResult();
                    }
                }

                if (blocking) {
                    eskimoMain.hideProgressbar();
                }

                // reschedule updateStatus
                statusUpdateTimeoutHandler = setTimeout(updateStatus, STATUS_UPDATE_INTERVAL);
                inUpdateStatus = false;
            },

            error: function (jqXHR, status) {
                // error handler
                console.log(jqXHR);
                console.log(status);

                if (jqXHR.status == "401") {
                    window.location = "login.html";
                }

                if (blocking) {
                    alert('fail : ' + status);

                    eskimoMain.hideProgressbar();
                }

                // reschedule updateStatus
                statusUpdateTimeoutHandler = setTimeout(updateStatus, STATUS_UPDATE_INTERVAL);
                inUpdateStatus = false;
            }
        });

        // use same timer to fetch notifications
        eskimoMain.getNotifications().fetchNotifications();

        // show a message on status page if there is some operations in progress pending
        if (eskimoMain.isOperationInProgress()) {
            showMessage("Pending operations in progress on backend. See 'Backend Messages' for more information.");
        }
    }
    this.updateStatus = updateStatus;

    // call constructor
    this.initialize();
};
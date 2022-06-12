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
eskimo.SystemStatus = function() {

    // will be injected eventually from constructorObject
    this.eskimoNotifications = null;
    this.eskimoOperations = null;
    this.eskimoNodesConfig = null;
    this.eskimoSetup = null;
    this.eskimoServices = null;
    this.eskimoMain = null;
    this.eskimoConsoles = null;
    this.eskimoFileManagers = null;

    const that = this;

    let initialized = false;

    const STATUS_UPDATE_INTERVAL = 4000;

    // initialized by backend
    let STATUS_SERVICES = [];
    let SERVICES_STATUS_CONFIG = {};

    let nodeFilter = "";

    let disconnectedFlag = true;

    let statusUpdateTimeoutHandler = null;

    let prevHidingMessageTimeout = null;

    this.scheduleInitializeStatusTableMenus = function() {

        if (initialized) {
            that.initializeStatusTableMenus();
        } else {
            // retry after a while
            setTimeout (that.scheduleInitializeStatusTableMenus, 400);
        }
    };

    this.initializeStatusTableMenus = function () {
        // initialize menus
        let serviceMenuContent = '' +
            (eskimoMain.hasRole("ADMIN") ? '' +
                '    <li><a id="start" tabindex="-1" href="#" title="Start Service"><i class="fa fa-play"></i> Start Service</a></li>\n' +
                '    <li><a id="stop" tabindex="-1" href="#" title="Stop Service"><i class="fa fa-stop"></i> Stop Service</a></li>\n' +
                '    <li><a id="restart" tabindex="-1" href="#" title="Restart Service"><i class="fa fa-refresh"></i> Restart Service</a></li>\n' +
                '    <li class="divider"></li>' +
                '    <li><a id="reinstall" tabindex="-1" href="#" title="Reinstall Service"><i class="fa fa-undo"></i> Reinstall Service</a></li>\n' +
                '    <li class="divider"></li>'
                : '') +
            '    <li><a id="show_journal" tabindex="-1" href="#" title="Show Journal"><i class="fa fa-file"></i> Show Journal</a></li>\n';

        $('#serviceContextMenuTemplate').html(serviceMenuContent);

        let nodeMenuContent = '' +
            (eskimoMain.hasRole("ADMIN") ? '' +
                '    <li><a id="terminal" tabindex="-1" href="#" title="Start Service"><i class="fa fa-terminal"></i> SSH Terminal</a></li>\n'
                : '') +
            '    <li><a id="file_manager" tabindex="-1" href="#" title="Stop Service"><i class="fa fa-folder"></i> SFTP File Manager</a></li>\n';

        $('#nodeContextMenuTemplate').html(nodeMenuContent);
    };

    this.initialize = function () {
        // Initialize HTML Div from Template
        $("#inner-content-status").load("html/eskimoSystemStatus.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt == "success") {

                loadUIStatusServicesConfig();

                $('#show-all-nodes-btn').click($.proxy (function () {
                    $(".filter-btn").attr("class", "btn btn-default filter-btn");
                    setNodeFilter (null);
                    showStatus(true);
                }, this));

                $('#show-master-services-btn').click($.proxy (function () {
                    $(".filter-btn").attr("class", "btn btn-default filter-btn");
                    $("#show-master-services-btn").attr("class", "btn filter-btn btn-success");
                    setNodeFilter ("master");
                    showStatus(true);
                }, this));

                $('#show-issues-btn').click($.proxy (function () {
                    $(".filter-btn").attr("class", "btn btn-default filter-btn");
                    $("#show-issues-btn").attr("class", "btn filter-btn btn-success");
                    setNodeFilter ("issues");
                    showStatus(true);
                }, this));

                $('#empty-nodes-configure').click(function () {
                    that.eskimoNodesConfig.showNodesConfig();
                });

                initialized = true;

            } else if (statusTxt == "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });

        // register menu handler on nodes
        $.fn.nodeContextMenu = function (settings) {

            return this.each(function () {

                // Open context menu
                $(this).on("click", function (e) {

                    let target = $(e.target);

                    let nodeMenu = $("#nodeContextMenu");
                    nodeMenu.html($("#nodeContextMenuTemplate").html());


                    //open menu
                    let $menu = nodeMenu
                        .data("invokedOn", target)
                        .show()
                        .css({
                            position: "absolute",
                            left: getMenuPosition(settings, e.clientX, 'width', 'scrollLeft', "#nodeContextMenu") - $("#inner-content-status").offset().left,
                            top: getMenuPosition(settings, e.clientY, 'height', 'scrollTop', "#nodeContextMenu") - $("#inner-content-status").offset().top
                        })
                        .off('click')
                        .on('click', 'a', function (evt) {
                            $menu.hide();

                            let $invokedOn = $menu.data("invokedOn");
                            let $selectedMenu = $(evt.target);

                            settings.menuSelected.call(this, $invokedOn, $selectedMenu);
                        });

                    return false;
                });

                //make sure menu closes on any click
                $('body').click(function () {
                    $("#nodeContextMenu").hide();
                });
            });
        };

        // register menu handler on services
        $.fn.serviceContextMenu = function (settings) {

            return this.each(function () {

                // Open context menu
                $(this).on("click", function (e) {

                    let target = $(e.target);

                    //let nodeAddress = $(target).closest("td.status-node-cell").data('eskimo-node');
                    let service = $(target).closest("td.status-node-cell").data('eskimo-service');

                    let additionalCommands = SERVICES_STATUS_CONFIG[service].commands;

                    // TODO make it empty if no commmand
                    let additionalCommandsHTML = '';

                    if (additionalCommands) {
                        if (additionalCommands.length > 0) {
                            additionalCommandsHTML += '<li class="divider"></li>';
                        }

                        for (let i = 0; i < additionalCommands.length; i++) {
                            additionalCommandsHTML +=
                                '<li><a id="' + additionalCommands[i].id + '" ' +
                                '       tabindex="-1" ' +
                                '       href="#" ' +
                                '       title="' + additionalCommands[i].name + '"' +
                                '    >' +
                                '<i class="fa ' + additionalCommands[i].icon + '"></i> ' +
                                additionalCommands[i].name + '' +
                                '</a>' +
                                '</li>\n';
                        }
                    }

                    let serviceMenu = $("#serviceContextMenu");
                    serviceMenu.html($("#serviceContextMenuTemplate").html() + additionalCommandsHTML);


                    //open menu
                    let $menu = serviceMenu
                        .data("invokedOn", target)
                        .show()
                        .css({
                            position: "absolute",
                            left: getMenuPosition(settings, e.clientX, 'width', 'scrollLeft', "#serviceContextMenu") - $("#inner-content-status").offset().left,
                            top: getMenuPosition(settings, e.clientY, 'height', 'scrollTop', "#serviceContextMenu") - $("#inner-content-status").offset().top
                        })
                        .off('click')
                        .on('click', 'a', function (evt) {
                            $menu.hide();

                            let $invokedOn = $menu.data("invokedOn");
                            let $selectedMenu = $(evt.target);

                            settings.menuSelected.call(this, $invokedOn, $selectedMenu);
                        });

                    return false;
                });

                //make sure menu closes on any click
                $('body').click(function () {
                    $("#serviceContextMenu").hide();
                });
            });
        };
    };

    this.isDisconnected = function() {
        return disconnectedFlag;
    };

    function getMenuPosition(settings, mouse, direction, scrollDir, menu) {
        let win = $("#inner-content-status")[direction](),
            scroll = $("#inner-content-status")[scrollDir](),
            menuTarget = $(menu)[direction](),
            position = mouse + scroll;

        // opening menu would pass the side of the page
        if (mouse + menuTarget > win && menuTarget < mouse)
            position -= menuTarget;

        return position;
    }

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

                    that.eskimoSetup.loadSetup(true);

                } else {
                    alert(data.error);
                }
            },
            error: errorHandler
        });
    }

    function setNodeFilter (doNodeFilter) {
        nodeFilter = doNodeFilter;
    }
    this.setNodeFilter = setNodeFilter;

    /** For tests */
    this.setStatusServices = function (statusServices) {
        STATUS_SERVICES = statusServices;
    };
    this.setServicesStatusConfig = function (servicesStatusConfig) {
        SERVICES_STATUS_CONFIG = servicesStatusConfig;
    };

    function showStatus (blocking) {

        if (!that.eskimoMain.isSetupLoaded()) {

            that.eskimoSetup.loadSetup();

            // retry after a ŵhile
            setTimeout (function() {
                showStatus(blocking);
            }, 100);

        } else {
            if (!that.eskimoMain.isSetupDone()) {

                that.eskimoMain.showSetupNotDone(blocking ? "" : "Cannot show nodes status as long as initial setup is not completed");

                // Still initialize the status update timer (also used for notifications)
                updateStatus(false);

            } else {

                // maybe Progress bar was shown previously and we don't show it on status page
                that.eskimoMain.hideProgressbar();

                that.eskimoMain.showOnlyContent("status");

                updateStatus(blocking);
            }
        }
    }
    this.showStatus = showStatus;

    function showStatusMessage (message, error) {

        if (prevHidingMessageTimeout != null) {
            clearTimeout(prevHidingMessageTimeout);
        }

        let serviceStatusWarning = $("#service-status-warning");
        serviceStatusWarning.css("display", "inherit");
        serviceStatusWarning.css("visibility", "inherit");

        let serviceStatusWarningMessage = $("#service-status-warning-message");
        serviceStatusWarningMessage.html(message);

        if (error) {
            serviceStatusWarningMessage.attr('class', "alert alert-danger");
        } else {
            serviceStatusWarningMessage.attr('class', "alert alert-warning");
        }

        prevHidingMessageTimeout = setTimeout(function () {
            serviceStatusWarning.css("display", "none");
            serviceStatusWarning.css("visibility", "hidden");
        }, 10000);

    }
    this.showStatusMessage = showStatusMessage;

    function showStatusWhenServiceUnavailable (service) {
        showStatusMessage (service + " is not up and running");
    }
    this.showStatusWhenServiceUnavailable = showStatusWhenServiceUnavailable;

    function serviceAction (action, service, node) {
        serviceActionInternal (action, service, node, false);
    }

    function serviceActionCustom (action, service, node) {
        serviceActionInternal (action, service, node, true);
    }

    function serviceActionInternal (action, service, node, custom) {

        that.eskimoOperations.showOperations();

        that.eskimoMain.startOperationInProgress();

        // 1 hour timeout
        $.ajax({
            type: "GET",
            dataType: "json",
            timeout: 1000 * 3600,
            contentType: "application/json; charset=utf-8",
            url: (custom ?
                "service-custom-action?action=" + action + "&service=" + service + "&nodeAddress=" + node :
                action + "?service=" + service + "&nodeAddress=" + node),
            success: function (data, status, jqXHR) {

                // OK
                console.log(data);

                if (!data || data.error) {
                    console.error(data.error);
                    that.eskimoMain.scheduleStopOperationInProgress (false);
                } else {
                    that.eskimoMain.scheduleStopOperationInProgress (true);

                    if (data.message != null) {
                        showStatusMessage (data.message);
                    }
                }
            },

            error: function (jqXHR, status) {
                errorHandler (jqXHR, status);
                that.eskimoMain.scheduleStopOperationInProgress (false);
            }
        });
    }

    function showJournal (service, node) {
        console.log("showJournal", service, node);

        serviceAction("show-journal", service, node);
    }
    this.showJournal = showJournal;

    function startService (service, node) {
        console.log("startService ", service, node);

        serviceAction("start-service", service, node);
    }
    this.startService = startService;

    function stopService (service, node) {
        console.log("stoptService ", service, node);

        serviceAction("stop-service", service, node);
    }
    this.stopService = stopService;

    function restartService (service, node) {
        console.log("restartService ", service, node);

        serviceAction("restart-service", service, node);
    }
    this.restartService = restartService;

    function reinstallService (service, node) {
        console.log("reinstallService ", service, node);
        if (confirm ("Are you sure you want to reinstall " + service + " on " + node + " ?")) {
            serviceAction("reinstall-service", service, node);
        }
    }
    this.reinstallService = reinstallService;

    function performServiceAction (action, service, node) {
        console.log("performServiceAction ", action, service, node);
        serviceActionCustom(action, service, node);
    }
    this.reinstallService = reinstallService;

    this.serviceIsUp = function (nodeServicesStatus, service) {
        if (!nodeServicesStatus) {
            return false;
        }
        for (let key in nodeServicesStatus) {
            if (key.indexOf("service_"+service+"_") > -1) {
                let serviceStatus = nodeServicesStatus[key];
                if (serviceStatus == "OK") {
                    return true;
                }
            }
        }
        return false;
    };

    this.displayMonitoringDashboard = function (monitoringDashboardId, refreshPeriod) {

        $.ajax({
            type: "GET",
            url: "grafana/api/dashboards/uid/" +monitoringDashboardId,
            success: function (data, status, jqXHR) {

                let forceRefresh = false;
                if ($("#status-monitoring-dashboard-frame").css("display") == "none") {


                    setTimeout (function() {
                        $("#status-monitoring-dashboard-frame").css("display", "inherit");
                        $("#status-monitoring-no-dashboard").css("display", "none");
                    }, 500);

                    forceRefresh = true;
                }

                let url = "grafana/d/" + monitoringDashboardId + "/monitoring?orgId=1&&kiosk&refresh="
                    + (refreshPeriod == null || refreshPeriod == "" ? "30s" : refreshPeriod);

                let prevUrl = $("#status-monitoring-dashboard-frame").attr('src');
                if (prevUrl == null || prevUrl == "" || prevUrl != url || forceRefresh) {
                    $("#status-monitoring-dashboard-frame").attr('src', url);

                    setTimeout(that.monitoringDashboardFrameTamper, 4000);
                }
            },
            error: function (jqXHR, status) {

                // ignore
                console.debug("error : could not fetch dashboard " + monitoringDashboardId);

                // mention the fact that dashboard does not exist
                $('#status-monitoring-no-dashboard').html("<strong>Grafana doesn't know dashboard with ID " + monitoringDashboardId + "</strong>");
            }
        });

    };

    function hideGrafanaDashboard() {

        let statusMonitoringInfo = $('.status-monitoring-info');
        statusMonitoringInfo.css("min-height", "220px");
        statusMonitoringInfo.css("height", "220px");

        $("#status-monitoring-info-panel").attr("class", "col-md-6");
        $("#status-monitoring-info-actions").attr("class", "col-md-6");

        $("#status-monitoring-info-container").attr("class", "col-xs-12 col-sm-12 col-md-12");

        let statusMonitoringGrafana = $('#status-monitoring-grafana');
        statusMonitoringGrafana.css("display", "none");
        statusMonitoringGrafana.css("visibility", "hidden");
    }
    this.hideGrafanaDashboard = hideGrafanaDashboard;

    function showGrafanaDashboard() {

        let statusMonitoringInfo = $('.status-monitoring-info');
        statusMonitoringInfo.css("min-height", "413px");
        statusMonitoringInfo.css("height", "413px");

        $("#status-monitoring-info-panel").attr("class", "col-md-12");
        $("#status-monitoring-info-actions").attr("class", "col-md-12");

        $("#status-monitoring-info-container").attr("class", "col-xs-12 col-sm-12 col-md-4");

        let statusMonitoringGrafana = $('#status-monitoring-grafana');
        statusMonitoringGrafana.css("display", "inherit");
        statusMonitoringGrafana.css("visibility", "inherit");
    }
    this.showGrafanaDashboard = showGrafanaDashboard;

    this.handleSystemStatus = function (nodeServicesStatus, systemStatus, blocking) {

        // A. Handle Grafana Dashboard ID display

        // A.1 Find out if grafana is available
        let grafanaAvailable = this.serviceIsUp (nodeServicesStatus, "grafana");

        let monitoringDashboardId = systemStatus.monitoringDashboardId;

        // grafana disabled (no dashboard configured)
        if (monitoringDashboardId == null
                || monitoringDashboardId == ""
                || monitoringDashboardId == "null"
                || monitoringDashboardId == "NONE"
                || monitoringDashboardId == "none") {

            hideGrafanaDashboard();

        } else {

            showGrafanaDashboard();

            if (!grafanaAvailable
                // or service grafana not yet available
                || !that.eskimoServices.isServiceAvailable("grafana")
            ) {

                let noDashboardDiv = $("#status-monitoring-no-dashboard");
                let monitoringDashboardFrame = $("#status-monitoring-dashboard-frame");

                noDashboardDiv.css("display", "inherit");
                noDashboardDiv.html("(Grafana not available or no dashboard configured)");

                monitoringDashboardFrame.css("display", "none");
                monitoringDashboardFrame.attr('src', "html/emptyPage.html");
            }
            // render iframe with refresh period (default 30s)
            else {

                let refreshPeriod = systemStatus.monitoringDashboardRefreshPeriod;

                setTimeout(function () {
                    that.displayMonitoringDashboard(monitoringDashboardId, refreshPeriod);
                }, blocking ? 0 : 5000);
            }
        }

        // B. Inject information

        $("#eskimo-flavour").html(eskimoFlavour);

        $("#system-information-version").html(systemStatus.buildVersion);

        $("#system-information-timestamp").html(systemStatus.buildTimestamp);

        $("#system-information-user").html(systemStatus.sshUsername);

        $("#system-information-start-timestamp").html (systemStatus.startTimestamp);

        // C. Cluster nodes and services
        let nodesWithproblem = [];
        if (nodeServicesStatus) {
            for (let key in nodeServicesStatus) {
                if (key.indexOf("node_alive_") > -1) {
                    let nodeName = key.substring("node_alive_".length);
                    let nodeAlive = nodeServicesStatus[key];
                    if (nodeAlive != "OK") {
                        nodesWithproblem.push(nodeName.replace(/-/g, "."));
                    }
                }
            }
        }

        if (nodesWithproblem.length == 0) {
            $("#system-information-nodes-status").html("<span style='color: darkgreen;'>OK</span>");
        } else {
            $("#system-information-nodes-status").html(
                "Following nodes are reporting problems : <span style='color: darkred;'>" +
                nodesWithproblem.join(", ") +
                "</span>");
        }

        // find out about services status
        let servicesWithproblem = [];
        if (nodeServicesStatus) {
            for (let key in nodeServicesStatus) {
                if (key.indexOf("service_") > -1) {
                    let serviceName = key.substring("service_".length, key.indexOf("_", "service_".length));
                    let serviceAlive = nodeServicesStatus[key];
                    if (serviceAlive != "OK") {
                        if (servicesWithproblem.length <= 0 || !servicesWithproblem.includes(serviceName)) {
                            servicesWithproblem.push(serviceName);
                        }
                    }
                }
            }
        }

        if (servicesWithproblem.length == 0) {
            if (nodesWithproblem.length == 0) {
                $("#system-information-services-status").html("<span style='color: darkgreen;'>OK</span>");
            } else {
                $("#system-information-services-status").html("<span style='color: darkred;'>-</span>");
            }
        } else {
            $("#system-information-services-status").html("Following services are reporting problems : " +
                "<span style='color: darkred;'>" +
                servicesWithproblem.join(", ") +
                "</span>");
        }

        // C. System Information Actions

        let systemInformationActions = '';

        if (systemStatus.links && systemStatus.links.length && systemStatus.links.length > 0) {
            for (let i = 0; i < systemStatus.links.length; i++) {

                let link = systemStatus.links[i];

                if (that.eskimoServices.isServiceAvailable(link.service)
                    && this.serviceIsUp (nodeServicesStatus, link.service)) {
                    systemInformationActions += '' +
                        '<a href="javascript:eskimoMain.getServices().showServiceIFrame(\''+link.service+'\');">' +
                        '<table class=".status-monitoring-action-table">' +
                        '<tr>' +
                        '<td>' +
                        '<img class="control-logo-logo" src="images/'+link.service+'-logo.png"/>' +
                        '</td><td>&nbsp;' +
                        link.title +
                        '</td>' +
                        '</tr>' +
                        '</table>' +
                        '</a>';
                }
            }
        }

        $("#system-information-actions").html(systemInformationActions);

        // D. General configuration

        that.eskimoMain.handleKubernetesSubsystem (systemStatus.enableKubernetes);
    };

    this.monitoringDashboardFrameTamper = function() {
        // remove widgets menus from iframe DOM

        // grafana 5.x
        $("#status-monitoring-dashboard-frame").contents().find(".panel-menu").remove();

        // grafana 6.x
        $("#status-monitoring-dashboard-frame").contents().find(".panel-menu-toggle").remove();

        $("#status-monitoring-dashboard-frame").contents().find(".panel-title").on('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            return false;
        })

        setTimeout (that.monitoringDashboardFrameTamper, 10000);
    };

    this.callServiceMenuHooks  = function (serviceStatus, nodeName, node, service, blocking) {
        if (serviceStatus == "NA" || serviceStatus == "KO") {
            that.eskimoServices.serviceMenuServiceFoundHook(nodeName, node, service, false, blocking);
        } else if (serviceStatus == "OK") {
            that.eskimoServices.serviceMenuServiceFoundHook(nodeName, node, service, true, blocking);
        }
    };

    this.renderNodesStatus = function (nodeServicesStatus, masters, blocking) {

        let nodeNamesByNbr = [];

        that.eskimoMain.handleSetupCompleted();

        let availableNodes = [];

        // loop on node nbrs and get Node Name + create table row
        for (let key in nodeServicesStatus) {
            if (key.indexOf("node_nbr_") > -1) {
                let nodeName = key.substring("node_nbr_".length);
                let nbr = nodeServicesStatus[key];
                nodeNamesByNbr [parseInt(nbr)] = nodeName;
            }
        }

        for (let nbr = 1; nbr < nodeNamesByNbr.length; nbr++) { // 0 is empty

            let nodeName = nodeNamesByNbr[nbr];

            let node = nodeServicesStatus["node_address_" + nodeName];
            let nodeAlive = nodeServicesStatus["node_alive_" + nodeName];

            // if at least one node is up, show the consoles menu
            if (nodeAlive == 'OK') {

                // Show SFTP and Terminal Menu entries
                $("#folderMenuConsoles").attr("class", "folder-menu-items");
                $("#folderMenuFileManagers").attr("class", "folder-menu-items");

                availableNodes.push({"nbr": nbr, "nodeName": nodeName, "nodeAddress": node});
            }

            for (let sNb = 0; sNb < STATUS_SERVICES.length; sNb++) {
                let service = STATUS_SERVICES[sNb];
                if (nodeAlive == 'OK') {

                    let serviceStatus = nodeServicesStatus["service_" + service + "_" + nodeName];

                    if (serviceStatus) {

                        if (SERVICES_STATUS_CONFIG[service].unique) {

                            this.callServiceMenuHooks (serviceStatus, nodeName, node, service, blocking);
                        } else {

                            // check master and only do it if nodeAddress is master, otherwise don't bother'
                            if (masters && masters[service] == nodeName) {
                                this.callServiceMenuHooks (serviceStatus, nodeName, node, service, blocking);
                            }
                        }
                    }
                }
            }
        }

        if (nodeNamesByNbr.length == 0) {

            this.renderNodesStatusEmpty();

        } else {

            this.renderNodesStatusTable(nodeServicesStatus, blocking, availableNodes, nodeNamesByNbr);
        }

        that.eskimoMain.setAvailableNodes(availableNodes);
    };

    this.renderNodesStatusEmpty = function() {

        let statusRenderOptions = $(".status-render-options");
        statusRenderOptions.css("visibility", "hidden");
        statusRenderOptions.css("display", "none");

        let statusContainerEmpty = $("#status-node-container-empty");
        statusContainerEmpty.css("visibility", "inherit");
        statusContainerEmpty.css("display", "inherit");
    };

    function showTerminal(node, nodeName) {
        that.eskimoConsoles.showConsoles();
        that.eskimoConsoles.openConsole(node, nodeName)
    }

    function showFileManager(node, nodeName) {
        that.eskimoFileManagers.showFileManagers();
        that.eskimoFileManagers.openFileManager(node, nodeName)
    }

    function registerNodeMenu(selector, dataSelector) {
        // register menu
        $(selector).nodeContextMenu({
            menuSelected: function (invokedOn, selectedMenu) {

                let action = selectedMenu.attr('id');
                let node = $(invokedOn).closest("td."+dataSelector).data('eskimo-node');
                let nodeName = $(invokedOn).closest("td."+dataSelector).data('eskimo-node-name');

                if (action == "terminal") {
                    showTerminal(node, nodeName);

                } else if (action == "file_manager") {
                    showFileManager(node, nodeName);

                } else {
                    alert ("Unknown action : " + action);
                }
            }
        })
    }
    this.registerNodeMenu = registerNodeMenu;

    function registerServiceMenu(selector, dataSelector) {
        // register menu
        $(selector).serviceContextMenu({
            menuSelected: function (invokedOn, selectedMenu) {

                let action = selectedMenu.attr('id');
                let node = $(invokedOn).closest("td."+dataSelector).data('eskimo-node');
                let service = $(invokedOn).closest("td."+dataSelector).data('eskimo-service');

                if (action == "show_journal") {
                    showJournal(service, node);

                } else if (action == "start") {
                    startService(service, node);

                } else if (action == "stop") {
                    stopService(service, node);

                } else if (action == "restart") {
                    restartService(service, node);

                } else if (action == "reinstall") {
                    reinstallService(service, node);

                } else {
                    performServiceAction (action, service, node);
                }
            }
        })
    }
    this.registerServiceMenu = registerServiceMenu;

    this.generateTableHeader = function() {

        let tableHeaderHtml = ''+
            '<tr id="header_1" class="status-node-table-header">\n'+
            '<td class="status-node-cell" rowspan="2">Status</td>\n' +
            '<td class="status-node-cell" rowspan="2">No</td>\n' +
            '<td class="status-node-cell" rowspan="2">Node</td>\n';

        // Phase 1 : render first row
        let prevGroup = null;
        for (let i = 0; i < STATUS_SERVICES.length; i++) {

            let serviceName = STATUS_SERVICES[i];
            let serviceStatusConfig = SERVICES_STATUS_CONFIG[serviceName];

            if (serviceStatusConfig.group != null && serviceStatusConfig.group != "") {

                if (prevGroup == null || serviceStatusConfig.group != prevGroup) {

                    // first need to know size of group
                    let sizeOfGroup = 1;
                    for (let j = i + 1; j < STATUS_SERVICES.length; j++) {
                        let nextGroup = SERVICES_STATUS_CONFIG[STATUS_SERVICES[j]].group;
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
                    '<td class="status-node-cell" rowspan="2">' +
                    //'   <img class="control-logo-logo" src="' + that.eskimoNodesConfig.getServiceLogoPath(serviceName) +
                    //'   "/><br>' +
                    serviceStatusConfig.name +
                    '</td>\n';
            }
        }

        tableHeaderHtml +=
                '</tr>\n' +
                '<tr id="header_2" class="status-node-table-header">\n';

        // Phase 2 : render second row
        for (let i = 0; i < STATUS_SERVICES.length; i++) {

            let serviceName = STATUS_SERVICES[i];
            let serviceStatusConfig = SERVICES_STATUS_CONFIG[serviceName];

            if (serviceStatusConfig.group && serviceStatusConfig.group != "") {
                tableHeaderHtml = tableHeaderHtml +
                    '<td class="status-node-cell">' +
                    //'   <img class="control-logo-logo" src="' + that.eskimoNodesConfig.getServiceLogoPath(serviceName) +
                    //'   "/><br>' +
                    serviceStatusConfig.name + '</td>\n';
            }
        }

        tableHeaderHtml += "</tr>";

        return tableHeaderHtml;
    };

    this.renderNodesStatusTable = function (data, blocking, availableNodes, nodeNamesByNbr) {

        let statusRenderOptions = $(".status-render-options");
        statusRenderOptions.css("visibility", "hidden");
        statusRenderOptions.css("display", "none");

        let statucContainerTable = $("#status-node-container-table");
        statucContainerTable.css("visibility", "inherit");
        statucContainerTable.css("display", "inherit");

        // clear table
        $("#status-node-table-head").html(this.generateTableHeader());

        let statusContainerTableBody = $("#status-node-table-body");
        statusContainerTableBody.html("");

        for (let nbr = 1; nbr < nodeNamesByNbr.length; nbr++) { // 0 is empty

            let nodeHasIssues = false;
            let nodeHasMasters = false;

            let nodeName = nodeNamesByNbr[nbr];

            let node = data["node_address_" + nodeName];
            let nodeAlive = data["node_alive_" + nodeName];

            let arrayRow = ' ' +
                '<tr id="' + nodeName + '">\n' +
                '    <td class="status-node-cell-intro">\n';

            if (nodeAlive == 'OK') {
                arrayRow +=
                    '        <img src="images/node-icon.png" class="status-node-image"></img>\n';
            } else {
                arrayRow +=
                    '        <img src="images/node-icon-red.png" class="status-node-image"></img>\n';
                nodeHasIssues = true;
            }

            arrayRow +=
                '    </td>\n' +
                '    <td class="status-node-cell-intro"' +
                '        data-eskimo-node="' + node + '"' +
                '        data-eskimo-node-name="' + nodeName + '">' + nbr + '</td>\n' +
                '    <td class="status-node-cell-intro" ' +
                '        data-eskimo-node="' + node + '"' +
                '        data-eskimo-node-name="' + nodeName + '">' + node + '</td>\n';

            for (let sNb = 0; sNb < STATUS_SERVICES.length; sNb++) {

                let service = STATUS_SERVICES[sNb];

                if (nodeAlive == 'OK') {

                    let serviceStatus = data["service_" + service + "_" + nodeName];
                    //console.log ("For service '" + service + "' on node '" + nodeName + "' got '"+ serviceStatus + "'");
                    if (!serviceStatus) {

                        arrayRow += '    <td class="status-node-cell-empty"></td>\n'

                    } else if (serviceStatus == "NA") {

                        if (that.eskimoNodesConfig.isServiceUnique(service)) {
                            nodeHasMasters = true;
                        }

                        arrayRow +=
                            '    <td class="status-node-cell-empty"><span class="service-status-error '+
                            '        '+(that.eskimoMain.isOperationInProgress() ? 'blinking-status' : '') +
                            '      "><i class="fa fa-question"></i></span></td>\n';
                        nodeHasIssues = true;

                    } else if (serviceStatus == "KO") {

                        if (that.eskimoNodesConfig.isServiceUnique(service)) {
                            nodeHasMasters = true;
                        }

                        arrayRow +=
                            '    <td class="status-node-cell'+(that.eskimoMain.isOperationInProgress() ? "-empty": "")+'"' +
                            '         data-eskimo-node="'+node+'" data-eskimo-service="'+service+'" \'>' +
                            '<span class="service-status-error">\n' +
                            '<table class="node-status-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td colspan="5" class="nodes-status-status"><span class="font-weight-bold ' +
                            '        '+(that.eskimoMain.isOperationInProgress() ? 'blinking-status' : '') +
                            '        "><i class="fa fa-times"></i></span></td>\n' +
                            '    </tr>\n' +
                            '</tbody></table>\n' +
                            '\n' +
                            '</span>' +
                            '</td>\n';
                        nodeHasIssues = true;

                    } else {

                        if (that.eskimoNodesConfig.isServiceUnique(service)) {
                            nodeHasMasters = true;
                        }

                        let color = "darkgreen";
                        if (serviceStatus == "TD") {
                            color = "violet";
                        } else if (serviceStatus == "restart") {
                            color = "#CB4335";
                            nodeHasIssues = true;
                        }

                        arrayRow +=
                            '    <td class="status-node-cell'+(that.eskimoMain.isOperationInProgress() ? "-empty": "")+'"' +
                            '         data-eskimo-node="'+node+'" data-eskimo-service="'+service+'">\n' +
                            '<span style="color: '+color+';">\n' +
                            '<table class="node-status-table">\n' +
                            '    <tbody><tr>\n' +
                            '        <td colspan="5" class="nodes-status-status"><span class="font-weight-bold '+
                            '        '+(that.eskimoMain.isOperationInProgress() && color == "violet" ? 'blinking-status' : '') +
                            '        "><i class="fa fa-check"></i></span></td>\n' +
                            '    </tr>\n' +
                            '</tbody></table>\n' +
                            '\n' +
                            '</span>' +
                            '</td>\n'

                    }
                } else {
                    arrayRow += '    <td class="status-node-cell-empty">-</td>\n'
                }
            }

            arrayRow += '</tr>';

            let newRow = $(arrayRow);

            // filtering
            if (   !nodeFilter || ((nodeFilter == "master") && nodeHasMasters)
                ||
                   ((nodeFilter == "issues") && nodeHasIssues)) {
                statusContainerTableBody.append(newRow);
            }
        }

        registerNodeMenu("#status-node-table-body td.status-node-cell-intro", "status-node-cell-intro");
        registerServiceMenu("#status-node-table-body td.status-node-cell", "status-node-cell");
    };

    this.fetchOperationResult = function() {
        $.ajax({
            type: "GET",
            dataType: "json",
            url: "get-last-operation-result",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {
                    that.eskimoMain.scheduleStopOperationInProgress (data.success);
                } else {
                    alert (data.error);
                }
            },
            error: errorHandler
        });
    };

    let inUpdateStatus = false;
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
            that.eskimoMain.showProgressbar();
        }

        $.ajax({
            type: "GET",
            dataType: "json",
            url: "get-status",
            timeout: 1000 * 10, // 10 secs
            success: function (data, status, jqXHR) {

                disconnectedFlag = false;

                /*
                let serviceStatusWarning = $("#service-status-warning");
                serviceStatusWarning.css("display", "none");
                serviceStatusWarning.css("visibility", "hidden");
                */

                that.eskimoMain.serviceMenuClear(data.nodeServicesStatus);

                //console.log (data);

                if (!data.clear) {

                    that.handleSystemStatus(data.nodeServicesStatus, data.systemStatus, blocking);

                    that.renderNodesStatus (data.nodeServicesStatus, data.masters, blocking);

                } else if (data.clear == "setup"){

                    that.eskimoMain.handleSetupNotCompleted();

                    if (   !that.eskimoMain.isCurrentDisplayedService("setup")
                        && !that.eskimoMain.isCurrentDisplayedService("operations")) { // don't move to setup if operations are being shown !
                        that.eskimoMain.showSetupNotDone();
                    }

                } else if (data.clear == "nodes"){

                    if (data.systemStatus) {
                        that.handleSystemStatus(null, data.systemStatus, blocking);
                    }

                    that.renderNodesStatusEmpty();
                }

                if (data.processingPending) {  // if backend says there is some processing going on
                    that.eskimoMain.recoverOperationInProgress();

                } else {                         // if backend says there is nothing going on
                    if (that.eskimoMain.isOperationInProgress()  // but frontend still things there is ...
                            && that.eskimoMain.isOperationInProgressOwner()) {  // ... and if that is my fault
                        that.fetchOperationResult();
                    }
                }

                if (blocking) {
                    that.eskimoMain.hideProgressbar();
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

                    that.eskimoMain.hideProgressbar();

                } else {

                    showStatusMessage("Couldn't fetch latest status from Eskimo Backend. Shown status is the latest known status. ", true)
                }

                disconnectedFlag = true;

                // reschedule updateStatus
                statusUpdateTimeoutHandler = setTimeout(updateStatus, STATUS_UPDATE_INTERVAL);
                inUpdateStatus = false;
            }
        });

        // use same timer to fetch notifications
        that.eskimoNotifications.fetchNotifications();

        // show a message on status page if there is some operations in progress pending
        if (that.eskimoMain.isOperationInProgress()) {
            showStatusMessage("Pending operations in progress on backend. See 'Operations Monitoring' for more information.");
        }
    }
    this.updateStatus = updateStatus;

};
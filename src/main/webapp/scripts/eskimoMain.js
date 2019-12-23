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
eskimo.Main = function() {

    // constants
    var MESSAGES_POLLING_STOP_DELAY = 10000;


    var that = this;

    var setupLoaded = false;
    var setupDone = false;

    var isMenuMinimized = false;
    var dontMessWithSidebarSizeAnyMore = false;

    var eskimoSetup = null;
    var eskimoNodesConfig = null;
    var eskimoSystemStatus = null;
    var eskimoMessaging = null;
    var eskimoConsoles = null;
    var eskimoNotifications = null;
    var eskimoServices = null;
    var eskimoServicesSelection = null;
    var eskimoServicesConfig = null;
    var eskimoFileManagers = null;
    var eskimoOperationsCommand = null;
    var eskimoSetupCommand = null;
    var eskimoAbout = null;

    var operationInProgress = false;
    var operationInProgressOwner = false;

    var menuHidingPos = 0;

    this.isSetupLoaded = function() {
        return setupLoaded;
    };

    this.setSetupLoaded = function() {
        setupLoaded = true;
    };

    this.isSetupDone = function() {
        return setupDone;
    };

    this.setSetupDone = function() {
        setupDone = true;
    };

    function isOperationInProgress() {
        return operationInProgress;
    }
    this.isOperationInProgress = isOperationInProgress;

    function isOperationInProgressOwner() {
        return operationInProgressOwner;
    }
    this.isOperationInProgressOwner = isOperationInProgressOwner;

    function setOperationInProgress (pendingOp) {
        operationInProgress = pendingOp;
        eskimoMessaging.setOperationInProgress(pendingOp);
    }
    this.setOperationInProgress = setOperationInProgress;

    function startOperationInProgress () {
        setOperationInProgress (true);
        eskimoMessaging.startOperationInProgress();
    }
    this.startOperationInProgress = startOperationInProgress;

    function stopOperationInProgress (success) {
        eskimoMessaging.stopOperationInProgress (success);
        setOperationInProgress (false);
        hideProgressbar();
    }
    this.stopOperationInProgress = stopOperationInProgress;

    function scheduleStopOperationInProgress (success) {
        window.setTimeout (stopOperationInProgress, MESSAGES_POLLING_STOP_DELAY, success);
    }
    this.scheduleStopOperationInProgress = scheduleStopOperationInProgress;

    function recoverOperationInProgress() {
        if (!isOperationInProgress()) { // but frontend has no clue
            startOperationInProgress();
            operationInProgressOwner = true;
            eskimoMessaging.addMessage("(Recovering messages from backend processing)");
            eskimoMessaging.showMessages();
        }
    }
    this.recoverOperationInProgress = recoverOperationInProgress;

    this.showOnlyContent = function (content, isServiceIFrame) {

        // if service iframe is already shown, clicking a second time on the link refreshed the iframe

        if (isServiceIFrame && $("#inner-content-" + content).css("visibility") == "visible") {

            eskimoServices.refreshIframe (content);

        } else {

            $(".inner-content").css("visibility", "hidden");

            $("#inner-content-" + content).css("visibility", "visible");
        }
    };

    function getDisplayedService () {
        var displayService;
        $(".inner-content").each(function (nbr, innerContent) {
            if ($(innerContent).css("visibility") == "visible") {
                displayService = $(innerContent).attr('id').substring("inner-content-".length);
            }
        });
        return displayService;
    }

    this.isCurrentDisplayedService = function (service) {
        var displayedService = getDisplayedService ();
        //console.log ("displayedService is : " + service);
        return displayedService == service;
    };

    function serviceMenuClear(nodeServicesStatus) {

        // remove all menu entries (cannot find out which service is here :-(
        if (!nodeServicesStatus || nodeServicesStatus == null) {

            $(".folder-menu-items").each(function () {

                var menuService = this.id.substring('folderMenu'.length);

                var service = getHyphenSeparated(menuService);

                $(this).attr("class", "folder-menu-items disabled");

                that.getServices().handleServiceHiding(service);
            });

        }
        // else check with system status and nodeServiceStatus
        else {

            $(".folder-menu-items").each(function () {

                var menuService = this.id.substring('folderMenu'.length);

                var service = getHyphenSeparated(menuService);

                var serviceUp = that.getSystemStatus().serviceIsUp(nodeServicesStatus, service);

                if (!serviceUp || !that.getServices().isServiceAvailable(service)) {
                    $(this).attr("class", "folder-menu-items disabled");
                    that.getServices().handleServiceHiding(service);
                } else {
                    $(this).attr("class", "folder-menu-items");
                }
            });
        }
    }
    this.serviceMenuClear = serviceMenuClear;

    this.showProgressbar = function () {
        var contentProgressBar = $(".inner-content-show");
        contentProgressBar.css("visibility", "visible");
        contentProgressBar.css("display", "flex");
    };

    function hideProgressbar () {
        var contentProgressBar = $(".inner-content-show");
        contentProgressBar.css("visibility", "hidden");
        contentProgressBar.css("display", "none");
    }
    this.hideProgressbar = hideProgressbar;

    this.sidebarToggleClickedListener = function () {
        dontMessWithSidebarSizeAnyMore = true;
    };

    this.setNavigationCompact = function () {
        var hoeAppContainer = $('#hoeapp-container');
        if (!dontMessWithSidebarSizeAnyMore && !hoeAppContainer.hasClass("hoe-minimized-lpanel")) {
            if ($('#hoeapp-wrapper').attr("hoe-device-type") !== "phone") {
                hoeAppContainer.toggleClass('hoe-minimized-lpanel');
                $('#hoe-header').toggleClass('hoe-minimized-lpanel');
                $('body').attr("hoe-navigation-type", "vertical-compact");
            } else {
                $('#hoeapp-wrapper').addClass('hoe-hide-lpanel');
            }
            isMenuMinimized = true;
        }
    };

    this.handleSetupCompleted = function () {
        setupDone = true;

        $(".config-menu-items").each(function() {
            $(this).attr("class", "config-menu-items");
        });
    };

    this.handleSetupNotCompleted = function () {

        setupDone = false;

        serviceMenuClear();

        $(".config-menu-items").each(function() {
            $(this).attr("class", "config-menu-items disabled");
        });

        $("#menu-configure-setup").attr("class", "config-menu-items");
        $("#menu-messages").attr("class", "config-menu-items");
    };

    this.showSetupNotDone = function (message) {
        eskimoSetup.showSetup();

        if (message && message != null && message != "") {
            eskimoSetup.showSetupMessage(message);
        }
    };

    this.setAvailableNodes = function(nodes) {
        eskimoConsoles.setAvailableNodes(nodes);
        eskimoFileManagers.setAvailableNodes(nodes);
    };

    this.getSetup = function (){
        return eskimoSetup;
    };
    this.getNodesConfig = function() {
        return eskimoNodesConfig;
    };
    this.getSystemStatus = function() {
        return eskimoSystemStatus;
    };
    this.getMessaging = function() {
        return eskimoMessaging;
    };
    this.getConsoles = function() {
        return eskimoConsoles;
    };
    this.getNotifications = function() {
        return eskimoNotifications;
    };
    this.getServices = function() {
        return eskimoServices;
    };
    this.getServicesSelection = function() {
        return eskimoServicesSelection;
    };
    this.getServicesConfig = function() {
        return eskimoServicesConfig;
    };
    this.getFileManagers = function() {
        return eskimoFileManagers;
    };
    this.getOperationsCommand = function() {
        return eskimoOperationsCommand;
    };
    this.getSetupCommand = function() {
        return eskimoSetupCommand;
    };
    this.getAbout = function() {
        return eskimoAbout;
    };

    this.initialize = function() {

        eskimoSetup = new eskimo.Setup();
        //  -> No specific backend loading

        eskimoNodesConfig = new eskimo.NodesConfig();
        // loadConfigServices -> get-services-dependencies
        // - calls eskimoServices.initialize()
        // loadServiceDependencies -> list-config-services

        eskimoNotifications = new eskimo.Notifications();
        // loadLastLine -> get-lastline-notification

        eskimoMessaging = new eskimo.Messaging();
        // loadLastLine -> get-lastline-messaging

        eskimoConsoles = new eskimo.Consoles();
        // (nothing)

        eskimoServices = new eskimo.Services();
        // loadUIServicesConfig -> get-ui-services-config
        // - loadUIServices -> list-ui-services
        //   - createServicesIFrames()
        //   - createServicesMenu()

        eskimoServicesSelection = new eskimo.ServicesSelection();
        // loadServicesConfig -> get-services-config
        // - initModalServicesConfig()

        eskimoServicesConfig = new eskimo.ServicesConfig();
        // loadServicesConfig -> load-services-config

        eskimoOperationsCommand = new eskimo.OperationsCommand();
        // (nothing)

        eskimoSetupCommand = new eskimo.SetupCommand();
        // (nothing)

        eskimoFileManagers = new eskimo.FileManagers();
        // (nothing)

        eskimoSystemStatus = new eskimo.SystemStatus();
        // loadUIStatusServicesConfig -> get-ui-services-status-config
        // - loadListServices -> list-services
        //   - setup.loadSetup -> load-setup
        //     + success OR clear=services
        //       - eskimoMain.handleSetupCompleted
        //     + clear=[others]
        //       - eskimoMain.handleSetupNotCompleted();
        //       - if initializationTime
        //         + eskimoMain.showOnlyContent("setup");
        //         + status.updateStatus(false); -- to start the polling
        //     +  success OR OR clear=services
        //       - if initializationTime
        //         + eskimoMain.getSystemStatus().showStatus(true);
        //     + PROCESSING PENDING DETECTION LOGIC

        eskimoAbout = new eskimo.About();

        isMenuMinimized = $('#hoeapp-container').hasClass("hoe-minimized-lpanel");

        $(window).resize (this.menuResize);

        $("#menu-scroll-up").click(this.menuUp);
        $("#menu-scroll-down").click(this.menuDown);
    };

    this.menuResize = function() {
        // alert (window.innerHeight + " - " + window.innerWidth);

        var actualMenuHeight = $("#menu-container").height();
        var menuContainerHeight = $("#hoe-left-panel").height();

        console.log (menuContainerHeight + " -  " + actualMenuHeight);

        if (menuContainerHeight - 80 < actualMenuHeight) {
            $("#menu-scroll-up").css ("display", "inherit");
            $("#menu-scroll-down").css ("display", "inherit");
            $("#menu-container").css ("top", "25px");
            that.menuUp();
        } else {
            $("#menu-scroll-up").css ("display", "none");
            $("#menu-scroll-down").css ("display", "none");
            $("#menu-container").css ("top", "0px");
        }

        // reset visibility state
        $("#menu-container > * > li").each(function(nbr, node) {
            $(node).css("display", "");
        });
        menuHidingPos = 0;
    };

    this.menuUp = function(e) {

        if (menuHidingPos > 0) {

            menuHidingPos--;

            // browse both menu and unhide last hidden element
            // and deincrement menuHidingPos
            $("#menu-container > * > li").each(function(nbr, node) {
                if (nbr == menuHidingPos) {
                    $(node).css("display", "");
                }
            });
        }

        if (e != null) {
            e.preventDefault();
        }
        return false;
    };

    this.menuDown = function(e) {

        var actualMenuHeight = $("#menu-container").height();
        var menuContainerHeight = $("#hoe-left-panel").height();

        // IF AND ONLY IF size is not sufficient for current menu site, THEN
        if (menuContainerHeight - 80 < actualMenuHeight) {

            // hide first non-hidden li element from both menu
            // and increment menuHidingPos
            $("#menu-container > * > li").each(function(nbr, node) {
                if (nbr == menuHidingPos) {
                    $(node).css("display", "none");
                }
            });

            menuHidingPos++;
        }

        if (e != null) {
            e.preventDefault();
        }
        return false;
    };

    this.initialize();

};


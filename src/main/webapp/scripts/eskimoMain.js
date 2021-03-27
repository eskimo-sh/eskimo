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
eskimo.Main = function() {

    const that = this;

    let setupLoaded = false;
    let setupDone = false;

    let dontMessWithSidebarSizeAnyMore = false;

    let eskimoSetup = null;
    let eskimoNodesConfig = null;
    let eskimoSystemStatus = null;
    let eskimoOperations = null;
    let eskimoConsoles = null;
    let eskimoNotifications = null;
    let eskimoServices = null;
    let eskimoServicesSelection = null;
    let eskimoMarathonServicesSelection = null;
    let eskimoMarathonServicesConfig = null;
    let eskimoServicesSettings = null;
    let eskimoFileManagers = null;
    let eskimoOperationsCommand = null;
    let eskimoSetupCommand = null;
    let eskimoMarathonOperationsCommand = null;
    let eskimoSettingsOperationsCommand = null;
    let eskimoAbout = null;

    let operationInProgress = false;
    let operationInProgressOwner = false;

    let menuHidingPos = 0;

    let roles = [];

    this.doInitializeInternal = function() {
        $("#eskimoTitle").html("Eskimo " + eskimoFlavour);

        // A. Create services

        eskimoSetup = new eskimo.Setup();
        eskimoNotifications = new eskimo.Notifications();
        eskimoOperations = new eskimo.Operations();
        eskimoOperationsCommand = new eskimo.OperationsCommand();
        eskimoMarathonOperationsCommand = new eskimo.MarathonOperationsCommand();
        eskimoSetupCommand = new eskimo.SetupCommand();
        eskimoConsoles = new eskimo.Consoles();
        eskimoFileManagers = new eskimo.FileManagers();
        eskimoServices = new eskimo.Services();
        eskimoServicesSelection = new eskimo.ServicesSelection({eskimoMain: that});
        eskimoServicesSettings = new eskimo.ServicesSettings();
        eskimoSettingsOperationsCommand = new eskimo.SettingsOperationsCommand();
        eskimoNodesConfig = new eskimo.NodesConfig();
        eskimoMarathonServicesConfig = new eskimo.MarathonServicesConfig();
        eskimoMarathonServicesSelection = new eskimo.MarathonServicesSelection();
        eskimoSystemStatus = new eskimo.SystemStatus();
        eskimoAbout = new eskimo.About();

        // B. Inject dependencies
        let initObject = {
            eskimoSetup: eskimoSetup,
            eskimoNodesConfig: eskimoNodesConfig,
            eskimoSystemStatus: eskimoSystemStatus,
            eskimoOperations: eskimoOperations,
            eskimoConsoles: eskimoConsoles,
            eskimoNotifications: eskimoNotifications,
            eskimoServices: eskimoServices,
            eskimoServicesSelection: eskimoServicesSelection,
            eskimoMarathonServicesSelection: eskimoMarathonServicesSelection,
            eskimoMarathonServicesConfig: eskimoMarathonServicesConfig,
            eskimoServicesSettings: eskimoServicesSettings,
            eskimoFileManagers: eskimoFileManagers,
            eskimoOperationsCommand: eskimoOperationsCommand,
            eskimoSetupCommand: eskimoSetupCommand,
            eskimoMarathonOperationsCommand: eskimoMarathonOperationsCommand,
            eskimoSettingsOperationsCommand: eskimoSettingsOperationsCommand,
            eskimoAbout: eskimoAbout,
            eskimoMain: this
        };

        for (let dependency in initObject) {
            for (let service in initObject) {
                if (initObject[service][dependency] !== undefined) {
                    initObject[service][dependency] = initObject[dependency];
                }
            }
        }

        // fetch context
        fetchContext();

        // C. Initialize services

        eskimoSetup.initialize();
        //  -> No specific backend loading

        eskimoNotifications.initialize();
        // loadLastLine -> get-lastline-notification

        eskimoOperations.initialize();

        eskimoOperationsCommand.initialize();
        // (nothing)

        eskimoMarathonOperationsCommand.initialize();
        // (nothing)

        eskimoSetupCommand.initialize();
        // (nothing)

        eskimoConsoles.initialize();
        // (nothing)

        eskimoFileManagers.initialize();
        // (nothing)

        // CALLED ELSWEHERE
        //eskimoServices.initialize();
        // loadUIServicesConfig -> get-ui-services-config
        // - loadUIServices -> list-ui-services
        //   - createServicesIFrames()
        //   - createServicesMenu()

        eskimoServicesSelection.initialize();
        // loadServicesConfig -> get-services-config
        // - initModalServicesConfig()

        eskimoServicesSettings.initialize();
        // loadServicesConfig -> load-services-config

        eskimoSettingsOperationsCommand.initialize();
        // (nothing)

        eskimoNodesConfig.initialize();
        // loadConfigServices -> get-services-dependencies
        // - calls eskimoServices.initialize()
        // loadServiceDependencies -> list-config-services

        eskimoMarathonServicesConfig.initialize();
        // loadMarathonServices -> get-marathon-services

        eskimoMarathonServicesSelection.initialize();
        // (nothing)

        eskimoSystemStatus.initialize();
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
        //         + eskimoSystemStatus.showStatus(true);
        //     + PROCESSING PENDING DETECTION LOGIC

        eskimoAbout.initialize();

        $(window).resize (this.windowResize);

        // menu scrolling
        $("#menu-scroll-up").click(menuUp);
        $("#menu-scroll-down").click(menuDown);
        $('#hoe-left-panel').on('mousewheel', menuMouseWheel);

        // notifications
        $("#main-show-notifications-link").click(eskimoNotifications.notificationsShown);
        $("#main-clear-notifications-link").click(eskimoNotifications.clearNotifications);

        // about
        $("#main-show-about-link").click(eskimoAbout.showAbout);

        // menu entries
        $("#main-menu-show-consoles-link").click(eskimoConsoles.showConsoles);
        $("#main-menu-show-file-managers-link").click(eskimoFileManagers.showFileManagers);
        $("#main-menu-show-status-link").click(eskimoSystemStatus.showStatus);
        $("#main-menu-show-setup-link").click(eskimoSetup.showSetup);
        $("#main-menu-show-services-settings-link").click(eskimoServicesSettings.showServicesSettings);
        $("#main-menu-show-nodes-config-link").click(eskimoNodesConfig.showNodesConfig);
        $("#main-menu-show-marathon-config-link").click(eskimoMarathonServicesConfig.showMarathonServicesConfig);
        $("#main-menu-show-operations-link").click(eskimoOperations.showOperations);
        $("#main-menu-logout-link").click(function() {
            window.location = "logout";
        });

        this.windowResize();
    };

    this.initialize = function() {

        $(document).ready(function () {

            $("#hoeapp-wrapper").load("html/eskimoMain.html", function (responseTxt, statusTxt, jqXHR) {

                that.doInitializeInternal();

                // This has to remain last
                initHoe();
            });
        });
    };

    function fetchContext  () {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "context",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    roles = data.roles;
                    disableAdminMenu();

                    that.version = data.version;
                    $("#eskimo-version").html(that.version);

                    eskimoSystemStatus.scheduleInitializeStatusTableMenus();

                } else {
                    console.error(data.error);
                }
            },
            error: errorHandler
        });
    }
    this.fetchContext = fetchContext;

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

    function disableAdminMenu () {

        $(".folder-menu-items, .config-menu-items").each(function() {
            let menuRole = $(this).attr("data-menu-role");
            //console.log (this.id, menuRole, that.hasRole(menuRole), $(this).hasClass("menu-hidden"));
            if (menuRole != null && menuRole != "") {
                if (!that.hasRole(menuRole) && !$(this).hasClass("menu-hidden")) {
                    $(this).addClass ("menu-hidden");
                    $(this).css ("display", "none");
                }
            }
        });
    }
    this.disableAdminMenu = disableAdminMenu;

    this.hasRole = function (role) {
        if (role == "*") {
            return true;
        }
        for (let i = 0; i < roles.length; i++) {
            if (roles[i] == role) {
                return true;
            }
        }
        return false;
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
        eskimoOperations.setOperationInProgress(pendingOp);
    }
    this.setOperationInProgress = setOperationInProgress;

    function startOperationInProgress () {
        console.log ("eskimoMain - startOperationInProgress");
        setOperationInProgress (true);
        eskimoOperations.startOperationInProgress();
    }
    this.startOperationInProgress = startOperationInProgress;

    function scheduleStopOperationInProgress (success) {
        console.log ("eskimoMain - scheduleStopOperationInProgress");
        eskimoOperations.stopOperationInProgress (success, function() {
            setOperationInProgress (false);
            hideProgressbar();
        });

    }
    this.scheduleStopOperationInProgress = scheduleStopOperationInProgress;

    function recoverOperationInProgress() {
        if (!isOperationInProgress()) { // but frontend has no clue
            startOperationInProgress();
            operationInProgressOwner = true;
            eskimoOperations.showOperations();
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

    this.handleMarathonSubsystem = function (enableMarathon) {
        let menuMarathonConfig = $("#menu-marathon-configuration");
        if (enableMarathon) {

            let menuRole = menuMarathonConfig.data("menu-role");

            if (menuRole == null || menuRole == "" || that.hasRole(menuRole)) {

                if (menuMarathonConfig.hasClass("menu-hidden")) {

                    menuMarathonConfig.css("visibility", "visible");
                    menuMarathonConfig.css("display", "inherit");
                    menuMarathonConfig.removeClass("menu-hidden")

                    // perhaps need to resize the menu ?
                    this.menuResize();
                }
            }
        } else {
            if (!menuMarathonConfig.hasClass("menu-hidden")) {
                menuMarathonConfig.css("visibility", "hidden");
                menuMarathonConfig.css("display", "none");
                menuMarathonConfig.addClass("menu-hidden")
            }
        }
    };

    function getDisplayedService () {
        let displayService = null;
        $(".inner-content").each(function (nbr, innerContent) {
            if ($(innerContent).css("visibility") == "visible") {
                displayService = $(innerContent).attr('id').substring("inner-content-".length);
            }
        });
        return displayService;
    }

    this.isCurrentDisplayedService = function (service) {
        let displayedService = getDisplayedService ();
        //console.log ("displayedService is : " + service);
        return displayedService == service;
    };

    function serviceMenuClear(nodeServicesStatus) {

        // remove all menu entries (cannot find out which service is here :-(
        if (!nodeServicesStatus) {

            $(".folder-menu-items").each(function () {

                let menuService = this.id.substring('folderMenu'.length);

                let service = getHyphenSeparated(menuService);

                $(this).attr("class", "folder-menu-items disabled");

                that.getServices().handleServiceHiding(service);
            });

        }
        // else check with system status and nodeServiceStatus
        else {

            $(".folder-menu-items").each(function () {

                let menuService = this.id.substring('folderMenu'.length);

                let service = getHyphenSeparated(menuService);

                let serviceUp = that.getSystemStatus().serviceIsUp(nodeServicesStatus, service);

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
        let contentProgressBar = $(".inner-content-show");
        contentProgressBar.css("visibility", "visible");
        contentProgressBar.css("display", "flex");
    };

    function hideProgressbar () {
        let contentProgressBar = $(".inner-content-show");
        contentProgressBar.css("visibility", "hidden");
        contentProgressBar.css("display", "none");
    }
    this.hideProgressbar = hideProgressbar;

    this.sidebarToggleClickedListener = function () {
        dontMessWithSidebarSizeAnyMore = true;
        that.menuResize();
    };

    this.setNavigationCompact = function () {
        let hoeAppContainer = $('#hoeapp-container');
        if (!dontMessWithSidebarSizeAnyMore && !hoeAppContainer.hasClass("hoe-minimized-lpanel")) {
            if ($('#hoeapp-wrapper').attr("hoe-device-type") !== "phone") {
                hoeAppContainer.toggleClass('hoe-minimized-lpanel');
                $('#hoe-header').toggleClass('hoe-minimized-lpanel');
                $('body').attr("hoe-navigation-type", "vertical-compact");
            } else {
                $('#hoeapp-wrapper').addClass('hoe-hide-lpanel');
            }
        }
    };

    this.handleSetupCompleted = function () {
        setupDone = true;

        $(".config-menu-items").each(function() {
            if (!$(this).hasClass("menu-hidden") && !($(this).hasClass("menu-static"))) {
                $(this).attr("class", "config-menu-items");
            }
        });
    };

    this.handleSetupNotCompleted = function () {

        setupDone = false;

        serviceMenuClear();

        $(".config-menu-items").each(function() {
            if (!$(this).hasClass("menu-hidden") && !($(this).hasClass("menu-static"))) {
                $(this).attr("class", "config-menu-items disabled");
            }
        });

        $("#menu-configure-setup").attr("class", "config-menu-items");
        $("#menu-operations").attr("class", "config-menu-items");
    };

    this.showSetupNotDone = function (message) {
        eskimoSetup.showSetup();

        if (message && message != "") {
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
    this.getOperations = function() {
        return eskimoOperations;
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
    this.getMarathonServicesSelection = function() {
        return eskimoMarathonServicesSelection;
    };
    this.getServicesSettings = function() {
        return eskimoServicesSettings;
    };
    this.getMarathonServicesConfig = function() {
        return eskimoMarathonServicesConfig;
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
    this.getMarathonOperationsCommand = function() {
        return eskimoMarathonOperationsCommand;
    };
    this.getSettingsOperationsCommand = function() {
        return eskimoSettingsOperationsCommand;
    };
    this.getAbout = function() {
        return eskimoAbout;
    };

    this.windowResize = function() {

        var viewPortHeight = $("body").innerHeight();
        var viewPortWidth = $("body").innerWidth();

        var headerHeight = $("#hoe-header").height();;

        $(".inner-content").css("height", (viewPortHeight - headerHeight) + "px");

        that.menuResize();
    };

    this.menuResize = function() {
        // alert (window.innerHeight + " - " + window.innerWidth);

        let actualMenuHeight = $("#menu-container").height();
        let menuContainerHeight = $("#hoe-left-panel").height();

        //console.log (menuContainerHeight + " -  " + actualMenuHeight);

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
            if (!$(node).hasClass("menu-hidden")) {
                $(node).css("display", "");
            }
        });
        menuHidingPos = 0;
    };

    function menuMouseWheel (event) {
        if (event.deltaY > 0) {
            menuUp();
        } else if (event.deltaY < 0) {
            menuDown();
        }
        event.preventDefault();
    }

    function menuUp(e) {

        if (menuHidingPos > 0) {

            menuHidingPos--;

            // browse both menu and unhide last hidden element
            // and deincrement menuHidingPos
            $("#menu-container > * > li").each(function(nbr, node) {
                if (nbr == menuHidingPos) {
                    if (!$(node).hasClass ("menu-hidden")) {
                        $(node).css("display", "");
                    }
                }
            });
        }

        if (e != null) {
            e.preventDefault();
        }
        return false;
    }
    this.menuUp = menuUp;

    function menuDown (e) {

        let menuContainerHeight = $("#hoe-left-panel").height();
        let actualMenuHeight = $("#menu-container").height();

        console.log(menuContainerHeight, actualMenuHeight, menuHidingPos);

        // IF AND ONLY IF size is not sufficient for current menu site, THEN
        if (menuContainerHeight - 80 < actualMenuHeight) {

            console.log ("OK");

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
    }
    this.menuDown = menuDown;

    this.initialize();
};


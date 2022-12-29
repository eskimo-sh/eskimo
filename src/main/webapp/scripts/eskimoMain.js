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
    let eskimoEditUser = null;

    let eskimoKubernetesServicesSelection = null;
    let eskimoKubernetesServicesConfig = null;

    let eskimoServicesSettings = null;
    let eskimoFileManagers = null;
    let eskimoOperationsCommand = null;
    let eskimoSetupCommand = null;
    let eskimoKubernetesOperationsCommand = null;
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
        eskimoKubernetesOperationsCommand = new eskimo.KubernetesOperationsCommand();
        eskimoSetupCommand = new eskimo.SetupCommand();
        eskimoConsoles = new eskimo.Consoles();
        eskimoFileManagers = new eskimo.FileManagers();
        eskimoServices = new eskimo.Services();
        eskimoServicesSelection = new eskimo.ServicesSelection({eskimoMain: that});
        eskimoServicesSettings = new eskimo.ServicesSettings();
        eskimoSettingsOperationsCommand = new eskimo.SettingsOperationsCommand();
        eskimoNodesConfig = new eskimo.NodesConfig();
        eskimoKubernetesServicesConfig = new eskimo.KubernetesServicesConfig();
        eskimoKubernetesServicesSelection = new eskimo.KubernetesServicesSelection();
        eskimoSystemStatus = new eskimo.SystemStatus();
        eskimoAbout = new eskimo.About();
        eskimoEditUser = new eskimo.EditUser();

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
            eskimoKubernetesServicesSelection: eskimoKubernetesServicesSelection,
            eskimoKubernetesServicesConfig: eskimoKubernetesServicesConfig,
            eskimoServicesSettings: eskimoServicesSettings,
            eskimoFileManagers: eskimoFileManagers,
            eskimoOperationsCommand: eskimoOperationsCommand,
            eskimoSetupCommand: eskimoSetupCommand,
            eskimoKubernetesOperationsCommand: eskimoKubernetesOperationsCommand,
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

        eskimoKubernetesOperationsCommand.initialize();
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

        eskimoKubernetesServicesConfig.initialize();
        // loadKubernetesServices -> get-kubernetes-services

        eskimoKubernetesServicesSelection.initialize();
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

        eskimoEditUser.initialize();

        $(window).resize (this.windowResize);

        // about
        $("#main-show-about-link").click(eskimoAbout.showAbout);

        // menu entries
        $("#main-menu-show-consoles-link").click(eskimoConsoles.showConsoles);
        $("#main-menu-show-file-managers-link").click(eskimoFileManagers.showFileManagers);
        $("#main-menu-show-status-link").click(eskimoSystemStatus.showStatus);
        $("#main-menu-show-setup-link").click(eskimoSetup.showSetup);
        $("#main-menu-show-services-settings-link").click(eskimoServicesSettings.showServicesSettings);
        $("#main-menu-show-nodes-config-link").click(eskimoNodesConfig.showNodesConfig);
        $("#main-menu-show-kubernetes-config-link").click(eskimoKubernetesServicesConfig.showKubernetesServicesConfig);
        $("#main-menu-show-operations-link").click(eskimoOperations.showOperations);
        $("#user-logout").click(function() {
            window.location = "logout";
        });

        this.windowResize();
    };

    this.initialize = function() {

        $(document).ready(function () {

            $("#hoeapp-wrapper").load("html/eskimoMain.html", function (responseTxt, statusTxt, jqXHR) {

                that.doInitializeInternal();

                new ThemeCustomizer().init();

                // This has to remain last
                // FIXME theme
                //initHoe();
            });
        });
    };

    function fetchContext  () {
        $.ajaxGet({
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

        $(".side-nav-item").each(function() {
            let menuRole = $(this).attr("data-menu-role");
            //console.log (this.id, menuRole, that.hasRole(menuRole), $(this).hasClass("visually-hidden"));
            if (menuRole != null && menuRole != "") {
                if (!that.hasRole(menuRole) && !$(this).hasClass("visually-hidden")) {
                    $(this).addClass ("visually-hidden");
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

        $("#main-content").scrollTop();

        if (isServiceIFrame) {
            $('#main-content').addClass("overflow-hidden");
        } else {
            $('#main-content').removeClass("overflow-hidden");
        }

        // if service iframe is already shown, clicking a second time on the link refreshed the iframe

        if (isServiceIFrame && $("#inner-content-" + content).css("visibility") == "visible") {

            eskimoServices.refreshIframe (content);

        } else {

            $.hideElement($(".inner-content"));
            $.showElement($("#inner-content-" + content));
        }
    };

    this.handleKubernetesSubsystem = function (enableKubernetes) {
        let menuKubernetesConfig = $("#menu-kubernetes-configuration");
        if (enableKubernetes) {

            let menuRole = menuKubernetesConfig.data("menu-role");

            if (menuRole == null || menuRole == "" || that.hasRole(menuRole)) {

                if (menuKubernetesConfig.hasClass("visually-hidden")) {

                    $.showElement(menuKubernetesConfig);
                    menuKubernetesConfig.removeClass("visually-hidden")
                }
            }
        } else {
            if (!menuKubernetesConfig.hasClass("visually-hidden")) {
                $.hideElement(menuKubernetesConfig);
                menuKubernetesConfig.addClass("visually-hidden")
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

                $(this).attr("class", "side-nav-item folder-menu-items disabled");

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
                    $(this).attr("class", "side-nav-item folder-menu-items disabled");
                    that.getServices().handleServiceHiding(service);
                } else {
                    $(this).attr("class", "side-nav-item folder-menu-items");
                }
            });
        }
    }
    this.serviceMenuClear = serviceMenuClear;

    this.showProgressbar = function () {
        $.showElement($(".inner-content-show"));
    };

    function hideProgressbar () {
        $.hideElement($(".inner-content-show"));
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

        $(".side-nav-item").each(function() {
            if (!$(this).hasClass("visually-hidden")
                && !($(this).hasClass("menu-static"))
                && !($(this).hasClass("side-nav-title"))
                && !($(this).hasClass("folder-menu-items"))) {
                $(this).attr("class", "side-nav-item");
            }
        });
    };

    this.handleSetupNotCompleted = function () {

        setupDone = false;

        serviceMenuClear();

        $(".side-nav-item").each(function() {
            if (!$(this).hasClass("visually-hidden")
                && !($(this).hasClass("menu-static"))
                && !($(this).hasClass("side-nav-title"))
                && !($(this).hasClass("folder-menu-items"))) {
                $(this).attr("class", "side-nav-item disabled");
            }
        });

        $("#menu-configure-setup").attr("class", "side-nav-item");
        $("#menu-operations").attr("class", "side-nav-item");
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
    this.getKubernetesServicesSelection = function() {
        return eskimoKubernetesServicesSelection;
    };
    this.getServicesSettings = function() {
        return eskimoServicesSettings;
    };
    this.getKubernetesServicesConfig = function() {
        return eskimoKubernetesServicesConfig;
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
    this.getKubernetesOperationsCommand = function() {
        return eskimoKubernetesOperationsCommand;
    };
    this.getSettingsOperationsCommand = function() {
        return eskimoSettingsOperationsCommand;
    };
    this.getAbout = function() {
        return eskimoAbout;
    };
    this.getEditUser = function() {
        return eskimoEditUser;
    }

    this.windowResize = function() {

        const body = $("body");
        const viewPortHeight = body.innerHeight();
        const viewPortWidth = body.innerWidth();

        let headerHeight = $("#hoe-header").height();

        $(".inner-content").css("height", (viewPortHeight - headerHeight) + "px");
    };


    this.initialize();
};


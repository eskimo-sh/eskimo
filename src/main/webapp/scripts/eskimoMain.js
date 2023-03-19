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
eskimo.Main = function() {

    const that = this;

    let setupLoaded = false;
    let setupDone = false;

    let eskimoSetup = null;
    let eskimoNodesConfig = null;
    let eskimoSystemStatus = null;
    let eskimoOperations = null;
    let eskimoConsoles = null;
    let eskimoNotifications = null;
    let eskimoServices = null;
    let eskimoServicesSelection = null;
    let eskimoEditUser = null;
    let eskimoMenu = null;
    let eskimoApp = null;

    let eskimoKubernetesServicesSelection = null;
    let eskimoKubernetesServicesConfig = null;

    let eskimoServicesSettings = null;
    let eskimoFileManagers = null;
    let eskimoOperationsCommand = null;
    let eskimoSetupCommand = null;
    let eskimoKubernetesOperationsCommand = null;
    let eskimoSettingsOperationsCommand = null;
    let eskimoAbout = null;
    let eskimoAlert = null;

    let operationInProgress = false;
    let operationInProgressRecovery = false;

    let userRoles = [];
    let userId = "";

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
        eskimoAlert = new eskimo.Alert();
        eskimoEditUser = new eskimo.EditUser();
        eskimoMenu = new eskimo.Menu();
        eskimoApp = new eskimo.App();

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
            eskimoAlert: eskimoAlert,
            eskimoMenu: eskimoMenu,
            eskimoApp: eskimoApp,
            eskimoEditUser: eskimoEditUser,
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

        // C. Initialize components
        eskimoMenu.initialize();

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

        eskimoAlert.initialize();

        eskimoApp.initialize();

        // about
        $("#main-show-about-link").click(eskimoAbout.showAbout);

    };

    this.initialize = function() {

        $(document).ready(() => {

            $("#hoeapp-wrapper").load("html/eskimoMain.html", () => {

                that.doInitializeInternal();

                new ThemeCustomizer().init();
            });
        });
    };

    function fetchContext  () {
        $.ajaxGet({
            url: "context",
            success: (data, status, jqXHR) => {

                if (data.status == "OK") {

                    userRoles = data.roles;
                    userId = data.user;
                    eskimoMenu.adaptMenuToUserRole();

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

    this.hasRole = function (role) {
        if (role == "*") {
            return true;
        }
        for (let i = 0; i < userRoles.length; i++) {
            if (userRoles[i] == role) {
                return true;
            }
        }
        return false;
    };

    this.getUserId = function() {
        return userId;
    }

    function isOperationInProgress() {
        return operationInProgress;
    }
    this.isOperationInProgress = isOperationInProgress;

    function isOperationInProgressRecovery() {
        return operationInProgressRecovery;
    }
    this.isOperationInProgressRecovery = isOperationInProgressRecovery;

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
        eskimoOperations.stopOperationInProgress (success, () => {
            setOperationInProgress (false);
            operationInProgressRecovery = false;
            hideProgressbar();
        });

    }
    this.scheduleStopOperationInProgress = scheduleStopOperationInProgress;

    function recoverOperationInProgress() {
        if (!isOperationInProgress()) { // but frontend has no clue
            startOperationInProgress();
            operationInProgressRecovery = true;
            eskimoOperations.showOperations();
        }
    }
    this.recoverOperationInProgress = recoverOperationInProgress;

    this.showOnlyContent = function (content, isServiceIFrame) {

        const $mainContent = $("#main-content");

        $mainContent.scrollTop();

        eskimoMenu.setActiveMenuEntry (content, isServiceIFrame);

        // if service iframe is already shown, clicking a second time on the link refreshed the iframe

        const $selectedInnerContent = $("#inner-content-" + content);
        if (isServiceIFrame && $selectedInnerContent.css("visibility") === "visible") {

            eskimoServices.refreshIframe (content);

        } else {

            $.hideElement($(".inner-content"));
            $.showElement($selectedInnerContent);
        }

        if (isServiceIFrame) {
            $mainContent.addClass("overflow-hidden");
        } else {
            $mainContent.removeClass("overflow-hidden");
        }
    };

    this.handleKubernetesSubsystem = function (enableKubernetes) {
        eskimoMenu.handleKubeMenuDisplay (enableKubernetes);
    };

    function getDisplayedScreen () {
        let displayedScreen = null;
        $(".inner-content").each(function (nbr, innerContent) {
            if ($(innerContent).css("visibility") === "visible") {
                displayedScreen = $(innerContent).attr('id').substring("inner-content-".length);
            }
        });
        return displayedScreen;
    }

    this.isCurrentDisplayedScreen = function (screen) {
        let displayedScreen = getDisplayedScreen ();
        //console.log ("displayedService is : " + service);
        return displayedScreen === screen;
    };

    this.showProgressbar = function () {
        $.showElement($(".inner-content-show"));
    };

    function hideProgressbar () {
        $.hideElement($(".inner-content-show"));
    }
    this.hideProgressbar = hideProgressbar;

    this.handleSetupCompleted = function () {
        setupDone = true;

        eskimoMenu.handleSetupCompleted();
    };

    this.handleSetupNotCompleted = function () {

        setupDone = false;

        eskimoMenu.serviceMenuClear();

        eskimoMenu.handleSetupNotCompleted();
    };

    this.showSetupNotDone = function (message) {
        eskimoSetup.showSetup();

        if (message && message != "") {
            that.alert(ESKIMO_ALERT_LEVEL.ERROR, message);
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
    this.getAlert = function() {
        return eskimoAlert;
    };
    this.getEditUser = function() {
        return eskimoEditUser;
    }
    this.getMenu = function() {
        return eskimoMenu;
    }

    this.alert = function(level, message, callback) {
        eskimoAlert.showAlert(level, message, callback);
    }

    this.confirm = function(message, confirmCallback, closeCallback) {
        eskimoAlert.confirm(message, confirmCallback, closeCallback);
    }

    this.initialize();
};


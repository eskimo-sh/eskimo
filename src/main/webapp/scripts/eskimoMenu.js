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
eskimo.Menu = function() {

    const that = this;

    this.eskimoMain = null;
    this.eskimoConsoles = null;
    this.eskimoFileManagers = null;
    this.eskimoSystemStatus = null;
    this.eskimoSetup = null;
    this.eskimoServicesSettings = null;
    this.eskimoNodesConfig = null;
    this.eskimoKubernetesServicesConfig = null;
    this.eskimoOperations = null;
    this.eskimoServices = null;

    this.initialize = function() {
        // Initialize HTML Div from Template
        $("#left-menu-placeholder").load("html/eskimoMenu.html", (responseTxt, statusTxt, jqXHR) => {

            if (statusTxt === "success") {

                // menu entries
                $("#main-menu-show-consoles-link").click(that.eskimoConsoles.showConsoles);
                $("#main-menu-show-file-managers-link").click(that.eskimoFileManagers.showFileManagers);
                $("#main-menu-show-status-link").click(that.eskimoSystemStatus.showStatus);
                $("#main-menu-show-setup-link").click(that.eskimoSetup.showSetup);
                $("#main-menu-show-services-settings-link").click(that.eskimoServicesSettings.showServicesSettings);
                $("#main-menu-show-nodes-config-link").click(that.eskimoNodesConfig.showNodesConfig);
                $("#main-menu-show-kubernetes-services-config-link").click(that.eskimoKubernetesServicesConfig.showKubernetesServicesConfig);
                $("#main-menu-show-operations-link").click(that.eskimoOperations.showOperations);
                $("#user-logout").click(() => { window.location = "logout"; });

                $(".simplebar-wrapper").mouseover(sidebarMouseover);

            } else if (statusTxt === "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });
    };

    function sidebarMouseover(e) {
        let x = e.pageX - this.offsetLeft;

        const html = document.getElementsByTagName('html')[0]

        let size = html.getAttribute('data-sidenav-size');

        const $simplebarContentWrapper = $(".simplebar-content-wrapper");
        const $simplebarOffset = $(".simplebar-offset");

        if (size !== 'full') {
            if (size === 'condensed') {
                if (x > 70) {
                    $simplebarContentWrapper.css("width", "");
                    $simplebarOffset.css("width", "");
                } else {
                    $simplebarContentWrapper.css("width", "260px");
                    $simplebarOffset.css("width", "260px");
                }
            } else {
                if ($simplebarContentWrapper.get(0).style.width === "260px") { // don't use $().css
                    $simplebarContentWrapper.css("width", "");
                }
                if ($simplebarOffset.get(0).style.width === "260px") { // don't use $().css
                    $simplebarOffset.css("width", "");
                }
            }
        } else {
            if ($simplebarContentWrapper.get(0).style.width === "260px") { // don't use $().css
                $simplebarContentWrapper.css("width", "");
            }
            if ($simplebarOffset.get(0).style.width === "260px") { // don't use $().css
                $simplebarOffset.css("width", "");
            }
        }
        if (x > 70) {
            $(".side-nav-item").addClass("nohover");
        } else {
            $(".side-nav-item").removeClass("nohover");
        }
    }
    this.sidebarMouseover = sidebarMouseover;

    this.adaptMenuToUserRole = function () {

        $(".side-nav-item").each(function() {
            let menuRole = $(this).attr("data-menu-role");
            //console.log (this.id, menuRole, eskimoMain.hasRole(menuRole), $(this).hasClass("visually-hidden"));
            if (menuRole != null && menuRole != "") {
                if (!eskimoMain.hasRole(menuRole) && !$(this).hasClass("visually-hidden")) {
                    $(this).addClass ("visually-hidden");
                    $(this).css ("display", "none");
                }
            }
        });
    };

    this.enforceMenuConsisteny = function () {
        // menu consistency checks (naming conventions)
        const menuIdsToCheck = [];
        $(".side-nav-link").each(function (target) {
            if (this.id.indexOf("main-menu-show-") <= -1 || this.id.indexOf("link") <= -1) {
                if (this.id.indexOf("services-menu_") <= -1) {
                    eskimoAlert.showAlert(ESKIMO_ALERT_LEVEL.ERROR,
                        "menu with id '" + this.id + "' is expected of having an id of form 'main-menu-show-XXX-link'. There will be inconsistencies down the line.");
                }
            } else {
                menuIdsToCheck.push (this.id.substring("main-menu-show-".length, this.id.indexOf("link") - 1));
            }
        });
        for (let i = 0; i < menuIdsToCheck.length; i++) {
            if ($("#inner-content-" + menuIdsToCheck[i]).length === 0) {
                eskimoAlert.showAlert(ESKIMO_ALERT_LEVEL.ERROR, "No target screen found with id 'inner-content-" + menuIdsToCheck[i] + "'");
            }
        }
    }

    this.setActiveMenuEntry = function(content, isServiceIFrame) {

        // reset current menu entry everywhwre
        $(".side-nav-item").each(function (target) {
            let classname = $(this).attr('class');
            if (classname.indexOf('menuitem-active') > -1) {
                $(this).removeClass('menuitem-active');
            }
        });

        if (isServiceIFrame) {
            $("#services-menu_" + content).parent().addClass("menuitem-active");
        } else {
            $("#main-menu-show-" + content + "-link").parent().addClass("menuitem-active");
        }
    }

    this.handleKubeMenuDisplay = function (enableKubernetes) {
        let menuKubernetesConfig = $("#menu-kubernetes-configuration");
        if (enableKubernetes) {

            let menuRole = menuKubernetesConfig.data("menu-role");

            if (menuRole == null || menuRole == "" || eskimoMain.hasRole(menuRole)) {

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
    }

    this.serviceMenuClear = function (nodeServicesStatus) {

        // remove all menu entries (cannot find out which service is here :-(
        if (!nodeServicesStatus) {

            $(".folder-menu-items").each(function () {

                let menuService = this.id.substring('folderMenu'.length);

                let service = getHyphenSeparated(menuService);

                $(this).attr("class", "side-nav-item folder-menu-items disabled");

                that.eskimoServices.handleServiceHiding(service);
            });

        }
        // else check with system status and nodeServiceStatus
        else {

            $(".folder-menu-items").each(function () {

                let menuService = this.id.substring('folderMenu'.length);

                let service = getHyphenSeparated(menuService);

                let serviceUp = that.eskimoSystemStatus.serviceIsUp(nodeServicesStatus, service);

                if (!serviceUp || !that.eskimoServices.isServiceAvailable(service)) {
                    $(this).attr("class", "side-nav-item folder-menu-items disabled");
                    that.eskimoServices.handleServiceHiding(service);

                } else if ($(this).hasClass("menuitem-active")) {
                    $(this).attr("class", "side-nav-item folder-menu-items menuitem-active");

                } else {
                    $(this).attr("class", "side-nav-item folder-menu-items");
                }
            });
        }
    };

    this.handleSetupNotCompleted = function () {
        $(".side-nav-item").each(function() {
            if (!$(this).hasClass("visually-hidden")
                && !($(this).hasClass("menu-static"))
                && !($(this).hasClass("side-nav-title"))
                && !($(this).hasClass("folder-menu-items"))) {
                $(this).attr("class", "side-nav-item disabled");
            }
            // force unselecting menu (this is done already by eskimoMain.showOnlyContent, but I do it here as well for
            // consistency
            if ($(this).hasClass("folder-menu-items") && $(this).hasClass("menuitem-active")) {
                $(this).removeClass("menuitem-active");
            }
        });

        $("#menu-configure-setup").attr("class", "side-nav-item menuitem-active");
        $("#menu-operations").attr("class", "side-nav-item");
    };

    this.handleSetupCompleted = function () {
        $(".side-nav-item").each(function() {
            if (!$(this).hasClass("visually-hidden")
                && !($(this).hasClass("menu-static"))
                && !($(this).hasClass("side-nav-title"))
                && !($(this).hasClass("folder-menu-items"))) {

                if ($(this).hasClass("menuitem-active")) {
                    $(this).attr("class", "side-nav-item menuitem-active");
                } else {
                    $(this).attr("class", "side-nav-item");
                }
            }
        });
    };

    this.createServicesMenu = function (uiServices, uiServicesConfig) {

        for (let i = uiServices.length - 1; i >= 0; i--) {

            let service = uiServices[i];

            let uiConfig = uiServicesConfig[service];

            if (eskimoMain.hasRole(uiConfig.role)) {

                let menuEntry = '' +
                    '<li class="side-nav-item folder-menu-items disabled" id="folderMenu' + getUcfirst(getCamelCase(service)) + '">\n' +
                    '    <a id="services-menu_' + service + '" href="#" class="side-nav-link">\n' +
                    '        <em><img alt="' + service + ' icon" src="' + that.eskimoNodesConfig.getServiceIconPath(service) + '"></em>\n' +
                    '        <span class="menu-text">' + uiConfig.title + '</span>\n' +
                    '        <span class="badge rounded-pill bg-secondary text-light font-11 align-middle float-end">&nbsp;Off&nbsp;</span>' +
                    '    </a>\n' +
                    '</li>';

                $("#mainFolderMenuAnchor").after(menuEntry);

                $("#services-menu_" + service).click(function () {
                    let serviceName = this.id.substring("services-menu_".length);
                    that.eskimoServices.showServiceIFrame(serviceName);
                });
            }
        }
    };
};
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
eskimo.Services = function () {

    var that = this;

    UI_SERVICES = [];
    UI_SERVICES_CONFIG = {};

    var serviceInitialized = {};

    EMPTY_FRAMETARGET = "html/emptyPage.html";

    this.initialize = function () {

        loadUIServicesConfig();
    };

    function loadUIServicesConfig() {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "get-ui-services-config",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    UI_SERVICES_CONFIG = data.uiServicesConfig;
                    loadUIServices();

                } else {
                    alert(data.error);
                }
            },
            error: errorHandler
        });
    }

    function loadUIServices() {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "list-ui-services",
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    UI_SERVICES = data.uiServices;
                    createServicesIFrames();
                    createServicesMenu();

                } else {
                    alert(data.error);
                }
            },
            error: errorHandler
        });
    }

    /* For tests */
    this.setEmptyFrameTarget = function (emptyFrameTarget) {
        EMPTY_FRAMETARGET = emptyFrameTarget;
    };
    this.setUiServices = function (uiServices) {
        UI_SERVICES = uiServices;
    };
    this.setUiServicesConfig = function (uiServicesConfig) {
        UI_SERVICES_CONFIG = uiServicesConfig;
    };

    function showServiceIFrame(service) {

        if (!eskimoMain.isSetupDone()) {

            showSetupNotDone("Service " + service + " is not available at this stage.");

        } else {

            if (serviceInitialized[service]) {

                // maybe Progress bar was shown previously
                eskimoMain.hideProgressbar();

                eskimoMain.setNavigationCompact();

                eskimoMain.showOnlyContent(service, true);

            } else {

                eskimoMain.getSystemStatus().showStatusWhenServiceUnavailable(service);
            }
        }
    }

    this.showServiceIFrame = showServiceIFrame;

    function checkIFrames() {

        // Unfortunately, as long as I don't procy everything, I cannot check iframe content due to cross-orifins policy
        // being enforced by browsers : the parent frame cannot access iframe content

    }

    this.refreshIframe = function (service) {

        var uiConfig = UI_SERVICES_CONFIG[service];

        // reset to empty frame ...
        $("#iframe-content-" + service).attr('src', EMPTY_FRAMETARGET);

        // ... and back to URL
        setTimeout(function () {
            $("#iframe-content-" + service).attr('src', uiConfig.actualUrl);
        });
    };

    function buildUrl(uiConfig, nodeAddress) {
        var actualUrl = null;
        if (uiConfig.urlTemplate != null && uiConfig.urlTemplate != "") {

            var indexOfNodeAddress = uiConfig.urlTemplate.indexOf("{NODE_ADDRESS}");

            if (indexOfNodeAddress <= 0) {
                actualUrl = uiConfig.urlTemplate;
            } else {

                actualUrl = uiConfig.urlTemplate.substring(0, indexOfNodeAddress)
                    + nodeAddress
                    + uiConfig.urlTemplate.substring(indexOfNodeAddress + 14);
            }
        } else {

            if (uiConfig.proxyContext == null || uiConfig.proxyContext == "") {
                throw "uiConfig.proxyContext is empty !";
            }

            if (uiConfig.unique) {
                actualUrl = uiConfig.proxyContext;
            } else {
                actualUrl = uiConfig.proxyContext + "/" + nodeAddress.replace(/\./g, "-");
            }
        }
        return actualUrl;
    }

    /* For tests */
    this.buildUrl = buildUrl;

    function shouldReinitialize(service, nodeAddress) {

        var uiConfig = UI_SERVICES_CONFIG[service];

        var urlChanged = false;
        if (uiConfig.actualUrl != null && uiConfig.actualUrl != "") {
            var newUrl = buildUrl(uiConfig, nodeAddress);
            if (uiConfig.actualUrl != newUrl) {
                urlChanged = true;
            }
        } else {
            urlChanged = true;
        }

        return urlChanged;
    }

    /* For tests */
    this.shouldReinitialize = shouldReinitialize;

    function serviceMenuServiceFoundHook(nodeName, nodeAddress, service, found, immediate) {

        var uiConfig = UI_SERVICES_CONFIG[service];

        if (uiConfig != null) {

            var reinitialize = shouldReinitialize(service, nodeAddress);

            var waitTime = immediate || !reinitialize ? 0 : uiConfig.waitTime;

            /*
            console.log(service + " - " + found + " - " + immediate + " - " + reinitialize + " - " + uiConfig.waitTime);
            console.log ((immediate || !reinitialize) + " - " + waitTime);
            */

            var serviceMenu = $("#folderMenu" + getUcfirst(getCamelCase(service)));

            if (found) {

                if (waitTime == 0) { // avoid setTimeout if not needed, it avoids the UI to blink
                    serviceMenu.attr("class", "folder-menu-items");
                } else {
                    setTimeout(function () {
                        serviceMenu.attr("class", "folder-menu-items");
                    }, waitTime);
                }

                if ((!serviceInitialized[service] || reinitialize) && !uiConfig.refreshWaiting) {
                    setTimeout(function () {
                        uiConfig.actualUrl = buildUrl(uiConfig, nodeAddress);
                        $("#iframe-content-" + service).attr('src', uiConfig.actualUrl);
                        uiConfig.refreshWaiting = false;
                    }, waitTime);
                    uiConfig.refreshWaiting = true;
                    serviceInitialized[service] = true;
                }
            } else {

                serviceMenu.attr("class", "folder-menu-items disabled");

                if (serviceInitialized[service]) {
                    $("#iframe-content-" + service).attr('src', EMPTY_FRAMETARGET);
                    serviceInitialized[service] = false;
                    uiConfig.actualUrl = null;
                }

                // if service was displayed, show Status
                if (eskimoMain.isCurrentDisplayedService(service)) {
                    eskimoMain.getSystemStatus().showStatus();
                }

            }
        }
    }

    this.serviceMenuServiceFoundHook = serviceMenuServiceFoundHook;

    function createServicesMenu() {

        for (var i = UI_SERVICES.length - 1; i >= 0; i--) {

            var service = UI_SERVICES[i];

            var uiConfig = UI_SERVICES_CONFIG[service];

            var menuEntry = '' +
                '<li class="folder-menu-items disabled" id="folderMenu' + getUcfirst(getCamelCase(service)) + '">\n' +
                '    <a href="javascript:eskimoMain.getServices().showServiceIFrame(\'' + service + '\');">\n' +
                '        <img src="' + uiConfig.icon + '"></img>\n' +
                '        <span class="menu-text">' + uiConfig.title + '</span>\n' +
                '    </a>\n' +
                '</li>';

            $("#mainFolderMenuAnchor").after(menuEntry);
        }
    }

    /** for tests */
    this.createServicesMenu = createServicesMenu;

    function createServicesIFrames() {

        for (var i = 0; i < UI_SERVICES.length; i++) {

            var service = UI_SERVICES[i];

            serviceInitialized[service] = false;

            var iframeWrapperString = '' +
                '<div class="inner-content inner-content-frame-container" id="inner-content-' + service + '" style="visibility: hidden;">\n' +
                '    <div id="' + service + '-management"\n' +
                '         class="panel theme-panel inner-content-inner inner-content-inner-frame" >\n' +
                '        <div class="service-container" id="' + service + '-container" >\n' +
                '          <iframe class="inner-content-iframe" id="iframe-content-' + service + '" src="' + EMPTY_FRAMETARGET + '">\n' +
                '          </iframe>\n' +
                '        </div>\n' +
                '    </div>\n' +
                '</div>';

            $("#main-content").append($(iframeWrapperString));
        }
    }

    /** for tests */
    this.createServicesIFrames = createServicesIFrames;


    // call constructor
    this.initialize();
};


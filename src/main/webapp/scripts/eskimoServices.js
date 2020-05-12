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
eskimo.Services = function (constructorObject) {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;

    var PERIODIC_RETRY_SERVICE_MS = 2000;

    var DEBUG = false;

    var that = this;

    var UI_SERVICES = [];
    var UI_SERVICES_CONFIG = {};

    var serviceInitialized = {};

    var uiConfigsToRetry = [];

    var EMPTY_FRAMETARGET = "html/emptyPage.html";

    this.initialize = function () {

        loadUIServicesConfig();

        setTimeout(that.periodicRetryServices, PERIODIC_RETRY_SERVICE_MS / 2);
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
    this.setServiceInitializedForTests = function (service) {
        serviceInitialized[service] = true;
    };
    this.setServiceNotInitializedForTests = function (service) {
        serviceInitialized[service] = false;
    };

    function showServiceIFrame(service) {

        if (!that.eskimoMain.isSetupDone()) {

            that.eskimoMain.showSetupNotDone("Service " + service + " is not available at this stage.");

        } else if (that.eskimoMain.getSystemStatus().isDisconnected()) {

            that.eskimoMain.showStatus();

        } else {

            if (serviceInitialized[service]) {

                // maybe Progress bar was shown previously
                that.eskimoMain.hideProgressbar();

                that.eskimoMain.setNavigationCompact();

                that.eskimoMain.showOnlyContent(service, true);

            } else {

                that.eskimoMain.getSystemStatus().showStatusWhenServiceUnavailable(service);
            }
        }
    }
    this.showServiceIFrame = showServiceIFrame;

    this.isServiceAvailable = function (service) {

        var uiConfig = UI_SERVICES_CONFIG[service];
        if (uiConfig == null) {
            console.log ("service " + service + " - has not uiConfig (A)");
            return false;
        }

        if (uiConfigsToRetry.length > 0 && uiConfigsToRetry.includes(uiConfig)) {
            console.log ("service " + service + " - is pending retry (B)");
            return false;
        }

        if (uiConfig.refreshWaiting) {
            console.log ("service " + service + " - is pending refresh (C)");
            return false;
        }

        return serviceInitialized[service];
    };

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

    function disableFrameConsole (iFrameId) {
        if (!DEBUG) {
            setTimeout(function () {
                var iFrame = $(iFrameId).get(0);
                if (iFrame && iFrame != null) {
                    iFrame.contentWindow.console.log = function () {
                        // No Op
                    };
                }
            });
        }
    }

    function removeFromRetry (uiConfig) {
        // remove from retry list
        for (var j = 0; j < uiConfigsToRetry.length; j++) {
            if (uiConfigsToRetry[j] == uiConfig) {
                //console.trace();
                console.log("removing from retry " + uiConfigsToRetry[j].service);
                uiConfigsToRetry.splice(j, 1);
                break;
            }
        }
    }

    function handleServiceIsUp(effUIConfig) {

        // remove from retry list
        removeFromRetry (effUIConfig);

        // schedule usual refresh
        setTimeout(function (otherUIConfig) {

            // iframe
            otherUIConfig.actualUrl = otherUIConfig.targetUrl;
            $("#iframe-content-" + otherUIConfig.service).attr('src', otherUIConfig.actualUrl);
            otherUIConfig.refreshWaiting = false;

            disableFrameConsole("#iframe-content-" + otherUIConfig.service);


        }, effUIConfig.targetWaitTime, effUIConfig);
    }
    this.handleServiceIsUp = handleServiceIsUp;

    this.getUIConfigsToRetryForTests = function() {
        return uiConfigsToRetry;
    };

    this.periodicRetryServices = function() {

        //console.log ("periodicRetryServices - " + uiConfigsToRetry.length);

        for (var i = 0; i < uiConfigsToRetry.length; i++) {

            var uiConfig = uiConfigsToRetry[i];

            setTimeout (function (effUIConfig) {
                $.ajax({
                    type: "GET",
                    url: effUIConfig.targetUrl,
                    success: function (data, status, jqXHR) {

                        console.log("FOUND : " + effUIConfig.service + " - " + effUIConfig.targetUrl + " - " + effUIConfig.targetWaitTime);

                        handleServiceIsUp(effUIConfig);

                    },
                    error: function (jqXHR, status) {
                        // ignore
                        console.log("error : " + effUIConfig.service + " - " + effUIConfig.targetUrl);
                    }
                });
            }, 0, uiConfig);
        }

        setTimeout(that.periodicRetryServices, PERIODIC_RETRY_SERVICE_MS);
    };

    function alreadyInRetry (uiConfig) {
        for (var i = 0; i < uiConfigsToRetry.length; i++) {
            var inUiConfig = uiConfigsToRetry[i];
            if (inUiConfig.title == uiConfig.title) {
                return true;
            }
        }
        return false;
    }

    this.handleServiceDisplay = function (service, uiConfig, nodeAddress, immediate) {

        //var serviceMenu = $("#folderMenu" + getUcfirst(getCamelCase(service)));

        var reinitialize = shouldReinitialize(service, nodeAddress);

        var waitTime = (immediate || !reinitialize) ? 0 : uiConfig.waitTime;

        /*
        console.log ("service display : " + service +
            " - uiConfig.title? " + uiConfig.title +
            " - immediate ? " + immediate +
            " - reinitialize ? " + reinitialize +
            " - waitTime : " +  uiConfig.waitTime +
            " - refreshWaiting : " + uiConfig.refreshWaiting +
            " - serviceInitialized? : " + serviceInitialized[service] +
            " - condition? : " + ((!serviceInitialized[service] || reinitialize) && !uiConfig.refreshWaiting) +
            " - uiConfig.includes? : " + uiConfigsToRetry.includes(uiConfig));
        */

        if ((!serviceInitialized[service] || reinitialize) && !uiConfig.refreshWaiting) {

            uiConfig.targetUrl = buildUrl(uiConfig, nodeAddress);
            uiConfig.service = service;
            uiConfig.targetWaitTime = waitTime;

            uiConfig.refreshWaiting = true;

            serviceInitialized[uiConfig.service] = true;

            if (uiConfigsToRetry.length == 0 || !alreadyInRetry (uiConfig)) {
                //console.log("Adding retry service " + uiConfig.service);
                uiConfigsToRetry.push(uiConfig);
            }
        }
    };

    this.handleServiceHiding = function (service, uiConfig) {

        var comingFromMenuHook = true;
        if (!uiConfig || uiConfig == null) { // if coming from eskimoMain.serviceMenuClear()
            uiConfig = UI_SERVICES_CONFIG[service];
            comingFromMenuHook = false;
        }

        if (uiConfig == null) {
            //console.error ("Couldn't find config for " + service);
            return;
        }

        //console.log ("Hiding " + service);

        /*
        // menu
        var serviceMenu = $("#folderMenu" + getUcfirst(getCamelCase(service)));
        serviceMenu.attr("class", "folder-menu-items disabled");
        */

        // iframe
        if (serviceInitialized[service]) {
            $("#iframe-content-" + service).attr('src', EMPTY_FRAMETARGET);
            serviceInitialized[service] = false;
            uiConfig.actualUrl = null;
        }

        // comingFromMenuHook is false if the hiding is triggered by the periodic menu cleaning in prealable of service
        // injection (eskimoMain.serviceMenuClear())
        // => in this case we don't want to mess with retry yet (not as long as we don't know newest status)
        if (comingFromMenuHook) {
            // If there as a retry running on service, drop it
            // (remove from retry list)
            removeFromRetry(uiConfig);
            uiConfig.refreshWaiting = false;
        }

        // if service was displayed, show Status
        if (that.eskimoMain.isCurrentDisplayedService(service)) {
            that.eskimoMain.getSystemStatus().showStatus();
        }
    };

    function serviceMenuServiceFoundHook(nodeName, nodeAddress, service, found, immediate) {

        var uiConfig = UI_SERVICES_CONFIG[service];

        if (uiConfig != null) {

            if (found) {

                // handle iframe display
                that.handleServiceDisplay(service, uiConfig, nodeAddress, immediate);

            } else {

                // handle iframe hiding
                that.handleServiceHiding(service, uiConfig);
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
                '    <a id="services-menu_' + service + '" href="#">\n' +
                '        <img src="' + that.eskimoMain.getNodesConfig().getServiceIconPath(service) + '"></img>\n' +
                '        <span class="menu-text">' + uiConfig.title + '</span>\n' +
                '    </a>\n' +
                '</li>';

            $("#mainFolderMenuAnchor").after(menuEntry);

            $("#services-menu_" + service).click(function() {
                var serviceName = this.id.substring("services-menu_".length);
                showServiceIFrame(serviceName);
            });
        }

        that.eskimoMain.menuResize();
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

    // inject constructor object in the end
    if (constructorObject != null) {
        $.extend(this, constructorObject);
    }
};


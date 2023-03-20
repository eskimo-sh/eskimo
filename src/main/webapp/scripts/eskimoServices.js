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
eskimo.Services = function () {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;
    this.eskimoSystemStatus = null;
    this.eskimoNodesConfig = null;
    this.eskimoMenu = null;

    const PERIODIC_RETRY_SERVICE_MS = 5000;

    const DEBUG = false;

    const that = this;

    let EMPTY_FRAMETARGET = "html/emptyPage.html";

    let UI_SERVICES = [];
    let UI_SERVICES_CONFIG = {};

    let serviceInitialized = {};

    let uiConfigsToRetry = [];


    this.initialize = function () {

        loadUIServicesConfig();

        setTimeout(that.periodicRetryServices, PERIODIC_RETRY_SERVICE_MS / 2);
    };

    function loadUIServicesConfig() {
        $.ajaxGet({
            url: "get-ui-services-config",
            success: (data, status, jqXHR) => {

                if (data.status == "OK") {

                    UI_SERVICES_CONFIG = data.uiServicesConfig;
                    loadUIServices();

                } else {
                    eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error);
                }
            },
            error: errorHandler
        });
    }

    function loadUIServices() {
        $.ajaxGet({
            url: "list-ui-services",
            success: (data, status, jqXHR) => {

                if (data.status == "OK") {

                    UI_SERVICES = data.uiServices;
                    createServicesIFrames();
                    that.eskimoMenu.createServicesMenu(UI_SERVICES, UI_SERVICES_CONFIG);

                } else {
                    eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error);
                }
            },
            error: errorHandler
        });
    }

    /* For tests */
    this.setEmptyFrameTarget = function (emptyFrameTarget) {
        EMPTY_FRAMETARGET = emptyFrameTarget;
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

        } else if (that.eskimoSystemStatus.isDisconnected()) {

            that.eskimoSystemStatus.showStatus();

        } else {

            if (serviceInitialized[service]) {

                // maybe Progress bar was shown previously
                that.eskimoMain.hideProgressbar();

                that.eskimoMain.showOnlyContent(service, true);

            } else {

                that.eskimoSystemStatus.showStatusWhenServiceUnavailable(service);
            }
        }
    }
    this.showServiceIFrame = showServiceIFrame;

    this.isServiceAvailable = function (service) {

        let uiConfig = UI_SERVICES_CONFIG[service];
        if (uiConfig == null) {
            //console.log ("service " + service + " - has not uiConfig (A)");
            return false;
        }

        if (!eskimoMain.hasRole (uiConfig.role)) {
            return false;
        }

        if (uiConfigsToRetry.length > 0 && alreadyInRetry(uiConfig)) {
            //console.log ("service " + service + " - is pending retry (B)");
            return false;
        }

        if (uiConfig.refreshWaiting) {
            //console.log ("service " + service + " - is pending refresh (C)");
            return false;
        }

        return serviceInitialized[service];
    };

    this.randomizeUrl = function (url) {
        if (!url || (""+url) === "") {
            return url;
        }

        let indexOfQuest = url.indexOf("?");
        let indexOfHash = url.indexOf("#");

        if (indexOfQuest < 0 && indexOfHash < 0) {

            return url + "?dummyarg=" + Math.random();

        } else if (indexOfQuest < 0 || (indexOfHash > 0 && indexOfQuest > indexOfHash)) {

            return url.substring (0, indexOfHash)
                + "?dummyarg=" + Math.random()
                + url.substring (indexOfHash);

        } else if (indexOfHash < 0) {

            return url + "&dummyarg=" + Math.random();

        } else {

            return url.substring (0, indexOfHash)
                + "&dummyarg=" + Math.random()
                + url.substring (indexOfHash);
        }
    }

    this.refreshIframe = function (service) {

        let uiConfig = UI_SERVICES_CONFIG[service];

        if (eskimoMain.hasRole (uiConfig.role)) {

            $("#iframe-content-" + service).attr('src', that.randomizeUrl (uiConfig.actualUrl));
        }
    };

    function buildUrl(uiConfig, node) {
        let actualUrl = null;
        if (uiConfig.urlTemplate != null && uiConfig.urlTemplate != "") {

            let indexOfNodeAddress = uiConfig.urlTemplate.indexOf("{NODE_ADDRESS}");

            if (indexOfNodeAddress <= 0) {
                actualUrl = uiConfig.urlTemplate;
            } else {

                actualUrl = uiConfig.urlTemplate.substring(0, indexOfNodeAddress)
                    + node.replace(/\./g, "-")
                    + uiConfig.urlTemplate.substring(indexOfNodeAddress + 14);
            }
        } else {

            if (uiConfig.proxyContext == null || uiConfig.proxyContext == "") {
                throw "uiConfig.proxyContext is empty !";
            }

            if (uiConfig.unique) {
                actualUrl = uiConfig.proxyContext;
            } else {
                actualUrl = uiConfig.proxyContext
                    + (uiConfig.proxyContext.endsWith ("/") ? "" : "/")
                    + node.replace(/\./g, "-");
            }
        }
        return actualUrl;
    }
    /* For tests */
    this.buildUrl = buildUrl;

    function shouldReinitialize(service, node) {

        let uiConfig = UI_SERVICES_CONFIG[service];

        let urlChanged = false;
        if (eskimoMain.hasRole (uiConfig.role)) {

            if (uiConfig.actualUrl != null && uiConfig.actualUrl != "") {
                let newUrl = buildUrl(uiConfig, node);
                if (uiConfig.actualUrl != newUrl) {
                    urlChanged = true;
                }
            } else {
                urlChanged = true;
            }
        }

        return urlChanged;
    }
    /* For tests */
    this.shouldReinitialize = shouldReinitialize;

    function disableFrameConsole (iFrameId) {
        if (!DEBUG) {
            setTimeout(() => {
                let iFrame = $(iFrameId).get(0);
                if (iFrame) {
                    iFrame.contentWindow.console.log = function () {
                        // No Op
                    };
                }
            });
        }
    }

    function removeFromRetry (uiConfig) {
        // remove from retry list
        for (let j = 0; j < uiConfigsToRetry.length; j++) {
            if (uiConfigsToRetry[j] == uiConfig) {
                //console.trace();
                //console.log("removing from retry " + uiConfigsToRetry[j].service);
                uiConfigsToRetry.splice(j, 1);
                break;
            }
        }
    }

    function handleServiceIsUp(effUIConfig) {

        // remove from retry list
        removeFromRetry (effUIConfig);

        // schedule usual refresh
        setTimeout(otherUIConfig => {

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

        for (let i = 0; i < uiConfigsToRetry.length; i++) {

            let uiConfig = uiConfigsToRetry[i];

            setTimeout (effUIConfig => {
                $.ajax({
                    type: "GET",
                    timeout: 1000 * 10, // 10 secs. We don't want this call to hang !
                    url: effUIConfig.targetUrl,
                    success: (data, status, jqXHR) => {

                        console.log("FOUND : " + effUIConfig.service + " - " + effUIConfig.targetUrl + " - " + effUIConfig.targetWaitTime);

                        handleServiceIsUp(effUIConfig);

                    },
                    error: (jqXHR, status) => {
                        // ignore
                        //console.log("error : " + effUIConfig.service + " - " + effUIConfig.targetUrl);
                    }
                });
            }, 0, uiConfig);
        }

        setTimeout(that.periodicRetryServices, PERIODIC_RETRY_SERVICE_MS);
    };

    function alreadyInRetry (uiConfig) {
        for (let i = 0; i < uiConfigsToRetry.length; i++) {
            let inUiConfig = uiConfigsToRetry[i];
            if (inUiConfig.title == uiConfig.title) {
                return true;
            }
        }
        return false;
    }

    this.handleServiceDisplay = function (service, uiConfig, node, immediate) {

        let reinitialize = shouldReinitialize(service, node);

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

            let waitTime = (immediate || !reinitialize) ? 0 : uiConfig.waitTime;

            uiConfig.targetUrl = buildUrl(uiConfig, node);
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

        let comingFromMenuHook = true;
        if (!uiConfig) { // if coming from eskimoMain.serviceMenuClear()
            uiConfig = UI_SERVICES_CONFIG[service];
            comingFromMenuHook = false;
        }

        if (uiConfig == null) {
            //console.error ("Couldn't find config for " + service);
            return;
        }

        if (!eskimoMain.hasRole(uiConfig.role)) {
            return false;
        }

        //console.log ("Hiding " + service);

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
        if (that.eskimoMain.isCurrentDisplayedScreen(service)) {
            that.eskimoSystemStatus.showStatus();
        }
    };

    function serviceMenuServiceFoundHook(nodeName, node, service, found, immediate) {

        let uiConfig = UI_SERVICES_CONFIG[service];

        if (uiConfig != null) {

            if (eskimoMain.hasRole(uiConfig.role)) {
                if (found) {

                    // handle iframe display
                    that.handleServiceDisplay(service, uiConfig, node, immediate);

                } else {

                    // handle iframe hiding
                    that.handleServiceHiding(service, uiConfig);
                }
            }
        }
    }
    this.serviceMenuServiceFoundHook = serviceMenuServiceFoundHook;

    function createServicesIFrames() {

        for (let i = 0; i < UI_SERVICES.length; i++) {

            let service = UI_SERVICES[i];

            serviceInitialized[service] = false;

            let iframeWrapperString = '' +
                '<div class="inner-content inner-content-frame-container" id="inner-content-' + service + '" style="visibility: hidden; display: none;">\n' +
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

};


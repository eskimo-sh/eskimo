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
eskimo.Operations = function() {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;

    const OPERATIONS_POLLING_DELAY = 5000;

    const that = this;

    let operationsPollingHandle = null;

    let messagesStore = {};
    let globalLastLine = 0;

    this.initialize = function () {
        // Initialize HTML Div from Template
        $("#inner-content-operations").load("html/eskimoOperations.html", (responseTxt, statusTxt, jqXHR) => {

            if(statusTxt === "success"){

                $("#operation-log-cancel").click(e => {
                    hideLogs();
                    e.preventDefault();
                    return false;
                });

                $("#operation-log-button-cancel").click(e => {
                    hideLogs();
                    e.preventDefault();
                    return false;
                });

                $("#interrupt-operations-btn").click(e => {
                    $("#operations-title").html("<h3>Operations pending .... <strong>(Interrupting ...)</strong></h3>");
                    $.ajaxGet({
                        url: "interrupt-processing",
                        success: function (data, status, jqXHR2) {
                        },
                        error: errorHandler
                    });

                    e.preventDefault();
                    return false;
                });

                $("#operation-log-modal").detach().appendTo("#content-page");
            }

            if(statusTxt === "error"){
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });
    };

    function showOperations() {

        // maybe Progress bar was shown previously and we don't show it on messages page
        that.eskimoMain.hideProgressbar();

        that.eskimoMain.showOnlyContent("operations");
    }
    this.showOperations = showOperations;

    function setOperationInProgress (pendingOp) {
        if (pendingOp) {
            $("#interrupt-operations-btn").attr("class", "btn btn-danger");
        } else {
            $("#interrupt-operations-btn").attr("class", "btn btn-danger disabled");
        }
    }
    this.setOperationInProgress = setOperationInProgress;

    function getLastLines() {
        let lastLines = {};
        for (let operation in messagesStore) {
            lastLines[operation] = messagesStore[operation].lastLine;
        }
        lastLines["global"] = globalLastLine;
        return lastLines;
    }
    this.getLastLines = getLastLines;

    function hideLogs () {
        $('#operation-log-modal').modal('hide');
    }
    this.hideLogs = hideLogs;

    function showLogs (operation) {
        if (!messagesStore[operation]) {
            $("#log-message-content").html("(no log received yet for operation)");
        } else {
            if (!messagesStore[operation].messages || messagesStore[operation].messages == "") {
                $("#log-message-content").html("(no log received yet for operation)");
            } else {
                $("#log-message-content").html(messagesStore[operation].messages);
            }
        }

        $('#operation-log-modal').modal('show');
    }
    this.showLogs = showLogs;

    function updateGlobalMessages (globalMessages) {
        globalLastLine = globalMessages.lastLine;
        if (globalMessages.lines && globalMessages.lines != "") {
            const $operationsGlobalMessages = $("#operations-global-messages");
            $operationsGlobalMessages.css("display", "block");
            let previous = $operationsGlobalMessages.html().trim();
            if (previous == null || previous == "") {
                $operationsGlobalMessages.html(atob(globalMessages.lines));
            } else {
                $operationsGlobalMessages.html(previous + "<br>" + atob(globalMessages.lines));
            }
        }
    }
    this.updateGlobalMessages = updateGlobalMessages;

    function updateMessages (labels, messages) {
        for (let i = 0; i < labels.length; i++) {

            let operation = labels[i].operation;

            if (!messagesStore[operation]) {
                messagesStore[operation] = {
                    "lastLine": 0,
                    "messages": ""
                };
            }

            messagesStore[operation].lastLine = messages[operation].lastLine;
            messagesStore[operation].messages += atob(messages[operation].lines);
        }
    }
    this.updateMessages = updateMessages;

    function renderStatus (labels, status) {
        for (let i = 0; i < labels.length; i++) {

            let operation = labels[i].operation;
            let opStatus = status[operation];
            
            let progress = $("#" + operation + "-progress");
            
            if (opStatus === "INIT") {
                progress.html("0%");
                progress.css("width", "0%");

            } else if (opStatus === "RUNNING") {
                progress.html("30% (Running)");
                progress.css("width", "30%");

            } else if (opStatus === "ERROR") {
                progress.html("Error!");
                progress.removeClass("bg-info");

            } else if (opStatus === "CANCELLED") {
                progress.html("Cancelled!");
                progress.addClass("bg-warning");

            } else if (opStatus === "COMPLETE") {
                progress.html("100% (Complete)");
                progress.addClass("bg-success");
            }

            if (opStatus === "ERROR" || opStatus === "CANCELLED" || opStatus === "COMPLETE") {
                progress.css("width", "100%");
                progress.removeClass("bg-info");
                progress.removeClass("progress-bar-striped");
                progress.removeClass("progress-bar-animated");
            }
        }
    }
    this.renderStatus = renderStatus;

    function renderLabels(labels) {

        // Don't rerender if no changes
        let newOpString = "";
        for (let i = 0; i < labels.length; i++) {
            newOpString += labels[i].operation;
        }

        let currentOpString = "";
        let $operationsTableBody = $("#operations-table-body");
        $operationsTableBody.children('tr').each(function () {
            currentOpString += $(this).attr("id");
        });

        if (newOpString !== currentOpString) {

            let $operationsGlobalMessages = $("#operations-global-messages");
            $operationsGlobalMessages.html("");
            $operationsGlobalMessages.css("display", "none");

            messagesStore = {};

            $operationsTableBody.html("");
            for (let i = 0; i < labels.length; i++) {

                let operation = labels[i].operation;
                let label = labels[i].label;

                let operationRow = ""
                    + "<tr id=\"" + operation + "\">\n" +
                    "      <td class=\"operation-title\">" + label + "</td>\n" +
                    "      <td class=\"operations-cell\">\n" +
                    "          <div id=\"" + operation + "-progress-wrapper\" class=\"progress active progress-operation-wrapper\">\n" +
                    "              <div id=\"" + operation + "-progress\" class=\"progress-bar progress-bar-striped progress-bar-animated bg-info\" role=\"progressbar\" aria-valuenow=\"0\" aria-valuemin=\"0\" aria-valuemax=\"100\" >\n" +
                    "              </div>\n" +
                    "          </div>\n" +
                    "      </td>\n" +
                    "      <td class=\"operations-cell\">\n" +
                    "          <a onclick=\"eskimoMain.getOperations().showLogs('" + operation + "');\">View</a>\n" +
                    "      </td>\n" +
                    "      </tr>";

                $operationsTableBody.append($(operationRow));
            }
        }
    }
    this.renderLabels = renderLabels;

    function fetchOperationStatus(callback) {
        $.ajaxGet({
            url: "fetch-operations-status?last-lines="  + JSON.stringify(getLastLines()),
            success: (data, status, jqXHR) => {

                // OK
                //console.log(data);

                if (data && data.result == "OK") {

                    renderLabels (data.labels);

                    renderStatus (data.labels, data.status);

                    updateMessages (data.labels, data.messages)

                    updateGlobalMessages (data.globalMessages);

                } else {
                    console.error("No data received / no operations in progress");
                    updateGlobalMessages ({lastLine: 1, lines: btoa("No data received / no operations in progress")});
                }

                if (callback != null && typeof callback === "function") {
                    callback();
                }

                if (that.eskimoMain.isOperationInProgress() && !callback) {
                    operationsPollingHandle = setTimeout(
                        fetchOperationStatus,
                        OPERATIONS_POLLING_DELAY);
                }
            },
            error: (jqXHR, status) => {

                if (callback != null && typeof callback === "function") {
                    callback();
                }

                errorHandler(jqXHR, status);

                if (that.eskimoMain.isOperationInProgress() && !callback) {
                    operationsPollingHandle = setTimeout(
                        fetchOperationStatus,
                        OPERATIONS_POLLING_DELAY);
                }
            }
        });
    }
    this.fetchOperationStatus = fetchOperationStatus;

    function startOperationInProgress() {

        $("#operations-table-body").html("");
        $("#operations-global-messages").html("");

        messagesStore = {};

        $("#operations-title").html("<h3>Operations pending ....</h3>");

        operationsPollingHandle = setTimeout(
            fetchOperationStatus,
            1000);
    }
    this.startOperationInProgress = startOperationInProgress;

    function stopOperationInProgress (success, callback) {

        // cancel periodic message fetching
        clearTimeout (operationsPollingHandle);

        // fetch messages one last time and close OperationInProgress in the end
        that.fetchOperationStatus (() => {

            if (success) {
                $("#operations-title").html("<h3>Operations completed successfully.</h3>");
            } else {
                $("#operations-title").html("<h3><span class='processing-error'>Operations completed in error !</span></h3>");
            }

            if (callback != null && typeof callback === "function") {
                callback();
            }
        });
    }
    this.stopOperationInProgress = stopOperationInProgress;

};
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
eskimo.Operations = function() {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;

    const OPERATIONS_POLLING_DELAY = 5000;

    const that = this;

    let operationsPollingHandle = null;

    this.initialize = function () {
        // Initialize HTML Div from Template
        $("#inner-content-operations").load("html/eskimoOperations.html", function(responseTxt, statusTxt, jqXHR){

            if(statusTxt == "success"){

                $("#clear-messaging-btn").click(function(e) {

                    /*
                    $.ajax({
                        type: "GET",
                        dataType: "json",
                        contentType: "application/json; charset=utf-8",
                        url: "clear-messaging",
                        success: function (data, status, jqXHR2) {
                            lastLineMessaging = 0;
                            $("#pending-message-content").html("");
                        },
                        error: errorHandler
                    });
                    */

                    e.preventDefault();
                    return false;
                });

                $("#interupt-messaging-btn").click(function(e) {
                    $("#pending-message-title").html("<h3>Processing pending on Eskimo backend. <strong>(Interrupting ...)</strong></h3>");
                    $.ajax({
                        type: "GET",
                        dataType: "json",
                        contentType: "application/json; charset=utf-8",
                        url: "interupt-processing",
                        success: function (data, status, jqXHR2) {
                        },
                        error: errorHandler
                    });

                    e.preventDefault();
                    return false;
                });
            }

            if(statusTxt == "error"){
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
            $("#interupt-messaging-btn").attr("class", "btn btn-danger");
        } else {
            $("#interupt-messaging-btn").attr("class", "btn btn-danger disabled");
        }
    }
    this.setOperationInProgress = setOperationInProgress;

    function fetchOperationStatus(callback) {
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "fetch-operations-status?last-lines="  + getLastLines(),
            success: function (data, status, jqXHR) {

                // OK
                //console.log(data);

                if (data && data.status) {
                    //console.log (atob(data.lines));

                    addMessage(atob(data.lines));

                    lastLineMessaging = data.lastLine;

                } else {
                    console.error("No data received");
                }

                if (callback != null && typeof callback === "function") {
                    callback();
                }

                if (that.eskimoMain.isOperationInProgress() && !callback) {
                    operationsPollingHandle = setTimeout(
                        fetchOperationStatus,
                        MESSAGING_POLLING_DELAY);
                }
            },
            error: function (jqXHR, status) {

                if (callback != null && typeof callback === "function") {
                    callback();
                }

                errorHandler(jqXHR, status);

                if (that.eskimoMain.isOperationInProgress() && !callback) {
                    operationsPollingHandle = setTimeout(
                        fetchOperationStatus,
                        MESSAGING_POLLING_DELAY);
                }
            }
        });
    }
    this.fetchOperationStatus = fetchOperationStatus;

    function startOperationInProgress() {
        let pendingBarWrapper = $("#progress-bar-pending-wrapper");
        pendingBarWrapper.css("visibility", "inherit");
        pendingBarWrapper.css("display", "block");

        $("#pending-message-title").html("<h3>Processing pending on Eskimo backend ....</h3>");

        $("#pending-message-content").html("");
        
        operationsPollingHandle = setTimeout(
            fetchOperationStatus,
            MESSAGING_POLLING_DELAY);
    }
    this.startOperationInProgress = startOperationInProgress;

    function addMessage (message) {
        $("#pending-message-content").append(message);
    }
    this.addMessage = addMessage;

    function stopOperationInProgress (success, callback) {

        // cancel periodic message fetching
        clearTimeout (operationsPollingHandle);

        /*
        // fetch messages one last time and close OperationInProgress in the end
        that.fetchOperationStatus (function() {

            if (!success) {
                $("#pending-message-title").html("<h3><span class='processing-error'>Processing completed in error !</span></h3>");
            } else {
                $("#pending-message-title").html("<h3>Processing completed successfully.</h3>");
            }

            let pendingBarWrapper = $("#progress-bar-pending-wrapper");
            pendingBarWrapper.css("visibility", "hidden");
            pendingBarWrapper.css("display", "none");

            if (callback != null && typeof callback === "function") {
                callback();
            }
        });
        */
    }
    this.stopOperationInProgress = stopOperationInProgress;

};
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
eskimo.Consoles = function(constructorObject) {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;

    const that = this;

    // Caution : this variable is populated by EskimoNodesStatus.
    var availableNodes = [];

    var openedConsoles = [];

    this.initialize = function() {

        // Initialize HTML Div from Template
        $("#inner-content-consoles").load("html/eskimoConsoles.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt == "success") {

                //

            } else if (statusTxt == "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }

        });
    };

    this.setOpenedConsoles = function(handles) {
        openedConsoles = handles;
    };
    this.getOpenedConsoles = function () {
        return openedConsoles;
    };
    this.setAvailableNodes = function(nodes) {

        var consoleToDisable=[];
        main: for (var i = 0; i < availableNodes.length; i++) {
            for (var j = 0; j < nodes.length; j++) {
                if (availableNodes[i].nodeName == nodes[j].nodeName) {
                    continue main;
                }
            }
            consoleToDisable.push(availableNodes[i]);
        }

        //console.log (consoleToDisable);
        //console.log (openedConsoles);

        for (var i = 0; i < consoleToDisable.length; i++) {

            var openedConsole = null;
            var closedConsoleNbr;
            for (closedConsoleNbr = 0; closedConsoleNbr < openedConsoles.length; closedConsoleNbr++) {
                if (openedConsoles[closedConsoleNbr].nodeName == consoleToDisable[i].nodeName) {
                    openedConsole = openedConsoles[closedConsoleNbr];
                    break;
                }
            }

            // disable console
            if (openedConsole != null) {
                console.log ("Disabling console " + openedConsole.nodeName);
                openedConsole.terminal.close();
            }
        }

        availableNodes = nodes;

        updateMenu();
    };

    this.getAvailableNodes = function () {
        return availableNodes;
    };

    function getNodeAddress(nodeName) {
        for (var i = 0; i < availableNodes.length; i++) {
            if (availableNodes[i].nodeName == nodeName) {
                // {"nbr": nbr, "nodeName": nodeName, "nodeAddress" : nodeAddress}
                return availableNodes[i].nodeAddress;
            }
        }
        return null;
    }

    var updateMenu = function () {

        // Find available nodes and add them to open console dropdown
        // {"nbr": nbr, "nodeName": nodeName, "nodeAddress" : nodeAddress}
        var actionOpenConsole = $("#consoles-action-open-console");
        actionOpenConsole.html("");
        for (var i = 0; i < availableNodes.length; i++) {
            var nodeObject = availableNodes[i];
            var newLi = '' +
                '<li><a id="console_open_' + nodeObject.nodeName + '" href="#">' +
                nodeObject.nodeAddress +
                '</a></li>';

            actionOpenConsole.append($(newLi));

            // register on click handler to actually open console
            $('#console_open_' + nodeObject.nodeName).click(function () {
                var nodeNameEff = this.id.substring("console_open_".length);
                openConsole(getNodeAddress(nodeNameEff), nodeNameEff);
            });
        }
    };

    function showConsoles() {

        if (!that.eskimoMain.isSetupDone()) {

            that.eskimoMain.showSetupNotDone ("Consoles are not available at this stage.");

        } else {

            // maybe progress bar was shown previously
            that.eskimoMain.hideProgressbar();

            that.eskimoMain.showOnlyContent("consoles");
            updateMenu();
        }
    }
    this.showConsoles = showConsoles;

    function selectConsole (nodeAddress, nodeName) {

        // select active console
        $("#consoles-tab-list").find("li").each(function() {
            if (this.id == "console_" + nodeName) {
                $(this).attr("class", "active");
            } else {
                $(this).attr("class", "");
            }
        });

        // Hide all consoles
        var ajaxTerminal = $(".ajaxterminal");
        ajaxTerminal.css("visibility", "hidden");
        ajaxTerminal.css("display", "none");

        // Select Console
        var consoleForNode = $("#consoles-console-" + nodeName);
        consoleForNode.css("visibility", "inherit");
        consoleForNode.css("display", "inherit");

        $("#term_" + nodeName).focus();
    }
    this.selectConsole = selectConsole;

    let showTarget = function (target, other) {
        if (target == null) {
            target = other;
        }

        var selectedName = target.id.substring(8);
        //console.log ("to be selected is " + selectedName);

        // select previous console
        for (var i = 0; i < availableNodes.length; i++) {
            if (availableNodes[i].nodeName == selectedName) {
                // {"nbr": nbr, "nodeName": nodeName, "nodeAddress" : nodeAddress}
                selectConsole(availableNodes[i].nodeAddress, availableNodes[i].nodeName);
            }
        }
    };

    function showPrevTab() {
        //console.log ("showPrevTab");
        // Find previous link
        var prev = null;
        var target = null;
        $("#consoles-tab-list").find("li").each(function() {
            if ($(this).attr("class") == "active") {
                if (prev != null) {
                    target = prev;
                }
            }
            prev = this;
        });

        showTarget(target, prev);
    }
    this.showPrevTab = showPrevTab;

    function showNextTab() {

        var takeNext = true;
        var target = null;
        var first = null;
        $("#consoles-tab-list").find("li").each(function() {
            if (first == null) {
                first = this;
            }
            if ($(this).attr("class") == "active") {
                takeNext = true
            } else {
                if (takeNext) {
                    target = this;
                }
                takeNext = false;
            }
        });

        showTarget(target, first);
    }
    this.showNextTab = showNextTab;

    function closeConsole (nodeName, terminalToClose) {

        //console.log (terminalToClose);

        // remove from open console
        var openedConsole = null;
        var closedConsoleNbr;
        for (closedConsoleNbr = 0; closedConsoleNbr < openedConsoles.length; closedConsoleNbr++) {
            if (openedConsoles[closedConsoleNbr].nodeName == terminalToClose) {
                openedConsole = openedConsoles[closedConsoleNbr];
                openedConsoles.splice(closedConsoleNbr, 1);
                break;
            }
        }

        // remove menu
        $("#console_" + nodeName).remove();

        // remove div
        $("#consoles-console-" + nodeName).remove();

        // close session on backend
        if (openedConsole == null) {
            alert("Console " + terminalToClose + " not found");
        } else {
            //console.log(openedConsole.terminal);
            $.ajax({
                type: "GET",
                dataType: "json",
                url: "terminal-remove?session=" + openedConsole.terminal.getSessionId(),
                success: function (data, status, jqXHR) {
                    //console.log(data);
                    //alert(data);
                },
                error: function(error) {
                    console.debug (error);
                }
            });

            openedConsole.terminal.close();
        }

        // show another console if available
        if (openedConsoles.length > 0) {
            if (closedConsoleNbr < openedConsoles.length) {
                selectConsole(openedConsoles[closedConsoleNbr].nodeAddress, openedConsoles[closedConsoleNbr].nodeName);
            } else {
                selectConsole(openedConsoles[closedConsoleNbr - 1].nodeAddress, openedConsoles[closedConsoleNbr - 1].nodeName);
            }
        }
    }
    this.closeConsole = closeConsole;

    function getTerminalWidth() {
        return Math.round(
            ($("#inner-content-consoles").outerWidth()
                - 130) // decoration
            / 8.1  // width of column
        );
    }

    function getTerminalHeight() {
        return Math.round(
            ($("#inner-content-consoles").outerHeight()
                - 60 // title and tabs
                - 220 // decoration
                //- 28 // toolbar
            )
            / 19  // Height of line
        );
    }

    function openConsole (nodeAddress, nodeName) {

        // add tab entry
        var consoleFound = false;
        var consoleTabList = $("#consoles-tab-list");
        consoleTabList.find("li").each(function() {
            if (this.id == "console_"+nodeName) {
                consoleFound = true;
            }
        });
        if (!consoleFound) {

            // Add tab entry
            consoleTabList.append($(''+
                '<li id="console_' + nodeName + '">'+
                '<a id="select_console_' + nodeName + '" href="#">' + nodeAddress +
                '</a></li>'));

            var consoleContent = '<div class="col-md-12 ajaxterminal" id="consoles-console-' + nodeName + '">\n' +
                '    <div id="term_' + nodeName + '" class="ajaxterm" tabindex="0"></div>\n' +
                '    <div id="console-actions-' + nodeName + '">\n' +
                '        <button id="console-close-' + nodeName + '" name="console-close-1" class="btn btn-primary">\n' +
                '            Close\n' +
                '        </button>\n' +
                '    </div>';

            $("#consoles-console-content").append ($(consoleContent));

            $("#console-close-" + nodeName).click(function () {
                var terminalToClose = this.id.substring("console-close-".length);
                closeConsole (nodeName, terminalToClose);
            });

            var t = new ajaxterm.Terminal("term_"+nodeName, {
                width: getTerminalWidth(),
                height: getTerminalHeight(),
                endpoint: "./terminal?node="+nodeAddress
            });
            t.setShowNextTab(showNextTab);
            t.setShowPrevTab(showPrevTab);

            openedConsoles.push({"nodeName" : nodeName, "nodeAddress": nodeAddress, "terminal" : t});

            // register on click handler to actually open console
            $('#select_console_' + nodeName).click(function() {
                var nodeNameEff = this.id.substring("select_console_".length);
                selectConsole(getNodeAddress(nodeNameEff), nodeNameEff);
            });

        }
        selectConsole(nodeAddress, nodeName);
    }
    this.openConsole = openConsole;

    // inject constructor object in the end
    if (constructorObject != null) {
        $.extend(this, constructorObject);
    }

    // call constructor
    this.initialize();
};
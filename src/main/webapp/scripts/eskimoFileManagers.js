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
eskimo.FileManagers = function(constructorObject) {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;

    var that = this;

    this.fileEditHook = null;

    // Caution : this variable is populated by EskimoNodesStatus.
    var availableNodes = [];

    var openedFileManagers = [];

    this.initialize = function () {
        // Initialize HTML Div from Template
        $("#inner-content-file-managers").load("html/eskimoFileManagers.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt == "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);

            } else {

                $("#file-manager-viewer-header-close").click(closeFileViewer);
                $("#file-manager-viewer-button-close").click(closeFileViewer);

                $("#file-manager-input-header-close").click(closeFilenameInput);
                $("#file-manager-input-button-cancel").click(closeFilenameInput);
                $("#file-manager-input-button-validate").click(validateCreateFile);
            }

        });
    };

    this.setFileEditHook = function (fileEditHook) {
        this.fileEditHook = fileEditHook;
    };

    this.setOpenedFileManagers = function(handles) {
        openedFileManagers = handles;
    };
    this.getOpenedFileManagers = function () {
        return openedFileManagers;
    };

    this.setAvailableNodes = function(nodes) {

        // FIXME If some nodes are removed from previous list, check if a console was opened that should be disabled

        var fmToDisable=[];
        main: for (var i = 0; i < availableNodes.length; i++) {
            for (j = 0; j < nodes.length; j++) {
                if (availableNodes[i].nodeName == nodes[j].nodeName) {
                    continue main;
                }
            }
            fmToDisable.push(availableNodes[i]);
        }

        //console.log (fmToDisable);
        //console.log (openedFileManagers);

        for (var i = 0; i < fmToDisable.length; i++) {

            var openedFm = null;
            var closedFmNbr;
            for (closedFmNbr = 0; closedFmNbr < openedFileManagers.length; closedFmNbr++) {
                if (openedFileManagers[closedFmNbr].nodeName == fmToDisable[i].nodeName) {
                    openedFm = openedFileManagers[closedFmNbr];
                    break;
                }
            }

            // disable console
            if (openedFm != null) {
                console.log ("Disabling file manager " + openedFm.nodeName);

                // Disable file manager
                $('#file-manager-folder-menu-' + openedFm.nodeName).html('' +
                    '            <div class="btn-group">'+
                    '                <button id="file-manager-close-' + openedFm.nodeName + '" name="file-manager-close-' + openedFm.nodeName + '" class="btn btn-primary">Close</button>\n' +
                    '            </div>');

                $('#file-manager-folder-content-' + openedFm.nodeName).html('(connection to backend lost)');

                $("#file-manager-close-" + openedFm.nodeName).click(function () {
                    var effNodeName = this.id.substring("file-manager-close-".length);
                    closeFileManager(effNodeName);
                });
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

        // Find available nodes and add them to open sftp dropdown
        // {"nbr": nbr, "nodeName": nodeName, "nodeAddress" : nodeAddress}
        var actionOpen = $("#file-managers-action-open-file-manager");
        actionOpen.html("");
        for (var i = 0; i < availableNodes.length; i++) {
            var nodeObject = availableNodes[i];
            var newLi = '<li><a id="file_manager_open_' + nodeObject.nodeName + '" href="#">'
                + nodeObject.nodeAddress + '</a></li>';

            actionOpen.append($(newLi));

            // register on click handler to actually open console
            $('#file_manager_open_' + nodeObject.nodeName).click(function () {
                var nodeName = this.id.substring("file_manager_open_".length);
                openFileManager(getNodeAddress(nodeName), nodeName);
            });
        }
    };

    function showFileManagers() {

        if (!that.eskimoMain.isSetupDone()) {

            that.eskimoMain.showSetupNotDone ("Consoles are not available at this stage.");

        } else {

            // maybe progress bar was shown previously
            that.eskimoMain.hideProgressbar();

            that.eskimoMain.showOnlyContent("file-managers");
            updateMenu();
        }
    }
    this.showFileManagers = showFileManagers;

    function selectFileManager (nodeAddress, nodeName) {

        // select active file-manager
        $("#file-managers-tab-list").find("li").each(function() {
            if (this.id == "file-manager_"+nodeName) {
                $(this).attr("class", "active");
            } else {
                $(this).attr("class", "");
            }
        });

        // Hide all file managers
        var fileManagerView = $(".file-manager-view");
        fileManagerView.css("visibility", "hidden");
        fileManagerView.css("display", "none");

        // Show selected one
        var fileManagerNode = $("#file-managers-file-manager-" + nodeName);
        fileManagerNode.css("visibility", "inherit");
        fileManagerNode.css("display", "inherit");
    }
    this.selectFileManager = selectFileManager;

    function findFileManager(nodeName) {

        //console.log(openedFileManagers);
        var openedFileManager = null;
        for (var i = 0; i < openedFileManagers.length; i++) {
            if (openedFileManagers[i].nodeName == nodeName) {
                openedFileManager = openedFileManagers[i];
                break;
            }
        }
        if (openedFileManager == null) {
            throw "Opened File manager handle not found for " + nodeName;
        }
        return openedFileManager;
    }
    this.findFileManager = findFileManager;

    function updateCurrentFolder (nodeName, folderName) {
        // update current folder in openedFileManager
        var openedFileManager = findFileManager(nodeName);

        if (openedFileManager.current != folderName) {
            openedFileManager.previous = openedFileManager.current;
        }
        openedFileManager.current = folderName;
    }
    this.updateCurrentFolder = updateCurrentFolder;

    function listFolder (nodeAddress, nodeName, folderName, content) {

        updateCurrentFolder (nodeName, folderName);

        // Create current path links
        var foldersLinkWrapper = '<a href="javascript:eskimoMain.getFileManagers().openFolder(\''+ nodeAddress + '\', \'' + nodeName + '\', \'/\', \'.\');"> / </a>';
        var folders = folderName.split("/");
        var prevFolder = "/";
        for (var i = 0; i < folders.length; i++) {
            var folder = folders[i];
            if (folder != "") {

                var folderLink = '<a href="javascript:eskimoMain.getFileManagers().openFolder(\''
                    + nodeAddress
                    + '\', \''
                    + nodeName
                    + '\', \''
                    + prevFolder
                    + '\', \''
                    + folder
                    + '\');">' + folder + '</a>';
                foldersLinkWrapper = foldersLinkWrapper + folderLink + " / ";

                prevFolder = prevFolder + (prevFolder != "/" ? "/" : "") + folder;
            }
        }
        $("#file-manager-folder-current-" + nodeName).html("Path : " + foldersLinkWrapper);

        // Create folder content list
        var folderContentHeader = ''+
            '        <div class="table-responsive">\n' +
            '            <table class="table table-hover">\n' +
            '                <tbody>\n';

        var sortedFilesArray = [];
        for (var subFolder in content) {
            sortedFilesArray.push(subFolder);
        }
        sortedFilesArray.sort();
        console.log (sortedFilesArray);

        var folderContentFiles = "";

        for (var i = 0; i <  sortedFilesArray.length; i++) {
            var subFolderProps = content[sortedFilesArray[i]];

            var isFolder = subFolderProps.permissions.substring(0, 1) == "d";
            var isLink = subFolderProps.permissions.substring(0, 1) == "l";

            var folderContentRow = ''+
                '                <tr>\n' +
                '                    <td>' +
                '                        <a href="javascript:eskimoMain.getFileManagers().' +
                                         (isFolder ? 'openFolder' : 'openFile') +
                                         '(\'' + nodeAddress + '\', \'' + nodeName + '\', \'' + folderName + '\', \'' + sortedFilesArray[i] + '\');">' +
                                             sortedFilesArray[i] +
                '                        </a>' +
                '                    </td>\n' +
                '                    <td>' + subFolderProps.permissions + '</td>\n' +
                '                    <td>' + subFolderProps.count + '</td>\n' +
                '                    <td>' + subFolderProps.user + '</td>\n' +
                '                    <td>' + subFolderProps.group + '</td>\n' +
                '                    <td>' + subFolderProps.size + '</td>\n' +
                '                    <td>' + subFolderProps.timestamp + '</td>\n' +
                '                    <td>' +
                '                       <button type="button" onclick="javascript:eskimoMain.getFileManagers().deletePath(\'' +
                                        nodeAddress + '\', \'' + nodeName + '\', \'' + folderName + '\', \'' + sortedFilesArray[i] + '\');" ' +
                '                               class="btn btn-xs btn-default" title="Delete"><i class="fa fa-close"></i></button>\n' +
                                         (!isFolder && !isLink ?
                '                       <button type="button" onclick="javascript:eskimoMain.getFileManagers().downloadFile(\'' +
                                        nodeAddress + '\', \'' + nodeName + '\', \'' + folderName + '\', \'' + sortedFilesArray[i] + '\');" ' +
                '                               class="btn btn-xs btn-default" title="Download"><i class="fa fa-arrow-down"></i></button>\n'
                                             : '')+
                '                    </td>\n' +
                '                </tr>\n';

            folderContentFiles = folderContentFiles + folderContentRow;
        }

        var folderContentFooter = ''+
            '                </tbody>\n' +
            '            </table>\n' +
            '        </div>';

        $("#file-manager-folder-content-"+nodeName).html(folderContentHeader + folderContentFiles + folderContentFooter);
    }
    this.listFolder = listFolder;

    function closeFileViewer() {
        $('#file-viewer-modal').modal("hide");
    }
    this.closeFileViewer = closeFileViewer;

    this.openFile = function (nodeAddress, nodeName, currentFolder, file) {

        $.ajax({
            type: "GET",
            dataType: "json",
            context: this,
            contentType: "application/json; charset=utf-8",
            url: "file-manager-open-file?address=" + nodeAddress + "&folder=" + currentFolder + "&file=" + file,
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    if (data.folder != null && data.folder != "" ) {

                        // file was actually a folder
                        this.listFolder (nodeAddress, nodeName, data.folder, data.content);

                    } else if (!data.accessible) {
                        alert ("User used by eskimo has no read permission to this file");

                    } else {

                        if (data.fileViewable) {

                            if (this.fileEditHook != null) {
                                this.fileEditHook (nodeAddress, nodeName, data.fileName);

                            } else {

                                $("#file-viewer-title").html("Viewing File : " + data.fileName);

                                $('#file-viewer-modal').modal("show");

                                $("#file-viewer-body").html("<pre>" + atob(data.fileContent) + "</pre>");
                            }

                        } else {

                            this.downloadFile(nodeAddress, nodeName, currentFolder, file);
                        }
                    }
                } else {
                    alert(data.error);

                    // FIXME Close file-manager or make disabled
                }
            },
            error: errorHandler
        });
    };

    this.downloadFile = function (nodeAddress, nodeName, currentFolder, file) {
        console.log ("Downloading: target:"+ encodeURIComponent(file) + " - address=" + nodeAddress + " - folder=" + currentFolder + " - file=" + file);
        window.open("file-manager-download/" + encodeURIComponent(file) + "?address=" + nodeAddress + "&folder=" + currentFolder + "&file=" + file);
    };

    this.deletePath = function (nodeAddress, nodeName, currentFolder, file) {
        if (confirm("Are you sure you want to delete file " + file + "?")) {
            $.ajax({
                type: "GET",
                dataType: "json",
                context: this,
                contentType: "application/json; charset=utf-8",
                url: "file-manager-delete?address=" + nodeAddress + "&folder=" + currentFolder + "&file=" + file,
                success: function (data, status, jqXHR) {

                    if (data.status == "OK") {

                        that.openFolder(nodeAddress, nodeName, currentFolder, ".");

                    } else {
                        alert(data.error);

                        // FIXME Close File Manager or make disabled
                    }
                },
                error: errorHandler
            });
        }
    };

    function showRoot (nodeAddress, nodeName) {
        that.openFolder (nodeAddress, nodeName, "/", ".");
    }
    this.showRoot = showRoot;

    function showParent (nodeAddress, nodeName) {

        var openedFileManager = findFileManager(nodeName);

        // find location of last /
        var indexOfLastSlash = 0;
        for (var i = openedFileManager.current.length - 1; i >= 0; i--) {
            if (openedFileManager.current.charAt(i) == "/") {
                indexOfLastSlash = i;
                break;
            }
        }

        var parentFolder = "/";
        if (indexOfLastSlash > 0) {
            parentFolder = openedFileManager.current.substring(0, indexOfLastSlash);
        }

        that.openFolder (nodeAddress, nodeName, parentFolder, ".");
    }
    this.showParent = showParent;

    function showPrevious (nodeAddress, nodeName) {

        var openedFileManager = findFileManager(nodeName);

        if (openedFileManager.previous != null && openedFileManager.previous != "") {

            that.openFolder(nodeAddress, nodeName, openedFileManager.previous, ".");
        }
    }
    this.showPrevious = showPrevious;

    function refreshFolder (nodeAddress, nodeName) {

        var openedFileManager = findFileManager(nodeName);
        that.openFolder(nodeAddress, nodeName, openedFileManager.current, ".");
    }
    this.refreshFolder = refreshFolder;

    function createFile (nodeAddress, nodeName) {
        var openedFileManager = findFileManager(nodeName);
        var currentFolder = openedFileManager.current;

        $('#filename-input-nodeName').val(nodeName);
        $('#filename-input-nodeAddress').val(nodeAddress);
        $('#filename-input-currentfolder').val(currentFolder);


        $('#filename-input-modal').modal("show");
    }
    this.createFile = createFile;

    function validateCreateFile() {

        var nodeName = $('#filename-input-nodeName').val();
        var nodeAddress = $('#filename-input-nodeAddress').val();
        var currentFolder = $('#filename-input-currentfolder').val();
        var newFileName = $("#filename-input-input").val();

        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "file-manager-create-file?address=" + nodeAddress + "&folder=" + currentFolder + "&fileName=" + newFileName,
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    listFolder (nodeAddress, nodeName, data.folder, data.content);

                } else {
                    alert(data.error);

                    // FIXME Close File Manager or make disabled
                }
            },
            error: errorHandler
        });

        $('#filename-input-modal').modal("hide");
    }
    this.validateCreateFile = validateCreateFile;

    function closeFilenameInput() {
        $('#filename-input-modal').modal("hide");
    }
    this.closeFilenameInput = closeFilenameInput;

    this.openFolder = function (nodeAddress, nodeName, currentFolder, subFolder) {
        console.log ("Opening folder for " + nodeName + " - " + currentFolder + "/" + subFolder);
        $.ajax({
            type: "GET",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            url: "file-manager-navigate?address=" + nodeAddress + "&folder=" + currentFolder + "&subFolder=" + subFolder ,
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    listFolder (nodeAddress, nodeName, data.folder, data.content);

                } else {
                    alert(data.error);

                    // FIXME Close File Manager or make disabled
                }
            },
            error: errorHandler
        });
    };

    function uploadFile (nodeAddress, nodeName) {

        var openedFileManager = findFileManager(nodeName);

        $("#file-manager-hidden-folder-"+nodeName).val(openedFileManager.current);

        $("#file-manager-hidden-file-"+nodeName).trigger('click');
    }
    this.uploadFile = uploadFile;

    function registerSubmitFormFileUpload (e, nodeName, nodeAddress) {
        $("#file-manager-upload-form-"+nodeName).on('submit',(function(event) {

            // reset modal
            $('#file-upload-progress-bar').attr('aria-valuenow', "1%").css('width', "1%");
            $('#file-upload-progress-bar').html("1%");
            $('#file-upload-progress-modal').modal("show");

            var completeCallback = function (data) {

                $('#file-upload-progress-modal').modal("hide");

                if(data) {

                    if (data.status == "KO") {
                        alert (data.error);
                    } else {
                        refreshFolder (nodeAddress, nodeName);
                    }

                } else {
                    alert ("No result obtained from backend. This is an unexpected error.")
                }
            };

            event.preventDefault();
            $.ajax({
                url: "file-manager-upload?address=" + nodeAddress+ "&nodeName=" + nodeName,
                xhr: function () {
                    var xhr = new window.XMLHttpRequest();
                    xhr.upload.addEventListener("progress", function (evt) {
                        if (evt.lengthComputable) {
                            var percentComplete = evt.loaded / evt.total;
                            console.log(percentComplete);
                            var newProgress = Math.ceil(percentComplete * 100);
                            $('#file-upload-progress-bar').attr('aria-valuenow', newProgress+"%").css('width', newProgress+"%");
                            $('#file-upload-progress-bar').html(newProgress+"%");
                        }
                    }, false);
                    return xhr;
                },
                type: "POST",
                dataType: "json",
                data:  new FormData(this),
                contentType: false,//"application/json; charset=utf-8",
                cache: false,
                processData:false,
                success: completeCallback,
                error: function (jqXHR, status) {
                    errorHandler(jqXHR, status);
                    $('#file-upload-progress-modal').modal("hide");
                }
            });
        }));
    }

    this.connectFileManager = function (nodeAddress, nodeName) {
        $.ajax({
            type: "GET",
            dataType: "json",
            context: this,
            contentType: "application/json; charset=utf-8",
            url: "file-manager-connect?address=" +nodeAddress ,
            success: function (data, status, jqXHR) {

                if (data.status == "OK") {

                    openedFileManagers.push({"nodeName" : nodeName, "nodeAddress": nodeAddress, "current": "/"});

                    this.listFolder (nodeAddress, nodeName, data.folder, data.content);

                } else {
                    alert(data.error);

                    // FIXME Close File Manager
                }
            },
            error: errorHandler
        });
    };

    var closeFileManager = function (nodeName) {
        console.log(nodeName);
        // {"nbr": nbr, "nodeName": nodeName, "nodeAddress" : nodeAddress}

        // remove from open File Manager
        var openedFileManager = null;
        var closedFileManagerNbr = -1;
        for (closedFileManagerNbr = 0; closedFileManagerNbr < openedFileManagers.length; closedFileManagerNbr++) {
            if (openedFileManagers[closedFileManagerNbr].nodeName == nodeName) {
                openedFileManager = openedFileManagers[closedFileManagerNbr];
                openedFileManagers.splice(closedFileManagerNbr, 1);
                break;
            }
        }

        // remove menu
        $("#file-manager_" + nodeName).remove();

        // remove div
        $("#file-managers-file-manager-" + nodeName).remove();

        // close session on backend
        if (openedFileManager == null) {
            alert("File Manager " + terminalToClose + " not found");
        } else {
            $.ajax({
                type: "GET",
                dataType: "json",
                url: "file-manager-remove?address=" + openedFileManager.nodeAddress,
                success: function (data, status, jqXHR) {
                    console.log(data);
                    //alert(data);
                },
                error: errorHandler
            });
        }

        // show another File Manager if available
        if (openedFileManagers.length > 0) {
            if (closedFileManagerNbr < openedFileManagers.length) {
                selectFileManager(openedFileManagers[closedFileManagerNbr].nodeAddress, openedFileManagers[closedFileManagerNbr].nodeName);
            } else {
                selectFileManager(openedFileManagers[closedFileManagerNbr - 1].nodeAddress, openedFileManagers[closedFileManagerNbr - 1].nodeName);
            }
        }
    };

    function openFileManager (nodeAddress, nodeName) {

        // add tab entry
        var fileManagerFound = false;
        var fileManagersTabList = $("#file-managers-tab-list");
        fileManagersTabList.find("li").each(function() {
            if (this.id == "file-manager_"+nodeName) {
                fileManagerFound = true;
            }
        });
        if (!fileManagerFound) {

            // Add tab entry
            fileManagersTabList.append($('<li id="file-manager_' + nodeName + '">'+
                '<a id="select_file_manager_' + nodeName  + '" href="#">' + nodeAddress + '</a></li>'));

            var fileManagerContent = '<div class="col-md-12 file-manager-view" id="file-managers-file-manager-' + nodeName + '">\n' +
                '    <div id="file-manager-actions-' + nodeName + '">\n' +
                '        <nav id="file-manager-folder-menu-' + nodeName + '" class="btn-toolbar file-manager-folder-menu">\n' +
                '            <div class="btn-group">'+
                '                <button id="file-manager-close-' + nodeName + '" name="file-manager-close-' + nodeName + '" class="btn btn-primary">Close</button>\n' +
                '            </div>' +
                '            <div class="btn-group">' +
                '                <button type="button" id="show_root_' + nodeName + '" class="btn btn-default"><i class="fa fa-home"></i> Root</button>\n' +
                '            </div>' +
                '            <div class="btn-group">' +
                '                <button type="button" id="show_parent_' + nodeName + '" class="btn btn-default"><i class="fa fa-arrow-up"></i> Parent</button>\n' +
                '            </div>' +
                '            <div class="btn-group">' +
                '                <button type="button" id="show_previous_' + nodeName + '" class="btn btn-default"><i class="fa fa-arrow-left"></i> Previous</button>\n' +
                '            </div>' +
                '            <div class="btn-group">' +
                '                <button type="button" id="refresh_folder_' + nodeName + '" class="btn btn-default"><i class="fa fa-refresh"></i> Refresh</button>\n' +
                '            </div>' +
                '            <div class="btn-group"><form target="_blank" id="file-manager-upload-form-' + nodeName + '" method="POST" enctype="multipart/form-data">' +
                '                <div style="visibility: hidden; display: none;"><input type="file" id="file-manager-hidden-file-' + nodeName + '" name="file"></div>' +
                '                <button type="button" id="upload_file_' + nodeName + '" class="btn btn-default"><i class="fa fa-arrow-circle-up"></i> Upload file</button>\n' +
                '                <input type="hidden" id="file-manager-hidden-folder-' + nodeName + '" name="folder">' +
                '                <input type="hidden" id="file-manager-hidden-filename-' + nodeName + '" name="filename">' +
                '            </form></div>' +
                '            <div class="btn-group">' +
                '                <button type="button" id="create_file_' + nodeName + '" class="btn btn-default"><i class="fa fa-file"></i> Create file</button>\n' +
                '            </div>' +
                '           <div class="btn-group">' +
                '                <label id="file-manager-folder-current-' + nodeName + '" class="btn"></label>' +
                '            </div>' +
                '        </nav>'  +
                '        <div id="file-manager-folder-content-' + nodeName + '"></div>\n' +
                '    </div>';

            $("#file-managers-file-manager-content").append ($(fileManagerContent));

            $(document).on('change', '#file-manager-hidden-file-'+ nodeName, function(e) {

                var fileName = e.target.files[0].name;
                $("#file-manager-hidden-filename-"+nodeName).val(fileName);

                  //console.log ($('#file-manager-hidden-file-'+ nodeName).val());
                $("#file-manager-upload-form-"+nodeName).submit();
            });

            $(document).ready(function (e) {
                registerSubmitFormFileUpload (e, nodeName, nodeAddress);
            });

            // $('input[type=file]').simpleUpload(url, options);

            $("#file-manager-close-" + nodeName).click(function () {
                var effNodeName = this.id.substring("file-manager-close-".length);
                closeFileManager(effNodeName);
            });

            // register on click handlers
            $('#select_file_manager_' + nodeName).click(function() {
                var effNodeName = this.id.substring("select_file_manager_".length);
                selectFileManager(getNodeAddress(effNodeName), effNodeName);
            });

            $('#show_root_' + nodeName).click(function() {
                var effNodeName = this.id.substring("show_root_".length);
                showRoot(getNodeAddress(effNodeName), effNodeName);
            });

            $('#show_parent_' + nodeName).click(function() {
                var effNodeName = this.id.substring("show_parent_".length);
                showParent(getNodeAddress(effNodeName), effNodeName);
            });

            $('#show_previous_' + nodeName).click(function() {
                var effNodeName = this.id.substring("show_previous_".length);
                showPrevious(getNodeAddress(effNodeName), effNodeName);
            });

            $('#refresh_folder_' + nodeName).click(function() {
                var effNodeName = this.id.substring("refresh_folder_".length);
                refreshFolder(getNodeAddress(effNodeName), effNodeName);
            });

            $('#upload_file_' + nodeName).click(function() {
                var effNodeName = this.id.substring("upload_file_".length);
                uploadFile(getNodeAddress(effNodeName), effNodeName);
            });

            $('#create_file_' + nodeName).click(function() {
                var effNodeName = this.id.substring("create_file_".length);
                createFile(getNodeAddress(effNodeName), effNodeName);
            });

            that.connectFileManager (nodeAddress, nodeName);

        }
        selectFileManager(nodeAddress, nodeName);
    }
    this.openFileManager = openFileManager;

    // inject constructor object in the end
    if (constructorObject != null) {
        $.extend(this, constructorObject);
    }

    // call constructor
    this.initialize();

};
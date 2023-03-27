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
eskimo.FileManagers = function() {

    // will be injected eventually from constructorObject
    this.eskimoMain = null;

    const that = this;

    this.fileEditHook = null;

    // Caution : this variable is populated by EskimoNodesStatus.
    let availableNodes = [];

    let openedFileManagers = [];

    this.initialize = function () {
        // Initialize HTML Div from Template
        $("#inner-content-file-managers").load("html/eskimoFileManagers.html", (responseTxt, statusTxt, jqXHR) => {

            if (statusTxt === "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);

            } else {

                $("#file-manager-viewer-header-close").click(closeFileViewer);
                $("#file-manager-viewer-button-close").click(closeFileViewer);

                $("#file-manager-input-header-close").click(closeFilenameInput);
                $("#file-manager-input-button-cancel").click(closeFilenameInput);
                $("#file-manager-input-button-validate").click(validateCreateFile);

                // reattach modals to content-page
                $("#file-viewer-modal").detach().appendTo("#content-page");
                $("#filename-input-modal").detach().appendTo("#content-page");
                $("#file-upload-progress-modal").detach().appendTo("#content-page");
            }

        });
    };

    this.setFileEditHook = function (fileEditHook) {
        this.fileEditHook = fileEditHook;
    };

    this.getOpenedFileManagers = function () {
        return openedFileManagers;
    };

    this.setAvailableNodes = function(nodes) {

        // FIXME If some nodes are removed from previous list, check if a console was opened that should be disabled

        let fmToDisable=[];
        main: for (let i = 0; i < availableNodes.length; i++) {
            for (let j = 0; j < nodes.length; j++) {
                if (availableNodes[i].nodeName == nodes[j].nodeName) {
                    continue main;
                }
            }
            fmToDisable.push(availableNodes[i]);
        }

        //console.log (fmToDisable);
        //console.log (openedFileManagers);

        for (let i = 0; i < fmToDisable.length; i++) {

            let openedFm = null;
            let closedFmNbr;
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
                    let effNodeName = this.id.substring("file-manager-close-".length);
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
        for (let i = 0; i < availableNodes.length; i++) {
            if (availableNodes[i].nodeName == nodeName) {
                // {"nbr": nbr, "nodeName": nodeName, "nodeAddress" : nodeAddress}
                return availableNodes[i].nodeAddress;
            }
        }
        return null;
    }

    let updateMenu = function () {

        // Find available nodes and add them to open sftp dropdown
        // {"nbr": nbr, "nodeName": nodeName, "nodeAddress" : nodeAddress}
        let actionOpen = $("#file-managers-action-open-file-manager");
        actionOpen.html("");
        for (let i = 0; i < availableNodes.length; i++) {
            let nodeObject = availableNodes[i];
            let newLi = '<li><a id="file_manager_open_' + nodeObject.nodeName + '" href="#">'
                    + nodeObject.nodeAddress + '</a></li>';

            actionOpen.append($(newLi));

            // register on click handler to actually open console
            $('#file_manager_open_' + nodeObject.nodeName).click(function () {
                let nodeName = this.id.substring("file_manager_open_".length);
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

    function selectFileManager (node, nodeName) {

        // select active file-manager
        $("#file-managers-tab-list").find("li").each(function() {
            if (this.id == "file-manager_"+nodeName) {
                $(this).attr("class", "nav-item active");
            } else {
                $(this).attr("class", "nav-item ");
            }
        });

        // Hide all file managers
        //$.hideElement($(".file-manager-view"));
        let fileManagerView = $(".file-manager-view");
        fileManagerView.css("visibility", "hidden");
        fileManagerView.css("display", "none");

        // Show selected one
        //$.showElement($("#file-managers-file-manager-" + nodeName));
        let fileManagerNode = $("#file-managers-file-manager-" + nodeName);
        fileManagerNode.css("visibility", "inherit");
        fileManagerNode.css("display", "inherit");
    }
    this.selectFileManager = selectFileManager;

    function findFileManager(nodeName) {

        //console.log(openedFileManagers);
        let openedFileManager = null;
        for (let i = 0; i < openedFileManagers.length; i++) {
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
        let openedFileManager = findFileManager(nodeName);

        if (openedFileManager.current != folderName) {
            openedFileManager.previous = openedFileManager.current;
        }
        openedFileManager.current = folderName;
    }
    this.updateCurrentFolder = updateCurrentFolder;

    function listFolder (node, nodeName, folderName, content) {

        updateCurrentFolder (nodeName, folderName);

        // Create current path links
        let foldersLinkWrapper = '<a href="javascript:eskimoMain.getFileManagers().openFolder(\''+ node + '\', \'' + nodeName + '\', \'/\', \'.\');"> / </a>';
        let folders = folderName.split("/");
        let prevFolder = "/";
        for (let i = 0; i < folders.length; i++) {
            let folder = folders[i];
            if (folder != "") {

                let folderLink = '<a href="javascript:eskimoMain.getFileManagers().openFolder(\''
                    + node
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
        let folderContentHeader = ''+
            '        <div class="table-responsive">\n' +
            '            <table class="file-manager-files-table table table-hover">\n' +
            '                <tbody>\n';

        let sortedFilesArray = [];
        for (let subFolder in content) {
            sortedFilesArray.push(subFolder);
        }
        sortedFilesArray.sort();
        console.log (sortedFilesArray);

        let folderContentFiles = "";

        for (let i = 0; i <  sortedFilesArray.length; i++) {
            let subFolderProps = content[sortedFilesArray[i]];

            let isFolder = subFolderProps.permissions.substring(0, 1) === "d";
            let isLink = subFolderProps.permissions.substring(0, 1) === "l";

            let folderContentRow = ''+
                '                <tr>\n' +
                '                    <td>' +
                '                        <a href="javascript:eskimoMain.getFileManagers().' +
                                         (isFolder ? 'openFolder' : 'openFile') +
                                         '(\'' + node + '\', \'' + nodeName + '\', \'' + folderName + '\', \'' + sortedFilesArray[i] + '\');">' +
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
                '                       <button type="button" onclick="eskimoMain.getFileManagers().deletePath(\'' +
                                        node + '\', \'' + nodeName + '\', \'' + folderName + '\', \'' + sortedFilesArray[i] + '\');" ' +
                '                               class="btn btn-xs btn-default" title="Delete"><i class="fa fa-close"></i></button>\n' +
                                         (!isFolder && !isLink ?
                '                       <button type="button" onclick="eskimoMain.getFileManagers().downloadFile(\'' +
                                        node + '\', \'' + nodeName + '\', \'' + folderName + '\', \'' + sortedFilesArray[i] + '\');" ' +
                '                               class="btn btn-xs btn-default" title="Download"><i class="fa fa-arrow-down"></i></button>\n'
                                             : '')+
                '                    </td>\n' +
                '                </tr>\n';

            folderContentFiles = folderContentFiles + folderContentRow;
        }

        let folderContentFooter = ''+
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

    this.openFile = function (node, nodeName, currentFolder, file) {

        $.ajaxGet({
            url: "file-manager-open-file?nodeAddress=" + node + "&folder=" + currentFolder + "&file=" + file,
            success: (data, status, jqXHR) => {

                if (data.status == "OK") {

                    if (data.folder != null && data.folder != "" ) {

                        // file was actually a folder
                        that.listFolder (node, nodeName, data.folder, data.content);

                    } else if (!data.accessible) {
                        that.eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR,  "User used by eskimo has no read permission to this file");

                    } else {

                        if (data.fileViewable) {

                            if (that.fileEditHook != null) {
                                that.fileEditHook (node, nodeName, data.fileName);

                            } else {

                                $("#file-viewer-title").html("Viewing File : " + data.fileName);

                                $('#file-viewer-modal').modal("show");

                                /*
                                $('#file-viewer-modal-dialog').keypress(function(ev) {
                                   console.log (ev);
                                });
                                */

                                $("#file-viewer-content").html(atob(data.fileContent));
                            }

                        } else {

                            that.downloadFile(node, nodeName, currentFolder, file);
                        }
                    }
                } else {
                    that.eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error);

                    // FIXME Close file-manager or make disabled
                }
            },
            error: errorHandler
        });
    };

    this.downloadFile = function (node, nodeName, currentFolder, file) {
        console.log ("Downloading: target:"+ encodeURIComponent(file) + " - address=" + node + " - folder=" + currentFolder + " - file=" + file);
        window.open("file-manager-download/" + encodeURIComponent(file) + "?nodeAddress=" + node + "&folder=" + currentFolder + "&file=" + file);
    };

    this.deletePath = function (node, nodeName, currentFolder, file) {
        that.eskimoMain.confirm("Are you sure you want to delete file " + file + "?", () => {
            $.ajaxGet({
                context: this,
                url: "file-manager-delete?nodeAddress=" + node + "&folder=" + currentFolder + "&file=" + file,
                success: (data, status, jqXHR) => {

                    if (data.status == "OK") {

                        that.openFolder(node, nodeName, currentFolder, ".");

                    } else {
                        that.eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error);

                        // FIXME Close File Manager or make disabled
                    }
                },
                error: errorHandler
            });
        });
    };

    function showRoot (node, nodeName) {
        that.openFolder (node, nodeName, "/", ".");
    }
    this.showRoot = showRoot;

    function showParent (node, nodeName) {

        let openedFileManager = findFileManager(nodeName);

        // find location of last /
        let indexOfLastSlash = 0;
        for (let i = openedFileManager.current.length - 1; i >= 0; i--) {
            if (openedFileManager.current.charAt(i) === "/") {
                indexOfLastSlash = i;
                break;
            }
        }

        let parentFolder = "/";
        if (indexOfLastSlash > 0) {
            parentFolder = openedFileManager.current.substring(0, indexOfLastSlash);
        }

        that.openFolder (node, nodeName, parentFolder, ".");
    }
    this.showParent = showParent;

    function showPrevious (node, nodeName) {

        let openedFileManager = findFileManager(nodeName);

        if (openedFileManager.previous != null && openedFileManager.previous != "") {

            that.openFolder(node, nodeName, openedFileManager.previous, ".");
        }
    }
    this.showPrevious = showPrevious;

    function refreshFolder (node, nodeName) {

        let openedFileManager = findFileManager(nodeName);
        that.openFolder(node, nodeName, openedFileManager.current, ".");
    }
    this.refreshFolder = refreshFolder;

    function createFile (node, nodeName) {
        let openedFileManager = findFileManager(nodeName);
        let currentFolder = openedFileManager.current;

        $('#filename-input-nodeName').val(nodeName);
        $('#filename-input-nodeAddress').val(node);
        $('#filename-input-currentfolder').val(currentFolder);


        $('#filename-input-modal').modal("show");
    }
    this.createFile = createFile;

    function validateCreateFile() {

        let nodeName = $('#filename-input-nodeName').val();
        let nodeAddress = $('#filename-input-nodeAddress').val();
        let currentFolder = $('#filename-input-currentfolder').val();
        let newFileName = $("#filename-input-input").val();

        $.ajaxGet({
            url: "file-manager-create-file?nodeAddress=" + nodeAddress + "&folder=" + currentFolder + "&fileName=" + newFileName,
            success: (data, status, jqXHR) => {

                if (data.status == "OK") {

                    listFolder (nodeAddress, nodeName, data.folder, data.content);

                } else {
                    that.eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error);

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

    this.openFolder = function (node, nodeName, currentFolder, subFolder) {
        console.log ("Opening folder for " + nodeName + " - " + currentFolder + "/" + subFolder);
        $.ajaxGet({
            url: "file-manager-navigate?nodeAddress=" + node + "&folder=" + currentFolder + "&subFolder=" + subFolder ,
            success: (data, status, jqXHR) => {

                if (data.status == "OK") {

                    listFolder (node, nodeName, data.folder, data.content);

                } else {
                    that.eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error);

                    // FIXME Close File Manager or make disabled
                }
            },
            error: errorHandler
        });
    };

    function uploadFile (node, nodeName) {

        let openedFileManager = findFileManager(nodeName);

        $("#file-manager-hidden-folder-"+nodeName).val(openedFileManager.current);

        $("#file-manager-hidden-file-"+nodeName).trigger('click');
    }
    this.uploadFile = uploadFile;

    function registerSubmitFormFileUpload (e, nodeName, node) {
        $("#file-manager-upload-form-"+nodeName).on('submit',(function(event) {

            // reset modal
            let fileUploadprogressBar = $('#file-upload-progress-bar');
            fileUploadprogressBar.attr('aria-valuenow', "1%").css('width', "1%");
            fileUploadprogressBar.html("1%");
            $('#file-upload-progress-modal').modal("show");

            let completeCallback = function (data) {

                $('#file-upload-progress-modal').modal("hide");

                if(data) {

                    if (data.status == "KO") {
                        that.eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error);
                    } else {
                        refreshFolder (node, nodeName);
                    }

                } else {
                    that.eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, "No result obtained from backend. This is an unexpected error.")
                }
            };

            event.preventDefault();
            $.ajax({
                url: "file-manager-upload?nodeAddress=" + node,
                xhr: () => {
                    let xhr = new window.XMLHttpRequest();
                    xhr.upload.addEventListener("progress", evt => {
                        if (evt.lengthComputable) {
                            let percentComplete = evt.loaded / evt.total;
                            console.log(percentComplete);
                            let newProgress = Math.ceil(percentComplete * 100);
                            fileUploadprogressBar.attr('aria-valuenow', newProgress+"%").css('width', newProgress+"%");
                            fileUploadprogressBar.html(newProgress+"%");
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
                error: (jqXHR, status) => {
                    errorHandler(jqXHR, status);
                    $('#file-upload-progress-modal').modal("hide");
                }
            });
        }));
    }

    this.connectFileManager = function (node, nodeName) {
        $.ajaxGet({
            context: this,
            url: "file-manager-connect?nodeAddress=" +node ,
            success: function (data, status, jqXHR) {

                // noinspection JSPotentiallyInvalidUsageOfThis
                if (data.status == "OK") {

                    openedFileManagers.push({"nodeName" : nodeName, "nodeAddress": node, "current": "/"});

                    listFolder (node, nodeName, data.folder, data.content);

                } else {
                    that.eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, data.error);

                    // FIXME Close File Manager
                }
            },
            error: errorHandler
        });
    };

    let closeFileManager = function (nodeName) {
        console.log(nodeName);
        // {"nbr": nbr, "nodeName": nodeName, "nodeAddress" : nodeAddress}

        // remove from open File Manager
        let openedFileManager = null;
        let closedFileManagerNbr;
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
            that.eskimoMain.alert(ESKIMO_ALERT_LEVEL.ERROR, "File Manager " + nodeName + " not found");
        } else {
            $.ajaxGet({
                url: "file-manager-remove?nodeAddress=" + openedFileManager.nodeAddress,
                success: (data, status, jqXHR) => { console.log(data); },
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

    function openFileManager (node, nodeName) {

        // add tab entry
        let fileManagerFound = false;
        let fileManagersTabList = $("#file-managers-tab-list");
        fileManagersTabList.find("li").each(function() {
            if (this.id == "file-manager_"+nodeName) {
                fileManagerFound = true;
            }
        });
console.log ("a");
        if (!fileManagerFound) {
console.log ("b");

            // Add tab entry
            fileManagersTabList.append($('<li id="file-manager_' + nodeName + '" class="nav-item">'+
                '<a class="nav-link" id="select_file_manager_' + nodeName  + '" href="#">' + node + '</a></li>'));

            let fileManagerContent = '<div class="col-md-12 file-manager-view" id="file-managers-file-manager-' + nodeName + '">\n' +
                '    <div id="file-manager-actions-' + nodeName + '">\n' +
                '        <nav id="file-manager-folder-menu-' + nodeName + '" class="btn-toolbar file-manager-folder-menu">\n' +
                '            <div class="btn-group">'+
                '                <button id="file-manager-close-' + nodeName + '" name="file-manager-close-' + nodeName + '" class="btn btn-secondary">Close</button>\n' +
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
                '    </div>\n' +
                '</div>';

            $("#file-managers-file-manager-content").append ($(fileManagerContent));

            $(document).on('change', '#file-manager-hidden-file-'+ nodeName, e => {

                let fileName = e.target.files[0].name;
                $("#file-manager-hidden-filename-"+nodeName).val(fileName);

                  //console.log ($('#file-manager-hidden-file-'+ nodeName).val());
                $("#file-manager-upload-form-"+nodeName).submit();
            });

            $(document).ready(e => { registerSubmitFormFileUpload (e, nodeName, node); });

            // $('input[type=file]').simpleUpload(url, options);

            $("#file-manager-close-" + nodeName).click(function () {
                let effNodeName = this.id.substring("file-manager-close-".length);
                closeFileManager(effNodeName);
            });

            // register on click handlers
            $('#select_file_manager_' + nodeName).click(function() {
                let effNodeName = this.id.substring("select_file_manager_".length);
                selectFileManager(getNodeAddress(effNodeName), effNodeName);
            });

            $('#show_root_' + nodeName).click(function() {
                let effNodeName = this.id.substring("show_root_".length);
                showRoot(getNodeAddress(effNodeName), effNodeName);
            });

            $('#show_parent_' + nodeName).click(function() {
                let effNodeName = this.id.substring("show_parent_".length);
                showParent(getNodeAddress(effNodeName), effNodeName);
            });

            $('#show_previous_' + nodeName).click(function() {
                let effNodeName = this.id.substring("show_previous_".length);
                showPrevious(getNodeAddress(effNodeName), effNodeName);
            });

            $('#refresh_folder_' + nodeName).click(function() {
                let effNodeName = this.id.substring("refresh_folder_".length);
                refreshFolder(getNodeAddress(effNodeName), effNodeName);
            });

            $('#upload_file_' + nodeName).click(function() {
                let effNodeName = this.id.substring("upload_file_".length);
                uploadFile(getNodeAddress(effNodeName), effNodeName);
            });

            $('#create_file_' + nodeName).click(function() {
                let effNodeName = this.id.substring("create_file_".length);
                createFile(getNodeAddress(effNodeName), effNodeName);
            });

            that.connectFileManager (node, nodeName);

        }
        selectFileManager(node, nodeName);
    }
    this.openFileManager = openFileManager;

};
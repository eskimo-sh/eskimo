/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */

package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.services.FileManagerService;
import ch.niceideas.eskimo.utils.ReturnStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


@Controller
public class FileManagerController {

    private static final Logger logger = Logger.getLogger(FileManagerController.class);

    @Autowired
    private FileManagerService fileManagerService;

    /* For tests */
    void setFileManagerService(FileManagerService fileManagerService) {
        this.fileManagerService = fileManagerService;
    }

    @GetMapping("/file-manager-remove")
    @ResponseBody
    public String removeFileManager(@RequestParam("address") String hostAddress) {
        try {
            fileManagerService.removeFileManager(hostAddress);

            return ReturnStatusHelper.createOKStatus();
        } catch (JSONException e) {
            logger.error (e, e);
            return ReturnStatusHelper.createErrorStatus(e);
        }
    }

    @GetMapping("/file-manager-connect")
    @ResponseBody
    public String connectFileManager(@RequestParam("address") String hostAddress) {

       return navigateFileManager(hostAddress, "/", ".");

    }

    @GetMapping("/file-manager-navigate")
    @ResponseBody
    public String navigateFileManager(
            @RequestParam("address") String hostAddress,
            @RequestParam("folder") String folder,
            @RequestParam("subFolder") String subFolder) {

        try {
            Pair<String, JSONObject> result = fileManagerService.navigateFileManager(hostAddress, folder, subFolder);

            return ReturnStatusHelper.createOKStatus (map -> {
                map.put("folder", result.getKey());
                map.put("content", result.getValue());
            });
        } catch (IOException e) {
            return ReturnStatusHelper.createErrorStatus(e);
        }
    }

    @GetMapping("/file-manager-create-file")
    @ResponseBody
    public String createFile(
            @RequestParam("address") String hostAddress,
            @RequestParam("folder") String folder,
            @RequestParam("fileName") String fileName) {

        try {
            Pair<String, JSONObject> result = fileManagerService.createFile(hostAddress, folder, fileName);

            return ReturnStatusHelper.createOKStatus (map -> {
                map.put("folder", result.getKey());
                map.put("content", result.getValue());
            });

        } catch (IOException e) {
            return ReturnStatusHelper.createErrorStatus(e);
        }
    }

    @GetMapping("/file-manager-open-file")
    @ResponseBody
    public String openFile(
            @RequestParam("address") String hostAddress,
            @RequestParam("folder") String folder,
            @RequestParam("file") String file) {

        try {
            return fileManagerService.openFile(hostAddress, folder, file).toString(2);

        } catch (JSONException e) {
            logger.error (e, e);
            return ReturnStatusHelper.createErrorStatus(e);

        }
    }

    @GetMapping(value = "/file-manager-download/{filename}")
    public void downloadFile(
            @RequestParam("address") String hostAddress,
            @RequestParam("folder") String folder,
            @RequestParam("file") String file,
            HttpServletResponse response) {
        response.setHeader("Content-Disposition", "attachment");
        fileManagerService.downloadFile (hostAddress, folder, file, new FileManagerService.HttpServletResponseAdapter() {

            @Override
            public void setContentType(String type) {
                response.setContentType(type);
            }

            @Override
            public ServletOutputStream getOutputStream() throws IOException {
                return response.getOutputStream();
            }
        });
        try {
            response.flushBuffer();
        } catch (IOException ex) {
            logger.error("Download error. Filename was " + file, ex);
            throw new FileManagerService.FileDownloadException("Download error. Filename was " + file, ex);
        }
    }

    @GetMapping(value = "/file-manager-delete")
    @ResponseBody
    public String deletePath (
            @RequestParam("address") String hostAddress,
            @RequestParam("folder") String folder,
            @RequestParam("file") String file) {

        try {
            String deletedPath =  fileManagerService.deletePath(hostAddress, folder, file);

            return ReturnStatusHelper.createOKStatus (map -> map.put("path", deletedPath));

        } catch (IOException e) {
            logger.error (e, e);
            return ReturnStatusHelper.createErrorStatus(e);
        }
    }

    @PostMapping("/file-manager-upload")
    @ResponseBody
    public String handleFileUpload(
            @RequestParam("address") String hostAddress,
            @RequestParam("folder") String folder,
            @RequestParam("filename") String filename,
            @RequestParam("file") MultipartFile file) {

        try {
            fileManagerService.uploadFile (hostAddress, folder, filename, file.getInputStream());

            return ReturnStatusHelper.createOKStatus (map -> map.put("file", file.getName()));

        } catch (IOException e) {
            logger.error (e, e);
            return ReturnStatusHelper.createErrorStatus(e);
        }
    }
}

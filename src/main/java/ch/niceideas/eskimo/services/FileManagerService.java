/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StreamUtils;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SFTPv3Client;
import com.trilead.ssh2.SFTPv3DirectoryEntry;
import com.trilead.ssh2.SFTPv3FileAttributes;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class FileManagerService {

    private static final Logger logger = Logger.getLogger(FileManagerService.class);

    private static final Pattern pattern = Pattern.compile("([tlcbdprwxs-]+) *([^ ]+) *([^ ]+) *([^ ]+) *([^ ]+) *(.*) +([^ ]+|'.+'|\".+\")");

    private static final String[] OTHER_TEXT_MIME_TYPES = new String[]{
            "application/x-sh",
            "application/x-csh",
            "text/x-c",
            "chemical/x-cml",
            "chemical/x-csml",
            "application/xml-dtd",
            "application/ecmascript",
            "application/javascript",
            "application/json",
            "application/x-latex",
            "application/xml",
            "inode/x-empty"
    };

    @Autowired
    private ConnectionManagerService connectionManagerService;

    @Autowired
    private SSHCommandService sshCommandService;

    @Value("${fileManager.maxFileSize}")
    protected int maxFileSize = 2097152;

    /* Controlers are singleton */
    private Map<String, SFTPv3Client> sftpClients = new ConcurrentHashMap<>();


    /** For tests */
    void setConnectionManagerService(ConnectionManagerService connectionManagerService) {
        this.connectionManagerService = connectionManagerService;
    }
    void setSshCommandService (SSHCommandService sshCommandService) {
        this.sshCommandService = sshCommandService;
    }

    public void removeFileManager(@RequestParam("address") String hostAddress) {
        logger.debug(hostAddress);

        SFTPv3Client client = sftpClients.get(hostAddress);
        if (client == null) {
            throw new IllegalStateException("Session not found : " + hostAddress);
        }

        client.close();
        sftpClients.remove(hostAddress);

    }

    public Pair<String, JSONObject> navigateFileManager(String hostAddress, String folder, String subFolder) throws IOException {

        try {

            SFTPv3Client client = getClient(hostAddress);

            String newPath = client.canonicalPath(folder + (folder.endsWith("/") ? "" : "/") + subFolder);

            List<SFTPv3DirectoryEntry> listing = client.ls(newPath);

            return new Pair<>(newPath, directoryListToJson (listing));

        } catch (IOException | ConnectionManagerException | JSONException e) {
            logger.error (e, e);
            throw new IOException(e.getMessage(), e);
        }
    }

    public Pair<String, JSONObject> createFile(String hostAddress, String folder, String fileName) throws IOException {

        try {

            SFTPv3Client client = getClient(hostAddress);

            String newFilePath = client.canonicalPath(folder + (folder.endsWith("/") ? "" : "/") + fileName);

            client.createFile(newFilePath);

            return navigateFileManager (hostAddress, folder, ".");

        } catch (IOException | ConnectionManagerException | JSONException e) {
            logger.error (e, e);
            throw new IOException(e.getMessage(), e);
        }
    }

    public JSONObject openFile(String hostAddress, String folder, String file) {

        try {

            SFTPv3Client client = getClient(hostAddress);

            String newPath = client.canonicalPath(folder + (folder.endsWith("/") ? "" : "/") + file);

            // test file type
            String fileMimeType = getFileMimeType(hostAddress, newPath);
            logger.info ("File " + newPath + " has MIME type " + fileMimeType);

            if (fileMimeType.contains("inode/directory")) {

                Pair <String, JSONObject> result = navigateFileManager (hostAddress, folder, file);
                return new JSONObject(new HashMap<String, Object>() {{
                    put ("status", "OK");
                    put ("folder", result.getKey());
                    put ("content", result.getValue());
                }});

            } else if (fileMimeType.contains("no read permission")) {

                return new JSONObject(new HashMap<String, Object>() {{
                    put("status", "OK");
                    put("accessible", false);
                    put("fileViewable", false);
                }});

            } else if (isTextMimeType (fileMimeType)) {

                // test size
                SFTPv3FileAttributes fileAttributes = client.stat(newPath);
                if (fileAttributes.size != null && fileAttributes.size <= maxFileSize) {

                    InputStream is = client.read(newPath);
                    String fileContent = StreamUtils.getAsString(is);

                    return new JSONObject(new HashMap<String, Object>() {{
                        put("status", "OK");
                        put("accessible", true);
                        put("fileViewable", true);
                        put ("fileName", newPath);
                        put ("fileContent", Base64.getEncoder().encodeToString(fileContent.getBytes()));
                    }});

                } else {

                    return new JSONObject(new HashMap<String, Object>() {{
                        put("status", "OK");
                        put("accessible", true);
                        put("fileViewable", false);
                    }});
                }
            }
            else {

                return new JSONObject(new HashMap<String, Object>() {{
                    put("status", "OK");
                    put("accessible", true);
                    put("fileViewable", false);
                }});
            }

        } catch (IOException | ConnectionManagerException | SSHCommandException e) {
            logger.error (e, e);

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "KO");
                put("error", e.getMessage());
            }});
        }
    }


    public void downloadFile(String hostAddress, String folder, String file, HttpServletResponse response) {
        try {

            SFTPv3Client client = getClient(hostAddress);

            String fullPath = client.canonicalPath(folder + (folder.endsWith("/") ? "" : "/") + file);

            // test file type
            String fileMimeType = getFileMimeType(hostAddress, fullPath).trim();
            if (fileMimeType.endsWith("\n")) {
                fileMimeType = fileMimeType.substring(0, fileMimeType.length() - 1);
            }

            response.setContentType(fileMimeType);

            // get your file as InputStream
            InputStream is = client.read(fullPath);

            // copy it to response's OutputStream
            StreamUtils.copy(is, response.getOutputStream());
            response.flushBuffer();

        } catch (IOException | SSHCommandException | ConnectionManagerException ex) {
            logger.error("Download error. Filename was " + file, ex);
            throw new FileDownloadException("IOError writing file to output stream");
        }
    }

    public String deletePath(String hostAddress, String folder, String file) throws IOException {

        try {
            SFTPv3Client client = getClient(hostAddress);

            String fullPath = client.canonicalPath(folder + (folder.endsWith("/") ? "" : "/") + file);

            client.rm(fullPath);

            return fullPath;

        } catch (ConnectionManagerException ex) {
            logger.error("Error deleting path. Error was " + file, ex);
            throw new IOException("IOError writing file to output stream", ex);
        }
    }

    public void uploadFile(String hostAddress, String folder, String name, InputStream fileContent) throws IOException {

        SFTPv3Client client = null;

        try {
            // getting dedicated client for file upload
            Connection con = connectionManagerService.getSharedConnection(hostAddress);
            client = new SFTPv3Client (con);

            String fullPath = client.canonicalPath(folder + (folder.endsWith("/") ? "" : "/") + name);

            writeFile(hostAddress, fullPath, fileContent);

        } catch (ConnectionManagerException ex) {
            logger.error("Upload error. Filename was " + name, ex);
            throw new IOException("IOError writing file to output stream", ex);

        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    protected void writeFile(String hostAddress, String fullPath, InputStream fileContent) throws IOException {

        try {
            SFTPv3Client client = getClient(hostAddress);

            OutputStream os = client.writeToFile(fullPath);

            StreamUtils.copyThenClose (fileContent, os);

        } catch (ConnectionManagerException ex) {
            logger.error("Error writing file to output stream. Filename path " + fullPath, ex);
            throw new IOException("IOError writing file to output stream", ex);
        }
    }

    String getFileMimeType(String hostAddress, String newPath) throws SSHCommandException {
        return sshCommandService.runSSHCommand(hostAddress, "file --brief --mime-type " + newPath);
    }

    boolean isTextMimeType(String fileMimeType) {

        if (fileMimeType.startsWith("text")) {
            return true;

        } else if (fileMimeType.contains("+xml")) {
            return true;

        } else {

            return Arrays.stream(OTHER_TEXT_MIME_TYPES)
                    .anyMatch(otherText -> fileMimeType.contains(otherText) && !otherText.contains("sharedlib"));
        }
    }

    synchronized SFTPv3Client getClient(@RequestParam("address") String hostAddress) throws ConnectionManagerException, IOException {

        SFTPv3Client client = sftpClients.get(hostAddress);

        if (client == null) {

            Connection con = connectionManagerService.getSharedConnection(hostAddress);
            client = new SFTPv3Client (con);

            sftpClients.put(hostAddress, client);

        } else {

            // test it
            boolean recreate = false;
            try {
                if (!client.exists("/")) {
                    logger.warn("SFTP Client for " + hostAddress + " got into problems. Recreating");
                    recreate = true;
                }
            } catch (IOException e) {
                logger.warn("SFTP Client for " + hostAddress + " got into problems. Recreating");
                logger.warn (e.getMessage());
                logger.debug (e, e);
                recreate = true;
            }
            if (recreate) {
                return recreateClient(hostAddress);
            }
        }

        return client;
    }

    private SFTPv3Client recreateClient(@RequestParam("address") String hostAddress) throws ConnectionManagerException, IOException {
        sftpClients.remove(hostAddress);
        return getClient(hostAddress);
    }

    JSONObject directoryListToJson(List<SFTPv3DirectoryEntry> folder) {

        Map<String, Object> directoryContent = new HashMap<>();

        for (SFTPv3DirectoryEntry entry : folder) {

            Matcher matcher = pattern.matcher(entry.longEntry);
            if (!matcher.matches()) {
                throw new IllegalStateException("Couldn't parse " + entry.longEntry);
            }

            String tsString = formatTimestamp(matcher);

            directoryContent.put(entry.filename, new JSONObject(new HashMap<String, Object>() {{
                put ("permissions", matcher.group(1));
                put ("count", matcher.group(2));
                put ("user", matcher.group(3));
                put ("group", matcher.group(4));
                put ("size", matcher.group(5));
                put ("timestamp", tsString);
            }}));
        }

        return new JSONObject(directoryContent);
    }

    private String formatTimestamp(Matcher matcher) {
        String tsString = matcher.group(6);
        if (tsString.length() > 12) {
            tsString = tsString.substring(0, 12);
        }
        return tsString;
    }

    public static class FileDownloadException extends RuntimeException {

        static final long serialVersionUID = -3117632123352229248L;

        FileDownloadException(String message) {
            super(message);
        }
    }
}

/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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


package ch.niceideas.eskimo.test.services;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.services.FileManagerService;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;

@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile("no-cluster")
public class FileManagerServiceTestImpl implements FileManagerService {

    @Override
    public void removeFileManager(String node) {
        // NO-OP
    }

    @Override
    public Pair<String, JSONObject> navigateFileManager(String node, String folder, String subFolder) throws IOException {

        if (node.equals("192.168.10.15")) {

            return new Pair<>(folder + subFolder, new JSONObject(new HashMap<String, Object>() {{
                put("test", new JSONObject(new HashMap<String, Object>() {{
                    put("permissions", "rwxrwxrwx");
                    put("count", "1");
                    put("user", "badtrash");
                    put("group", "badtrash");
                    put("size", "1024");
                    put("timestamp", "2018-01-01 12:00:00");
                }}));
            }}));

        } else {
            throw new IOException ("Test Error");
        }
    }

    @Override
    public Pair<String, JSONObject> createFile(String node, String folder, String fileName) throws IOException {

        if (node.equals("192.168.10.15")) {
            return new Pair<>(folder, new JSONObject(new HashMap<String, Object>() {{
                put(fileName, new JSONObject(new HashMap<String, Object>() {{
                    put("permissions", "rwxrwxrwx");
                    put("count", "1");
                    put("user", "badtrash");
                    put("group", "badtrash");
                    put("size", "1024");
                    put("timestamp", "2018-01-01 12:00:00");
                }}));
            }}));

        } else {
            throw new IOException ("Test Error");
        }
    }

    @Override
    public JSONObject openFile(String node, String folder, String file) {
        if (node.equals("192.168.10.15")) {
            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("accessible", true);
                put("fileViewable", true);
                put("fileName", folder + file);
                put("fileContent", Base64.getEncoder().encodeToString("TEST CONTENT".getBytes()));
            }});

        } else {
            throw new JSONException( "Test Error");
        }

    }

    @Override
    public void downloadFile(String node, String folder, String file, HttpServletResponseAdapter response) {

    }

    @Override
    public String deletePath(String node, String folder, String file) throws IOException {
        if (node.equals("192.168.10.15")) {
            return folder + file;
        } else {
            throw new IOException("Test Error");
        }
    }

    @Override
    public void uploadFile(String node, String folder, String name, InputStream fileContent) throws IOException {
        if (node.equals("192.168.10.15")) {
            // No-Op
        } else {
            throw new IOException("Test Error");
        }
    }
}

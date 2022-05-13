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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.AbstractBaseSSHTest;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import com.trilead.ssh2.SFTPv3Client;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

public class FileManagerServiceTest extends AbstractBaseSSHTest {

    @Override
    protected CommandFactory getSShSubsystemToUse() {
        return new ProcessShellCommandFactory();
    }

    /** Run Test on Linux only */
    @BeforeEach
    public void beforeMethod() {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    private ConnectionManagerService cm = null;

    private ProxyManagerService pms = null;

    private FileManagerService sc = null;

    private SSHCommandService scs = null;

    private SetupService setupService = null;

    private ConfigurationService cs = null;

    @BeforeEach
    public void setUp() throws Exception {
        setupService = new SetupService();
        String tempPath = SystemServiceTest.createTempStoragePath();
        setupService.setConfigStoragePathInternal(tempPath);
        FileUtils.writeFile(new File(tempPath + "/config.json"), "{ \"ssh_username\" : \"test\" }");

        cm = new ConnectionManagerService(privateKeyRaw, getSShPort());
        cm.setSetupService (setupService);

        sc = new FileManagerService();

        scs = new SSHCommandService();
        scs.setConnectionManagerService(cm);

        sc.setConnectionManagerService(cm);
        sc.setSshCommandService(scs);

        pms = new ProxyManagerService();
        pms.setConnectionManagerService(cm);
        cm.setProxyManagerService(pms);
        pms.setConnectionManagerService(cm);

        cs = new ConfigurationService();
        cs.setSetupService(setupService);

        cm.setConfigurationService(cs);
    }

    @Test
    public void testConnectSftp() throws Exception {
        assertNotNull (sshd);
        assertNotNull (cm);
        assertNotNull (sc);

        Pair<String, JSONObject> result = sc.navigateFileManager("localhost", "/", ".");

        assertEquals ("/", result.getKey());

        JSONObject dir = result.getValue();

        assertNotNull (dir);

        assertTrue(dir.getJSONObject(".").toString().contains("\"size\":\"4096\",\"user\":\"root\",\"permissions\""));
    }

    @Test
    public void testNavigateSftp() throws Exception {
        assertNotNull (sshd);
        assertNotNull (cm);
        assertNotNull (sc);

        Pair<String, JSONObject> result = sc.navigateFileManager("localhost", "/", "boot");

        assertEquals ("/boot", result.getKey());

        JSONObject dir = result.getValue();

        assertNotNull (dir);

        assertTrue(dir.getJSONObject(".").toString().contains("\"size\":\"4096\",\"user\":\"root\",\"permissions\""));
    }

    @Test
    public void testIsTextMimeType() {

        assertTrue(sc.isTextMimeType("text/plain"));
        assertTrue(sc.isTextMimeType("text/csv"));

        assertTrue(sc.isTextMimeType("application/resource-lists-diff+xml"));
        assertTrue(sc.isTextMimeType("application/xslt+xml"));

        assertTrue(sc.isTextMimeType("application/javascript"));

        assertFalse(sc.isTextMimeType("application/x-xpinstall"));
    }

    @Test
    public void testDeleteFile() throws Exception {

        File tempFile = File.createTempFile("test-", "test");
        FileUtils.writeFile(tempFile, "Test File Content");
        assertTrue(tempFile.exists());

        sc.deletePath("localhost", tempFile.getParent(), tempFile.getName());

        assertFalse(tempFile.exists());
    }

    @Test
    public void testDownloadFile() throws Exception {

        File tempFile = File.createTempFile("test-", "test");
        FileUtils.writeFile(tempFile, "Test File Content");
        assertTrue(tempFile.exists());

        OutputStream testStream = new ByteArrayOutputStream();

        ServletOutputStream streamWrapper = new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }

            @Override
            public void write(int b) throws IOException {
                testStream.write(b);
            }
        };

        HttpServletResponse proxyResponse = (HttpServletResponse) Proxy.newProxyInstance(
                FileManagerServiceTest.class.getClassLoader(),
                new Class[] { HttpServletResponse.class },
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("getOutputStream")) {
                        return streamWrapper;
                    }
                    return null;
                });

        sc.downloadFile("localhost", tempFile.getParent(), tempFile.getName(), new FileManagerService.HttpServletResponseAdapter(){

            @Override
            public void setContentType(String type) {
                proxyResponse.setContentType(type);
            }

            @Override
            public ServletOutputStream getOutputStream() throws IOException {
                return proxyResponse.getOutputStream();
            }
        });

        String downloadedContent = testStream.toString();
        String originalContent = FileUtils.readFile(tempFile);

        assertEquals (originalContent, downloadedContent);
    }

    void getTestClient(String mimeType) throws IOException, ConnectionManagerException {
        sc = new FileManagerService() {
            @Override
            SFTPv3Client getClient(@RequestParam("address") String node) throws ConnectionManagerException, IOException {
                return new SFTPv3Client(cm.getSharedConnection("localhost").getUnder());
            }
            @Override
            String getFileMimeType(String node, String newPath) throws SSHCommandException {
                return mimeType;
            }
            @Override
            public Pair<String, JSONObject> navigateFileManager(String node, String folder, String subFolder) throws IOException {
                return new Pair<>("/test", new JSONObject(new HashMap<String, Object>() {{

                    put ("test", new JSONObject(new HashMap<String, Object>() {{
                        put ("permissions", "rwxrwxrwx");
                        put ("count", "2");
                        put ("user", "badtrash");
                        put ("group", "badtrash");
                        put ("size", "1024");
                        put ("timestamp", "2019-11-11 08:30:00");
                    }}));

                }}));
            }
        };
    }

    @Test
    public void testOpenFileDirectory() throws Exception {

        getTestClient("inode/directory");

        sc.setConnectionManagerService(cm);
        sc.setSshCommandService(scs);

        JSONObject result = sc.openFile("localhost", "/etc", "passwd");

        assertEquals ("{\n" +
                "  \"content\": {\"test\": {\n" +
                "    \"count\": \"2\",\n" +
                "    \"size\": \"1024\",\n" +
                "    \"user\": \"badtrash\",\n" +
                "    \"permissions\": \"rwxrwxrwx\",\n" +
                "    \"group\": \"badtrash\",\n" +
                "    \"timestamp\": \"2019-11-11 08:30:00\"\n" +
                "  }},\n" +
                "  \"folder\": \"/test\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", result.toString(2));
    }

    @Test
    public void testOpenFileNoPermission() throws Exception {

        getTestClient("no read permission");

        sc.setConnectionManagerService(cm);
        sc.setSshCommandService(scs);

        JSONObject result = sc.openFile("localhost", "/etc", "passwd");

        assertEquals ("{\n" +
                "  \"accessible\": false,\n" +
                "  \"fileViewable\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", result.toString(2));
    }

    @Test
    public void testOpenFileBinaryFile() throws Exception {

        File tempFile = File.createTempFile("test", "sftp");
        FileUtils.writeFile(tempFile, "ABCD");

        getTestClient("application/binary");

        sc.setConnectionManagerService(cm);
        sc.setSshCommandService(scs);

        JSONObject result = sc.openFile("localhost", tempFile.getParent(), tempFile.getName());

        assertEquals ("{\n" +
                "  \"accessible\": true,\n" +
                "  \"fileViewable\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", result.toString(2));
    }

    @Test
    public void testOpenFileTextSmallFile() throws Exception {

        File tempFile = File.createTempFile("test", "sftp");
        FileUtils.writeFile(tempFile, "ABCD");

        getTestClient("text/plain");

        sc.setConnectionManagerService(cm);
        sc.setSshCommandService(scs);

        JSONObject result = sc.openFile("localhost", tempFile.getParent(), tempFile.getName());

        assertEquals (tempFile.getAbsolutePath(), result.getString("fileName"));
        assertEquals (Base64.getEncoder().encodeToString("ABCD".getBytes()), result.getString("fileContent"));

        tempFile.delete();
    }

    @Test
    public void testOpenFileTextBigFile() throws Exception {

        File tempFile = File.createTempFile("test_big", "sftp");
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile)));
        for (int i = 0; i < 2000000; i++) {
            bw.write("NEW_LINE_" + ThreadLocalRandom.current().nextInt());
        }
        bw.close();

        getTestClient("text/plain");

        sc.setConnectionManagerService(cm);
        sc.setSshCommandService(scs);

        JSONObject result = sc.openFile("localhost", tempFile.getParent(), tempFile.getName());

        assertEquals ("{\n" +
                "  \"accessible\": true,\n" +
                "  \"fileViewable\": false,\n" +
                "  \"status\": \"OK\"\n" +
                "}", result.toString(2));

        tempFile.delete();
    }

    @Test
    public void testCreateFile() throws Exception {

        File tempFile = File.createTempFile("test", "sftp");

        tempFile.delete();

        sc.createFile("localhost", "/tmp/", tempFile.getName());

        assertTrue(tempFile.exists());

        IOException exception = assertThrows(IOException.class, () -> sc.createFile("localhost", "/", tempFile.getName()));

        assertTrue(exception.getMessage().contains("Permission denied"));

    }


}

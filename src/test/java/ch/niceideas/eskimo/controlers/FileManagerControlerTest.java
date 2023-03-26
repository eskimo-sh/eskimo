/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.test.infrastructure.HttpObjectsHelper;
import org.apache.catalina.ssi.ByteArrayServletOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-file-manager", "test-system", "test-setup"})
public class FileManagerControlerTest {

    @Autowired
    private FileManagerController fmc;

    @Test
    public void testRemoveFileManager() {

        String result = fmc.removeFileManager("192.168.10.15");

        assertEquals("{\"status\": \"OK\"}", result);
    }

    @Test
    public void testNavigateFileManager() {

        assertEquals("{\n" +
                "  \"content\": {\"test\": {\n" +
                "    \"count\": \"1\",\n" +
                "    \"size\": \"1024\",\n" +
                "    \"user\": \"badtrash\",\n" +
                "    \"permissions\": \"rwxrwxrwx\",\n" +
                "    \"group\": \"badtrash\",\n" +
                "    \"timestamp\": \"2018-01-01 12:00:00\"\n" +
                "  }},\n" +
                "  \"folder\": \"/etc\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", fmc.navigateFileManager("192.168.10.15", "/", "etc"));

        assertEquals("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", fmc.navigateFileManager("192.168.10.16", "/", "etc"));
    }

    @Test
    public void testCreateFile() {

        assertEquals("{\n" +
                "  \"content\": {\"passwd\": {\n" +
                "    \"count\": \"1\",\n" +
                "    \"size\": \"1024\",\n" +
                "    \"user\": \"badtrash\",\n" +
                "    \"permissions\": \"rwxrwxrwx\",\n" +
                "    \"group\": \"badtrash\",\n" +
                "    \"timestamp\": \"2018-01-01 12:00:00\"\n" +
                "  }},\n" +
                "  \"folder\": \"/etc/\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", fmc.createFile("192.168.10.15", "/etc/", "passwd"));

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", fmc.createFile("192.168.10.16", "/etc/", "passwd"));
    }

    @Test
    public void testDeletePath() {

        assertEquals ("{\n" +
                "  \"path\": \"/etc/passwd\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", fmc.deletePath("192.168.10.15", "/etc/", "passwd"));

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", fmc.deletePath("192.168.10.16", "/etc/", "passwd"));
    }

    @Test
    public void testHandleFileUpload() {

        MultipartFile file = (MultipartFile) Proxy.newProxyInstance(
                FileManagerControlerTest.class.getClassLoader(),
                new Class[]{MultipartFile.class},
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("getInputStream")) {
                        return new ByteArrayInputStream("TEST CONTENT".getBytes());
                    } else if (method.getName().equals("getName")) {
                        return "passwd";
                    } else {
                        throw new UnsupportedOperationException(
                                "Unsupported method: " + method.getName());
                    }
                });

        assertEquals ("{\n" +
                "  \"file\": \"passwd\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", fmc.handleFileUpload("192.168.10.15", "/etc/", "passwd", file));

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", fmc.handleFileUpload("192.168.10.16", "/etc/", "passwd", file));
    }

    @Test
    public void testOpenFile() {

        assertEquals ("{\n" +
                "  \"accessible\": true,\n" +
                "  \"fileName\": \"/etc/passwd\",\n" +
                "  \"fileViewable\": true,\n" +
                "  \"fileContent\": \"VEVTVCBDT05URU5U\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", fmc.openFile("192.168.10.15", "/etc/", "passwd"));

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", fmc.openFile("192.168.10.16", "/etc/", "passwd"));
    }

    @Test
    public void testDownloadFile() throws Exception {

        File tempFile = File.createTempFile("test", "fmc");
        assertTrue (tempFile.delete());

        FileUtils.writeFile(tempFile, "test content");

        ByteArrayServletOutputStream baos = new ByteArrayServletOutputStream();

        Map<String, Object> headers = new HashMap<>();

        HttpServletResponse response = HttpObjectsHelper.createHttpServletResponse(headers, baos);

        fmc.downloadFile("192.168.56.21", tempFile.getParent(), tempFile.getName(), response);

        assertEquals ("test content", new String (baos.toByteArray(), StandardCharsets.UTF_8));
    }
}

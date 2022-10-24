package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.services.FileManagerServiceImpl;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.Base64;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-file-manager"})
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
}

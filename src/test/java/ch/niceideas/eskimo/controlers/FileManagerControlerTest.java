package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.services.FileManagerService;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.Base64;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileManagerControlerTest {

    FileManagerController fmc = new FileManagerController();

    @Test
    public void testRemoveFileManager() {
        fmc.setFileManagerService(new FileManagerService() {
            @Override
            public void removeFileManager(@RequestParam("address") String node) {
                // NO-OP
            }
        });

        String result = fmc.removeFileManager("192.168.10.15");

        assertEquals("{\"status\": \"OK\"}", result);
    }

    @Test
    public void testNavigateFileManager() {
        fmc.setFileManagerService(new FileManagerService() {
            @Override
            public Pair<String, JSONObject> navigateFileManager(String node, String folder, String subFolder) throws IOException {

                return new Pair<>("/etc/", new JSONObject(new HashMap<String, Object>(){{
                    put("passwd", new JSONObject(new HashMap<String, Object>() {{
                        put ("permissions", "rwxrwxrwx");
                        put ("count", "1");
                        put ("user", "badtrash");
                        put ("group", "badtrash");
                        put ("size", "1024");
                        put ("timestamp", "2018-01-01 12:00:00");
                    }}));
                }}));
            }
        });

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
                "}", fmc.navigateFileManager("192.168.10.15", "/", "etc"));

        fmc.setFileManagerService(new FileManagerService() {
            @Override
            public Pair<String, JSONObject> navigateFileManager(String node, String folder, String subFolder) throws IOException {
                throw new IOException ("Test Error");
            }
        });

        assertEquals("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", fmc.navigateFileManager("192.168.10.15", "/", "etc"));
    }

    @Test
    public void testCreateFile() {
        fmc.setFileManagerService(new FileManagerService() {
            @Override
            public Pair<String, JSONObject> createFile(String node, String folder, String fileName) throws IOException {
                return new Pair<>("/etc/", new JSONObject(new HashMap<String, Object>(){{
                    put("passwd", new JSONObject(new HashMap<String, Object>() {{
                        put ("permissions", "rwxrwxrwx");
                        put ("count", "1");
                        put ("user", "badtrash");
                        put ("group", "badtrash");
                        put ("size", "1024");
                        put ("timestamp", "2018-01-01 12:00:00");
                    }}));
                }}));
            }
        });

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

        fmc.setFileManagerService(new FileManagerService() {
            @Override
            public Pair<String, JSONObject> createFile(String node, String folder, String fileName) throws IOException {
                throw new IOException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", fmc.createFile("192.168.10.15", "/etc/", "passwd"));
    }

    @Test
    public void testDeletePath() {

        fmc.setFileManagerService(new FileManagerService() {
            @Override
            public String deletePath(String node, String folder, String file) throws IOException {
                return folder + file;
            }
        });

        assertEquals ("{\n" +
                "  \"path\": \"/etc/passwd\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", fmc.deletePath("192.168.10.15", "/etc/", "passwd"));


        fmc.setFileManagerService(new FileManagerService() {
            @Override
            public String deletePath(String node, String folder, String file) throws IOException {
                throw new IOException("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", fmc.deletePath("192.168.10.15", "/etc/", "passwd"));
    }

    @Test
    public void testHandleFileUpload() {
        fmc.setFileManagerService(new FileManagerService() {
            @Override
            public void uploadFile(String node, String folder, String name, InputStream fileContent) throws IOException {
                // NO OP
            }
        });

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

        fmc.setFileManagerService(new FileManagerService() {
            @Override
            public void uploadFile(String node, String folder, String name, InputStream fileContent) throws IOException {
                throw new IOException ("Test Error");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Test Error\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", fmc.handleFileUpload("192.168.10.15", "/etc/", "passwd", file));
    }

    @Test
    public void testOpenFile() {
        fmc.setFileManagerService(new FileManagerService() {
            @Override
            public JSONObject openFile(String node, String folder, String file) {

                return new JSONObject(new HashMap<String, Object>() {{
                    put("status", "OK");
                    put("accessible", true);
                    put("fileViewable", true);
                    put("fileName", folder+file);
                    put("fileContent", Base64.getEncoder().encodeToString("TEST CONTENT".getBytes()));
                }});
            }
        });

        assertEquals ("{\n" +
                "  \"accessible\": true,\n" +
                "  \"fileName\": \"/etc/passwd\",\n" +
                "  \"fileViewable\": true,\n" +
                "  \"fileContent\": \"VEVTVCBDT05URU5U\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", fmc.openFile("192.168.10.15", "/etc/", "passwd"));

        fmc.setFileManagerService(new FileManagerService() {
            @Override
            public JSONObject openFile(String node, String folder, String file) {
                throw new JSONException("TEST ERROR");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"TEST ERROR\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", fmc.openFile("192.168.10.15", "/etc/", "passwd"));
    }
}

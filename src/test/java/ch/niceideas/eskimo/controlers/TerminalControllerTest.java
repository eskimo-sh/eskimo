package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.services.TerminalService;
import ch.niceideas.eskimo.terminal.ScreenImage;
import ch.niceideas.eskimo.terminal.Terminal;
import org.json.JSONException;
import org.junit.Test;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;

public class TerminalControllerTest {

    private TerminalController tc = new TerminalController();

    @Test
    public void testRemoveTerminal() {

        tc.setTerminalService(new TerminalService() {
            @Override
            public void removeTerminal (String sessionId) throws IOException {
                // No Op
            }
        });

        assertEquals ("{\"status\": \"OK\"}", tc.removeTerminal("ABC"));

        tc.setTerminalService(new TerminalService() {
            @Override
            public void removeTerminal (String sessionId) throws IOException {
                throw new JSONException("Error Test");
            }
        });

        assertEquals ("{\n" +
                "  \"error\": \"Error Test\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", tc.removeTerminal("ABC"));
    }


    @Test
    public void testPostUpdate() {

        tc.setTerminalService(new TerminalService() {
            @Override
            public ScreenImage postUpdate(String terminalBody) throws IOException {
                Terminal t = new Terminal(60, 24);
                return t.dumpHtml(true, 30);
            }
        });

        Map<String, String> headers = new HashMap<>();

        HttpServletResponse httpServletResponse = (HttpServletResponse) Proxy.newProxyInstance(
                NodesConfigController.class.getClassLoader(),
                new Class[]{HttpServletResponse.class},
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("setContentType")) {
                        return headers.put ("Content-Type", (String)methodArgs[0]);
                    } else if (method.getName().equals("addHeader")) {
                        return headers.put ((String)methodArgs[0], (String)methodArgs[1]);
                    } else {
                        throw new UnsupportedOperationException(
                                "Unsupported method: " + method.getName());
                    }
                });

        assertEquals ("<pre class='term '><span class='f7 b0 cur'> </span><span class='f7 b0'>                                                           \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "                                                            \n" +
                "</span></pre>", tc.postUpdate("abc", httpServletResponse));

    }

}

package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.services.TerminalService;
import ch.niceideas.eskimo.terminal.ScreenImage;
import ch.niceideas.eskimo.terminal.Terminal;
import ch.niceideas.eskimo.test.infrastructure.HttpObjectsHelper;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                throw new IOException("Error Test");
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

        HttpServletResponse httpServletResponse = HttpObjectsHelper.createHttpServletResponse(headers, null);

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

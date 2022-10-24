package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.services.TerminalService;
import ch.niceideas.eskimo.terminal.ScreenImage;
import ch.niceideas.eskimo.terminal.Terminal;
import ch.niceideas.eskimo.test.infrastructure.HttpObjectsHelper;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.TerminalServiceTestImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-terminal"})
public class TerminalControllerTest {

    @Autowired
    private TerminalController tc;

    @Autowired
    private TerminalServiceTestImpl terminalServiceTest;

    @BeforeEach
    public void setUp() throws Exception {
        SecurityContextHelper.loginAdmin();
    }

    @Test
    public void testRemoveTerminal() {

        assertEquals ("{\"status\": \"OK\"}", tc.removeTerminal("ABC"));

        terminalServiceTest.setRemoveTerminalError();

        assertEquals ("{\n" +
                "  \"error\": \"Error Test\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", tc.removeTerminal("ABC"));
    }


    @Test
    public void testPostUpdate() {

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

package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.services.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-system", "test-setup"})
public class NotificationControllerTest {

    @Autowired
    private NotificationController notificationController;

    @Autowired
    private NotificationService notificationService;

    @Test
    public void testFetchNotifications() {

        notificationService.addInfo("Info 1");
        notificationService.addError("Error 1");
        notificationService.addDoing("Doing 1");

        assertEquals ("{\n" +
                "  \"lastLine\": \"3\",\n" +
                "  \"notifications\": [\n" +
                "    {\n" +
                "      \"type\": \"Info\",\n" +
                "      \"message\": \"Info 1\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"type\": \"Error\",\n" +
                "      \"message\": \"Error 1\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"type\": \"Doing\",\n" +
                "      \"message\": \"Doing 1\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"status\": \"OK\"\n" +
                "}", notificationController.fetchNotifications(0));
    }
}

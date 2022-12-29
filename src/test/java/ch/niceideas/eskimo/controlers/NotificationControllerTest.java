package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.services.NotificationService;
import org.json.JSONArray;
import org.json.JSONObject;
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

        JSONObject result = new JSONObject(notificationController.fetchNotifications(0));

        assertEquals (3, result.getInt("lastLine"));

        JSONArray notifArray = result.getJSONArray("notifications");

        assertEquals ("Info", notifArray.getJSONObject(0).get("type"));
        assertEquals ("Info 1", notifArray.getJSONObject(0).get("message"));

        assertEquals ("Error", notifArray.getJSONObject(1).get("type"));
        assertEquals ("Error 1", notifArray.getJSONObject(1).get("message"));

        assertEquals ("Doing", notifArray.getJSONObject(2).get("type"));
        assertEquals ("Doing 1", notifArray.getJSONObject(2).get("message"));
    }
}

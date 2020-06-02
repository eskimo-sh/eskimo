package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.services.NotificationService;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertThrows;

public class NotificationControllerTest {

    private NotificationController notificationController = new NotificationController();

    @Test
    public void testFetchNotifications() {

        notificationController.setNotificationService(new NotificationService() {
            @Override
            public Pair<Integer, List<JSONObject>> fetchElements(int lastLine) {
                return new Pair<>(3, new ArrayList<JSONObject>(){{
                    add(new JSONObject(new HashMap<String, String>(){{
                        put ("Info", "Info 1");
                        put ("Error", "Error 1");
                        put ("Doing", "Doing 1");
                    }}));
                }});
            }
        });

        assertEquals ("{\n" +
                "  \"lastLine\": \"3\",\n" +
                "  \"notifications\": [{\n" +
                "    \"Info\": \"Info 1\",\n" +
                "    \"Doing\": \"Doing 1\",\n" +
                "    \"Error\": \"Error 1\"\n" +
                "  }],\n" +
                "  \"status\": \"OK\"\n" +
                "}", notificationController.fetchNotifications(0));

        notificationController.setNotificationService(new NotificationService() {
            @Override
            public Pair<Integer, List<JSONObject>> fetchElements(int lastLine) {
                throw new JSONException("Test Error");
            }
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> notificationController.fetchNotifications(0));

        assertEquals ("org.json.JSONException: Test Error", exception.getMessage());
    }
}

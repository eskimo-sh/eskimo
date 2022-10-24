package ch.niceideas.eskimo.test.infrastructure;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.services.NotificationService;
import org.json.JSONObject;

import java.util.List;
import java.util.stream.Collectors;

public class NotificationHelper {

    public static String getAssembledNotifications(NotificationService notificationService) {
        Pair<Integer, List<JSONObject>> notifications =  notificationService.fetchElements(0);
        return notifications.getValue().stream()
                .map(obj -> obj.get("message").toString())
                .collect(Collectors.joining(","));
    }
}

package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.services.MessagingManager;
import org.json.JSONException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MessagingControllerTest {

    private MessagingController messagingController = new MessagingController();

    @Test
    public void testFetchMessages() {

        messagingController.setMessagingService(new MessagingManager() {
            @Override
            public Pair<Integer, String> fetchElements(int lastLine) {
                return new Pair<>(3, "Line1\nLine2\nLine3");
            }
        });

        assertEquals ("{\n" +
                "  \"lines\": \"TGluZTEKTGluZTIKTGluZTM=\",\n" +
                "  \"lastLine\": 3,\n" +
                "  \"status\": \"OK\"\n" +
                "}", messagingController.fetchMessages(0));

        messagingController.setMessagingService(new MessagingManager() {
            @Override
            public Pair<Integer, String> fetchElements(int lastLine) {
                throw new JSONException("Test Error");
            }
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> messagingController.fetchMessages(0));

        assertEquals ("org.json.JSONException: Test Error", exception.getMessage());
    }
}

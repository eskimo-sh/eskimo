package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.model.JSONOpCommand;
import ch.niceideas.eskimo.services.MessagingService;
import ch.niceideas.eskimo.services.NotificationService;
import ch.niceideas.eskimo.services.OperationsMonitoringService;
import ch.niceideas.eskimo.services.SystemService;
import ch.niceideas.eskimo.utils.ReturnStatusHelper;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.HashMap;

public class AbstractOperationController {

    @Resource
    protected MessagingService messagingService;

    @Autowired
    protected NotificationService notificationService;

    @Autowired
    protected SystemService systemService;

    @Autowired
    protected OperationsMonitoringService operationsMonitoringService;

    @Value("${eskimo.demoMode}")
    private boolean demoMode = false;

    /** For tests */
    void setSystemService(SystemService systemService) {
        this.systemService = systemService;
    }
    void setMessagingService(MessagingService messagingService) { this.messagingService = messagingService; }
    void setNotificationService (NotificationService notificationService) { this.notificationService = notificationService; }
    void setDemoMode (boolean demoMode) {
        this.demoMode = demoMode;
    }
    void setOperationsMonitoringService (OperationsMonitoringService operationsMonitoringService) {
        this.operationsMonitoringService = operationsMonitoringService;
    }

    protected String returnCommand(JSONOpCommand command) {
        return ReturnStatusHelper.createOKStatus(map -> map.put("command", command.toJSON()));
    }

    protected JSONObject checkOperations(String demoMessage) {

        JSONObject checkObject = null;

        if (operationsMonitoringService.isProcessingPending()) {

            String message = "Some backend operations are currently running. Please retry after they are completed.";

            messagingService.addLines (message);
            notificationService.addError("Operation In Progress");

            checkObject = new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("messages", message);
            }});
        }

        if (demoMode) {

            messagingService.addLines (demoMessage);
            notificationService.addError("Demo Mode");

            checkObject = new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("messages", demoMessage);
            }});
        }

        return checkObject;
    }
}

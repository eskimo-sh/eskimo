package ch.niceideas.eskimo.services.mdStrategy;

import ch.niceideas.eskimo.model.MasterDetection;
import ch.niceideas.eskimo.model.Service;
import ch.niceideas.eskimo.services.*;

import java.util.Date;

public interface MdStrategy {

    Date detectMaster(
            Service service, String node, MasterDetection masterDetection,
            MasterService masterService, SSHCommandService sshCommandService,
            MessagingService messagingService, NotificationService notificationService) throws MasterDetectionException;
}

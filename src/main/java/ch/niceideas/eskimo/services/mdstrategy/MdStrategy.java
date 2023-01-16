package ch.niceideas.eskimo.services.mdstrategy;

import ch.niceideas.eskimo.model.service.MasterDetection;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.services.*;

import java.util.Date;

public interface MdStrategy {

    Date detectMaster(
            Service service, String node, MasterDetection masterDetection,
            MasterService masterService, SSHCommandService sshCommandService,
            NotificationService notificationService) throws MasterDetectionException;
}

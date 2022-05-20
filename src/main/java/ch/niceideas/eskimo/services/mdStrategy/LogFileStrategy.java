package ch.niceideas.eskimo.services.mdStrategy;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.service.MasterDetection;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.services.*;
import org.apache.log4j.Logger;

import java.text.ParseException;
import java.util.Date;
import java.util.regex.Matcher;

public class LogFileStrategy implements MdStrategy {

    private static final Logger logger = Logger.getLogger(LogFileStrategy.class);

    @Override
    public Date detectMaster(
            Service service, String node, MasterDetection masterDetection,
            MasterService masterService, SSHCommandService sshCommandService,
            NotificationService notificationService) throws MasterDetectionException {

        String ping = null;
        try {
            ping = sendPing(sshCommandService, node);
        } catch (SSHCommandException e) {
            logger.warn(e.getMessage());
            logger.debug(e, e);
        }

        if (StringUtils.isNotBlank(ping) && ping.startsWith("OK")) {

            try {
                String grepResult = sshCommandService.runSSHScript(node,
                        "grep '" + masterDetection.getGrep() + "' " + masterDetection.getLogFile() , false);

                if (StringUtils.isNotBlank(grepResult)) {

                    Matcher matcher = masterDetection.getTimeStampExtractRexp().matcher(grepResult);
                    if (!matcher.find()) {
                        String error = "Couldn't find pattern " + masterDetection.getTimeStampExtractRexp() + " in " + grepResult;
                        logger.warn(error);
                        throw new MasterDetectionException(error);

                    } else {

                        // skip to last one
                        String timestampString = matcher.group(1);
                        while (matcher.find()) {
                            timestampString = matcher.group(1);
                        }

                        return masterDetection.getTimeStampFormat().parse(timestampString);
                    }
                }

            } catch (SSHCommandException | ParseException e) {
                logger.warn(e.getMessage());
                logger.debug(e, e);
                throw new MasterDetectionException (e);

            }
        }

        return null;
    }

    String sendPing(SSHCommandService sshCommandService, String node) throws SSHCommandException {
        return sshCommandService.runSSHScript(node, "echo OK", false);
    }
}

/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */


package ch.niceideas.eskimo.services.mdstrategy;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.service.MasterDetection;
import ch.niceideas.eskimo.model.service.ServiceDefinition;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.types.Node;
import org.apache.log4j.Logger;

import java.text.ParseException;
import java.util.Date;
import java.util.regex.Matcher;

public class LogFileStrategy implements MdStrategy {

    private static final Logger logger = Logger.getLogger(LogFileStrategy.class);

    @Override
    public Date detectMaster(
            ServiceDefinition serviceDef, Node node, MasterDetection masterDetection,
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

    String sendPing(SSHCommandService sshCommandService, Node node) throws SSHCommandException {
        return sshCommandService.runSSHScript(node, "echo OK", false);
    }
}

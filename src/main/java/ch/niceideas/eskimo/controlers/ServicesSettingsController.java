/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.ServicesSettingsWrapper;
import ch.niceideas.eskimo.model.SettingsOperationsCommand;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.utils.ErrorStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;
import java.util.HashMap;


@Controller
public class ServicesSettingsController {

    private static final Logger logger = Logger.getLogger(ServicesSettingsController.class);

    public static final String PENDING_SETTINGS_OPERATIONS_COMMAND = "PENDING_SETTINGS_OPERATIONS_COMMAND";

    @Autowired
    private ServicesSettingsService servicesSettingsService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SystemService systemService;


    /* For tests */
    void setServicesSettingsService(ServicesSettingsService servicesSettingsService) {
        this.servicesSettingsService = servicesSettingsService;
    }
    void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
    void setSystemService(SystemService systemService) {
        this.systemService = systemService;
    }
    void setMessagingService(MessagingService messagingService) { this.messagingService = messagingService; }
    void setNotificationService (NotificationService notificationService) { this.notificationService = notificationService; }

    @GetMapping("/load-services-settings")
    @ResponseBody
    public String loadServicesSettings() {
        try {

            ServicesSettingsWrapper wrapper = configurationService.loadServicesSettings();

            wrapper.setValueForPath("status", "OK");

            return wrapper.getFormattedValue();
        } catch (JSONException | FileException | SetupException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createErrorStatus(e.getMessage());
        }
    }

    @PostMapping("/save-services-settings")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String prepareSaveServicesSettings(@RequestBody String settingsFormAsString, HttpSession session) {

        logger.info("Got config : " + settingsFormAsString);

        try {

            // first of all check nodes config

            // Create OperationsCommand
            SettingsOperationsCommand command = SettingsOperationsCommand.create(settingsFormAsString, servicesSettingsService);

            // store command and config in HTTP Session
            session.setAttribute(PENDING_SETTINGS_OPERATIONS_COMMAND, command);

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("command", command.toJSON());
            }}).toString(2);

        } catch (JSONException | SetupException | FileException e) {
            logger.error(e, e);
            messagingService.addLines (e.getMessage());
            notificationService.addError("Service Settings Application preparation failed !");
            return ErrorStatusHelper.createEncodedErrorStatus(e);
        }
    }

    @PostMapping("/apply-services-settings")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String saveServicesSettings(HttpSession session) {

        try {

            if (systemService.isProcessingPending()) {
                return new JSONObject(new HashMap<String, Object>() {{
                    put("status", "OK");
                    put("messages", "Some backend operations are currently running. Please retry after they are completed..");
                }}).toString(2);
            }

            SettingsOperationsCommand command = (SettingsOperationsCommand) session.getAttribute(PENDING_SETTINGS_OPERATIONS_COMMAND);
            session.removeAttribute(PENDING_SETTINGS_OPERATIONS_COMMAND);

            servicesSettingsService.applyServicesSettings(command);

            return "{\"status\": \"OK\" }";

        } catch (SystemException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createEncodedErrorStatus(e);

        } catch (JSONException | SetupException | FileException e) {
            logger.error(e, e);
            messagingService.addLines (e.getMessage());
            notificationService.addError("Setting application failed !");
            return ErrorStatusHelper.createEncodedErrorStatus(e);
        }
    }
}

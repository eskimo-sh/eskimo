/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.eskimo.utils.ReturnStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;


@Controller
public class ServicesSettingsController extends AbstractOperationController{

    private static final Logger logger = Logger.getLogger(ServicesSettingsController.class);

    public static final String PENDING_SETTINGS_OPERATIONS_COMMAND = "PENDING_SETTINGS_OPERATIONS_COMMAND";

    @Autowired
    private ServicesSettingsService servicesSettingsService;

    @Autowired
    private ConfigurationService configurationService;

    /* For tests */
    void setServicesSettingsService(ServicesSettingsService servicesSettingsService) {
        this.servicesSettingsService = servicesSettingsService;
    }
    void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping("/load-services-settings")
    @ResponseBody
    public String loadServicesSettings() {
        try {

            ServicesSettingsWrapper wrapper = configurationService.loadServicesSettings();
            wrapper.setValueForPath("status", "OK");

            return wrapper.getFormattedValue();
        } catch (JSONException | FileException | SetupException e) {
            logger.error(e, e);
            return ReturnStatusHelper.createErrorStatus(e.getMessage());
        }
    }

    @PostMapping("/save-services-settings")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public String prepareSaveServicesSettings(@RequestBody String settingsFormAsString, HttpSession session) {

        logger.info("Got config : " + settingsFormAsString);

        try {

            // first of all check nodes config

            // Create OperationsCommand
            SettingsOperationsCommand command = SettingsOperationsCommand.create(settingsFormAsString, servicesSettingsService);

            // store command and config in HTTP Session
            session.setAttribute(PENDING_SETTINGS_OPERATIONS_COMMAND, command);

            return ReturnStatusHelper.createOKStatus(map -> map.put("command", command.toJSON()));

        } catch (SetupException | FileException e) {
            logger.error(e, e);
            notificationService.addError("Service Settings Application preparation failed ! " + e.getMessage());
            return ReturnStatusHelper.createEncodedErrorStatus(e);
        }
    }

    @PostMapping("/apply-services-settings")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public String saveServicesSettings(HttpSession session) {

        try {

            if (operationsMonitoringService.isProcessingPending()) {
                return ReturnStatusHelper.createOKStatus(map ->
                        map.put("messages", "Some backend operations are currently running. Please retry after they are completed.."));
            }

            SettingsOperationsCommand command = (SettingsOperationsCommand) session.getAttribute(PENDING_SETTINGS_OPERATIONS_COMMAND);
            session.removeAttribute(PENDING_SETTINGS_OPERATIONS_COMMAND);

            servicesSettingsService.applyServicesSettings(command);

            return ReturnStatusHelper.createOKStatus();

        } catch (SystemException | SetupException | FileException e) {
            logger.error(e, e);
            notificationService.addError("Setting application failed ! " + e.getMessage());
            return ReturnStatusHelper.createEncodedErrorStatus(e);
        }
    }
}

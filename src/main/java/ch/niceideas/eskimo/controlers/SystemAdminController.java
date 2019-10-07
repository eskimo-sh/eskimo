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
import ch.niceideas.eskimo.model.OperationsCommand;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.HashMap;


@Controller
public class SystemAdminController {

    private static final Logger logger = Logger.getLogger(SystemAdminController.class);

    @Autowired
    private SystemService systemService;

    @Resource
    private MessagingService messagingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @GetMapping("/interupt-processing")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String interuptProcessing() {

        try {
            systemService.interruptProcessing();

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
            }}).toString(2);

        } catch (JSONException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createErrorStatus(e);

        }
    }

    @GetMapping("/show-journal")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String showJournal(@RequestParam(name="service") String service, @RequestParam(name="address") String address) {

        try {
            systemService.showJournal(service, address);

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("messages", service + " journal display from " + address  + ".");
            }}).toString(2);

        } catch (JSONException | SSHCommandException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createErrorStatus(e);

        }
    }

    @GetMapping("/start-service")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String startService(@RequestParam(name="service") String service, @RequestParam(name="address") String address) {

        try {
            systemService.startService(service, address);

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("messages", service + " has been started successfuly on " + address  + ".");
            }}).toString(2);

        } catch (JSONException | SSHCommandException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createErrorStatus(e);

        }
    }

    @GetMapping("/stop-service")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String stopService(@RequestParam(name="service") String service, @RequestParam(name="address") String address) {

        try {

            systemService.stopService(service, address);

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("messages", service + " has been stopped successfuly on " + address  + ".");
            }}).toString(2);

        } catch (JSONException | SSHCommandException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createEncodedErrorStatus(e);
        }
    }

    @GetMapping("/restart-service")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String restartService(@RequestParam(name="service") String service, @RequestParam(name="address") String address) {

        try {
            systemService.restartService(service, address);

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("messages", service + " has been restarted successfuly on " + address  + ".");
            }}).toString(2);

        } catch (JSONException | SSHCommandException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createEncodedErrorStatus(e);

        }
    }

    @GetMapping("/reinstall-service")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String reinstallService(@RequestParam(name="service") String service, @RequestParam(name="address") String address) {

        try {

            if (systemService.isProcessingPending()) {
                return new JSONObject(new HashMap<String, Object>() {{
                    put("status", "OK");
                    put("messages", "Some backend operations are currently running. Please retry after they are completed..");
                }}).toString(2);
            }

            String nodeName = address.replaceAll("\\.", "-");

            ServicesInstallStatusWrapper formerServicesInstallationStatus = systemService.loadServicesInstallationStatus();

            ServicesInstallStatusWrapper newServicesInstallationStatus = ServicesInstallStatusWrapper.empty();

            for (String is : formerServicesInstallationStatus.getAllInstallStatusesExceptServiceOnNode(service, nodeName)) {
                newServicesInstallationStatus.setValueForPath(is, formerServicesInstallationStatus.getValueForPath(is));
            }

            systemService.saveServicesInstallationStatus(newServicesInstallationStatus);

            NodesConfigWrapper nodesConfig = systemService.loadNodesConfig();
            if (nodesConfig == null || nodesConfig.isEmpty()) {
                return ErrorStatusHelper.createClearStatus("missing", systemService.isProcessingPending());
            }

            OperationsCommand operationsCommand = OperationsCommand.create (
                    servicesDefinition, nodeRangeResolver, newServicesInstallationStatus, nodesConfig);

            systemService.applyNodesConfig (operationsCommand);

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("messages", service + " has been reinstalled successfuly on " + address  + ".");
            }}).toString(2);

        } catch (SetupException | SystemException | FileException | JSONException | NodesConfigurationException | ServiceDefinitionException e) {
            logger.error(e, e);
            messagingService.addLines (e.getMessage());
            notificationService.addEvent("error", "Nodes installation failed !");
            return ErrorStatusHelper.createEncodedErrorStatus(e);
        }
    }

}
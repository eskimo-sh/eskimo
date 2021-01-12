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
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.utils.ReturnStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class SystemAdminController extends AbstractOperationController {

    private static final Logger logger = Logger.getLogger(SystemAdminController.class);

    @Autowired
    private MarathonService marathonService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    /* for tests */
    void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    void setNodeRangeResolver(NodeRangeResolver nodeRangeResolver) {
        this.nodeRangeResolver = nodeRangeResolver;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping("/interupt-processing")
    @ResponseBody
    public String interruptProcessing() {

        try {
            systemService.interruptProcessing();

            return ReturnStatusHelper.createOKStatus();

        } catch (JSONException e) {
            logger.error(e, e);
            return ReturnStatusHelper.createErrorStatus(e);

        }
    }

    private String performMarathonOperation(MarathonOperation operation, String message) {

        try {
            operation.performOperation(marathonService);

            return ReturnStatusHelper.createOKStatus(map -> map.put("messages", message));

        } catch (SSHCommandException | MarathonException e) {
            logger.error(e, e);
            return ReturnStatusHelper.createErrorStatus(e);
        }
    }

    private String performSystemOperation(SystemOperation operation, String message) {

        try {
            operation.performOperation(systemService);

            return ReturnStatusHelper.createOKStatus(map -> map.put("messages", message));

        } catch (SSHCommandException | NodesConfigurationException | ServiceDefinitionException | SystemException | MarathonException e) {
            logger.error(e, e);
            return ReturnStatusHelper.createErrorStatus(e);
        }
    }

    @GetMapping("/show-journal")
    @ResponseBody
    public String showJournal(@RequestParam(name="service") String serviceName, @RequestParam(name="address") String address) {
        Service service = servicesDefinition.getService(serviceName);
        if (service.isMarathon()) {
            return performMarathonOperation(
                    marathonService -> marathonService.showJournalMarathon(service),
                    "Successfully shown journal of " +  serviceName + ".");

        } else {
            return performSystemOperation(
                    sysService -> sysService.showJournal(serviceName, address),
                    serviceName + " journal display from " + address + ".");
        }

    }

    @GetMapping("/start-service")
    @ResponseBody
    public String startService(@RequestParam(name="service") String serviceName, @RequestParam(name="address") String address) {
        Service service = servicesDefinition.getService(serviceName);
        if (service.isMarathon()) {
            return performMarathonOperation(
                    marathonService -> marathonService.startServiceMarathon(service),
                    serviceName + " has been started successfuly on marathon.");

        } else {
            return performSystemOperation(
                    sysService -> sysService.startService(serviceName, address),
                    serviceName + " has been started successfuly on " + address + ".");
        }
    }

    @GetMapping("/stop-service")
    @ResponseBody
    public String stopService(@RequestParam(name="service") String serviceName, @RequestParam(name="address") String address) {
        Service service = servicesDefinition.getService(serviceName);
        if (service.isMarathon()) {
            return performMarathonOperation(
                    marathonService -> marathonService.stopServiceMarathon(service),
                    serviceName + " has been stopped successfuly on marathon.");

        } else {
            return performSystemOperation(
                    sysService -> sysService.stopService(serviceName, address),
                    serviceName + " has been stopped successfuly on " + address + ".");
        }
    }

    @GetMapping("/restart-service")
    @ResponseBody
    public String restartService(@RequestParam(name="service") String serviceName, @RequestParam(name="address") String address) {

        Service service = servicesDefinition.getService(serviceName);
        if (service.isMarathon()) {
            return performMarathonOperation(
                    marathonService -> marathonService.restartServiceMarathon(service),
                    serviceName + " has been restarted successfuly on marathon.");

        } else {
            return performSystemOperation(
                    sysService -> sysService.restartService(serviceName, address),
                    serviceName + " has been restarted successfuly on " + address + ".");
        }
    }

    @GetMapping("service-custom-action")
    @ResponseBody
    public String serviceActionCustom(
            @RequestParam(name="action") String commandId,
            @RequestParam(name="service") String serviceName,
            @RequestParam(name="address") String address) {
        return performSystemOperation(
                sysService -> sysService.callCommand(commandId, serviceName, address),
                "command " + commandId + " for " + serviceName + " has been executed successfuly on " + address + ".");
    }

    @GetMapping("/reinstall-service")
    @ResponseBody
    public String reinstallService(@RequestParam(name="service") String serviceName, @RequestParam(name="address") String address) {

        try {

            JSONObject checkObject = checkOperations("Unfortunately, re-installing a service is not possible in DEMO mode.");
            if (checkObject != null) {
                return checkObject.toString(2);
            }

            Service service = servicesDefinition.getService(serviceName);

            String nodeName;
            if (service.isMarathon()) {
                nodeName = ServicesInstallStatusWrapper.MARATHON_NODE;
            } else {
                nodeName = address.replaceAll("\\.", "-");
            }

            ServicesInstallStatusWrapper formerServicesInstallationStatus = configurationService.loadServicesInstallationStatus();

            ServicesInstallStatusWrapper newServicesInstallationStatus = ServicesInstallStatusWrapper.empty();

            for (String is : formerServicesInstallationStatus.getAllInstallStatusesExceptServiceOnNode(serviceName, nodeName)) {
                newServicesInstallationStatus.setValueForPath(is, formerServicesInstallationStatus.getValueForPath(is));
            }

            configurationService.saveServicesInstallationStatus(newServicesInstallationStatus);

            if (service.isMarathon()) {

                MarathonServicesConfigWrapper marathonServicesConfig = configurationService.loadMarathonServicesConfig();
                if (marathonServicesConfig == null || marathonServicesConfig.isEmpty()) {
                    return ReturnStatusHelper.createClearStatus("missing", systemService.isProcessingPending());
                }

                MarathonOperationsCommand operationsCommand = MarathonOperationsCommand.create(
                        servicesDefinition, systemService, newServicesInstallationStatus, marathonServicesConfig);

                return performMarathonOperation(
                        marathonService -> marathonService.applyMarathonServicesConfig(operationsCommand),
                        serviceName + " has been reinstalled successfuly on marathon.");

            } else {

                NodesConfigWrapper nodesConfig = configurationService.loadNodesConfig();
                if (nodesConfig == null || nodesConfig.isEmpty()) {
                    return ReturnStatusHelper.createClearStatus("missing", systemService.isProcessingPending());
                }

                OperationsCommand operationsCommand = OperationsCommand.create(
                        servicesDefinition, nodeRangeResolver, newServicesInstallationStatus, nodesConfig);

                return performSystemOperation(
                        sysService -> sysService.delegateApplyNodesConfig(operationsCommand),
                        serviceName + " has been reinstalled successfuly on " + address + ".");
            }
        } catch (SetupException | SystemException | FileException | JSONException | NodesConfigurationException e) {
            logger.error(e, e);
            messagingService.addLines (e.getMessage());
            notificationService.addError("Nodes installation failed !");
            return ReturnStatusHelper.createErrorStatus(e);
        }
    }

    private interface SystemOperation {
        void performOperation (SystemService systemService) throws SSHCommandException, NodesConfigurationException, ServiceDefinitionException, SystemException, MarathonException;
    }

    private interface MarathonOperation {
        void performOperation (MarathonService marathonService) throws SSHCommandException, MarathonException;
    }

}
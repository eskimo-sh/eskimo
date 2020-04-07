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
import ch.niceideas.eskimo.model.*;
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

    @Autowired
    private MarathonService marathonService;

    @Resource
    private MessagingService messagingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    /* for tests */
    void setSystemService(SystemService systemService) {
        this.systemService = systemService;
    }
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
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String interruptProcessing() {

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

    private String performMarathonOperation(MarathonOperation operation, String message) {

        try {
            operation.performOperation(marathonService);

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("messages", message);
            }}).toString(2);

        } catch (JSONException | ServiceDefinitionException | SSHCommandException | SystemException | MarathonException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createErrorStatus(e);

        }
    }

    private String performSystemOperation(SystemOperation operation, String message) {

        try {
            operation.performOperation(systemService);

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("messages", message);
            }}).toString(2);

        } catch (JSONException | SSHCommandException | NodesConfigurationException | ServiceDefinitionException | SystemException | MarathonException e) {
            logger.error(e, e);
            return ErrorStatusHelper.createErrorStatus(e);

        }
    }

    @GetMapping("/show-journal")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String showJournal(@RequestParam(name="service") String serviceName, @RequestParam(name="address") String address) {
        Service service = servicesDefinition.getService(serviceName);
        if (service.isMarathon()) {
            return performMarathonOperation(
                    marathonService -> marathonService.showJournalMarathon(service),
                    serviceName + " has been restarted successfuly on marathon.");

        } else {
            return performSystemOperation(
                    sysService -> sysService.showJournal(serviceName, address),
                    serviceName + " journal display from " + address + ".");
        }

    }

    @GetMapping("/start-service")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String startService(@RequestParam(name="service") String serviceName, @RequestParam(name="address") String address) {
        Service service = servicesDefinition.getService(serviceName);
        if (service.isMarathon()) {
            return performMarathonOperation(
                    marathonService -> marathonService.startServiceMarathon(service),
                    serviceName + " has been restarted successfuly on marathon.");

        } else {
            return performSystemOperation(
                    sysService -> sysService.startService(serviceName, address),
                    serviceName + " has been started successfuly on " + address + ".");
        }
    }

    @GetMapping("/stop-service")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String stopService(@RequestParam(name="service") String serviceName, @RequestParam(name="address") String address) {
        Service service = servicesDefinition.getService(serviceName);
        if (service.isMarathon()) {
            return performMarathonOperation(
                    marathonService -> marathonService.stopServiceMarathon(service),
                    serviceName + " has been restarted successfuly on marathon.");

        } else {
            return performSystemOperation(
                    sysService -> sysService.stopService(serviceName, address),
                    serviceName + " has been stopped successfuly on " + address + ".");
        }
    }

    @GetMapping("/restart-service")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
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

    @GetMapping("/reinstall-service")
    @Transactional(isolation= Isolation.REPEATABLE_READ)
    @ResponseBody
    public String reinstallService(@RequestParam(name="service") String serviceName, @RequestParam(name="address") String address) {

        try {

            if (systemService.isProcessingPending()) {
                return new JSONObject(new HashMap<String, Object>() {{
                    put("status", "OK");
                    put("messages", "Some backend operations are currently running. Please retry after they are completed..");
                }}).toString(2);
            }

            Service service = servicesDefinition.getService(serviceName);

            String nodeName = null;
            if (service.isMarathon()) {
                nodeName = MarathonService.MARATHON_NODE;
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
                    return ErrorStatusHelper.createClearStatus("missing", systemService.isProcessingPending());
                }

                MarathonOperationsCommand operationsCommand = MarathonOperationsCommand.create(
                        servicesDefinition, newServicesInstallationStatus, marathonServicesConfig);

                return performMarathonOperation(
                        marathonService -> marathonService.applyMarathonServicesConfig(operationsCommand),
                        serviceName + " has been reinstalled successfuly on marathon.");

            } else {

                NodesConfigWrapper nodesConfig = configurationService.loadNodesConfig();
                if (nodesConfig == null || nodesConfig.isEmpty()) {
                    return ErrorStatusHelper.createClearStatus("missing", systemService.isProcessingPending());
                }

                OperationsCommand operationsCommand = OperationsCommand.create(
                        servicesDefinition, nodeRangeResolver, newServicesInstallationStatus, nodesConfig);

                return performSystemOperation(
                        sysService -> sysService.applyNodesConfig(operationsCommand),
                        serviceName + " has been reinstalled successfuly on " + address + ".");
            }
        } catch (SetupException | SystemException | MarathonException | FileException | JSONException | NodesConfigurationException e) {
            logger.error(e, e);
            messagingService.addLines (e.getMessage());
            notificationService.addError("Nodes installation failed !");
            return ErrorStatusHelper.createErrorStatus(e);
        }
    }

    private interface SystemOperation {
        void performOperation (SystemService systemService) throws SSHCommandException, NodesConfigurationException, ServiceDefinitionException, SystemException, MarathonException;
    }

    private interface MarathonOperation {
        void performOperation (MarathonService marathonService) throws ServiceDefinitionException, SSHCommandException, SystemException, MarathonException;
    }

}
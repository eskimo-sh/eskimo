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
import org.springframework.security.access.prepost.PreAuthorize;
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
    private KubernetesService kubernetesService;

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
    @PreAuthorize("hasAuthority('ADMIN')")
    public String interruptProcessing() {

        try {
            operationsMonitoringService.interruptProcessing();

            return ReturnStatusHelper.createOKStatus();

        } catch (JSONException e) {
            logger.error(e, e);
            return ReturnStatusHelper.createErrorStatus(e);

        }
    }

    private String performKubernetesOperation(KubernetesOperation operation, String message) {

        try {
            operation.performOperation(kubernetesService);

            return ReturnStatusHelper.createOKStatus(map -> map.put("messages", message));

        } catch (KubernetesException | SystemException e) {
            logger.error(e, e);
            return ReturnStatusHelper.createErrorStatus(e);
        }
    }

    @Deprecated
    private String performMarathonOperation(MarathonOperation operation, String message) {

        try {
            operation.performOperation(marathonService);

            return ReturnStatusHelper.createOKStatus(map -> map.put("messages", message));

        } catch (MarathonException | SystemException e) {
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
    public String showJournal(@RequestParam(name="service") String serviceName, @RequestParam(name="nodeAddress") String node) {
        Service service = servicesDefinition.getService(serviceName);

        if (service.isKubernetes()) {
            return performKubernetesOperation(
                    marathonService -> marathonService.showJournal(service),
                    "Successfully shown journal of " +  serviceName + ".");

        } else {
            return performSystemOperation(
                    sysService -> sysService.showJournal(serviceName, node),
                    serviceName + " journal display from " + node + ".");
        }

    }

    @GetMapping("/start-service")
    @ResponseBody
    public String startService(@RequestParam(name="service") String serviceName, @RequestParam(name="nodeAddress") String node) {
        Service service = servicesDefinition.getService(serviceName);
        if (service.isKubernetes()) {
            return performKubernetesOperation(
                    kubernetesService -> kubernetesService.startService(service),
                    serviceName + " has been started successfuly on kubernetes.");

        } else {
            return performSystemOperation(
                    sysService -> sysService.startService(serviceName, node),
                    serviceName + " has been started successfuly on " + node + ".");
        }
    }

    @GetMapping("/stop-service")
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    public String stopService(@RequestParam(name="service") String serviceName, @RequestParam(name="nodeAddress") String node) {
        Service service = servicesDefinition.getService(serviceName);
        if (service.isKubernetes()) {
            return performKubernetesOperation(
                    kubernetesService -> kubernetesService.stopService(service),
                    serviceName + " has been stopped successfuly on kubernetes.");

        } else {
            return performSystemOperation(
                    sysService -> sysService.stopService(serviceName, node),
                    serviceName + " has been stopped successfuly on " + node + ".");
        }
    }

    @GetMapping("/restart-service")
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    public String restartService(@RequestParam(name="service") String serviceName, @RequestParam(name="nodeAddress") String node) {

        Service service = servicesDefinition.getService(serviceName);
        if (service.isKubernetes()) {
            return performKubernetesOperation(
                    kubernetesService -> kubernetesService.restartService(service),
                    serviceName + " has been restarted successfuly on kubernetes.");

        } else {
            return performSystemOperation(
                    sysService -> sysService.restartService(serviceName, node),
                    serviceName + " has been restarted successfuly on " + node + ".");
        }
    }

    @GetMapping("service-custom-action")
    @ResponseBody
    public String serviceActionCustom(
            @RequestParam(name="action") String commandId,
            @RequestParam(name="service") String serviceName,
            @RequestParam(name="nodeAddress") String node) {
        return performSystemOperation(
                sysService -> sysService.callCommand(commandId, serviceName, node),
                "command " + commandId + " for " + serviceName + " has been executed successfuly on " + node + ".");
    }

    @GetMapping("/reinstall-service")
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    public String reinstallService(@RequestParam(name="service") String serviceName, @RequestParam(name="nodeAddress") String node) {

        try {

            JSONObject checkObject = checkOperations("Unfortunately, re-installing a service is not possible in DEMO mode.");
            if (checkObject != null) {
                return checkObject.toString(2);
            }

            Service service = servicesDefinition.getService(serviceName);

            String nodeName;
            if (service.isKubernetes()) {
                nodeName = ServicesInstallStatusWrapper.KUBERNETES_NODE;
            } else {
                nodeName = node.replace(".", "-");
            }

            ServicesInstallStatusWrapper formerServicesInstallationStatus = configurationService.loadServicesInstallationStatus();

            ServicesInstallStatusWrapper newServicesInstallationStatus = ServicesInstallStatusWrapper.empty();

            for (String is : formerServicesInstallationStatus.getAllInstallStatusesExceptServiceOnNode(serviceName, nodeName)) {
                newServicesInstallationStatus.setValueForPath(is, formerServicesInstallationStatus.getValueForPath(is));
            }

            configurationService.saveServicesInstallationStatus(newServicesInstallationStatus);

            if (service.isKubernetes()) {

                KubernetesServicesConfigWrapper kubeServicesConfig = configurationService.loadKubernetesServicesConfig();
                if (kubeServicesConfig == null || kubeServicesConfig.isEmpty()) {
                    return ReturnStatusHelper.createClearStatus("missing", operationsMonitoringService.isProcessingPending());
                }

                KubernetesOperationsCommand operationsCommand = KubernetesOperationsCommand.create(
                        servicesDefinition, systemService, newServicesInstallationStatus, kubeServicesConfig);

                return performKubernetesOperation(
                        kubernetesService -> kubernetesService.applyKubernetesServicesConfig(operationsCommand),
                        serviceName + " has been reinstalled successfully on kubernetes.");

            } else {

                NodesConfigWrapper nodesConfig = configurationService.loadNodesConfig();
                if (nodesConfig == null || nodesConfig.isEmpty()) {
                    return ReturnStatusHelper.createClearStatus("missing", operationsMonitoringService.isProcessingPending());
                }

                ServiceOperationsCommand operationsCommand = ServiceOperationsCommand.create(
                        servicesDefinition, nodeRangeResolver, newServicesInstallationStatus, nodesConfig);

                return performSystemOperation(
                        sysService -> sysService.delegateApplyNodesConfig(operationsCommand),
                        serviceName + " has been reinstalled successfuly on " + node + ".");
            }
        } catch (SetupException | SystemException | FileException | JSONException | NodesConfigurationException e) {
            logger.error(e, e);
            notificationService.addError("Nodes installation failed !");
            return ReturnStatusHelper.createErrorStatus(e);
        }
    }

    private interface SystemOperation {
        void performOperation (SystemService systemService) throws SSHCommandException, NodesConfigurationException, ServiceDefinitionException, SystemException, MarathonException;
    }

    @Deprecated
    private interface MarathonOperation {
        void performOperation (MarathonService marathonService) throws MarathonException, SystemException;
    }

    private interface KubernetesOperation {
        void performOperation (KubernetesService kubernetesService) throws KubernetesException, SystemException;
    }
}
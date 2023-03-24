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

package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.KubernetesOperationsCommand;
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.services.satellite.KubernetesServicesConfigChecker;
import ch.niceideas.eskimo.services.satellite.KubernetesServicesConfigException;
import ch.niceideas.eskimo.utils.ReturnStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;


@Controller
public class KubernetesServicesConfigController extends AbstractOperationController {

    private static final Logger logger = Logger.getLogger(KubernetesServicesConfigController.class);

    public static final String PENDING_KUBERNETES_OPERATIONS_STATUS_OVERRIDE = "PENDING_KUBERNETES_OPERATIONS_STATUS_OVERRIDE";
    public static final String PENDING_KUBERNETES_OPERATIONS_COMMAND = "PENDING_KUBERNETES_OPERATIONS_COMMAND";
    public static final String NOKUBERNETES = "nokubernetes";

    @Autowired
    private KubernetesService kubernetesService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private SetupService setupService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private KubernetesServicesConfigChecker kubernetesServicesConfigChecker;

    @Value("${eskimo.enableKubernetesSubsystem}")
    protected String enableKubernetes = "true";

    @GetMapping("/load-kubernetes-services-config")
    @ResponseBody
    public String loadKubernetesServicesConfig() {

        if (StringUtils.isBlank(enableKubernetes) || !enableKubernetes.equals("true")) {
            return ReturnStatusHelper.createClearStatus(NOKUBERNETES, operationsMonitoringService.isProcessingPending());
        }

        try {
            setupService.ensureSetupCompleted();
            KubernetesServicesConfigWrapper kubeServicesConfig = configurationService.loadKubernetesServicesConfig();
            if (kubeServicesConfig == null || kubeServicesConfig.isEmpty()) {
                return ReturnStatusHelper.createClearStatus("missing", operationsMonitoringService.isProcessingPending());
            }
            return kubeServicesConfig.getFormattedValue();

        } catch (SystemException | JSONException e) {
            logger.error(e, e);
            return ReturnStatusHelper.createErrorStatus(e.getMessage());

        } catch (SetupException e) {
            // this is OK. means application is not yet initialized
            logger.debug (e, e);
            return ReturnStatusHelper.createClearStatus("setup", operationsMonitoringService.isProcessingPending());
        }
    }

    @PostMapping("/save-kubernetes-services-config")
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    public String saveKubernetesServicesConfig(@RequestBody String configAsString, HttpSession session) {

        logger.info ("Got config : " + configAsString);

        if (StringUtils.isBlank(enableKubernetes) || !enableKubernetes.equals("true")) {
            return ReturnStatusHelper.createClearStatus(NOKUBERNETES, operationsMonitoringService.isProcessingPending());
        }

        try {

            // first of all check nodes config
            KubernetesServicesConfigWrapper kubeServicesConfig = new KubernetesServicesConfigWrapper(configAsString);
            kubernetesServicesConfigChecker.checkKubernetesServicesSetup(kubeServicesConfig);

            ServicesInstallStatusWrapper serviceInstallStatus = configurationService.loadServicesInstallationStatus();

            // Create OperationsCommand
            KubernetesOperationsCommand command = KubernetesOperationsCommand.create(
                    servicesDefinition, systemService,
                    configurationService.loadKubernetesServicesConfig(),
                    serviceInstallStatus, kubeServicesConfig);

            // store command and config in HTTP Session
            session.setAttribute(PENDING_KUBERNETES_OPERATIONS_COMMAND, command);

            return returnCommand (command);

        } catch (JSONException | SetupException | FileException | KubernetesServicesConfigException | SystemException e) {
            logger.error(e, e);
            notificationService.addError("Kubernetes Services installation preparation failed !");
            return ReturnStatusHelper.createEncodedErrorStatus(e);
        }
    }

    @PostMapping("/reinstall-kubernetes-services-config")
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    public String reinstallKubernetesServiceConfig(@RequestBody String reinstallModel, HttpSession session) {

        logger.info ("Got model : " + reinstallModel);

        if (StringUtils.isBlank(enableKubernetes) || !enableKubernetes.equals("true")) {
            return ReturnStatusHelper.createClearStatus(NOKUBERNETES, operationsMonitoringService.isProcessingPending());
        }

        try {

            ServicesInstallStatusWrapper servicesInstallStatus = configurationService.loadServicesInstallationStatus();
            ServicesInstallStatusWrapper newServicesInstallStatus = ServicesInstallStatusWrapper.empty();

            KubernetesServicesConfigWrapper reinstallModelConfig = new KubernetesServicesConfigWrapper(reinstallModel);

            for (String serviceInstallStatusFlag : servicesInstallStatus.getInstalledServicesFlags()) {
                if (!reinstallModelConfig.isServiceInstallRequired(servicesInstallStatus.getService(serviceInstallStatusFlag))) {
                    newServicesInstallStatus.copyFrom (serviceInstallStatusFlag, servicesInstallStatus);
                }
            }

            KubernetesServicesConfigWrapper kubeServicesConfig = configurationService.loadKubernetesServicesConfig();
            if (kubeServicesConfig == null || kubeServicesConfig.isEmpty()) {
                return ReturnStatusHelper.createClearStatus("missing", operationsMonitoringService.isProcessingPending());
            }

            // Create OperationsCommand
            KubernetesOperationsCommand command = KubernetesOperationsCommand.create(
                    servicesDefinition, systemService,
                    configurationService.loadKubernetesServicesConfig(),
                    newServicesInstallStatus, kubeServicesConfig);

            // store command and config in HTTP Session
            session.setAttribute(PENDING_KUBERNETES_OPERATIONS_STATUS_OVERRIDE, newServicesInstallStatus);
            session.setAttribute(PENDING_KUBERNETES_OPERATIONS_COMMAND, command);

            return returnCommand (command);

        } catch (JSONException | FileException | SetupException | SystemException e) {
            logger.error(e, e);
            return ReturnStatusHelper.createEncodedErrorStatus(e);
        }
    }

    @PostMapping("/apply-kubernetes-services-config")
    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    public String applyKubernetesServicesConfig(HttpSession session) {

        if (StringUtils.isBlank(enableKubernetes) || !enableKubernetes.equals("true")) {
            return ReturnStatusHelper.createClearStatus(NOKUBERNETES, operationsMonitoringService.isProcessingPending());
        }

        try {

            JSONObject checkObject = checkOperations("Unfortunately, re-applying kubernetes configuration or changing kubernetes configuration is not possible in DEMO mode.");
            if (checkObject != null) {
                return checkObject.toString(2);
            }

            KubernetesOperationsCommand command = (KubernetesOperationsCommand) session.getAttribute(PENDING_KUBERNETES_OPERATIONS_COMMAND);
            session.removeAttribute(PENDING_KUBERNETES_OPERATIONS_COMMAND);

            ServicesInstallStatusWrapper newServicesInstallationStatus = (ServicesInstallStatusWrapper) session.getAttribute(PENDING_KUBERNETES_OPERATIONS_STATUS_OVERRIDE);
            session.removeAttribute(PENDING_KUBERNETES_OPERATIONS_STATUS_OVERRIDE);

            // newNodesStatus is null in case of kubernetes services config update (as opposed to forced reinstall)
            if (newServicesInstallationStatus != null) {
                configurationService.saveServicesInstallationStatus(newServicesInstallationStatus);
            }

            configurationService.saveKubernetesServicesConfig(command.getRawConfig());

            kubernetesService.applyServicesConfig(command);

            return ReturnStatusHelper.createOKStatus();

        } catch (KubernetesException e) {
            logger.error(e, e);
            return ReturnStatusHelper.createEncodedErrorStatus(e);

        } catch (JSONException | SetupException | FileException e) {
            logger.error(e, e);
            notificationService.addError("Kubernetes Services installation failed !");
            return ReturnStatusHelper.createEncodedErrorStatus(e);
        }
    }

}

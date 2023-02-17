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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.MemoryModel;
import ch.niceideas.eskimo.services.satellite.MemoryComputer;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.services.satellite.ServicesInstallationSorter;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Profile("!test-services-settings")
public class ServicesSettingsServiceImpl implements ServicesSettingsService {

    private static final Logger logger = Logger.getLogger(ServicesSettingsServiceImpl.class);

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Autowired
    private ServicesInstallationSorter servicesInstallationSorter;

    @Autowired
    private SystemService systemService;

    @Autowired
    private MemoryComputer memoryComputer;

    @Autowired
    private SystemOperationService systemOperationService;

    @Autowired
    private NodesConfigurationService nodesConfigurationService;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Value("${system.operationWaitTimoutSeconds}")
    private int operationWaitTimoutSeconds = 800; // ~ 13 minutes (for an individual step)

    @Value("${system.parallelismInstallThreadCount}")
    private int parallelismInstallThreadCount = 10;

    @Value("${system.baseInstallWaitTimoutSeconds}")
    private int baseInstallWaitTimout = 1000;

    private final ReentrantLock servicesSettingsApplyLock = new ReentrantLock();

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public void applyServicesSettings(SettingsOperationsCommand command) throws FileException, SetupException, SystemException  {

        servicesSettingsApplyLock.lock();
        try {

            ServicesSettingsWrapper servicesSettings = command.getNewSettings();

            List<Service> dirtyServices = command.getRestartedServices();

            configurationService.saveServicesSettings(servicesSettings);

            NodesConfigWrapper rawNodesConfig = configurationService.loadNodesConfig();

            if (dirtyServices != null && !dirtyServices.isEmpty() && rawNodesConfig != null && !rawNodesConfig.isEmpty()) {

                NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);

                KubernetesServicesConfigWrapper kubeServicesConfig = configurationService.loadKubernetesServicesConfig();
                ServicesInstallStatusWrapper servicesInstallStatus = configurationService.loadServicesInstallationStatus();

                ServiceRestartOperationsCommand restartCommand = ServiceRestartOperationsCommand.create(
                        servicesDefinition, nodesConfig, dirtyServices);

                boolean success = false;
                operationsMonitoringService.startCommand(restartCommand);
                try {

                    Set<Node> liveNodes = new HashSet<>();
                    Set<Node> deadNodes = new HashSet<>();

                    List<Pair<String, Node>> nodeSetupPairs = systemService.discoverAliveAndDeadNodes(
                            restartCommand.getAllNodes(),
                            nodesConfig,
                            liveNodes, deadNodes);
                    if (nodeSetupPairs == null) {
                        return;
                    }

                    List<ServiceOperationsCommand.ServiceOperationId> nodesSetup =
                            nodeSetupPairs.stream()
                                    .map(nodeSetupPair -> new ServiceOperationsCommand.ServiceOperationId(
                                            ServiceOperationsCommand.ServiceOperation.CHECK_INSTALL,
                                            Service.SETTINGS,
                                            nodeSetupPair.getValue()))
                                    .collect(Collectors.toList());

                    MemoryModel memoryModel = memoryComputer.buildMemoryModel(nodesConfig, kubeServicesConfig, deadNodes);

                    // Nodes setup
                    systemService.performPooledOperation (nodesSetup, parallelismInstallThreadCount, baseInstallWaitTimout,
                            (operation, error) -> {
                                Node node = operation.getNode();
                                if (nodesConfig.getAllNodes().contains(node) && liveNodes.contains(node)) {
                                    systemOperationService.applySystemOperation(
                                            operation,
                                            ml -> {
                                                // topology
                                                if (!operationsMonitoringService.isInterrupted() && (error.get() == null)) {
                                                    operationsMonitoringService.addInfo(operation, "Installing Topology and settings");
                                                    nodesConfigurationService.installTopologyAndSettings(
                                                            ml, nodesConfig, kubeServicesConfig, servicesInstallStatus, memoryModel, node);
                                                }
                                            }, null);
                                }
                            });

                    // restarts
                    for (List<ServiceOperationsCommand.ServiceOperationId> restarts : servicesInstallationSorter.orderOperations (
                            restartCommand.getRestarts(), nodesConfig)) {
                        systemService.performPooledOperation(restarts, 1, operationWaitTimoutSeconds,
                                (operation, error) -> {
                                    if (operation.getNode().equals(Node.KUBERNETES_FLAG) || liveNodes.contains(operation.getNode())) {
                                        nodesConfigurationService.restartServiceForSystem(operation);
                                    }
                                });
                    }

                    success = true;

                } finally {
                    operationsMonitoringService.endCommand(success);
                    logger.info ("System Deployment Operations Completed.");
                }
            }

        } catch (SystemException | NodesConfigurationException | ServiceDefinitionException e) {
            logger.error (e, e);
            throw new SystemException(e);

        } finally {
            servicesSettingsApplyLock.unlock();
        }
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public ServicesSettingsWrapper prepareSaveSettings (
            String settingsFormAsString,
            Map<Service, Map<String, List<SettingsOperationsCommand.ChangedSettings>>> changedSettings,
            List<Service> restartedServices) throws FileException, SetupException  {

        ServicesSettingsWrapper servicesSettings = configurationService.loadServicesSettings();

        Service[] dirtyServices = fillInEditedConfigs(changedSettings, new JSONObject(settingsFormAsString), servicesSettings.getSubJSONArray("settings"));

        restartedServices.addAll(Arrays.asList(dirtyServices));

        return servicesSettings;
    }

    protected Service[] fillInEditedConfigs(Map<Service, Map<String, List<SettingsOperationsCommand.ChangedSettings>>> changedSettings,
                                 JSONObject settingsForm, JSONArray configArrayForService) {

        Set<Service> dirtyServices = new HashSet<>();

        // apply changes, proceed service by service
        for (Service service : servicesDefinition.listAllServices()) {

            // get all properties for service
            for (String settingsKey : settingsForm.keySet()) {

                if (settingsKey.startsWith(service.getName())) {

                    String value = settingsForm.getString(settingsKey);

                    String propertyKey = settingsKey.substring(service.getName().length() + 1).replace("---", ".");

                    // now iterate through saved (existing) configs and update values
                    main:
                    for (int i = 0; i < configArrayForService.length(); i++) {
                        JSONObject object = configArrayForService.getJSONObject(i);
                        String serviceName = object.getString("name");
                        if (serviceName.equals(service.getName())) {

                            // iterate through all editableConfiguration
                            JSONArray editableConfigurations = object.getJSONArray("settings");
                            for (int j = 0; j < editableConfigurations.length(); j++) {
                                JSONObject editableConfiguration = editableConfigurations.getJSONObject(j);

                                String filename = editableConfiguration.getString("filename");
                                JSONArray properties = editableConfiguration.getJSONArray("properties");
                                for (int k = 0; k < properties.length(); k++) {

                                    JSONObject property = properties.getJSONObject(k);

                                    String propertyName = property.getString("name");
                                    if (propertyName.equals(propertyKey)) {

                                        handleProperty(changedSettings, dirtyServices, service, value, filename, property, propertyName);
                                        break main;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return dirtyServices.toArray(new Service[0]);
    }

    private void handleProperty(
            Map<Service, Map<String, List<SettingsOperationsCommand.ChangedSettings>>> changedSettings,
            Set<Service> dirtyServices,
            Service service,
            String value,
            String filename,
            JSONObject property,
            String propertyName) {
        String defaultValue = property.getString("defaultValue");
        String previousValue = property.has("value") ? property.getString("value") : null;

        // Handle service dirtiness
        if (   (StringUtils.isBlank(previousValue) && StringUtils.isNotBlank(value))
            || (StringUtils.isNotBlank(previousValue) && StringUtils.isBlank(value))
            || (StringUtils.isNotBlank(previousValue) && StringUtils.isNotBlank(value) && !previousValue.equals(value)
                )) {

            dirtyServices.add(service);

            Map<String, List<SettingsOperationsCommand.ChangedSettings>> changedSettingsforService =
                    changedSettings.computeIfAbsent(service, ser -> new HashMap<>());

            List<SettingsOperationsCommand.ChangedSettings> changeSettingsForFile =
                    changedSettingsforService.computeIfAbsent(filename, fn -> new ArrayList<>());

            changeSettingsForFile.add(new SettingsOperationsCommand.ChangedSettings (
                    service, filename, propertyName, value, previousValue));
        }

        // Handle value saving
        if (StringUtils.isBlank(value) || value.equals(defaultValue)) {
            property.remove("value");
        } else {
            property.put("value", value);
        }
    }

    private static class ServiceRestartOperationsCommand extends JSONInstallOpCommand<ServiceOperationsCommand.ServiceOperationId> {

        public static ServiceRestartOperationsCommand create (
                ServicesDefinition servicesDefinition,
                NodesConfigWrapper nodesConfig,
                List<Service> dirtyServices) {
            return new ServiceRestartOperationsCommand(servicesDefinition, nodesConfig, dirtyServices);
        }

        public ServiceRestartOperationsCommand (
                ServicesDefinition servicesDefinition,
                NodesConfigWrapper nodesConfig,
                List<Service> dirtyServices) {
            dirtyServices.stream()
                    .map (servicesDefinition::getServiceDefinition)
                    .forEach(serviceDef -> {
                        if (serviceDef.isKubernetes()) {
                            addRestart(new ServiceOperationsCommand.ServiceOperationId(ServiceOperationsCommand.ServiceOperation.RESTART, serviceDef.toService(), Node.KUBERNETES_FLAG));
                        } else {
                            nodesConfig.getNodeNumbers(serviceDef).stream()
                                    .map (nodesConfig::getNode)
                                    .forEach(node -> addRestart(new ServiceOperationsCommand.ServiceOperationId(ServiceOperationsCommand.ServiceOperation.RESTART, serviceDef.toService(), node)));
                        }
                    });
            //
        }

        @Override
        public JSONObject toJSON () {
            return new JSONObject(new HashMap<String, Object>() {{
                put("restarts", new JSONArray(toJsonList(getRestarts())));
            }});
        }

        public Set<Node> getAllNodes() {
            Set<Node> retSet = new HashSet<>();
            getRestarts().stream()
                    .map(ServiceOperationsCommand.ServiceOperationId::getNode)
                    .forEach(retSet::add);

            // this one can come from restartes flags
            retSet.remove(Node.KUBERNETES_FLAG);

            return retSet;
        }

        @Override
        public List<ServiceOperationsCommand.ServiceOperationId> getAllOperationsInOrder
                (OperationsContext context)
                throws ServiceDefinitionException, NodesConfigurationException, SystemException {

            List<ServiceOperationsCommand.ServiceOperationId> allOpList = new ArrayList<>();

            context.getNodesConfig().getAllNodes()
                    .forEach(node -> allOpList.add(new ServiceOperationsCommand.ServiceOperationId(
                            ServiceOperationsCommand.ServiceOperation.RESTART,
                            Service.SETTINGS,
                            node)));

            getRestartsInOrder(context.getServicesInstallationSorter(), context.getNodesConfig()).forEach(allOpList::addAll);

            return allOpList;
        }

        public List<List<ServiceOperationsCommand.ServiceOperationId>> getRestartsInOrder
                (ServicesInstallationSorter servicesInstallationSorter, NodesConfigWrapper nodesConfig)
                throws ServiceDefinitionException, NodesConfigurationException, SystemException {
            return servicesInstallationSorter.orderOperations (getRestarts(), nodesConfig);
        }
    }

}

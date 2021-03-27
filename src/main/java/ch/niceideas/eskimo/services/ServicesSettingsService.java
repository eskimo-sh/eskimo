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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ServicesSettingsService {

    private static final Logger logger = Logger.getLogger(ServicesSettingsService.class);

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Autowired
    private NodesConfigurationService nodesConfigurationService;

    private final ReentrantLock servicesSettingsApplyLock = new ReentrantLock();

    /* For tests */
    public void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    public void setNodeRangeResolver(NodeRangeResolver nodeRangeResolver) {
        this.nodeRangeResolver = nodeRangeResolver;
    }
    public void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
    void setNodesConfigurationService (NodesConfigurationService nodesConfigurationService) {
        this.nodesConfigurationService = nodesConfigurationService;
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public void applyServicesSettings(SettingsOperationsCommand command) throws FileException, SetupException, SystemException  {

        servicesSettingsApplyLock.lock();
        try {

            ServicesSettingsWrapper servicesSettings = command.getNewSettings();

            List<String> dirtyServices = command.getRestartedServices();

            configurationService.saveServicesSettings(servicesSettings);

            if (dirtyServices != null && !dirtyServices.isEmpty()) {
                NodesConfigWrapper nodesConfig = configurationService.loadNodesConfig();

                if (nodesConfig != null) { // maybe this happens before any nodes config has been set

                    ServicesInstallStatusWrapper serviceInstallStatus = configurationService.loadServicesInstallationStatus();

                    ServiceOperationsCommand restartCommand = ServiceOperationsCommand.createForRestartsOnly(
                            servicesDefinition,
                            nodeRangeResolver,
                            dirtyServices.toArray(new String[0]),
                            serviceInstallStatus,
                            nodesConfig);

                    nodesConfigurationService.applyNodesConfig(restartCommand);
                }
            }

        } catch (SystemException | NodesConfigurationException | ServiceDefinitionException e) {
            logger.error (e, e);
            throw new SystemException(e);

        } finally {
            servicesSettingsApplyLock.unlock();
        }
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public ServicesSettingsWrapper prepareSaveSettings (
            String settingsFormAsString,
            Map<String, Map<String, List<SettingsOperationsCommand.ChangedSettings>>> changedSettings,
            List<String> restartedServices) throws FileException, SetupException  {

        ServicesSettingsWrapper servicesSettings = configurationService.loadServicesSettings();

        String[] dirtyServices = fillInEditedConfigs(changedSettings, new JSONObject(settingsFormAsString), servicesSettings.getSubJSONArray("settings"));

        restartedServices.addAll(Arrays.asList(dirtyServices));

        return servicesSettings;
    }

    String[] fillInEditedConfigs(Map<String, Map<String, List<SettingsOperationsCommand.ChangedSettings>>> changedSettings,
                                 JSONObject settingsForm, JSONArray configArrayForService) {

        Set<String> dirtyServices = new HashSet<>();

        // apply changes, proceed service by service
        for (String service : servicesDefinition.listAllServices()) {

            // get all properties for service
            for (String settingsKey : settingsForm.keySet()) {

                if (settingsKey.startsWith(service)) {

                    String value = settingsForm.getString(settingsKey);

                    String propertyKey = settingsKey.substring(service.length() + 1).replace("-", ".");

                    // now iterate through saved (existing) configs and update values
                    main:
                    for (int i = 0; i < configArrayForService.length(); i++) {
                        JSONObject object = configArrayForService.getJSONObject(i);
                        String serviceName = object.getString("name");
                        if (serviceName.equals(service)) {

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

                                        String defaultValue = property.getString("defaultValue");
                                        String previousValue = property.has("value") ? property.getString("value") : null;

                                        // Handle service dirtiness
                                        if (   (StringUtils.isBlank(previousValue) && StringUtils.isNotBlank(value))
                                            || (StringUtils.isNotBlank(previousValue) && StringUtils.isBlank(value))
                                            || (StringUtils.isNotBlank(previousValue) && StringUtils.isNotBlank(value) && !previousValue.equals(value)
                                                )) {

                                            dirtyServices.add(serviceName);

                                            Map<String, List<SettingsOperationsCommand.ChangedSettings>> changedSettingsforService =
                                                    changedSettings.computeIfAbsent(serviceName, ser -> new HashMap<>());

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
                                        break main;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return dirtyServices.toArray(new String[0]);
    }
}

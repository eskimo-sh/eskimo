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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.*;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ServicesDefinition implements InitializingBean {

    private static final Logger logger = Logger.getLogger(ServicesDefinition.class);

    @Autowired
    private SetupService setupService;

    @Value("${servicesDefinitionFile}")
    private String servicesDefinitionFile = "classpath:services.json";

    private ReentrantLock persistEnvLock = new ReentrantLock();

    private Map<String, Service> services = new HashMap<>();

    /** For tests only */
    public void addService(Service service) {
        this.services.put (service.getName(), service);
    }
    public void setSetupService(SetupService setupService) {
        this.setupService = setupService;
    }


    @Override
    public void afterPropertiesSet() throws Exception {

        InputStream is = ResourceUtils.getResourceAsStream(servicesDefinitionFile);
        if (is == null) {
            throw new RuntimeException("File " + servicesDefinitionFile + " couldn't be loaded");
        }
        String servicesAsString =  StreamUtils.getAsString(is);
        if (StringUtils.isBlank(servicesAsString)) {
            throw new RuntimeException("File " + servicesDefinitionFile + " is empty.");
        }
        JsonWrapper servicesConfig = new JsonWrapper(servicesAsString);

        for (String serviceString : servicesConfig.getRootKeys()) {

            Service service = new Service();

            service.setName(serviceString);

            Integer configOrder = (Integer) servicesConfig.getValueForPath(serviceString+".config.order");
            if (configOrder == null) {
                throw new ServiceDefinitionException("Service " + serviceString + " it not properly declaring 'order' config");
            }
            service.setConfigOrder(configOrder);

            Boolean unique = (Boolean)  servicesConfig.getValueForPath(serviceString+".config.unique");
            service.setUnique(unique != null && unique); // false by default

            Boolean mandatory = (Boolean)  servicesConfig.getValueForPath(serviceString+".config.mandatory");
            service.setMandatory(mandatory != null && mandatory);

            String conditional = (String) servicesConfig.getValueForPath(serviceString+".config.conditional");
            if (StringUtils.isNotBlank(conditional)) {
                service.setConditional(ConditionalInstallation.valueOf(conditional));
            } else {
                service.setConditional(ConditionalInstallation.NONE);
            }

            String group = (String) servicesConfig.getValueForPath(serviceString+".config.group");
            String name = (String) servicesConfig.getValueForPath(serviceString+".config.name");

            service.setStatusGroup (group);
            service.setStatusName (name);

            Integer selectionLayoutRow = (Integer) servicesConfig.getValueForPath(serviceString+".config.selectionLayout.row");
            if (selectionLayoutRow == null) {
                throw new ServiceDefinitionException("For service " + serviceString + " selectionLayout Row is null");
            }
            service.setSelectionLayoutRow(selectionLayoutRow);

            Integer selectionLayoutCol = (Integer) servicesConfig.getValueForPath(serviceString+".config.selectionLayout.col");
            if (selectionLayoutCol == null) {
                throw new ServiceDefinitionException("For service " + serviceString + " selectionLayout Col is null");
            }
            service.setSelectionLayoutCol(selectionLayoutCol);

            String memoryConsumptionString = (String) servicesConfig.getValueForPath(serviceString+".config.memory");
            service.setMemoryConsumptionSize(StringUtils.isBlank(memoryConsumptionString) ?
                    MemoryConsumptionSize.NEGLECTABLE :
                    MemoryConsumptionSize.valueOf(memoryConsumptionString.toUpperCase()));

            if (servicesConfig.hasPath(serviceString+".config.memoryAdditional")) {
                JSONArray memAdditionalArray = servicesConfig.getSubJSONObject(serviceString).getJSONObject("config").getJSONArray("memoryAdditional");
                for (int i = 0; i < memAdditionalArray.length(); i++) {

                    String memAdditionalService = memAdditionalArray.getString(i);
                    service.addAdditionalMemory (memAdditionalService);
                }
            }

            if (servicesConfig.hasPath(serviceString+".ui")) {

                UIConfig uiConfig = new UIConfig(service);
                uiConfig.setUrlTemplate((String) servicesConfig.getValueForPath(serviceString+".ui.urlTemplate"));
                Integer uiWaitTime = (Integer) servicesConfig.getValueForPath(serviceString+".ui.waitTime");
                if (uiWaitTime != null) {
                    uiConfig.setWaitTime(uiWaitTime);
                }
                uiConfig.setProxyTargetPort((Integer) servicesConfig.getValueForPath(serviceString+".ui.proxyTargetPort"));
                uiConfig.setTitle((String) servicesConfig.getValueForPath(serviceString+".ui.title"));
                uiConfig.setIcon((String) servicesConfig.getValueForPath(serviceString+".ui.icon"));

                service.setUiConfig(uiConfig);
            }

            if (servicesConfig.hasPath(serviceString+".dependencies")) {
                JSONArray dependenciesConf = servicesConfig.getSubJSONObject(serviceString).getJSONArray("dependencies");
                for (int i = 0; i < dependenciesConf.length(); i++) {

                    JSONObject depObj = dependenciesConf.getJSONObject(i);
                    Dependency dependency = new Dependency();

                    String mesString = depObj.getString("masterElectionStrategy");
                    if (StringUtils.isNotBlank(mesString)) {
                        dependency.setMes(MasterElectionStrategy.valueOf(mesString));
                    } else {
                        dependency.setMes(MasterElectionStrategy.NONE);
                    }

                    String masterServiceString = depObj.getString("masterService");
                    if (masterServiceString != null) {
                        dependency.setMasterService(masterServiceString);
                    }


                    Integer numberOfMaster = depObj.has("numberOfMasters") ? depObj.getInt("numberOfMasters") : null;
                    if (numberOfMaster != null) {
                        dependency.setNumberOfMasters(numberOfMaster);
                    }

                    Boolean depMandatory = depObj.has("mandatory") ? depObj.getBoolean("mandatory") : null;
                    // default is true
                    dependency.setMandatory (depMandatory == null || depMandatory);

                    service.addDependency(dependency);
                }
            }

            if (servicesConfig.hasPath(serviceString+".additionalEnvironment")) {
                JSONArray addEnvConf = servicesConfig.getSubJSONObject(serviceString).getJSONArray("additionalEnvironment");
                for (int i = 0; i < addEnvConf.length(); i++) {
                    String additionalEnv = addEnvConf.getString(i);
                    service.addAdditionalEnvironment(additionalEnv);
                }
            }

            services.put(serviceString, service);
        }
    }


    public void executeInEnvironmentLock (EnvironmentOperation operation)
            throws JSONException, FileException, ServiceDefinitionException, SetupException {
        JsonWrapper env = null;
        try {
            persistEnvLock.lock();
            env = loadPersistentEnvironment();

            operation.call(env);

        } finally {

            if (env != null) {
                savePersistentEnvironment(env);
            }
            persistEnvLock.unlock();
        }

    }

    private void savePersistentEnvironment(JsonWrapper env) throws FileException, JSONException, SetupException {
        String configStoragePath = setupService.getConfigStoragePath();
        FileUtils.writeFile(new File(configStoragePath + "/persistent-environment.json"), env.getFormattedValue());
    }

    private JsonWrapper loadPersistentEnvironment() throws JSONException, FileException, SetupException {
        String configStoragePath = setupService.getConfigStoragePath();
        File envFile = new File(configStoragePath + "/persistent-environment.json");
        if (!envFile.exists()) {
            return new JsonWrapper("{}");
        }
        return new JsonWrapper(FileUtils.readFile(envFile));
    }

    public String[] getAllServices() {
        return services.values().stream()
                .map(Service::getName).toArray(String[]::new);
    }

    public String getAllServicesString() {
        String[] allServices = getAllServices();
        return String.join(" ", allServices);
    }

    public Topology getTopology(NodesConfigWrapper nodesConfig, Set<String> deadIps)
            throws ServiceDefinitionException, NodesConfigurationException {
        return Topology.create(nodesConfig, deadIps, this);
    }

    public Service getService(String serviceName) {
        return services.get (serviceName);
    }

    public String[] listMultipleServices() {
        return services.values().stream()
                .filter(it -> !it.isUnique())
                .map(Service::getName)
                .sorted().toArray(String[]::new);
    }

    public String[] listMandatoryServices() {
        return services.values().stream()
                .filter(Service::isMandatory)
                .map(Service::getName)
                .sorted().toArray(String[]::new);
    }

    public String[] listUniqueServices() {
        return services.values().stream()
                .filter(Service::isUnique)
                .map(Service::getName)
                .sorted().toArray(String[]::new);
    }

    public String[] listProxiedServices() {
        return services.values().stream()
                .filter(it -> it.isProxied())
                .sorted(Comparator.comparingInt(Service::getConfigOrder))
                .map(Service::getName).toArray(String[]::new);
    }

    public String[] listUIServices() {
        return services.values().stream()
                .filter(it -> it.isUiService())
                .sorted(Comparator.comparingInt(Service::getConfigOrder))
                .map(Service::getName).toArray(String[]::new);
    }

    public Map<String, UIConfig> getUIServicesConfig() {
        return services.values().stream()
                .filter(it -> it.isUiService())
                .collect(Collectors.toMap(Service::getName, Service::getUiConfig));
    }

    public String[] listServicesInOrder() {
        return services.values().stream()
                .sorted(Comparator.comparingInt(Service::getConfigOrder))
                .map(Service::getName).toArray(String[]::new);
    }

    public String[] listServicesOrderedByDependencies() {
        return services.values().stream()
                .sorted(this::compareServices)
                .map(Service::getName).toArray(String[]::new);
    }

    int compareServices(Service one, Service other) {
        if (one.getRelevantDependenciesCount() > other.getRelevantDependenciesCount()) {
            return 1;
        }
        if (one.getRelevantDependenciesCount() < other.getRelevantDependenciesCount()) {
            return -1;
        }

        // need to browse dependencies
        for (Dependency dep : one.getDependencies()) {
            if (dep.getMasterService().equals(other.getName())) {
                return 1;
            }
        }
        for (Dependency dep : other.getDependencies()) {
            if (dep.getMasterService().equals(one.getName())) {
                return -1;
            }
        }

        return 0;
    }

    public int compareServices(String servOne, String servOther) {

        Service one = getService(servOne);
        Service other = getService(servOther);

        return compareServices(one, other);
    }

    public Collection<String> getDependentServices(String service) {
        return getDependentServicesInner(service, new HashSet<>()).stream()
                .sorted((service1, service2) -> {
                    if (getDependentServicesInner(service1, new HashSet<>()).contains(service2)) {
                        return -1;
                    }
                    if (getDependentServicesInner(service2, new HashSet<>()).contains(service1)) {
                        return +1;
                    }
                    return service1.compareTo(service2);
                }).collect(Collectors.toList());
    }

    private Set<String> getDependentServicesInner(String service, Set<String> currentSet) {
        if (currentSet.contains(service)) {
            return new HashSet<>();
        }
        List<String> directDependentList = services.values().stream()
                .filter(it -> it.dependsOn(service))
                .map(Service::getName)
                .collect(Collectors.toList());
        currentSet.addAll(directDependentList);
        for (String depService: directDependentList) {
            currentSet.addAll(getDependentServicesInner(depService, currentSet));
        }
        return currentSet;
    }

    public interface EnvironmentOperation {
        void call(JsonWrapper persistentEnvironment) throws JSONException, ServiceDefinitionException;
    }
}

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
import org.json.JSONArray;
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

    public static final String SERVICE_PREFIX = "Service ";
    @Autowired
    private SetupService setupService;

    @Value("${servicesDefinitionFile}")
    private String servicesDefinitionFile = "classpath:services.json";


    @Value("${server.servlet.context-path:#{null}}")
    private String configuredContextPath = "";

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
            throw new ServiceDefinitionException("File " + servicesDefinitionFile + " couldn't be loaded");
        }
        String servicesAsString =  StreamUtils.getAsString(is);
        if (StringUtils.isBlank(servicesAsString)) {
            throw new ServiceDefinitionException("File " + servicesDefinitionFile + " is empty.");
        }
        JsonWrapper servicesConfig = new JsonWrapper(servicesAsString);

        for (String serviceString : servicesConfig.getRootKeys()) {

            Service service = new Service();

            service.setName(serviceString);

            Integer configOrder = (Integer) servicesConfig.getValueForPath(serviceString+".config.order");
            if (configOrder == null) {
                throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " it not properly declaring 'order' config");
            }
            service.setConfigOrder(configOrder);

            Boolean unique = (Boolean)  servicesConfig.getValueForPath(serviceString+".config.unique");
            service.setUnique(unique != null && unique); // false by default

            Boolean marathon = (Boolean)  servicesConfig.getValueForPath(serviceString+".config.marathon");
            service.setMarathon(marathon != null && marathon); // false by default

            Boolean mandatory = (Boolean)  servicesConfig.getValueForPath(serviceString+".config.mandatory");
            service.setMandatory(mandatory != null && mandatory);

            String conditional = (String) servicesConfig.getValueForPath(serviceString+".config.conditional");
            if (StringUtils.isNotBlank(conditional)) {
                service.setConditional(ConditionalInstallation.valueOf(conditional));
            } else {
                service.setConditional(ConditionalInstallation.NONE);
            }

            String imageName = (String) servicesConfig.getValueForPath(serviceString+".config.imageName");
            service.setImageName (imageName);

            String group = (String) servicesConfig.getValueForPath(serviceString+".config.group");
            String name = (String) servicesConfig.getValueForPath(serviceString+".config.name");

            service.setStatusGroup (group);
            service.setStatusName (name);

            Integer selectionLayoutRow = (Integer) servicesConfig.getValueForPath(serviceString+".config.selectionLayout.row");
            if (selectionLayoutRow != null) {
                service.setSelectionLayoutRow(selectionLayoutRow);
            }

            Integer selectionLayoutCol = (Integer) servicesConfig.getValueForPath(serviceString+".config.selectionLayout.col");
            if (selectionLayoutCol != null) {
                service.setSelectionLayoutCol(selectionLayoutCol);
            } else if (selectionLayoutRow != null) {
                throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " defined a Row for selection layout but no column");
            }

            // find out if another service is already defined at same location
            if (services.values().stream()
                    .filter(srv -> service.getSelectionLayoutCol() != -1)
                    .anyMatch(srv -> srv.getSelectionLayoutCol() == selectionLayoutCol && srv.getSelectionLayoutRow() == selectionLayoutRow)) {
                throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " defines a row and col for selection layout already defined by another service");
            }

            String memoryConsumptionString = (String) servicesConfig.getValueForPath(serviceString+".config.memory");
            service.setMemoryConsumptionSize(StringUtils.isBlank(memoryConsumptionString) ?
                    MemoryConsumptionSize.NEGLECTABLE :
                    MemoryConsumptionSize.valueOf(memoryConsumptionString.toUpperCase()));

            service.setLogo((String) servicesConfig.getValueForPath(serviceString+".config.logo"));
            service.setIcon((String) servicesConfig.getValueForPath(serviceString+".config.icon"));

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

                if (servicesConfig.hasPath(serviceString+".ui.applyStandardProxyReplacements")) {
                    uiConfig.setApplyStandardProxyReplacements((Boolean)servicesConfig.getValueForPath(serviceString+".ui.applyStandardProxyReplacements"));
                }

                if (servicesConfig.hasPath(serviceString+".ui.statusPageLinktitle")) {
                    uiConfig.setStatusPageLinkTitle((String)servicesConfig.getValueForPath(serviceString+".ui.statusPageLinktitle"));
                }

                service.setUiConfig(uiConfig);

                if (servicesConfig.hasPath(serviceString+".ui.proxyReplacements")) {

                    JSONArray proxyReplacements = servicesConfig.getSubJSONObject(serviceString).getJSONObject("ui").getJSONArray("proxyReplacements");
                    for (int i = 0; i < proxyReplacements.length(); i++) {

                        JSONObject proxyReplacement = proxyReplacements.getJSONObject(i);
                        String typeAsString = proxyReplacement.getString("type");
                        String source = proxyReplacement.getString("source");
                        String target = proxyReplacement.getString("target");
                        String urlPattern = proxyReplacement.has("urlPattern") ? proxyReplacement.getString("urlPattern") : null;

                        ProxyReplacement pr = new ProxyReplacement();
                        pr.setType(ProxyReplacement.ProxyReplacementType.valueOf(typeAsString));
                        pr.setSource(source);
                        pr.setTarget(target);
                        pr.setUrlPattern(urlPattern);

                        uiConfig.addProxyReplacement (pr);
                    }

                }
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

            if (servicesConfig.hasPath(serviceString+".commands")) {
                JSONArray commandsConf = servicesConfig.getSubJSONObject(serviceString).getJSONArray("commands");
                for (int i = 0; i < commandsConf.length(); i++) {

                    JSONObject commandObj = commandsConf.getJSONObject(i);
                    Command command = new Command();

                    String commandId = commandObj.getString("id");
                    if (StringUtils.isBlank(commandId)) {
                        throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a command without an id");
                    }
                    command.setId(commandId);

                    String commandName = commandObj.getString("name");
                    if (StringUtils.isBlank(commandName)) {
                        throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a command without a name");
                    }
                    command.setName(commandName);

                    String commandIcon = commandObj.getString("icon");
                    if (StringUtils.isBlank(commandIcon)) {
                        throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a command without an icon");
                    }
                    command.setIcon(commandIcon);

                    String commandCall = commandObj.getString("command");
                    if (StringUtils.isBlank(commandCall)) {
                        throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a command without a command");
                    }
                    command.setCommandCall(commandCall);

                    service.addCommand(command);
                }
            }

            // Marathon services have an implicit dependency on marathon
            if (service.isMarathon()) {
                Dependency marathonDependency = new Dependency();
                marathonDependency.setMes(MasterElectionStrategy.RANDOM);
                marathonDependency.setMasterService("marathon");
                marathonDependency.setNumberOfMasters(1);
                marathonDependency.setMandatory(true);
                service.addDependency(marathonDependency);
            }

            if (servicesConfig.hasPath(serviceString+".additionalEnvironment")) {
                JSONArray addEnvConf = servicesConfig.getSubJSONObject(serviceString).getJSONArray("additionalEnvironment");
                for (int i = 0; i < addEnvConf.length(); i++) {
                    String additionalEnv = addEnvConf.getString(i);
                    service.addAdditionalEnvironment(additionalEnv);
                }
            }

            if (servicesConfig.hasPath(serviceString+".editableConfigurations")) {

                JSONArray editableConfArray = servicesConfig.getSubJSONObject(serviceString).getJSONArray("editableConfigurations");
                for (int i = 0; i < editableConfArray.length(); i++) {
                    JSONObject conf = editableConfArray.getJSONObject(i);

                    String filename = conf.getString("filename");
                    String propertyTypeAsString = conf.getString("propertyType");
                    EditablePropertyType propertyType = EditablePropertyType.valueOf(propertyTypeAsString.toUpperCase());
                    String propertyFormat = conf.getString("propertyFormat");
                    String filesystemService = conf.getString("filesystemService");

                    EditableConfiguration configuration = new EditableConfiguration(service, filename, propertyType, propertyFormat, filesystemService);

                    if (conf.has("commentPrefix")) {
                        String commentPrefix = conf.getString("commentPrefix");
                        configuration.setCommentPrefix(commentPrefix);
                    }


                    JSONArray propertiesArray = conf.getJSONArray("properties");
                    for (int j = 0; j < propertiesArray.length(); j++) {
                        JSONObject prop = propertiesArray.getJSONObject(j);

                        String propName = prop.getString("name");
                        String comment = prop.getString("comment");
                        String defaultValue = prop.getString("defaultValue");

                        EditableProperty property = new EditableProperty(propName, comment, defaultValue);

                        configuration.addProperty(property);
                    }

                    service.addEditableConfiguration(configuration);
                }
            }

            services.put(serviceString, service);
        }
    }


    public void executeInEnvironmentLock (EnvironmentOperation operation)
            throws FileException, ServiceDefinitionException, SetupException {
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

    private void savePersistentEnvironment(JsonWrapper env) throws FileException, SetupException {
        String configStoragePath = setupService.getConfigStoragePath();
        FileUtils.writeFile(new File(configStoragePath + "/persistent-environment.json"), env.getFormattedValue());
    }

    private JsonWrapper loadPersistentEnvironment() throws FileException, SetupException {
        String configStoragePath = setupService.getConfigStoragePath();
        File envFile = new File(configStoragePath + "/persistent-environment.json");
        if (!envFile.exists()) {
            return new JsonWrapper("{}");
        }
        return new JsonWrapper(FileUtils.readFile(envFile));
    }

    public String getAllServicesString() {
        return String.join(" ", listAllServices());
    }

    public Topology getTopology(NodesConfigWrapper nodesConfig, MarathonServicesConfigWrapper marathonConfig, Set<String> deadIps, String currentNodeIpAddress)
            throws ServiceDefinitionException, NodesConfigurationException {
        return Topology.create(nodesConfig, marathonConfig, deadIps, this, configuredContextPath, currentNodeIpAddress);
    }

    public Service getService(String serviceName) {
        return services.get (serviceName);
    }

    public String[] listAllServices() {
        return services.values().stream()
                .map(Service::getName)
                .sorted().toArray(String[]::new);
    }

    public String[] listAllNodesServices() {
        return services.values().stream()
                .filter(service -> !service.isMarathon())
                .map(Service::getName)
                .sorted().toArray(String[]::new);
    }

    public String[] listMultipleServices() {
        return services.values().stream()
                .filter(it -> !it.isUnique() && !it.isMarathon())
                .map(Service::getName)
                .sorted().toArray(String[]::new);
    }

    public String[] listMandatoryServices() {
        return services.values().stream()
                .filter(Service::isMandatory)
                .map(Service::getName)
                .sorted()
                .toArray(String[]::new);
    }

    public String[] listUniqueServices() {
        return services.values().stream()
                .filter(Service::isUnique)
                .filter(Service::isNotMarathon)
                .map(Service::getName)
                .sorted()
                .toArray(String[]::new);
    }

    public String[] listMarathonServices() {
        return services.values().stream()
                .filter(Service::isMarathon)
                .map(Service::getName)
                .sorted()
                .toArray(String[]::new);
    }

    public String[] listProxiedServices() {
        return services.values().stream()
                .filter(Service::isProxied)
                .sorted(Comparator.comparingInt(Service::getConfigOrder))
                .map(Service::getName)
                .toArray(String[]::new);
    }

    public UIConfig[] listLinkServices() {
        return services.values().stream()
                .filter(Service::isLink)
                .sorted(Comparator.comparingInt(Service::getConfigOrder))
                .map(Service::getUiConfig)
                .toArray(UIConfig[]::new);
    }

    public String[] listUIServices() {
        return services.values().stream()
                .filter(Service::isUiService)
                .sorted(Comparator.comparingInt(Service::getConfigOrder))
                .map(Service::getName)
                .toArray(String[]::new);
    }

    public Map<String, UIConfig> getUIServicesConfig() {
        return services.values().stream()
                .filter(Service::isUiService)
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

        // marathon services are always last
        if (one.isMarathon() && !other.isMarathon()) {
            return 1;
        }
        if (!one.isMarathon() && other.isMarathon()) {
            return -1;
        }

        // compare based on dependencies
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
        void call(JsonWrapper persistentEnvironment) throws ServiceDefinitionException;
    }
}

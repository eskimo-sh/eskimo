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

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.*;
import ch.niceideas.eskimo.model.service.proxy.PageScripter;
import ch.niceideas.eskimo.model.service.proxy.ProxyReplacement;
import ch.niceideas.eskimo.model.service.proxy.UrlRewriting;
import ch.niceideas.eskimo.model.service.proxy.WebCommand;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Profile("!test-services")
public class ServicesDefinitionImpl implements ServicesDefinition, InitializingBean {

    private static final Logger logger = Logger.getLogger(ServicesDefinitionImpl.class);

    @Autowired
    private SetupService setupService;

    @Value("${servicesDefinitionFile}")
    private String servicesDefinitionFile = "classpath:services.json";

    @Value("${server.servlet.context-path:#{null}}")
    private String configuredContextPath = "";

    private final ReentrantLock persistEnvLock = new ReentrantLock();

    private final Map<Service, ServiceDefinition> services = new HashMap<>();

    private ServiceDefinition kubeMasterServiceDef;
    private ServiceDefinition kubeSlaveServiceDef;

    /** For tests only */
    public void addService(ServiceDefinition serviceDef) {
        this.services.put (serviceDef.toService(), serviceDef);
    }
    public void setSetupService(SetupService setupService) {
        this.setupService = setupService;
    }

    protected String getServicesDefinitionFile() {
        return servicesDefinitionFile;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        HashMap<WebCommand, Service> webCommandServices = new HashMap<>();

        logger.info ("Using services definition from file : " + getServicesDefinitionFile());

        InputStream is = ResourceUtils.getResourceAsStream(getServicesDefinitionFile());
        if (is == null) {
            throw new ServiceDefinitionException("File " + getServicesDefinitionFile() + " couldn't be loaded");
        }
        String servicesAsString =  StreamUtils.getAsString(is, StandardCharsets.UTF_8);
        if (StringUtils.isBlank(servicesAsString)) {
            throw new ServiceDefinitionException("File " + getServicesDefinitionFile() + " is empty.");
        }
        JsonWrapper servicesConfig = new JsonWrapper(servicesAsString);

        for (String serviceString : servicesConfig.getRootKeys()) {

            ServiceDefinition serviceDef = new ServiceDefinition(serviceString);

            Integer configOrder = (Integer) servicesConfig.getValueForPath(serviceString+".config.order");
            if (configOrder == null) {
                throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " it not properly declaring 'order' config");
            }

            // ensure there is not already a service defined with same order
            if (services.values().stream()
                            .anyMatch(s -> s.getConfigOrder() == configOrder)) {
                throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is defining order " + configOrder + " which is already used");
            }

            serviceDef.setConfigOrder(configOrder);

            Boolean unique = (Boolean) servicesConfig.getValueForPath(serviceString+".config.unique");
            serviceDef.setUnique(unique != null && unique); // false by default

            Boolean kubernetes = (Boolean) servicesConfig.getValueForPath(serviceString+".config.kubernetes");
            serviceDef.setKubernetes(kubernetes != null && kubernetes); // false by default

            Boolean kubeMaster = (Boolean) servicesConfig.getValueForPath(serviceString+".config.kubeMaster");
            if (kubeMaster != null && kubeMaster) {
                if (kubeMasterServiceDef != null) {
                    throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is defined as kube master but another servce " + kubeMasterServiceDef.getName() + " is already");
                }
                kubeMasterServiceDef = serviceDef;
            }

            Boolean kubeSlave = (Boolean) servicesConfig.getValueForPath(serviceString+".config.kubeSlave");
            if (kubeSlave != null && kubeSlave) {
                if (kubeSlaveServiceDef != null) {
                    throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is defined as kube slave but another servce " + kubeSlaveServiceDef.getName() + " is already");
                }
                kubeSlaveServiceDef = serviceDef;
            }

            Boolean registryOnly = (Boolean) servicesConfig.getValueForPath(serviceString+".config.registryOnly");
            serviceDef.setRegistryOnly(registryOnly != null && registryOnly); // false by default

            Boolean mandatory = (Boolean)  servicesConfig.getValueForPath(serviceString+".config.mandatory");
            serviceDef.setMandatory(mandatory != null && mandatory);

            String conditional = (String) servicesConfig.getValueForPath(serviceString+".config.conditional");
            if (StringUtils.isNotBlank(conditional)) {
                serviceDef.setConditional(ConditionalInstallation.valueOf(conditional));
            } else {
                serviceDef.setConditional(ConditionalInstallation.NONE);
            }

            String imageName = (String) servicesConfig.getValueForPath(serviceString+".config.imageName");
            serviceDef.setImageName (imageName);

            String group = (String) servicesConfig.getValueForPath(serviceString+".config.group");
            String name = (String) servicesConfig.getValueForPath(serviceString+".config.name");

            serviceDef.setStatusGroup (group);
            serviceDef.setStatusName (name);

            String userName = (String) servicesConfig.getValueForPath(serviceString+".config.user.name");
            if (StringUtils.isNotBlank(userName)) {

                Integer userId = (Integer) servicesConfig.getValueForPath(serviceString+".config.user.id");
                if (userId == null) {
                    throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " defined a user '" + userName +"' but no user ID !");
                }

                ServiceUser user = new ServiceUser (userName, userId);
                serviceDef.setUser (user);
            }

            Integer selectionLayoutRow = (Integer) servicesConfig.getValueForPath(serviceString+".config.selectionLayout.row");
            if (selectionLayoutRow != null) {
                serviceDef.setSelectionLayoutRow(selectionLayoutRow);
            }

            Integer selectionLayoutCol = (Integer) servicesConfig.getValueForPath(serviceString+".config.selectionLayout.col");
            if (selectionLayoutCol != null) {
                serviceDef.setSelectionLayoutCol(selectionLayoutCol);
            } else if (selectionLayoutRow != null) {
                throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " defined a Row for selection layout but no column");
            }

            // find out if another service is already defined at same location
            //noinspection ConstantConditions
            if (services.values().stream()
                    .filter(srv -> serviceDef.getSelectionLayoutCol() != -1)
                    .anyMatch(srv -> srv.getSelectionLayoutCol() == selectionLayoutCol && srv.getSelectionLayoutRow() == selectionLayoutRow)) {
                throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " defines a row and col for selection layout already defined by another service");
            }

            String memoryConsumptionString = (String) servicesConfig.getValueForPath(serviceString+".config.memory");
            serviceDef.setMemoryConsumptionSize(StringUtils.isBlank(memoryConsumptionString) ?
                    MemoryConsumptionSize.NEGLIGIBLE :
                    MemoryConsumptionSize.valueOf(memoryConsumptionString.toUpperCase()));

            serviceDef.setLogo((String) servicesConfig.getValueForPath(serviceString+".config.logo"));
            serviceDef.setIcon((String) servicesConfig.getValueForPath(serviceString+".config.icon"));

            if (servicesConfig.hasPath(serviceString+".config.memoryAdditional")) {
                JSONArray memAdditionalArray = servicesConfig.getSubJSONObject(serviceString).getJSONObject("config").getJSONArray("memoryAdditional");
                for (int i = 0; i < memAdditionalArray.length(); i++) {

                    String memAdditionalService = memAdditionalArray.getString(i);
                    serviceDef.addAdditionalMemory (Service.from(memAdditionalService));
                }
            }

            if (servicesConfig.hasPath(serviceString+".ui")) {

                UIConfig uiConfig = new UIConfig(serviceDef);
                uiConfig.setUrlTemplate((String) servicesConfig.getValueForPath(serviceString+".ui.urlTemplate"));
                Integer uiWaitTime = (Integer) servicesConfig.getValueForPath(serviceString+".ui.waitTime");
                if (uiWaitTime != null) {
                    uiConfig.setWaitTime(uiWaitTime);
                }

                Boolean kubeProxy = ((Boolean) servicesConfig.getValueForPath(serviceString+".ui.kubeProxy"));
                uiConfig.setUsingKubeProxy (kubeProxy != null && kubeProxy);

                uiConfig.setProxyTargetPort((Integer) servicesConfig.getValueForPath(serviceString+".ui.proxyTargetPort"));
                uiConfig.setTitle((String) servicesConfig.getValueForPath(serviceString+".ui.title"));

                String requiredRole = servicesConfig.getValueForPathAsString(serviceString+".ui.role");
                if (StringUtils.isBlank(requiredRole)) {
                    requiredRole = "*";
                }
                uiConfig.setRequiredRole(requiredRole);

                if (servicesConfig.hasPath(serviceString+".ui.applyStandardProxyReplacements")) {
                    uiConfig.setApplyStandardProxyReplacements((Boolean)servicesConfig.getValueForPath(serviceString+".ui.applyStandardProxyReplacements"));
                }

                serviceDef.setUiConfig(uiConfig);

                if (servicesConfig.hasPath(serviceString+".ui.pageScripters")) {

                    JSONArray pageScripters = servicesConfig.getSubJSONObject(serviceString).getJSONObject("ui").getJSONArray("pageScripters");
                    for (int i = 0; i < pageScripters.length(); i++) {

                        JSONObject pageScripterObj = pageScripters.getJSONObject(i);

                        String resourceUrl = pageScripterObj.getString("resourceUrl");
                        if (StringUtils.isBlank(resourceUrl)) {
                            throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " ui config is declaring a pageScripter without a resourceUrl");
                        }

                        String script = pageScripterObj.getString("script");
                        if (StringUtils.isBlank(script)) {
                            throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " ui config is declaring a pageScripter without a script");
                        }

                        PageScripter pageScriper = new PageScripter(resourceUrl, script);

                        uiConfig.addPageScripter (pageScriper);
                    }

                }

                if (servicesConfig.hasPath(serviceString+".ui.urlRewriting")) {
                    JSONArray urlRewritings = servicesConfig.getSubJSONObject(serviceString).getJSONObject("ui").getJSONArray("urlRewriting");
                    for (int i = 0; i < urlRewritings.length(); i++) {

                        JSONObject urlRewritingsObj = urlRewritings.getJSONObject(i);

                        String startUrl = urlRewritingsObj.getString("startUrl");
                        if (StringUtils.isBlank(startUrl)) {
                            throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring an urlRewriting without a startUrl");
                        }

                        String replacement = urlRewritingsObj.getString("replacement");
                        if (StringUtils.isBlank(replacement)) {
                            throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring an urlRewriting without a replacement");
                        }

                        UrlRewriting urlRewriting = new UrlRewriting(startUrl, replacement);

                        uiConfig.addUrlRewriting(urlRewriting);
                    }
                }

                if (servicesConfig.hasPath(serviceString+".ui.proxyReplacements")) {

                    JSONArray proxyReplacements = servicesConfig.getSubJSONObject(serviceString).getJSONObject("ui").getJSONArray("proxyReplacements");
                    for (int i = 0; i < proxyReplacements.length(); i++) {

                        JSONObject proxyReplacement = proxyReplacements.getJSONObject(i);
                        String typeAsString = proxyReplacement.getString("type");
                        String source = proxyReplacement.getString("source");
                        String target = proxyReplacement.getString("target");
                        String urlPattern = proxyReplacement.has("urlPattern") ? proxyReplacement.getString("urlPattern") : null;

                        ProxyReplacement pr = new ProxyReplacement(
                                ProxyReplacement.ProxyReplacementType.valueOf(typeAsString), source, target, urlPattern);

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
                        dependency.setMasterService(Service.from(masterServiceString));
                    }

                    Integer numberOfMaster = depObj.has("numberOfMasters") ? depObj.getInt("numberOfMasters") : null;
                    if (numberOfMaster != null) {
                        dependency.setNumberOfMasters(numberOfMaster);
                    }

                    Boolean depMandatory = depObj.has("mandatory") ? depObj.getBoolean("mandatory") : null;
                    // default is true
                    dependency.setMandatory (depMandatory == null || depMandatory);

                    Boolean depRestart = depObj.has("restart") ? depObj.getBoolean("restart") : null;
                    dependency.setRestart(depRestart == null || depRestart);

                    String conditionalDependency = depObj.has("conditional") ? depObj.getString("conditional") : null;
                    if (StringUtils.isNotBlank(conditionalDependency)) {
                        dependency.setConditionalDependency(Service.from(conditionalDependency));
                    }

                    serviceDef.addDependency(dependency);
                }
            }

            if (servicesConfig.hasPath(serviceString+".webCommands")) {
                JSONArray webCommandsConf = servicesConfig.getSubJSONObject(serviceString).getJSONArray("webCommands");
                for (int i = 0; i < webCommandsConf.length(); i++) {

                    JSONObject webCommandObj = webCommandsConf.getJSONObject(i);

                    String commandId = webCommandObj.getString("id");
                    if (StringUtils.isBlank(commandId)) {
                        throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a command without an id");
                    }

                    String commandCall = webCommandObj.getString("command");
                    if (StringUtils.isBlank(commandCall)) {
                        throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a command without a command");
                    }

                    String serviceName = webCommandObj.getString("service");
                    if (StringUtils.isBlank(serviceName)) {
                        throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a Web command without a service name");
                    }

                    String roleName = webCommandObj.getString("role");

                    WebCommand command = new WebCommand(serviceDef, commandId, commandCall, roleName);
                    webCommandServices.put (command, Service.from(serviceName));

                    serviceDef.addWebCommand (command);
                }
                
            }

            if (servicesConfig.hasPath(serviceString+".commands")) {
                JSONArray commandsConf = servicesConfig.getSubJSONObject(serviceString).getJSONArray("commands");
                for (int i = 0; i < commandsConf.length(); i++) {

                    JSONObject commandObj = commandsConf.getJSONObject(i);

                    String commandId = commandObj.getString("id");
                    if (StringUtils.isBlank(commandId)) {
                        throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a command without an id");
                    }

                    String commandName = commandObj.getString("name");
                    if (StringUtils.isBlank(commandName)) {
                        throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a command without a name");
                    }

                    String commandIcon = commandObj.getString("icon");
                    if (StringUtils.isBlank(commandIcon)) {
                        throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a command without an icon");
                    }

                    String commandCall = commandObj.getString("command");
                    if (StringUtils.isBlank(commandCall)) {
                        throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a command without a command");
                    }

                    Command command = new Command(commandId, commandName, commandIcon, commandCall);

                    serviceDef.addCommand(command);
                }
            }

            if (servicesConfig.hasPath(serviceString+".config.kubeConfig")) {

                KubeConfig kubeConfig = new KubeConfig();

                if (servicesConfig.hasPath(serviceString+".config.kubeConfig.request")) {

                    KubeRequest request = new KubeRequest();

                    String cpu = servicesConfig.getValueForPathAsString(serviceString+".config.kubeConfig.request.cpu");
                    String ram = servicesConfig.getValueForPathAsString(serviceString+".config.kubeConfig.request.ram");

                    request.setCpu(cpu);
                    request.setRam(ram);

                    kubeConfig.setRequest(request);
                }

                serviceDef.setKubeConfig (kubeConfig);

            }

            if (servicesConfig.hasPath(serviceString+".additionalEnvironment")) {
                JSONArray addEnvConf = servicesConfig.getSubJSONObject(serviceString).getJSONArray("additionalEnvironment");
                for (int i = 0; i < addEnvConf.length(); i++) {
                    String additionalEnv = addEnvConf.getString(i);
                    serviceDef.addAdditionalEnvironment(additionalEnv);
                }
            }

            if (servicesConfig.hasPath(serviceString+".masterDetection")) {

                String detStrategyString = servicesConfig.getValueForPathAsString(serviceString+".masterDetection.strategy");
                if (StringUtils.isBlank(detStrategyString)) {
                    throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a master detection without strategy");
                }
                MasterDetectionStrategy detectionStrategy = MasterDetectionStrategy.valueOf(detStrategyString);

                String logFile = servicesConfig.getValueForPathAsString(serviceString+".masterDetection.logFile");
                if (StringUtils.isBlank(logFile)) {
                    throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a master detection without logFile");
                }

                String grep = servicesConfig.getValueForPathAsString(serviceString+".masterDetection.grep");
                if (StringUtils.isBlank(grep)) {
                    throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a master detection without grep");
                }

                String timeStampExtractRexpString = servicesConfig.getValueForPathAsString(serviceString+".masterDetection.timeStampExtractRexp");
                if (StringUtils.isBlank(timeStampExtractRexpString)) {
                    throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a master detection without timeStampExtract REXP");
                }
                Pattern timeStampExtractRexp = Pattern.compile(timeStampExtractRexpString);

                String timeStampFormatString = servicesConfig.getValueForPathAsString(serviceString+".masterDetection.timeStampFormat");
                if (StringUtils.isBlank(timeStampFormatString)) {
                    throw new ServiceDefinitionException(SERVICE_PREFIX + serviceString + " is declaring a master detection without timeStamp Format");
                }
                SimpleDateFormat timeStampFormat = new SimpleDateFormat(timeStampFormatString);

                MasterDetection masterDetection = new MasterDetection(detectionStrategy, logFile, grep, timeStampExtractRexp, timeStampFormat);
                serviceDef.setMasterDetection (masterDetection);

            }

            if (servicesConfig.hasPath(serviceString+".editableSettings")) {

                JSONArray editableSettingsArray = servicesConfig.getSubJSONObject(serviceString).getJSONArray("editableSettings");
                for (int i = 0; i < editableSettingsArray.length(); i++) {
                    JSONObject conf = editableSettingsArray.getJSONObject(i);

                    String filename = conf.getString("filename");
                    String propertyTypeAsString = conf.getString("propertyType");
                    EditablePropertyType propertyType = EditablePropertyType.valueOf(propertyTypeAsString.toUpperCase());
                    String propertyFormat = conf.getString("propertyFormat");
                    String filesystemService = conf.getString("filesystemService");

                    EditableSettings editableSettings = new EditableSettings(serviceDef, filename, propertyType, propertyFormat, filesystemService);

                    if (conf.has("commentPrefix")) {
                        String commentPrefix = conf.getString("commentPrefix");
                        editableSettings.setCommentPrefix(commentPrefix);
                    }


                    JSONArray propertiesArray = conf.getJSONArray("properties");
                    for (int j = 0; j < propertiesArray.length(); j++) {
                        JSONObject prop = propertiesArray.getJSONObject(j);

                        String propName = prop.getString("name");
                        String comment = prop.getString("comment");
                        String defaultValue = prop.getString("defaultValue");
                        String value = prop.has("value") ? prop.getString("value") : null;

                        EditableProperty property = new EditableProperty(propName, comment, defaultValue, value);

                        editableSettings.addProperty(property);
                    }

                    serviceDef.addEditableSettings(editableSettings);
                }
            }

            services.put(serviceDef.toService(), serviceDef);
        }

        // post-processing web commands
        webCommandServices.keySet().forEach(
                command -> command.setTarget(getServiceDefinition(webCommandServices.get(command)))
        );

        try {
            services.values().stream()
                    .filter(ServiceDefinition::isKubernetes)
                    .forEach(service -> {
                        // Kubernetes services have an implicit dependency on kubernetes
                            Dependency kubeDependency = new Dependency();
                            kubeDependency.setMes(MasterElectionStrategy.RANDOM);
                            if (getKubeMasterServiceDef() == null) {
                                throw new IllegalArgumentException("No Kube Master service found !");
                            }
                            kubeDependency.setMasterService(getKubeMasterServiceDef().toService());
                            kubeDependency.setNumberOfMasters(1);
                            kubeDependency.setMandatory(true);
                            kubeDependency.setRestart(false);
                            service.addDependency(kubeDependency);
                    });
        } catch (IllegalArgumentException e) {
            throw new ServiceDefinitionException(e.getMessage(), e);
        }

        enforceConsistency();
    }

    private void enforceConsistency() throws ServiceDefinitionException {

        // make sure there is no hole in order: sum of configOrder should be n(n+1) / 2
        int effSum = services.values().stream()
                .map (ServiceDefinition::getConfigOrder)
                .reduce(0, Integer::sum);

        int nbr = services.keySet().size();
        int theoreticalSum = (nbr * (nbr + 1) / 2) - nbr;
        if (effSum != theoreticalSum) {
            throw new ServiceDefinitionException("There must be a hole in the services config.order. Theoretical sum is " + theoreticalSum
                    + " while effective sum is " + effSum);
        }

        // make sure all master service exist
        try {
            services.values().stream()
                    .map(ServiceDefinition::getDependencies)
                    .flatMap(Collection::stream)
                    .forEach(dep -> {
                        Service master = dep.getMasterService();
                        if (services.get(master) == null) {
                            throw new IllegalArgumentException("Master service " + master + " doesn't exist");
                        }
                    });
        } catch (IllegalArgumentException e) {
            throw new ServiceDefinitionException(e.getMessage(), e);
        }

        // make sure all webCommand services are set !
        try {
            Arrays.stream(listUIServices())
                    .map(this::getServiceDefinition)
                    .map (ServiceDefinition::getWebCommands)
                    .flatMap(List::stream)
                    .forEach(webCommand -> {
                        if (webCommand.getOwner() == null) {
                            throw new IllegalArgumentException("Web command" + webCommand.getId() + " didn't get its service injected");
                        }
                    });
        } catch (IllegalArgumentException e) {
            throw new ServiceDefinitionException(e.getMessage(), e);
        }
    }

    @Override
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

    @Override
    public String getAllServicesString() {
        return Arrays.stream(listAllServices()).map(Service::getName).sorted().collect(Collectors.joining(" "));
    }

    @Override
    public Topology getTopology(NodesConfigWrapper nodesConfig, KubernetesServicesConfigWrapper kubeServicesConfig, Node currentNode)
            throws ServiceDefinitionException, NodesConfigurationException {
        return Topology.create(nodesConfig, kubeServicesConfig, this, configuredContextPath, currentNode);
    }

    @Override
    public ServiceDefinition getServiceDefinition(Service service) {
        return services.get (service);
    }

    @Override
    public Service[] listAllServices() {
        return services.values().stream()
                .map(ServiceDefinition::toService)
                .sorted().toArray(Service[]::new);
    }

    @Override
    public Service[] listAllNodesServices() {
        return services.values().stream()
                .filter(service -> !service.isKubernetes())
                .map(ServiceDefinition::toService)
                .sorted().toArray(Service[]::new);
    }

    @Override
    public long countAllNodesServices() {
        return services.values().stream()
                .filter(service -> !service.isKubernetes())
                .count();
    }

    @Override
    public Service[] listMultipleServicesNonKubernetes() {
        return services.values().stream()
                .filter(it -> !it.isUnique() && !it.isKubernetes())
                .map(ServiceDefinition::toService)
                .sorted().toArray(Service[]::new);
    }

    @Override
    public Service[] listMultipleServices() {
        return services.values().stream()
                .filter(it -> !it.isUnique())
                .map(ServiceDefinition::toService)
                .sorted().toArray(Service[]::new);
    }

    @Override
    public Service[] listMandatoryServices() {
        return services.values().stream()
                .filter(ServiceDefinition::isMandatory)
                .map(ServiceDefinition::toService)
                .sorted()
                .toArray(Service[]::new);
    }

    @Override
    public Service[] listUniqueServices() {
        return services.values().stream()
                .filter(ServiceDefinition::isUnique)
                .filter(ServiceDefinition::isNotKubernetes)
                .map(ServiceDefinition::toService)
                .sorted()
                .toArray(Service[]::new);
    }

    @Override
    public Service[] listKubernetesServices() {
        return services.values().stream()
                .filter(ServiceDefinition::isKubernetes)
                .map(ServiceDefinition::toService)
                .sorted()
                .toArray(Service[]::new);
    }

    @Override
    public long countKubernetesServices() {
        return services.values().stream()
                .filter(ServiceDefinition::isKubernetes)
                .count();
    }

    @Override
    public Service[] listProxiedServices() {
        return services.values().stream()
                .filter(ServiceDefinition::isProxied)
                .sorted(Comparator.comparingInt(ServiceDefinition::getConfigOrder))
                .map(ServiceDefinition::toService)
                .toArray(Service[]::new);
    }

    @Override
    public Service[] listUIServices() {
        return services.values().stream()
                .filter(ServiceDefinition::isUiService)
                .sorted(Comparator.comparingInt(ServiceDefinition::getConfigOrder))
                .map(ServiceDefinition::toService)
                .toArray(Service[]::new);
    }

    @Override
    public Map<Service, UIConfig> getUIServicesConfig() {
        return services.values().stream()
                .filter(ServiceDefinition::isUiService)
                .collect(Collectors.toMap(ServiceDefinition::toService, ServiceDefinition::getUiConfig));
    }

    @Override
    public Service[] listServicesInOrder() {
        return services.values().stream()
                .sorted(Comparator.comparingInt(ServiceDefinition::getConfigOrder))
                .map(ServiceDefinition::toService)
                .toArray(Service[]::new);
    }

    @Override
    public Service[] listServicesOrderedByDependencies() {
        return services.values().stream()
                .sorted(this::compareServices)
                .map(ServiceDefinition::toService)
                .toArray(Service[]::new);
    }

    @Override
    public Service[] listKubernetesServicesOrderedByDependencies() {
        return services.values().stream()
                .sorted(this::compareServices) // dunno why, but if I sort after filtering, it's not working (copare not called)
                .filter(ServiceDefinition::isKubernetes)
                .map(ServiceDefinition::toService)
                .toArray(Service[]::new);
    }

    @Override
    public int compareServices(ServiceDefinition one, ServiceDefinition other) {

        // kubernetes services are always last
        if (one.isKubernetes() && !other.isKubernetes()) {
            return 1;
        }
        if (!one.isKubernetes() && other.isKubernetes()) {
            return -1;
        }

        // need to browse dependencies
        if (one.hasInDependencyTree(other, this)) {
            return 1;
        }
        if (other.hasInDependencyTree(one, this)) {
            return -1;
        }

        // compare based on dependencies
        return Integer.compare(one.getRelevantDependenciesCount(), other.getRelevantDependenciesCount());

    }

    @Override
    public int compareServices(Service servOne, Service servOther) {

        ServiceDefinition one = getServiceDefinition(servOne);
        ServiceDefinition other = getServiceDefinition(servOther);

        return compareServices(one, other);
    }

    @Override
    public Collection<Service> getDependentServices(Service service) {
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

    @Override
    public ServiceDefinition getKubeMasterServiceDef() {
        return kubeMasterServiceDef;
    }

    @Override
    public ServiceDefinition getKubeSlaveServiceDef() {
        return kubeSlaveServiceDef;
    }

    private Set<Service> getDependentServicesInner(Service service, Set<Service> currentSet) {
        if (currentSet.contains(service)) {
            return new HashSet<>();
        }
        List<Service> directDependentList = services.values().stream()
                .filter(it -> it.dependsOn(service))
                .map(ServiceDefinition::toService)
                .collect(Collectors.toList());
        currentSet.addAll(directDependentList);
        for (Service depService: directDependentList) {
            currentSet.addAll(getDependentServicesInner(depService, currentSet));
        }
        return currentSet;
    }

}

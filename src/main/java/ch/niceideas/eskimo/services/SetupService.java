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

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.MessageLogger;
import ch.niceideas.eskimo.model.Service;
import ch.niceideas.eskimo.model.SetupCommand;
import ch.niceideas.eskimo.utils.ReturnStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class SetupService {

    private static final Logger logger = Logger.getLogger(SetupService.class);

    public static final String ESKIMO_PACKAGES_VERSIONS_JSON = "eskimo_packages_versions.json";
    public static final String TEMP_DOWNLOAD_SUFFIX = "__temp_download";
    public static final String DOCKER_TEMPLATE_PREFIX = "docker_template_";
    public static final String MESOS_PREFIX = "eskimo_";
    public static final String DOWNLOAD_FLAG = "download";
    public static final String BUILD_FLAG = "build";
    public static final String TAR_GZ_EXTENSION = ".tar.gz";
    public static final String NO_DOWNLOAD_IN_SNAPSHOT_ERROR = "Downloading packages is not supported on development version (SNAPSHOT)";

    private static final Pattern imageFileNamePattern = Pattern.compile("("+DOCKER_TEMPLATE_PREFIX+"|eskimo_)[a-zA-Z\\-]+_([a-zA-Z0-9_\\.]+)_([0-9]+)\\.tar\\.gz");

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SystemService systemService;

    @Autowired
    private SystemOperationService systemOperationService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ApplicationStatusService applicationStatusService;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    private String storagePathConfDir = System.getProperty("user.dir");

    @Value("${system.packageDistributionPath}")
    private String packageDistributionPath = "./packages_distrib";

    @Value("${setup.packagesDevPath}")
    private String packagesDevPath = "./packages_dev";

    @Value("${setup.packagesToBuild}")
    private String packagesToBuild = "base-eskimo,ntp,zookeeper,gluster,elasticsearch,cerebro,kibana,logstash,prometheus,grafana,kafka,kafka-manager,mesos-master,spark,flink,zeppelin,marathon";

    @Value("${setup.mesosPackages}")
    private String mesosPackages = "mesos-debian,mesos-redhat";

    @Value("${setup.packagesDownloadUrlRoot}")
    private String packagesDownloadUrlRoot = "https://niceideas.ch/eskimo/";

    @Value("${setup.temporaryBuildFolder}")
    private String temporaryBuildFolder = "/tmp";

    @Value("${build.version}")
    private String buildVersion = "DEV-SNAPSHOT";

    private String configStoragePathInternal = null;

    /** For tests */
    void setBuildVersion (String buildVersion) {
        this.buildVersion = buildVersion;
    }
    public void setConfigStoragePathInternal(String configStoragePathInternal) {
        this.configStoragePathInternal = configStoragePathInternal;
    }
    void setPackageDistributionPath(String packageDistributionPath) {
        this.packageDistributionPath = packageDistributionPath;
    }
    void setPackagesToBuild (String packagesToBuild) {
        this.packagesToBuild = packagesToBuild;
    }
    void setMesosPackages (String mesosPackages) {
        this.mesosPackages = mesosPackages;
    }
    void setSystemService (SystemService systemService) {
        this.systemService = systemService;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
    void setServicesDefinition (ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    void setApplicationStatusService (ApplicationStatusService applicationStatusService) {
        this.applicationStatusService = applicationStatusService;
    }
    void setPackagesDevPathForTests (String packagesDevPathForTest) {
        this.packagesDevPath = packagesDevPathForTest;
    }
    void setSystemOperationService (SystemOperationService systemOperationService) {
        this.systemOperationService = systemOperationService;
    }
    void setOperationsMonitoringService (OperationsMonitoringService operationsMonitoringService) {
        this.operationsMonitoringService = operationsMonitoringService;
    }

    void setStoragePathConfDir (String storagePathConfDir) {
        this.storagePathConfDir = storagePathConfDir;
    }
    String getStoragePathConfDir() { return storagePathConfDir; }

    @PostConstruct
    public void init() {
        configStoragePathInternal = readConfigStoragePath();
    }

    public String getConfigStoragePath() throws SetupException {
        if (StringUtils.isBlank(configStoragePathInternal)) {
            throw new SetupException ("Application is not initialized properly. Missing file 'storagePath.conf' in backend working directory.");
        }
        return configStoragePathInternal;
    }

    public String getPackagesDownloadUrlRoot() {
        return packagesDownloadUrlRoot;
    }

    String readConfigStoragePath() {
        // First read config storage path
        File entryFile = new File(storagePathConfDir + "/storagePath.conf");
        if (!entryFile.exists()) {
            logger.warn ("Application is not initialized properly. Missing file 'storagePath.conf' in backend working directory.");
            return null;
        }
        try {
            return FileUtils.readFile(entryFile).trim();
        } catch (FileException e) {
            logger.error(e, e);
            return null;
        }
    }

    public void ensureSetupCompleted() throws SetupException {

        // 1. Ensure config Storage path is set
        getConfigStoragePath();

        // 2. Ensure setup config is stored
        try {
            configurationService.loadSetupConfig();
        } catch (FileException e) {
            throw new SetupException ("Application is not initialized properly. File 'config.conf' cannot be read");
        }

        // 3. Ensure all services are downloaded / built

        File packagesDistribFolder = new File (packageDistributionPath);
        if (!packagesDistribFolder.exists()) {
            throw new SetupException ("Packages dev scripts folder doesn't exist : " + packageDistributionPath);
        }

        Set<String> missingServices = new HashSet<>();

        findMissingPackages(packagesDistribFolder, missingServices);

        // 4. Ensure mesos is properly downloaded / built
        findMissingMesos(packagesDistribFolder, missingServices);

        if (!missingServices.isEmpty()) {
            List<String> missingServicesList = new ArrayList<>(missingServices);
            Collections.sort(missingServicesList);
            throw new SetupException ("Following services are missing and need to be downloaded or built " + String.join(", ", missingServicesList));
        }
    }

    void findMissingMesos(File packagesDistribFolder, Set<String> missingServices) {
        for (String mesosPackage : mesosPackages.split(",")) {
            if (Arrays.stream(Objects.requireNonNull(packagesDistribFolder.listFiles()))
                    .noneMatch(file ->
                            file.getName().contains(mesosPackage)
                                    && !file.getName().contains(TEMP_DOWNLOAD_SUFFIX)
                                    && file.getName().endsWith(TAR_GZ_EXTENSION)
                                    && file.getName().startsWith(MESOS_PREFIX) )) {
                missingServices.add(mesosPackage);
            }
        }
    }

    void findMissingPackages(File packagesDistribFolder, Set<String> missingServices) {
        for (String service : packagesToBuild.split(",")) {
            if (Arrays.stream(Objects.requireNonNull(packagesDistribFolder.listFiles()))
                    .noneMatch(file ->
                            file.getName().startsWith("docker_template")
                                    && file.getName().contains("_"+service+"_")
                                    && !file.getName().contains(TEMP_DOWNLOAD_SUFFIX)
                                    && file.getName().endsWith(TAR_GZ_EXTENSION))) {
                missingServices.add(service);
            }
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public SetupCommand saveAndPrepareSetup(String configAsString) throws SetupException {

        logger.info("Got config : " + configAsString);

        try {

            JsonWrapper setupConfigJSON = configurationService.createSetupConfigAndSaveStoragePath(configAsString);

            String configStoragePath = (String) setupConfigJSON.getValueForPath("setup_storage");
            if (StringUtils.isBlank(configStoragePath)) {
                throw new SetupException ("config Storage path cannot be empty.");
            }
            configStoragePathInternal = configStoragePath;

            String sshKeyContent = (String) setupConfigJSON.getValueForPath("content-ssh-key");
            if (StringUtils.isBlank(sshKeyContent)) {
                throw new SetupException("Provided SSH key is empty");
            }

            // save config

            File privateKeyFile = new File(getConfigStoragePath() + "/privateKey");
            FileUtils.writeFile(privateKeyFile, sshKeyContent.replaceAll("\r\n", "\n"));

            if (!privateKeyFile.setExecutable(false, false)) {
                logger.debug("Coudln't remove world executable flag from key file. Moving on.");
            }
            if (!privateKeyFile.setWritable(false, false)) {
                logger.debug("Coudln't remove world writable flag from key file. Moving on.");
            }
            if (!privateKeyFile.setWritable(true, true)) {
                logger.debug("Coudln't add user writabke flag from key file. Moving on.");
            }
            if (!privateKeyFile.setReadable (false, false)) {
                logger.debug("Coudln't remove world readable flag from key file. Moving on.");
            }
            if (!privateKeyFile.setReadable(true, true)) {
                logger.debug("Coudln't add user readable flag from key file. Moving on.");
            }

            configurationService.saveSetupConfig(configAsString);

            return SetupCommand.create(setupConfigJSON, this);

        } catch (JSONException | FileException e) {
            logger.error(e, e);
            notificationService.addError("\nerror : "
                    + e.getMessage());
            throw new SetupException(e);
        }
    }

    public Pair<String,String> parseVersion(String name) {

        Matcher matcher = imageFileNamePattern.matcher(name);
        if (!matcher.matches()) {
            logger.warn ("File " + name + " doesn't match expected packqage image file name pattern");
            return null;
        }

        return new Pair<>(matcher.group(2), matcher.group(3));

    }

    public String findLastPackageFile(String prefix, String packageName) {

        File packagesDistribFolder = new File (packageDistributionPath);
        if (!packagesDistribFolder.exists()) {
            throw new IllegalStateException(packageDistributionPath + " doesn't exist");
        }

        Pair<File, Pair<String, String>> lastVersion = findLastVersion(prefix, packageName, packagesDistribFolder);

        if (lastVersion.getKey() == null) {
            throw new IllegalStateException("No package image found for " + packageName);
        }

        return lastVersion.getKey().getName();
    }

    Pair<File, Pair<String, String>> findLastVersion(String prefix, String packageName, File packagesDistribFolder) {

        List<File> imageFiles = Arrays.stream(Objects.requireNonNull(packagesDistribFolder.listFiles()))
                .filter(file -> file.getName().contains(prefix) && file.getName().contains("_" + packageName + "_"))
                .collect(Collectors.toList());

        File lastVersionFile = null;
        Pair<String, String> lastFileVersion = null;
        for (File imageFile : imageFiles) {
            Pair<String, String> imageVersion = parseVersion (imageFile.getName());
            if (imageVersion != null) {
                if (lastVersionFile == null) {
                    lastVersionFile = imageFile;
                    lastFileVersion = imageVersion;
                } else {
                    if (compareVersion (imageVersion, lastFileVersion) > 0) {
                        lastVersionFile = imageFile;
                        lastFileVersion = imageVersion;
                    }
                }
            }
        }

        return new Pair<>(lastVersionFile, lastFileVersion);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void prepareSetup (
            JsonWrapper setupConfig,
            Set<String> downloadPackages, Set<String> buildPackage, Set<String> downloadMesos, Set<String> buildMesos, Set<String> packageUpdate)
            throws SetupException {

        File packagesDistribFolder = new File (packageDistributionPath);
        if (!packagesDistribFolder.exists()) {
            packagesDistribFolder.mkdirs();
        }

        JsonWrapper packagesVersion = null;
        try {
            packagesVersion = loadRemotePackagesVersionFile();
        } catch (SetupException | JSONException e) {
            logger.warn (e.getMessage());
            logger.debug(e, e);
        }

        // 1. Find out about missing packages
        String servicesOrigin = (String) setupConfig.getValueForPath("setup-services-origin");
        if (StringUtils.isEmpty(servicesOrigin) || servicesOrigin.equals(BUILD_FLAG)) { // for services default is build

            findMissingPackages(packagesDistribFolder, buildPackage);

        } else {

            if (applicationStatusService.isSnapshot(buildVersion)) {
                throw new SetupException(NO_DOWNLOAD_IN_SNAPSHOT_ERROR);
            }

            if (packagesVersion == null) {
                throw new SetupException("Could not download latest package definition file from " + packagesDownloadUrlRoot);
            }

            Set<String> missingServices = new HashSet<>();
            findMissingPackages(packagesDistribFolder, missingServices);

            fillInPackages(downloadPackages, packagesVersion, missingServices);

        }

        // 2. Find out about missing mesos distrib
        String mesosOrigin = (String) setupConfig.getValueForPath("setup-mesos-origin");
        if (StringUtils.isEmpty(mesosOrigin) || mesosOrigin.equals(DOWNLOAD_FLAG)) { // for mesos default is download

            if (applicationStatusService.isSnapshot(buildVersion)) {
                throw new SetupException(NO_DOWNLOAD_IN_SNAPSHOT_ERROR);
            }

            if (packagesVersion == null) {
                throw new SetupException("Could not download latest package definition file from " + packagesDownloadUrlRoot);
            }

            Set<String> missingServices = new HashSet<>();

            findMissingMesos(packagesDistribFolder, missingServices);

            fillInPackages(downloadMesos, packagesVersion, missingServices);

        } else {
            findMissingMesos(packagesDistribFolder, buildMesos);
        }

        // 3. Find out about upgrades
        if (!applicationStatusService.isSnapshot(buildVersion)
                && StringUtils.isNotEmpty(servicesOrigin) && servicesOrigin.equals(DOWNLOAD_FLAG) // for services default is build
                && packagesVersion != null) {
            Set<String> updates = new HashSet<>();

            for (String imageName : packagesToBuild.split(",")) {

                Pair<File, Pair<String, String>> lastVersion = findLastVersion(DOCKER_TEMPLATE_PREFIX, imageName, packagesDistribFolder);
                Pair<String, String> lastVersionValues = lastVersion.getValue();

                if (lastVersionValues != null) {

                    String newSoftwareVersion = (String) packagesVersion.getValueForPath(imageName + ".software");
                    String newDistributionVersion = (String) packagesVersion.getValueForPath(imageName + ".distribution");

                    if (compareVersion (new Pair<> (newSoftwareVersion, newDistributionVersion),
                            lastVersionValues) > 0) {
                        updates.add(imageName);
                    }
                }
            }
            fillInPackages(packageUpdate, packagesVersion, updates);
        }

    }

    void fillInPackages(Set<String> downloadPackages, JsonWrapper packagesVersion, Set<String> missingServices) {
        for (String packageName : missingServices) {

            String softwareVersion = (String) packagesVersion.getValueForPath(packageName+".software");
            String distributionVersion = (String) packagesVersion.getValueForPath(packageName+".distribution");

            downloadPackages.add(packageName+"_"+softwareVersion+"_"+distributionVersion);
        }
    }

    protected JsonWrapper loadRemotePackagesVersionFile() throws SetupException{
        try {
            File tempPackagesVersionFile = File.createTempFile(ESKIMO_PACKAGES_VERSIONS_JSON, "temp_download");

            URL downloadUrl = new URL(packagesDownloadUrlRoot + "/" + ESKIMO_PACKAGES_VERSIONS_JSON);

            dowloadFile(new MessageLogger() {
                @Override
                public void addInfo(String message) {
                    // ignored
                }

                @Override
                public void addInfo(String[] messages) {
                    // ignored
                }
            }, tempPackagesVersionFile, downloadUrl, "");

            JsonWrapper packagesVersion = new JsonWrapper(FileUtils.readFile(tempPackagesVersionFile));
            Files.delete(tempPackagesVersionFile.toPath());
            return packagesVersion;
        } catch (IOException | FileException e) {
            logger.error (e, e);
            throw new SetupException(e);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public String applySetup(SetupCommand setupCommand) {

        boolean success = false;
        try {

            operationsMonitoringService.operationsStarted(setupCommand);

            JsonWrapper setupConfig = setupCommand.getRawSetup();

            File packagesDistribFolder = new File (packageDistributionPath);
            if (!packagesDistribFolder.exists()) {
                packagesDistribFolder.mkdirs();
            }

            // only build or download if not already done !!!

            // 1. Start with services
            File packagesDevFile = new File (packagesDevPath);
            if (!packagesDevFile.exists()) {
                throw new SetupException ("Packages dev scripts folder doesn't exist : " + packagesDevPath);
            }

            String servicesOrigin = (String) setupConfig.getValueForPath("setup-services-origin");

            Set<String> missingPackages = new HashSet<>();
            findMissingPackages(packagesDistribFolder, missingPackages);

            JsonWrapper packagesVersion = null;

            List<String> sortedServices = Arrays.stream(servicesDefinition.listAllServices())
                    .map(serviceName -> servicesDefinition.getService(serviceName))
                    .filter(service -> missingPackages.contains(service.getImageName()))
                    .sorted((one, other) -> servicesDefinition.compareServices(one, other))
                    .map(Service::getImageName)
                    .distinct()
                    .collect(Collectors.toList());

            // this one cannot be added by services
            if (missingPackages.contains("base-eskimo")) {
                sortedServices.add(0, "base-eskimo");
            }

            if (!missingPackages.isEmpty()) {
                if (StringUtils.isEmpty(servicesOrigin) || servicesOrigin.equals(BUILD_FLAG)) { // for services default is build

                    for (String packageName : sortedServices) {
                        buildPackage(packageName);
                    }

                } else {

                    if (applicationStatusService.isSnapshot(buildVersion)) {
                        throw new SetupException(NO_DOWNLOAD_IN_SNAPSHOT_ERROR);
                    }

                    packagesVersion = loadRemotePackagesVersionFile();

                    for (String packageName : sortedServices) {

                        String softwareVersion = (String) packagesVersion.getValueForPath(packageName + ".software");
                        String distributionVersion = (String) packagesVersion.getValueForPath(packageName + ".distribution");

                        downloadPackage(DOCKER_TEMPLATE_PREFIX + packageName + "_" + softwareVersion + "_" + distributionVersion + TAR_GZ_EXTENSION);
                    }
                }
            }

            // 2. Then focus on mesos

            Set<String> missingMesosPackages = new HashSet<>();
            findMissingMesos(packagesDistribFolder, missingMesosPackages);

            String mesosOrigin = (String) setupConfig.getValueForPath("setup-mesos-origin");

            if (!missingMesosPackages.isEmpty()) {
                if (StringUtils.isEmpty(mesosOrigin) || mesosOrigin.equals(DOWNLOAD_FLAG)) { // for mesos default is download

                    if (applicationStatusService.isSnapshot(buildVersion)) {
                        throw new SetupException(NO_DOWNLOAD_IN_SNAPSHOT_ERROR);
                    }

                    if (packagesVersion == null) {
                        packagesVersion = loadRemotePackagesVersionFile();
                    }

                    for (String mesosPackageName : missingMesosPackages) {

                        String softwareVersion = (String) packagesVersion.getValueForPath(mesosPackageName + ".software");
                        String distributionVersion = (String) packagesVersion.getValueForPath(mesosPackageName + ".distribution");

                        downloadPackage(MESOS_PREFIX + mesosPackageName + "_" + softwareVersion + "_" + distributionVersion + TAR_GZ_EXTENSION);
                    }

                } else {

                    // call script
                    for (String mesosPackageName : missingMesosPackages) {
                        buildPackage(mesosPackageName);
                    }
                }
            }

            // 3. Handle updates
            if (!applicationStatusService.isSnapshot(buildVersion)
                    && StringUtils.isNotEmpty(servicesOrigin)
                    && servicesOrigin.equals(DOWNLOAD_FLAG)) { // for services default is build

                for (String imageName : packagesToBuild.split(",")) {

                    Pair<File, Pair<String, String>> lastVersion = findLastVersion(DOCKER_TEMPLATE_PREFIX, imageName, packagesDistribFolder);
                    Pair<String, String> lastVersionValues = lastVersion.getValue();

                    if (lastVersionValues != null) {

                        if (packagesVersion == null) {
                            packagesVersion = loadRemotePackagesVersionFile();
                        }

                        String newSoftwareVersion = (String) packagesVersion.getValueForPath(imageName + ".software");
                        String newDistributionVersion = (String) packagesVersion.getValueForPath(imageName + ".distribution");

                        if (compareVersion (new Pair<> (newSoftwareVersion, newDistributionVersion),
                                lastVersionValues) > 0) {
                            downloadPackage(DOCKER_TEMPLATE_PREFIX + imageName + "_" + newSoftwareVersion + "_" + newDistributionVersion + TAR_GZ_EXTENSION);
                        }
                    }
                }
            }

            success = true;
            return "{\"status\": \"OK\"}";

        } catch (JSONException | SetupException | ServiceDefinitionException | NodesConfigurationException e) {
            logger.error(e, e);
            return ReturnStatusHelper.createErrorStatus (e);

        } finally {
            operationsMonitoringService.operationsFinished (success);
        }
    }

    public int compareVersion(Pair<String, String> first, Pair<String, String> second) {

        if (first == null) {
            if (second == null) {
                throw new IllegalArgumentException("Both versions are null");
            } else {
                return -1;
            }
        }
        if (second == null) {
            return 1;
        }

        int softwareVersionComparison = compareSoftwareVersion(first.getKey(), second.getKey());
        if (softwareVersionComparison != 0) {
            return softwareVersionComparison;
        }

        return Integer.valueOf (first.getValue()).compareTo(Integer.valueOf(second.getValue()));
    }

    public int compareSoftwareVersion (String firstVersion, String secondVersion) {

        String[] unitsFirst = firstVersion.split("[,.\\-_]");
        String[] unitsSecond = secondVersion.split("[,.\\-_]");

        for (int i = 0; i < Math.max(unitsFirst.length, unitsSecond.length); i++) {

            String unitFirst = i < unitsFirst.length ? unitsFirst[i] : null;
            String unitSecond = i < unitsSecond.length ? unitsSecond[i] : null;

            if (unitFirst == null) {
                return -1;
            }
            if (unitSecond == null) {
                return 1;
            }

            if (StringUtils.isIntegerValue(unitFirst) && StringUtils.isIntegerValue(unitSecond)) {
                int comparison = Integer.valueOf(unitFirst).compareTo(Integer.valueOf(unitSecond));
                if (comparison != 0) {
                    return comparison;
                }
            } else {
                int comparison = unitFirst.compareTo(unitSecond);
                if (comparison != 0) {
                    return comparison;
                }
            }
        }

        return 0;
    }

    protected void downloadPackage(String fileName) throws SetupException {
        if (!operationsMonitoringService.isInterrupted()) {
            try {
                systemOperationService.applySystemOperation(
                        new SetupCommand.SetupOperationId(SetupCommand.TYPE_DOWNLOAD, fileName),
                        ml -> {

                            File targetFile = new File(packageDistributionPath + "/" + fileName);

                            String downloadUrlString = packagesDownloadUrlRoot + "/" + fileName;
                            URL downloadUrl = new URL(downloadUrlString);

                            File tempFile = new File(targetFile.getAbsolutePath() + TEMP_DOWNLOAD_SUFFIX);

                            if (targetFile.exists()) {
                                ml.addInfo(fileName + " is already downloaded");
                            } else {

                                dowloadFile(ml, tempFile, downloadUrl, "Downloading image "+ fileName + " ...");

                                FileUtils.delete(targetFile);
                                if (!tempFile.renameTo(targetFile)) {
                                    throw new SystemException("Failed to rename " + tempFile.getName() + " to " + targetFile.getName());
                                }
                            }
                        }, null);
            } catch (SystemException e) {
                logger.error(e, e);
                throw new SetupException(e);
            }
        }
    }

    protected void dowloadFile(MessageLogger ml, File destinationFile, URL downloadUrl, String message) throws IOException {
        // download mesos using full java solution, no script (don't want dependency on system script for this)
        try (ReadableByteChannel readableByteChannel = Channels.newChannel(downloadUrl.openStream())) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(destinationFile)) {
                try (FileChannel fileChannel = fileOutputStream.getChannel()) {

                    ml.addInfo(message);
                    fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                }
            }
        }
    }

    protected void buildPackage(String image) throws SetupException {

        if (!operationsMonitoringService.isInterrupted()) {

            try {
                systemOperationService.applySystemOperation(
                        new SetupCommand.SetupOperationId(SetupCommand.TYPE_BUILD, image),
                        ml -> {
                            try {

                                File tempScript = File.createTempFile("tmp_build_script_" + image, ".sh");
                                FileUtils.writeFile(tempScript, "#!/bin/bash\n" +
                                        "export BUILD_TEMP_FOLDER=" + temporaryBuildFolder +"\n" +
                                        "bash " + packagesDevPath + "/build.sh -n " + image);

                                ml.addInfo(ProcessHelper.exec(new String[]{
                                        "bash",
                                        tempScript.getAbsolutePath()
                                }, true));
                            } catch (ProcessHelper.ProcessHelperException | FileException e) {
                                logger.debug(e, e);
                                ml.addInfo(e.getMessage());
                                throw new ProcessHelper.ProcessHelperException("build.sh script execution for " + image + " failed.");
                            }
                        },
                        null);
            } catch (SystemException e) {
                logger.error(e, e);
                throw new SetupException(e);
            }
        }
    }

}

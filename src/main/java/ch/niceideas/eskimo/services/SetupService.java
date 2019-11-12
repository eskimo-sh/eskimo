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
import ch.niceideas.eskimo.model.SetupCommand;
import ch.niceideas.eskimo.utils.ErrorStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class SetupService {

    private static final Logger logger = Logger.getLogger(SetupService.class);
    public static final String ESKIMO_PACKAGES_VERSIONS_JSON = "eskimo_packages_versions.json";

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SystemService systemService;

    @Autowired
    private SystemOperationService systemOperationService;

    @Value("${system.packageDistributionPath}")
    private String packageDistributionPath = "./packages_distrib";

    @Value("${system.packagesDevPath}")
    private String packagesDevPath = "./packages_dev";

    @Value("${system.packagesToBuild}")
    private String packagesToBuild = "base-eskimo,ntp,zookeeper,gluster,gdash,elasticsearch,cerebro,kibana,logstash,prometheus,grafana,kafka,kafka-manager,mesos-master,spark,zeppelin";

    @Value("${system.mesosPackages}")
    private String mesosPackages = "niceideas_mesos-debian-1.7.2.tar.gz,niceideas_mesos-redhat-1.7.2.tar.gz";

    @Value("${system.packagesDownloadUrlRoot}")
    private String packagesDownloadUrlRoot = "https://niceideas.ch/eskimo/";

    private String configStoragePathInternal = null;

    /** For tests */
    void setConfigStoragePathInternal(String configStoragePathInternal) {
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

    private String readConfigStoragePath() {
        // First read config storage path
        String currentDir = System.getProperty("user.dir");
        File entryFile = new File(currentDir + "/storagePath.conf");
        if (!entryFile.exists()) {
            logger.warn ("Application is not initialized properly. Missing file 'storagePath.conf' in backend working directory.");
            return null;
        }
        BufferedReader reader =null;
        try {
            reader = new BufferedReader(new FileReader(entryFile));
            return reader.readLine();
        } catch (IOException e) {
            logger.error(e, e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}; // ignored
            }
        }
    }

    public void ensureSetupCompleted() throws SetupException {

        // 1. Ensure config Storage path is set
        getConfigStoragePath();

        // 2. Ensure setup config is stored
        try {
            loadSetupConfig();
        } catch (FileException e) {
            throw new SetupException ("Application is not initialized properly. File 'config.conf' cannot be read");
        }

        // 3. Ensure all services are downloaded / built

        File packagesDistribFolder = new File (packageDistributionPath);
        if (!packagesDistribFolder.exists()) {
            throw new SetupException ("Packages dev scripts folder doesn't exist : " + packageDistributionPath);
        }

        Set<String> missingServices = new HashSet<>();

        findMissingServices(packagesDistribFolder, missingServices);

        // 4. Ensure mesos is properly downloaded / built
        findMissingMesos(packagesDistribFolder, missingServices);

        if (!missingServices.isEmpty()) {
            List<String> missingServicesList = new ArrayList<String>(missingServices);
            Collections.sort(missingServicesList);
            throw new SetupException ("Following services are missing and need to be downloaded or built " + String.join(", ", missingServicesList));
        }
    }

    void findMissingMesos(File packagesDistribFolder, Set<String> missingServices) {
        if (Arrays.stream(packagesDistribFolder.listFiles())
                .noneMatch(file ->
                        file.getName().contains("_mesos-")
                                && file.getName().contains("debian")
                                && !file.getName().contains("__temp_download"))) {
            missingServices.add("mesos-debian");
        }
        if (Arrays.stream(packagesDistribFolder.listFiles())
                .noneMatch(file ->
                        file.getName().contains("_mesos-")
                                && file.getName().contains("redhat")
                                && !file.getName().contains("__temp_download"))) {
            missingServices.add("mesos-redhat");
        }
    }

    void findMissingServices(File packagesDistribFolder, Set<String> missingServices) {
        for (String service : packagesToBuild.split(",")) {
            if (Arrays.stream(packagesDistribFolder.listFiles())
                    .noneMatch(file ->
                            file.getName().contains("docker_template")
                                    && file.getName().contains("_"+service+"_")
                                    && !file.getName().contains("__temp_download"))) {
                missingServices.add(service);
            }
        }
    }

    public String loadSetupConfig() throws FileException, SetupException {
        File configFile = new File(getConfigStoragePath() + "/config.json");
        if (!configFile.exists()) {
            //return ErrorStatusHelper.createClearStatus("missing");
            throw new SetupException ("Application is not initialized properly. Missing file 'config.conf' system configuration");
        }

        return FileUtils.readFile(configFile);
    }

    public SetupCommand saveAndPrepareSetup(String configAsString) throws SetupException {

        logger.info("Got config : " + configAsString);

        try {
            JsonWrapper setupConfigJSON = new JsonWrapper(configAsString);

            // First thing first : save storage path
            String configStoragePath = (String) setupConfigJSON.getValueForPath("setup_storage");
            if (StringUtils.isBlank(configStoragePath)) {
                throw new SetupException ("config Storage path cannot be empty.");
            }
            configStoragePathInternal = configStoragePath;

            String currentDir = System.getProperty("user.dir");
            File entryFile = new File(currentDir + "/storagePath.conf");
            FileUtils.writeFile(entryFile, configStoragePathInternal);

            File storagePath = new File(configStoragePathInternal);
            if (!storagePath.exists()) {

                storagePath.mkdirs();

                if (!storagePath.exists()) {
                    throw new SetupException("Path \"" + configStoragePath + "\" doesn't exist and couldn't be created.");
                }
            }
            if (!storagePath.canWrite()) {
                String username = System.getProperty("user.name");
                throw new SetupException("User " + username + " cannot write in path " + getConfigStoragePath() + " doesn't exist.");
            }

            String sshKeyContent = (String) setupConfigJSON.getValueForPath("content-ssh-key");
            if (StringUtils.isBlank(sshKeyContent)) {
                throw new SetupException("Provided SSH key is empty");
            }

            // save config

            File privateKeyFile = new File(getConfigStoragePath() + "/privateKey");
            FileUtils.writeFile(privateKeyFile, sshKeyContent.replaceAll("\r\n", "\n"));
            privateKeyFile.setExecutable(false, false);
            privateKeyFile.setWritable(false, false);
            privateKeyFile.setWritable(true, true);
            privateKeyFile.setReadable (false, false);
            privateKeyFile.setReadable(true, true);

            File configFile = new File(getConfigStoragePath() + "/config.json");
            FileUtils.writeFile(configFile, configAsString);

            return SetupCommand.create(setupConfigJSON, systemService, this);

        } catch (JSONException | FileException e) {
            logger.error(e, e);
            messagingService.addLines("\nerror : "
                    + e.getMessage());
            throw new SetupException(e);
        }
    }

    private Pattern imageFileNamePattern = Pattern.compile("docker_template_[a-zA-Z\\-]+_([a-zA-Z0-9_\\.]+)_([0-9]+)\\.tar\\.gz");

    Pair<String,String> parseVersion(String name) {

        Matcher matcher = imageFileNamePattern.matcher(name);
        if (!matcher.matches()) {
            logger.warn ("File " + name + " doesn't match expected packqage image file name pattern");
            return null;
        }

        return new Pair<>(matcher.group(1), matcher.group(2));

    }

    public String findLastPackageFile(String imageName) {

        File packagesDistribFolder = new File (packageDistributionPath);
        if (!packagesDistribFolder.exists()) {
            throw new IllegalStateException(packageDistributionPath + " doesn't exist");
        }

        List<File> imageFiles = Arrays.stream(packagesDistribFolder.listFiles())
                .filter(file -> file.getName().contains("docker_template_") && file.getName().contains(imageName))
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
                    if (imageVersion.getKey().compareTo(lastFileVersion.getKey()) > 0) {
                        lastVersionFile = imageFile;
                        lastFileVersion = imageVersion;
                    } else if (imageVersion.getKey().compareTo(lastFileVersion.getKey()) == 0) {
                        if (imageVersion.getValue().compareTo(lastFileVersion.getValue()) > 0) {
                            lastVersionFile = imageFile;
                            lastFileVersion = imageVersion;
                        }
                    }
                }
            }
        }

        if (lastVersionFile == null) {
            throw new IllegalStateException("No package image found for " + imageName);
        }

        return lastVersionFile.getName();
    }


    public void prepareSetup (
            JsonWrapper setupConfig,
            Set<String> downloadPackages, Set<String> buildPackage, Set<String> downloadMesos, Set<String> buildMesos)
            throws JSONException, SetupException {

        File packagesDistribFolder = new File (packageDistributionPath);
        if (!packagesDistribFolder.exists()) {
            packagesDistribFolder.mkdirs();
        }

        String servicesOrigin = (String) setupConfig.getValueForPath("setup-services-origin");
        if (StringUtils.isEmpty(servicesOrigin) || servicesOrigin.equals("build")) { // for services default is build

            findMissingServices(packagesDistribFolder, buildPackage);

        } else {

            Set<String> missingServices = new HashSet<>();
            findMissingServices(packagesDistribFolder, missingServices);

            try {

                JsonWrapper packagesVersion = loadRemotePackagesVersionFile();

                for (String packageName : missingServices) {

                    String softwareVersion = (String) packagesVersion.getValueForPath(packageName+".software");
                    String distributionVersion = (String) packagesVersion.getValueForPath(packageName+".distribution");

                    downloadPackages.add(packageName+"_"+softwareVersion+"_"+distributionVersion);
                }

            } catch (IOException | FileException e) {
                logger.error (e, e);
                throw new SetupException(e);
            }
        }

        String mesosOrigin = (String) setupConfig.getValueForPath("setup-mesos-origin");
        if (StringUtils.isEmpty(mesosOrigin) || mesosOrigin.equals("download")) { // for mesos default is download

            Set<String> missingServices = new HashSet<>();

            findMissingMesos(packagesDistribFolder, missingServices);

            for (String mesosPackageName : missingServices) {
                for (String mesosPackage : mesosPackages.split(",")) {

                    if (mesosPackage.contains(mesosPackageName)) {
                        downloadMesos.add(mesosPackage);
                    }
                }
            }

        } else {

            findMissingMesos(packagesDistribFolder, buildMesos);
        }

    }

    protected JsonWrapper loadRemotePackagesVersionFile() throws IOException, FileException {
        File tempPackagesVersionFile = File.createTempFile("eskimo_packages_versions.json", "temp_download");

        URL downloadUrl = new URL(packagesDownloadUrlRoot + "/" + ESKIMO_PACKAGES_VERSIONS_JSON);

        dowloadFile(new StringBuilder(), tempPackagesVersionFile, downloadUrl, "");

        JsonWrapper packagesVersion = new JsonWrapper(FileUtils.readFile(tempPackagesVersionFile));
        tempPackagesVersionFile.delete();
        return packagesVersion;
    }


    public String applySetup(JsonWrapper setupConfig) throws SetupException, JSONException {

        boolean success = false;
        systemService.setProcessingPending();
        try {

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

            Set<String> missingServices = new HashSet<>();
            findMissingServices(packagesDistribFolder, missingServices);

            if (StringUtils.isEmpty(servicesOrigin) || servicesOrigin.equals("build")) { // for services default is build

                for (String packageName : missingServices) {
                    buildPackage(packageName);
                }

            } else {

                JsonWrapper packagesVersion = null;
                try {
                    packagesVersion = loadRemotePackagesVersionFile();
                } catch (IOException | FileException e) {
                    logger.error (e, e);
                    throw new SetupException(e);
                }

                for (String packageName : missingServices) {

                    String softwareVersion = (String) packagesVersion.getValueForPath(packageName+".software");
                    String distributionVersion = (String) packagesVersion.getValueForPath(packageName+".distribution");

                    String fileName = "docker_template_" + packageName + "_" + softwareVersion + "_" + distributionVersion + ".tar.gz";

                    downloadPackage(fileName);
                }

            }

            // 2. Then focus on mesos

            Set<String> missingMesosPackages = new HashSet<>();
            findMissingMesos(packagesDistribFolder, missingMesosPackages);

            String mesosOrigin = (String) setupConfig.getValueForPath("setup-mesos-origin");
            if (StringUtils.isEmpty(mesosOrigin) || mesosOrigin.equals("download")) { // for mesos default is download

                for (String mesosPackageName : missingMesosPackages) {
                    for (String mesosPackage : mesosPackages.split(",")) {

                        if (mesosPackage.contains(mesosPackageName)) {
                            downloadPackage(mesosPackage);
                        }
                    }
                }

            } else {

                // call script
                buildPackage("mesos-debian");
                buildPackage("mesos-rhel");
            }

            success = true;
            return "{\"status\": \"OK\"}";

        } catch (JSONException | SetupException e) {
            logger.error(e, e);
            messagingService.addLines("\nerror : "
                    + e.getMessage());
            return ErrorStatusHelper.createErrorStatus (e);

        } finally {
            systemService.setLastOperationSuccess (success);
            systemService.releaseProcessingPending();
        }
    }

    protected void downloadPackage(String fileName) throws SetupException {
        if (!systemService.isInterrupted()) {
            try {
                systemOperationService.applySystemOperation("Downloading of package " + fileName,
                        (builder) -> {

                            File targetFile = new File(packageDistributionPath + "/" + fileName);

                            String downloadUrlString = packagesDownloadUrlRoot + "/" + fileName;
                            URL downloadUrl = new URL(downloadUrlString);

                            File tempFile = new File(targetFile.getAbsolutePath() + "__temp_download");

                            if (targetFile.exists()) {
                                builder.append(fileName);
                                builder.append(" is already downloaded");
                            } else {

                                dowloadFile(builder, tempFile, downloadUrl, "Downloading image "+ fileName + " ...");

                                FileUtils.delete(targetFile);
                                tempFile.renameTo(targetFile);
                            }
                        }, null);
            } catch (SystemException e) {
                logger.error(e, e);
                throw new SetupException(e);
            }
        }
    }

    protected void dowloadFile(StringBuilder builder, File destinationFile, URL downloadUrl, String message) throws IOException {
        // download mesos using full java solution, no script (don't want dependency on system script for this)
        ReadableByteChannel readableByteChannel = Channels.newChannel(downloadUrl.openStream());

        FileOutputStream fileOutputStream = new FileOutputStream(destinationFile);
        FileChannel fileChannel = fileOutputStream.getChannel();

        builder.append(message);

        fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        fileChannel.close();
    }

    protected void buildPackage(String image) throws SetupException {

        if (!systemService.isInterrupted()) {

            try {
                systemOperationService.applySystemOperation("Building of package " + image,
                        (builder) -> {
                            String[] setupScript = ArrayUtils.concatAll(new String[]{"bash", packagesDevPath + "/build.sh", "-n", image});//, dependencies);
                            try {
                                builder.append(ProcessHelper.exec(setupScript, true));
                            } catch (ProcessHelper.ProcessHelperException e) {
                                logger.debug(e, e);
                                builder.append(e.getMessage());
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

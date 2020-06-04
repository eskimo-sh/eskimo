package ch.niceideas.eskimo.utils;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.services.SetupService;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class PackageVersionGenerator {

    private static final Logger logger = Logger.getLogger(EncodedPasswordGenerator.class);

    public static void main (String[] args) {

        if (args.length == 0) {
            logger.error("Expected working directory as first argument");
            System.exit(-1);
        }

        if (args[0] == null || args[0].length() == 0) {
            logger.error("Expected working directory as first argument");
            System.exit(-2);
        }

        new PackageVersionGenerator().generatePackageVersionFile(args[0]);
    }

    void generatePackageVersionFile(String workingDirString) {
        File workingDirectory = new File (workingDirString);
        if (!workingDirectory.exists()) {
            logger.error("Directory " + workingDirString + " doesn't exist.");
            System.exit(-3);
        }

        SetupService setupService = new SetupService();

        Map<String, Pair<String, String>> packages = new HashMap<>();

        for (File packageFile : workingDirectory.listFiles()) {

            String fileName = packageFile.getName();

            if ((fileName.startsWith(SetupService.DOCKER_TEMPLATE_PREFIX)
                    || fileName.startsWith(SetupService.MESOS_PREFIX)
                ) && fileName.endsWith("tar.gz")) {

                String packageName;
                if (fileName.startsWith(SetupService.DOCKER_TEMPLATE_PREFIX)) {
                    packageName = fileName.substring(
                            SetupService.DOCKER_TEMPLATE_PREFIX.length(),
                            fileName.indexOf("_", SetupService.DOCKER_TEMPLATE_PREFIX.length() + 1));
                } else {
                    packageName = fileName.substring(
                            SetupService.MESOS_PREFIX.length(),
                            fileName.indexOf("_", SetupService.MESOS_PREFIX.length() + 1));
                }

                Pair<String,String> version = setupService.parseVersion(fileName);

                Pair<String,String> previousVersion = packages.computeIfAbsent(packageName, k -> version);
                if (setupService.compareVersion(version, previousVersion) > 0) {
                    packages.put (packageName, version);
                }

            }
        }

        JsonWrapper resultJSON = new JsonWrapper("{}");
        for (Map.Entry<String, Pair<String, String>> packageEntry : packages.entrySet()) {
            String packageName = packageEntry.getKey();
            Pair<String, String> version = packageEntry.getValue();
            resultJSON.setValueForPath(packageName + ".software", version.getKey());
            resultJSON.setValueForPath(packageName + ".distribution", version.getValue());
        }

        //System.err.println (resultJSON.getFormattedValue());

        File resultFile = new File (workingDirectory, "eskimo_packages_versions.json");
        try {
            FileUtils.writeFile(resultFile, resultJSON.getFormattedValue());
        } catch (FileException e) {
            logger.error (e, e);
            throw new RuntimeException(e);
        }

    }
}

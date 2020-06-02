package ch.niceideas.eskimo.utils;

import ch.niceideas.eskimo.services.SetupService;
import org.apache.log4j.Logger;

import java.io.File;

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
    }
}

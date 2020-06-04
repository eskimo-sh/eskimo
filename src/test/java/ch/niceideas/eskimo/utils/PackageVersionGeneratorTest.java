package ch.niceideas.eskimo.utils;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class PackageVersionGeneratorTest {

    private File tmpFolder = null;

    @Before
    public void setUp() throws Exception {
        tmpFolder = File.createTempFile("packagetest", "distrib");
        assertTrue(tmpFolder.delete());
        assertTrue (tmpFolder.mkdir());

        assertTrue (new File (tmpFolder, "docker_template_base-eskimo_0.2_1.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "docker_template_base-eskimo_0.2_2.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "docker_template_base-eskimo_0.2_3.tar.gz").createNewFile());

        assertTrue (new File (tmpFolder, "docker_template_flink_1.9.1_1.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "docker_template_flink_1.10.0_1.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "docker_template_flink_1.10.1_1.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "docker_template_flink_1.10.1_2.tar.gz").createNewFile());

        assertTrue (new File (tmpFolder, "docker_template_kibana_7.6.2_1.tar.gz").createNewFile());

        assertTrue (new File (tmpFolder, "docker_template_spark_2.4.5_2.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "docker_template_spark_2.4.6_2.tar.gz").createNewFile());

        assertTrue (new File (tmpFolder, "eskimo_mesos-debian_1.8.1_1.tar.gz").createNewFile());
        assertTrue (new File (tmpFolder, "eskimo_mesos-debian_1.8.1_2.tar.gz").createNewFile());

        assertTrue (new File (tmpFolder, "eskimo_mesos-suse_1.8.1_1.tar.gz").createNewFile());
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.delete(tmpFolder);
    }

    @Test
    public void testPackageGeneration() throws Exception {
        new PackageVersionGenerator().generatePackageVersionFile(tmpFolder.getAbsolutePath());
        checkGeneratedFile();
    }

    void checkGeneratedFile() throws FileException {
        String generatedPackageFile = FileUtils.readFile(new File(tmpFolder, "eskimo_packages_versions.json"));
        assertEquals("{\n" +
                "    \"flink\": {\n" +
                "        \"software\": \"1.10.1\",\n" +
                "        \"distribution\": \"2\"\n" +
                "    },\n" +
                "    \"spark\": {\n" +
                "        \"software\": \"2.4.6\",\n" +
                "        \"distribution\": \"2\"\n" +
                "    },\n" +
                "    \"base-eskimo\": {\n" +
                "        \"software\": \"0.2\",\n" +
                "        \"distribution\": \"3\"\n" +
                "    },\n" +
                "    \"mesos-debian\": {\n" +
                "        \"software\": \"1.8.1\",\n" +
                "        \"distribution\": \"2\"\n" +
                "    },\n" +
                "    \"kibana\": {\n" +
                "        \"software\": \"7.6.2\",\n" +
                "        \"distribution\": \"1\"\n" +
                "    },\n" +
                "    \"mesos-suse\": {\n" +
                "        \"software\": \"1.8.1\",\n" +
                "        \"distribution\": \"1\"\n" +
                "    }\n" +
                "}", generatedPackageFile);
    }

    @Test
    public void testPackageGenerationFromMain() throws Exception {
        PackageVersionGenerator.main(new String[] {tmpFolder.getAbsolutePath()});
        checkGeneratedFile();
    }
}

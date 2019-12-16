package ch.niceideas.eskimo.utils;

import ch.niceideas.common.utils.FileUtils;
import jscover.report.BranchData;
import jscover.report.FileData;
import jscover.report.JSONDataMerger;
import jscover.report.lcov.LCovGenerator;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import static java.lang.String.format;

public class GenerateLCOV {

    public static final String SOURCE_DIRECTORY = "src/main/webapp";
    private static Logger logger = Logger.getLogger(GenerateLCOV.class);

    private static String jsCoverReportDir = "target/jscov-report";

    private static String mergedJsCoverReportDir = "target/jscov-report-merged";

    private static String lcovReportDir = "target/jscov-lcov";

    private static JSONDataMerger jsonDataMerger = new JSONDataMerger();

    private static LCovGenerator lCovGenerator = new LCovGenerator();

    public static void main (String[] args) {

        try {

            File reportDir = new File (jsCoverReportDir);
            if (!reportDir.exists()) {
                logger.warn(jsCoverReportDir + " doesn't exist");
                System.exit (-1);
            }

            SortedMap<String, FileData> total = new TreeMap<>();
            for (File reportSubdir : reportDir.listFiles()) {
                File reportFile = new File (reportSubdir, "jscoverage.json");
                if (!reportFile.exists()) {
                    logger.warn(reportFile + " doesn't exist");
                } else {

                    logger.info("Merging : " + reportFile);
                    String reportContent = FileUtils.readFile(reportFile);
                    total = jsonDataMerger.mergeJSONCoverageMaps(total, jsonDataMerger.jsonToMap(reportContent));
                }
            }

            String merged = GenerateLCOV.toJSON(total);
            File targetFile = new File (mergedJsCoverReportDir, "jscoverage.json");
            targetFile.getParentFile().mkdirs();
            FileUtils.writeFile(targetFile, merged);


            File lcovFile = new File(lcovReportDir, "jscover.lcov");
            lCovGenerator.saveData(jsonDataMerger.jsonToMap(merged).values(), SOURCE_DIRECTORY, lcovFile);

        } catch (Exception e) {
            logger.error(e, e);
        }

    }

    public static String toJSON(SortedMap<String, FileData> map) {
        StringBuilder json = new StringBuilder("{");
        int scriptCount = 0;
        for (String scriptURI : map.keySet()) {
            StringBuilder coverage = new StringBuilder();
            StringBuilder branchData = new StringBuilder();
            FileData coverageData = map.get(scriptURI);
            for (int i = 0; i < coverageData.getLines().size(); i++) {
                if (i > 0)
                    coverage.append(",");
                coverage.append(coverageData.getLines().get(i));
            }

            // Function Coverage (HA-CA)
            StringBuilder functions = new StringBuilder();
            for (int i = 0; i < coverageData.getFunctions().size(); i++) {
                if (i > 0)
                    functions.append(",");
                functions.append(coverageData.getFunctions().get(i));
            }

            addBranchData(branchData, coverageData);
            if (scriptCount++ > 0) {
                json.append(",");
            }

            StringBuilder scriptJSON = new StringBuilder(format("\"%s\":{", scriptURI));
            scriptJSON.append(format("\"lineData\":[%s]", coverage));
            if (functions.length() > 0)
                scriptJSON.append(format(",\"functionData\":[%s]", functions));
            if (branchData.length() > 0)
                scriptJSON.append(format(",\"branchData\":{%s}", branchData));
            scriptJSON.append("}");
            json.append(scriptJSON);
        }
        json.append("}");
        return json.toString();
    }

    static void addBranchData(StringBuilder branchData, FileData coverageData) {
        int count = 0;
        for (Integer i: coverageData.getBranchData().keySet()) {
            List<BranchData> conditions = coverageData.getBranchData().get(i);
            if (count++ > 0)
                branchData.append(",");
            branchData.append(format("\"%s\":[",i));
            addBranchConditions(branchData, conditions);
            branchData.append("]");
        }
    }

    static void addBranchConditions(StringBuilder branchData, List<BranchData> conditions) {
        for (int j = 0; j < conditions.size();  j++) {
            if (j > 0)
                branchData.append(",");
            BranchData branchObj = conditions.get(j);
            if (branchObj == null) {
                branchData.append("null");
            } else {
                String branchJSON = "{\"position\":%d,\"nodeLength\":%d,\"evalFalse\":%d,\"evalTrue\":%d}";
                branchData.append(format(branchJSON, branchObj.getPosition(), branchObj.getNodeLength(), branchObj.getEvalFalse(), branchObj.getEvalTrue()));
            }
        }
    }
}

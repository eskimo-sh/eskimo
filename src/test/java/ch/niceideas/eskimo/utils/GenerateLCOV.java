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

package ch.niceideas.eskimo.utils;

import ch.niceideas.common.utils.FileUtils;
import jscover.report.BranchData;
import jscover.report.FileData;
import jscover.report.JSONDataMerger;
import jscover.report.lcov.LCovGenerator;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import static java.lang.String.format;

public class GenerateLCOV {

    public static final String SOURCE_DIRECTORY = "src/main/webapp";
    private static final Logger logger = Logger.getLogger(GenerateLCOV.class);

    private static final String jsCoverReportDir = "target/jscov-report";

    private static final String mergedJsCoverReportDir = "target/jscov-report-merged";

    private static final String lcovReportDir = "target/jscov-lcov";

    private static final JSONDataMerger jsonDataMerger = new JSONDataMerger();

    private static final LCovGenerator lCovGenerator = new LCovGenerator();

    public static void main (String[] args) {

        try {

            File reportDir = new File (jsCoverReportDir);
            if (!reportDir.exists()) {
                logger.warn(jsCoverReportDir + " doesn't exist");
                System.exit (-1);
            }

            SortedMap<String, FileData> total = new TreeMap<>();
            for (File reportSubdir : Objects.requireNonNull(reportDir.listFiles())) {
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

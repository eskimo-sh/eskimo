/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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


package ch.niceideas.eskimo.html.infra;

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.eskimo.utils.GenerateLCOV;
import jscover.Main;
import jscover.report.FileData;
import jscover.report.JSONDataMerger;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class CoverageServer implements TestResourcesServer {

    private static final Logger logger = Logger.getLogger(CoverageServer.class);

    private String className = null;

    private static String jsCoverReportDir = "target/jscov-report";
    private static String[] jsCoverArgs = new String[]{
            "-ws",
            "--document-root=.",
            "--port=9001",
            //"--no-branch",
            //"--no-function",
            //"--no-instrument=example/lib",
            "--log=INFO",
            "--report-dir=" + jsCoverReportDir
    };

    private List<String> coverages = new ArrayList<>();

    private JSONDataMerger jsonDataMerger = new JSONDataMerger();

    private Thread server;

    private Main main = null;

    public void startServer(String className) throws Exception {
        this.className = className;

        main = new Main();
        server = new Thread(() -> main.runMain(jsCoverArgs));
        server.start();
    }

    public void stopServer() throws Exception {
        main.stop();

        File targetFile = new File(jsCoverReportDir + "/" + className, "jscoverage.json");
        targetFile.getParentFile().mkdirs();
        FileUtils.writeFile(targetFile, mergeJSON());
    }

    public void postTestMethodHook(JsRunner runner) throws Exception {
        runner.js("window.jscoverFinished = false;");
        runner.js("jscoverage_report('', function(){window.jscoverFinished=true;});");

        // FIXME I have failing tests with Awaitility !?!
            /*
            await().atMost(MAX_WAIT_TIME_SECS * (isCoverageRun() ? 2 : 1)  , TimeUnit.SECONDS).until(
                    () -> (Boolean) js("window.jscoverFinished").getJavaScriptResult());

            */

        int attempt = 0;
        while ((!(Boolean) (runner.js("window.jscoverFinished"))) && attempt < 10) {
            logger.debug("Waiting for coverage report to be written ...");
            Thread.sleep(500);
            attempt++;
        }

        String json = (String) (runner.js("jscoverage_serializeCoverageToJSON();"));
        coverages.add(json);
    }

    private String mergeJSON() {
        SortedMap<String, FileData> total = new TreeMap<>();
        for (String json : coverages) {
            total = jsonDataMerger.mergeJSONCoverageMaps(total, jsonDataMerger.jsonToMap(json));
        }
        return GenerateLCOV.toJSON(total);
    }
}
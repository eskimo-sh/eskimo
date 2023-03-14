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


package ch.niceideas.eskimo.html.infra;

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.eskimo.utils.ActiveWaiter;
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
    public static final String SOURCES_FOLDER = "src/main/webapp/";

    private String className = null;

    private final static String jsCoverReportDir = "target/jscov-report";

    private final List<String> coverages = new ArrayList<>();

    private final JSONDataMerger jsonDataMerger = new JSONDataMerger();

    private final String[] jsCoverArgs = new String[]{
            "-ws",
            "--document-root=.",
            "--port=" + TestResourcesServer.LOCAL_TEST_SERVER_PORT,
            //"--no-branch",
            //"--no-function",
            "--no-instrument=src/main/webapp/scripts/vendor/bootstrap-5.2.0.js",
            "--log=INFO",
            "--report-dir=" + jsCoverReportDir
    };

    private Main main = null;

    @Override
    public void startServer(String className) {
        this.className = className;

        main = new Main();
        Thread server = new Thread(() -> main.runMain(jsCoverArgs));
        server.start();
    }

    @Override
    public void stopServer() throws Exception {
        main.stop();

        File targetFile = new File(jsCoverReportDir + "/" + className, "jscoverage.json");
        if (!targetFile.getParentFile().mkdirs()) {
            logger.error ("Directory creation for '" + targetFile  + "' returned false. Some further errors might me expected.");
        }
        FileUtils.writeFile(targetFile, mergeJSON());
    }

    @Override
    public void postTestMethodHook(JsRunner runner) {
        runner.js("window.jscoverFinished = false;");
        runner.js("jscoverage_report('', function(){window.jscoverFinished=true;});");

        ActiveWaiter.wait(() -> ((Boolean) (runner.js("return window.jscoverFinished"))));

        String json = (String) (runner.js("return jscoverage_serializeCoverageToJSON();"));

        // hacky hack to remove leading slashes from file names
        json = json.replace("/" + SOURCES_FOLDER, SOURCES_FOLDER);
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

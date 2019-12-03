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

package ch.niceideas.eskimo.model;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.services.SetupService;
import ch.niceideas.eskimo.services.SystemService;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class SetupCommand implements Serializable {

    private static final Logger logger = Logger.getLogger(SetupCommand.class);

    private final JsonWrapper rawSetup;

    private String packageDownloadUrl;

    private final Set<String> downloadPackages;
    private final Set<String> buildPackage;
    private final Set<String> downloadMesos;
    private final Set<String> buildMesos;
    private final Set<String> packageUpdates;

    public static SetupCommand create (
            JsonWrapper rawSetup,
            SystemService systemService,
            SetupService setupService) throws SetupException {

        Set<String> downloadPackages = new HashSet<String>();
        Set<String> buildPackage = new HashSet<String>();
        Set<String> downloadMesos = new HashSet<String>();
        Set<String> buildMesos = new HashSet<String>();
        Set<String> packageUpdates = new HashSet<String>();

        setupService.prepareSetup(rawSetup, downloadPackages, buildPackage, downloadMesos, buildMesos, packageUpdates);

        SetupCommand retCommand = new SetupCommand(rawSetup, setupService.getPackagesDownloadUrlRoot(),
                downloadPackages, buildPackage, downloadMesos, buildMesos, packageUpdates);

        return retCommand;

    }

    SetupCommand(JsonWrapper rawSetup, String packageDownloadUrl,
                 Set<String> downloadPackages,
                 Set<String> buildPackage,
                 Set<String> downloadMesos,
                 Set<String> buildMesos,
                 Set<String> packageUpdates) {
        this.rawSetup = rawSetup;
        this.packageDownloadUrl = packageDownloadUrl;
        this.downloadPackages = downloadPackages;
        this.buildPackage = buildPackage;
        this.downloadMesos = downloadMesos;
        this.buildMesos = buildMesos;
        this.packageUpdates = packageUpdates;
    }

    public JSONObject toJSON () {
        return new JSONObject(new HashMap<String, Object>() {{
            put("packageDownloadUrl", packageDownloadUrl);
            put("downloadPackages", new JSONArray(downloadPackages));
            put("buildPackage", new JSONArray(buildPackage));
            put("downloadMesos", new JSONArray(downloadMesos));
            put("buildMesos", new JSONArray(buildMesos));
            put("packageUpdates", new JSONArray(packageUpdates));
            put("none", buildMesos.size() + downloadMesos.size() + buildPackage.size() + downloadPackages.size() + packageUpdates.size() <= 0);
        }});
    }

    public JsonWrapper getRawSetup() {
        return rawSetup;
    }

    public Set<String> getDownloadPackages() {
        return downloadPackages;
    }

    public Set<String> getBuildPackage() {
        return buildPackage;
    }

    public Set<String> getDownloadMesos() {
        return downloadMesos;
    }

    public Set<String> getBuildMesos() {
        return buildMesos;
    }

    public Set<String> getPackageUpdates() {
        return packageUpdates;
    }
}

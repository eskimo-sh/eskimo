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

package ch.niceideas.eskimo.test.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.model.SetupCommand;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.services.SetupService;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.util.Set;

@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile("no-cluster")
public class SetupServiceTestImpl implements SetupService {

    private boolean setupError = false;
    private boolean setupCompleted = false;

    public void setSetupError() {
        this.setupError = true;
        this.setupCompleted = false;
    }

    public void setSetupCompleted() {
        this.setupError = false;
        this.setupCompleted = true;
    }

    @Override
    public String getPackagesToBuild() {
        return null;
    }

    @Override
    public String getConfigStoragePath() throws SetupException {
        return null;
    }

    @Override
    public String getPackagesDownloadUrlRoot() {
        return null;
    }

    @Override
    public void ensureSetupCompleted() throws SetupException {
        if (setupError) {
            throw new SetupException("Test Error");
        }
    }

    @Override
    public SetupCommand saveAndPrepareSetup(String configAsString) throws SetupException {
        return null;
    }

    @Override
    public Pair<String, String> parseVersion(String name) {
        return null;
    }

    @Override
    public String findLastPackageFile(String prefix, String packageName) {
        return null;
    }

    @Override
    public void prepareSetup(JsonWrapper setupConfig, Set<String> downloadPackages, Set<String> buildPackage, Set<String> downloadKube, Set<String> buildKube, Set<String> packageUpdate) throws SetupException {

    }

    @Override
    public String applySetup(SetupCommand setupCommand) {
        return null;
    }

    @Override
    public int compareVersion(Pair<String, String> first, Pair<String, String> second) {
        return 0;
    }

    @Override
    public int compareSoftwareVersion(String firstVersion, String secondVersion) {
        return 0;
    }

    @Override
    public String getStoragePathConfDir() {
        return null;
    }
}

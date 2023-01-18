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

package ch.niceideas.eskimo.model;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.services.SetupService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

import static ch.niceideas.eskimo.model.SimpleOperationCommand.standardizeOperationMember;

@Data
public class SetupCommand implements JSONOpCommand {

    public static final String TYPE_DOWNLOAD = "Download";
    public static final String TYPE_BUILD = "Build";
    public static final String BASE_ESKIMO_PACKAGE = "base-eskimo";
    private final JsonWrapper rawSetup;

    private final String packageDownloadUrl;

    private final List<String> downloadPackages;
    private final List<String> buildPackage;
    private final List<String> downloadKube;
    private final List<String> buildKube;
    private final List<String> packageUpdates;

    public static SetupCommand create (
            JsonWrapper rawSetup,
            SetupService setupService,
            ServicesDefinition servicesDefinition) throws SetupException {

        Set<String> downloadPackages = new HashSet<>();
        Set<String> buildPackage = new HashSet<>();
        Set<String> downloadKube = new HashSet<>();
        Set<String> buildKube = new HashSet<>();
        Set<String> packageUpdates = new HashSet<>();

        setupService.prepareSetup(rawSetup, downloadPackages, buildPackage, downloadKube, buildKube, packageUpdates);

        return new SetupCommand(rawSetup, setupService.getPackagesDownloadUrlRoot(),
                sortPackage (downloadPackages, servicesDefinition),
                sortPackage (buildPackage, servicesDefinition),
                sortKubePackage(downloadKube, servicesDefinition),
                sortKubePackage(buildKube, servicesDefinition),
                sortPackage (packageUpdates, servicesDefinition));
    }

    public static List<String> sortKubePackage(Set<String> missingKubePackages, ServicesDefinition servicesDefinition) {
        List<String> sortedKubePackages = new ArrayList<>(missingKubePackages);
        sortedKubePackages.sort(String::compareTo);
        return sortedKubePackages;
    }

    public static List<String> sortPackage(Set<String> missingPackages, ServicesDefinition servicesDefinition) {

        List<String> missingServices = missingPackages.stream()
                .map (SetupCommand::getServiceName)
                .collect(Collectors.toList());

        List<String> sortedServices = Arrays.stream(servicesDefinition.listAllServices())
                .map(servicesDefinition::getService)
                .filter(service -> missingServices.contains(service.getImageName()))
                .sorted(servicesDefinition::compareServices)
                .map(Service::getImageName)
                .distinct()
                .collect(Collectors.toList());

        // this one cannot be added by services
        if (missingServices.contains(BASE_ESKIMO_PACKAGE) && !sortedServices.contains(BASE_ESKIMO_PACKAGE)) {
            sortedServices.add(0, BASE_ESKIMO_PACKAGE);
        }

        return sortedServices.stream()
                .map (service -> getPackageName (missingPackages, service))
                .collect(Collectors.toList());
    }

    private static String getPackageName (Set<String> missingPackages, String serviceName) {
        if (missingPackages.contains(serviceName)) {
            return serviceName;
        }

        return missingPackages.stream()
                .filter(packageName -> packageName.contains(serviceName + "_"))
                .findAny().orElseThrow(IllegalStateException::new);
    }

    private static String getServiceName (String packageName) {
        if (StringUtils.isBlank(packageName)) {
            return null;
        }
        if (!packageName.contains("_")) {
            return packageName;
        }
        return packageName.substring(0, packageName.indexOf("_"));
    }

    SetupCommand(JsonWrapper rawSetup, String packageDownloadUrl,
                 List<String> downloadPackages,
                 List<String> buildPackage,
                 List<String> downloadKube,
                 List<String> buildKube,
                 List<String> packageUpdates) {
        this.rawSetup = rawSetup;
        this.packageDownloadUrl = packageDownloadUrl;
        this.downloadPackages = downloadPackages;
        this.buildPackage = buildPackage;
        this.downloadKube = downloadKube;
        this.buildKube = buildKube;
        this.packageUpdates = packageUpdates;
    }

    public JSONObject toJSON () {
        return new JSONObject(new HashMap<String, Object>() {{
            put("packageDownloadUrl", packageDownloadUrl);
            put("downloadPackages", new JSONArray(downloadPackages));
            put("buildPackage", new JSONArray(buildPackage));
            put("downloadKube", new JSONArray(downloadKube));
            put("buildKube", new JSONArray(buildKube));
            put("packageUpdates", new JSONArray(packageUpdates));
            put("none", buildKube.size() + downloadKube.size() + buildPackage.size() + downloadPackages.size() + packageUpdates.size() <= 0);
        }});
    }

    @Override
    public boolean hasChanges() {
        return !downloadPackages.isEmpty()
                || !buildPackage.isEmpty()
                || !downloadKube.isEmpty()
                || !buildKube.isEmpty()
                || !packageUpdates.isEmpty();
    }

    @Override
    public List<SetupOperationId> getAllOperationsInOrder(OperationsContext context) {

        List<SetupOperationId> allOpList = new ArrayList<>();

        downloadPackages.stream()
                .map (pack -> new SetupOperationId(TYPE_DOWNLOAD, pack))
                .forEach(allOpList::add);

        buildPackage.stream()
                .map (pack -> new SetupOperationId(TYPE_BUILD, pack))
                .forEach(allOpList::add);

        downloadKube.stream()
                .map (pack -> new SetupOperationId(TYPE_DOWNLOAD, pack))
                .forEach(allOpList::add);

        buildKube.stream()
                .map (pack -> new SetupOperationId(TYPE_BUILD, pack))
                .forEach(allOpList::add);

        packageUpdates.stream()
                .map (pack -> new SetupOperationId(TYPE_DOWNLOAD, pack))
                .forEach(allOpList::add);

        return allOpList;
    }

    @Data
    @RequiredArgsConstructor
    public static class SetupOperationId implements OperationId {

        private final String type;
        private final String service;

        public boolean isOnNode(String node) {
            return false;
        }

        public boolean isSameNode(OperationId other) {
            return false;
        }

        public String getMessage() {
            return type + " of package " + getService();
        }

        @Override
        public String toString() {
            return standardizeOperationMember (type)
                    + "_"
                    + standardizeOperationMember (service);
        }
    }
}

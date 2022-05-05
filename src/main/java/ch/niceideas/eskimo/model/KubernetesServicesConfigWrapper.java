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

package ch.niceideas.eskimo.model;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.StringUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public class KubernetesServicesConfigWrapper extends JsonWrapper implements Serializable {

    public static final String INSTALL_FLAG = "_install";
    public static final String CPU_FLAG = "_cpu";
    public static final String RAM_FLAG = "_ram";

    public KubernetesServicesConfigWrapper(File statusFile) throws FileException {
        super(FileUtils.readFile(statusFile));
    }

    public static KubernetesServicesConfigWrapper empty() {
        return new KubernetesServicesConfigWrapper("{}");
    }

    public KubernetesServicesConfigWrapper(JSONObject json) {
        super(json);
    }

    public KubernetesServicesConfigWrapper(Map<String, Object> map) {
        super(new JSONObject(map));
    }

    public KubernetesServicesConfigWrapper(String jsonString) {
        super(jsonString);
    }

    public Collection<String> getEnabledServices() {
        return getRootKeys().stream()
                .filter(key -> key.contains(INSTALL_FLAG))
                .filter(key -> getValueForPath(key).equals("on"))
                .map(key -> key.substring(0, key.indexOf(INSTALL_FLAG)))
                .collect(Collectors.toList());
    }

    public String getCpuSetting(String service) {
        return getValueForPathAsString(service + CPU_FLAG);
    }

    public String getRamSetting(String service) {
        return getValueForPathAsString(service + RAM_FLAG);
    }

    public boolean isServiceInstallRequired(String service) {
        return StringUtils.isNotBlank(getValueForPathAsString(service + INSTALL_FLAG))
                && getValueForPath(service + INSTALL_FLAG).equals("on");
    }

    public boolean hasEnabledServices() {
        return getRootKeys().stream()
                .filter(key -> key.contains(INSTALL_FLAG))
                .anyMatch(key -> getValueForPath(key).equals("on"));
    }

    public boolean isDifferentConfig(KubernetesServicesConfigWrapper previousConfig, String service) {

        String prevCpu = previousConfig.getCpuSetting(service);
        String prevRam = previousConfig.getRamSetting(service);

        String curCpu = getCpuSetting(service);
        String curRam = getRamSetting(service);

        if (StringUtils.isBlank(curCpu)) {
            if (StringUtils.isNotBlank(prevCpu)) {
                return true;
            }
        } else {
            if (StringUtils.isBlank(prevCpu)) {
                return true;
            } else if (!prevCpu.equals(curCpu)) {
                return true;
            }
        }

        if (StringUtils.isBlank(curRam)) {
            if (StringUtils.isNotBlank(prevRam)) {
                return true;
            }
        } else {
            if (StringUtils.isBlank(prevRam)) {
                return true;
            } else if (!prevRam.equals(curRam)) {
                return true;
            }
        }

        return false;
    }
}
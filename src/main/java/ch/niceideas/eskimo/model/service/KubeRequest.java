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



package ch.niceideas.eskimo.model.service;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.services.ServiceDefinitionException;
import lombok.Data;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.regex.Pattern;

@Data
public class KubeRequest {

    public static final Pattern KUBE_REQUEST_CPU_RE = Pattern.compile("[0-9\\.]+[m]{0,1}");
    public static final Pattern KUBE_REQUEST_RAM_RE = Pattern.compile("[0-9\\.]+[EPTGMk]{0,1}");

    private String cpu;
    private String ram;

    public void setCpu(String cpu) throws ServiceDefinitionException {
        if (KUBE_REQUEST_CPU_RE.matcher(cpu).matches()) {
            this.cpu = cpu;
        } else {
            throw new ServiceDefinitionException("Kubernetes request cpu '" + cpu + "' doesn't match expected pattern (number)[m]{0,1}");
        }
    }

    public void setRam(String ram) throws ServiceDefinitionException {
        if (KUBE_REQUEST_RAM_RE.matcher(ram).matches()) {
            this.ram = ram;
        } else {
            throw new ServiceDefinitionException("Kubernetes request ram '" + ram + "' doesn't match expected pattern (number)[EPTGMk]{0,1}");
        }
    }

    public JSONObject toJSON() {
        return new JSONObject(new HashMap<String, Object>() {{
            put("cpu", getCpu());
            put("ram", getRam());
        }});
    }
}

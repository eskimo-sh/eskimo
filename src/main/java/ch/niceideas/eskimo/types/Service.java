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


package ch.niceideas.eskimo.types;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode
public class Service implements Comparable<Service> {

    public static final Service NODE_ALIVE = new Service("Node Alive");
    public static final Service TOPOLOGY_ALL_NODES = new Service ("Topology (All Nodes)");
    public static final Service BASE_SYSTEM = new Service ("Base System");
    public static final Service SETTINGS = new Service ("settings");
    public static final Service NODE_ID_FIELD = new Service (NodesConfigWrapper.NODE_ID_FIELD);
    public static final Service TOPOLOGY_FLAG = new Service ("eskimo_topology");
    public static final Service SERVICES_SETTINGS_FLAG = new Service ("eskimo_services-settings");

    @Getter
    @Setter(AccessLevel.PROTECTED)
    @EqualsAndHashCode.Include
    private String name;

    protected Service() {}

    protected Service (String name) {
        this.name = name;
    }

    public static Service from(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Service name can't be blank");
        }
        if (name.equals(NODE_ALIVE.getName())) {
            throw new IllegalArgumentException("'" + NODE_ALIVE.getName() + "' dummy service should be obtained from static constant");
        }
        if (name.equals(TOPOLOGY_ALL_NODES.getName())) {
            throw new IllegalArgumentException("'" + TOPOLOGY_ALL_NODES.getName() + "' dummy service should be obtained from static constant");
        }
        if (name.equals(BASE_SYSTEM.getName())) {
            throw new IllegalArgumentException("'" + BASE_SYSTEM.getName() + "' dummy service should be obtained from static constant");
        }
        if (name.equals(TOPOLOGY_FLAG.getName())) {
            throw new IllegalArgumentException("'" + TOPOLOGY_FLAG.getName() + "' dummy service should be obtained from static constant");
        }
        if (name.equals(SERVICES_SETTINGS_FLAG.getName())) {
            throw new IllegalArgumentException("'" + SERVICES_SETTINGS_FLAG.getName() + "' dummy service should be obtained from static constant");
        }
        return new Service(name);
    }

    public String toString() {
        return name;
    }

    public String getEnv() {
        return name.toUpperCase().replace("-", "_");
    }

    @Override
    public int compareTo(Service o) {
        return getName().compareTo(o.getName());
    }
}

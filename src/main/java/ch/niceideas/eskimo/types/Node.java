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
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;

@EqualsAndHashCode
public final class Node implements Comparable<Node>, Serializable {

    private static final String KUBERNETES_NODE_TAG = "KUBERNETES_NODE";
    public static final Node KUBERNETES_NODE = new Node (KUBERNETES_NODE_TAG, KUBERNETES_NODE_TAG);

    private static final String KUBERNETES_FLAG_TAG = "(kubernetes)";
    public static final Node KUBERNETES_FLAG = new Node (KUBERNETES_FLAG_TAG, KUBERNETES_FLAG_TAG);

    private static final String KUBE_NA_FLAG_TAG = "KUBERNETES_NA";
    public static final Node KUBE_NA_FLAG = new Node (KUBE_NA_FLAG_TAG, KUBE_NA_FLAG_TAG);

    @Getter
    private final String name;

    @Getter
    private final String address;

    private Node (String name, String address) {
        this.name = name;
        this.address = address;
    }

    public static Node fromName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Node Name can't be blank");
        }
        if (name.equals(KUBERNETES_NODE.getName())) {
            return KUBERNETES_NODE;
        }
        if (name.equals(KUBERNETES_FLAG.getName())) {
            throw new IllegalArgumentException("'" + KUBERNETES_FLAG.getName() + "' dummy node should be obtained from static constant");
        }
        if (name.equals(KUBE_NA_FLAG.getName())) {
            throw new IllegalArgumentException("'" + KUBE_NA_FLAG.getName() + "' dummy node should be obtained from static constant");
        }
        return new Node (name, name.replace("-", "."));
    }

    public static Node fromAddress(String address) {
        if (StringUtils.isBlank(address)) {
            throw new IllegalArgumentException("Node Address can't be blank");
        }
        if (address.equals(KUBERNETES_NODE.getAddress())) {
            throw new IllegalArgumentException("'" + KUBERNETES_NODE.getAddress() + "' dummy node should be obtained from static constant");
        }
        if (address.equals(KUBERNETES_FLAG.getAddress())) {
            throw new IllegalArgumentException("'" + KUBERNETES_FLAG.getAddress() + "' dummy node should be obtained from static constant");
        }
        if (address.equals(KUBE_NA_FLAG.getAddress())) {
            throw new IllegalArgumentException("'" + KUBE_NA_FLAG.getAddress() + "' dummy node should be obtained from static constant");
        }
        return new Node (address.replace(".", "-"), address);
    }

    public String toString() {
        return address;
    }

    public String getEnv() {
        return getName().replace("-", "");
    }

    @Override
    public int compareTo(Node o) {
        return this.getName().compareTo(o.getName());
    }
}

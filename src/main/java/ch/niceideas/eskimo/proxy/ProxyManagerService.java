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

package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.services.ConnectionManagerException;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import ch.niceideas.eskimo.types.ServiceWebId;
import org.apache.hc.core5.http.HttpHost;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public interface ProxyManagerService {

    int LOCAL_PORT_RANGE_START = 39152;

    int PORT_GENERATION_MAX_ATTEMPT_COUNT = 10000;

    HttpHost getServerHost(ServiceWebId serviceId);

    String getServerURI(Service serviceName, String pathInfo);

    Node extractHostFromPathInfo(String pathInfo);

    List<ProxyTunnelConfig> getTunnelConfigForHost (Node host);

    void updateServerForService(Service service, Node runtimeNode) throws ConnectionManagerException;

    void removeServerForService(Service service, Node runtimeNode);

    Collection<ServiceWebId> getAllTunnelConfigKeys();

    ProxyTunnelConfig getTunnelConfig(ServiceWebId serviceId);

    /** get a port number from 49152 to 65535 */
    static int generateLocalPort() {
        int portNumber;
        int tryCount = 0;
        do {
            int randInc = ThreadLocalRandom.current().nextInt(65534 - LOCAL_PORT_RANGE_START);
            portNumber = LOCAL_PORT_RANGE_START + randInc;
            tryCount++;
        } while (isLocalPortInUse(portNumber) && tryCount < PORT_GENERATION_MAX_ATTEMPT_COUNT);
        if (tryCount >= PORT_GENERATION_MAX_ATTEMPT_COUNT) {
            throw new IllegalStateException();
        }
        return portNumber;
    }

    private static boolean isLocalPortInUse(int port) {
        try {
            // ServerSocket try to open a LOCAL port
            new ServerSocket(port).close();
            // local port can be opened, it's available
            return false;
        } catch(IOException e) {
            // local port cannot be opened, it's in use
            return true;
        }
    }
}

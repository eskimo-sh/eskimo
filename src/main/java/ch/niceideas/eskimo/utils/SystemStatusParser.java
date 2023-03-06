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

package ch.niceideas.eskimo.utils;

import ch.niceideas.eskimo.services.SSHCommandException;
import ch.niceideas.eskimo.services.SSHCommandService;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SystemStatusParser {

    private static final Logger logger = Logger.getLogger(SystemStatusParser.class);

    /**
     * Debug with
     *
     * sudo systemctl status --no-pager -al mesos-master zookeeper zeppelin ntp gluster mesos-agent logstash cerebro elasticsearch kafka-manager kafka spark-runtime kibana spark-console
     *
     * systemctl status -a 2>/dev/null | grep ● -A 5 | grep ".service" -A 5  | grep "Active:" -A 5 | grep logstash -A 5
     *
     * while (true) ; do sleep 2 ; echo -e "\n" ; date; systemctl status -a 2>/dev/null | grep ● -A 5 | grep ".service" -A 5  | grep "Active:" -A 5 | grep logstash -A 2; done
     */

    static final Pattern pattern = Pattern.compile(" *Active: ([^\\( ]+) \\(([^\\(\\)]+)\\).*");
    public static final String SERVICE_SUFFIX = ".service";

    private final Map<Service, String> serviceStatus = new HashMap<>();

    public SystemStatusParser (Node node, SSHCommandService sshCommandService, ServicesDefinition servicesDefinition)
            throws SSHCommandException {
        parse(sshCommandService, node, servicesDefinition);
    }

    void parse(SSHCommandService sshCommandService, Node node, ServicesDefinition servicesDefinition) throws SSHCommandException {

        String allServicesStatus = sshCommandService.runSSHScript(node,
                "sudo systemctl status --no-pager --no-block -al " + servicesDefinition.getAllServicesString() + " 2>/dev/null ", false);

        String[] contentLines = allServicesStatus.split("\n");
        for (int i = 0; i < contentLines.length; i++) {

            if (    (contentLines[i].startsWith("●")
                 || contentLines[i].startsWith("×")
                    || contentLines[i].startsWith("○"))
                    && contentLines[i].contains(SERVICE_SUFFIX)) {

                String foundService;
                if (contentLines[i].contains("●")) {
                    foundService = contentLines[i].substring(contentLines[i].indexOf('●') + 2, contentLines[i].indexOf(SERVICE_SUFFIX));
                } else if (contentLines[i].contains("○")) {
                    foundService = contentLines[i].substring(contentLines[i].indexOf('○') + 2, contentLines[i].indexOf(SERVICE_SUFFIX));
                } else { // then it's obligatory '×'
                    foundService = contentLines[i].substring(contentLines[i].indexOf('×') + 2, contentLines[i].indexOf(SERVICE_SUFFIX));
                }

                handleServiceFound (Service.from(foundService), i, contentLines);
            }
        }
    }

    private void handleServiceFound(Service service, int position, String[] contentLines) {

        // search for active flag
        for (int i = position + 1; i < position + 5; i++) {

            if (i < contentLines.length) {

                if (contentLines[i].contains("Loaded: ") && contentLines[i].contains("not-found")) {

                    serviceStatus.put(service, "NA");
                    break;

                } else if (contentLines[i].contains("Active: ")) {

                    Matcher matcher = pattern.matcher(contentLines[i]);
                    if (matcher.find()) {

                        String status = matcher.group(2);
                        logger.debug ("Found service status " + status + " for service " + service);
                        serviceStatus.put(service, status);
                    }
                }
            }
        }
    }

    public String getDebugLog () {
        StringBuilder builder = new StringBuilder();
        for(Map.Entry<Service, String> entry : serviceStatus.entrySet()) {
            builder.append(entry.getKey()).append(" : ").append(entry.getValue());
        }
        return builder.toString();
    }

    public String getServiceStatus(Service service) {
        return serviceStatus.computeIfAbsent(service, key -> "NA");
    }
}

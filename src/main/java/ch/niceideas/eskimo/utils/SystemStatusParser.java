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

    private final Map<String, String> serviceStatus = new HashMap<>();

    public SystemStatusParser (String content) {
        parse(content);
    }

    void parse(String content) {

        String[] contentLines = content.split("\n");
        for (int i = 0; i < contentLines.length; i++) {

            if (    (contentLines[i].startsWith("●")
                 || contentLines[i].startsWith("×")
                    || contentLines[i].startsWith("○"))
                    && contentLines[i].contains(".service")) {

                handleServiceFound (
                        contentLines[i].substring(
                                (contentLines[i].indexOf('●') > -1 ? contentLines[i].indexOf('●') :
                                        (contentLines[i].contains("○") ? contentLines[i].indexOf("○") : contentLines[i].indexOf('×'))) + 2,
                                contentLines[i].indexOf(".service")),
                        i,
                        contentLines);

            }
        }

    }

    private void handleServiceFound(String service, int position, String[] contentLines) {

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
        for(String service : serviceStatus.keySet()) {
            builder.append(service).append(" : ").append(serviceStatus.get(service));
        }
        return builder.toString();
    }

    public String getServiceStatus(String service) {
        if (serviceStatus.containsKey(service)) {
            return serviceStatus.get(service);
        }
        return "NA";
    }
}

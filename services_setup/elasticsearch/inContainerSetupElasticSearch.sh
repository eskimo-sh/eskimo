#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
# Author : eskimo.sh / https://www.eskimo.sh
#
# Eskimo is available under a dual licensing model : commercial and GNU AGPL.
# If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
# terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
# Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
# any later version.
# Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
# commercial license.
#
# Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Affero Public License for more details.
#
# You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
# see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
# Boston, MA, 02110-1301 USA.
#
# You can be released from the requirements of the license by purchasing a commercial license. Buying such a
# commercial license is mandatory as soon as :
# - you develop activities involving Eskimo without disclosing the source code of your own product, software,
#   platform, use cases or scripts.
# - you deploy eskimo as part of a commercial product, platform or software.
# For more information, please contact eskimo.sh at https://www.eskimo.sh
#
# The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
# Software.
#

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

echo "-- SETTING UP ELASTICSEARCH ----------------------------------------------------"

# These should be mounted properly by docker
#echo " - Creating elasticsearch data directory in /var/lib/elasticsearch"
#mkdir -p /var/lib/elasticsearch
#chown -R elasticsearch /var/lib/elasticsearch

#echo " - Creating elasticsearch PID file directory"
#mkdir -p /var/run/elasticsearch
#chown -R elasticsearch /var/run/elasticsearch

echo " - Simlinking elasticsearch logs to /var/log/"
rm -Rf /usr/local/lib/elasticsearch/logs
ln -s /var/log/elasticsearch /usr/local/lib/elasticsearch/logs

#echo " - Simlinking ES config to /usr/local/etc/elasticsearch"
#ln -s /usr/local/lib/elasticsearch/config /usr/local/etc/elasticsearch

echo " - Simlinking ES binaries and scripts to /usr/local/sbin"
ln -s /usr/local/lib/elasticsearch/bin/elasticsearch /usr/local/sbin/elasticsearch

echo " - Changing owner of important folders"
chown -R elasticsearch /usr/local/lib/elasticsearch/config

echo " - Adapting configuration in file elasticsearch.yml"
#get_ip_address
sed -i s/"#cluster.name: my-application"/"cluster.name: eskimo"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml
sed -i s/"#path.data: \/path\/to\/data"/"path.data: \/var\/lib\/elasticsearch"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml
sed -i s/"#path.logs: \/path\/to\/logs"/"path.logs: \/usr\/local\/lib\/elasticsearch\/logs"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml
sed -i s/"#bootstrap.memory_lock: true"/"bootstrap.memory_lock: false"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml
sed -i s/"#network.host: 192.168.0.1"/"network.host: 0.0.0.0"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml

sed -i s/"#action.destructive_requires_name: false"/"action.destructive_requires_name: false"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml

echo " - Creating eskimo specific configurations"
bash -c "echo -e \"\n# ------------------------------- Eskimo specific ------------------------------\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"

echo " - Addressing issue with multiple interfaces but only one global"
bash -c "echo -e \"\n# The following settings control the fault detection process\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"

# ES 8.x
if [[ $(grep -F "discovery.zen.minimum_master_nodes" /usr/local/lib/elasticsearch/config/elasticsearch.yml) == "" && \
      $(grep -F "gateway.recover_after_nodes" /usr/local/lib/elasticsearch/config/elasticsearch.yml) == "" ]]; then
    bash -c "echo \"discovery.find_peers_interval: 1s\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"
    bash -c "echo \"discovery.probe.connect_timeout: 80s\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"
    bash -c "echo \"discovery.request_peers_timeout: 5s\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"
    bash -c "echo \"cluster.fault_detection.follower_check.retry_count: 6\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"
    bash -c "echo \"cluster.fault_detection.leader_check.retry_count: 6\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"

    bash -c "echo -e \"\n#Disabling Security for now\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"
    bash -c "echo \"xpack.security.enabled: false\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"

else
# ES 6.x / ES 7.x
    bash -c "echo \"discovery.zen.fd.ping_interval: 1s\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"
    bash -c "echo \"discovery.zen.fd.ping_timeout: 60s\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"
    bash -c "echo \"discovery.zen.fd.ping_retries: 8\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"
fi

bash -c "echo -e \"\n# Esimo Default indices settings\" >> /usr/local/lib/elasticsearch/config/elasticsearch-index-defaults.properties"
bash -c "echo -e \"\n# Set the number of shards (splits) of an index (5 by default):\" >> /usr/local/lib/elasticsearch/config/elasticsearch-index-defaults.properties"
bash -c "echo \"index.number_of_shards=DEFAULT\" >> /usr/local/lib/elasticsearch/config/elasticsearch-index-defaults.properties"
bash -c "echo -e \"\n# Set the number of replicas (additional copies) of an index (0 by default - only master replica):\" >> /usr/local/lib/elasticsearch/config/elasticsearch-index-defaults.properties"
bash -c "echo \"index.number_of_replicas=DEFAULT\" >> /usr/local/lib/elasticsearch/config/elasticsearch-index-defaults.properties"

# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"




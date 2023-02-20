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

PROMETHEUS_USER_ID=$1
if [[ $PROMETHEUS_USER_ID == "" ]]; then
    echo " - Didn't get PROMETHEUS_USER_ID User ID as argument"
    exit 2
fi


echo "-- SETTING UP PROMETHEUS -----------------------------------------------------------"

echo " - Creating prometheus user (if not exist) in container"
set +e
prometheus_user_id=$(id -u kubernetes 2>/dev/null)
set -e
if [[ $prometheus_user_id == "" ]]; then
    useradd -u $PROMETHEUS_USER_ID prometheus
elif [[ $prometheus_user_id != $PROMETHEUS_USER_ID ]]; then
    echo "Docker PROMETHEUS USER ID is $PROMETHEUS_USER_ID while requested USER ID is $PROMETHEUS_USER_ID"
    exit 5
fi

echo " - Creating user prometheus home directory"
mkdir -p /home/prometheus
chown -R prometheus /home/prometheus

echo " - Enabling user to change config at runtime"
chown -R prometheus /usr/local/lib/prometheus/prometheus.yml
chmod 755 /usr/local/lib/prometheus/prometheus.yml

echo " - Adding kube-state-metrics scrape target"
cat >> /usr/local/lib/prometheus/prometheus.yml <<EOF

  - job_name: 'kube-state-metrics'
    honor_timestamps: true
    scrape_interval: 1m
    scrape_timeout: 1m
    metrics_path: /metrics
    scheme: http
    static_configs:
    - targets:
      - kube-state-metrics.kube-system.svc.cluster.eskimo:8080
EOF

echo " - Changing data storage to 777 (required by prometheus)"
sudo mkdir -p /var/lib/prometheus/data
chown -R prometheus /var/lib/prometheus/data
sudo chmod 777 /var/lib/prometheus/data
ln -s /var/lib/prometheus/data /usr/local/lib/prometheus/data


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"



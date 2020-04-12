#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

echo " - Injecting topology"
. /usr/local/sbin/inContainerInjectTopology.sh

echo " - Creating required directories (as marathon)"
mkdir -p /var/log/marathon/log

mkdir -p /var/lib/marathon/tmp

mkdir -p /var/log/marathon/docker_registry
mkdir -p /var/lib/marathon/docker_registry

echo " - Inject settings"
/usr/local/sbin/settingsInjector.sh marathon

echo " - Basic mesos configuration for mesos Scheduler (set env vars)"
# point to your libmesos.so if you use Mesos
export MESOS_NATIVE_JAVA_LIBRARY=/usr/local/lib/mesos/lib/libmesos.so
# Set external IP for Mesos Scheduler used by Flink / Marathon / etc. to reach mesos (required for mesos callback to succeed)
export LIBPROCESS_ADVERTISE_IP=$SELF_IP_ADDRESS

echo " - Starting Docker registry"
docker-registry serve /etc/docker_registry/config.yml > /var/log/marathon/docker_registry/docker_registry.log 2>&1 &

# TODO put in place some monitoring to kill container if docker registry goes down

echo " - Starting service"
/usr/local/lib/marathon/bin/marathon \
    --master zk://$ZOOKEEPER_IP_ADDRESS:2181/mesos \
    --zk zk://$ZOOKEEPER_IP_ADDRESS:2181/marathon  \
    --http_port 28080 \
    --metrics_prometheus \
    --hostname $SELF_IP_ADDRESS | tee /var/log/marathon/marathon.log


# --metrics_prometheus
# Enable the Prometheus reporter.

# --hostname
# The advertised hostname that is used for the communication with the Mesos master.
# The value is also stored in the persistent store so another standby host can redirect to the elected leader.

# --enable_features  <arg>
# A comma-separated list of features. Available features are:
# secrets - Enable support for secrets in Marathon (experimental),
# external_volumes - Enable external volumes support in Marathon,
# vips - Enable networking VIPs UI,
# gpu_resources - Enable support for GPU in Marathon (experimental),
# task_killing - Enable the optional TASK_KILLING state, available in Mesos 0.28 and later

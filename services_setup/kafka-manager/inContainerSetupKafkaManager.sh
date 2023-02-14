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

. /etc/eskimo_topology.sh


echo "-- SETTING UP KAFKA MANAGER-----------------------------------------------------"

echo " - Getting kafka user ID"
set +e
kafka_user_id=$(id -u kafka 2> kafka_install_log)
set -e
echo " - Found user with ID $kafka_user_id"
if [[ $kafka_user_id == "" ]]; then
    echo "Docker Kafka USER ID not found"
    exit 2
fi

echo " - Enabling kafka user to create kafka directories and chown them"
bash -c "echo \"spark  ALL = NOPASSWD: /bin/mkdir /var/lib/spark/tmp\" >> /etc/sudoers.d/spark"

bash -c "echo \"kafka  ALL = NOPASSWD: /bin/mkdir -p /var/run/kafka/kafka-manager\" >> /etc/sudoers.d/kafka"
bash -c "echo \"kafka  ALL = NOPASSWD: /bin/chown kafka /var/run/kafka/kafka-manager\" >> /etc/sudoers.d/kafka"
bash -c "echo \"kafka  ALL = NOPASSWD: /bin/mkdir -p /var/log/kafka/kafka-manager\" >> /etc/sudoers.d/kafka"
bash -c "echo \"kafka  ALL = NOPASSWD: /bin/chown kafka /var/log/kafka/kafka-manager\" >> /etc/sudoers.d/kafka"


echo " - Changing owner of /var/run/kafka-manager/"
sudo mkdir -p /var/run/kafka/kafka-manager
sudo chown -R kafka /var/run/kafka/kafka-manager

echo " - Symlinking logs to /var/log/kafka-manager/"
sudo mkdir -p /var/log/kafka/kafka-manager
sudo chown kafka /var/log/kafka/kafka-manager

sudo rm -Rf /usr/local/lib/kafka-manager/logs
sudo ln -s /var/log/kafka/kafka-manager /usr/local/lib/kafka-manager/logs

echo " - Adapting Configuration file (first time for config run)"
# removing all declarations except the one user ENV VAR
sudo sed -i s/"kafka-manager.zkhosts=\"kafka-manager-zookeeper:2181\""/""/g /usr/local/lib/kafka-manager/conf/application.conf
sudo sed -i s/"kafka-manager.zkhosts=\${?ZK_HOSTS}"/""/g /usr/local/lib/kafka-manager/conf/application.conf
sudo sed -i s/"cmak.zkhosts=\"kafka-manager-zookeeper:2181\""/""/g /usr/local/lib/kafka-manager/conf/application.conf

echo " - Changing owner of config directory to kafka"
sudo chown -R kafka. /usr/local/lib/kafka-manager/conf

# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"

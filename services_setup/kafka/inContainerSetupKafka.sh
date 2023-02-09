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

# Loading topology
. /etc/eskimo_topology.sh

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit 1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit 2
fi

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- SETTING UP KAFKA ------------------------------------------------------"


echo " - Getting kafka user ID"
set +e
kafka_user_id=$(id -u kafka 2>kafka_install_log)
set -e
echo " - Found user with ID $kafka_user_id"
if [[ $kafka_user_id == "" ]]; then
    echo "Docker Kafka USER ID not found"
    exit 3
fi

echo " - Creating lo folder required for glusterMountChecker"
sudo mkdir -p /var/log/gluster/
sudo chown -R kafka /var/log/gluster/


echo " - Simlinking kafka logs to /var/log/"
sudo rm -Rf /usr/local/lib/kafka/logs
sudo ln -s /var/log/kafka /usr/local/lib/kafka/logs

echo " - Simlinking kafka config to /usr/local/etc/kafka"
sudo ln -s /usr/local/lib/kafka/config /usr/local/etc/kafka

echo " - Creating temporary folder for kafka"
sudo mkdir -p /tmp/kafka-logs/
sudo chown -R kafka /tmp/kafka-logs/

echo " - Preparing file to store overriden eskimo memory settings"
touch /usr/local/lib/kafka/config/eskimo-memory.opts
chown kafka /usr/local/lib/kafka/config/eskimo-memory.opts

echo " - Enabling kafka user to create /var/lib/kafka and chown it"
sudo bash -c "echo \"kafka  ALL = NOPASSWD: /bin/chown -R kafka /var/lib/kafka\" >> /etc/sudoers.d/kafka"
sudo bash -c "echo \"kafka  ALL = NOPASSWD: /bin/chmod 755 /var/lib/kafka\" >> /etc/sudoers.d/kafka"
sudo bash -c "echo \"kafka  ALL = NOPASSWD: /bin/mkdir -p /var/lib/kafka/*\" >> /etc/sudoers.d/kafka"

echo " - Enabling kafka user to mount gluster shares (sudo)"
sudo bash -c "echo \"kafka  ALL = NOPASSWD: /bin/bash /usr/local/sbin/inContainerMountGluster.sh *\" >> /etc/sudoers.d/kafka"
sudo bash -c "echo \"kafka  ALL = NOPASSWD: /bin/bash /usr/local/sbin/glusterMountChecker.sh\" >> /etc/sudoers.d/kafka"


echo " - Adapting configuration in file server.properties"
# shellcheck disable=SC2140
sudo sed -i s/"zookeeper.connection.timeout.ms=6000"/"zookeeper.connection.timeout.ms=22000"/g /usr/local/etc/kafka/server.properties

#sudo bash -c "echo -e \"\n\n#Using IP as host name \"  >> /usr/local/etc/kafka/server.properties"
#sudo bash -c "echo -e \"host.name=$SELF_IP_ADDRESS\"  >> /usr/local/etc/kafka/server.properties"
#sudo bash -c "echo -e \"advertised.host.name=$SELF_IP_ADDRESS\"  >> /usr/local/etc/kafka/server.properties"

# shellcheck disable=SC2140
sudo sed -i s/"#listeners=PLAINTEXT:\/\/:9092"/"listeners=PLAINTEXT:\/\/0.0.0.0:9092"/g /usr/local/etc/kafka/server.properties

sudo bash -c "echo -e \"\n\n#Enabling topic deletion. \"  >> /usr/local/etc/kafka/server.properties"
sudo bash -c "echo -e \"delete.topic.enable=true\"  >> /usr/local/etc/kafka/server.properties"

echo " - Enabling user kafka later on to change configuration file"
chown kafka. /usr/local/etc/kafka/server.properties


echo " - Enabling JMX"
sudo sed -i s/\
"KAFKA_JMX_OPTS=\"-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.authenticate=false  -Dcom.sun.management.jmxremote.ssl=false \""\
/\
"KAFKA_JMX_OPTS=\"-Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.rmi.port=\$JMX_PORT -Djava.rmi.server.hostname=$SELF_IP_ADDRESS -Djava.net.preferIPv4Stack=true\""/g /usr/local/lib/kafka/bin/kafka-run-class.sh

sudo sed -i s/"COMMAND=\$1"/"export JMX_PORT=\${JMX_PORT:-9999}\nCOMMAND=\$1"/g /usr/local/lib/kafka/bin/kafka-server-start.sh



# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"
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


KAFKA_USER_ID=$1
if [[ $KAFKA_USER_ID == "" ]]; then
    echo " - Didn't get Kafka User ID as argument"
    exit 1
fi


SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- SETTING UP KAFKA (Common part) -------------------------------------------"

echo " - Creating kafka user (if not exist) in container"
set +e
kafka_user_id=$(id -u kafka 2>> /tmp/cerebro_kafka_log)
set -e
echo " - Found user with ID $kafka_user_id"
if [[ $kafka_user_id == "" ]]; then
    useradd -u $KAFKA_USER_ID kafka
elif [[ $kafka_user_id != $KAFKA_USER_ID ]]; then
    echo "Docker Kafka USER ID is $kafka_user_id while requested USER ID is $KAFKA_USER_ID"
    exit 2
fi

echo " - Creating user kafka home directory"
mkdir -p /home/kafka
chown kafka /home/kafka

echo " - Enabling kafka to run kube_do"
sudo bash -c "echo \"kafka  ALL = NOPASSWD:SETENV: /bin/bash /usr/local/sbin/import-hosts.sh\" >> /etc/sudoers.d/kafka"


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"
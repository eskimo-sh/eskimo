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

export SELF_IP_ADDRESS=$1
if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address in argument"
    exit -2
fi

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

echo " - Creating flink user (if not exist)"
export flink_user_id=`id -u flink 2>> /tmp/flink_executor_install.log`
if [[ $flink_user_id == "" ]]; then
    sudo useradd -u 3305 flink
    flink_user_id=`id -u flink 2>> /tmp/flink_executor_install.log`
    if [[ $flink_user_id == "" ]]; then
        echo "Failed to add user flink"
        exit -4
    fi
fi

# Create shared dir
echo " - Creating shared directory"
sudo mkdir -p /var/log/flink
sudo chown flink /var/log/flink
sudo mkdir -p /var/log/flink/log
sudo chown flink /var/log/flink/log
sudo mkdir -p /var/lib/flink
sudo mkdir -p /var/lib/flink/tmp
sudo chown -R flink /var/lib/flink


echo " - Installing setupFlinkGlusterShares.sh to /usr/local/sbin"
sudo cp setupFlinkGlusterShares.sh /usr/local/sbin/
sudo chmod 755 /usr/local/sbin/setupFlinkGlusterShares.sh
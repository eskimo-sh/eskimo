#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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


MARATHON_USER_ID=$1
if [[ $MARATHON_USER_ID == "" ]]; then
    echo " - Didn't get MARATHON User ID as argument"
    exit -2
fi

# Loading topology
. /etc/eskimo_topology.sh

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit -1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit -2
fi


echo " - Symlinking some RHEL mesos dependencies "
saved_dir=`pwd`
cd /usr/lib/x86_64-linux-gnu/
sudo ln -s libsvn_delta-1.so.1.0.0 libsvn_delta-1.so.0
sudo ln -s libsvn_subr-1.so.1.0.0 libsvn_subr-1.so.0
sudo ln -s libsasl2.so.2 libsasl2.so.3
cd $saved_dir

echo " - Creating marathon user (if not exist) in container"
set +e
marathon_user_id=`id -u marathon 2>/dev/null`
set -e
if [[ $marathon_user_id == "" ]]; then
    useradd -u $MARATHON_USER_ID marathon
elif [[ $marathon_user_id != $MARATHON_USER_ID ]]; then
    echo "Docker MARATHON USER ID is $marathon_user_id while requested USER ID is $MARATHON_USER_ID"
    exit -2
fi

echo " - Creating user marathon home directory"
mkdir -p /home/marathon
chown marathon /home/marathon

echo " - Updating marathon registry config"
sudo sed -i s/"rootdirectory: \/var\/lib\/docker_registry"/"rootdirectory: \/var\/lib\/marathon\/docker_registry"/g /etc/docker_registry/config.yml
#rootdirectory: /var/lib/docker_registry

echo " - Create runtime env variables configuration file"
sudo mkdir -p /usr/local/lib/marathon/etc/
sudo touch /usr/local/lib/marathon/etc/runtime_vars.conf
sudo bash -c "echo '# The timeout in second to wait for a task to be launched successfuly before considering it failed' >> /usr/local/lib/marathon/etc/runtime_vars.conf"
sudo bash -c "echo 'task_launch_timeout=420000' >> /usr/local/lib/marathon/etc/runtime_vars.conf"

sudo chown -R marathon /usr/local/lib/marathon/etc/


# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container config SUCCESS"

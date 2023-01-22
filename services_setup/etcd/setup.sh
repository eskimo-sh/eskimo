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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR || exit 199

# Loading topology
loadTopology

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit 1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit 2
fi


# reinitializing log
sudo rm -f etcd_install_log

set -e

echo " - Creating shared directory"
if [[ ! -d /var/lib/etcd ]]; then
    sudo mkdir -p /var/lib/etcd
    sudo chmod -R 700 /var/lib/etcd
    sudo chown -R kubernetes /var/lib/etcd
fi
if [[ ! -d /var/run/etcd ]]; then
    sudo mkdir -p /var/run/etcd
    sudo chown -R kubernetes /var/run/etcd
fi
if [[ ! -d /var/log/etcd ]]; then
    sudo mkdir -p /var/log/etcd
    sudo chmod -R 777 /var/log/etcd
    sudo chown -R kubernetes /var/log/etcd
fi

echo " - Linking /etc/etcd to /usr/local/lib/etcd/etc"
if [[ ! -L /etc/etcd ]]; then
    sudo ln -s /usr/local/etc/etcd /etc/etcd
fi

echo " - Installing etcd.env.sh"
sudo cp etcd.env.sh /etc/etcd/
sudo chmod 755 /etc/etcd/

set +e

echo " - Installing and checking systemd service file"
install_and_check_service_file etcd etcd_install_log

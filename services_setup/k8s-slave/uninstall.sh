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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Change current folder to script dir (important !)
cd $SCRIPT_DIR

# Loading topology
. /etc/eskimo_topology.sh

echo " - Removing systemd unit files"
if [[ -d /lib/systemd/system/ ]]; then
    export systemd_units_dir=/lib/systemd/system/
elif [[ -d /usr/lib/systemd/system/ ]]; then
    export systemd_units_dir=/usr/lib/systemd/system/
else
    echo "Couldn't find systemd unit files directory"
    exit 24
fi

if [[ "$MASTER_K8S_MASTER_1" != "$SELF_IP_ADDRESS" ]]; then
    sudo rm -Rf $systemd_units_dir/etcd.service
    sudo systemctl stop etcd >> /dev/null 2>&1
fi

sudo rm -Rf $systemd_units_dir/kubeproxy.service
sudo systemctl stop kubeproxy >> /dev/null 2>&1

sudo rm -Rf $systemd_units_dir/kubelet.service
sudo systemctl stop kubelet >> /dev/null 2>&1

sudo rm -Rf /etc/k8s/kubeproxy.env.sh
sudo rm -Rf /etc/k8s/kubelet.env.sh
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
sudo rm -f k8s-master_install_log

# Initially this was a Hack for BTRFS support :
#   - need to unmount gluster shares otherwise cp command goes nuts
#   - https://github.com/moby/moby/issues/38252
# But eventually I need to do this in anyway to make sure everything is preoperly re-installed
# I need to make sure I'm doing this before attempting to recreate the directories
#preinstall_unmount_gluster_share /var/lib/kubernetes/data

echo " - Getting kubernetes user"
export kubernetes_user_id=$(id -u kubernetes 2>> k8s-master_install_log)
if [[ $kubernetes_user_id == "" ]]; then
    echo "User kubernetes should have been added by eskimo-base-system setup script"
    exit 4
fi

bash ./setup-kube-common.sh
fail_if_error $? /dev/null 101

if [[ -d /lib/systemd/system/ ]]; then
    export systemd_units_dir=/lib/systemd/system/
elif [[ -d /usr/lib/systemd/system/ ]]; then
    export systemd_units_dir=/usr/lib/systemd/system/
else
    echo "Couldn't find systemd unit files directory"
    exit 24
fi

set -e

echo " - Copying SystemD unit files to /lib/systemd/system"
for i in $(find ./service_files -mindepth 1); do
    sudo cp $i $systemd_units_dir
    filename=$(echo $i | cut -d '/' -f 3)
    sudo chmod 755 $systemd_units_dir$filename
done

echo " - Copying K8s service files to /etc/k8s/services"
sudo mkdir -p /etc/k8s/services
for i in $(find ./k8s-service_files -mindepth 1); do
    sudo cp $i /etc/k8s/services/
    filename=$(echo $i | cut -d '/' -f 3)
    sudo chmod 755 /etc/k8s/services/$filename
done

set +e

# Setup all individual services
bash ./setup-k8s-registry.sh
fail_if_error $? /dev/null 301

bash ./setup-kubectl.sh
fail_if_error $? /dev/null 302

bash /etc/k8s/runtime_config/setup-runtime-kubectl.sh
fail_if_error $? /dev/null 304

bash ./setup-kubeapi.sh
fail_if_error $? /dev/null 305

bash ./setup-kube-services.sh
fail_if_error $? /dev/null 308


echo " - Copying kube-master process files to /usr/local/sbin"
sudo cp start-kube-master.sh /usr/local/sbin/
sudo chmod 755 /usr/local/sbin/start-kube-master.sh

sudo cp stop-kube-master.sh /usr/local/sbin/
sudo chmod 755 /usr/local/sbin/stop-kube-master.sh

echo " - Copying kube-housekeeping.sh to /usr/local/sbin"
sudo cp kube-housekeeping.sh /usr/local/sbin/
sudo chmod 755 /usr/local/sbin/kube-housekeeping.sh

if [[ $(sudo crontab -u root -l 2>/dev/null | grep kube-housekeeping.sh) == "" ]]; then
    echo " - Scheduling periodic execution of kube-housekeeping.sh using crontab"
    sudo rm -f /tmp/crontab
    sudo bash -c "crontab -u root -l >> /tmp/crontab 2>/dev/null"
    sudo bash -c "echo \"* * * * * /bin/bash /usr/local/sbin/kube-housekeeping.sh\" >> /tmp/crontab"
    sudo crontab -u root /tmp/crontab
fi

echo " - Installing and checking systemd service file"
install_and_check_service_file kube-master k8s-master_install_log

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


. /usr/local/sbin/eskimo-utils.sh

# Take exclusive lock
mkdir -p /var/lib/kubernetes/
take_global_lock kube_houskeeping_lock /var/lib/kubernetes/ nonblock
if [[ $? != 0 ]]; then
    echo "$(date +"%Y-%m-%d %H:%M:%S") - kube-housekeeping.sh is in execution already. Skipping ..."
    exit 0
fi


echo "   + Sourcing kubernetes environment"
. /etc/k8s/env.sh

export HOME=/root

export PATH=/usr/local/bin:$PATH


echo "$(date +"%Y-%m-%d %H:%M:%S")   + Searching for failed PODs" \
      >> /var/log/kubernetes/kube-houskeeping.log

for failedPod in $(kubectl get pods --all-namespaces --field-selector 'status.phase=Failed' -o name); do

      echo "$(date +"%Y-%m-%d %H:%M:%S") Found failed POD $failedPod" \
            >> /var/log/kubernetes/kube-houskeeping.log

      echo "$(date +"%Y-%m-%d %H:%M:%S") ------------- POD $failedPod details are as follows : ---------------"  \
            >> /var/log/kubernetes/kube-houskeeping.log

      kubectl get $failedPod -o json \
            >> /var/log/kubernetes/kube-houskeeping.log

      echo "$(date +"%Y-%m-%d %H:%M:%S") Now deleting POD $failedPod" \
            >> /var/log/kubernetes/kube-houskeeping.log

      kubectl delete $failedPod \
            >> /var/log/kubernetes/kube-houskeeping.log

done

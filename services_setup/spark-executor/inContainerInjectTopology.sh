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

# silent
#echo " - Loading Topology"
. /etc/eskimo_topology.sh

export KUBERNETES_API_MASTER=$MASTER_K8S_MASTER_1
if [[ $KUBERNETES_API_MASTER == "" ]]; then
    echo " - No Kubernetes API master found in topology"
    exit 3
fi

echo " - Mounting spark gluster shares"
echo "   + (Taking the opportunity to do it in inContainerInjectTopology.sh since it's used everywhere)"
sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh spark_data /var/lib/spark/data spark
sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh spark_eventlog /var/lib/spark/eventlog spark

# silent
#echo " - Adapting configuration files and scripts"
bash -c "echo -e \"\n#Defining the kubernetes API master\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
bash -c "echo -e \"spark.kubernetes.driver.master=https://${KUBERNETES_API_MASTER}:6443\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
bash -c "echo -e \"spark.master=k8s://https://${KUBERNETES_API_MASTER}:6443\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

if [[ $MEMORY_SPARK_EXECUTOR != "" ]]; then
    bash -c "echo -e \"\n#Defining default Spark executor memory allowed by Eskimo Memory Management (found in topology)\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
    bash -c "echo -e \"spark.executor.memory=\"$MEMORY_SPARK_EXECUTOR\"m\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
fi

# replacing driver bind IP address at runtime
#sed -i s/"spark.driver.host=RUNTIME_IP_ADDRESS"/"spark.driver.host="$SELF_IP_ADDRESS""/g  /usr/local/lib/spark/conf/spark-defaults.conf

#echo " - Updating spark environment file"
#bash -c "echo -e \"\n#Binding Spark driver to local address \"  >> /usr/local/lib/spark/conf/spark-env.sh"
#bash -c "echo -e \"export LIBPROCESS_IP=$SELF_IP_ADDRESS\"  >> /usr/local/lib/spark/conf/spark-env.sh"
#bash -c "echo -e \"export SPARK_LOCAL_IP=$SELF_IP_ADDRESS\"  >> /usr/local/lib/spark/conf/spark-env.sh"

echo " - Creating required directories (this is the only place I can do it)"
sudo /bin/mkdir -p /var/lib/spark/tmp
sudo /bin/chown -R spark /var/lib/spark

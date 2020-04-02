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

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


SELF_IP_ADDRESS=$1
if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - Didn't get Self IP Address as argument"
    exit -2
fi


echo " - Symlinking some RHEL mesos dependencies "
saved_dir=`pwd`
cd /usr/lib/x86_64-linux-gnu/
sudo ln -s libsvn_delta-1.so.1.0.0 libsvn_delta-1.so.0
sudo ln -s libsvn_subr-1.so.1.0.0 libsvn_subr-1.so.0
sudo ln -s libsasl2.so.2 libsasl2.so.3
cd $saved_dir



# The external address of the host on which the JobManager runs and can be
# reached by the TaskManagers and any clients which want to connect
sed -i s/"jobmanager.rpc.address: localhost"/"jobmanager.rpc.address: $SELF_IP_ADDRESS"/g /usr/local/lib/flink/conf/flink-conf.yaml

# The address to which the REST client will connect to
sed -i s/"#rest.address: 0.0.0.0"/"rest.address: $SELF_IP_ADDRESS"/g /usr/local/lib/flink/conf/flink-conf.yaml

# The address that the REST & web server binds to
sed -i s/"#rest.bind-address: 0.0.0.0"/"rest.bind-address: $SELF_IP_ADDRESS"/g /usr/local/lib/flink/conf/flink-conf.yaml


sudo bash -c "echo -e \"\n# CPUs to assign to the Mesos workers. \"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"mesos.resourcemanager.tasks.cpus: 1\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"

# XXX This is not supported abymore by Flink 1.10
# Caused by: org.apache.flink.configuration.IllegalConfigurationException: Inconsistent worker memory configuration:
# both legacy Mesos specific and the newer unified options are configured but they differ
# - mesos.resourcemanager.tasks.mem: 1024 Mb (1073741824 bytes), taskmanager.memory.process.size: 1568 Mb (1644167168 bytes)
#sudo bash -c "echo -e \"\n# Memory to assign to the Mesos workers in MB. \"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
#sudo bash -c "echo -e \"mesos.resourcemanager.tasks.mem: 1024\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"

# XXX This is somewhat legacy, commented out already as of 1.9
#sudo bash -c "echo -e \"\n#  The amount of memory (in megabytes) that the task manager reserves on-heap or off-heap (in MB). \"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
#sudo bash -c "echo -e \"taskmanager.memory.size: 800\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"


# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container config SUCCESS"

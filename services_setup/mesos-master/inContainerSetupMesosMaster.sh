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




SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- SETTING UP MESOS-MASTER ------------------------------------------------------"

echo " - Symlinking some RHEL mesos dependencies "
saved_dir=`pwd`
cd /usr/lib/x86_64-linux-gnu/
sudo ln -s libsvn_delta-1.so.1.0.0 libsvn_delta-1.so.0
sudo ln -s libsvn_subr-1.so.1.0.0 libsvn_subr-1.so.0
sudo ln -s libsasl2.so.2 libsasl2.so.3
cd $saved_dir

echo " - Simlinking mesos config to /usr/local/etc/mesos"
if [[ ! -f /usr/local/etc/mesos ]]; then
    sudo ln -s /usr/local/lib/mesos/etc/mesos /usr/local/etc/mesos
fi

echo " - Basic environment setup (mesos cannot run without these variables)"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/lib/:/usr/local/lib/mesos/lib
# etc profile
sudo bash -c "echo -e \"\n# Environment variables required for mesos\" >> /etc/profile"
sudo bash -c "echo -e \"export LD_LIBRARY_PATH=\$LD_LIBRARY_PATH:/usr/local/lib/:/usr/local/lib/mesos/lib\" >> /etc/profile"
sudo bash -c "echo -e \"export PYTHONPATH=\$PYTHONPATH:/usr/lib/python2.7/:/usr/local/lib/mesos/lib/python2.7/site-packages/\" >> /etc/profile"
# etc bash.bashrc
sudo bash -c "echo -e \"\n# Environment variables required for mesos\" >> /etc/bash.bashrc"
sudo bash -c "echo -e \"export LD_LIBRARY_PATH=\$LD_LIBRARY_PATH:/usr/local/lib/:/usr/local/lib/mesos/lib\" >> /etc/bash.bashrc"
sudo bash -c "echo -e \"export PYTHONPATH=\$PYTHONPATH:/usr/lib/python2.7/:/usr/local/lib/mesos/lib/python2.7/site-packages/\" >> /etc/bash.bashrc"
sudo ldconfig

echo " - Creating mesos master working directory in /var/lib/mesos/master"
sudo mkdir -p /var/lib/mesos/master
sudo chmod 755 /var/lib/mesos/master

echo " - Creating mesos master environment file"
sudo rm -Rf /usr/local/etc/mesos/mesos-master-env.sh

sudo bash -c "echo -e \"\n#Avoid issues with systems that have multiple ethernet interfaces when the Master or Slave\"  >> /usr/local/etc/mesos/mesos-master-env.sh"
sudo bash -c "echo -e \"#registers with a loopback or otherwise undesirable interface.\"  >> /usr/local/etc/mesos/mesos-master-env.sh"
sudo bash -c "echo \"export MESOS_ip=$SELF_IP_ADDRESS\" >> /usr/local/etc/mesos/mesos-master-env.sh"

sudo bash -c "echo -e \"\n#By default, the Master will use the system hostname which can result in issues in the \"  >> /usr/local/etc/mesos/mesos-master-env.sh"
sudo bash -c "echo -e \"#event the system name isn’t resolvable via your DNS server.\"  >> /usr/local/etc/mesos/mesos-master-env.sh"
sudo bash -c "echo \"export MESOS_hostname=$SELF_IP_ADDRESS\" >> /usr/local/etc/mesos/mesos-master-env.sh"


sudo bash -c "echo -e \"\n#Path of the master work directory. \"  >> /usr/local/etc/mesos/mesos-master-env.sh"
sudo bash -c "echo -e \"#This is where the persistent information of the cluster will be stored\"  >> /usr/local/etc/mesos/mesos-master-env.sh"
sudo bash -c "echo \"export MESOS_work_dir=/var/lib/mesos/master\" >> /usr/local/etc/mesos/mesos-master-env.sh"

# TODO Change quorum for a greater value if one has more than one master
sudo bash -c "echo -e \"\n#We need a quorum of one since we have only one master\"  >> /usr/local/etc/mesos/mesos-master-env.sh"
sudo bash -c "echo \"export MESOS_quorum=1\" >> /usr/local/etc/mesos/mesos-master-env.sh"

sudo bash -c "echo -e \"\n# file path containing the JSON-formatted ACLs used for authorization.\" >> /usr/local/etc/mesos/mesos-master-env.sh"
sudo bash -c "echo \"export MESOS_acls=/usr/local/lib/mesos/etc/mesos/mesos-acls.json\" >> /usr/local/etc/mesos/mesos-master-env.sh"

echo " - Creating mesos ACLs file to forbid root from submitting tasks."
sudo rm -Rf /usr/local/lib/mesos/etc/mesos/mesos-acls.json
cat > /tmp/mesos-acls.json <<- "EOF"
{
  "run_tasks": [
    {
      "principals": { "type": "NONE" },
      "users": { "values": ["root"] }
    }
  ]
}
EOF
sudo mv /tmp/mesos-acls.json /usr/local/lib/mesos/etc/mesos/mesos-acls.json
sudo chmod 755 /usr/local/lib/mesos/etc/mesos/mesos-acls.json
sudo chown root.staff /usr/local/lib/mesos/etc/mesos/mesos-acls.json

# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container config SUCCESS"
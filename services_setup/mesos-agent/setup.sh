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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR

# Loading topology
loadTopology

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit -1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit -2
fi


# reinitializing log
sudo rm -f /tmp/mesos_install_log

echo " - Creating shared directory"
if [[ ! -d /var/lib/mesos ]]; then
    sudo mkdir -p /var/lib/mesos
    sudo chmod -R 777 /var/lib/mesos
fi
if [[ ! -d /var/run/mesos ]]; then
    sudo mkdir -p /var/run/mesos
fi
if [[ ! -d /var/log/mesos ]]; then
    sudo mkdir -p /var/log/mesos
    sudo chmod -R 777 /var/log/mesos
fi

if [[ -f /usr/local/lib/mesos/sbin/mesos-init-wrapper.sh ]]; then
    echo " - Removing existing mesos-init-wrapper script (will be rewritten)"
    sudo rm -f /usr/local/lib/mesos/sbin/mesos-init-wrapper.sh
fi

echo " - Copying mesos-init-wrapper script"
sudo cp $SCRIPT_DIR/mesos-init-wrapper.sh /usr/local/lib/mesos/sbin/
sudo chmod 754 /usr/local/lib/mesos/sbin/mesos-init-wrapper.sh

echo " - Symlinking mesos-init-wrapper script to /usr/local/sbin/"
sudo rm -f /usr/local/sbin/mesos-init-wrapper.sh
sudo ln -s /usr/local/lib/mesos/sbin/mesos-init-wrapper.sh /usr/local/sbin/mesos-init-wrapper.sh


if [[ -f /usr/local/sbin/register-marathon-registry.sh ]]; then
    echo " - Removing existing register-marathon-registry.sh (will be rewritten)"
    sudo rm -f /usr/local/sbin/register-marathon-registry.sh
fi

echo " - Copying register-marathon-registry.sh script"
sudo cp $SCRIPT_DIR/register-marathon-registry.sh /usr/local/sbin/
sudo chmod 754 /usr/local/sbin/register-marathon-registry.sh


if [[ -f /usr/local/sbin/kill-orphan-marathon-container-HACK.sh ]]; then
    echo " - Removing existing kill-orphan-marathon-container-HACK.sh (will be rewritten)"
    sudo rm -f /usr/local/sbin/kill-orphan-marathon-container-HACK.sh
fi

echo " - Copying kill-orphan-marathon-container-HACK.sh script"
sudo cp $SCRIPT_DIR/kill-orphan-marathon-container-HACK.sh /usr/local/sbin/
sudo chmod 754 /usr/local/sbin/kill-orphan-marathon-container-HACK.sh

echo " - Copying settingsInjector.sh script"
sudo cp $SCRIPT_DIR/settingsInjector.sh /usr/local/sbin/
sudo chmod 754 /usr/local/sbin/settingsInjector.sh

echo " - Copying injectMesosAgentSettings.sh script"
sudo cp $SCRIPT_DIR/injectMesosAgentSettings.sh /usr/local/sbin/
sudo chmod 754 /usr/local/sbin/injectMesosAgentSettings.sh


if [[ `sudo crontab -u root -l 2>/dev/null | grep kill-orphan-marathon-container-HACK.sh` == "" ]]; then
    echo " - Scheduling periodic execution of kill-orphan-marathon-container-HACK.sh using crontab"
    sudo rm -f /tmp/crontab
    sudo bash -c "crontab -u root -l >> /tmp/crontab 2>/dev/null"
    sudo bash -c "echo \"* * * * * /usr/local/sbin/kill-orphan-marathon-container-HACK.sh\" >> /tmp/crontab"
    sudo crontab -u root /tmp/crontab
fi


echo " - Creating mesos slave working directory in /var/lib/mesos/slave"
sudo mkdir -p /var/lib/mesos/slave
sudo chmod 755 /var/lib/mesos/slave

echo " - Copying mesos-agent systemd file"
if [[ -d /lib/systemd/system/ ]]; then
    export systemd_units_dir=/lib/systemd/system/
elif [[ -d /usr/lib/systemd/system/ ]]; then
    export systemd_units_dir=/usr/lib/systemd/system/
else
    echo "Couldn't find systemd unit files directory"
    exit -10
fi
sudo cp $SCRIPT_DIR/mesos-agent.service $systemd_units_dir
sudo chmod 644 $systemd_units_dir/mesos-agent.service

echo " - Creating mesos slave environment file"
sudo rm -Rf /usr/local/etc/mesos/mesos-slave-env.sh

sudo bash -c "echo -e \"\n#Path of the slave work directory. \"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo -e \"#This is where executor sandboxes will be placed, as well as the agent's checkpointed state.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo \"export MESOS_work_dir=/var/lib/mesos/slave\" >> /usr/local/etc/mesos/mesos-slave-env.sh"

sudo bash -c "echo -e \"\n#we need the Slave to discover the Master.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh" 
sudo bash -c "echo -e \"#This is accomplished by updating the master argument to the master Zookeeper URL\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"

sudo bash -c "echo -e \"#For this we first need to source the eskimo topology\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo -e \". /etc/eskimo_topology.sh\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo 'export MESOS_master=zk://\$MASTER_ZOOKEEPER_1:2181/mesos\' >> /usr/local/etc/mesos/mesos-slave-env.sh"

sudo bash -c "echo -e \"\n# file path containing the JSON-formatted Total consumable resources per agent.\" >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo \"export MESOS_resources=file:///usr/local/lib/mesos/etc/mesos/mesos-resources.json\" >> /usr/local/etc/mesos/mesos-slave-env.sh"

sudo bash -c "echo -e \"\n#Avoid issues with systems that have multiple ethernet interfaces when the Master or Slave\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo -e \"#registers with a loopback or otherwise undesirable interface.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo \"export MESOS_ip=$SELF_IP_ADDRESS\" >> /usr/local/etc/mesos/mesos-slave-env.sh"

sudo bash -c "echo -e \"\n#By default, the Master will use the system hostname which can result in issues in the \"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo -e \"#event the system name isn’t resolvable via your DNS server.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo \"export MESOS_hostname=$SELF_IP_ADDRESS\" >> /usr/local/etc/mesos/mesos-slave-env.sh"

sudo bash -c "echo -e \"\n#Enabling docker image provider.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"

# THIS DOESN?T WORK !!!!
#sudo bash -c "echo -e \"#Comma-separated list of supported image providers.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
#sudo bash -c "echo -e \"export MESOS_image_providers=docker,appc\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"

sudo bash -c "echo -e \"\n# Comma-separated list of containerizer implementations to compose in order to provide containerization\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo -e \"# Available options are mesos and docker\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo -e \"export MESOS_containerizers=docker,mesos\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"

sudo bash -c "echo -e \"\n# isolation mechanisms to use\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo -e \"export MESOS_isolation=docker/runtime,filesystem/linux\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"

sudo bash -c "echo -e \"\n# Giving it a little time do download and extract large docker images\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo -e \"export MESOS_executor_registration_timeout=5mins\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"

sudo bash -c "echo -e \"\n# This flag controls which agent configuration changes are considered acceptable when recovering the previous agent state.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo -e \"# additive: The new state must be a superset of the old state: it is permitted to add additional resources, attributes \"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo -e \"# and domains but not to remove or to modify existing ones.\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"
sudo bash -c "echo -e \"export MESOS_reconfiguration_policy=additive\"  >> /usr/local/etc/mesos/mesos-slave-env.sh"


echo " - Reloading systemd config"
sudo systemctl daemon-reload >> /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -6

echo " - Checking mesos agent Systemd file"
if [[ `systemctl status mesos-agent | grep 'could not be found'` != "" ]]; then
    echo "mesos-agent systemd file installation failed"
    exit -12
fi
sudo systemctl status mesos-agent > /tmp/mesos_install_log 2>&1
if [[ $? != 0 && $? != 3 ]]; then
    echo "mesos-agent systemd file doesn't work as expected"
    exit -12
fi  

echo " - Testing systemd startup - starting mesos-agent"
sudo systemctl start mesos-agent > /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -6

echo " - Testing systemd startup - Checking startup of agent"
sleep 8
sudo systemctl status mesos-agent > /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -7

echo " - Testing systemd startup - Make sure service is really running"
if [[ `systemctl show -p SubState mesos-agent | grep exited` != "" ]]; then
    echo "mesos-agent service is actually not really running"
    exit -13
fi

echo " - Testing systemd startup - stopping mesos-agent"
sudo systemctl stop mesos-agent > /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -8

echo " - Enabling mesos-agent on startup"
sudo systemctl enable mesos-agent > /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -9

echo " - Testing systemd startup - starting mesos-agent (again)"
sudo systemctl start mesos-agent > /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -6




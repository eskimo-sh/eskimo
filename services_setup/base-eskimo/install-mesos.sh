#!/usr/bin/env bash

# Deprecated
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

export AMESOS_VERSION=1.11.0


function create_binary_wrapper(){
    if [[ $1 == "" || $2 == "" ]]; then
        echo "source and target have to be passed as argument of the create_kafka_wrapper function"
    else
        sudo touch $2
        sudo chmod 777 $2
        echo -e '#!/bin/bash' > $2
        echo -e "" >> $2
        echo -e "__tmp_saved_dir=`pwd`" >> $2
        echo -e "function __tmp_returned_to_saved_dir() {" >> $2
        echo -e '     cd $__tmp_saved_dir' >> $2
        echo -e "}" >> $2
        echo -e "trap __tmp_returned_to_saved_dir 15" >> $2
        echo -e "trap __tmp_returned_to_saved_dir EXIT" >> $2
        echo -e "" >> $2
        echo -e "$1 \"\$@\"" >> $2
        echo -e "" >> $2
        echo -e "__tmp_returned_to_saved_dir" >> $2
        sudo chmod 755 $2
    fi
}


function fail_if_error(){
    if [[ $1 != 0 ]]; then
        echo " -> failed !!"
        cat $2
        exit $3
    fi
}

function get_ip_address(){
    export IP_ADDRESS="`cat /etc/network/interfaces | grep address | cut -d ' ' -f 8`"
}


echo "-- INSTALLING MESOS ------------------------------------------------------------"

if [ -z "$AMESOS_VERSION" ]; then
    echo "Need to set AMESOS_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Changing to temp directory"
sudo rm -Rf /tmp/mesos_setup > /tmp/mesos_install_log 2>&1
mkdir -p /tmp/mesos_setup

if [[ -f "/etc/debian_version" ]]; then
    mv eskimo_mesos-debian_$AMESOS_VERSION*.tar.gz /tmp/mesos_setup/eskimo_mesos_$AMESOS_VERSION.tar.gz

elif [[ -f "/etc/redhat-release" ]]; then
    mv eskimo_mesos-redhat_$AMESOS_VERSION*.tar.gz /tmp/mesos_setup/eskimo_mesos_$AMESOS_VERSION.tar.gz

elif [[ -f "/etc/SUSE-brand" ]]; then
    mv eskimo_mesos-suse_$AMESOS_VERSION*.tar.gz /tmp/mesos_setup/eskimo_mesos_$AMESOS_VERSION.tar.gz

else
    echo " - !! ERROR : Could not find any brand marker file "
    echo "   + none of /etc/debian_version, /etc/redhat-release or /etc/SUSE-brand exist"
    exit 101
fi

cd /tmp/mesos_setup


echo " - Extracting mesos-$AMESOS_VERSION"
tar -zxf eskimo_mesos_$AMESOS_VERSION.tar.gz > /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -2

echo " - Removing any previous mesos installation"
sudo rm -Rf /usr/local/lib/mesos-$AMESOS_VERSION
sudo rm -Rf /usr/local/lib/mesos

echo " - Installing mesos"
sudo chown root.root -R mesos-$AMESOS_VERSION
sudo mv mesos-$AMESOS_VERSION /usr/local/lib

echo " - Symlinking /usr/local/lib/mesos-$AMESOS_VERSION to /usr/local/lib/mesos"
sudo ln -s /usr/local/lib/mesos-$AMESOS_VERSION /usr/local/lib/mesos

echo " - BUGFIXING mesos-tail "
sudo sed -i s/"master_state"/"state"/g /usr/local/lib/mesos-$AMESOS_VERSION/bin/mesos-tail

set -e

echo " - Simlinking mesos config to /usr/local/etc/mesos"
sudo mkdir -p /usr/local/etc/
if [[ ! -f /usr/local/etc/mesos ]]; then
    sudo ln -s /usr/local/lib/mesos/etc/mesos /usr/local/etc/mesos
fi

echo " - Simlinking mesos binaries to /usr/local/bin"
for i in `ls -1 /usr/local/lib/mesos/bin`; do
    if [[ ! -f /usr/local/bin/$i ]]; then
        cat > /tmp/tmp_$i <<- "EOF"
#!/bin/bash
export PYTHONPATH=$PYTHONPATH:/usr/lib/python2.7/:/usr/local/lib/mesos/lib/python2.7/site-packages/
/usr/local/lib/mesos/bin/__command__ "$@"
EOF
        sudo mv /tmp/tmp_$i /usr/local/bin/$i
        sudo sed -i s/"__command__"/"$i"/g /usr/local/bin/$i
        sudo chown root. /usr/local/bin/$i
	    sudo chmod 755 /usr/local/bin/$i
	fi
done

echo " - Simlinking mesos system binaries to /usr/local/sbin"
for i in `ls -1 /usr/local/lib/mesos/sbin`; do
    if [[ ! -f /usr/local/sbin/$i ]]; then
        cat > /tmp/tmp_$i <<- "EOF"
#!/bin/bash
export PYTHONPATH=$PYTHONPATH:/usr/lib/python2.7/:/usr/local/lib/mesos/lib/python2.7/site-packages/
/usr/local/lib/mesos/sbin/__command__ "$@"
EOF
        sudo mv /tmp/tmp_$i /usr/local/sbin/$i
        sudo sed -i s/"__command__"/"$i"/g /usr/local/sbin/$i
        sudo chown root. /usr/local/sbin/$i
	    sudo chmod 755 /usr/local/sbin/$i
	fi
done
set +e

echo " - Basic environment setup (mesos cannot run without these variables)"
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/lib/:/usr/local/lib/mesos-$AMESOS_VERSION/lib
# etc profile
sudo bash -c "echo -e \"\n# Environment variables required for mesos\" >> /etc/profile"
sudo bash -c "echo -e \"export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/lib/:/usr/local/lib/mesos-$AMESOS_VERSION/lib\" >> /etc/profile"
#sudo bash -c "echo -e \"export PYTHONPATH=$PYTHONPATH:/usr/lib/python2.7/:/usr/local/lib/mesos-$AMESOS_VERSION/lib/python2.7/site-packages/\" >> /etc/profile"
# etc bash.bashrc
sudo bash -c "echo -e \"\n# Environment variables required for mesos\" >> /etc/bash.bashrc"
sudo bash -c "echo -e \"export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/lib/:/usr/local/lib/mesos-$AMESOS_VERSION/lib\" >> /etc/bash.bashrc"
#sudo bash -c "echo -e \"export PYTHONPATH=$PYTHONPATH:/usr/lib/python2.7/:/usr/local/lib/mesos-$AMESOS_VERSION/lib/python2.7/site-packages/\" >> /etc/bash.bashrc"
sudo ldconfig
sudo mkdir -p /var/lib/mesos
sudo chmod -R 777 /var/lib/mesos

echo " - Creating config mesos environment file"
sudo rm -Rf /usr/local/etc/mesos/mesos-env.sh
sudo bash -c "echo -e \"\n#Working configuration \" >> /usr/local/etc/mesos/mesos-env.sh"
sudo bash -c "echo \"export MESOS_log_dir=/var/log/mesos\" >> /usr/local/etc/mesos/mesos-env.sh"

sudo bash -c "echo -e \"\n#Specify a human readable name for the cluster \" >> /usr/local/etc/mesos/mesos-env.sh"
sudo bash -c "echo \"export MESOS_cluster=eskimo\" >> /usr/local/etc/mesos/mesos-env.sh"

echo " - Create / update eskimo MESOS version file"
sudo bash -c "echo AMESOS_VERSION=`find /usr/local/lib/ -mindepth 1 -maxdepth 1 ! -type l | grep \"mesos-*.*\" | cut -d '-' -f 2` > /etc/eskimo_mesos_environment"

echo " - Checking eskimo MESOS version file"
if [[ -z $TEST_MODE && ! -f /etc/eskimo_mesos_environment ]]; then
    echo "Could not create /etc/eskimo_mesos_environment"
    exit 21
fi
. /etc/eskimo_mesos_environment

if [[ -z $TEST_MODE && ! -d /usr/local/lib/mesos-$AMESOS_VERSION ]]; then
    echo "/etc/eskimo_mesos_environment doesn't point to valid mesos version"
    exit 21
fi


# installation tests

echo " - Registering test cleaning traps"
export MESOS_MASTER_PROC_ID=-1
export MESOS_AGENT_PROC_ID=-1
function check_stop_mesos(){
    if [[ $MESOS_MASTER_PROC_ID != -1 ]]; then
        echo " - Stopping Mesos Master !!"
        kill -15 $MESOS_MASTER_PROC_ID
    fi
    if [[ $MESOS_AGENT_PROC_ID != -1 ]]; then
        echo " - Stopping Mesos Agent !! "
        kill -15 $MESOS_AGENT_PROC_ID
    fi
}
trap check_stop_mesos 15
trap check_stop_mesos EXIT

echo " - Starting Mesos Master"
sudo rm -Rf /tmp/mesos_tmp/master_work_dir/
sudo mkdir -p /tmp/mesos_tmp/master_work_dir/
sudo chmod -R 777 /tmp/mesos_tmp/master_work_dir/
/usr/local/lib/mesos-$AMESOS_VERSION/sbin/mesos-master \
        --port=63050 \
        --ip=127.0.0.1 \
        --work_dir=/tmp/mesos_tmp/master_work_dir/ \
        > /tmp/mesos_master_log 2>&1 &
export MESOS_MASTER_PROC_ID=$!

echo " - Checking Mesos master startup"
sleep 2
if [[ `ps -e | grep $MESOS_MASTER_PROC_ID` == "" ]]; then
    echo " !! Failed to start Mesos Master !!"
    cat /tmp/mesos_master_log
    exit -8
fi

echo " - Starting Mesos Agent"
sudo rm -Rf /tmp/mesos_tmp/slave_work_dir/
sudo mkdir -p /tmp/mesos_tmp/slave_work_dir/
sudo chmod -R 777 /tmp/mesos_tmp/slave_work_dir/
/usr/local/lib/mesos-$AMESOS_VERSION/sbin/mesos-agent  \
        --port=63051 \
        --master=127.0.0.1:63050 \
        --no-systemd_enable_support \
        --work_dir=/tmp/mesos_tmp/slave_work_dir/ \
        > /tmp/mesos_agent_log 2>&1 &
export MESOS_AGENT_PROC_ID=$!

echo " - Checking Mesos Agent startup"
sleep 2
if [[ `ps -e | grep $MESOS_AGENT_PROC_ID` == "" ]]; then
    echo " !! Failed to start Mesos Agent !!"
    cat /tmp/mesos_agent_log
    exit -9
fi

# Visit the Mesos web page.
#$ http://127.0.0.1:5050

echo " - Stopping Mesos Agent"
kill -15 $MESOS_AGENT_PROC_ID
export MESOS_AGENT_PROC_ID=-1

echo " - Stopping Mesos Master"
kill -15 $MESOS_MASTER_PROC_ID
export MESOS_MASTER_PROC_ID=-1

echo " - Cleaning build folder"
cd $saved_dir
sudo rm -Rf /tmp/mesos_setup > /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -10


returned_to_saved_dir





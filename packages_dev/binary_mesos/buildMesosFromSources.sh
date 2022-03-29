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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


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
mkdir -p /tmp/mesos_setup
cd /tmp/mesos_setup

echo " - Downloading mesos-$AMESOS_VERSION"
wget http://www.apache.org/dist/mesos/$AMESOS_VERSION/mesos-$AMESOS_VERSION.tar.gz >> /tmp/mesos_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad mesos-$AMESOS_VERSION from http://www.apache.org/d. Trying to download from niceideas.ch"
    wget http://niceideas.ch/mes/mesos-$AMESOS_VERSION.tar.gz >> /tmp/mesos_install_log 2>&1
    fail_if_error $? "/tmp/mesos_install_log" -1
fi

echo " - Extracting mesos-$AMESOS_VERSION"
tar -zxf mesos-$AMESOS_VERSION.tar.gz >> /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -2

echo " - Configuring source tree (preparing build)"
cd ./mesos-$AMESOS_VERSION/

mkdir build
cd build

../configure --prefix=/usr/local/lib/mesos-$AMESOS_VERSION/ >> /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -3

echo " - Building source tree (!! this takes a long time, up to 1 hour or more !!!)"
make -j 2 > /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -4

# Don't build tests
# FIXME : some of them fail for now :-(
#if [[ $IS_MASTER == 1 ]]; then
#    echo " - Running test suite (!! this takes a long time, up to 30 mins !!)"
#    make -j 2 check > /tmp/mesos_install_log 2>&1
#    fail_if_error $? "/tmp/mesos_install_log" -5
#fi

echo " - Installing mesos"
sudo make install > /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -6

# installation tests

echo " - Basic environment setup "
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/lib/
sudo bash -c 'echo -e "\nexport LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/lib/" > /etc/profile'
sudo ldconfig
sudo mkdir -p /var/lib/mesos
sudo chmod 777 /var/lib/mesos

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
/usr/local/lib/mesos-$AMESOS_VERSION/sbin/mesos-master  --ip=127.0.0.1 --work_dir=/var/lib/mesos >> /tmp/mesos_master_log 2>&1 &
export MESOS_MASTER_PROC_ID=$!

echo " - Checking Mesos master startup"
sleep 2
if [[ `ps -e | grep $MESOS_MASTER_PROC_ID` == "" ]]; then
    echo " !! Failed to start Mesos Master !!"
    exit -8
fi

echo " - Starting Mesos Agent"
/usr/local/lib/mesos-$AMESOS_VERSION/sbin/mesos-agent  --master=127.0.0.1:5050 --no-systemd_enable_support --work_dir=/var/lib/mesos --launcher=posix >> /tmp/mesos_agent_log 2>&1 &
export MESOS_AGENT_PROC_ID=$!

echo " - Checking Mesos Agent startup"
sleep 2
if [[ `ps -e | grep $MESOS_AGENT_PROC_ID` == "" ]]; then
    echo " !! Failed to start Mesos Agent !!"
    exit -9
fi

# Visit the Mesos web page.
#$ http://127.0.0.1:5050


# Don't run tests
# FIXME : some of them fail for now :-(
#if [[ $IS_MASTER ]]; then
#    echo " - Run Java tests framework "
#    ./src/examples/java/test-framework 127.0.0.1:5050 > /tmp/mesos_install_log 2>&1
#    fail_if_error $? "/tmp/mesos_install_log" -11
#
#    echo " - Run Python framework "
#    ./src/examples/python/test-framework 127.0.0.1:5050 > /tmp/mesos_install_log 2>&1
#    fail_if_error $? "/tmp/mesos_install_log" -12
#fi

echo " - Stopping Mesos Agent"
kill -15 $MESOS_AGENT_PROC_ID
export MESOS_AGENT_PROC_ID=-1

echo " - Stopping Mesos Master"
kill -15 $MESOS_MASTER_PROC_ID
export MESOS_MASTER_PROC_ID=-1


echo " - Cleaning build files"
make clean >> /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -7

echo " - Cleaning build folder"
cd $saved_dir
sudo rm -Rf /tmp/mesos_setup >> /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -10

echo " - Creating tar.gz mesos archive"
cd /usr/local/lib/
tar cvfz mesos-$AMESOS_VERSION.tar.gz mesos-$AMESOS_VERSION >> /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -9

returned_to_saved_dir





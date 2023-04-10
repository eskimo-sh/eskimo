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


SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- INSTALLING PYTHON 3.7-----------------------------------------------------"

if [ -z "$FLINK_PYTHON_VERSION" ]; then
    echo "Need to set FLINK_VERSION environment variable before calling this script !"
    exit 1
fi


echo " - Changing to temp directory"
rm -Rf /tmp/python37_setup
mkdir -p /tmp/python37_setup
cd /tmp/python37_setup || (echo "Couldn't change to /tmp/python37_setup" && exit 200)



echo " - Downloading python"
wget https://www.python.org/ftp/python/$FLINK_PYTHON_VERSION/Python-$FLINK_PYTHON_VERSION.tgz > /tmp/python37_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad Python-$FLINK_PYTHON_VERSION from https://www.python.org/ Trying to download from niceideas.ch"
    wget https://niceideas.ch/mes/Python-$FLINK_PYTHON_VERSION.tgz > /tmp/flink_install_log 2>&1
    fail_if_error $? "/tmp/flink_install_log" -1
fi

echo " - Extracting python"
tar xvzf Python-$FLINK_PYTHON_VERSION.tgz > /tmp/python37_install_log 2>&1
fail_if_error $? "/tmp/python37_install_log" -23

cd Python-$FLINK_PYTHON_VERSION || (echo "Couldn't cd to Python-$FLINK_PYTHON_VERSION" && exit 1)

echo " - Configuring python"
sudo ./configure --enable-optimizations --prefix=/usr > /tmp/python37_install_log 2>&1
fail_if_error $? "/tmp/python37_install_log" -24

echo " - Building and installing python (This takes time)"
sudo make install > /tmp/python37_install_log 2>&1
fail_if_error $? "/tmp/python37_install_log" -25

echo " - Removing test folder"
sudo rm -Rf /usr/lib/python3.7/test > /tmp/python37_install_log 2>&1
fail_if_error $? "/tmp/python37_install_log" -25

echo " - Installing pip"
python3.7 -m pip install pip > /tmp/python37_install_log 2>&1
fail_if_error $? "/tmp/python37_install_log" -26

echo " - Installing six"
pip3 install six > /tmp/python37_install_log 2>&1
fail_if_error $? "/tmp/python37_install_log" -27


sudo rm -Rf /tmp/python37_setup



# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"
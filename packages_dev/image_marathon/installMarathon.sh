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


echo "-- INSTALLING MARATHON -----------------------------------------------------------"

if [ -z "$MARATHON_VERSION" ]; then
    echo "Need to set MARATHON_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Changing to temp directory"
sudo rm -Rf /tmp/marathon_setup
mkdir -p /tmp/marathon_setup
cd /tmp/marathon_setup

echo " - Updating dependencies for libmesos "
sudo DEBIAN_FRONTEND=noninteractive apt-get -y install \
            libcurl4-nss-dev libsasl2-dev libsasl2-modules maven libapr1-dev libsvn-dev zlib1g-dev \
            > /tmp/marathon_install_log 2>&1
fail_if_error $? "/tmp/marathon_install_log" -2

if [[ -f /tmp/marathon-$MARATHON_VERSION_SHORT.tgz ]]; then

    echo " - Using local marathon distribution marathon-$MARATHON_VERSION_SHORT.tgz"
    mv /tmp/marathon-$MARATHON_VERSION_SHORT.tgz ./marathon-$MARATHON_VERSION_SHORT.tgz > /tmp/marathon_install_log 2>&1
    fail_if_error $? "/tmp/marathon_install_log" -1

    export VERSION_TO_USE=$MARATHON_VERSION_SHORT

else

    echo " - Downloading marathon-$MARATHON_VERSION"
    wget https://downloads.mesosphere.io/marathon/builds/$MARATHON_VERSION/marathon-$MARATHON_VERSION.tgz  > /tmp/marathon_install_log 2>&1
    if [[ $? != 0 ]]; then
        echo " -> Failed to downolad marathon-$MARATHON_VERSION from http://www.apache.org/. Trying to download from niceideas.ch"
        wget http://niceideas.ch/mes/marathon-$MARATHON_VERSION.tgz > /tmp/marathon_install_log 2>&1
        fail_if_error $? "/tmp/marathon_install_log" -1
    fi

    export VERSION_TO_USE=$MARATHON_VERSION

fi

echo " - Extracting marathon-$VERSION_TO_USE"
tar -xvf marathon-$VERSION_TO_USE.tgz > /tmp/marathon_install_log 2>&1
fail_if_error $? "/tmp/marathon_install_log" -2

#bash

echo " - Installing marathon"
sudo chown root.staff -R marathon-$MARATHON_VERSION
sudo mv marathon-$MARATHON_VERSION /usr/local/lib/marathon-$MARATHON_VERSION

echo " - symlinking /usr/local/lib/marathon/ to /usr/local/lib/marathon-$MARATHON_VERSION"
sudo ln -s /usr/local/lib/marathon-$MARATHON_VERSION /usr/local/lib/marathon



#TODO TEST STARTUP

#echo " - Checking Marathon Installation"
#/usr/local/lib/marathon-$MARATHON_VERSION/bin/run-example MarathonPi 10 > /tmp/marathon_run_log 2>&1 &
#fail_if_error $? "/tmp/marathon_run_log" -3


sudo rm -Rf /tmp/marathon_setup
returned_to_saved_dir




# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"
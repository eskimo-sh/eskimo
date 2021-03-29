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


echo "-- INSTALLING EGMI -----------------------------------------------------------"

if [ -z "$EGMI_VERSION" ]; then
    echo "Need to set EGMI_VERSION environment variable before calling this script !"
    exit 1
fi


echo " - Testing if local EGMI is found "
export EGMI_LOCAL_ARCHIVE=
for i in `find /tmp -name 'egmi*tar.gz'`; do
    export EGMI_LOCAL_ARCHIVE=$i
done
if [[ $EGMI_LOCAL_ARCHIVE != "" ]]; then
    echo "   + Found local archive : $EGMI_LOCAL_ARCHIVE"
fi




saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Changing to temp directory"
mkdir -p /tmp/egmi_setup
cd /tmp/egmi_setup

if [[ $EGMI_LOCAL_ARCHIVE != "" ]]; then
    echo " - Using local archive"
    mv $EGMI_LOCAL_ARCHIVE egmi-$EGMI_VERSION-bin.tar.gz
else
    echo " - Downloading archive egmi-$EGMI_VERSION"
    wget "https://github.com/eskimo-sh/egmi/releases/download/$EGMI_VERSION/egmi-"$EGMI_VERSION"-bin.tar.gz" > /tmp/egmi_install_log 2>&1
    fail_if_error $? "/tmp/egmi_install_log" -1
fi


echo " - Extracting egmi-$EGMI_VERSION"
tar -xvf egmi-$EGMI_VERSION-bin.tar.gz > /tmp/egmi_install_log 2>&1
fail_if_error $? "/tmp/egmi_install_log" -2

export EGMI_FOLDER=`ls -1 | grep -v tar.gz`

echo " - Installing egmi"
sudo chown root.staff -R $EGMI_FOLDER
sudo mv $EGMI_FOLDER /usr/local/lib/$EGMI_FOLDER

echo " - symlinking /usr/local/lib/egmi/ to /usr/local/lib/$EGMI_FOLDER"
sudo ln -s /usr/local/lib/$EGMI_FOLDER /usr/local/lib/egmi

echo " - Proceeding with EGMI Installation script"
sudo bash /usr/local/lib/egmi/bin/utils/__install-egmi-systemD-unit-file.sh -fs > /tmp/egmi_install_log 2>&1
fail_if_error $? "/tmp/egmi_install_log" -2

echo " - Checking EGMI Installation"

echo "   + temporary messing with config file"
sudo cp /usr/local/lib/egmi/conf/egmi.properties /usr/local/lib/egmi/conf/egmi.properties.bak

sed -i s/"zookeeper.urls=ZOOKEEPER_URL:2181"/"#zookeeper.urls=ZOOKEEPER_URL:2181"/g /usr/local/lib/egmi/conf/egmi.properties
sed -i s/"master="/"master=true"/g /usr/local/lib/egmi/conf/egmi.properties
sed -i s/"# target.predefined-ip-addresses"/"target.predefined-ip-addresses=127.0.0.1"/g /usr/local/lib/egmi/conf/egmi.properties


echo "   + Starting EGMI"
/usr/local/lib/egmi/bin/egmi.sh > /tmp/egmi_run_log 2>&1 &
EXAMPLE_PID=$!
fail_if_error $? "/tmp/egmi_run_log" -3
sleep 12

echo "   + Checking EGMI startup"
if [[ `ps | grep $EXAMPLE_PID` == "" ]]; then
    echo "EGMI process not started successfully !"
    cat /tmp/egmi_run_log
    exit 10
fi

echo "   + Restoring config file"
sudo mv -f /usr/local/lib/egmi/conf/egmi.properties.bak /usr/local/lib/egmi/conf/egmi.properties

sudo rm -Rf /tmp/egmi_setup
returned_to_saved_dir



# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"
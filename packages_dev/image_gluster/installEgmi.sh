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
    cp $EGMI_LOCAL_ARCHIVE egmi-$EGMI_VERSION.tar.gz
else
    echo " - Downloading archive egmi-$EGMI_VERSION"
    # wget https://www-eu.apache.org/dist/flink/flink-$FLINK_VERSION/flink-$FLINK_VERSION-bin-scala_$SCALA_VERSION.tgz > /tmp/flink_install_log 2>&1
    echo " - TODO TO BE IMPLEMENTED"
    exit 1
fi


echo " - Extracting egmi-$EGMI_VERSION"
tar -xvf egmi-$EGMI_VERSION.tar.gz > /tmp/egmi_install_log 2>&1
fail_if_error $? "/tmp/egmi_install_log" -2

echo " - Installing egmi"
sudo chown root.staff -R egmi-$EGMI_VERSION
sudo mv egmi-$EGMI_VERSION /usr/local/lib/egmi-$EGMI_VERSION

echo " - symlinking /usr/local/lib/egmi/ to /usr/local/lib/egmi-$EGMI_VERSION"
sudo ln -s /usr/local/lib/egmi-$EGMI_VERSION /usr/local/lib/egmi


echo " - Checking EGMI Installation"
/usr/local/lib/egmi/bin/egmi.sh > /tmp/egmi_run_log 2>&1 &
EXAMPLE_PID=$!
fail_if_error $? "/tmp/egmi_run_log" -3
sleep 10
if [[ `ps | grep $EXAMPLE_PID` == "" ]]; then
    echo "EGMI process not started successfully !"
    cat /tmp/egmi_run_log
    exit 10
fi

sudo rm -Rf /tmp/egmi_setup
returned_to_saved_dir



# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"
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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

echo "-- INSTALLING ZEPPELIN ---------------------------------------------------------"

if [ -z "$ZEPPELIN_VERSION" ]; then
    echo "Need to set ZEPPELIN_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Changing to temp directory"
mkdir -p /tmp/zeppelin_setup/
cd /tmp/zeppelin_setup/

echo " - Downloading zeppelin-$ZEPPELIN_VERSION"
wget https://www-eu.apache.org/dist/zeppelin/zeppelin-$ZEPPELIN_VERSION/zeppelin-$ZEPPELIN_VERSION-bin-netinst.tgz > /tmp/zeppelin_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad cerebro-$CEREBRO_VERSION from apache. Trying to download from niceideas.ch"
    wget http://niceideas.ch/mes/zeppelin-$ZEPPELIN_VERSION-bin-netinst.tgz >> /tmp/zeppelin_install_log 2>&1
    fail_if_error $? "/tmp/zeppelin_install_log" -1
fi

echo " - Extracting zeppelin-$ZEPPELIN_VERSION"
tar -xvf zeppelin-$ZEPPELIN_VERSION-bin-netinst.tgz > /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -2

echo " - Installing Zeppelin"
sudo chown root.staff -R zeppelin-$ZEPPELIN_VERSION-bin-netinst
sudo mv zeppelin-$ZEPPELIN_VERSION-bin-netinst /usr/local/lib/zeppelin-$ZEPPELIN_VERSION-bin-netinst

echo " - symlinking /usr/local/lib/zeppelin/ to /usr/local/lib/zeppelin-$ZEPPELIN_VERSION-bin-netinst/"
sudo ln -s /usr/local/lib/zeppelin-$ZEPPELIN_VERSION-bin-netinst /usr/local/lib/zeppelin

echo " - Installing required interpreters"
sudo /usr/local/lib/zeppelin/bin/install-interpreter.sh --name md,shell,jdbc,python,angular,elasticsearch >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -1

# ZEPPELIN 0.8.1 FIXES
# ----------------------------------------------------------------------------------------------------------------------

echo " - FIX - Fixing shell interpreter issue"
sudo bash -c "cd /usr/local/lib/zeppelin/interpreter && ln -s shell sh"

echo " - FIX - Replacing zeppelin commes-lang3 version 3.4 by spark's version 3.5"
for i in `find /usr/local/lib/zeppelin/ -name *lang3*3.4*.jar`; do
  sudo rm -f $i
  sudo ln -s /usr/local/lib/spark-2.4.3/jars/commons-lang3-3.5.jar $i
done

echo " - FIX - Replacing zeppelin commes-lang3 version 3.4 by spark's version 3.5 in spark interpreter archive"

rm -Rf /tmp/working_zeppeling_fix
mkdir /tmp/working_zeppelin_fix
cd /tmp/working_zeppelin_fix/

echo "   + copying spark interpreter"
cp /usr/local/lib/zeppelin/interpreter/spark/spark-interpreter-0.8.1.jar . >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -1

echo "   + extracting spark interpreter"
unzip spark-interpreter-0.8.1.jar  >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -1

rm -Rf spark-interpreter-0.8.1.jar

echo "   + copying spark commons-lang3"
cp /usr/local/lib/spark/jars/commons-lang3-3.5.jar . >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -1

echo "   + extracting spark commons-lang3"
unzip -o commons-lang3-3.5.jar >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -1

rm commons-lang3-3.5.jar

echo "   + recomposing archive"
zip -r spark-interpreter-0.8.1.jar . >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -1

echo "   + deleting former spark interpreter"
sudo rm -f /usr/local/lib/zeppelin/interpreter/spark/spark-interpreter-0.8.1.jar >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -1

echo "   + overwriting archive"
mv spark-interpreter-0.8.1.jar /usr/local/lib/zeppelin/interpreter/spark/spark-interpreter-0.8.1.jar >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -1

echo "   + cleanup"
cd /tmp/zeppelin_setup/
rm -Rf /tmp/working_zeppeling_fix

# ----------------------------------------------------------------------------------------------------------------------
# END OF ZEPPELIN 0.8.1 FIXES


echo " - Registering test cleaning traps"
export ZEPPELIN_PROC_ID=-1
function check_stop_zeppelin(){
    if [[ $ZEPPELIN_PROC_ID != -1 ]]; then
        echo " - Stopping Zeppelin !!"
        kill -15 $ZEPPELIN_PROC_ID
    fi
}
trap check_stop_zeppelin 15
trap check_stop_zeppelin EXIT


echo " - Starting Zeppelin"
/usr/local/lib/zeppelin/bin/zeppelin.sh >> /tmp/zeppelin_install_log 2>&1 &
export ZEPPELIN_PROC_ID=$!

echo " - Checking Zeppelin startup"
sleep 10
if [[ `ps -e | grep $ZEPPELIN_PROC_ID` == "" ]]; then
    echo " !! Failed to start Zeppelin !!"
    exit -8
fi

echo " - Stopping Zeppelin"
kill -15 $ZEPPELIN_PROC_ID
export ZEPPELIN_PROC_ID=-1

returned_to_saved_dir
sudo rm -Rf /tmp/zeppelin_setup/




# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"







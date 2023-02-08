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


echo "-- INSTALLING PYFLINK VENV ARCHIVE --------------------------------------------"

if [ -z "$FLINK_VERSION" ]; then
    echo "Need to set FLINK_VERSION environment variable before calling this script !"
    exit 1
fi

if [ -z "$FLINK_MINICONDA_VERSION" ]; then
    echo "Need to set FLINK_MINICONDA_VERSION environment variable before calling this script !"
    exit 1
fi


echo " - Changing to temp directory"
rm -Rf /tmp/pyflink_setup
mkdir -p /tmp/pyflink_setup
cd /tmp/pyflink_setup || (echo "Couldn't change to /tmp/pyflink_setup" && exit 200)



echo " - Downloading miniconda"
#wget https://repo.anaconda.com/miniconda/Miniconda3-$FLINK_MINICONDA_VERSION-Linux-x86_64.sh -O "miniconda.sh" > /tmp/pyflink_install_log 2>&1
wget "https://repo.continuum.io/miniconda/Miniconda3-$FLINK_MINICONDA_VERSION-Linux-x86_64.sh" -O "miniconda.sh" > /tmp/pyflink_install_log 2>&1
fail_if_error $? "/tmp/pyflink_install_log" -21

# fixing shell
#sed -i s/'#!\/bin\/sh'/'#!\/bin\/bash'/ ./miniconda.sh

chmod +x miniconda.sh  > /tmp/pyflink_install_log 2>&1
fail_if_error $? "/tmp/pyflink_install_log" -22

echo " - Create python virtual environment"
./miniconda.sh -b -p venv > /tmp/pyflink_install_log 2>&1
fail_if_error $? "/tmp/pyflink_install_log" -23

echo " - Activate the conda python virtual environment"
source venv/bin/activate ""  > /tmp/pyflink_install_log 2>&1
fail_if_error $? "/tmp/pyflink_install_log" -24

echo " - Installing apache flink python runtime (This takes time)"
pip install "apache-flink==$FLINK_VERSION"  > /tmp/pyflink_install_log 2>&1
fail_if_error $? "/tmp/pyflink_install_log" -25

echo " - deactivate the conda python virtual environment"
conda deactivate  > /tmp/pyflink_install_log 2>&1
fail_if_error $? "/tmp/pyflink_install_log" -26

# remove the cached packages
rm -rf venv/pkgs  > /tmp/pyflink_install_log 2>&1
fail_if_error $? "/tmp/pyflink_install_log" -27

# package the prepared conda python virtual environment
echo " - Packaging Virtual environment"
zip -r venv.zip venv  > /tmp/pyflink_install_log 2>&1
fail_if_error $? "/tmp/pyflink_install_log" -28

echo " - Installing Virtual environment"
mv venv.zip /usr/local/lib/flink/opt/python/  > /tmp/pyflink_install_log 2>&1
fail_if_error $? "/tmp/pyflink_install_log" -29

sudo rm -Rf /tmp/pyflink_setup



# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"
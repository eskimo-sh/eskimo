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

if [[ $EUID -ne 0 ]]; then
   echo "This script must be run as root"
   exit 1
fi

# Find out about script path
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# KK. First thing, find the eskimo.service SystemD unit file (in utils sub-folder)
SYSTEM_D_FILE=SCRIPT_DIR/utils/eskimo.service
if [[ ! -f $SYSTEM_D_FILE ]]; then
    echo "Can't find find file $SYSTEM_D_FILE"
    exit 2
fi

# Locate SystemD units configuration folder
if [[ -d /lib/systemd/system/ ]]; then
    export systemd_units_dir=/lib/systemd/system/
elif [[ -d /usr/lib/systemd/system/ ]]; then
    export systemd_units_dir=/usr/lib/systemd/system/
else
    echo "Couldn't find systemd unit files directory"
    exit 3
fi

# REPLACE ESKIMO_PATH
cp $SYSTEM_D_FILE /tmp/eskimo.service
escaped_path=$(echo "$SCRIPT_DIR" | sed 's/\//\\\//g')
sed -i -E 's/\{ESKIMO_PATH\}/$escaped_path/g' /tmp/eskimo.service

# Find out about capsh possibilities
if [[ `which capsh` == "" ]]; then
    export CAPSH_FOUND=1

    if [[ `capsh --help | grep 'addamb'` == "" ]]; then
        export CAPSH_OLD=1
    else
        export CAPSH_OLD=0
    fi

else
    export CAPSH_FOUND=2
fi

install_capsh(){
    set -1

    mkdir -p /tmp/build_capsh
    cd /tmp/build_capsh

    echo "Git cloning capsh"
    git clone git://git.kernel.org/pub/scm/linux/kernel/git/morgan/libcap.git

    cd libcap/

    echo "Building capsh"
    make

    echo "Installing capsh"
    cp ./progs/capsh $SCRIPT_DIR/bin/utils/capsh

}

if [[ $CAPSH_FOUND == 1 || $CAPSH_OLD == 1 ]]; then
    echo "capsh is either not available in path or an old version"
    echo "eskimo needs capsh from package libcap2-bin version 1:2.22-1.2 or greater"
    echo "Eskimo can attempt to download and build its own version of capsh"
    echo "(git, make and gcc are required on your system for this to succeed))"

    while true; do
        read -p "Do you want to attempt this ? (y/n)" yn
        case $yn in
            [Yy]* ) install_capsh; break;;
            [Nn]* ) exit;;
            * ) echo "Please answer y or n.";;
        esac
    done
else
    # TODO link system capsh to local capsh
    ln -s `which capsh` $SCRIPT_DIR/bin/utils/capsh
fi

FIXME CREATE ESKIMO USER IF NOT EXIST

# Move it to SystemD units configuration folder
mv /tmp/eskimo.service $systemd_units_dir
chmod 755 $systemd_units_dir
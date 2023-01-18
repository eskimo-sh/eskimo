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

# This script takes care of performing sanity checks to ensure SystemD will be able to start Eskimo and setup all
# the environment for this, including installing the Eskimo SystemD Unit Configuration file.

function usage() {
    echo "Usage:"
    echo "    -h  Display this help message."
    echo "    -f  assume 'y' answer to all questions'"
}

# Parse options to the install script
while getopts ":hf" opt; do
    case ${opt} in
        h )
            usage
            exit 0
        ;;
        f )
            export FORCE=force
            break
        ;;
        : )
            break
        ;;
        \? )
           echo "Invalid Option: -$OPTARG" 1>&2
           exit 1
         ;;
    esac
done

if [[ $EUID -ne 0 ]]; then
   echo "This script must be run as root"
   exit 1
fi

set -e

# Find out about script path
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# KK. First thing, find the eskimo.service SystemD unit file (in utils sub-folder)
SYSTEM_D_FILE=$SCRIPT_DIR/../../utils/eskimo.service
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

cp $SYSTEM_D_FILE /tmp/eskimo.service

# REPLACE ESKIMO_PATH
escaped_path=$(echo "$SCRIPT_DIR/../.." | sed 's/\//\\\//g')
sed -i -E "s/\{ESKIMO_PATH\}/$escaped_path/g" /tmp/eskimo.service

# Sanity checks:

# Ensure Java 11 in path (check java version)
set +e
export PATH=$JAVA_HOME/bin:$PATH
java_version=`java -version`
if [[ $? != 0 ]]; then
    echo "Could not find any java executable in PATH."
    echo "Eskimo needs to have the JDK 11 java executable in path or the JAVA_HOME env var properly defined"
    echo "Please re-launch this script after defining JAVA_HOME in the system profile or bashrc or adding the java executable in PATH"
    exit 5
fi
if [[ `echo $java_version | grep version | grep "11"` == "" ]]; then
    echo "The java version in path of JAVA_HOME is $(echo \"$java_version\" | grep version)"
    echo "Eskimo needs JDK 11 or greater to run"
    if [[ $FORCE != "force" ]]; then
        while true; do
            read -p "Please confirm your JAVA version is 11 or greater ? (y/n)" yn
            case $yn in
                [Yy]* ) break;;
                [Nn]* ) exit;;
                * ) echo "Please answer y or n.";;
            esac
        done
    fi
fi

set -e

# Handle capsh usage or installation
install_capsh(){

    echo " - checking whether gcc is installed"
    if [[ `which gcc 2>/dev/null` == "" ]]; then
        echo "!!! capsh building needs gcc installed (e.g. yum install gcc) !!! "
        echo "Cannot move forward with capsh building. Stopping here."
        echo "Please install gcc and restart this script"
        exit 4
    fi

    echo " - checking whether git is installed"
    if [[ `which git 2>/dev/null` == "" ]]; then
        echo "!!! capsh building needs git installed (e.g. yum install git) !!! "
        echo "Cannot move forward with capsh building. Stopping here."
        echo "Please install git and restart this script"
        exit 4
    fi

    echo " - checking whether libc static library is available"
    if [[ `find / -name "libc.*a" 2>/dev/null` == "" ]]; then
        echo "!!! capsh building needs static libc installed (e.g. yum install glib-static) !!! "
        echo "Cannot move forward with capsh building. Stopping here."
        echo "Please install glibc static library and restart this script"
        exit 5
    fi

    rm -Rf /tmp/build_capsh
    mkdir -p /tmp/build_capsh
    cd /tmp/build_capsh

    echo " - Git cloning capsh"
    git clone git://git.kernel.org/pub/scm/linux/kernel/git/morgan/libcap.git

    cd libcap/

    echo " - Building capsh"
    make

    echo " - Installing capsh"
    cp ./progs/capsh $SCRIPT_DIR/capsh

    rm -Rf /tmp/build_capsh
    cd
}

if [[ ! -f $SCRIPT_DIR/capsh ]]; then
    # Find out about capsh possibilities
    if [[ `which capsh 2>/dev/null` == "" ]]; then
        export CAPSH_NOT_FOUND=1
    else
        export CAPSH_NOT_FOUND=0

        if [[ `capsh --help | grep 'addamb'` == "" ]]; then
            export CAPSH_OLD=1
        else
            export CAPSH_OLD=0
        fi
    fi

    if [[ $CAPSH_NOT_FOUND == 1 || $CAPSH_OLD == 1 ]]; then
        echo "capsh is either not available in path or an old version"
        echo "eskimo needs capsh from package libcap2-bin version 1:2.22-1.2 or greater"
        echo "Eskimo can attempt to download and build its own version of capsh"
        echo "(git, make and gcc are required on your system for this to succeed))"

        if [[ $FORCE == "force" ]]; then
            install_capsh
        else
            while true; do
                read -p "Do you want to attempt this ? (y/n)" yn
                case $yn in
                    [Yy]* ) install_capsh; break;;
                    [Nn]* ) exit;;
                    * ) echo "Please answer y or n.";;
                esac
            done
        fi
    else
        # link system capsh to local capsh
        ln -s `which capsh` $SCRIPT_DIR/capsh
    fi
fi

# Handle Eskimo user creation
create_eskimo_user() {

    echo " - Creating user eskimo (if not exist)"
    useradd eskimo
    new_user_id=`id -u eskimo`
    if [[ $new_user_id == "" ]]; then
        echo "Failed to add user eskimo"
        exit 43
    fi

    echo " - Creating user system folders"

    mkdir -p /home/eskimo
    chown -R eskimo /home/eskimo

    if [ $(getent group docker) ]; then

        echo " - Adding eskimo to docker group"
        usermod -a -G docker eskimo
    fi
}

# Find out if user eskimo exists
set +e
eskimo_id=`id -u eskimo`
if [[ $eskimo_id == "" ]]; then
    echo "Eskimo runs under user 'eskimo'"
    echo "User 'eskimo' has not been found on this system"

    if [[ $FORCE == "force" ]]; then
        create_eskimo_user
    else
        while true; do
            read -p "Do you want to create user eskimo now ? (y/n)" yn
            case $yn in
                [Yy]* ) create_eskimo_user; break;;
                [Nn]* ) exit;;
                * ) echo "Please answer y or n.";;
            esac
        done
    fi
fi
set -e

# even if user already exists, I should ensure these folders exist or are created
mkdir -p /var/lib/eskimo
chown -R eskimo /var/lib/eskimo

# This is only executed if eskimo is expected in /usr/local/lib/
if [[ `find /usr/local/lib/ -name '*eskimo*'` != "" ]]; then

    # Creating eskimo symlink
    if [[ ! -L "/usr/local/lib/eskimo" || ! -d "/usr/local/lib/eskimo" ]]; then
        saved_dir=`pwd`
        cd /usr/local/lib
        rm -f eskimo
        ln -s eskimo-* eskimo
        cd $saved_dir
    fi

    # creating logs folder
    mkdir -p /var/log/eskimo
    chown -R eskimo /var/log/eskimo

    if [[ ! -L /usr/local/lib/eskimo/logs || ! -d /usr/local/lib/eskimo/logs ]]; then
        rm -Rf /usr/local/lib/eskimo/logs
        ln -s /var/log/eskimo /usr/local/lib/eskimo/logs
        chown eskimo /usr/local/lib/eskimo/logs
    fi
fi

# Need to chown services_setup, packages_dev and packages_distrib to eskimo
chown -R eskimo $SCRIPT_DIR/../../services_setup
chown -R eskimo $SCRIPT_DIR/../../packages_dev
chown -R eskimo $SCRIPT_DIR/../../packages_distrib


# Move it to SystemD units configuration folder
mv /tmp/eskimo.service $systemd_units_dir
chmod 755 $systemd_units_dir

# Try Service startup
try_eskimo_startup(){

    echo " - Starting Eskimo"
    systemctl start eskimo
}

if [[ `systemctl status eskimo | grep 'dead'` != "" ]]; then

    if [[ $FORCE == "force" ]]; then
        try_eskimo_startup
    else
        while true; do
            read -p "Do you want to try to start Eskimo as SystemD service now ? (y/n)" yn
            case $yn in
                [Yy]* ) try_eskimo_startup; break;;
                [Nn]* ) break;;
                * ) echo "Please answer y or n.";;
            esac
        done
    fi
fi

# Enable Service eskimo
enable_eskimo(){

    echo " - Enabling Eskimo"
    systemctl enable eskimo
}

if [[ `systemctl status eskimo | grep 'disabled;'` != "" ]]; then

    if [[ $FORCE == "force" ]]; then
        enable_eskimo
    else
        while true; do
            read -p "Do you want to try to Enable Eskimo to start as SystemD service on machine startup ? (y/n)" yn
            case $yn in
                [Yy]* ) enable_eskimo; break;;
                [Nn]* ) break;;
                * ) echo "Please answer y or n.";;
            esac
        done
    fi
fi
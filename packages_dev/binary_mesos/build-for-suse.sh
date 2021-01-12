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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

check_for_internet

check_for_docker


mkdir -p /tmp/build-mesos-suse
saved_dir=`pwd`
cleanup() {
    #rm -f Vagrantfile
    #rm -f ssh_key
    #rm -f ssh_key.pub
    cd $saved_dir
}
trap cleanup 15
trap cleanup EXIT

err_report() {
    echo "Error on line $1"
    tail -n 2000 /tmp/build-mesos-suse-log
}

trap 'err_report $LINENO' ERR

set -e

cp common.sh /tmp/build-mesos-suse/common.sh
chmod 755 /tmp/build-mesos-suse/common.sh

cp buildMesosFromSources.sh /tmp/build-mesos-suse/buildMesosFromSources.sh
chmod 755 /tmp/build-mesos-suse/buildMesosFromSources.sh

rm -f /tmp/build-mesos-suse-log

cp Dockerfile.suse /tmp/build-mesos-suse/Dockerfile

cd /tmp/build-mesos-suse

export NO_BASE_IMAGE=true
build_image build_suse /tmp/build-mesos-suse-log

#echo " - Destroying any previously existing VM"
#vagrant destroy --force suse-build-node >> /tmp/build-mesos-suse-log 2>&1

#echo " - Bringing Build VM up"
#if [[ $USE_VIRTUALBOX == 1 ]]; then
#    vagrant up suse-build-node >> /tmp/build-mesos-suse-log 2>&1
#else
#    vagrant up suse-build-node --provider=libvirt >> /tmp/build-mesos-suse-log 2>&1
#fi

#deb http://httpredir.debian.org/debian/ jessie main
#deb http://httpredir.debian.org/debian/ jessie main contrib non-free

echo " - refreshing the appliance"
docker exec -i build_suse bash -c "zypper --no-gpg-checks --non-interactive refresh" >> /tmp/build-mesos-suse-log 2>&1

echo " - Updating the appliance"
docker exec -i build_suse bash -c "zypper --no-gpg-checks --non-interactive update" >> /tmp/build-mesos-suse-log 2>&1

echo " - Installing OpenJDK 8 (keeping old version for mesos compatibility)"
docker exec -i build_suse bash -c "zypper install -y java-1_8_0-openjdk java-1_8_0-openjdk-devel" >> /tmp/build-mesos-suse-log 2>&1

echo " - Installing a few utility tools"
docker exec -i build_suse bash -c "zypper install -y tar wget git unzip curl moreutils sudo" >> /tmp/build-mesos-suse-log 2>&1

echo " - Installing build tools"
docker exec -i build_suse bash -c "zypper install -y -t pattern devel_basis" >> /tmp/build-mesos-suse-log 2>&1

echo " - Installing C/C++development tools"
docker exec -i build_suse bash -c "zypper install -y -t pattern devel_C_C++" >> /tmp/build-mesos-suse-log 2>&1

#echo " - Installing swapspace"
#docker exec -i build_suse bash -c "yum install -y swapspace" >> /tmp/build-mesos-suse-log 2>&1

#echo " - installing epel release repository"
#docker exec -i build_suse bash -c "yum install -y epel-release" >> /tmp/build-mesos-suse-log 2>&1

echo " - Installing python"
docker exec -i build_suse bash -c "zypper install -y python-devel python-six python-virtualenv python-pip" >> /tmp/build-mesos-suse-log 2>&1

echo " - Installing Devel repos"
docker exec -i build_suse bash -c "zypper addrepo https://download.opensuse.org/repositories/devel:tools:building/openSUSE_Leap_15.1/devel:tools:building.repo" >> /tmp/build-mesos-suse-log 2>&1
docker exec -i build_suse bash -c "zypper addrepo https://download.opensuse.org/repositories/Java:packages/openSUSE_Leap_15.1/Java:packages.repo" >> /tmp/build-mesos-suse-log 2>&1

echo " - Installing g++ explictely"
docker exec -i build_suse bash -c "zypper --no-gpg-checks install -y gcc-c++" >> /tmp/build-mesos-suse-log 2>&1


echo " - Installing maven "
docker exec -i build_suse bash -c "zypper --no-gpg-checks --non-interactive refresh" >> /tmp/build-mesos-suse-log 2>&1
docker exec -i build_suse bash -c "zypper --no-gpg-checks install -y maven" >> /tmp/build-mesos-suse-log 2>&1

echo " - Installing other Mesos dependencies"
docker exec -i build_suse bash -c "zypper --no-gpg-checks install -y zlib-devel libcurl-devel openssl-devel cyrus-sasl-devel cyrus-sasl-plain cyrus-sasl-crammd5 apr-devel subversion-devel apr-util-devel" >> /tmp/build-mesos-suse-log 2>&1


echo " - Finding java"
JAVA_HOME=`docker exec -i build_suse bash -c "find /usr/lib64/jvm/*  -type d -maxdepth 0"`

echo " - Building Mesos"
docker exec -i build_suse bash -c "JAVA_HOME=$JAVA_HOME /scripts/buildMesosFromSources.sh" suse-build-node

#echo " - Creating archive mesos-$AMESOS_VERSION.tar.gz"
#docker exec -i build_suse bash -c "bash -c \"cd /usr/local/lib && tar cvfz mesos-$AMESOS_VERSION.tar.gz mesos-$AMESOS_VERSION\"" >> /tmp/build-mesos-debian-log 2>&1

echo " - Copying Mesos archive to shared folder"
docker exec -i build_suse bash -c "mv /usr/local/lib/mesos-$AMESOS_VERSION.tar.gz /scripts/" >> /tmp/build-mesos-debian-log 2>&1

#echo " - Dowloading mesos archive"
#chmod 700 ssh_key
#chmod 700 ssh_key.pub
#scp -o StrictHostKeyChecking=no -i ssh_key vagrant@192.168.10.105:/vagrant/mesos-$AMESOS_VERSION.tar.gz . >> /tmp/build-mesos-debian-log 2>&1

if [[ -f "$SCRIPT_DIR/../../packages_distrib/eskimo_mesos-suse-$AMESOS_VERSION.tar.gz" ]]; then
    echo " - renaming previous eskimo_mesos-suse-$AMESOS_VERSION.tar.gz"
    mv $SCRIPT_DIR/../../packages_distrib/eskimo_mesos-suse-$AMESOS_VERSION.tar.gz $SCRIPT_DIR/../../packages_distrib/eskimo_mesos-suse-$AMESOS_VERSION.tar.gz.old
fi

echo " - Copying Mesos archive to distribution folder"
mv mesos-$AMESOS_VERSION.tar.gz $SCRIPT_DIR/../../packages_distrib/eskimo_mesos-suse_"$AMESOS_VERSION"_1.tar.gz

echo " - Destroying Docker Container"
#vagrant destroy --force suse-build-node  >> /tmp/build-mesos-suse-log 2>&1

docker stop build_suse

docker container rm build_suse
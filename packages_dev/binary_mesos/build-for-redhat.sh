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


mkdir -p /tmp/build-mesos-redhat
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
    tail -n 2000 /tmp/build-mesos-redhat-log
}

trap 'err_report $LINENO' ERR

set -e

cp common.sh /tmp/build-mesos-redhat/common.sh
chmod 755 /tmp/build-mesos-redhat/common.sh

cp buildMesosFromSources.sh /tmp/build-mesos-redhat/buildMesosFromSources.sh
chmod 755 /tmp/build-mesos-redhat/buildMesosFromSources.sh

rm -f /tmp/build-mesos-redhat-log

cp Dockerfile.redhat /tmp/build-mesos-redhat/Dockerfile

cd /tmp/build-mesos-redhat

export NO_BASE_IMAGE=true
build_image build_redhat /tmp/build-mesos-redhat-log

#echo " - Destroying any previously existing VM"
#vagrant destroy --force rhel-build-node > /tmp/build-mesos-redhat-log 2>&1

#echo " - Bringing Build VM up"
#if [[ $USE_VIRTUALBOX == 1 ]]; then
#    vagrant up rhel-build-node > /tmp/build-mesos-redhat-log 2>&1
#else
#    vagrant up rhel-build-node --provider=libvirt > /tmp/build-mesos-redhat-log 2>&1
#fi

#deb http://httpredir.debian.org/debian/ jessie main
#deb http://httpredir.debian.org/debian/ jessie main contrib non-free

echo " - Updating the appliance"
docker exec -i build_redhat bash -c "yum update -y" > /tmp/build-mesos-redhat-log 2>&1

echo " - Installing OpenJDK 8 (keeping old version for mesos compatibility)"
docker exec -i build_redhat bash -c "yum install -y java-1.8.0-openjdk java-1.8.0-openjdk-devel" > /tmp/build-mesos-redhat-log 2>&1

echo " - Installing a few utility tools"
docker exec -i build_redhat bash -c "yum install -y tar wget git unzip curl moreutils sudo" > /tmp/build-mesos-redhat-log 2>&1

echo " - Installing development tools"
docker exec -i build_redhat bash -c "yum groupinstall -y 'Development Tools'" > /tmp/build-mesos-redhat-log 2>&1

echo " - Installing build tools"
docker exec -i build_redhat bash -c "yum install -y devtoolset-2-toolchain autoconf libtool" > /tmp/build-mesos-redhat-log 2>&1

#echo " - Installing swapspace"
#docker exec -i build_redhat bash -c "yum install -y swapspace" > /tmp/build-mesos-redhat-log 2>&1

echo " - installing epel release repository"
docker exec -i build_redhat bash -c "yum install -y epel-release" > /tmp/build-mesos-redhat-log 2>&1

echo " - Installing python"
docker exec -i build_redhat bash -c "yum install -y python-devel python-six python-virtualenv python-pip" > /tmp/build-mesos-redhat-log 2>&1

echo " - Installing maven repository"
docker exec -i build_redhat bash -c "wget http://repos.fedorapeople.org/repos/dchen/apache-maven/epel-apache-maven.repo -O /etc/yum.repos.d/epel-apache-maven.repo" > /tmp/build-mesos-redhat-log 2>&1

echo " - Installing other Mesos dependencies"
docker exec -i build_redhat bash -c "yum install -y  apache-maven zlib-devel libcurl-devel openssl-devel cyrus-sasl-devel cyrus-sasl-md5 apr-devel subversion-devel apr-util-devel" > /tmp/build-mesos-redhat-log 2>&1

echo " - Finding java"
JAVA_HOME=`docker exec -i build_redhat bash -c "find /usr/lib/jvm/*  -type d -maxdepth 0"`

echo " - Building Mesos"
docker exec -i build_redhat bash -c "JAVA_HOME=$JAVA_HOME /scripts/buildMesosFromSources.sh"

#echo " - Creating archive mesos-$AMESOS_VERSION.tar.gz"
#docker exec -i build_redhat bash -c "bash -c \"cd /usr/local/lib && tar cvfz mesos-$AMESOS_VERSION.tar.gz mesos-$AMESOS_VERSION\"" > /tmp/build-mesos-redhat-log 2>&1

echo " - Copying Mesos archive to shared folder"
docker exec -i build_redhat bash -c "mv /usr/local/lib/mesos-$AMESOS_VERSION.tar.gz /scripts/" > /tmp/build-mesos-redhat-log 2>&1

#echo " - Dowloading mesos archive"
#chmod 700 ssh_key
#chmod 700 ssh_key.pub
#scp -o StrictHostKeyChecking=no -i ssh_key vagrant@192.168.10.103:/vagrant/mesos-$AMESOS_VERSION.tar.gz . > /tmp/build-mesos-redhat-log 2>&1

if [[ -f "$SCRIPT_DIR/../../packages_distrib/eskimo_mesos-redhat-$AMESOS_VERSION.tar.gz" ]]; then
    echo " - renaming previous eskimo_mesos-redhat-$AMESOS_VERSION.tar.gz"
    mv $SCRIPT_DIR/../../packages_distrib/eskimo_mesos-redhat-$AMESOS_VERSION.tar.gz $SCRIPT_DIR/../../packages_distrib/eskimo_mesos-redhat-$AMESOS_VERSION.tar.gz.old
fi

echo " - Copying Mesos archive to distribution folder"
mv mesos-$AMESOS_VERSION.tar.gz $SCRIPT_DIR/../../packages_distrib/eskimo_mesos-redhat_"$AMESOS_VERSION"_1.tar.gz

echo " - Destroying Docker Container"
#vagrant destroy --force rhel-build-node  > /tmp/build-mesos-redhat-log 2>&1

docker stop build_redhat

docker container rm build_redhat
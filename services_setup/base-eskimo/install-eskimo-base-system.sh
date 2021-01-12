#!/bin/bash

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

export PATH=/bin:/sbin/:/usr/bin:/usr/sbin:/usr/local/bin:/usr/local/sbin:$PATH

function fail_if_error(){
    if [[ $1 != 0 ]]; then
        echo " -> failed !!"
        cat $2
        exit $3
    fi
}

# Make sure we have sudo access
echo "  - checking if user $USER has sudo access"
sudo -n ls /dev/null >/dev/null 1>&2
if [[ $? != 0 ]]; then
    echoerr "$USER requires sudo access on machine $HOSTNAME"
    exit 1
fi

# make sure systemd is installed
echo "  - checking if systemd is running"
pidof_command=`which pidof`
if [[ -f "/etc/debian_version" ]]; then
    systemd_command=`which systemd`
    if [[ ! `$pidof_command $systemd_command` ]]; then
        echoerr "Systemd is not running on node !"
        exit 101
    fi
else
    # works for both suse and RHEL
    if [[ ! `$pidof_command /usr/lib/systemd/systemd` ]]; then
        echoerr "Systemd is not running on node !"
        exit 102
    fi
fi


function enable_docker() {

    echo "  - Enabling docker service"
    sudo systemctl enable docker >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to enable docker"
        cat /tmp/install_docker 1>&2
        exit 71
    fi

    echo "  - Starting docker service"
    sudo systemctl start docker >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to start docker"
        cat /tmp/install_docker 1>&2
        exit 72
    fi

    echo "  - Adding current user to docker group"
    sudo usermod -a -G docker $USER >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to add user $USER to docker"
        cat /tmp/install_docker 1>&2
        exit 73
    fi

    echo "  - Registering marathon.registry as insecure registry"
    cat > /tmp/daemon.json <<- "EOF"
{
  "insecure-registries" : ["marathon.registry:5000"]
}

EOF

    sudo mv /tmp/daemon.json /etc/docker/daemon.json
    sudo chown root. /etc/docker/daemon.json

    echo "  - Restart docker"
    sudo systemctl restart docker >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to reload docker"
        cat /tmp/install_docker 1>&2
        exit 74
    fi

}

function install_docker_suse_based() {

    rm -Rf /tmp/install_docker

    sudo zypper install -y docker >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to install required packages"
        cat /tmp/install_docker 1>&2
        exit 69
    fi
}

function install_docker_redhat_based() {

    rm -Rf /tmp/install_docker

    echo "  - Install required packages. "
    sudo yum install -y yum-utils \
            device-mapper-persistent-data \
            lvm2 >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to install required packages"
        cat /tmp/install_docker 1>&2
        exit 66
    fi

    echo "  - set up the stable repository."
    if [[ -f /usr/bin/dnf ]]; then
        sudo dnf config-manager \
                --add-repo \
                https://download.docker.com/linux/$LINUX_DISTRIBUTION/docker-ce.repo  >>/tmp/install_docker 2>&1
    else
        sudo yum-config-manager \
                --add-repo \
                https://download.docker.com/linux/$LINUX_DISTRIBUTION/docker-ce.repo  >>/tmp/install_docker 2>&1
    fi
    if [[ $? != 0 ]]; then
        echoerr "Unable to setup stable repo"
        cat /tmp/install_docker 1>&2
        exit 67
    fi

    echo "  - Install the latest version of Docker CE and containerd"
    sudo yum install -y docker-ce docker-ce-cli containerd.io >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to install docker"
        cat /tmp/install_docker 1>&2
        exit 68
    fi
}

function install_docker_debian_based() {

    rm -Rf /tmp/install_docker

    echo "  - install packages to allow apt to use a repository over HTTPS"
    sudo DEBIAN_FRONTEND=noninteractive apt-get -yq install \
            apt-transport-https \
            ca-certificates \
            curl \
            software-properties-common >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to install packages"
        cat /tmp/install_docker 1>&2
        exit 61
    fi

    echo "  - attempting packages installation that are different between ubuntu and debian"
    sudo DEBIAN_FRONTEND=noninteractive apt-get -yq install gnupg-agent >/dev/null 2>&1
    sudo DEBIAN_FRONTEND=noninteractive apt-get -yq install gnupg2 >/dev/null 2>&1

    echo "  - Add Docker’s official GPG key"
    curl -fsSL https://download.docker.com/linux/$LINUX_DISTRIBUTION/gpg | sudo apt-key add -  >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to add docker GPG key"
        cat /tmp/install_docker 1>&2
        exit 62
    fi

    echo "  - Add Docker repository"
    sudo add-apt-repository \
           "deb [arch=amd64] https://download.docker.com/linux/$LINUX_DISTRIBUTION \
           $(lsb_release -cs) \
           stable" >>/tmp/install_docker 2>&1

    if [[ $? != 0 ]]; then
        echoerr "Unable to add docker repository"
        cat /tmp/install_docker 1>&2
        exit 63
    fi

    echo "  - Update the apt package index."
    sudo DEBIAN_FRONTEND=noninteractive apt-get -yq update >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to apt package index"
        # This error is accepted for now, we'll see if we can move further ...
        #cat /tmp/install_docker 1>&2
        #exit 64
    fi

    echo "  - Install the latest version of Docker CE and containerd"
    sudo DEBIAN_FRONTEND=noninteractive apt-get -yq install docker-ce docker-ce-cli containerd.io >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to install docker"
        cat /tmp/install_docker 1>&2
        exit 65
    fi
}

function install_suse_mesos_dependencies() {

    echo " - Installing other Mesos dependencies"
    sudo zypper install -y zlib-devel libcurl-devel openssl-devel cyrus-sasl-devel cyrus-sasl-plain cyrus-sasl-crammd5 apr-devel subversion-devel apr-util-devel >> /tmp/setup_log 2>&1
     if [[ $? != 0 ]]; then
        echoerr "Unable to install mesos dependencies"
        cat /tmp/setup_log 1>&2
        exit 53
    fi

}

function install_redhat_mesos_dependencies() {

    echo " - Installing other Mesos dependencies"
    sudo yum install -y zlib-devel libcurl-devel openssl-devel cyrus-sasl-devel cyrus-sasl-md5 apr-devel subversion-devel apr-util-devel >> /tmp/setup_log 2>&1
     if [[ $? != 0 ]]; then
        echoerr "Unable to install mesos dependencies"
        cat /tmp/setup_log 1>&2
        exit 52
    fi

}

function install_debian_mesos_dependencies() {

    echo " - Installing other Mesos dependencies"
    sudo DEBIAN_FRONTEND=noninteractive apt-get -y install \
            libcurl4-nss-dev libsasl2-dev libsasl2-modules maven libapr1-dev libsvn-dev zlib1g-dev >> /tmp/setup_log 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to install mesos dependencies"
        cat /tmp/setup_log 1>&2
        exit 51
    fi

}

function create_user_infrastructure() {

    if [[ "$1" == "" ]]; then
        echo "Expecting user name as first argument"
        exit 41
    fi
    USER_NAME=$1

    if [[ "$2" == "" ]]; then
        echo "Expecting user ID as second argument"
        exit 42
    fi
    USER_ID=$2

    echo " - Creating user $USER_NAME (if not exist)"
    new_user_id=`id -u $USER_NAME 2>> /tmp/setup_log`
    if [[ $new_user_id == "" ]]; then
        sudo useradd -u $USER_ID $USER_NAME
        new_user_id=`id -u $USER_NAME 2>> /tmp/setup_log`
        if [[ $new_user_id == "" ]]; then
            echo "Failed to add user $USER_NAME"
            exit 43
        fi
    fi

    echo " - Creating user system folders"
    sudo mkdir -p /var/lib/$USER_NAME
    sudo chown -R $USER_NAME /var/lib/$USER_NAME

    sudo mkdir -p /var/log/$USER_NAME
    sudo chown -R $USER_NAME /var/log/$USER_NAME

    sudo mkdir -p /var/run/$USER_NAME
    sudo chown -R $USER_NAME /var/run/$USER_NAME
}

function create_common_system_users() {

    create_user_infrastructure elasticsearch 3301

    create_user_infrastructure spark 3302

    create_user_infrastructure kafka 3303

    create_user_infrastructure grafana 3304

    create_user_infrastructure flink 3305

    create_user_infrastructure marathon 3306
}

# System Installation
# ----------------------------------------------------------------------------------------------------------------------

rm -Rf /tmp/setup_log

# Extract Linux distribution
export LINUX_DISTRIBUTION=`awk -F= '/^NAME/{print $2}' /etc/os-release | cut -d ' ' -f 1 | tr -d \" | tr '[:upper:]' '[:lower:]'`
echo "  - Linux distribution is $LINUX_DISTRIBUTION"


if [[ -f "/etc/debian_version" ]]; then

    echo "  - updating apt package index"
    sudo DEBIAN_FRONTEND=noninteractive apt-get -yq update >>/tmp/setup_log 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to update apt package index"
        # attempting to continue further
        #cat /tmp/setup_log 1>&2
        #exit -1
    fi

    echo "  - installing some required dependencies"
    sudo DEBIAN_FRONTEND=noninteractive apt-get -yq install net-tools >> /tmp/setup_log 2>&1
    fail_if_error $? "/tmp/setup_log" -1

    # ignore this one if it fails
    sudo DEBIAN_FRONTEND=noninteractive apt-get -yq install attr >> /tmp/setup_log 2>&1

elif [[ -f "/etc/redhat-release" ]]; then

    echo "  - updating apt package index"
    sudo yum -y update >> /tmp/setup_log 2>&1
    fail_if_error $? "/tmp/setup_log" -1

    echo "  - installing some required dependencies"
    sudo yum install -y net-tools anacron >> /tmp/setup_log 2>&1
    fail_if_error $? "/tmp/setup_log" -1

    echo "  - enabling crond"
    sudo systemctl enable crond >> /tmp/setup_log 2>&1
    fail_if_error $? "/tmp/setup_log" -1

    sudo systemctl start crond >> /tmp/setup_log 2>&1
    fail_if_error $? "/tmp/setup_log" -1

elif [[ -f "/etc/SUSE-brand" ]]; then

    echo "  - updating zypper package index"
    sudo bash -c "zypper --non-interactive refresh | echo 'a'" >> /tmp/setup_log 2>&1
    fail_if_error $? "/tmp/setup_log" -1

    echo "  - installing some required dependencies"
    sudo zypper install -y net-tools cron >> /tmp/setup_log 2>&1
    fail_if_error $? "/tmp/setup_log" -1

    echo "  - enabling cron"
    sudo systemctl enable cron >> /tmp/setup_log 2>&1
    fail_if_error $? "/tmp/setup_log" -1

    sudo systemctl start cron >> /tmp/setup_log 2>&1
    fail_if_error $? "/tmp/setup_log" -1

else
    echo " - !! ERROR : Could not find any brand marker file "
    echo "   + none of /etc/debian_version, /etc/redhat-release or /etc/SUSE-brand exist"
    exit 101

fi


echo "  - installing mesos dependencies"
if [[ -f "/etc/debian_version" ]]; then
    install_debian_mesos_dependencies
elif [[ -f "/etc/redhat-release" ]]; then
    install_redhat_mesos_dependencies
elif [[ -f "/etc/SUSE-brand" ]]; then
    install_suse_mesos_dependencies
else
    echo " - !! ERROR : Could not find any brand marker file "
    echo "   + none of /etc/debian_version, /etc/redhat-release or /etc/SUSE-brand exist"
    exit 102
fi


echo " - Installing gluster client"
if [[ -f "/etc/debian_version" ]]; then
    sudo apt-get -y install glusterfs-client >> /tmp/setup_log 2>&1
    fail_if_error $? "/tmp/setup_log" -1

elif [[ -f "/etc/redhat-release" ]]; then
    sudo yum -y install glusterfs glusterfs-fuse >> /tmp/setup_log 2>&1
    fail_if_error $? "/tmp/setup_log" -1

elif [[ -f "/etc/SUSE-brand" ]]; then
    sudo zypper install -y glusterfs  >> /tmp/setup_log 2>&1
    fail_if_error $? "/tmp/setup_log" -1

else
    echo " - !! ERROR : Could not find any brand marker file "
    echo "   + none of /etc/debian_version, /etc/redhat-release or /etc/SUSE-brand exist"
    exit 103
fi


# Check if docker is installed
echo "  - checking if docker is installed"
docker -v 2>/dev/null
if [[ $? != 0 ]]; then

    echo "  - docker is not installed. attempting installation"
    if [[ -f "/etc/debian_version" ]]; then
        install_docker_debian_based

    elif [[ -f "/etc/redhat-release" ]]; then

        install_docker_redhat_based

    elif [[ -f "/etc/SUSE-brand" ]]; then

        install_docker_suse_based
    else

        echo " - !! ERROR : Could not find any brand marker file "
        echo "   + none of /etc/debian_version, /etc/redhat-release or /etc/SUSE-brand exist"
        exit 104

    fi
fi

echo "  - Enabling docker"
enable_docker


echo " - Disabling IPv6"

sudo sysctl -w net.ipv6.conf.all.disable_ipv6=1  >> /tmp/setup_log 2>&1
fail_if_error $? "/tmp/setup_log" -1

sudo sysctl -w net.ipv6.conf.default.disable_ipv6=1  >> /tmp/setup_log 2>&1
fail_if_error $? "/tmp/setup_log" -1

for i in `/sbin/ip -o -4 address | awk '{print $2}'`; do
    sudo sysctl -w net.ipv6.conf.$i.disable_ipv6=1  >> /tmp/setup_log 2>&1
    fail_if_error $? "/tmp/setup_log" -1
done



if [[ `grep net.ipv6.conf.all.disable_ipv6=1 /etc/sysctl.conf` == "" ]]; then
    sudo bash -c 'echo -e "\nnet.ipv6.conf.all.disable_ipv6=1" >>  /etc/sysctl.conf'
fi

if [[ `grep net.ipv6.conf.default.disable_ipv6=1 /etc/sysctl.conf` == "" ]]; then
    sudo bash -c 'echo -e "net.ipv6.conf.default.disable_ipv6=1" >>  /etc/sysctl.conf'
fi

for i in `/sbin/ip -o -4 address | awk '{print $2}'`; do
    if [[ `grep net.ipv6.conf.$i.disable_ipv6=1 /etc/sysctl.conf` == "" ]]; then
        sudo bash -c "echo -e \"net.ipv6.conf.$i.disable_ipv6=1\" >>  /etc/sysctl.conf"
    fi
done

echo " - Increasing system vm.max_map_count setting"
sudo bash -c 'echo -e "\nvm.max_map_count=262144" >>  /etc/sysctl.conf'
sudo sysctl -w vm.max_map_count=262144 > /tmp/setup_log 2>&1
fail_if_error $? "/tmp/setup_log" -1

echo " - Disable selinux if enabled"
if [[ -f /etc/selinux/config ]]; then
    sudo sed -i s/"SELINUX=enforcing"/"SELINUX=permissive"/g /etc/selinux/config
    sudo setenforce 0 2>/dev/null # ignoring errors
fi

if [[ `mount | grep /sys/fs/cgroup/systemd` == "" ]]; then
    echo " - cgroup creation hack"
    sudo mkdir /sys/fs/cgroup/systemd
    sudo mount -t cgroup -o none,name=systemd cgroup /sys/fs/cgroup/systemd
fi

# Make sur some required packages are installed
#echo "  - checking some key packages"

echo "  - Creating common system users"
create_common_system_users
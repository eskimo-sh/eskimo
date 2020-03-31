#!/bin/bash

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
    exit -1
fi

# make sure systemd is installed
echo "  - checking if systemd is running"
pidof_command=`which pidof`
if [[ -f "/etc/debian_version" ]]; then
    systemd_command=`which systemd`
    if [[ ! `$pidof_command $systemd_command` ]]; then
        echoerr "Systemd is not running on node !"
        exit -101
    fi
else
    # works for both suse and RHEL
    if [[ ! `$pidof_command /usr/lib/systemd/systemd` ]]; then
        echoerr "Systemd is not running on node !"
        exit -101
    fi
fi


function enable_docker() {

    echo "  - Enabling docker service"
    sudo systemctl enable docker >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to enable docker"
        cat /tmp/install_docker 1>&2
        exit -1
    fi

    echo "  - Starting docker service"
    sudo systemctl start docker >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to start docker"
        cat /tmp/install_docker 1>&2
        exit -1
    fi

    echo "  - Adding current user to docker group"
    sudo usermod -a -G docker $USER >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to add user $USER to docker"
        cat /tmp/install_docker 1>&2
        exit -1
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
        exit -1
    fi

}

function install_docker_suse_based() {

    rm -Rf /tmp/install_docker

    sudo zypper install -y docker >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to install required packages"
        cat /tmp/install_docker 1>&2
        exit -1
    fi

    enable_docker
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
        exit -1
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
        exit -1
    fi

    echo "  - Install the latest version of Docker CE and containerd"
    sudo yum install -y docker-ce docker-ce-cli containerd.io >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to install docker"
        cat /tmp/install_docker 1>&2
        exit -1
    fi

    enable_docker
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
        exit -1
    fi

    echo "  - attempting packages installation that are different between ubuntu and debian"
    sudo DEBIAN_FRONTEND=noninteractive apt-get -yq install gnupg-agent >/dev/null 2>&1
    sudo DEBIAN_FRONTEND=noninteractive apt-get -yq install gnupg2 >/dev/null 2>&1

    echo "  - Add Docker’s official GPG key"
    curl -fsSL https://download.docker.com/linux/$LINUX_DISTRIBUTION/gpg | sudo apt-key add -  >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to add docker GPG key"
        cat /tmp/install_docker 1>&2
        exit -1
    fi

    echo "  - Add Docker repository"
    sudo add-apt-repository \
           "deb [arch=amd64] https://download.docker.com/linux/$LINUX_DISTRIBUTION \
           $(lsb_release -cs) \
           stable" >>/tmp/install_docker 2>&1

    if [[ $? != 0 ]]; then
        echoerr "Unable to add docker repository"
        cat /tmp/install_docker 1>&2
        exit -1
    fi

    echo "  - Update the apt package index."
    sudo DEBIAN_FRONTEND=noninteractive apt-get -yq update >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to apt package index"
        cat /tmp/install_docker 1>&2
        exit -1
    fi

    echo "  - Install the latest version of Docker CE and containerd"
    sudo DEBIAN_FRONTEND=noninteractive apt-get -yq install docker-ce docker-ce-cli containerd.io >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to install docker"
        cat /tmp/install_docker 1>&2
        exit -1
    fi

    enable_docker
}

function install_suse_mesos_dependencies() {

    echo " - Installing other Mesos dependencies"
    sudo zypper install -y zlib-devel libcurl-devel openssl-devel cyrus-sasl-devel cyrus-sasl-plain cyrus-sasl-crammd5 apr-devel subversion-devel apr-util-devel >> /tmp/setup_log 2>&1
     if [[ $? != 0 ]]; then
        echoerr "Unable to install mesos dependencies"
        cat /tmp/setup_log 1>&2
        exit -1
    fi

}

function install_redhat_mesos_dependencies() {

    echo " - Installing other Mesos dependencies"
    sudo yum install -y zlib-devel libcurl-devel openssl-devel cyrus-sasl-devel cyrus-sasl-md5 apr-devel subversion-devel apr-util-devel >> /tmp/setup_log 2>&1
     if [[ $? != 0 ]]; then
        echoerr "Unable to install mesos dependencies"
        cat /tmp/setup_log 1>&2
        exit -1
    fi

}

function install_debian_mesos_dependencies() {

    echo " - Installing other Mesos dependencies"
    sudo DEBIAN_FRONTEND=noninteractive apt-get -y install \
            libcurl4-nss-dev libsasl2-dev libsasl2-modules maven libapr1-dev libsvn-dev zlib1g-dev >> /tmp/setup_log 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to install mesos dependencies"
        cat /tmp/setup_log 1>&2
        exit -1
    fi

}

function create_common_system_users() {

    echo " - Creating elasticsearch user (if not exist)"
    elasticsearch_user_id=`id -u elasticsearch 2>> /tmp/setup_log`
    if [[ $elasticsearch_user_id == "" ]]; then
        sudo useradd -u 3301 elasticsearch
        elasticsearch_user_id=`id -u elasticsearch 2>> /tmp/setup_log`
        if [[ $elasticsearch_user_id == "" ]]; then
            echo "Failed to add user elasticsearch"
            exit -4
        fi
    fi

    echo " - Creating spark user (if not exist)"
    export spark_user_id=`id -u spark 2>> /tmp/setup_log`
    if [[ $spark_user_id == "" ]]; then
        sudo useradd -u 3302 spark
        spark_user_id=`id -u spark 2>> /tmp/setup_log`
        if [[ $spark_user_id == "" ]]; then
            echo "Failed to add user spark"
            exit -4
        fi
    fi

    echo " - Creating kafka user (if not exist)"
    kafka_user_id=`id -u kafka 2>> /tmp/setup_log`
    if [[ $kafka_user_id == "" ]]; then
        sudo useradd -u 3303 kafka
        kafka_user_id=`id -u kafka 2>> /tmp/setup_log`
        if [[ $kafka_user_id == "" ]]; then
            echo "Failed to add user kafka"
            exit -4
        fi
    fi

    echo " - Creating grafana user (if not exist)"
    grafana_user_id=`id -u grafana 2>> /tmp/setup_log`
    if [[ $grafana_user_id == "" ]]; then
        sudo useradd -u 3304 grafana
        grafana_user_id=`id -u grafana 2>> /tmp/setup_log`
        if [[ $grafana_user_id == "" ]]; then
            echo "Failed to add user grafana"
            exit -4
        fi
    fi

    echo " - Creating flink user (if not exist)"
    export flink_user_id=`id -u flink 2>> /tmp/setup_log`
    if [[ $flink_user_id == "" ]]; then
        sudo useradd -u 3305 flink
        flink_user_id=`id -u flink 2>> /tmp/setup_log`
        if [[ $flink_user_id == "" ]]; then
            echo "Failed to add user flink"
            exit -4
        fi
    fi

    echo " - Creating marathon user (if not exist)"
    export marathon_user_id=`id -u marathon 2>> /tmp/setup_log`
    if [[ $marathon_user_id == "" ]]; then
        sudo useradd -u 3306 marathon
        marathon_user_id=`id -u marathon 2>> /tmp/setup_log`
        if [[ $marathon_user_id == "" ]]; then
            echo "Failed to add user marathon"
            exit -4
        fi
    fi

}

# System Installation
# ----------------------------------------------------------------------------------------------------------------------

rm -Rf /tmp/setup_log

# Extract Linux distribution
export LINUX_DISTRIBUTION=`awk -F= '/^NAME/{print $2}' /etc/os-release | cut -d ' ' -f 1 | tr -d \" | tr '[:upper:]' '[:lower:]'`
echo "  - Linux distribution is $LINUX_DISTRIBUTION"


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
        exit -101

    fi
fi


if [[ -f "/etc/debian_version" ]]; then

    echo "  - updating apt package index"
    sudo DEBIAN_FRONTEND=noninteractive apt-get -yq update >>/tmp/install_docker 2>&1
    if [[ $? != 0 ]]; then
        echoerr "Unable to update apt package index"
        cat /tmp/install_docker 1>&2
        exit -1
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
    exit -101

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
    exit -101
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
    exit -101    
fi


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

echo " - Creating gluster shares (or not) manipulation commodity script"

cat > /tmp/handle_gluster_share.sh <<- "EOF"
#!/bin/bash
if [[ $1 == "" ]]; then
    echo "Expecting Gluster share name as first argument"
    exit -1
fi
export SHARE_NAME=$1

if [[ $2 == "" ]]; then
    echo "Expecting share full path as second argument"
    exit -2
fi
export SHARE_PATH=$2

if [[ $3 == "" ]]; then
    echo "Expecting share user name as third argument"
    exit -3
fi
export SHARE_USER=$3

# find out if gluster is available
if [[ -f /etc/eskimo_topology.sh && `cat /etc/eskimo_topology.sh  | grep MASTER_GLUSTER` != "" ]]; then
    export GLUSTER_AVAILABLE=1
else
    export GLUSTER_AVAILABLE=0
fi

# Only if gluster is enabled
if [[ $GLUSTER_AVAILABLE == 1 ]]; then

    echo " - Proceeding with gluster mount of $SHARE_PATH"
    /usr/local/sbin/gluster_mount.sh $SHARE_NAME $SHARE_PATH $SHARE_USER `/usr/bin/id -u $SHARE_USER`
    
else

    echo " - Not mounting gluster shares since not working in cluster mode"

    echo " - Getting rid of former gluster mount of $SHARE_PATH if any"

    if [[ `grep $SHARE_NAME /etc/mtab` != "" ]]; then
        echo "   + umounting gluster $SHARE_NAME"
        umount $SHARE_PATH
        if [[ $? != 0 ]]; then
            umount -l $SHARE_PATH
        fi
    fi

    if [[ `grep $SHARE_NAME /etc/fstab` != "" ]]; then
        echo "   + removing gluster $SHARE_NAME from /etc/fstab"
        sed -i "/$SHARE_NAME/d" /etc/fstab
    fi

    if [[ ! -d $SHARE_PATH ]]; then
        echo " - Creating $SHARE_PATH"
        mkdir -p $SHARE_PATH
    fi

    if [[ `stat -c '%U' $SHARE_PATH` != "$SHARE_USER" ]]; then
        echo " - Changing owner of $SHARE_PATH"
        chown -R $SHARE_USER $SHARE_PATH
    fi

    if [[ `ls -ld $SHARE_PATH | grep drwxrwxrw` == "" ]]; then
        echo " - Changing rights of $SHARE_PATH"
        chmod -R 777 $SHARE_PATH
    fi   

fi
EOF

sudo mv /tmp/handle_gluster_share.sh /usr/local/sbin/handle_gluster_share.sh
sudo chmod 755 /usr/local/sbin/handle_gluster_share.sh


# Make sur some required packages are installed
#echo "  - checking some key packages"

echo "  - Creating common system users"
create_common_system_users
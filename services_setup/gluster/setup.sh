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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

# Change current folder to script dir (important !)
cd $SCRIPT_DIR

# Loading topology
loadTopology

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit -1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit -2
fi

# reinitializing log
sudo rm -f gluster_install_log


echo " - Creating required folders"
sudo systemctl stop gluster 2>/dev/null

# The problem here if I remove that folder is that the existing gluster bricks shared are destroyed
# and lost for good.
# This is lieky better that the chances to lead to bricks being unrecoverable in anyway and hence gluster refusing
# to start. I am hence keeping it for now.
#sudo rm -Rf /var/lib/gluster

sudo mkdir -p /var/lib/gluster


echo " - Building container gluster"
build_container gluster gluster gluster_install_log

# create and start container
echo " - Running docker container"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        --network host \
        --privileged=true \
        -v /var/lib/gluster:/var/lib/gluster \
        -v /var/log/gluster:/var/log/gluster \
        -v /var/run/gluster:/var/run/gluster \
        --mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh \
        -d --name gluster \
        -i \
        -t eskimo:gluster bash >> gluster_install_log 2>&1
fail_if_error $? "gluster_install_log" -2

# connect to container
#docker exec -it gluster bash

echo " - Gluster Remote Server Scripts"
for i in `find ./command_server`; do
    if [[ -f $SCRIPT_DIR/$i ]]; then
        filename=`basename $i`
        docker cp $SCRIPT_DIR/$i gluster:/usr/local/sbin/$filename >> /tmp/gluster_install_log 2>&1
        docker exec gluster chmod 755 /usr/local/sbin/$filename >> /tmp/gluster_install_log 2>&1
        fail_if_error $? /tmp/gluster_install_log -30
    fi
done

echo " - Gluster in container helpers"
for i in `find ./gluster_container_helpers`; do
    if [[ -f $SCRIPT_DIR/$i ]]; then
        filename=`basename $i`
        docker cp $SCRIPT_DIR/$i gluster:/usr/local/sbin/$filename >> /tmp/gluster_install_log 2>&1
        docker exec gluster chmod 755 /usr/local/sbin/$filename >> /tmp/gluster_install_log 2>&1
        fail_if_error $? /tmp/gluster_install_log -30
    fi
done

echo " - Copying containerWatchDog.sh script to container"
docker_cp_script containerWatchDog.sh sbin gluster gluster_install_log

echo " - Configuring gluster container"
docker exec gluster bash /scripts/inContainerSetupGluster.sh | tee -a gluster_install_log 2>&1
if [[ `tail -n 1 gluster_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat gluster_install_log
    exit -100
fi

#echo " - TODO"
#docker exec -it gluster TODO


echo " - Copying Service Start Script"
docker_cp_script inContainerStartService.sh sbin gluster gluster_install_log

echo " - Copying settingsInjector.sh Script"
docker_cp_script settingsInjector.sh sbin gluster gluster_install_log

echo " - Committing changes to local template and exiting container gluster"
commit_container gluster gluster_install_log


echo " - Copying gluster command line programs docker wrappers to /usr/local/bin"
for i in `find ./gluster_wrappers -mindepth 1`; do
    sudo cp $i /usr/local/sbin
    filename=`echo $i | cut -d '/' -f 3`
    sudo chmod 755 /usr/local/sbin/$filename
done


echo " - Copying glusterMountChecker.sh script to /usr/local/sbin"
sudo cp $SCRIPT_DIR/glusterMountChecker.sh /usr/local/sbin/glusterMountChecker.sh
sudo chown root /usr/local/sbin/glusterMountChecker.sh
sudo chmod 755 /usr/local/sbin/glusterMountChecker.sh

echo " - Copying gluster service docker container startup file"
sudo cp startGlusterServiceContainer.sh /usr/local/sbin/startGlusterServiceContainer.sh
sudo chown root /usr/local/sbin/startGlusterServiceContainer.sh
sudo chmod 755 /usr/local/sbin/startGlusterServiceContainer.sh

echo " - Creating glusterMountCheckerPeriodic.sh script"
cat > /tmp/glusterMountCheckerPeriodic.sh <<- "EOF"
#!/usr/bin/env bash
while true; do
     sleep 10
     sudo /bin/bash /usr/local/sbin/glusterMountChecker.sh
done
EOF
sudo /bin/chown root /tmp/glusterMountCheckerPeriodic.sh
sudo /bin/mv /tmp/glusterMountCheckerPeriodic.sh /usr/local/sbin/glusterMountCheckerPeriodic.sh
sudo /bin/chmod 755 /usr/local/sbin/glusterMountCheckerPeriodic.sh

if [[ `sudo crontab -u root -l 2>/dev/null | grep glusterMountCheckerPeriodic` == "" ]]; then
    echo " - Scheduling periodic execution of glusterMountCheckerPeriodic using crontab"
    sudo rm -f /tmp/crontab
    sudo bash -c "crontab -u root -l >> /tmp/crontab 2>/dev/null"
    sudo bash -c "echo \"* * * * * /usr/local/sbin/glusterMountCheckerPeriodic\" >> /tmp/crontab"
    sudo crontab -u root /tmp/crontab
fi


#echo " - Installing and checking systemd service file"
install_and_check_service_file gluster gluster_install_log





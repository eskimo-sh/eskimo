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

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- SETTING UP ZEPPELIN  ------------------------------------------------------"

echo " - Changing owner of important folders"
chown -R spark /usr/local/lib/zeppelin/conf/
#chown -R spark /usr/local/lib/zeppelin/webapps/
chown -R spark /usr/local/lib/zeppelin/interpreter/
chown -R spark /usr/local/lib/zeppelin/local-repo/
chown spark /usr/local/lib/zeppelin/

echo " - creating gluster log dir (for gluster mount checker)"
sudo mkdir -p /var/log/gluster/
sudo chmod 777 /var/log/gluster/

echo " - Enabling spark user to create /var/run/spark/zeppelin and chown it"
bash -c "echo \"spark  ALL = NOPASSWD: /bin/mkdir -p /var/run/spark/zeppelin\" >> /etc/sudoers.d/spark"
bash -c "echo \"spark  ALL = NOPASSWD: /bin/chown spark /var/run/spark/zeppelin\" >> /etc/sudoers.d/spark"

echo " - Enabling spark user to use host folders"
bash -c "echo \"spark  ALL = NOPASSWD: /bin/rm -Rf /var/lib/flink\" >> /etc/sudoers.d/spark"
bash -c "echo \"spark  ALL = NOPASSWD: /bin/ln -s /var/lib/host_flink /var/lib/flink\" >> /etc/sudoers.d/spark"
bash -c "echo \"spark  ALL = NOPASSWD: /bin/rm -Rf /var/lib/elasticsearch\" >> /etc/sudoers.d/spark"
bash -c "echo \"spark  ALL = NOPASSWD: /bin/ln -s /var/lib/host_elasticsearch /var/lib/elasticsearch\" >> /etc/sudoers.d/spark"

echo " - Enabling spark user to call glusterMountCheckerPeriodic.sh"
bash -c "echo \"spark  ALL = NOPASSWD: /bin/bash /usr/local/sbin/glusterMountChecker.sh\" >> /etc/sudoers.d/spark"

echo " - Enabling few other required sudo rights"
bash -c "echo \"spark  ALL = NOPASSWD: /bin/rm -Rf /tmp/zeppelin_import_log\" >> /etc/sudoers.d/spark"

# zeppelin is not mounting /var/lib/spark from host but gluster shares inside
# still need to be able to manipulate that directory !
chown spark /var/lib/spark


echo " - Creating Zeppelin env file"
cp /usr/local/lib/zeppelin/conf/zeppelin-env.sh.template /usr/local/lib/zeppelin/conf/zeppelin-env.sh

sudo sed -i s/"# export ZEPPELIN_PORT"/"export ZEPPELIN_PORT=38080"/g /usr/local/lib/zeppelin/conf/zeppelin-env.sh
#sudo sed -i s/"# export ZEPPELIN_ADDR"/"export ZEPPELIN_ADDR=0.0.0.0"/g /usr/local/lib/zeppelin/conf/zeppelin-env.sh


# FIXME ATTEMPT
sudo sed -i s/"# export ZEPPELIN_ADDR"/"export ZEPPELIN_ADDR=0.0.0.0"/g /usr/local/lib/zeppelin/conf/zeppelin-env.sh
sudo sed -i s/"# export ZEPPELIN_LOCAL_IP"/"export ZEPPELIN_LOCAL_IP=0.0.0.0"/g /usr/local/lib/zeppelin/conf/zeppelin-env.sh



sudo sed -i s/"# export ZEPPELIN_LOG_DIR"/"export ZEPPELIN_LOG_DIR=\/var\/log\/spark\/zeppelin\/"/g /usr/local/lib/zeppelin/conf/zeppelin-env.sh
sudo sed -i s/"# export ZEPPELIN_PID_DIR"/"export ZEPPELIN_PID_DIR=\/var\/run\/spark\/zeppelin\/"/g /usr/local/lib/zeppelin/conf/zeppelin-env.sh
sudo sed -i s/"# export ZEPPELIN_NOTEBOOK_DIR"/"export ZEPPELIN_NOTEBOOK_DIR=\/var\/lib\/spark\/data\/zeppelin\/notebooks\/"/g /usr/local/lib/zeppelin/conf/zeppelin-env.sh

sudo sed -i s/"# export ZEPPELIN_IDENT_STRING"/"export ZEPPELIN_IDENT_STRING=eskimo"/g /usr/local/lib/zeppelin/conf/zeppelin-env.sh

sudo sed -i s/"# export SPARK_HOME"/"export SPARK_HOME=\/usr\/local\/lib\/spark\/"/g /usr/local/lib/zeppelin/conf/zeppelin-env.sh

sudo bash -c 'echo -e "\n\n# Eskimo settings" >> /usr/local/lib/zeppelin/conf/zeppelin-env.sh'

sudo bash -c 'echo -e "\nexport FLINK_HOME=/usr/local/lib/flink/" >> /usr/local/lib/zeppelin/conf/zeppelin-env.sh'
sudo bash -c 'echo -e "\nexport ZEPPELIN_INTERPRETER_OUTPUT_LIMIT=1024000" >> /usr/local/lib/zeppelin/conf/zeppelin-env.sh'

# Disabling zeppelin interpreter execution as kubernetes pods (ZEPPELIN_RUN_MODE=k8s)
sudo bash -c 'echo -e "\nexport ZEPPELIN_RUN_MODE=local" >> /usr/local/lib/zeppelin/conf/zeppelin-env.sh'



echo " - Creating Zeppelin site file"
cp /usr/local/lib/zeppelin/conf/zeppelin-site.xml.template /usr/local/lib/zeppelin/conf/zeppelin-site.xml

sudo sed -i s/"<value>8080<\/value>"/"<value>38080<\/value>"/g /usr/local/lib/zeppelin/conf/zeppelin-site.xml

sudo sed -i s/"<value>127.0.0.1<\/value>"/"<value>0.0.0.0<\/value>"/g /usr/local/lib/zeppelin/conf/zeppelin-site.xml


echo " - Disabling same policy enforcement"
sudo sed -i s/"<\/configuration>"/""/g /usr/local/lib/zeppelin/conf/zeppelin-site.xml
sudo bash -c 'echo "<property>" >> /usr/local/lib/zeppelin/conf/zeppelin-site.xml'
sudo bash -c 'echo "  <name>zeppelin.server.xframe.options</name>" >> /usr/local/lib/zeppelin/conf/zeppelin-site.xml'
sudo bash -c 'echo "  <value>ALLOWALL</value>" >> /usr/local/lib/zeppelin/conf/zeppelin-site.xml'
sudo bash -c 'echo "  <description>The X-Frame-Options HTTP response header can be used to indicate whether or not a browser should be allowed to render a page in a frame/iframe/object.</description>" >> /usr/local/lib/zeppelin/conf/zeppelin-site.xml'
sudo bash -c 'echo "</property>" >> /usr/local/lib/zeppelin/conf/zeppelin-site.xml'
sudo bash -c 'echo "</configuration>" >> /usr/local/lib/zeppelin/conf/zeppelin-site.xml'


echo " - Configuring spark interpreter"

# not suppoorted by zeppelin 0.9-final
#sudo sed -i -n '1h;1!H;${;g;s/'\
#'          \"name\": \"zeppelin.spark.useNew\",\n'\
#'          \"value\": true,'\
#'/'\
#'          \"name\": \"zeppelin.spark.useNew\",\n'\
#'          \"value\": false,'\
#'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

sudo sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"SPARK_HOME\",\n'\
'          \"value\": \"\",'\
'/'\
'          \"name\": \"SPARK_HOME\",\n'\
'          \"value\": \"\/usr\/local\/lib\/spark\/\",'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

sudo sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"spark.app.name\",\n'\
'          \"value\": \"\",'\
'/'\
'          \"name\": \"spark.app.name\",\n'\
'          \"value\": \"zeppelin_spark\",'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

sudo sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"PYSPARK_PYTHON\",\n'\
'          \"value\": \"python\",'\
'/'\
'          \"name\": \"PYSPARK_PYTHON\",\n'\
'          \"value\": \"python3.9\",'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

sudo sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"PYSPARK_DRIVER_PYTHON\",\n'\
'          \"value\": \"python\",'\
'/'\
'          \"name\": \"PYSPARK_DRIVER_PYTHON\",\n'\
'          \"value\": \"python3.9\",'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

sudo sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"zeppelin.spark.enableSupportedVersionCheck\",\n'\
'          \"value\": true,'\
'/'\
'          \"name\": \"zeppelin.spark.enableSupportedVersionCheck\",\n'\
'          \"value\": false,'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json


echo " - Configuring ElasticSearch interpreter"

sudo sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"elasticsearch.client.type\",\n'\
'          \"value\": \"transport\",'\
'/'\
'          \"name\": \"elasticsearch.client.type\",\n'\
'          \"value\": \"http\",'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

sudo sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"elasticsearch.port\",\n'\
'          \"value\": \"9300\",'\
'/'\
'          \"name\": \"elasticsearch.port\",\n'\
'          \"value\": \"9200\",'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json


echo " - Configuring Flink interpreter"

sudo sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"flink.execution.remote.port\",\n'\
'          \"value\": \"\",'\
'/'\
'          \"name\": \"flink.execution.remote.port\",\n'\
'          \"value\": \"8081\",'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

sudo sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"flink.execution.mode\",\n'\
'          \"value\": \"local\",'\
'/'\
'          \"name\": \"flink.execution.mode\",\n'\
'          \"value\": \"remote\",'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

sudo sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"FLINK_HOME\",\n'\
'          \"value\": \"\",'\
'/'\
'          \"name\": \"FLINK_HOME\",\n'\
'          \"value\": \"\/usr\/local\/lib\/flink\/\",'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

if [[ -z $TEST_MODE ]]; then
    echo " - Fixing flink interpreter"
    mv /usr/local/lib/flink/lib/statefun-flink-distribution* $(ls -1 /usr/local/lib/flink/lib/statefun-flink-distribution* | sed s/distribution/dst/)
fi

echo " - Configuring Shell interpreter"

sudo sed -i -n '1h;1!H;${;g;s/'\
'          \"name\": \"shell.command.timeout.millisecs\",\n'\
'          \"value\": \"60000\",'\
'/'\
'          \"name\": \"shell.command.timeout.millisecs\",\n'\
'          \"value\": \"1200000\",'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json


echo " - Configuring Java Interpreter"

sudo sed -i -n '1h;1!H;${;g;s/'\
'      \"interpreterGroup\": \[\n'\
'        {\n'\
'          \"name\": \"java\",\n'\
'          \"class\": \"org.apache.zeppelin.java.JavaInterpreter\",\n'\
'          \"defaultInterpreter\": true,\n'\
'          \"editor\": {\n'\
'            \"language\": \"java\",\n'\
'            \"editOnDblClick\": false\n'\
'          }\n'\
'        }\n'\
'      \],\n'\
'      \"dependencies\": \[\],'\
'/'\
'      \"interpreterGroup\": \[\n'\
'        {\n'\
'          \"name\": \"java\",\n'\
'          \"class\": \"org.apache.zeppelin.java.JavaInterpreter\",\n'\
'          \"defaultInterpreter\": true,\n'\
'          \"editor\": {\n'\
'            \"language\": \"java\",\n'\
'            \"editOnDblClick\": false\n'\
'          }\n'\
'        }\n'\
'      \],\n'\
'      \"dependencies\": \[\n'\
'        {\n'\
'          \"groupArtifactVersion\": \"org.apache.kafka:kafka-streams:2.4.1\",\n'\
'          \"local\": false\n'\
'        },\n'\
'        {\n'\
'          \"groupArtifactVersion\": \"org.apache.kafka:kafka-clients:2.4.1\",\n'\
'          \"local\": false\n'\
'        }\n'\
'      \],'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

echo " - Preparing note isolation setting runtime injection"
sed -i -n '1h;1!H;${;g;s/'\
'        \"isExistingProcess\": false,\n'\
'/'\
'        \"isExistingProcess\": false,\n'\
'        \"perNote\": \"ESKIMO_PER_NOTE_ISOLATION\",\n'\
'        \"perUser\": \"ESKIMO_PER_USER_ISOLATION\",\n'\
'/g;p;}' /usr/local/lib/zeppelin/conf/interpreter.json

echo " - Preparing job manager enablement setting injection"

sed -i -n '1h;1!H;${;g;s/'\
'<property>\n'\
'  <name>zeppelin.jobmanager.enable<\/name>\n'\
'  <value>false<\/value>\n'\
'/'\
'<property>\n'\
'  <name>zeppelin.jobmanager.enable<\/name>\n'\
'  <value>ESKIMO_JOBMANAGER_ENABLE<\/value>\n'\
'/g;p;}' /usr/local/lib/zeppelin/conf/zeppelin-site.xml



echo " - Creating default eskimo_settings.conf"
sudo bash -c "echo 'zeppelin_note_isolation=shared' > /usr/local/lib/zeppelin/conf/eskimo_settings.conf"
sudo bash -c "echo 'zeppelin_jobmanager_enable=false' > /usr/local/lib/zeppelin/conf/eskimo_settings.conf"

echo " - Enabling spark to change configuration at runtime"
chown -R spark. "/usr/local/lib/zeppelin/conf/"

#chown -R spark. "/usr/local/lib/zeppelin/local-repo/helium-registry-cache/"

echo " - HACK to enable spark to change flink config as well"
chmod -R 777 "/usr/local/lib/flink/conf/"

echo " - [HACK] tampering with interpreter.sh to force JDK 8 for flink interpreter"

sudo sed -i -n '1h;1!H;${;g;s/'\
'if \[\[ "\${INTERPRETER_ID}" != \"flink\" \]\]; then\n'\
'/'\
'if \[\[ "${INTERPRETER_ID}" == \"flink\" \]\]; then\n'\
'  export JAVA_HOME=\/usr\/local\/lib\/jvm\/openjdk-8\n'\
'  export PATH=$JAVA_HOME\/bin:$PATH\n'\
'fi\n'\
'if \[\[ "${INTERPRETER_ID}" != \"flink\" \]\]; then\n'\
'/g;p;}' /usr/local/lib/zeppelin/bin/interpreter.sh


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"
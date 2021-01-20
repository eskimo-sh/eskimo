

set -e

echo "   + Unzipping zeppelin-elasticsearch-${ZEPPELIN_VERSION_FULL}.jar (and patching)"
cp /tmp/zeppelin_setup/zeppelin-${ZEPPELIN_VERSION_FULL}-bin-all/interpreter/elasticsearch/zeppelin-elasticsearch-${ZEPPELIN_VERSION_FULL}.jar .
unzip -n zeppelin-elasticsearch-${ZEPPELIN_VERSION_FULL}.jar
rm zeppelin-elasticsearch-${ZEPPELIN_VERSION_FULL}.jar

echo "   + Re-compressing zeppelin-elasticsearch-${ZEPPELIN_VERSION_FULL}.jar"
zip -r /tmp/zeppelin-elasticsearch-${ZEPPELIN_VERSION_FULL}.zip *
mv /tmp/zeppelin-elasticsearch-${ZEPPELIN_VERSION_FULL}.zip /tmp/zeppelin_setup/zeppelin-${ZEPPELIN_VERSION_FULL}-bin-all/interpreter/elasticsearch/zeppelin-elasticsearch-${ZEPPELIN_VERSION_FULL}.jar

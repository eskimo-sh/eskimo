

docker run \
    -it \
    --rm \
    --network host \
    --user spark \
    --privileged \
    -e LIBPROCESS_ADVERTISE_IP=192.168.56.22 \
    -v /var/log/spark:/var/log/spark:shared \
    -v /tmp/ekiji/notes/tests/spark-submit:/tmp/ekiji/notes/tests/spark-submit:slave \
    -v /tmp:/tmp:slave \
    --mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh \
    --mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json \
    --mount type=bind,source=/home/spark/.kube/config,target=/home/spark/.kube/config \
    -v /etc/k8s:/etc/k8s:ro \
    -e NODE_NAME=test-node2 \
    -e HOSTALIASES=/tmp/kube-hosts \
    kubernetes.registry:5000/spark \
          /usr/local/bin/spark-submit \
          -v \
          --files /tmp/log4j.properties \
          --conf spark.driver.extraJavaOptions=-Dlog4j.configuration=file:///tmp/log4j.properties \
          /tmp/test-es-spark.py


KUBE_SERVICES_HOSTS_FILE=$(create_kube_services_hosts_file)
docker run \
    -it \
    --rm \
    --network host \
    --user spark \
    --privileged \
    -e LIBPROCESS_ADVERTISE_IP=192.168.56.22 \
    -v /var/log/spark:/var/log/spark:shared \
    -v /tmp/ekiji/notes/tests/spark-submit:/tmp/ekiji/notes/tests/spark-submit:slave \
    -v /tmp:/tmp:slave \
    --mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh \
    --mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json \
    --mount type=bind,source=/home/spark/.kube/config,target=/home/spark/.kube/config \
    -v /etc/k8s:/etc/k8s:ro \
    -e NODE_NAME=test-node2 \
    --mount type=bind,source=$KUBE_SERVICES_HOSTS_FILE,target=$KUBE_SERVICES_HOSTS_FILE \
    -e ADDITONAL_HOSTS_FILE=$KUBE_SERVICES_HOSTS_FILE \
    kubernetes.registry:5000/spark \
          /bin/bash -c "/usr/local/bin/kube_do cat /etc/hosts"
rm -Rf $KUBE_SERVICES_HOSTS_FILE
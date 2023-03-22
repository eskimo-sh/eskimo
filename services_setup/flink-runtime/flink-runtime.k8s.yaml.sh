#!/bin/bash
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

# This is recreated with :
#  -> Launch flink container
# docker run -it --user flink --network host --mount type=bind,source=/home/flink/.kube/config,target=/home/flink/.kube/config -v /etc/k8s:/etc/k8s:ro kubernetes.registry:5000/flink bash
#  -> then inside container :
# /usr/local/lib/flink/bin/kubernetes-session.sh
#  -> Then export yaml files frm dashboard (servces + deployment)

. /usr/local/sbin/eskimo-utils.sh

TEMP_FILE=$(mktemp)

cat > $TEMP_FILE <<EOF
kind: Service
apiVersion: v1
metadata:
  name: flink-runtime
  namespace: eskimo
  labels:
    app: flink-runtime
    type: flink-native-kubernetes
spec:
  ports:
    - name: jobmanager-rpc
      protocol: TCP
      port: 6123
      targetPort: 6123
    - name: blobserver
      protocol: TCP
      port: 6124
      targetPort: 6124
  selector:
    app: flink-runtime
    component: jobmanager
    type: flink-native-kubernetes

---

kind: ConfigMap
apiVersion: v1
metadata:
  name: pod-template-flink
  namespace: eskimo
data:
  taskmanager-pod-template.yaml: |
    apiVersion: v1
    kind: Pod
    metadata:
      namespace: eskimo
    spec:
      containers:
      - name: flink-main-container # this has to be this name !!!
        image: kubernetes.registry:5000/flink-runtime:$(get_last_tag flink-runtime)
        securityContext:
          privileged: true
          allowPrivilegeEscalation: true
          runAsUser: 3305
          runAsGroup: 3305
        env:
          - name: POD_IP_ADDRESS
            valueFrom:
              fieldRef:
                apiVersion: v1
                fieldPath: status.podIP
        resources:
          requests:
            cpu: "$ESKIMO_KUBE_REQUEST_FLINK_RUNTIME_CPU"
            memory: "$ESKIMO_KUBE_REQUEST_FLINK_RUNTIME_RAM"    
        volumeMounts:
          - name: home-flink-kube
            mountPath: /home/flink/.kube
          - name: var-log-flink
            mountPath: /var/log/flink
          - name: var-run-flink
            mountPath: /var/run/flink
          - name: etc-eskimo-topology
            mountPath: /etc/eskimo_topology.sh
          - name: etc-eskimo-services-settings
            mountPath: /etc/eskimo_services-settings.json
          - name: kubectl
            mountPath: /usr/local/bin/kubectl
          - name: etc-k8s
            mountPath: /etc/k8s
      volumes:
        - name: home-flink-kube
          hostPath:
            path: /home/flink/.kube
            type: Directory
        - name: var-log-flink
          hostPath:
            path: /var/log/flink
            type: Directory
        - name: var-run-flink
          hostPath:
            path: /var/run/flink
            type: Directory
        - name: etc-eskimo-topology
          hostPath:
            path: /etc/eskimo_topology.sh
            type: File
        - name: etc-eskimo-services-settings
          hostPath:
            path: /etc/eskimo_services-settings.json
            type: File
        - name: kubectl
          hostPath:
            path: /usr/local/bin/kubectl
            type: File
        - name: etc-k8s
          hostPath:
            path: /etc/k8s
            type: Directory


---

kind: Service
apiVersion: v1
metadata:
  name: flink-runtime-rest
  namespace: eskimo
  labels:
    app: flink-runtime
    type: flink-native-kubernetes
spec:
  ports:
    - name: rest
      protocol: TCP
      port: 8081
      targetPort: 8081
  selector:
    app: flink-runtime
    component: jobmanager
    type: flink-native-kubernetes
status:
  loadBalancer: {}

---

kind: Deployment
apiVersion: apps/v1
metadata:
  name: flink-runtime
  namespace: eskimo
  labels:
    app: flink-runtime
    component: jobmanager
    type: flink-native-kubernetes
spec:
  replicas: 1
  selector:
    matchLabels:
      app: flink-runtime
      component: jobmanager
      type: flink-native-kubernetes
  template:
    metadata:
      namespace: eskimo
      labels:
        app: flink-runtime
        component: jobmanager
        type: flink-native-kubernetes
    spec:
      volumes:
        - name: home-flink-kube
          hostPath:
            path: /home/flink/.kube
            type: Directory
        - name: var-log-flink
          hostPath:
            path: /var/log/flink
            type: Directory
        - name: var-run-flink
          hostPath:
            path: /var/run/flink
            type: Directory
        - name: etc-eskimo-topology
          hostPath:
            path: /etc/eskimo_topology.sh
            type: File
        - name: etc-eskimo-services-settings
          hostPath:
            path: /etc/eskimo_services-settings.json
            type: File
        - name: etc-k8s
          hostPath:
            path: /etc/k8s
            type: Directory
        - name: flink-config-volume
          configMap:
            name: flink-config-flink-runtime
            items:
              - key: logback-console.xml
                path: logback-console.xml
              - key: log4j-console.properties
                path: log4j-console.properties
              - key: flink-conf.yaml
                path: flink-conf.yaml
            defaultMode: 420
        - name: pod-template-volume
          configMap:
            name: pod-template-flink
            items:
              - key: taskmanager-pod-template.yaml
                path: taskmanager-pod-template.yaml
            defaultMode: 420
      containers:
        - name: flink-main-container
          image: kubernetes.registry:5000/flink-runtime:$(get_last_tag flink-runtime)
          command:
            - /docker-entrypoint.sh
          args:
            - bash
            - '-c'
            - kubernetes-jobmanager.sh kubernetes-session
          ports:
            - name: rest
              containerPort: 8081
              protocol: TCP
            - name: jobmanager-rpc
              containerPort: 6123
              protocol: TCP
            - name: blobserver
              containerPort: 6124
              protocol: TCP
          env:
            - name: POD_IP_ADDRESS
              valueFrom:
                fieldRef:
                  apiVersion: v1
                  fieldPath: status.podIP
          resources:
            requests:
              cpu: "$ESKIMO_KUBE_REQUEST_FLINK_RUNTIME_CPU"
              memory: "$ESKIMO_KUBE_REQUEST_FLINK_RUNTIME_RAM"
          volumeMounts:
            - name: home-flink-kube
              mountPath: /home/flink/.kube
            - name: var-log-flink
              mountPath: /var/log/flink
            - name: var-run-flink
              mountPath: /var/run/flink
            - name: etc-eskimo-topology
              mountPath: /etc/eskimo_topology.sh
            - name: etc-eskimo-services-settings
              mountPath: /etc/eskimo_services-settings.json
            - name: etc-k8s
              mountPath: /etc/k8s
            - name: flink-config-volume
              mountPath: /opt/flink/conf
            - name: pod-template-volume
              mountPath: /opt/flink/pod-template
          terminationMessagePath: /dev/termination-log
          terminationMessagePolicy: File
          imagePullPolicy: IfNotPresent
          securityContext:
            privileged: true
            runAsUser: 3305
            runAsGroup: 3305
            allowPrivilegeEscalation: true
      restartPolicy: Always
      terminationGracePeriodSeconds: 30
      dnsPolicy: ClusterFirst
      serviceAccountName: default
      serviceAccount: default
      securityContext: {}
      schedulerName: default-scheduler
      # Comment the following tolerations if app must not be deployed on master
      tolerations:
        - key: node-role.kubernetes.io/master
          effect: NoSchedule
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 25%
      maxSurge: 25%
  revisionHistoryLimit: 10
  progressDeadlineSeconds: 600
EOF
cat $TEMP_FILE
rm -Rf $TEMP_FILE
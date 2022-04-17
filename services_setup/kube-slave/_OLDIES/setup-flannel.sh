


if [[ ! -d /run/flannel ]]; then
    sudo mkdir /run/flannel
    sudo chown kubernetes /run/flannel
    sudo chmod 755 /run/flannel
fi


if [[ ! -f /etc/k8s/ssl/flanneld-csr.json ]]; then
    cat > flanneld-csr.json <<EOF
{
  "CN": "flanneld",
  "hosts": [],
  "key": {
    "algo": "rsa",
    "size": 2048
  },
  "names": [
    {
      "C": "SH",
      "ST": "Eskimo",
      "L": "Eskimo",
      "O": "system:masters",
      "OU": "System"
    }
  ]
}
EOF

    sudo mv flanneld-csr.json /etc/k8s/ssl/flanneld-csr.json
    sudo chown kubernetes /etc/k8s/ssl/flanneld-csr.json
    sudo chmod 755 /etc/k8s/ssl/flanneld-csr.json
fi


if [[ ! -f /etc/k8s/ssl/flanneld.pem ]]; then
    cfssl gencert -ca=/etc/k8s/ssl/ca.pem \
      -ca-key=/etc/k8s/ssl/ca-key.pem \
      -config=/etc/k8s/ssl/ca-config.json \
      -profile=kubernetes /etc/k8s/ssl/flanneld-csr.json | cfssljson -bare flanneld

    sudo mkdir -p /etc/k8s/ssl
    sudo mv flanneld*.pem /etc/k8s/ssl
    sudo chown -R kubernetes:kubernetes /etc/k8s/ssl/flanneld*.pem
fi



etcdctl \
  --endpoints=${ETCD_ENDPOINTS} \
  --ca-file=/etc/kubernetes/ssl/ca.pem \
  --cert-file=/etc/flanneld/ssl/flanneld.pem \
  --key-file=/etc/flanneld/ssl/flanneld-key.pem \
  set ${FLANNEL_ETCD_PREFIX}/config '{"Network":"'${CLUSTER_CIDR}'", "SubnetLen": 24, "Backend": {"Type": "vxlan"}}'
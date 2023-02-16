
echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR || exit 199

if [[ ! -f /etc/k8s/env.sh ]]; then
    echo "Could not find /etc/k8s/env.sh"
    exit 1
fi

. /etc/k8s/env.sh

echo " - Creating kube config file for user eskimo"

echo "   + create temp folder"
tmp_dir=$(mktemp -d -t ci-XXXXXXXXXX)
cd $tmp_dir || (echo "Couldn't cd $tmp_dir" && exit 1)

set -e

echo "   + Configure the cluster and the certificates"
kubectl config set-cluster eskimo \
  --certificate-authority=/etc/k8s/shared/ssl/ca.pem \
  --embed-certs=true \
  --server=$ESKIMO_KUBE_APISERVER \
  --kubeconfig=temp.kubeconfig

export ADMIN_USER=$(cat /etc/eskimo_user)

echo "   + Configure client side user and certificates"
kubectl config set-credentials $ADMIN_USER \
  --client-certificate=/etc/k8s/shared/ssl/$ADMIN_USER.pem \
  --client-key=/etc/k8s/shared/ssl/$ADMIN_USER-key.pem \
  --token=$BOOTSTRAP_TOKEN \
  --kubeconfig=temp.kubeconfig

echo "   + Create context"
kubectl config set-context eskimo \
  --cluster=eskimo \
  --user=$ADMIN_USER \
  --kubeconfig=temp.kubeconfig

echo "   + Set the context eskimo as default context"
kubectl config use-context eskimo \
  --kubeconfig=temp.kubeconfig

echo "   + Set default namespace eskimo"
kubectl config set-context \
  --current \
  --namespace=eskimo \
  --kubeconfig=temp.kubeconfig

echo "   + removing previous configuration"
rm -f /home/$ADMIN_USER/.kube/config

echo "   + installing new configuration"
mkdir -p /home/$ADMIN_USER/.kube/
sudo chown $ADMIN_USER.$ADMIN_USER /home/$ADMIN_USER/.kube
mv temp.kubeconfig /home/$ADMIN_USER/.kube/config
sudo chown $ADMIN_USER.$ADMIN_USER /home/$ADMIN_USER/.kube/config

sudo mkdir -p /root/.kube/
sudo cp /home/$ADMIN_USER/.kube/config /root/.kube/config
sudo chown root /root/.kube/config

echo "   + Checking kube config file"
if [[ ! -f /home/$ADMIN_USER/.kube/config ]]; then
    echo "Couldn't find file /home/$ADMIN_USER/.kube/config"
    exit 101
fi

kube_config_file=/home/$ADMIN_USER/.kube/config
if [[ $(grep -F "name: eskimo" $kube_config_file | wc -l) -lt 2 ]]; then
    echo "Missing config in file /home/$ADMIN_USER/.kube/config"
    exit 101
fi

echo "   + removing temp folder"
rm -Rf $tmp_dir










if [[ ! -d /run/flannel ]]; then
    sudo mkdir /run/flannel
    sudo chown kubernetes /run/flannel
    sudo chmod 755 /run/flannel
fi



FROM https://github.com/shawnsong/kubernetes-handbook/blob/master/docker/setup-docker.md

Need to check if the config is new OR was changed and restart docker if it is (need a version  of the previous config !!!)
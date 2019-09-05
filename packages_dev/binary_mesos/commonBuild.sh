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

create-vagrant-file() {

    cat > ssh_key <<- "EOF"
-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAABFwAAAAdzc2gtcn
NhAAAAAwEAAQAAAQEA3ojkjT6HoRjuoYjspClIdBOy8av1tYM2MV1UI9kHJBmxKangTU0G
uM5s5iUNwUdhHffouomozeZvBt7XzrZrN5lO4dZzDAWc70KwmH1VteDfEaBmdp/ZEIjmvu
slErY872U6x15S6kpHfLaIJ5n7e9aCKcxEQLVzhHU/ybOKtQQMlXl3VCe+p1vUF9q7cpLo
+VFjMESNDOpsixhXAJ4n7VDA9XLd3T3vqev8eCxfPXhP9bFmW/hnhLHGSNEYT1WLYH+/xR
1v/b64EiIwzOUK/3vpSP5EAO0wlORkhlEE2PVxf3w3wsmPvSLq4NCm/MFJxbK0T4a2S4qg
1FexNiBfFwAAA8jwOZHB8DmRwQAAAAdzc2gtcnNhAAABAQDeiOSNPoehGO6hiOykKUh0E7
Lxq/W1gzYxXVQj2QckGbEpqeBNTQa4zmzmJQ3BR2Ed9+i6iajN5m8G3tfOtms3mU7h1nMM
BZzvQrCYfVW14N8RoGZ2n9kQiOa+6yUStjzvZTrHXlLqSkd8tognmft71oIpzERAtXOEdT
/Js4q1BAyVeXdUJ76nW9QX2rtykuj5UWMwRI0M6myLGFcAniftUMD1ct3dPe+p6/x4LF89
eE/1sWZb+GeEscZI0RhPVYtgf7/FHW/9vrgSIjDM5Qr/e+lI/kQA7TCU5GSGUQTY9XF/fD
fCyY+9Iurg0Kb8wUnFsrRPhrZLiqDUV7E2IF8XAAAAAwEAAQAAAQAPfZP7SQkD68pgsDlY
zA7hFaX1MLUv52xUT1zWCft3RdqRPeQBPYVkQ+pMsvOcKq3V+jXXFlIL0yiTX9vB5ezct+
1HxzfG9HUSKqBEXSUkPf0JKxM22rWvcvgs/g1cmhbvyyomSqiW6ojDY6liqFNbMXlqE3AE
2RyrccX48miLZRWHv3AidiBW16lDOQypDFJ7HFS+FMoPW5o0VitjqkPbE4FQd1etv7F39f
xqdoJ9MHk9pNrl6GiTucTRN2ws064Qw/D40Ta9/Qk4KkShWq9l/TV1DOJzYCw17o1thNbV
phRel1wxR0MbovorqyYH5h+i4Stu/5iox7MTBmEFm+ZhAAAAgHgug0Ins1wlsfjFjvCkRc
LUxMZsUYr45QG8JFNd4XzACWalfyMXyMlxNH9VWk2ctx+i8zzcNXBw5HJzA4Zxy8BTFz1a
EHrT6Uegzbeu37+XMOxnDBg1ssvRFK+XckYm6QcroCJA0jNOeSb3fJ7m91kT2aBwgoi0jd
YjOEDsYzO/AAAAgQDxqk0cITmYa4qICUOb5pokUa7cFfzogmEzfsttkCe4fLR9ck3TpCjO
B4Mc/LR97g8baP7PBvi0V3rnESF1fDnL5kTf19uVsJkBwVgPJwnmGL3azL8a0jXahJv5PA
dH/099m3MB6YOS8MULx41Rx/4YfcqjszS3wNoBTQPz9FknKQAAAIEA67wYDIfDO2Qb0BqU
4pl/IryTm/RfNBOzq8CF8NsaGH9ZPVdWaeUrLDQd4ZbpvdyoB9/3zrtH1mjm7sMtJRyEEr
ptDpKQU8fUKhIxc7XU0SOJrPEPfZDiO1i92WKglcQpJngyWvYlwWCeo83htVRnMFOlRQNn
Oaqn4JUMzm3VXD8AAAATYmFkdHJhc2hAYmFkYm9va25ldw==
-----END OPENSSH PRIVATE KEY-----
EOF

    cat > ssh_key.pub <<- "EOF"
ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDeiOSNPoehGO6hiOykKUh0E7Lxq/W1gzYxXVQj2QckGbEpqeBNTQa4zmzmJQ3BR2Ed9+i6iajN5m8G3tfOtms3mU7h1nMMBZzvQrCYfVW14N8RoGZ2n9kQiOa+6yUStjzvZTrHXlLqSkd8tognmft71oIpzERAtXOEdT/Js4q1BAyVeXdUJ76nW9QX2rtykuj5UWMwRI0M6myLGFcAniftUMD1ct3dPe+p6/x4LF89eE/1sWZb+GeEscZI0RhPVYtgf7/FHW/9vrgSIjDM5Qr/e+lI/kQA7TCU5GSGUQTY9XF/fDfCyY+9Iurg0Kb8wUnFsrRPhrZLiqDUV7E2IF8X badtrash@badbooknew
EOF

    cat > Vagrantfile <<- "EOF"
# -*- mode: ruby -*-
# vi: set ft=ruby :

# Sanity checks
if Vagrant.has_plugin?("vagrant-vbguest")
  puts '!!! "vagrant-vbguest" plugin is installed on your host system and known to lead to issues with this setup. !!!'
end


# Define build-nodes
nodes = [
  { :hostname => 'deb-build-node', :box => 'generic/debian8', :ip => '192.168.10.101', :ram => 16000},
  { :hostname => 'rhel-build-node', :box => 'centos/7',  :ip => '192.168.10.103', :ram => 16000},
]

Vagrant.configure("2") do |config|
  config.vm.boot_timeout = 600

  # specific configuration for each node
  nodes.each do |node|
    config.vm.define node[:hostname] do |nodeconfig|

      # node setup
      nodeconfig.vm.box = node[:box]
      nodeconfig.vm.hostname = node[:hostname]
      nodeconfig.vm.network :private_network, ip: node[:ip]

      memory = node[:ram] ? node[:ram] : 2500;

      # force rsync for libvirt
      nodeconfig.vm.synced_folder './', '/vagrant', type: 'rsync'

      # Config for VirtualBox
      nodeconfig.vm.provider :virtualbox do |vb|
        vb.name = node[:hostname]
        vb.customize ["modifyvm", :id, "--memory", memory.to_s]
        vb.customize ["modifyvm", :id, "--ioapic", "on"]
        vb.customize ["modifyvm", :id, "--cpus", "2"]
      end

      # Config for libvirt
      nodeconfig.vm.provider :libvirt do |lv|
        lv.cpus = 2
        lv.memory = memory
        lv.machine_virtual_size = 20
      end

      nodeconfig.vm.provision "file", source: "ssh_key.pub", destination: "./.ssh/id_rsa.pub"
      nodeconfig.vm.provision "shell" do |s|
          ssh_pub_key = File.readlines("ssh_key.pub").first.strip
          s.inline = <<-SHELL
            if [[ -f "/etc/debian_version" ]]; then
                useradd -m -G root,sudo,users,staff eskimo
            else
                useradd -m -G root,wheel,users eskimo
            fi
            echo #{ssh_pub_key} >> /home/vagrant/.ssh/authorized_keys
            sudo mkdir -p /root/.ssh/
            echo #{ssh_pub_key} >> /root/.ssh/authorized_keys
            sudo mkdir -p /home/eskimo/.ssh/
            echo #{ssh_pub_key} >> /home/eskimo/.ssh/authorized_keys
            sudo chown -R eskimo.eskimo /home/eskimo/.ssh/
            sudo chmod 600 /home/eskimo/.ssh/authorized_keys
            echo "eskimo ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/eskimo
            sudo chmod 440 /etc/sudoers.d/eskimo
          SHELL
      end
    end
  end
end
EOF
}
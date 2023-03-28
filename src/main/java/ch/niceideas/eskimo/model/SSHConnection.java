/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */


package ch.niceideas.eskimo.model;

import ch.niceideas.eskimo.types.Node;
import com.trilead.ssh2.*;
import lombok.EqualsAndHashCode;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

@EqualsAndHashCode
public class SSHConnection implements Closeable {

    private final Connection under;

    private final int readTimeout;

    public SSHConnection(Node node, int port, int readTimeout) {
        under = new Connection (node.getAddress(), port);
        this.readTimeout = readTimeout;
    }

    public Connection getUnder() {
        return under;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public boolean authenticateWithPublicKey(String user, char[] pemPrivateKey, String password) throws IOException {
        return under.authenticateWithPublicKey(user, pemPrivateKey, password);
    }

    public boolean authenticateWithPublicKey(String user, File pemFile, String password) throws IOException {
        return under.authenticateWithPublicKey(user, pemFile, password);
    }

    @Override
    public void close() {
        under.close();
    }

    public ConnectionInfo connect() throws IOException {
        return under.connect();
    }

    public ConnectionInfo connect(ServerHostKeyVerifier verifier, int connectTimeout, int kexTimeout) throws IOException {
        return under.connect(verifier, connectTimeout, readTimeout, kexTimeout);
    }

    public LocalPortForwarder createLocalPortForwarder(int localPort, String hostToConnect, int portToConnect) throws IOException {
        return under.createLocalPortForwarder(localPort, hostToConnect, portToConnect);
    }

    public SCPClient createSCPClient() throws IOException {
        return under.createSCPClient();
    }

    public String getHostname() {
        return under.getHostname();
    }

    public boolean isAuthenticationComplete() {
        return under.isAuthenticationComplete();
    }

    public Session openSession() throws IOException {
        return under.openSession();
    }

    public void sendIgnorePacket() throws IOException {
        under.sendIgnorePacket();
    }

    public void setTCPNoDelay(boolean enable) throws IOException {
        under.setTCPNoDelay(enable);
    }

    public int exec(String command, OutputStream output) throws IOException, InterruptedException {
        return under.exec(command, output);
    }

    @Override
    public String toString() {
        return under.toString();
    }

}



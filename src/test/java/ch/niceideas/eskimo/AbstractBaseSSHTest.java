/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.junit.After;
import org.junit.Before;

import java.io.*;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public abstract class AbstractBaseSSHTest {

    private static final Logger logger = Logger.getLogger(AbstractBaseSSHTest.class);

    protected static int SSH_PORT = 63022;

    protected SshServer sshd = null;

    protected String privateKeyRaw = null;
    protected String publicKeyRaw = null;

    protected String privateKeyContent = null;
    protected String publicKeyContent = null;

    protected byte[] privateKeyBytes = null;
    protected byte[] publicKeyBytes = null;

    @After
    public void afterTestTeardown() throws Exception {
        sshd.close(true);
    }

    protected abstract CommandFactory getSShSubsystemToUse();

    @Before
    public void beforeTestSetup() throws Exception {

        privateKeyRaw = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("AbstractBaseSSHTest/id_rsa"));
        publicKeyRaw = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("AbstractBaseSSHTest/id_rsa.pub"));

        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(SSH_PORT);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get("/tmp/hostkey.ser")));

        List<NamedFactory<UserAuth>> userAuthFactories = new ArrayList<NamedFactory<UserAuth>>();
        userAuthFactories.add(new UserAuthPublicKeyFactory());
        sshd.setUserAuthFactories(userAuthFactories);

        sshd.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            public boolean authenticate(String username, PublicKey key, ServerSession session) {
                // oh what the hell
                return true;
                /*
                if(key instanceof RSAPublicKey) {
                    String s1 = new String(encode((RSAPublicKey) key));
                    String s2 = new String(Base64.decodeBase64(publicKeyRaw.getBytes()));
                    return s1.equals(s2); //Returns true if the key matches our known key, this allows auth to proceed.
                }
                return false; //Doesn't handle other key types currently.
                */
            }
        });


        //sshd.setCommandFactory(new ScpCommandFactory());
        //sshd.setCommandFactory(new ProcessShellCommandFactory());
        sshd.setCommandFactory(getSShSubsystemToUse());

        sshd.setShellFactory(new ProcessShellFactory(new String[] { "/bin/bash", "-i" }));

        List<NamedFactory<Command>> namedFactoryList = new ArrayList<NamedFactory<Command>>();
        namedFactoryList.add(new SftpSubsystemFactory());
        sshd.setSubsystemFactories(namedFactoryList);

        try {
            sshd.start();
        } catch (Exception e) {
            logger.error (e, e);
            throw new RuntimeException(e);
        }

        privateKeyContent = privateKeyRaw.replace("\\n", "").replace("-----BEGIN RSA PRIVATE KEY-----", "").replace("-----END RSA PRIVATE KEY-----", "");
        publicKeyContent = publicKeyRaw.replace("\\n", "").replace("-----BEGIN RSA PUBLIC KEY-----", "").replace("-----END RSA PUBLIC KEY-----", "");

        privateKeyBytes = Base64.decodeBase64(privateKeyContent);
        publicKeyBytes = Base64.decodeBase64(publicKeyContent);
    }

    JSch createjSchInstance() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, JSchException {
        JSch jsch = new JSch();

        String privateKeyPath = ResourceUtils.getURL("classpath:AbstractBaseSSHTest/id_rsa").getPath();
        String publicKeyPath = ResourceUtils.getURL("classpath:AbstractBaseSSHTest/id_rsa.pub").getPath();

        File tempFileForKey = File.createTempFile("test", "key");
        FileWriter fileWriter = new FileWriter(tempFileForKey);
        JcaPEMWriter pemWriter = new JcaPEMWriter(fileWriter);

        KeyFactory kf = KeyFactory.getInstance("RSA"); // or "EC" or whatever
        PrivateKey privKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        //PublicKey pubKey = kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

        pemWriter.writeObject(privKey);
        pemWriter.close();

        jsch.addIdentity(tempFileForKey.getAbsolutePath(), publicKeyPath, new byte[]{});

        Hashtable<String, String> config = new Hashtable<String, String>();
        config.put("StrictHostKeyChecking", "no");
        JSch.setConfig(config);

        return jsch;
    }

    String getFileContents(File downloadedFile)
            throws FileNotFoundException, IOException
    {
        StringBuffer fileData = new StringBuffer();
        BufferedReader reader = new BufferedReader(new FileReader(downloadedFile));

        try {
            char[] buf = new char[1024];
            for(int numRead = 0; (numRead = reader.read(buf)) != -1; buf = new char[1024]) {
                fileData.append(String.valueOf(buf, 0, numRead));
            }
        } finally {
            reader.close();
        }

        return fileData.toString();
    }

    //Converts a Java RSA PK to SSH2 Format.
    public static byte[] encode(RSAPublicKey key) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] name = "ssh-rsa".getBytes("US-ASCII");
            write(name, buf);
            write(key.getPublicExponent().toByteArray(), buf);
            write(key.getModulus().toByteArray(), buf);
            return buf.toByteArray();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void write(byte[] str, OutputStream os) throws IOException {
        for (int shift = 24; shift >= 0; shift -= 8)
            os.write((str.length >>> shift) & 0xFF);
        os.write(str);
    }

}

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

import ch.niceideas.common.utils.FileUtils;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;

public class SSHTestInfrastructureTest extends AbstractBaseSSHTest {

    @Override
    protected CommandFactory getSShSubsystemToUse() {
        return new ProcessShellCommandFactory();
    }

    /** Run Test on Linux only */
    @Before
    public void beforeMethod() {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    @Test
    public void testPutAndGetFile() throws Exception {
        JSch jsch = createjSchInstance();

        Session session = jsch.getSession( "remote-username", "localhost", getSShPort());
        session.connect();

        Channel channel = session.openChannel( "sftp" );
        channel.connect();

        ChannelSftp sftpChannel = (ChannelSftp) channel;

        final String testFileContents = "some file contents";

        File uploadFile = File.createTempFile("upload", "file");
        FileUtils.delete(uploadFile);
        String uploadedFileName = uploadFile.getAbsolutePath();
        sftpChannel.put(new ByteArrayInputStream(testFileContents.getBytes()), uploadedFileName);

        File downloadFile = File.createTempFile("download", "file");
        FileUtils.delete(downloadFile);
        String downloadedFileName = downloadFile.getAbsolutePath();
        sftpChannel.get(uploadedFileName, downloadedFileName);

        File downloadedFile = new File(downloadedFileName);
        Assert.assertTrue(downloadedFile.exists());

        String fileData = getFileContents(downloadedFile);

        Assert.assertEquals(testFileContents, fileData);

        if (sftpChannel.isConnected()) {
            sftpChannel.exit();
            System.out.println("Disconnected channel");
        }

        if (session.isConnected()) {
            session.disconnect();
            System.out.println("Disconnected session");
        }

    }
}

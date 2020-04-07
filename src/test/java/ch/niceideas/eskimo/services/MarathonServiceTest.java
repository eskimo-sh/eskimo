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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileUtils;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.fail;

public class MarathonServiceTest extends AbstractSystemTest {

    private static final Logger logger = Logger.getLogger(MarathonServiceTest.class);


    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setupService.setConfigStoragePathInternal(createTempStoragePath());
    }

    public static String createTempStoragePath() throws Exception {
        File dtempFileName = File.createTempFile("test_marathonservice_", "config_storage");
        FileUtils.delete (dtempFileName); // delete file to create directory below

        File configStoragePathFile = new File (dtempFileName.getAbsolutePath() + "/");
        configStoragePathFile.mkdirs();
        return configStoragePathFile.getAbsolutePath();
    }

    @Test
    public void testQueryMarathon () throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testUpdateMarathon () throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testApplyMarathonServicesConfig () throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testFindUniqueServiceIP () throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testUninstallMarathonService () throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testInstallMarathonService () throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testFetchMarathonServicesStatus () throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testShouldInstall () throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testShowJournalMarathon () throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testStartServiceMarathon () throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testStopServiceMarathon () throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testRestartServiceMarathon() throws Exception {
        fail ("To Be Implemented");
    }
}

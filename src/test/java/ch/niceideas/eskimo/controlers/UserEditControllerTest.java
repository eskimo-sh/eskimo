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

package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.test.services.UserDetailsManagerTestImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-conf", "test-security"})
public class UserEditControllerTest {

    @Autowired
    private UserEditController userEditController;

    @Autowired
    private UserDetailsManagerTestImpl userDetailsManagerTest;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    public void testSetup() {
        userDetailsManagerTest.createUser(new User("testUser", passwordEncoder.encode("testPassword"), Collections.emptyList()));
    }

    @Test
    public void testEditUser() {
        JsonWrapper userData = new JsonWrapper("{}}");
        userData.setValueForPath("edit-user-user-id", "testUser");
        userData.setValueForPath("edit-user-current-password", "testPassword");
        userData.setValueForPath("edit-user-new-password", "testPasswordNew");
        userData.setValueForPath("edit-user-repeat-password", "testPasswordNew");

        assertEquals ("{\n" +
                "  \"message\": \"password successfully changed.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", userEditController.editUser(userData.getFormattedValue()));
    }

    @Test
    public void testEditUser_WrongPassword() {
        JsonWrapper userData = new JsonWrapper("{}}");
        userData.setValueForPath("edit-user-user-id", "testUser");
        userData.setValueForPath("edit-user-current-password", "wrong");
        userData.setValueForPath("edit-user-new-password", "testPasswordNew");
        userData.setValueForPath("edit-user-repeat-password", "testPasswordNew");

        assertEquals ("{\n" +
                "  \"error\": \"Authentication failed. Perhaps wrong current password ?\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", userEditController.editUser(userData.getFormattedValue()));
    }

    @Test
    public void testEditUser_NonMatchingPassword() {
        JsonWrapper userData = new JsonWrapper("{}}");
        userData.setValueForPath("edit-user-user-id", "testUser");
        userData.setValueForPath("edit-user-current-password", "testPassword");
        userData.setValueForPath("edit-user-new-password", "testPasswordNew");
        userData.setValueForPath("edit-user-repeat-password", "nonMatching");

        assertEquals ("{\n" +
                "  \"error\": \"Both password don't match\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", userEditController.editUser(userData.getFormattedValue()));
    }
}

/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.File;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class JSONBackedUserDetailsManagerTest {

    private JSONBackedUserDetailsManager mgr = null;

    private BCryptPasswordEncoder passwordEncoder = null;

    private File tmpUsersFile = null;

    @BeforeEach
    public void setUp() throws Exception {

        passwordEncoder = new BCryptPasswordEncoder(11);

        tmpUsersFile = File.createTempFile("users-config", ".json");

        mgr = new JSONBackedUserDetailsManager(tmpUsersFile.getAbsolutePath(), passwordEncoder);
    }

    @Test
    public void testDefaultUserInitialization() {
        assertTrue (mgr.userExists("admin"));

        assertTrue (passwordEncoder.matches("password", mgr.loadUserByUsername("admin").getPassword()));

        assertThrows(UsernameNotFoundException.class, () -> mgr.loadUserByUsername(null) );
        assertThrows(UsernameNotFoundException.class, () -> mgr.loadUserByUsername("blabla") );
    }

    @Test
    public void testAddUserPersisted() throws Exception {

        User newUser = new User("test_user", passwordEncoder.encode("test_password"),
                true, true, true, true,
                new ArrayList<SimpleGrantedAuthority>() {{
                    add (new SimpleGrantedAuthority("admin"));
                }}
        );

        mgr.createUser(newUser);

        JSONBackedUserDetailsManager otherMgr = new JSONBackedUserDetailsManager(
                tmpUsersFile.getAbsolutePath(), passwordEncoder);

        assertTrue (otherMgr.userExists("test_user"));
        assertTrue (passwordEncoder.matches("test_password",
                otherMgr.loadUserByUsername("test_user").getPassword()));
    }

    @Test
    public void testDeleteUserPersisted() throws Exception {

        // use other test to add user
        testAddUserPersisted();

        mgr.deleteUser("test_user");

        JSONBackedUserDetailsManager otherMgr = new JSONBackedUserDetailsManager(
                tmpUsersFile.getAbsolutePath(), passwordEncoder);

        assertFalse (otherMgr.userExists("test_user"));
    }

    @Test
    public void testUpdatePasswordPersisted() throws Exception {

        // use other test to add user
        testAddUserPersisted();

        UserDetails testUser = mgr.loadUserByUsername("test_user");
        assertNotNull(testUser);

        mgr.updatePassword(testUser, passwordEncoder.encode("other-password"));

        JSONBackedUserDetailsManager otherMgr = new JSONBackedUserDetailsManager(
                tmpUsersFile.getAbsolutePath(), passwordEncoder);

        assertTrue (otherMgr.userExists("test_user"));
        assertTrue (passwordEncoder.matches("other-password", otherMgr.loadUserByUsername("test_user").getPassword()));

    }

    @Test
    public void testChangePassword() throws Exception {

        // use other test to add user
        testAddUserPersisted();

        UserDetails testUser = mgr.loadUserByUsername("test_user");
        assertNotNull(testUser);

        TestingAuthenticationToken auth = new TestingAuthenticationToken(testUser, null);

        SecurityContextHolder.getContext().setAuthentication(auth);

        mgr.changePassword("test_password", "other-password");

        JSONBackedUserDetailsManager otherMgr = new JSONBackedUserDetailsManager(
                tmpUsersFile.getAbsolutePath(), passwordEncoder);

        assertTrue (otherMgr.userExists("test_user"));
        assertTrue (passwordEncoder.matches("other-password", otherMgr.loadUserByUsername("test_user").getPassword()));


    }
}

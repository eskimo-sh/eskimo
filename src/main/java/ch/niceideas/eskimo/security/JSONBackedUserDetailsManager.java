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

package ch.niceideas.eskimo.security;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.util.Assert;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JSONBackedUserDetailsManager implements UserDetailsManager, UserDetailsPasswordService {

    private static final Logger logger = Logger.getLogger(JSONBackedUserDetailsManager.class);

    private static final String DEFAULT_USER = "{ \"users\" : [ { \"username\" : \"admin\", \"password\" : \"$2a$10$W5pa6y.k95V27ABPd7eFqeqniTnpYqYOiGl75jJoXApG8SBEvERYO\" } ] }";

    private final Map<String, MutableUser> users = new ConcurrentHashMap<>();

    private AuthenticationManager authenticationManager;

    private final String jsonFilePath;

    public JSONBackedUserDetailsManager(String jsonFilePath) throws FileException, JSONException {

        this.jsonFilePath = jsonFilePath;

        File configFile = new File(jsonFilePath);
        if (!configFile.exists()) {
            FileUtils.writeFile(configFile, DEFAULT_USER);
        }

        String configContent = FileUtils.readFile(configFile);
        if (StringUtils.isBlank(configContent)) {
            configContent = DEFAULT_USER;
            FileUtils.writeFile(configFile, DEFAULT_USER);
        }

        JsonWrapper usersConfig = new JsonWrapper(configContent);

        JSONArray usersFromConfig = usersConfig.getJSONObject().getJSONArray("users");

        if (usersFromConfig != null) {

            for (int i = 0; i < usersFromConfig.length(); i++) {
                JSONObject userObject = usersFromConfig.getJSONObject(i);

                String username = userObject.getString("username");
                String password = userObject.getString("password");

                UserDetails user = new User(username, password, true, true,
                        true, true, new ArrayList<GrantedAuthority>() {{ add (new SimpleGrantedAuthority("admin"));}});

                users.put(user.getUsername().toLowerCase(), new MutableUser(user));
            }
        }
    }

    public void createUser(UserDetails user) {
        Assert.isTrue(!userExists(user.getUsername()), "user should not exist");

        users.put(user.getUsername().toLowerCase(), new MutableUser(user));

        saveUserConfig();
    }

    private synchronized void saveUserConfig() {

        List<Object> usersArray = new ArrayList<>();

        for (String username : users.keySet()) {

            MutableUser user = users.get(username);

            JSONObject userObject = new JSONObject(new HashMap<String, Object>(){{
                put ("username", user.getUsername());
                put ("password", user.getPassword());
            }});

            usersArray.add(userObject);
        }

        JSONArray usersObject = new JSONArray(usersArray);

        JSONObject usersConfigObject = new JSONObject(new HashMap<String, Object>() {{
            put ("users", usersObject);
        }});

        try {
            FileUtils.writeFile(new File (jsonFilePath), usersConfigObject.toString(2));
        } catch (FileException | JSONException e) {
            logger.error (e, e);
            throw new RuntimeException(e);
        }
    }

    public void deleteUser(String username) {
        users.remove(username.toLowerCase());

        saveUserConfig();
    }

    public void updateUser(UserDetails user) {
        Assert.isTrue(userExists(user.getUsername()), "user should exist");

        users.put(user.getUsername().toLowerCase(), new MutableUser(user));

        saveUserConfig();
    }

    public boolean userExists(String username) {
        return users.containsKey(username.toLowerCase());
    }

    public void changePassword(String oldPassword, String newPassword) {
        Authentication currentUser = SecurityContextHolder.getContext()
                .getAuthentication();

        if (currentUser == null) {
            // This would indicate bad coding somewhere
            throw new AccessDeniedException(
                    "Can't change password as no Authentication object found in context "
                            + "for current user.");
        }

        String username = currentUser.getName();

        logger.debug("Changing password for user '" + username + "'");

        // If an authentication manager has been set, re-authenticate the user with the
        // supplied password.
        if (authenticationManager != null) {
            logger.debug("Reauthenticating user '" + username
                    + "' for password change request.");

            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    username, oldPassword));
        }
        else {
            logger.debug("No authentication manager set. Password won't be re-checked.");
        }

        MutableUser user = users.get(username);

        if (user == null) {
            throw new IllegalStateException("Current user doesn't exist in database.");
        }

        user.setPassword(newPassword);

        saveUserConfig();
    }

    @Override
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        String username = user.getUsername();
        MutableUser mutableUser = this.users.get(username.toLowerCase());
        mutableUser.setPassword(newPassword);

        saveUserConfig();

        return mutableUser;
    }

    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        UserDetails user = users.get(username.toLowerCase());

        if (user == null) {
            throw new UsernameNotFoundException(username);
        }

        return new User(user.getUsername(), user.getPassword(), user.isEnabled(),
                user.isAccountNonExpired(), user.isCredentialsNonExpired(),
                user.isAccountNonLocked(), user.getAuthorities());
    }

    public void setAuthenticationManager(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }
}

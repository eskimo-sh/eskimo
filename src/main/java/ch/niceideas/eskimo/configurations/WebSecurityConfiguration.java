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

package ch.niceideas.eskimo.configurations;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.security.JSONBackedUserDetailsManager;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private static final Logger logger = Logger.getLogger(WebSecurityConfiguration.class);

    @Value("${security.userJsonFile}")
    private String userJsonFilePath = "/tmp/eskimo-users.json";

    @Value("${server.servlet.context-path:#{null}}")
    private String configuredContextPath = "";

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        String contextPath = StringUtils.isBlank(configuredContextPath) ?
                "" :
                (configuredContextPath.startsWith("/") ? "" : "/") + configuredContextPath;

        http
            // authentication and authorization stuff
            .authorizeRequests()
                .antMatchers("/login.html").permitAll()
                .antMatchers("/css/**").permitAll()
                .antMatchers("/scripts/**").permitAll()
                .antMatchers("/images/**").permitAll()
                .antMatchers("/fonts/**").permitAll()
                .antMatchers("/html/**").permitAll()
                .antMatchers("/index.html").authenticated()
                .anyRequest().authenticated()
                .and()
                .exceptionHandling()
                // way to avoid sending redirect to AJAX call (they tend not to like it)
                .authenticationEntryPoint((httpServletRequest, httpServletResponse, e) -> {
                    if (isAjax(httpServletRequest)) {
                        httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    } else {
                        httpServletResponse.sendRedirect(contextPath + "/login.html");
                    }
                }).and()
            // own login stuff
            .formLogin()
                .loginPage("/login.html").permitAll()
                .loginProcessingUrl("/login").permitAll()
                .defaultSuccessUrl("/index.html",true)
                .and()
            .logout().permitAll()
                .and()
             // disabling CSRF security as long as not implemented backend side
            .csrf().disable()
            // disabling Same origin policy on iframes (eskimo uses this extensively)
            .headers().frameOptions().disable();
    }

    private Boolean isAjax(HttpServletRequest request) {
        return request.getHeader("accept").contains("json")
                || request.getHeader("accept").contains("javascript");
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService())
                .passwordEncoder(new BCryptPasswordEncoder(11));
    }

    @Override
    public UserDetailsService userDetailsService() {
        try {
            return new JSONBackedUserDetailsManager(userJsonFilePath);
        } catch (FileException | JSONException e) {
            logger.error (e, e);
            throw new RuntimeException(e);
        }
    }
}
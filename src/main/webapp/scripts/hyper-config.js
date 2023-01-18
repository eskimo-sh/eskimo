/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
Author : eskimo.sh / https://www.eskimo.sh

Eskimo is available under a dual licensing model : commercial and GNU AGPL.
If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
any later version.
Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
commercial license.

Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Affero Public License for more details.

You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
Boston, MA, 02110-1301 USA.

You can be released from the requirements of the license by purchasing a commercial license. Buying such a
commercial license is mandatory as soon as :
- you develop activities involving Eskimo without disclosing the source code of your own product, software,
  platform, use cases or scripts.
- you deploy eskimo as part of a commercial product, platform or software.
For more information, please contact eskimo.sh at https://www.eskimo.sh

The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
Software.
*/


! function() {
    var t = sessionStorage.getItem("__HYPER_CONFIG__"),
        e = document.getElementsByTagName("html")[0],
        i = {
            theme: "light",
            nav: "vertical",
            layout: {
                mode: "fluid",
                position: "fixed"
            },
            topbar: {
                color: "dark"
            },
            sidenav: {
                color: "dark",
                size: "default",
                user: !1
            }
        },
        o = (this.html = document.getElementsByTagName("html")[0], config = Object.assign(JSON.parse(JSON.stringify(i)), {}), this.html.getAttribute("data-theme")),
        o = (config.theme = null !== o ? o : i.theme, this.html.getAttribute("data-layout")),
        o = (config.nav = null !== o ? "topnav" === o ? "horizontal" : "vertical" : i.nav, this.html.getAttribute("data-layout-mode")),
        o = (config.layout.mode = null !== o ? o : i.layout.mode, this.html.getAttribute("data-layout-position")),
        o = (config.layout.position = null !== o ? o : i.layout.position, this.html.getAttribute("data-topbar-color")),
        o = (config.topbar.color = null != o ? o : i.topbar.color, this.html.getAttribute("data-sidenav-color")),
        o = (config.sidenav.color = null !== o ? o : i.sidenav.color, this.html.getAttribute("data-sidenav-size")),
        o = (config.sidenav.size = null !== o ? o : i.sidenav.size, this.html.getAttribute("data-sidenav-user"));
    config.sidenav.user = null !== o || i.sidenav.user, 
    window.defaultConfig = JSON.parse(JSON.stringify(config)), null !== t && (config = JSON.parse(t)), 
    window.config = config, 
    "topnav" === e.getAttribute("data-layout") ? config.nav = "horizontal" : config.nav = "vertical", 
    config &&  (
        e.setAttribute("data-theme", config.theme), 
        e.setAttribute("data-layout-mode", config.layout.mode), 
        e.setAttribute("data-topbar-color", config.topbar.color), 
        "vertical" == config.nav && (
            e.setAttribute("data-sidenav-size", config.sidenav.size), 
            e.setAttribute("data-sidenav-color", config.sidenav.color), 
            e.setAttribute("data-layout-position", config.layout.position), 
            config.sidenav.user && "true" === config.sidenav.user.toString() ? e.setAttribute("data-sidenav-user", !0) : e.removeAttribute("data-sidenav-user")
            )
        )
}();

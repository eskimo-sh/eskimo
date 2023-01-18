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



class ThemeCustomizer {

    constructor() {
        this.html = document.getElementsByTagName('html')[0]
        this.config = {};
        this.defaultConfig = window.config;
    }

    initConfig() {
        this.defaultConfig = JSON.parse(JSON.stringify(window.defaultConfig));
        this.config = JSON.parse(JSON.stringify(window.config));
        this.setSwitchFromConfig();
    }

    changeLeftbarColor(color) {
        this.config.sidenav.color = color;
        this.html.setAttribute('data-sidenav-color', color);
        this.setSwitchFromConfig();
    }

    changeLeftbarSize(size, save = true) {
        this.html.setAttribute('data-sidenav-size', size);
        if (save) {
            this.config.sidenav.size = size;
            this.setSwitchFromConfig();
        }
    }

    changeLayoutMode(mode, save = true) {
        this.html.setAttribute('data-layout-mode', mode);
        if (save) {
            this.config.layout.mode = mode;
            this.setSwitchFromConfig();
        }
    }

    changeLayoutPosition(position) {
        this.config.layout.position = position;
        this.html.setAttribute('data-layout-position', position);
        this.setSwitchFromConfig();
    }

    changeLayoutColor(color) {
        this.config.theme = color;
        this.html.setAttribute('data-theme', color);
        this.setSwitchFromConfig();
    }

    changeTopbarColor(color) {
        this.config.topbar.color = color;
        this.html.setAttribute('data-topbar-color', color);
        this.setSwitchFromConfig();
    }

    changeSidebarUser(showUser) {

        this.config.sidenav.user = showUser;
        if (showUser) {
            this.html.setAttribute('data-sidenav-user', showUser);
        } else {
            this.html.removeAttribute('data-sidenav-user');
        }
        this.setSwitchFromConfig();
    }

    resetTheme() {
        this.config = JSON.parse(JSON.stringify(window.defaultConfig));
        this.changeLeftbarColor(this.config.sidenav.color);
        this.changeLeftbarSize(this.config.sidenav.size);
        this.changeLayoutColor(this.config.theme);
        this.changeLayoutMode(this.config.layout.mode);
        this.changeLayoutPosition(this.config.layout.position);
        this.changeTopbarColor(this.config.topbar.color);
        this.changeSidebarUser(this.config.sidenav.user);
        this._adjustLayout();
    }

    initSwitchListener() {
        const self = this;
        document.querySelectorAll('input[name=data-sidenav-color]').forEach(function (element) {
            element.addEventListener('change', () => {
                self.changeLeftbarColor(element.value);
            })
        });
        document.querySelectorAll('input[name=data-sidenav-size]').forEach(function (element) {
            element.addEventListener('change', () => {
                self.changeLeftbarSize(element.value);
            })
        });

        document.querySelectorAll('input[name=data-theme]').forEach(function (element) {
            element.addEventListener('change', () => {
                self.changeLayoutColor(element.value);
            })
        });
        document.querySelectorAll('input[name=data-layout-mode]').forEach(function (element) {
            element.addEventListener('change', () => {
                self.changeLayoutMode(element.value);
            })
        });

        document.querySelectorAll('input[name=data-layout-position]').forEach(function (element) {
            element.addEventListener('change', () =>{
                self.changeLayoutPosition(element.value);
            })
        });
        document.querySelectorAll('input[name=data-layout]').forEach(function (element) {
            element.addEventListener('change', () => {
                window.location = element.value === 'horizontal' ? 'layouts-horizontal.html' : 'index.html'
            })
        });
        document.querySelectorAll('input[name=data-topbar-color]').forEach(function (element) {
            element.addEventListener('change', () =>{
                self.changeTopbarColor(element.value);
            })
        });
        document.querySelectorAll('input[name=sidebar-user]').forEach(function (element) {
            element.addEventListener('change', () => {
                self.changeSidebarUser(element.checked);
            })
        });

        //TopBar Light Dark
        const themeColorToggle = document.getElementById('light-dark-mode');
        if (themeColorToggle) {
            themeColorToggle.addEventListener('click', () =>{

                if (self.config.theme === 'light') {
                    self.changeLayoutColor('dark');
                } else {
                    self.changeLayoutColor('light');
                }
            });
        }

        const resetBtn = document.querySelector('#reset-layout')
        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                self.resetTheme();
            });
        }

        const menuToggleBtn = document.querySelector('.button-toggle-menu');
        if (menuToggleBtn) {
            menuToggleBtn.addEventListener('click', () => {
                let configSize = self.config.sidenav.size;
                let size = self.html.getAttribute('data-sidenav-size', configSize);

                if (size !== 'full') {
                    if (size === 'condensed') {
                        self.changeLeftbarSize(configSize === 'condensed' ? 'default' : configSize, false);
                    } else {
                        self.changeLeftbarSize('condensed', false);
                        $(".simplebar-content-wrapper").css("width", "");
                        $(".simplebar-offset").css("width", "");
                    }
                    self.html.classList.remove('sidebar-enable');
                } else {
                    $(".simplebar-content-wrapper").css("width", "");
                    $(".simplebar-offset").css("width", "");
                    self.showBackdrop();

                    self.html.classList.toggle('sidebar-enable');
                }
            });
        }

        const hoverBtn = document.querySelectorAll('.button-sm-hover');
        hoverBtn.forEach(function (element) {
            element.addEventListener('click', () => {
                let configSize = self.config.sidenav.size;
                let size = self.html.getAttribute('data-sidenav-size', configSize);

                if (configSize === 'sm-hover') {
                    if (size === 'sm-hover-active') {
                        self.changeLeftbarSize('sm-hover', false);
                    } else {
                        self.changeLeftbarSize('sm-hover-active', false);
                    }
                }
            });
        })
    }

    showBackdrop() {
        if (document.getElementById("sidebar-backdrop")) {
            return;
        }
        const backdrop = document.createElement('div');
        backdrop.id = "sidebar-backdrop"
        backdrop.classList = 'offcanvas-backdrop fade show';
        document.body.appendChild(backdrop);
        document.body.style.overflow = "hidden";
        if (window.innerWidth > 750) {
            // document.body.style.paddingRight = "15px";
        }
        const self = this
        backdrop.addEventListener('click', () => {
            self.html.classList.remove('sidebar-enable');
            document.body.removeChild(backdrop);
            document.body.style.overflow = null;
            // document.body.style.paddingRight = null;
        })
    }

    initWindowSize() {
        const self = this;
        window.addEventListener('resize', () => {
            self._adjustLayout();
        })
    }

    _adjustLayout() {
        const self = this;

        if (window.innerWidth <= 750) {
            self.changeLeftbarSize('full', false);
        }
        else if (window.innerWidth >= 750 && window.innerWidth <= 1140) {
            if (self.config.sidenav.size !== 'full') {
                if (self.config.sidenav.size === 'sm-hover') {
                    self.changeLeftbarSize('condensed');
                } else {
                    self.changeLeftbarSize('condensed', false);
                }
            }
            if (self.config.layout.mode === 'boxed') {
                self.changeLayoutMode('fluid', false)
            }

        } else {
            self.changeLeftbarSize(self.config.sidenav.size);
            self.changeLayoutMode(self.config.layout.mode);
        }
    }

    setSwitchFromConfig() {

        sessionStorage.setItem('__HYPER_CONFIG__', JSON.stringify(this.config));
        // localStorage.setItem('__HYPER_CONFIG__', JSON.stringify(this.config));

        document.querySelectorAll('.right-bar input[type=checkbox]').forEach(function (checkbox) {
            checkbox.checked = false;
        })

        const config = this.config;
        if (config) {
            let layoutNavSwitch = document.querySelector('input[type=radio][name=data-layout][value=' + config.nav + ']');
            let layoutColorSwitch = document.querySelector('input[type=radio][name=data-theme][value=' + config.theme + ']');
            let layoutModeSwitch = document.querySelector('input[type=radio][name=data-layout-mode][value=' + config.layout.mode + ']');
            let topbarColorSwitch = document.querySelector('input[type=radio][name=data-topbar-color][value=' + config.topbar.color + ']');
            let leftbarColorSwitch = document.querySelector('input[type=radio][name=data-sidenav-color][value=' + config.sidenav.color + ']');
            let leftbarSizeSwitch = document.querySelector('input[type=radio][name=data-sidenav-size][value=' + config.sidenav.size + ']');
            let layoutSizeSwitch = document.querySelector('input[type=radio][name=data-layout-position][value=' + config.layout.position + ']');
            let sidebarUserSwitch = document.querySelector('input[type=checkbox][name=sidebar-user]');

            if (layoutNavSwitch) layoutNavSwitch.checked = true;
            if (layoutColorSwitch) layoutColorSwitch.checked = true;
            if (layoutModeSwitch) layoutModeSwitch.checked = true;
            if (topbarColorSwitch) topbarColorSwitch.checked = true;
            if (leftbarColorSwitch) leftbarColorSwitch.checked = true;
            if (leftbarSizeSwitch) leftbarSizeSwitch.checked = true;
            if (layoutSizeSwitch) layoutSizeSwitch.checked = true;
            if (sidebarUserSwitch && config.sidenav.user.toString() === "true") sidebarUserSwitch.checked = true;
        }
    }

    init() {
        this.initConfig();
        this.initSwitchListener();
        this.initWindowSize();
        this._adjustLayout();
        this.setSwitchFromConfig();

    }
}


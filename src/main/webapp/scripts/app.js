
(function ($) {

    'use strict';

    // Bootstrap Components
    function initComponents() {

        // loader - Preloader
        $(window).on('load', function () {
            $('#status').fadeOut();
            $('#preloader').delay(350).fadeOut('slow');
        });

        // Popovers
        const popoverTriggerList = document.querySelectorAll('[data-bs-toggle="popover"]')
        const popoverList = [...popoverTriggerList].map(popoverTriggerEl => new bootstrap.Popover(popoverTriggerEl))

        // Tooltips
        const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]')
        const tooltipList = [...tooltipTriggerList].map(tooltipTriggerEl => new bootstrap.Tooltip(tooltipTriggerEl))

        // offcanvas
        const offcanvasElementList = document.querySelectorAll('.offcanvas')
        const offcanvasList = [...offcanvasElementList].map(offcanvasEl => new bootstrap.Offcanvas(offcanvasEl))

        //Toasts
        var toastPlacement = document.getElementById("toastPlacement");
        if (toastPlacement) {
            document.getElementById("selectToastPlacement").addEventListener("change", function () {
                if (!toastPlacement.dataset.originalClass) {
                    toastPlacement.dataset.originalClass = toastPlacement.className;
                }
                toastPlacement.className = toastPlacement.dataset.originalClass + " " + this.value;
            });
        }

        var toastElList = [].slice.call(document.querySelectorAll('.toast'))
        var toastList = toastElList.map(function (toastEl) {
            return new bootstrap.Toast(toastEl)
        })

        // Bootstrap Alert Live Example
        const alertPlaceholder = document.getElementById('liveAlertPlaceholder')
        const alert = (message, type) => {
            const wrapper = document.createElement('div')
            wrapper.innerHTML = [
                `<div class="alert alert-${type} alert-dismissible" role="alert">`,
                `   <div>${message}</div>`,
                '   <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>',
                '</div>'
            ].join('')

            alertPlaceholder.append(wrapper)
        }

        const alertTrigger = document.getElementById('liveAlertBtn')
        if (alertTrigger) {
            alertTrigger.addEventListener('click', () => {
                alert('Nice, you triggered this alert message!', 'success')
            })
        }

        // RTL Layout
        if (document.getElementById('app-style').href.includes('rtl.min.css')) {
            document.getElementsByTagName('html')[0].dir = "rtl";
        }
    }

    // Portlet Widget (Card Reload, Collapse, and Delete)
    function initPortletCard() {

        var portletIdentifier = ".card"
        var portletCloser = '.card a[data-bs-toggle="remove"]'
        var portletRefresher = '.card a[data-bs-toggle="reload"]'
        let self = this

        // Panel closest
        $(document).on("click", portletCloser, function (ev) {
            ev.preventDefault();
            var $portlet = $(this).closest(portletIdentifier);
            var $portlet_parent = $portlet.parent();
            $portlet.remove();
            if ($portlet_parent.children().length == 0) {
                $portlet_parent.remove();
            }
        });

        // Panel Reload
        $(document).on("click", portletRefresher, function (ev) {
            ev.preventDefault();
            var $portlet = $(this).closest(portletIdentifier);
            // This is just a simulation, nothing is going to be reloaded
            $portlet.append('<div class="card-disabled"><div class="card-portlets-loader"></div></div>');
            var $pd = $portlet.find('.card-disabled');
            setTimeout(function () {
                $pd.fadeOut('fast', function () {
                    $pd.remove();
                });
            }, 500 + 300 * (Math.random() * 5));
        });
    }

    //  Multi Dropdown
    function initMultiDropdown() {
        $('.dropdown-menu a.dropdown-toggle').on('click', function () {
            var dropdown = $(this).next('.dropdown-menu');
            var otherDropdown = $(this).parent().parent().find('.dropdown-menu').not(dropdown);
            otherDropdown.removeClass('show')
            otherDropdown.parent().find('.dropdown-toggle').removeClass('show')
            return false;
        });
    }

    // Left Sidebar Menu (Vertical Menu)
    function initLeftSidebar() {
        var self = this;

        if ($(".side-nav").length) {
            var navCollapse = $('.side-nav li .collapse');
            var navToggle = $(".side-nav li [data-bs-toggle='collapse']");
            navToggle.on('click', function (e) {
                return false;
            });

            // open one menu at a time only
            navCollapse.on({
                'show.bs.collapse': function (event) {
                    var parent = $(event.target).parents('.collapse.show');
                    $('.side-nav .collapse.show').not(event.target).not(parent).collapse('hide');
                }
            });

            // activate the menu in left side bar (Vertical Menu) based on url
            $(".side-nav a").each(function () {
                var pageUrl = window.location.href.split(/[?#]/)[0];
                if (this.href == pageUrl) {
                    $(this).addClass("active");
                    $(this).parent().addClass("menuitem-active");
                    $(this).parent().parent().parent().addClass("show");
                    $(this).parent().parent().parent().parent().addClass("menuitem-active"); // add active to li of the current link

                    var firstLevelParent = $(this).parent().parent().parent().parent().parent().parent();
                    if (firstLevelParent.attr('id') !== 'sidebar-menu') firstLevelParent.addClass("show");

                    $(this).parent().parent().parent().parent().parent().parent().parent().addClass("menuitem-active");

                    var secondLevelParent = $(this).parent().parent().parent().parent().parent().parent().parent().parent().parent();
                    if (secondLevelParent.attr('id') !== 'wrapper') secondLevelParent.addClass("show");

                    var upperLevelParent = $(this).parent().parent().parent().parent().parent().parent().parent().parent().parent().parent();
                    if (!upperLevelParent.is('body')) upperLevelParent.addClass("menuitem-active");
                }
            });


            setTimeout(function () {
                var activatedItem = document.querySelector('li.menuitem-active .active');
                if (activatedItem != null) {
                    var simplebarContent = document.querySelector('.leftside-menu .simplebar-content-wrapper');
                    var offset = activatedItem.offsetTop - 300;
                    if (simplebarContent && offset > 100) {
                        scrollTo(simplebarContent, offset, 600);
                    }
                }
            }, 200);

            // scrollTo (Left Side Bar Active Menu)
            function easeInOutQuad(t, b, c, d) {
                t /= d / 2;
                if (t < 1) return c / 2 * t * t + b;
                t--;
                return -c / 2 * (t * (t - 2) - 1) + b;
            }
            function scrollTo(element, to, duration) {
                var start = element.scrollTop, change = to - start, currentTime = 0, increment = 20;
                var animateScroll = function () {
                    currentTime += increment;
                    var val = easeInOutQuad(currentTime, start, change, duration);
                    element.scrollTop = val;
                    if (currentTime < duration) {
                        setTimeout(animateScroll, increment);
                    }
                };
                animateScroll();
            }
        }
    }

    // Topbar Menu (HOrizontal Menu)
    function initTopbarMenu() {
        if ($('.topnav-menu').length) {
            $('.topnav-menu li a').each(function () {
                var pageUrl = window.location.href.split(/[?#]/)[0];
                if (this.href == pageUrl) {
                    $(this).addClass('active');
                    $(this).parent().parent().addClass('active'); // add active to li of the current link
                    $(this).parent().parent().parent().parent().addClass('active');
                    $(this).parent().parent().parent().parent().parent().parent().addClass('active');
                }
            });

            // Topbar - main menu
            $('.navbar-toggle').on('click', function () {
                $(this).toggleClass('open');
                $('#navigation').slideToggle(400);
            });
        }
    }

    // Topbar Search Form
    function initSearch() {
        // Serach Toggle
        var navDropdowns = $('.navbar-custom .dropdown:not(.app-search)');

        // hide on other click
        $(document).on('click', function (e) {
            if (e.target.id == "top-search" || e.target.closest('#search-dropdown')) {
                $('#search-dropdown').addClass('d-block');
            } else {
                $('#search-dropdown').removeClass('d-block');
            }
            return true;
        });

        // Serach Toggle
        $('#top-search').on('focus', function (e) {
            e.preventDefault();
            navDropdowns.children('.dropdown-menu.show').removeClass('show');
            $('#search-dropdown').addClass('d-block');
            return false;
        });

        // hide search on opening other dropdown
        navDropdowns.on('show.bs.dropdown', function () {
            $('#search-dropdown').removeClass('d-block');
        });
    }

    // Topbar Fullscreen Button
    function initfullScreenListener() {
        var self = this;
        var fullScreenBtn = document.querySelector('[data-toggle="fullscreen"]');

        if (fullScreenBtn) {
            fullScreenBtn.addEventListener('click', function (e) {
                e.preventDefault();
                document.body.classList.toggle('fullscreen-enable')
                if (!document.fullscreenElement && /* alternative standard method */ !document.mozFullScreenElement && !document.webkitFullscreenElement) {  // current working methods
                    if (document.documentElement.requestFullscreen) {
                        document.documentElement.requestFullscreen();
                    } else if (document.documentElement.mozRequestFullScreen) {
                        document.documentElement.mozRequestFullScreen();
                    } else if (document.documentElement.webkitRequestFullscreen) {
                        document.documentElement.webkitRequestFullscreen(Element.ALLOW_KEYBOARD_INPUT);
                    }
                } else {
                    if (document.cancelFullScreen) {
                        document.cancelFullScreen();
                    } else if (document.mozCancelFullScreen) {
                        document.mozCancelFullScreen();
                    } else if (document.webkitCancelFullScreen) {
                        document.webkitCancelFullScreen();
                    }
                }
            });
        }
    }

    // Show/Hide Password
    function initShowHidePassword() {
        $("[data-password]").on('click', function () {
            if ($(this).attr('data-password') == "false") {
                $(this).siblings("input").attr("type", "text");
                $(this).attr('data-password', 'true');
                $(this).addClass("show-password");
            } else {
                $(this).siblings("input").attr("type", "password");
                $(this).attr('data-password', 'false');
                $(this).removeClass("show-password");
            }
        });
    }

    // Form Validation
    function initFormValidation() {
        // Example starter JavaScript for disabling form submissions if there are invalid fields
        // Fetch all the forms we want to apply custom Bootstrap validation styles to
        // Loop over them and prevent submission
        document.querySelectorAll('.needs-validation').forEach(form => {
            form.addEventListener('submit', event => {
                if (!form.checkValidity()) {
                    event.preventDefault()
                    event.stopPropagation()
                }

                form.classList.add('was-validated')
            }, false)
        })
    }

    // Form Advance
    function initFormAdvance() {
        // Select2
        if (jQuery().select2) {
            $('[data-toggle="select2"]').select2();
        }

        // Input Mask
        if (jQuery().mask) {
            $('[data-toggle="input-mask"]').each(function (idx, obj) {
                var maskFormat = $(obj).data("maskFormat");
                var reverse = $(obj).data("reverse");
                if (reverse != null)
                    $(obj).mask(maskFormat, { 'reverse': reverse });
                else
                    $(obj).mask(maskFormat);
            });
        }

        // Date-Range-Picker
        if (jQuery().daterangepicker) {
            //date pickers ranges only
            var start = moment().subtract(29, 'days');
            var end = moment();
            var defaultRangeOptions = {
                startDate: start,
                endDate: end,
                ranges: {
                    'Today': [moment(), moment()],
                    'Yesterday': [moment().subtract(1, 'days'), moment().subtract(1, 'days')],
                    'Last 7 Days': [moment().subtract(6, 'days'), moment()],
                    'Last 30 Days': [moment().subtract(29, 'days'), moment()],
                    'This Month': [moment().startOf('month'), moment().endOf('month')],
                    'Last Month': [moment().subtract(1, 'month').startOf('month'), moment().subtract(1, 'month').endOf('month')]
                }
            };

            $('[data-toggle="date-picker-range"]').each(function (idx, obj) {
                var objOptions = $.extend({}, defaultRangeOptions, $(obj).data());
                var target = objOptions["targetDisplay"];
                //rendering
                $(obj).daterangepicker(objOptions, function (start, end) {
                    if (target)
                        $(target).html(start.format('MMMM D, YYYY') + ' - ' + end.format('MMMM D, YYYY'));
                });
            });

            // Datetime and date range picker
            var defaultOptions = {
                "cancelClass": "btn-light",
                "applyButtonClasses": "btn-success"
            };

            $('[data-toggle="date-picker"]').each(function (idx, obj) {
                var objOptions = $.extend({}, defaultOptions, $(obj).data());
                $(obj).daterangepicker(objOptions);
            });
        }

        // Bootstrap Timepicker
        if (jQuery().timepicker) {
            var defaultOptions = {
                "showSeconds": true,
                "icons": {
                    "up": "mdi mdi-chevron-up",
                    "down": "mdi mdi-chevron-down"
                }
            };

            $('[data-toggle="timepicker"]').each(function (idx, obj) {
                var objOptions = $.extend({}, defaultOptions, $(obj).data());
                $(obj).timepicker(objOptions);
            });
        }

        // Bootstrap Touchspin
        if (jQuery().TouchSpin) {
            var defaultOptions = {

            };

            $('[data-toggle="touchspin"]').each(function (idx, obj) {
                var objOptions = $.extend({}, defaultOptions, $(obj).data());
                $(obj).TouchSpin(objOptions);
            });
        }

        // Bootstrap Maxlength
        if (jQuery().maxlength) {
            var defaultOptions = {
                warningClass: "badge bg-success",
                limitReachedClass: "badge bg-danger",
                separator: ' out of ',
                preText: 'You typed ',
                postText: ' chars available.',
                placement: 'bottom',
            };

            $('[data-toggle="maxlength"]').each(function (idx, obj) {
                var objOptions = $.extend({}, defaultOptions, $(obj).data());
                $(obj).maxlength(objOptions);
            });
        }
    }

    function init() {
        initComponents();
        initPortletCard();
        initMultiDropdown();
        initLeftSidebar()
        initTopbarMenu();
        initSearch();
        initfullScreenListener();
        initShowHidePassword();
        initFormValidation();
        initFormAdvance();
    }

    init();

})(jQuery)



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
        var self = this;
        document.querySelectorAll('input[name=data-sidenav-color]').forEach(function (element) {
            element.addEventListener('change', function (e) {
                self.changeLeftbarColor(element.value);
            })
        });
        document.querySelectorAll('input[name=data-sidenav-size]').forEach(function (element) {
            element.addEventListener('change', function (e) {
                self.changeLeftbarSize(element.value);
            })
        });

        document.querySelectorAll('input[name=data-theme]').forEach(function (element) {
            element.addEventListener('change', function (e) {
                self.changeLayoutColor(element.value);
            })
        });
        document.querySelectorAll('input[name=data-layout-mode]').forEach(function (element) {
            element.addEventListener('change', function (e) {
                self.changeLayoutMode(element.value);
            })
        });

        document.querySelectorAll('input[name=data-layout-position]').forEach(function (element) {
            element.addEventListener('change', function (e) {
                self.changeLayoutPosition(element.value);
            })
        });
        document.querySelectorAll('input[name=data-layout]').forEach(function (element) {
            element.addEventListener('change', function (e) {
                window.location = element.value === 'horizontal' ? 'layouts-horizontal.html' : 'index.html'
            })
        });
        document.querySelectorAll('input[name=data-topbar-color]').forEach(function (element) {
            element.addEventListener('change', function (e) {
                self.changeTopbarColor(element.value);
            })
        });
        document.querySelectorAll('input[name=sidebar-user]').forEach(function (element) {
            element.addEventListener('change', function (e) {
                self.changeSidebarUser(element.checked);
            })
        });

        //TopBar Light Dark
        var themeColorToggle = document.getElementById('light-dark-mode');
        if (themeColorToggle) {
            themeColorToggle.addEventListener('click', function (e) {

                if (self.config.theme === 'light') {
                    self.changeLayoutColor('dark');
                } else {
                    self.changeLayoutColor('light');
                }
            });
        }

        var resetBtn = document.querySelector('#reset-layout')
        if (resetBtn) {
            resetBtn.addEventListener('click', function (e) {
                self.resetTheme();
            });
        }

        var menuToggleBtn = document.querySelector('.button-toggle-menu');
        if (menuToggleBtn) {
            menuToggleBtn.addEventListener('click', function () {
                var configSize = self.config.sidenav.size;
                var size = self.html.getAttribute('data-sidenav-size', configSize);

                console.log (configSize + " - " + size);

                if (size !== 'full') {
                    /*
                    if (size === 'condensed') {
                        self.changeLeftbarSize(configSize == 'condensed' ? 'default' : configSize, false);
                    } else {
                        self.changeLeftbarSize('condensed', false);
                    }
                    */
                    self.changeLeftbarSize('full', false);
                    self.html.classList.remove('sidebar-enable');
                } else {

                    if (window.innerWidth <= 1140) {
                        self.showBackdrop();
                    } else {
                        self.changeLeftbarSize('default', false);
                    }

                    self.html.classList.toggle('sidebar-enable');
                }

                // Todo: old implementation


                eskimoMain.menuResize();

            });
        }

        var hoverBtn = document.querySelectorAll('.button-sm-hover');
        hoverBtn.forEach(function (element) {
            element.addEventListener('click', function () {
                var configSize = self.config.sidenav.size;
                var size = self.html.getAttribute('data-sidenav-size', configSize);

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
        backdrop.addEventListener('click', function (e) {
            self.html.classList.remove('sidebar-enable');
            document.body.removeChild(backdrop);
            document.body.style.overflow = null;
            // document.body.style.paddingRight = null;
        })
    }

    initWindowSize() {
        var self = this;
        window.addEventListener('resize', function (e) {
            self._adjustLayout();
        })
    }

    _adjustLayout() {
        var self = this;

        if (window.innerWidth <= 1140) {
            self.changeLeftbarSize('full', false);

            if (self.html.classList.contains('sidebar-enable')) {
                self.showBackdrop();
            }


        }
        /*
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

        }
        */
        else {
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

        var config = this.config;
        if (config) {
            var layoutNavSwitch = document.querySelector('input[type=radio][name=data-layout][value=' + config.nav + ']');
            var layoutColorSwitch = document.querySelector('input[type=radio][name=data-theme][value=' + config.theme + ']');
            var layoutModeSwitch = document.querySelector('input[type=radio][name=data-layout-mode][value=' + config.layout.mode + ']');
            var topbarColorSwitch = document.querySelector('input[type=radio][name=data-topbar-color][value=' + config.topbar.color + ']');
            var leftbarColorSwitch = document.querySelector('input[type=radio][name=data-sidenav-color][value=' + config.sidenav.color + ']');
            var leftbarSizeSwitch = document.querySelector('input[type=radio][name=data-sidenav-size][value=' + config.sidenav.size + ']');
            var layoutSizeSwitch = document.querySelector('input[type=radio][name=data-layout-position][value=' + config.layout.position + ']');
            var sidebarUserSwitch = document.querySelector('input[type=checkbox][name=sidebar-user]');

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


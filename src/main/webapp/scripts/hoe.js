/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

let initHoe = function() {
    window.HoeDatapp = {
        appinit: function() {
            HoeDatapp.HandleSidebartoggle();
            HoeDatapp.Handlelpanel();
            HoeDatapp.Handlelpanelmenu();
            HoeDatapp.Handlethemeoption();
            HoeDatapp.Handlesidebareffect();
            HoeDatapp.Handlesidebarposition();
            HoeDatapp.Handlecontentheight();
            HoeDatapp.Handlethemecolor();
			HoeDatapp.Handlenavigationtype();
			HoeDatapp.Handlesidebarside();
			//HoeDatapp.Handleactivestatemenu();
			HoeDatapp.Handlethemelayout();
			HoeDatapp.Handlethemebackground();
			HoeDatapp.HandleSettings();


        },
		Handlethemebackground: function() {
            function setthemebgcolor() {
                $('#theme-color > a.theme-bg').on("click", function() {
                    $('body').attr("theme-bg", $(this).attr("hoe-themebg-type"));
                });
            }
			setthemebgcolor();
        },
        HandleSettings: function() {
            $('#cardHeight').on("change", function() {
               dashboardSettingCardHeightChange();
            });
        },
		Handlethemelayout: function() {
			 $('#theme-layout').on("change", function() {
                if ($(this).val() == 'box-layout') {
                  $('body').attr("theme-layout", "box-layout");
                }else {
				 $('body').attr("theme-layout", "wide-layout");
				}
            });
        },

		Handlesidebarside: function() {
			 $('#navigation-side').on("change", function() {
                if ($(this).val() == 'rightside') {
                  $('body').attr("hoe-nav-placement", "right");
				  $('body').attr("hoe-navigation-type", "vertical");
				  $('#hoeapp-wrapper').removeClass("compact-hmenu");
                }else {
				 $('body').attr("hoe-nav-placement", "left");
				 $('body').attr("hoe-navigation-type", "vertical");
				  $('#hoeapp-wrapper').removeClass("compact-hmenu");
				}
            });
        },
		Handlenavigationtype: function() {
			 $('#navigation-type').on("change", function() {
                if ($(this).val() == 'horizontal') {
                    $('body').attr("hoe-navigation-type", "horizontal");
					$('#hoeapp-wrapper').removeClass("compact-hmenu");
					$('#hoe-header, #hoeapp-container').removeClass("hoe-minimized-lpanel");
					$('body').attr("hoe-nav-placement", "left");
					$('#hoe-header').attr("hoe-color-type","logo-bg8");

                }else if  ($(this).val() == 'horizontal-compact'){
                    $('body').attr("hoe-navigation-type", "horizontal");
					$('#hoeapp-wrapper').addClass("compact-hmenu");
					$('#hoe-header, #hoeapp-container').removeClass("hoe-minimized-lpanel");
					$('body').attr("hoe-nav-placement", "left");
					$('#hoe-header').attr("hoe-color-type","logo-bg8");
                }else if  ($(this).val() == 'vertical-compact'){
                    $('body').attr("hoe-navigation-type", "vertical-compact");
					$('#hoeapp-wrapper').removeClass("compact-hmenu");
					$('#hoe-header, #hoeapp-container').addClass("hoe-minimized-lpanel");
					$('body').attr("hoe-nav-placement", "left");
                }else {
					$('body').attr("hoe-navigation-type", "vertical");
					$('#hoeapp-wrapper').removeClass("compact-hmenu");
					$('#hoe-header, #hoeapp-container').removeClass("hoe-minimized-lpanel");
					$('body').attr("hoe-nav-placement", "left");
				}
            });
        },

        Handlethemecolor: function() {

            function setheadercolor() {
                $('#theme-color > a.header-bg').on("click", function() {
                    $('#hoe-header > .hoe-right-header').attr("hoe-color-type", $(this).attr("hoe-color-type"));
                });
            }

            function setlpanelcolor() {
                $('#theme-color > a.lpanel-bg').on("click", function() {
                    $('#hoeapp-container').attr("hoe-color-type", $(this).attr("hoe-color-type"));
                });
            }

            function setllogocolor() {
                $('#theme-color > a.logo-bg').on("click", function() {
                    $('#hoe-header').attr("hoe-color-type", $(this).attr("hoe-color-type"));
                });
            }

            setheadercolor();
            setlpanelcolor();
            setllogocolor();
        },
        Handlecontentheight: function() {

            function setHeight() {
                let WH = $(window).height();
                let HH = $("#hoe-header").innerHeight();
                let FH = $("#footer").innerHeight();
                let contentH = WH - HH - FH - 2;
				let lpanelH = WH - HH - 2;
                $("#main-content ").css('min-height', contentH)
				 $(".inner-left-panel ").css('height', lpanelH)

            }

            setHeight();

            $(window).resize(function() {
                setHeight();
            });
        },
        Handlesidebarposition: function() {

            $('#sidebar-position').on("change", function() {
                if ($(this).val() == 'fixed') {
                    $('#hoe-left-panel,.hoe-left-header').attr("hoe-position-type", "fixed");
                } else {
                    $('#hoe-left-panel,.hoe-left-header').attr("hoe-position-type", "absolute");
                }
            });
        },
        Handlesidebareffect: function() {
            $('#leftpanel-effect').on("change", function() {
                if ($(this).val() == 'overlay') {
                    $('#hoe-header, #hoeapp-container').attr("hoe-lpanel-effect", "overlay");
                } else if ($(this).val() == 'push') {
                    $('#hoe-header, #hoeapp-container').attr("hoe-lpanel-effect", "push");
                } else {
                    $('#hoe-header, #hoeapp-container').attr("hoe-lpanel-effect", "shrink");
                }
            });

        },

        Handlethemeoption: function() {
            $('.selector-toggle > a').on("click", function() {
                $('#settingsSelector').toggleClass('open')
            });

        },
        HandlepanelmenuClick: function(jqEl) {
            let compactMenu = jqEl.closest('.hoe-minimized-lpanel').length;
            if (compactMenu === 0) {
                jqEl.parent('.hoe-has-menu').parent('ul').find('ul:visible').slideUp('fast');
                jqEl.parent('.hoe-has-menu').parent('ul').find('.opened').removeClass('opened');
                let submenu = jqEl.parent('.hoe-has-menu').find('>.hoe-sub-menu');
                if (submenu.is(':hidden')) {
                    submenu.slideDown('fast');
                    jqEl.parent('.hoe-has-menu').addClass('opened');
                } else {
                    jqEl.parent('.hoe-has-menu').parent('ul').find('ul:visible').slideUp('fast');
                    jqEl.parent('.hoe-has-menu').removeClass('opened');
                }
            }

            /*
            if (jqEl[0] && jqEl[0] != null && jqEl[0].parentNode.id && jqEl[0].parentNode.id != null) {
                let folderIdFl = jqEl[0].parentNode.id;
                selectFolder (folderIdFl.substring("folder_".length));
            }
            */
        },
        Handlelpanelmenu: function() {
            $('.hoe-has-menu > a').on("click", function() { HoeDatapp.HandlepanelmenuClick($(this)); });

        },
        HandleSidebartoggle: function() {
            $('.hoe-sidebar-toggle a').on("click", function() {
                if ($('#hoeapp-wrapper').attr("hoe-device-type") !== "phone") {
                    $('#hoeapp-container').toggleClass('hoe-minimized-lpanel');
                    $('#hoe-header').toggleClass('hoe-minimized-lpanel');
					if ($('body').attr("hoe-navigation-type") !== "vertical-compact") {
						$('body').attr("hoe-navigation-type", "vertical-compact");
					}else{
						$('body').attr("hoe-navigation-type", "vertical");
					}
                } else {
                    if (!$('#hoeapp-wrapper').hasClass('hoe-hide-lpanel')) {
                        $('#hoeapp-wrapper').addClass('hoe-hide-lpanel');
                    } else {
                        $('#hoeapp-wrapper').removeClass('hoe-hide-lpanel');
                    }
                }
                if (isFunction (eskimoMain.sidebarToggleClickedListener)) {
                    eskimoMain.sidebarToggleClickedListener();
                }
            });

        },
        Handlelpanel: function() {

            function Responsivelpanel() {

				let totalwidth = $(window)[0].innerWidth;
                if (totalwidth >= 768 && totalwidth <= 1024) {
                    $('#hoeapp-wrapper').attr("hoe-device-type", "tablet");
                    $('#hoe-header, #hoeapp-container').addClass('hoe-minimized-lpanel');
					$('li.theme-option select').attr('disabled', false);
                } else if (totalwidth < 768) {
                    $('#hoeapp-wrapper').attr("hoe-device-type", "phone");
                    $('#hoe-header, #hoeapp-container').removeClass('hoe-minimized-lpanel');
					$('li.theme-option select').attr('disabled', 'disabled');
                } else {
					if ($('body').attr("hoe-navigation-type") !== "vertical-compact") {
						$('#hoeapp-wrapper').attr("hoe-device-type", "desktop");
						$('#hoe-header, #hoeapp-container').removeClass('hoe-minimized-lpanel');
						$('li.theme-option select').attr('disabled', false);
					}else {
						$('#hoeapp-wrapper').attr("hoe-device-type", "desktop");
						$('#hoe-header, #hoeapp-container').addClass('hoe-minimized-lpanel');
						$('li.theme-option select').attr('disabled', false);

					}
                }
            }
            Responsivelpanel();
            $(window).resize(Responsivelpanel);

        }

    };
    HoeDatapp.appinit();
};

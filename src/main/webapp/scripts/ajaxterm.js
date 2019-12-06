/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

var ajaxterm={};

ajaxterm.Terminal=function(id,options) {
    // options
    var width  = options.width || 80; // dimension of the terminal
    var height = options.height || 25;
    var endpoint = options.endpoint;  // URL of the server endpoint that delivers the request to Session.handleUpdate
    var additionalQueryString = options.query; // additional parameters sent to the server

	var ie=0;
	if(window.ActiveXObject)
		ie=1;
	var sid=""+Math.round(Math.random()*1000000000);
	var query0="s="+sid+"&w="+width+"&h="+height;
	var query1=query0+"&c=1&k=";
	var buf="";
	var timeout;
    var screenTimestamp = 0;
	var error_timeout;
	var keybuf=[];
	var sending=0;
	var rmax=1;

	var div=(typeof(id)=="string" ? document.getElementById(id) : id);
    var fitter=document.createElement('div');   // for shrinking the screen area to the right size
	var dstat=document.createElement('pre');
	var sled=document.createElement('span');    // status LED. indicate the communication with the server
	var opt_get=document.createElement('a');    //
	var opt_color=document.createElement('a');
	var opt_paste=document.createElement('a');
	var sdebug=document.createElement('span');
    var spacer=document.createElement('div');   // creates border & padding around the main screen
    var screen = document.createElement('div'); // holds dterm&cursor. origin of the cursor positioning
	var dterm=document.createElement('div');    // area that shows the screen
    var cursor=document.createElement('div');   // cursor
	var showPrevTab = null;
    var showNextTab = null;
	this.getSessionId = function () {
		return sid;
	}
	this.close = function() {
		if (timeout && timeout != null) {
            window.clearTimeout(timeout);
        }
	}
	this.setShowPrevTab = function (showPrevTabFct){
	    showPrevTab = showPrevTabFct;
    };
    this.setShowNextTab = function (showNextTabFct){
        showNextTab = showNextTabFct;
    };
	function debug(s) {
		sdebug.innerHTML=s;
	}
	function error() {
		sled.className='off';
		debug("Connection lost timeout ts:"+((new Date).getTime()));
	}
	function opt_add(opt,name) {
		opt.className='off';
		opt.innerHTML=' '+name+' ';
		dstat.appendChild(opt);
		dstat.appendChild(document.createTextNode(' '));
	}
	function do_get(event) {
		opt_get.className=(opt_get.className=='off')?'on':'off';
		debug('GET '+opt_get.className);
	}
	function do_color(event) {
		var o=opt_color.className=(opt_color.className=='off')?'on':'off';
		if(o=='on')
			query1=query0+"&c=1&k=";
		else
			query1=query0+"&k=";
		debug('Color '+opt_color.className);
	}
	function is_clipboard_support() {
		if (window.clipboardData) {
			return true;
		} else if(window.netscape) {
			if (is_mozilla_clipboard_support() == '') {
				return true;
			} else {
				return false;
			}
		}
		return false;
	}
	function is_mozilla_clipboard_support(){
		try {
			netscape.security.PrivilegeManager.enablePrivilege("UniversalXPConnect");
		} catch (err) {
			return err + '\nAccess denied, <a href="http://kb.mozillazine.org/Granting_JavaScript_access_to_the_clipboard" target="_blank">more info</a>';
		}
		return '';
	}
	function mozilla_clipboard() {
		 // mozilla sucks
        var err;
		if ((err=is_mozilla_clipboard_support()) != '') {
			debug(err);
			return undefined;
		}
		var clip = Components.classes["@mozilla.org/widget/clipboard;1"].createInstance(Components.interfaces.nsIClipboard);
		var trans = Components.classes["@mozilla.org/widget/transferable;1"].createInstance(Components.interfaces.nsITransferable);
		if (!clip || !trans) {
			return undefined;
		}
		trans.addDataFlavor("text/unicode");
		clip.getData(trans,clip.kGlobalClipboard);
		var str=new Object();
		var strLength=new Object();
		try {
			trans.getTransferData("text/unicode",str,strLength);
		} catch(err) {
			return "";
		}
		if (str) {
			str=str.value.QueryInterface(Components.interfaces.nsISupportsString);
		}
		if (str) {
			return str.data.substring(0,strLength.value / 2);
		} else {
			return "";
		}
	}
	function do_paste(event) {
		var p=undefined;
		if (window.clipboardData) {
			p=window.clipboardData.getData("Text");
		} else if(window.netscape) {
			p=mozilla_clipboard();
		}
		if (p) {
			debug('Pasted');
			queue(encodeURIComponent(p));
		}
	}
	function update() {
//		debug("ts: "+((new Date).getTime())+" rmax:"+rmax);
		if(sending==0) {
			sending=1;
			sled.className='on';
			var r=new XMLHttpRequest();
			var send="";
			while(keybuf.length>0) {
				send+=keybuf.pop();
			}
			var query=query1+send+"&t="+screenTimestamp;
            if (additionalQueryString)  query+='&'+additionalQueryString;
			if(opt_get.className=='on') {
				r.open("GET",endpoint+"?"+query,true);
				if(ie) {
					r.setRequestHeader("If-Modified-Since", "Sat, 1 Jan 2000 00:00:00 GMT");
				}
			} else {
				r.open("POST",endpoint,true);
			}
			r.setRequestHeader('Content-Type','application/x-www-form-urlencoded');
			r.onreadystatechange = function () {
//				debug("xhr:"+((new Date).getTime())+" state:"+r.readyState+" status:"+r.status+" statusText:"+r.statusText);
				if (r.readyState==4) {
					if(r.status==200) {
						window.clearTimeout(error_timeout);
						if(r.responseText.trim()!="<idem/>") {
                            dterm.innerHTML = r.responseText;
							rmax=100;
						} else {
							rmax*=2;
							if(rmax>1000) {
								rmax=1000;
                            }
						}

                        // update cursor position
                        var cxs = r.getResponseHeader("Cursor-X");
                        if(cxs!=null) {
                            var cx = Number(cxs);
                            var cy = Number(r.getResponseHeader("Cursor-Y"));
                            var sx = Number(r.getResponseHeader("Screen-X"));
                            var sy = Number(r.getResponseHeader("Screen-Y"));

                            cursor.style.left=(dterm.offsetWidth*cx/sx)+"px";
                            cursor.style.top=(dterm.offsetHeight*cy/sy)+"px";
                            cursor.style.display="";
                        } else {
                            cursor.style.display="none";
                        }

						sending=0;
						sled.className='off';
						timeout=window.setTimeout(update,rmax);
                        screenTimestamp = r.getResponseHeader("Screen-Timestamp");
					} else {
						debug("Connection error status:"+r.status);
					}
				}
			}
			error_timeout=window.setTimeout(error,5000);
			if(opt_get.className=='on') {
				r.send(null);
			} else {
				r.send(query);
			}
		}
	}
	function queue(s) {
		keybuf.unshift(s);
		if(sending==0) {
			window.clearTimeout(timeout);
			timeout=window.setTimeout(update,1);
		}
	}
    // special keys that don't result in the keypress event.
    // we need to handle these in keydown
    var keyDownKeyCodes = {
        // see http://www.w3.org/TR/DOM-Level-3-Events/#determine-keydown-keyup-keyCode
        // also see http://www.javascripter.net/faq/keycodes.htm
        8:1, // Backspace
        9:1, // TAB
        27:1,   // Escape
        33:1,   // PageUp
        34:1,   // PageDown
        35:1,   // End
        36:1,   // Home
        37:1,   // Left
        38:1,   // Up
        39:1,   // Right
        40:1,   // Down
        45:1,   // Insert
        46:1,   // Del
        112:1, 113:1, 114:1, 115:1, 116:1, 117:1, 118:1, 119:1, 120:1, 121:1, 122:1, 123:1 // F1-F12
    };
	function keydown(ev) {
		if (!ev) {
		    ev=window.event;
        }
        if ((keyDownKeyCodes[ev.keyCode] && ev.charCode==0) || ev.ctrlKey || ev.altKey) {
            // ev.charCode!=0 implies those are keys that produce ASCII codes
            return handleKey(ev,0);
        }
	}
	function keypress(ev) {
        if ((keyDownKeyCodes[ev.keyCode] && ev.charCode==0) || ev.ctrlKey || ev.altKey) {
            // we handled these in keydown
        } else {
            return handleKey(ev,ev.which)
        }
    }

    // which==0 appears to be used as a signel but not sure exactly why --- Kohsuke
    function handleKey(ev,which) {
		if (!ev) {
            ev=window.event;
        }
        console.log ("kp keyCode="+ev.keyCode+" which="+ev.which+" shiftKey="+ev.shiftKey+" ctrlKey="+ev.ctrlKey+" altKey="+ev.altKey);
//		debug(s);
//		return false;
//		else { if (!ev.ctrlKey || ev.keyCode==17) { return; }
		var kc;
		var k="";
		if (ev.keyCode) {
			kc=ev.keyCode;
        }
		if (which) {
            kc = which;
        }
		if (ev.altKey) {
			if (kc>=65 && kc<=90) {
                kc += 32;
            }
			if (kc>=97 && kc<=122) {
				k=String.fromCharCode(27)+String.fromCharCode(kc);
			}
		} else if (ev.ctrlKey) {
			if (kc>=65 && kc<=90) {
                k=String.fromCharCode(kc-64); // Ctrl-A..Z
            } else if (kc>=97 && kc<=122) {
                k=String.fromCharCode(kc-96); // Ctrl-A..Z
            } else if (kc==54)  {
                k=String.fromCharCode(30); // Ctrl-^
            } else if (kc==109) {
                k=String.fromCharCode(31); // Ctrl-_
            } else if (kc==219) {
                k=String.fromCharCode(27); // Ctrl-[
            } else if (kc==220) {
                k=String.fromCharCode(28); // Ctrl-\
            } else if (kc==221) {
                k=String.fromCharCode(29); // Ctrl-]
            }
				/*
			else if (kc==219) k=String.fromCharCode(29); // Ctrl-]
			else if (kc==219) k=String.fromCharCode(0);  // Ctrl-@
			*/

            if (kc==33) {
                k=""; // PgUp
                if (showPrevTab && showPrevTab != null) {
                    showPrevTab();
                }
            }
            else if (kc==34) {
                k=""; // PgDn
                if (showNextTab && showNextTab != null) {
                    showNextTab();
                }
            }

		} else if (which==0) {
			if (kc==9) k=String.fromCharCode(9);  // Tab
			else if (kc==8) k=String.fromCharCode(127);  // Backspace
			else if (kc==27) k=String.fromCharCode(27); // Escape
			else {
				if (kc==33) k="[5~";        // PgUp
				else if (kc==34) k="[6~";   // PgDn
				else if (kc==35) k="[4~";   // End
				else if (kc==36) k="[1~";   // Home
				else if (kc==37) k="[D";    // Left
				else if (kc==38) k="[A";    // Up
				else if (kc==39) k="[C";    // Right
				else if (kc==40) k="[B";    // Down
				else if (kc==45) k="[2~";   // Ins
				else if (kc==46) k="[3~";   // Del
				else if (kc==112) k="[[A";  // F1
				else if (kc==113) k="[[B";  // F2
				else if (kc==114) k="[[C";  // F3
				else if (kc==115) k="[[D";  // F4
				else if (kc==116) k="[[E";  // F5
				else if (kc==117) k="[17~"; // F6
				else if (kc==118) k="[18~"; // F7
				else if (kc==119) k="[19~"; // F8
				else if (kc==120) k="[20~"; // F9
				else if (kc==121) k="[21~"; // F10
				else if (kc==122) k="[23~"; // F11
				else if (kc==123) k="[24~"; // F12
				if (k.length) {
					k=String.fromCharCode(27)+k;
				}
			}
		} else {
			if (kc==8) {
                k = String.fromCharCode(127);  // Backspace
            } else {
                k = String.fromCharCode(kc);
            }
		}
		if(k.length) {
//			queue(encodeURIComponent(k));
			if(k=="+") {
				queue("%2B");
			} else {
				queue(escape(k));
			}
		}
		ev.cancelBubble=true;
		if (ev.stopPropagation) ev.stopPropagation();
		if (ev.preventDefault)  ev.preventDefault();
		return false;
	}
	function init() {
		sled.appendChild(document.createTextNode('\xb7'));
		sled.className='off';
		dstat.appendChild(sled);
		dstat.appendChild(document.createTextNode(' '));
		opt_add(opt_color,'Colors');
		opt_color.className='on';
		opt_add(opt_get,'GET');
		if (is_clipboard_support()) {
			opt_add(opt_paste,'Paste');
		}
		dstat.appendChild(sdebug);
		dstat.className='stat';
        div.appendChild(fitter);
        fitter.className = 'fitter';
        //fitter.appendChild(dstat);
        fitter.appendChild(spacer);
        spacer.className='spacer';
        spacer.appendChild(screen);
        screen.className='screen';
        screen.appendChild(dterm);
		if(opt_color.addEventListener) {
			opt_get.addEventListener('click',do_get,true);
			opt_color.addEventListener('click',do_color,true);
			if (is_clipboard_support()) {
				opt_paste.addEventListener('click',do_paste,true);
			}
		} else {
			opt_get.attachEvent("onclick", do_get);
			opt_color.attachEvent("onclick", do_color);
			if (is_clipboard_support()) {
				opt_paste.attachEvent("onclick", do_paste);
			}
		}
        div.onkeypress=keypress;
        div.onkeydown=keydown;
		timeout=window.setTimeout(update,100);

        cursor.style.position="absolute";
        cursor.style.color="white";
        cursor.style.zIndex="999";
        cursor.innerHTML="_";
        screen.appendChild(cursor);
	}
	init();
};

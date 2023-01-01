/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

let ajaxterm={};

ajaxterm.Terminal = function(id,options) {

    this.id = id;
    const that = this;

    // options
    const width  = options.width || 80; // dimension of the terminal
	const height = options.height || 25;
    const endpoint = options.endpoint;  // URL of the server endpoint that delivers the request to Session.handleUpdate
    const additionalQueryString = options.query; // additional parameters sent to the server

	let ie=0;
	if(window.ActiveXObject)
		ie=1;
	let sid=""+Math.round(Math.random()*1000000000);
	let query0="s="+sid+"&w="+width+"&h="+height;
	let query1=query0+"&c=1&k=";
	let timeout;
	let screenTimestamp = 0;
	let error_timeout;
	let keybuf=[];
	let sending=0;
	let rmax=1;

	let closed = false;

	let systemPasteReady = false;
	let systemPasteContent;
	let cpTextArea;

	let div = (typeof(id)=="string" ? document.getElementById(id) : id);
	let fitter = document.createElement('div');   // for shrinking the screen area to the right size
	let dstat = document.createElement('pre');
	let sled = document.createElement('span');    // status LED. indicate the communication with the server
	let optGet = document.createElement('a');    //
	let optColor = document.createElement('a');
	let sdebug = document.createElement('span');
	let spacer = document.createElement('div');   // creates border & padding around the main screen
	let screen = document.createElement('div'); // holds dterm&cursor. origin of the cursor positioning
	let dterm = document.createElement('div');    // area that shows the screen
	let cursor = document.createElement('div');   // cursor
	let showPrevTab = null;
	let showNextTab = null;

	this.getSessionId = function () {
		return sid;
	};

	this.close = function() {
		if (timeout) {
            window.clearTimeout(timeout);
        }
        $("#" + that.id).find("pre").each(function() {
            $(this).addClass('dead');
        });

        div.onkeypress=null;
        div.onkeydown=null;

        closed = true;
	};

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
		optGet.className=(optGet.className == 'off')?'on':'off';
		debug('GET '+optGet.className);
	}

	function do_color(event) {
		var o=optColor.className=(optColor.className == 'off')?'on':'off';
		if (o == 'on') {
            query1 = query0 + "&c=1&k=";
        } else {
            query1 = query0 + "&k=";
        }
		debug('Color '+optColor.className);
	}

	function update() {
	    if (closed) {
	        return;
        }

//		debug("ts: "+((new Date).getTime())+" rmax:"+rmax);
		console.log("ts: "+((new Date).getTime())+" rmax:"+rmax);
		if(sending==0) {
			sending=1;
			sled.className='on';
			var r = new XMLHttpRequest();
			var send="";
			while(keybuf.length>0) {
				send+=keybuf.pop();
			}
			var query=query1+send+"&t="+screenTimestamp;
            if (additionalQueryString)  query+='&'+additionalQueryString;

			if(optGet.className == 'on') {
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
				console.log("xhr:"+((new Date).getTime())+" state:"+r.readyState+" status:"+r.status+" statusText:"+r.statusText);
				if (r.readyState === 4) {
					if(r.status === 200) {
						window.clearTimeout(error_timeout);
						if(r.responseText.trim() != "<idem/>") {
                            dterm.innerHTML = r.responseText;
							rmax=100;
						} else {
							rmax *= 2;
							if(rmax > 1000) {
								rmax = 1000;
                            }
						}

                        // update cursor position
                        var cxs = r.getResponseHeader("Cursor-X");
                        if (cxs != null) {
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
						console.log("done - "+r.status);
					} else {
						//debug("Connection error status:"+r.status);
						console.log("Connection error status:"+r.status);
					}
				}
			};

			error_timeout=window.setTimeout(error,5000);

			if(optGet.className == 'on') {
				r.send(null);
			} else {
				r.send(query);
			}
		}
	}

	function queue(s) {
        if (closed) {
            return;
        }
		keybuf.unshift(s);
		if (sending === 0) {
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
        if ((keyDownKeyCodes[ev.keyCode] && ev.charCode == 0) || ev.ctrlKey || ev.altKey) {
            // ev.charCode!=0 implies those are keys that produce ASCII codes
            return handleKey(ev,0);
        }
	}

	function keypress(ev) {
        if ((keyDownKeyCodes[ev.keyCode] && ev.charCode == 0) || ev.ctrlKey || ev.altKey) {
            // we handled these in keydown
        } else {
            return handleKey(ev,ev.which)
        }
    }

    // which==0 appears to be used as a signel but not sure exactly why
    function handleKey(ev,which) {
		if (!ev) {
            ev=window.event;
        }
        console.log ("kp keyCode="+ev.keyCode+" which="+which+" shiftKey="+ev.shiftKey+" ctrlKey="+ev.ctrlKey+" altKey="+ev.altKey);
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

			if (ev.shiftKey) {
				if (kc == 37) { // Ctrl+Shift+Left
					k = "";
					if (showPrevTab) {
						showPrevTab();
					}
				}
				else if (kc == 39) { // Ctrl+Shift+Left
                    k = "";
                    if (showNextTab) {
                        showNextTab();
                    }
                } else if (kc == 86) { // Ctrl+Shift+V
					doPaste (ev);
                    return true;
                } else if (kc == 67) { // Ctrl+Shift+C
                    doCopy (ev);
                    ev.cancelBubble=true;
                    if (ev.stopPropagation) ev.stopPropagation();
                    if (ev.preventDefault)  ev.preventDefault();
                    return false;
                }
			}

            if (kc == 86) { // Ctrl+V
                doPaste (ev);
                return true;
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
		if (ev.preventDefault) ev.preventDefault();
		return false;
	}

    function systemPasteListener(evt) {
        //console.debug (systemPasteListener);
        systemPasteContent = evt.clipboardData.getData('text/plain');
        systemPasteReady = true;
    }

    function getSelectionText() {
        var text = "";
        if (window.getSelection) {
            text = window.getSelection().toString();
        } else if (document.selection && document.selection.type != "Control") {
            text = document.selection.createRange().text;
        }
        return text;
    }

    function doCopy(evt) {
        console.debug ("doCopy");

        var target = evt.target;

        // standard way of copying
        var selectedText = getSelectionText();
        console.debug (selectedText);
        cpTextArea.value = selectedText;
        cpTextArea.select();
        document.execCommand('copy');
        setTimeout (function() {
            cpTextArea.value = "";
            div.focus();
        });
    }

    function doPaste(evt) {
        //console.debug ("doPaste");

        systemPasteReady = false;
        cpTextArea.value = "";

	    var target = evt.target;
	    var counter = 0;

        if (window.clipboardData) {
            //target.innerText = window.clipboardData.getData('Text');
            queue(encodeURIComponent(window.clipboardData.getData('Text')));
            return;
        }
        function waitForPaste() {
            //console.debug ("waitForPaste");
            if (counter > 100) {
                console.warn("Could not get paste event within 2 second");
                systemPasteReady = false;
                cpTextArea.value = "";
                return;
            }
            counter++;
            if (!systemPasteReady) {
                setTimeout(waitForPaste, 20);
                return;
            }
            queue(encodeURIComponent(systemPasteContent));
            systemPasteReady = false;
            cpTextArea.value = "";
            div.focus();
        }
        // FireFox requires at least one editable
        // element on the screen for the paste event to fire
        cpTextArea.select();

        waitForPaste();
        //document.execCommand('paste');
    }

	function init() {
		sled.appendChild(document.createTextNode('\xb7'));
		sled.className='off';
		dstat.appendChild(sled);
		dstat.appendChild(document.createTextNode(' '));
		opt_add(optColor,'Colors');
		optColor.className='on';
		//opt_add(optGet,'GET');
		dstat.appendChild(sdebug);
		dstat.className='stat';
        div.appendChild(fitter);
        fitter.className = 'fitter';

        //fitter.appendChild(dstat);

        cpTextArea = document.createElement('textarea');
        cpTextArea.setAttribute('style', 'position: absolute; top: 0px; left: 0px; width:1px; height: 1px; border:0; opacity:0;');
        document.body.appendChild(cpTextArea);

        fitter.appendChild(spacer);
        spacer.className='spacer';
        spacer.appendChild(screen);
        screen.className='screen';
        screen.appendChild(dterm);
		if(optColor.addEventListener) {
			optGet.addEventListener('click',do_get,true);
			optColor.addEventListener('click',do_color,true);
		} else {
			optGet.attachEvent("onclick", do_get);
			optColor.attachEvent("onclick", do_color);
		}
        div.onkeypress=keypress;
        div.onkeydown=keydown;
		timeout=window.setTimeout(update,100);

        window.addEventListener('paste',systemPasteListener);

        cursor.style.position="absolute";
        cursor.style.color="white";
        cursor.style.zIndex="999";
        cursor.innerHTML="_";
        screen.appendChild(cursor);
	}

	init();
};

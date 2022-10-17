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

if (typeof eskimo === "undefined" || eskimo == null) {
    window.eskimo = {}
}
eskimo.Notifications = function() {

    const that = this;

    let lastLineNotifications = 0;
    let notifications = [];
    let newNotificationsCount = 0;

    this.initialize = function () {
        loadLastLine();
    };

    // get last line of notifications
    function loadLastLine() {
        $.ajaxGet({
            url: "get-lastline-notification",
            success: function (data, status, jqXHR) {
                if (data && data.status) {
                    lastLineNotifications = data.lastLine;
                } else {
                    console.error(data);
                }
            },
            error: errorHandler
        });
    }

    function fetchNotifications() {
        $.ajaxGet({
            url: "fetch-notifications?last_line=" + lastLineNotifications,
            success: function (data, status, jqXHR) {

                // OK
                //console.log(data);
                if (data && data.status) {
                    //console.log(data.notifications);

                    for (let i = 0; i < data.notifications.length; i++) {
                        addNotification(data.notifications[i]);
                    }

                    lastLineNotifications = data.lastLine;

                } else {
                    console.error("No data received");
                }
            },
            error: function (jqXHR, status) {
                // error handler
                console.log(jqXHR);
                console.log (status);

                if (jqXHR.status == "401") {
                    window.location = "login.html";
                }

                // Don't alerrt in case of an error here. it spams the browser with spurious alert
                // messages all the time when the backend is down
                //alert('fail : ' + status);
            }
        });
    }
    this.fetchNotifications = fetchNotifications;

    function addNotification(notification) {

        let today = new Date();
        notification.tstamp = today.getHours() + ":" + today.getMinutes() + ":" + today.getSeconds();

        notifications.push(notification);
        newNotificationsCount++;

        renderNotifications();

        $("#new-notifications-count").html("" + newNotificationsCount);
    }
    this.addNotification = addNotification;

    function clearNotifications() {

        $.ajaxGet({
            url: "clear-notifications",
            success: function (data, status, jqXHR) {
                lastLineNotifications = 0;

                notifications = [];
                newNotificationsCount = 0;

                renderNotifications();

                $("#new-notifications-count").html("");
            },
            error: errorHandler
        });
    }

    this.clearNotifications = clearNotifications;

    function renderNotifications() {

        let notificationHTML = '' +
            '<li class="hoe-submenu-label">\n' +
            '    <h3><span id="notifications-count" class="bold">' +
            notifications.length +
            '    </span> Notification(s) ' +
            '    <a id="notifications-clear" href="#">' +
            '        <span class="notifications-clear-link">Clear</span>' +
            '    </a></h3>\n' +
            '</li>';

        let start = 0;
        if (notifications.length > 10) {
            start = notifications.length - 10;
            notificationHTML = notificationHTML + '' +
                '<li>\n' +
                '    <a href="#" class="clearfix">\n' +
                '        <span class="notification-title">...</span>\n' +
                '    </a>\n' +
                '</li>';
        }


        for (let i = start; i < notifications.length; i++) {

            let notification = notifications[i];

            if (notification.type == "error" || notification.type == "Error") {

                notificationHTML = notificationHTML +
                    '<li>\n' +
                    '    <a href="#" class="clearfix">\n' +
                    '        <i class="fa fa-exclamation-triangle red-text"></i>\n' +
                    '        <span class="notification-title">Error - received from backend</span>\n' +
                    '        <span class="notification-ago">' + notification.tstamp + '</span>\n' +
                    '        <p class="notification-message">' + notification.message + '</p>\n' +
                    '    </a>\n' +
                    '</li>';

            } else if (notification.type == "doing" || notification.type == "Doing") {

                notificationHTML = notificationHTML +
                    '<li>\n' +
                    '    <a href="#" class="clearfix">\n' +
                    '        <i class="fa fa-exchange green-text"></i>\n' +
                    '        <span class="notification-title">In progress...</span>\n' +
                    '        <span class="notification-ago">' + notification.tstamp + '</span>\n' +
                    '        <p class="notification-message">' + notification.message + '</p>\n' +
                    '        <p class="notification-message">\n' +
                    '            <div class="progress">\n' +
                    '                <div class="progress-bar progress-bar-striped active" role="progressbar"\n' +
                    '                     aria-valuenow="40" aria-valuemin="0" aria-valuemax="100" style="width:60%;"> 60%\n' +
                    '                </div>\n' +
                    '            </div>\n' +
                    '        </p>\n' +
                    '    </a>\n' +
                    '</li>';

            } else if (notification.type == "info" || notification.type == "Info") {

                notificationHTML = notificationHTML +
                    '<li>\n' +
                    '    <a href="#" class="clearfix">\n' +
                    '        <i class="fa fa-cogs green-text"></i>\n' +
                    '        <span class="notification-title">Information</span>\n' +
                    '        <span class="notification-ago">' + notification.tstamp + '</span>\n' +
                    '        <p class="notification-message">' + notification.message + '</p>\n' +
                    '    </a>\n' +
                    '</li>';

            } else {

                notificationHTML = notificationHTML +
                    '<li>\n' +
                    '    <a href="#" class="clearfix">\n' +
                    '        <i class="fa fa-exchange green-text"></i>\n' +
                    '        <span class="notification-title">Message</span>\n' +
                    '        <span class="notification-ago">' + notification.tstamp + '</span>\n' +
                    '        <p class="notification-message">' + notification.message + '</p>\n' +
                    '    </a>\n' +
                    '</li>';
            }
        }

        $("#notifications-container").html(notificationHTML);

        $("#notifications-clear").click(function () {
            clearNotifications();
        });

    }

    function notificationsShown() {

        newNotificationsCount = 0;
        $("#new-notifications-count").html("");
    }
    this.notificationsShown = notificationsShown;

};

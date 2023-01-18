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

if (typeof eskimo === "undefined" || eskimo == null) {
    window.eskimo = {}
}
eskimo.Notifications = function() {

    const that = this;

    const MAX_NOTIFICATION_DISPLAY = 6;

    let lastLineNotifications = 0;
    let notifications = [];
    let newNotificationsCount = 0;

    this.initialize = function () {

        // Initialize HTML Div from Template
        $("#notification-placeholder").load("html/eskimoNotifications.html", (responseTxt, statusTxt, jqXHR) => {

            if (statusTxt === "success") {

                $("#notifications-clear").click(function () {
                    clearNotifications();
                });

                $("#show-notifications-link").click(notificationsShown);

                loadLastLine();

            } else if (statusTxt === "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });
    };

    // get last line of notifications
    function loadLastLine() {
        $.ajaxGet({
            url: "get-lastline-notification",
            success: (data, status, jqXHR) => {
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
            success: (data, status, jqXHR) => {

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
            error: (jqXHR, status) => {
                // error handler
                console.log(jqXHR);
                console.log (status);

                if (jqXHR.status == "401") {
                    window.location = "login.html";
                }

                // Don't alert in case of an error here. it spams the browser with spurious alert
                // messages all the time when the backend is down
                //alert('fail : ' + status);
            }
        });
    }
    this.fetchNotifications = fetchNotifications;

    function renderTimestamp (timestamp) {
        const date = new Date (parseInt (timestamp));

        const year    = date.getFullYear();
        const month   = date.getMonth() + 1;
        const day     = date.getDate();
        const hour    = date.getHours();
        const minute  = date.getMinutes();
        const seconds = date.getSeconds();

        return "" + year + "-" + month + "-" + day + " " + hour + ":" + minute + ":" + seconds;
    }
    this.renderTimestamp = renderTimestamp;

    function addNotification(notification) {

        notification.tstamp = renderTimestamp (notification.timestamp);

        notifications.push(notification);

        renderNotifications();

        newNotificationsCount++;
        const $newNotificationsCount = $("#new-notifications-count");
        $.showElement($newNotificationsCount);
        $newNotificationsCount.html("" + newNotificationsCount);
    }
    this.addNotification = addNotification;

    function clearNotifications() {

        $.ajaxGet({
            url: "clear-notifications",
            success: (data, status, jqXHR) => {
                lastLineNotifications = 0;

                notifications = [];
                newNotificationsCount = 0;

                renderNotifications();

                const $newNotificationsCount = $("#new-notifications-count");
                $.hideElement($newNotificationsCount);
                $newNotificationsCount.html("");
            },
            error: errorHandler
        });
    }

    this.clearNotifications = clearNotifications;

    function renderNotifications() {

        let notificationHTML = '';

        let start = 0;

        if (notifications.length <= 0) {
            notificationHTML = notificationHTML +
                '<h5 class="text-muted font-13 fw-normal mt-2">(Empty)</h5>';

        } else if (notifications.length > MAX_NOTIFICATION_DISPLAY) {
            start = notifications.length - MAX_NOTIFICATION_DISPLAY;
            notificationHTML = notificationHTML + '' +
                '<li>\n' +
                '    <a href="#" class="clearfix">\n' +
                '        <span class="notification-title">...</span>\n' +
                '    </a>\n' +
                '</li>';
        }

        for (let i = start; i < notifications.length; i++) {

            let notification = notifications[i];

            notificationHTML = notificationHTML +
                '<a href="javascript:void(0);" class="dropdown-item p-0 notify-item card unread-noti shadow-none mb-2">\n' +
                '    <div class="card-body">\n' +
                '        <div class="d-flex align-items-center">\n' +
                '            <div class="flex-shrink-0">\n';

            if (notification.type == "error" || notification.type == "Error") {

                notificationHTML = notificationHTML +
                    '                <div class="notify-icon bg-danger">\n' +
                    '                    <i class="fa fa-exclamation-triangle"></i>\n' +
                    '                </div>\n';

            } else if (notification.type == "doing" || notification.type == "Doing") {

                notificationHTML = notificationHTML +
                    '                <div class="notify-icon bg-info">\n' +
                '                    <i class="fa fa-exchange"></i>\n' +
                '                </div>\n';

            } else if (notification.type == "info" || notification.type == "Info") {

                notificationHTML = notificationHTML +
                    '                <div class="notify-icon bg-primary">\n' +
                '                    <i class="fa fa-cogs"></i>\n' +
                '                </div>\n';

            } else {

                notificationHTML = notificationHTML +
                    '                <div class="notify-icon bg-primary">\n' +
                '                    <i class="fa fa-exchange"></i>\n' +
                '                </div>\n';

            }

            notificationHTML = notificationHTML +
                '            </div>\n' +
                '            <div class="flex-grow-1 text-truncate ms-2">\n';


            if (notification.type == "error" || notification.type == "Error") {

                notificationHTML = notificationHTML +
                    '                <h5 class="noti-item-title fw-semibold font-14">Error <small class="fw-normal text-muted ms-1">' + notification.tstamp + '</small></h5>\n' +
                    '                <small class="noti-item-subtitle text-muted">' + notification.message + '</small>\n';

            } else if (notification.type == "doing" || notification.type == "Doing") {

                notificationHTML = notificationHTML +
                    '                <h5 class="noti-item-title fw-semibold font-14">In progress... <small class="fw-normal text-muted ms-1">' + notification.tstamp + '</small></h5>\n' +
                    '                <small class="noti-item-subtitle text-muted">' + notification.message +
                    '                    <div class="progress">\n' +
                    '                        <div class="progress-bar progress-bar-striped active" role="progressbar"\n' +
                    '                             aria-valuenow="40" aria-valuemin="0" aria-valuemax="100" style="width:60%;"> 60%\n' +
                    '                        </div>\n' +
                    '                    </div>\n' +
                    '                </small>\n';

            } else if (notification.type == "info" || notification.type == "Info") {

                notificationHTML = notificationHTML +
                    '                <h5 class="noti-item-title fw-semibold font-14">Information <small class="fw-normal text-muted ms-1">' + notification.tstamp + '</small></h5>\n' +
                    '                <small class="noti-item-subtitle text-muted">' + notification.message + '</small>\n';

            } else {

                notificationHTML = notificationHTML +
                    '                <h5 class="noti-item-title fw-semibold font-14">Message <small class="fw-normal text-muted ms-1">' + notification.tstamp + '</small></h5>\n' +
                    '                <small class="noti-item-subtitle text-muted">' + notification.message + '</small>\n';

            }

            notificationHTML = notificationHTML +
                '            </div>\n' +
                '        </div>\n' +
                '    </div>\n' +
                '</a>'
        }

        $("#notifications-container").html(notificationHTML);

    }

    function notificationsShown() {

        newNotificationsCount = 0;

        const $newNotificationsCount = $("#new-notifications-count");
        $.hideElement($newNotificationsCount);
        $newNotificationsCount.html("");
    }
    this.notificationsShown = notificationsShown;

};

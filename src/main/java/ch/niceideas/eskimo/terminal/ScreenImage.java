/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.terminal;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;

/**
 * State of the virtual screen held by the terminal.
 *
 */
public final class ScreenImage implements Serializable {
    /**
     * HTML dump of the screen image.
     */
    public final String screen;
    /**
     * Represents the timestamp of the screen image.
     *
     * This value should be passed to the client, and it should then send it back
     * for the next invocation of {@link Terminal#dumpHtml(boolean, int)} for
     * up-to-date check of the screen image.
     */
    public final int timestamp;

    public final int cursorX;
    public final int cursorY;
    public final int screenX;
    public final int screenY;

    ScreenImage(int timestamp, String screen, Terminal t) {
        this.timestamp = timestamp;
        this.screen = screen;
        if (t.isCursorShown()) {
            this.cursorX = t.getCx();
            this.cursorY = t.getCy();
        } else {
            this.cursorX = this.cursorY = -1;
        }
        this.screenX = t.width;
        this.screenY = t.height;
    }

    public String renderResponse(HttpServletResponse resp) {
        resp.setContentType("application/xml;charset=UTF-8");
        if (cursorX != -1 || cursorY != -1) {
            resp.addHeader("Cursor-X", String.valueOf(cursorX));
            resp.addHeader("Cursor-Y", String.valueOf(cursorY));
        }
        resp.addHeader("Screen-X", String.valueOf(screenX));
        resp.addHeader("Screen-Y", String.valueOf(screenY));
        resp.addHeader("Screen-Timestamp", String.valueOf(timestamp));
        return screen;
    }

    private static final long serialVersionUID = 1L;
}

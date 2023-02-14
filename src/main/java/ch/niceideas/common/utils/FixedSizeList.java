/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.common.utils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class FixedSizeList<T> extends AbstractWrappingList<T> implements List<T> {

    private final int maxSize;
    private List<T> buffer = new CopyOnWriteArrayList<>();

    public FixedSizeList(int maxSize) {
        setUnderlying (buffer);
        this.maxSize = maxSize;
    }

    @Override
    public boolean add(T t) {
        boolean retValue = buffer.add(t);
        resize();
        return retValue;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        boolean retValue = buffer.addAll(c);
        resize();
        return retValue;
    }

    void resize() {
        int diff = buffer.size() - maxSize;
        if (diff > 0) {
            buffer = buffer.subList(diff, buffer.size());
            setUnderlying (buffer);
        }
    }

    @Override
    public void add(int index, T element) {
        buffer.add (index, element);
        resize();
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        boolean retValue = buffer.addAll(index, c);
        resize();
        return retValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FixedSizeList)) return false;
        if (!super.equals(o)) return false;
        FixedSizeList<?> that = (FixedSizeList<?>) o;
        // buffer is already accounted by parent equals
        return maxSize == that.maxSize;
    }

    @Override
    public int hashCode() {
        // buffer is already accounted by parent hashcode
        return Objects.hash(super.hashCode(), maxSize);
    }

}

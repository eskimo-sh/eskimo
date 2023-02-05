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

import java.util.*;

abstract class AbstractWrappingList <T> implements List<T> {

    private List<T> underlying;

    void setUnderlying (List<T> underlying) {
        this.underlying = underlying;
    }

    @Override
    public final Iterator<T> iterator() {
        return underlying.iterator();
    }

    @Override
    public final Object[] toArray() {
        return underlying.toArray();
    }

    @Override
    public final <T1> T1[] toArray(T1[] a) {
        return underlying.toArray(a);
    }

    @Override
    public final int indexOf(Object o) {
        return underlying.indexOf(o);
    }

    @Override
    public final int lastIndexOf(Object o) {
        return underlying.lastIndexOf(o);
    }

    @Override
    public final ListIterator<T> listIterator() {
        return underlying.listIterator();
    }

    @Override
    public final ListIterator<T> listIterator(int index) {
        return underlying.listIterator(index);
    }

    @Override
    public final boolean contains(Object o) {
        return underlying.contains(o);
    }

    @Override
    public final boolean removeAll(Collection<?> c) {
        return underlying.removeAll(c);
    }

    @Override
    public final boolean retainAll(Collection<?> c) {
        return underlying.retainAll(c);
    }

    @Override
    public final boolean containsAll(Collection<?> c) {
        return underlying.containsAll(c);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof List))
            return false;

        ListIterator<T> e1 = listIterator();
        ListIterator<?> e2 = ((List<?>) o).listIterator();
        while (e1.hasNext() && e2.hasNext()) {
            if (!(Objects.equals(e1.next(), e2.next())))
                return false;
        }
        return !(e1.hasNext() || e2.hasNext());
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        for (T e : this)
            hashCode = 31*hashCode + (e==null ? 0 : e.hashCode());
        return hashCode;
    }

    @Override
    public void clear() {
        underlying.clear();
    }

    @Override
    public T get(int index) {
        return underlying.get(index);
    }

    @Override
    public T set(int index, T element) {
        return underlying.set (index, element);
    }

    @Override
    public T remove(int index) {
        return underlying.remove(index);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return underlying.subList(fromIndex, toIndex);
    }

    @Override
    public int size() {
        return underlying.size();
    }

    @Override
    public boolean isEmpty() {
        return underlying.isEmpty();
    }

    @Override
    public boolean remove(Object o) {
        return underlying.remove(o);
    }
}

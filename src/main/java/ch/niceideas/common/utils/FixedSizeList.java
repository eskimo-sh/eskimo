/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

public class FixedSizeList<T> implements List<T> {

    private final int maxSize;
    private final LinkedList<T> buffer = new LinkedList<>();

    public FixedSizeList(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public boolean add(T t) {
        boolean retValue = buffer.add(t);
        resize();
        return retValue;
    }

    @Override
    public boolean remove(Object o) {
        return buffer.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return buffer.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        boolean retValue = buffer.addAll(c);
        resize();
        return retValue;
    }

    void resize() {
        while (buffer.size() > maxSize) {
            buffer.removeFirst();
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return buffer.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return buffer.retainAll(c);
    }

    @Override
    public void clear() {
        buffer.clear();
    }


    @Override
    public T get(int index) {
        return buffer.get(index);
    }

    @Override
    public T set(int index, T element) {
        return buffer.set (index, element);
    }

    @Override
    public void add(int index, T element) {
        buffer.add (index, element);
        resize();
    }

    @Override
    public T remove(int index) {
        return buffer.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return buffer.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return buffer.lastIndexOf(o);
    }

    @Override
    public ListIterator<T> listIterator() {
        return buffer.listIterator();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return buffer.listIterator(index);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return buffer.subList(fromIndex, toIndex);
    }

    @Override
    public int size() {
        return buffer.size();
    }

    @Override
    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return buffer.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return buffer.iterator();
    }

    @Override
    public Object[] toArray() {
        return buffer.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return buffer.toArray(a);
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        boolean retValue = buffer.addAll(index, c);
        resize();
        return retValue;
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
            T o1 = e1.next();
            Object o2 = e2.next();
            if (!(o1==null ? o2==null : o1.equals(o2)))
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
}

package ch.niceideas.common.utils;

import javax.validation.constraints.NotNull;
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
    public final <T1> T1[] toArray(@NotNull T1[] a) {
        return underlying.<T1>toArray(a);
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

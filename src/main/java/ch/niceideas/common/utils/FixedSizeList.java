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

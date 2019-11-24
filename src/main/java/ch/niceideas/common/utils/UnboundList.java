package ch.niceideas.common.utils;

import org.apache.log4j.Logger;

import java.util.*;

public class UnboundList<T> implements List<T> {

    private static final Logger logger = Logger.getLogger(UnboundList.class);

    private final int maxSize;

    private final FixedSizeList<T> buffer;

    private int virtualSize = 0;

    public UnboundList(int maxSize) {
        this.maxSize = maxSize;
        buffer = new FixedSizeList<>(maxSize);
    }

    @Override
    public boolean add(T t) {
        boolean retValue = buffer.add(t);
        virtualSize++;
        return retValue;
    }

    @Override
    public T get(int index) {
        int position = adaptIndex(index);
        return buffer.get(position);
    }

    int adaptIndex(int index) {
        if (virtualSize <= maxSize) {
            return index;
        }
        if (index >= virtualSize) {
            throw new IndexOutOfBoundsException(index + " is beyond last element index " + (virtualSize - 1));
        }
        int offsetToEnd = getOffsetToEnd(index);
        return getPosition(offsetToEnd);
    }

    int getPosition(int offsetToEnd) {
        return (maxSize - 1) - offsetToEnd;
    }

    int getOffsetToEnd(int index) {
        int offsetToEnd = (virtualSize - 1) - index;
        if (offsetToEnd >= maxSize) {
            logger.warn ("Asked for a position before first element (-"+offsetToEnd+"), returning 0");
            return maxSize - 1;
        }
        return offsetToEnd;
    }

    @Override
    public boolean remove(Object o) {
        boolean found = buffer.remove(o);
        if (found) {
            virtualSize--;
        }
        return found;
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {

        if (fromIndex < 0)
            throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
        if (toIndex > virtualSize)
            throw new IndexOutOfBoundsException("toIndex = " + toIndex);
        if (fromIndex > toIndex)
            throw new IllegalArgumentException("fromIndex(" + fromIndex +
                    ") > toIndex(" + toIndex + ")");

        int fromPosition = adaptIndex(fromIndex);
        int toPosition = adaptIndex(toIndex - 1) + 1;

        return buffer.subList(fromPosition, toPosition);
    }

    @Override
    public int size() {
        return virtualSize;
    }

    @Override
    public boolean isEmpty() {
        return virtualSize == 0;
    }

    @Override
    public boolean contains(Object o) {
        return buffer.contains(o);
    }

    @Override
    public void clear() {
        buffer.clear();
        virtualSize = 0;
    }

    @Override
    public T remove(int index) {
        int position = adaptIndex(index);
        T retValue = buffer.remove(position);
        virtualSize--;
        return retValue;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        boolean retValue = buffer.addAll(c);
        virtualSize += c.size();
        return retValue;
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

    // unimplemented mehtod

    @Override
    public T set(int index, T element) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    @Override
    public void add(int index, T element) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    @Override
    public int indexOf(Object o) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    @Override
    public ListIterator<T> listIterator() {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

}

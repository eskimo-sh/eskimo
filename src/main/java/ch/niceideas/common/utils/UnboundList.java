package ch.niceideas.common.utils;

import org.apache.log4j.Logger;

import java.util.*;

public class UnboundList<T> extends AbstractWrappingList<T> implements List<T> {

    private static final Logger logger = Logger.getLogger(UnboundList.class);

    private static final String ERROR_TO_BE_IMPLEMENTED = "To Be Implemented";

    private final int maxSize;

    private final FixedSizeList<T> buffer;

    private int virtualSize = 0;

    public UnboundList(int maxSize) {
        this.maxSize = maxSize;
        buffer = new FixedSizeList<>(maxSize);
        setUnderlying (buffer);
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
        if (fromIndex > virtualSize)
            throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
        if (toIndex > virtualSize)
            throw new IndexOutOfBoundsException("toIndex = " + toIndex);
        if (fromIndex > toIndex)
            throw new IllegalArgumentException("fromIndex(" + fromIndex +
                    ") > toIndex(" + toIndex + ")");

        if (fromIndex == virtualSize) {
            return Collections.emptyList();
        }

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

    // unimplemented mehtod

    @Override
    public T set(int index, T element) {
        throw new UnsupportedOperationException(ERROR_TO_BE_IMPLEMENTED);
    }

    @Override
    public void add(int index, T element) {
        throw new UnsupportedOperationException(ERROR_TO_BE_IMPLEMENTED);
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        throw new UnsupportedOperationException(ERROR_TO_BE_IMPLEMENTED);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnboundList)) return false;
        if (!super.equals(o)) return false;
        UnboundList<?> that = (UnboundList<?>) o;
        // buffer is already accounted by parent equals
        return maxSize == that.maxSize &&
                virtualSize == that.virtualSize;
    }

    @Override
    public int hashCode() {
        // buffer is already accounted by parent hashCode
        return Objects.hash(super.hashCode(), maxSize, virtualSize);
    }
}

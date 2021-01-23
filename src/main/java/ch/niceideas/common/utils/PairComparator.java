package ch.niceideas.common.utils;

import java.util.Comparator;

public class PairComparator<K extends Comparable<K>, V extends Comparable<V> > implements Comparator<Pair<K, V>> {

    @Override
    public int compare(Pair<K, V> o1, Pair<K, V> o2) {
        if (o1 == null) {
            return -1;
        }
        if (o2 == null) {
            return 1;
        }
        if (o1.getKey().equals(o2.getKey())) {
            return o1.getValue().compareTo(o2.getValue());
        }
        return o1.getKey().compareTo(o2.getKey());
    }

}

package ch.niceideas.common.utils;

import java.io.Serializable;

public class SerializablePair<K extends Serializable, V extends Serializable> extends Pair<K, V> {

    public SerializablePair(K key, V value) {
        super (key, value);
    }

}

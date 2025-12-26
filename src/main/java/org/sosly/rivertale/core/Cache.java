package org.sosly.rivertale.core;

public interface Cache<V> {
    V getOrCompute(int x, int z);

    void clear();
}

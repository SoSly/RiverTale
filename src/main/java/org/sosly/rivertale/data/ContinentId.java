package org.sosly.rivertale.data;

/**
 * Immutable identifier for a continent based on its boundaries.
 */
public class ContinentId {
    private final long id;

    public ContinentId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ContinentId)) {
            return false;
        }
        ContinentId other = (ContinentId) obj;
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Continent#" + id;
    }
}


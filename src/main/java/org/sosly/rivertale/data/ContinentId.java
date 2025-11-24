package org.sosly.rivertale.data;

/**
 * Immutable identifier for a continent based on its center point.
 */
public class ContinentId {
    public static final ContinentId OCEAN = new ContinentId(Long.MIN_VALUE);

    private final long id;

    public ContinentId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public boolean isOcean() {
        return this.id == Long.MIN_VALUE;
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


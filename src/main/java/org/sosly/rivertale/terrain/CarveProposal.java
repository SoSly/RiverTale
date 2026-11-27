package org.sosly.rivertale.terrain;

/** A proposed bed height and its influence at one world-space column. */
public record CarveProposal(int x, int z, double bedY, double influence, boolean bedCeiling) {
    public CarveProposal(int x, int z, double bedY, double influence) {
        this(x, z, bedY, influence, false);
    }

    public CarveProposal asBedCeiling() {
        return new CarveProposal(x, z, bedY, influence, true);
    }
}

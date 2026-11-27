package org.sosly.rivertale.terrain;

/** A proposed bed height and its influence at one world-space column. */
public record CarveProposal(int x, int z, double bedY, double influence) {
}

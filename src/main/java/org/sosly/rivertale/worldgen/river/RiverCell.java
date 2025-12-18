package org.sosly.rivertale.worldgen.river;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RiverCell {

    private final RiverCellKey key;
    private final double density;
    private final double[][] subcellDensities;
    private final CellClassification classification;
    private final boolean participating;

    private FlowDirection primaryOutput;
    private Set<FlowDirection> secondaryOutputs;
    private int distanceToTerminus;
    private boolean isBasin;
    private List<int[]> riverPath;

    public RiverCell(RiverCellKey key, double density, double[][] subcellDensities,
                     CellClassification classification, boolean participating) {
        this.key = key;
        this.density = density;
        this.subcellDensities = subcellDensities;
        this.classification = classification;
        this.participating = participating;
        this.primaryOutput = FlowDirection.NONE;
        this.secondaryOutputs = new HashSet<>();
        this.distanceToTerminus = -1;
        this.isBasin = false;
        this.riverPath = null;
    }

    public RiverCellKey getKey() {
        return key;
    }

    public double getDensity() {
        return density;
    }

    public double[][] getSubcellDensities() {
        return subcellDensities;
    }

    public CellClassification getClassification() {
        return classification;
    }

    public boolean isParticipating() {
        return participating;
    }

    public FlowDirection getPrimaryOutput() {
        return primaryOutput;
    }

    public void setPrimaryOutput(FlowDirection primaryOutput) {
        this.primaryOutput = primaryOutput;
    }

    public Set<FlowDirection> getSecondaryOutputs() {
        return secondaryOutputs;
    }

    public void setSecondaryOutputs(Set<FlowDirection> secondaryOutputs) {
        this.secondaryOutputs = secondaryOutputs;
    }

    public int getDistanceToTerminus() {
        return distanceToTerminus;
    }

    public void setDistanceToTerminus(int distanceToTerminus) {
        this.distanceToTerminus = distanceToTerminus;
    }

    public boolean isBasin() {
        return isBasin;
    }

    public void setBasin(boolean basin) {
        this.isBasin = basin;
    }

    public List<int[]> getRiverPath() {
        return riverPath;
    }

    public void setRiverPath(List<int[]> riverPath) {
        this.riverPath = riverPath;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putInt("cellX", key.cellX());
        tag.putInt("cellZ", key.cellZ());

        tag.putDouble("density", density);

        double[] flatDensities = new double[49];
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                flatDensities[i * 7 + j] = subcellDensities[i][j];
            }
        }
        tag.putIntArray("subcellDensities", convertDoubleArrayToIntArray(flatDensities));

        tag.putString("classification", classification.name());
        tag.putBoolean("participating", participating);
        tag.putString("primaryOutput", primaryOutput.name());

        ListTag secondaryList = new ListTag();
        for (FlowDirection direction : secondaryOutputs) {
            CompoundTag dirTag = new CompoundTag();
            dirTag.putString("direction", direction.name());
            secondaryList.add(dirTag);
        }
        tag.put("secondaryOutputs", secondaryList);

        tag.putInt("distanceToTerminus", distanceToTerminus);
        tag.putBoolean("isBasin", isBasin);

        if (riverPath != null) {
            int[] flatPath = new int[riverPath.size() * 2];
            for (int i = 0; i < riverPath.size(); i++) {
                flatPath[i * 2] = riverPath.get(i)[0];
                flatPath[i * 2 + 1] = riverPath.get(i)[1];
            }
            tag.putIntArray("riverPath", flatPath);
        }

        return tag;
    }

    public static RiverCell load(CompoundTag tag) {
        int cellX = tag.getInt("cellX");
        int cellZ = tag.getInt("cellZ");
        RiverCellKey key = new RiverCellKey(cellX, cellZ);

        double density = tag.getDouble("density");

        int[] flatDensitiesInt = tag.getIntArray("subcellDensities");
        double[] flatDensities = convertIntArrayToDoubleArray(flatDensitiesInt);
        double[][] subcellDensities = new double[7][7];
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                subcellDensities[i][j] = flatDensities[i * 7 + j];
            }
        }

        CellClassification classification = CellClassification.valueOf(tag.getString("classification"));
        boolean participating = tag.getBoolean("participating");

        RiverCell cell = new RiverCell(key, density, subcellDensities, classification, participating);

        cell.setPrimaryOutput(FlowDirection.valueOf(tag.getString("primaryOutput")));

        Set<FlowDirection> secondaryOutputs = new HashSet<>();
        ListTag secondaryList = tag.getList("secondaryOutputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < secondaryList.size(); i++) {
            CompoundTag dirTag = secondaryList.getCompound(i);
            secondaryOutputs.add(FlowDirection.valueOf(dirTag.getString("direction")));
        }
        cell.setSecondaryOutputs(secondaryOutputs);

        cell.setDistanceToTerminus(tag.getInt("distanceToTerminus"));
        cell.setBasin(tag.getBoolean("isBasin"));

        if (tag.contains("riverPath")) {
            int[] flatPath = tag.getIntArray("riverPath");
            List<int[]> riverPath = new ArrayList<>();
            for (int i = 0; i < flatPath.length; i += 2) {
                riverPath.add(new int[]{flatPath[i], flatPath[i + 1]});
            }
            cell.setRiverPath(riverPath);
        }

        return cell;
    }

    private static int[] convertDoubleArrayToIntArray(double[] doubles) {
        int[] ints = new int[doubles.length * 2];
        for (int i = 0; i < doubles.length; i++) {
            long bits = Double.doubleToRawLongBits(doubles[i]);
            ints[i * 2] = (int) (bits >>> 32);
            ints[i * 2 + 1] = (int) bits;
        }
        return ints;
    }

    private static double[] convertIntArrayToDoubleArray(int[] ints) {
        double[] doubles = new double[ints.length / 2];
        for (int i = 0; i < doubles.length; i++) {
            long bits = ((long) ints[i * 2] << 32) | (ints[i * 2 + 1] & 0xFFFFFFFFL);
            doubles[i] = Double.longBitsToDouble(bits);
        }
        return doubles;
    }
}

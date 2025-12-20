package org.sosly.rivertale.worldgen.river;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RiverCell {

    private final RiverCellKey key;
    private final double density;
    private final double[][] subcellDensities;
    private final CellClassification classification;
    private final boolean participating;

    private PathDirection primaryOutput;
    private Set<PathDirection> secondaryOutputs;
    private int distanceToTerminus;
    private boolean isBasin;
    private Map<PathDirection, List<int[]>> riverPaths;

    public RiverCell(RiverCellKey key, double density, double[][] subcellDensities,
                     CellClassification classification, boolean participating) {
        this.key = key;
        this.density = density;
        this.subcellDensities = subcellDensities;
        this.classification = classification;
        this.participating = participating;
        this.primaryOutput = PathDirection.NONE;
        this.secondaryOutputs = new HashSet<>();
        this.distanceToTerminus = -1;
        this.isBasin = false;
        this.riverPaths = new HashMap<>();
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

    public PathDirection getPrimaryOutput() {
        return primaryOutput;
    }

    public void setPrimaryOutput(PathDirection primaryOutput) {
        this.primaryOutput = primaryOutput;
    }

    public Set<PathDirection> getSecondaryOutputs() {
        return secondaryOutputs;
    }

    public void setSecondaryOutputs(Set<PathDirection> secondaryOutputs) {
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

    public Map<PathDirection, List<int[]>> getRiverPaths() {
        return riverPaths;
    }

    public void setRiverPaths(Map<PathDirection, List<int[]>> riverPaths) {
        this.riverPaths = riverPaths;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putInt("cellX", key.cellX());
        tag.putInt("cellZ", key.cellZ());

        tag.putDouble("density", density);

        double[] flatDensities = new double[64];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                flatDensities[i * 8 + j] = subcellDensities[i][j];
            }
        }
        tag.putIntArray("subcellDensities", convertDoubleArrayToIntArray(flatDensities));

        tag.putString("classification", classification.name());
        tag.putBoolean("participating", participating);
        tag.putString("primaryOutput", primaryOutput.name());

        ListTag secondaryList = new ListTag();
        for (PathDirection direction : secondaryOutputs) {
            CompoundTag dirTag = new CompoundTag();
            dirTag.putString("direction", direction.name());
            secondaryList.add(dirTag);
        }
        tag.put("secondaryOutputs", secondaryList);

        tag.putInt("distanceToTerminus", distanceToTerminus);
        tag.putBoolean("isBasin", isBasin);

        if (!riverPaths.isEmpty()) {
            ListTag pathsListTag = new ListTag();
            for (Map.Entry<PathDirection, List<int[]>> entry : riverPaths.entrySet()) {
                CompoundTag pathEntryTag = new CompoundTag();
                pathEntryTag.putString("direction", entry.getKey().name());

                List<int[]> path = entry.getValue();
                int[] flatPath = new int[path.size() * 2];
                for (int i = 0; i < path.size(); i++) {
                    flatPath[i * 2] = path.get(i)[0];
                    flatPath[i * 2 + 1] = path.get(i)[1];
                }
                pathEntryTag.putIntArray("path", flatPath);
                pathsListTag.add(pathEntryTag);
            }
            tag.put("riverPaths", pathsListTag);
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
        double[][] subcellDensities = new double[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                subcellDensities[i][j] = flatDensities[i * 8 + j];
            }
        }

        CellClassification classification = CellClassification.valueOf(tag.getString("classification"));
        boolean participating = tag.getBoolean("participating");

        RiverCell cell = new RiverCell(key, density, subcellDensities, classification, participating);

        cell.setPrimaryOutput(PathDirection.valueOf(tag.getString("primaryOutput")));

        Set<PathDirection> secondaryOutputs = new HashSet<>();
        ListTag secondaryList = tag.getList("secondaryOutputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < secondaryList.size(); i++) {
            CompoundTag dirTag = secondaryList.getCompound(i);
            secondaryOutputs.add(PathDirection.valueOf(dirTag.getString("direction")));
        }
        cell.setSecondaryOutputs(secondaryOutputs);

        cell.setDistanceToTerminus(tag.getInt("distanceToTerminus"));
        cell.setBasin(tag.getBoolean("isBasin"));

        if (tag.contains("riverPaths")) {
            Map<PathDirection, List<int[]>> riverPaths = new HashMap<>();
            ListTag pathsListTag = tag.getList("riverPaths", Tag.TAG_COMPOUND);
            for (int i = 0; i < pathsListTag.size(); i++) {
                CompoundTag pathEntryTag = pathsListTag.getCompound(i);
                PathDirection direction = PathDirection.valueOf(pathEntryTag.getString("direction"));

                int[] flatPath = pathEntryTag.getIntArray("path");
                List<int[]> path = new ArrayList<>();
                for (int j = 0; j < flatPath.length; j += 2) {
                    path.add(new int[]{flatPath[j], flatPath[j + 1]});
                }
                riverPaths.put(direction, path);
            }
            cell.setRiverPaths(riverPaths);
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

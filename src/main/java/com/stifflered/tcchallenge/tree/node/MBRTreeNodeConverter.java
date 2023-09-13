package com.stifflered.tcchallenge.tree.node;

import org.khelekore.prtree.MBRConverter;

public class MBRTreeNodeConverter implements MBRConverter<TreeNode> {

    public static final MBRTreeNodeConverter INSTANCE = new MBRTreeNodeConverter();

    private MBRTreeNodeConverter() {
    }

    @Override
    public int getDimensions() {
        return 3;
    }

    @Override
    public double getMax(int dimension, TreeNode region) {
        return switch (dimension) {
            case 0 -> region.getMax().getBlockX();
            case 1 -> region.getMax().getBlockY();
            case 2 -> region.getMax().getBlockZ();
            default -> throw new AssertionError();
        };
    }

    @Override
    public double getMin(int dimension, TreeNode region) {
        return switch (dimension) {
            case 0 -> region.getMin().getBlockX();
            case 1 -> region.getMin().getBlockY();
            case 2 -> region.getMin().getBlockZ();
            default -> throw new AssertionError();
        };
    }
}

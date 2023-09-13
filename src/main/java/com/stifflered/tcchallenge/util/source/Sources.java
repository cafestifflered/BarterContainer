package com.stifflered.tcchallenge.util.source;

import com.stifflered.tcchallenge.tree.Tree;
import com.stifflered.tcchallenge.tree.TreeKey;
import com.stifflered.tcchallenge.util.source.impl.PlayerSource;

public interface Sources {

    ObjectSource<TreeKey, Tree> TREE_SOURCE = new PlayerSource();

}

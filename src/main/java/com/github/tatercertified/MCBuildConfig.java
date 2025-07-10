package com.github.tatercertified;

import com.github.tatercertified.utils.MCGradleBuilder;

import java.util.HashMap;
import java.util.TreeMap;
import java.util.function.Consumer;

public class MCBuildConfig {
    private final TreeMap<String, HashMap<String, String>> dependencies;

    public MCBuildConfig(Consumer<MCGradleBuilder> configure) {
        MCGradleBuilder builder = new MCGradleBuilder();
        configure.accept(builder);
        this.dependencies = builder.build();
    }

    public TreeMap<String, HashMap<String, String>> getDependencies() {
        return this.dependencies;
    }
}

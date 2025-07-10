package com.github.tatercertified.utils;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.TreeMap;
import java.util.function.Consumer;

public class MCGradleBuilder {
    private final SemverComparator comparator = new SemverComparator();
    private final TreeMap<String, HashMap<String, String>> dependencies = new TreeMap<>(comparator);

    /**
     * Allows specifying a Minecraft version which is supported by this mod and will be compiled for
     * @param version Minecraft version (Ex: "1.21.7")
     * @return The Builder to continue building
     */
    public MCGradleBuilder mcVer(String version, Consumer<DependencyBuilder> deps) {
        DependencyBuilder builder = new DependencyBuilder(this.dependencies);
        deps.accept(builder);
        HashMap<String, String> result = builder.build();
        this.dependencies.put(version, result);
        return this;
    }

    @ApiStatus.Internal
    public TreeMap<String, HashMap<String, String>> build() {
        return this.dependencies;
    }
}

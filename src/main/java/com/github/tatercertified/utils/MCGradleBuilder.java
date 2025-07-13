package com.github.tatercertified.utils;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;

public class MCGradleBuilder {
    private final SemverComparator comparator = new SemverComparator();
    private final TreeMap<String, HashMap<String, String>> dependencies = new TreeMap<>(comparator);
    private final List<DependencyBuilder> previousBuilders = new ArrayList<>();

    /**
     * Allows specifying a Minecraft version which is supported by this mod and will be compiled for
     * @param version Minecraft version (Ex: "1.21.7")
     * @return The Builder to continue building
     */
    public MCGradleBuilder mcVer(String version, Consumer<DependencyBuilder> deps) {
        DependencyBuilder builder = new DependencyBuilder(version);
        deps.accept(builder);
        HashMap<String, String> result = builder.build();
        this.dependencies.put(version, result);
        if (builder.shouldSave()) {
            this.previousBuilders.add(builder);
        }
        return this;
    }

    @ApiStatus.Internal
    public TreeMap<String, HashMap<String, String>> build() {
        for (DependencyBuilder builder : this.previousBuilders) {
            builder.buildCopies(this.dependencies);
        }
        return this.dependencies;
    }
}

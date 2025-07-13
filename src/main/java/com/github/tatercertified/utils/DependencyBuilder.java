package com.github.tatercertified.utils;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;

public class DependencyBuilder {
    private final HashMap<String, String> dependencies = new HashMap<>();
    private final List<Consumer<TreeMap<String, HashMap<String, String>>>> copyDeps = new ArrayList<>();
    private final String mcVer;

    public DependencyBuilder(String mcVer) {
        this.mcVer = mcVer;
    }

    /**
     * Creates a dependency overwrite
     * @param var The variable declared in gradle.properties
     * @param val The new value for that variable
     * @return DependencyBuilder
     */
    public DependencyBuilder dep(String var, String val) {
        this.dependencies.put(var, val);
        return this;
    }

    private void depConsumer(String var, String val, TreeMap<String, HashMap<String, String>> map) {
        HashMap<String, String> depMap = map.get(this.mcVer);
        depMap.put(var, val);
    }

    /**
     * Copies a value from a previously declared Minecraft version
     * @param var The variable declared in gradle.properties
     * @param mcVer The Minecraft version to copy from
     * @return DependencyBuilder
     */
    public DependencyBuilder depCopy(String var, String mcVer) {
        this.copyDeps.add(stringHashMapTreeMap -> {
            String val = stringHashMapTreeMap.get(mcVer).get(var);
            depConsumer(var, val, stringHashMapTreeMap);
        });
        return this;
    }

    /**
     * Removes a value from the gradle file
     * @param var The variable declared in gradle.properties
     * @return DependencyBuilder
     */
    public DependencyBuilder depExclude(String var) {
        dep(var, null);
        return this;
    }

    /**
     * Copies all overwrites from another Minecraft version
     * @param mcVer The previously declared Minecraft version to copy from
     * @return DependencyBuilder
     */
    public DependencyBuilder depCopyAll(String mcVer) {
        this.copyDeps.add(stringHashMapTreeMap -> {
            stringHashMapTreeMap.put(this.mcVer, stringHashMapTreeMap.get(mcVer));
        });
        return this;
    }

    /**
     * Do not call this.<p>
     * Builds the final result
     * @return All configurations
     */
    @ApiStatus.Internal
    public HashMap<String, String> build() {
        return this.dependencies;
    }

    /**
     * Do not call this.<p>
     * Builds all dependencies that depend on other versions
     * @param otherDeps Other version's dependencies after {@link DependencyBuilder#build()} has been run
     */
    @ApiStatus.Internal
    public void buildCopies(TreeMap<String, HashMap<String, String>> otherDeps) {
        for (Consumer<TreeMap<String, HashMap<String, String>>> consumer : this.copyDeps) {
            consumer.accept(otherDeps);
        }
    }

    /**
     * If the dependency builder should be saved in the Minecraft version builder
     * @return True if it should be saved
     */
    @ApiStatus.Internal
    public boolean shouldSave() {
        return !this.copyDeps.isEmpty();
    }
}

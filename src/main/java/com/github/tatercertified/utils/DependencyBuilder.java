package com.github.tatercertified.utils;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.TreeMap;

public class DependencyBuilder {
    private final HashMap<String, String> dependencies = new HashMap<>();
    private final TreeMap<String, HashMap<String, String>> linkedDeps;

    public DependencyBuilder(TreeMap<String, HashMap<String, String>> linkedDeps) {
        this.linkedDeps = linkedDeps;
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

    /**
     * Copies a value from a previously declared Minecraft version
     * @param var The variable declared in gradle.properties
     * @param mcVer The Minecraft version to copy from
     * @return DependencyBuilder
     */
    public DependencyBuilder depCopy(String var, String mcVer) {
        String val = this.linkedDeps.get(mcVer).get(var);
        dep(var, val);
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
        this.dependencies.putAll(this.linkedDeps.get(mcVer));
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
}

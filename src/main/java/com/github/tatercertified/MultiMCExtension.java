package com.github.tatercertified;

import java.nio.file.Path;
import java.util.HashMap;

public class MultiMCExtension {
    /**
     * The directory to stick the compiled jars
     */
    private Path outputDir;
    /**
     * The path to the mod config (ex: fabric mod json) inside a compiled jar.<p>
     * <b>This should be set to the key of a property in the gradle.properties file in the specific loader submodule</b>
     */
    private String modConfigFileRelativePath;
    /**
     * Loader and the path to the build directory (submodule directory)
     */
    private HashMap<String, Path> loaderSpecificPaths;
    /**
     * GradleConfig instance
     */
    private MCBuildConfig gradleConfig;

    /**
     * Whether to mark as future compatible (unreleased MC versions)
     */
    private boolean futureCompatible = false;

    /**
     * Paths to modules that contains commonly shared code
     */
    private Path[] commonDirs = new Path[0];

    /**
     * The version that is actively being developed for
     */
    private String currentMinecraftVer;

    /**
     * Overrides the default {name}-{version}.jar output jar template.<p>
     * Use {name} for the project's name and {version} for the project's version
     */
    private String outputFileOverride;

    public Path getOutputDir() {
        return this.outputDir;
    }

    public void setOutputDir(Path outputDir) {
        this.outputDir = outputDir;
    }

    public String getModConfigFileRelativePath() {
        return modConfigFileRelativePath;
    }

    public void setModConfigFileRelativePath(String modConfigFileRelativePath) {
        this.modConfigFileRelativePath = modConfigFileRelativePath;
    }

    public HashMap<String, Path> getLoaderSpecificPaths() {
        return loaderSpecificPaths;
    }

    public void setLoaderSpecificPaths(HashMap<String, Path> loaderSpecificPaths) {
        this.loaderSpecificPaths = loaderSpecificPaths;
    }

    public MCBuildConfig getGradleConfig() {
        return this.gradleConfig;
    }

    public void setGradleConfig(MCBuildConfig gradleConfig) {
        this.gradleConfig = gradleConfig;
    }

    public boolean isFutureCompatible() {
        return futureCompatible;
    }

    public void setFutureCompatible(boolean futureCompatible) {
        this.futureCompatible = futureCompatible;
    }

    public Path[] getCommonDirs() {
        return commonDirs;
    }

    public void setCommonDirs(Path[] commonDir) {
        this.commonDirs = commonDir;
    }

    public String getCurrentMinecraftVer() {
        return currentMinecraftVer;
    }

    public void setCurrentMinecraftVer(String currentMinecraftVer) {
        this.currentMinecraftVer = currentMinecraftVer;
    }

    public String getOutputFileOverride() {
        return outputFileOverride;
    }

    public void setOutputFileOverride(String outputFileOverride) {
        this.outputFileOverride = outputFileOverride;
    }
}

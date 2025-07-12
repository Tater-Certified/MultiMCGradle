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
}

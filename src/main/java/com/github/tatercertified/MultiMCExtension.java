package com.github.tatercertified;

import java.nio.file.Path;
import java.util.HashMap;

public class MultiMCExtension {
    private Path outputDir;
    private Path commonPath;
    private HashMap<String, Path> loaderSpecificPaths;
    private MCBuildConfig gradleConfig;

    public Path getOutputDir() {
        return this.outputDir;
    }

    public void setOutputDir(Path outputDir) {
        this.outputDir = outputDir;
    }

    public Path getCommonPath() {
        return commonPath;
    }

    public void setCommonPath(Path commonPath) {
        this.commonPath = commonPath;
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

package com.github.tatercertified.utils;

import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.tooling.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class RemoteGradleRunner {
    public static void runBuildOnSubmodule(File subprojectDir) {
        try (ProjectConnection connection = GradleConnector.newConnector()
                .forProjectDirectory(subprojectDir)
                .connect()) {
            connection.newBuild()
                    .forTasks("build")
                    .run();
        }
    }

    public static String getJarName(Project project) {
        Jar jarTask = (Jar) project.getTasks().getByName("jar");
        File jarFile = jarTask.getArchiveFile().get().getAsFile();
        return jarFile.getName();
    }

    public static String getGradlePropertyValue(String key, Path workingDir) {
        Path gradleProperties = workingDir.resolve("gradle.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(gradleProperties)) {
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return properties.getProperty(key);
    }
}

package com.github.tatercertified.utils;

import org.gradle.api.Project;
import org.gradle.tooling.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class RemoteGradleRunner {
    public static boolean runBuildOnSubmodule(File subprojectDir, Project project) {
        try (ProjectConnection connection = GradleConnector.newConnector()
                .forProjectDirectory(subprojectDir)
                .connect()) {
            connection.newBuild()
                    .forTasks("build")
                    .setStandardOutput(new GradleOutputStream(project, false))
                    .setStandardError(new GradleOutputStream(project, true))
                    .run();
        } catch (GradleConnectionException ignored) {
            return false;
        }
        return true;
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

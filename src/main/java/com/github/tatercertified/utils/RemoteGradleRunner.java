package com.github.tatercertified.utils;

import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.tooling.*;

import java.io.File;

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
}

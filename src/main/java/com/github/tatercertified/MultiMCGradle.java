package com.github.tatercertified;

import com.github.tatercertified.tasks.MultiMCCompile;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

public class MultiMCGradle implements Plugin<Project> {

    @Override
    public void apply(@NotNull Project project) {
        MultiMCExtension ext = project.getExtensions()
                .create("multimc", MultiMCExtension.class);

        project.getTasks().register("multi-compile").get().doLast(task -> MultiMCCompile.compile(ext, task.getProject()));
    }
}
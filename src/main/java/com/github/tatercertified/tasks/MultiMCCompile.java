package com.github.tatercertified.tasks;

import com.github.tatercertified.MultiMCExtension;
import com.github.tatercertified.utils.RemoteGradleRunner;
import com.vdurmont.semver4j.Semver;
import org.gradle.api.Project;

import java.io.*;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class MultiMCCompile {

    public static void compile(MultiMCExtension ext, Project project) {
        createOutputDir(ext);

        for (Map.Entry<String, Path> entry : ext.getLoaderSpecificPaths().entrySet()) {
            copyGradleProperties(entry.getValue());
            LinkedHashMap<Path, Path> previousJars = new LinkedHashMap<>();
            Path lastJarFile = null;
            for (String mcVer : ext.getGradleConfig().getDependencies().keySet()) {
                modifyGradleProperties(ext, entry.getValue(), mcVer);
                if (lastJarFile == null || modifySourceCode(entry.getValue(), mcVer)) {
                    RemoteGradleRunner.runBuildOnSubmodule(entry.getValue().toFile());

                    String projectName = project.getName();
                    String projectVer = project.getVersion().toString();
                    String jarName = RemoteGradleRunner.getJarName(project);
                    Path lastOutput = migrateOutputFile(entry.getValue(), projectName, projectVer, jarName, mcVer, entry.getKey(), ext.getOutputDir(), project);
                    if (lastOutput != null) {
                        String mcVerFileName = lastOutput.getFileName().toString().replace(".jar", ".txt");
                        Path mcVerFile = lastOutput.getParent().resolve(mcVerFileName);
                        try {
                            Files.deleteIfExists(mcVerFile);
                            Files.createFile(mcVerFile);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(mcVerFile.toFile()))) {
                            writer.write(mcVer + ",");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        previousJars.put(lastOutput, mcVerFile);
                        lastJarFile = lastOutput;
                    }
                } else {
                    Path mcVerFile = previousJars.get(lastJarFile);
                    String line;
                    try (BufferedReader reader = new BufferedReader(new FileReader(mcVerFile.toFile()))) {
                        line = reader.readLine();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    line += mcVer + ",";
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(mcVerFile.toFile()))) {
                        writer.write(line);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            // TODO Write new supported versions to fabric mod json and/or (Neo)Forge's mod toml
            // I can do this be getting the first and last mcVer in the list. If they are the same, then I can just skip modifying the mod json/toml.
        }
    }

    private static void createOutputDir(MultiMCExtension ext) {
        if (!Files.isDirectory(ext.getOutputDir())) {
            try {
                Files.createDirectories(ext.getOutputDir());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void copyGradleProperties(Path workingDir) {
        Path gradleProperties = workingDir.resolve("gradle.properties");
        try {
            // Take a copy of the original file
            Files.copy(gradleProperties, workingDir.resolve("gradle.txt"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void modifyGradleProperties(MultiMCExtension ext, Path workingDir, String mcVer) {
        Path gradleProperties = workingDir.resolve("gradle.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(gradleProperties)) {
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        HashMap<String, String> vars = ext.getGradleConfig().getDependencies().get(mcVer);
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            if (properties.contains(entry.getKey())) {
                properties.setProperty(entry.getKey(), entry.getValue());
            }
        }
        try (FileWriter writer = new FileWriter(gradleProperties.toFile())) {
            properties.store(writer, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean modifySourceCode(Path workingDir, String mcVer) {
        boolean markChanged = false; // If a build is necessary

        Semver mcSemver = new Semver(mcVer);
        workingDir = workingDir.resolve("src/main/java");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(workingDir, "*.java")) {
            for (Path entry: stream) {
                if (modifyClassFile(entry, mcSemver)) {
                    markChanged = true;
                }
            }
        } catch (DirectoryIteratorException | IOException e) {
            throw new RuntimeException(e);
        }
        return markChanged;
    }

    private static boolean modifyClassFile(Path classFilePath, Semver mcVer) {
        boolean markChanged = false; // If a new build is necessary

        try (RandomAccessFile file = new RandomAccessFile(classFilePath.toFile(), "rw")) {
            String line;
            long pointer = 0;
            boolean modified = false;
            while ((line = file.readLine()) != null) {
                long currentPointer = file.getFilePointer();
                String modifiedLine = line.stripLeading();
                if (modifiedLine.startsWith("//: ")) {
                    String subString = modifiedLine.substring(4);
                    if (modified && subString.startsWith("END")) {
                        modified = false;
                        line = swapEndingComment(line, false);
                        file.seek(pointer);
                        file.writeBytes(line + System.lineSeparator());
                    } else {
                        String semverReq = modifiedLine.substring(4);
                        // Check if it should be disabled
                        if (!mcVer.satisfies(semverReq)) {
                            modified = true;
                            markChanged = true; // Needs a build
                            line = swapStartingComment(line, false);
                            file.seek(pointer);
                            file.writeBytes(line + System.lineSeparator());
                        }
                    }
                } else if (modifiedLine.startsWith("/*\\ ")) {
                    String semverReq = modifiedLine.substring(4);
                    if (mcVer.satisfies(semverReq)) {
                        modified = true;
                        markChanged = true; // Needs a build
                        line = swapStartingComment(line, true);
                        file.seek(pointer);
                        file.writeBytes(line + System.lineSeparator());
                    }
                } else if (modified && modifiedLine.startsWith("\\*/ ")) {
                    modified = false;
                    line = swapEndingComment(line, true);
                    file.seek(pointer);
                    file.writeBytes(line + System.lineSeparator());
                }
                pointer = currentPointer;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return markChanged;
    }

    private static String swapStartingComment(String line, boolean enable) {
        if (enable) {
            return line.replace("/*\\", "//:");
        } else {
            return line.replace("//:", "/*\\");
        }
    }

    private static String swapEndingComment(String line, boolean enable) {
        if (enable) {
            return line.replace("\\*/    ", "//: END");
        } else {
            return line.replace("//: END", "\\*/    ");
        }
    }

    private static Path migrateOutputFile(Path subprojectPath, String projectName, String projectVer, String jarFileName, String mcVer, String loader, Path finalOutputPath, Project project) {
        Path outputPath = subprojectPath.resolve("build/libs/" + jarFileName);
        if (Files.exists(outputPath)) {
            File outputJar = outputPath.toFile();
            String renamedFileName = projectName + "-" + loader + "-" + projectVer + "+mc" + mcVer + ".jar";
            File renamedFile = subprojectPath.resolve("build/libs/" + renamedFileName).toFile();
            if (!outputJar.renameTo(renamedFile)) {
                project.getLogger().warn("Failed to move {} Minecraft {}", loader, mcVer);
            }
            try {
                Files.delete(outputPath);
                finalOutputPath = finalOutputPath.toAbsolutePath().resolve(renamedFileName);
                Files.move(renamedFile.toPath(), finalOutputPath);
                return finalOutputPath;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            project.getLogger().warn("Failed to compile project for {} Minecraft {}", loader, mcVer);
            return null;
        }
    }
}

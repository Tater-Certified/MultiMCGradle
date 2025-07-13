package com.github.tatercertified.tasks;

import com.github.tatercertified.MultiMCExtension;
import com.github.tatercertified.utils.RemoteGradleRunner;
import com.vdurmont.semver4j.Semver;
import org.gradle.api.Project;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

public class MultiMCCompile {

    public static void compile(MultiMCExtension ext, Project project) {
        // Ensure the proper working directory is set
        ext.setOutputDir(project.getRootDir().toPath().resolve(ext.getOutputDir()));
        for (Map.Entry<String, Path> entry : ext.getLoaderSpecificPaths().entrySet()) {
            entry.setValue(project.getRootDir().toPath().resolve(entry.getValue()));
        }
        for (int i = 0; i < ext.getCommonDirs().length; i++) {
            Path common = ext.getCommonDirs()[i];
            ext.getCommonDirs()[i] = project.getRootDir().toPath().resolve(common);
        }

        createOutputDir(ext, project);
        for (Map.Entry<String, Path> entry : ext.getLoaderSpecificPaths().entrySet()) {
            copyGradleProperties(entry.getValue(), ext);
            LinkedHashMap<Path, Path> previousJars = new LinkedHashMap<>();
            Path lastJarFile = null;
            int index = 0;
            for (String mcVer : ext.getGradleConfig().getDependencies().keySet()) {
                index++;
                project.getLogger().info("--- Compiling {} ---", mcVer);
                boolean markAsFutureCompatible = (index == ext.getGradleConfig().getDependencies().size()) && ext.isFutureCompatible();
                modifyGradleProperties(ext, entry.getValue(), mcVer);
                if (modifySourceCode(entry.getValue(), mcVer, project, ext) || lastJarFile == null) {
                    project.getLogger().info("{} is incompatible with the previous version", mcVer);
                    RemoteGradleRunner.runBuildOnSubmodule(entry.getValue().toFile());

                    String childName = entry.getValue().getFileName().toString();
                    Project child = project.getChildProjects().get(childName);
                    String projectName = child.getName();
                    String projectVer = child.getVersion().toString();
                    Path lastOutput = migrateOutputFile(entry.getValue(), projectName, projectVer, mcVer, entry.getKey(), ext.getOutputDir(), project);
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
                            if (markAsFutureCompatible) {
                                writer.write(mcVer + ",*");
                            } else {
                                writer.write(mcVer + ",");
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        previousJars.put(lastOutput, mcVerFile);
                        lastJarFile = lastOutput;
                    }
                } else {
                    project.getLogger().info("{} is compatible with the previous version", mcVer);
                    Path mcVerFile = previousJars.get(lastJarFile);
                    String line;
                    try (BufferedReader reader = new BufferedReader(new FileReader(mcVerFile.toFile()))) {
                        line = reader.readLine();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    if (markAsFutureCompatible) {
                        line += mcVer + ",*";
                    } else {
                        line += mcVer + ",";
                    }
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(mcVerFile.toFile()))) {
                        writer.write(line);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            cleanUp(entry.getValue(), ext);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ext.getOutputDir(), "*.jar")) {
            for (Path entry: stream) {
                String txtFileName = entry.getFileName().toString().replace(".jar", ".txt");
                File txtFile = ext.getOutputDir().resolve(txtFileName).toFile();
                String[] supportedMCVers;
                try (BufferedReader reader = new BufferedReader(new FileReader(txtFile))) {
                    String line = reader.readLine();
                    supportedMCVers = line.split(",");
                }
                Path workingDir = null;
                for (Map.Entry<String, Path> loaderEntry : ext.getLoaderSpecificPaths().entrySet()) {
                    if (txtFileName.contains(loaderEntry.getKey())) {
                        workingDir = loaderEntry.getValue();
                        break;
                    }
                }
                if (workingDir == null) {
                    continue;
                }
                String configInJar = RemoteGradleRunner.getGradlePropertyValue(ext.getModConfigFileRelativePath(), workingDir);
                modifyJsonInJar(entry, configInJar, supportedMCVers, project);
                Files.delete(txtFile.toPath());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createOutputDir(MultiMCExtension ext, Project project) {
        project.getLogger().info("Checking for output folder at {}", ext.getOutputDir().toAbsolutePath());
        if (!Files.isDirectory(ext.getOutputDir())) {
            try {
                Files.createDirectories(ext.getOutputDir());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void copyGradleProperties(Path workingDir, MultiMCExtension ext) {
        Path gradleProperties = workingDir.resolve("gradle.properties");
        try {
            // Project
            {
                Files.copy(gradleProperties, workingDir.resolve("gradle.txt"), StandardCopyOption.REPLACE_EXISTING);
            }

            // Common
            {
                for (Path common : ext.getCommonDirs()) {
                    Files.copy(gradleProperties, common.resolve("gradle.txt"), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void cleanUp(Path workingDir, MultiMCExtension ext) {
        try {
            // Project
            {
                Path gradleProperties = workingDir.resolve("gradle.properties");
                Files.deleteIfExists(workingDir.resolve("gradle.properties"));
                Path gradleTxt = workingDir.resolve("gradle.txt");
                gradleTxt.toFile().renameTo(gradleProperties.toFile());
            }

            // Common
            {
                for (Path common : ext.getCommonDirs()) {
                    Path gradleProperties = common.resolve("gradle.properties");
                    Files.deleteIfExists(common.resolve("gradle.properties"));
                    Path gradleTxt = common.resolve("gradle.txt");
                    gradleTxt.toFile().renameTo(gradleProperties.toFile());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void modifyJsonInJar(Path jarPath, String filePathInJar, String[] supportedMCVers, Project project) throws IOException {
        Path tempJar = Files.createTempFile("modified-", ".jar");

        try (JarFile jarFile = new JarFile(jarPath.toFile());
             JarOutputStream jos = new JarOutputStream(Files.newOutputStream(tempJar))) {

            byte[] buffer = new byte[8192];

            // Copy all entries except the one we want to replace
            for (JarEntry entry : jarFile.stream().toList()) {
                String entryName = entry.getName();
                project.getLogger().info("Checking if jar file {} == {}", entryName, filePathInJar);

                // Replace the JSON file
                if (entryName.equals(filePathInJar)) {
                    InputStream is = jarFile.getInputStream(entry);
                    String originalJson = new String(is.readAllBytes());
                    String modifiedJson = originalJson.replace("%mcVer%", generateVersionExpression(entryName, supportedMCVers));

                    JarEntry newEntry = new JarEntry(entryName);
                    jos.putNextEntry(newEntry);
                    jos.write(modifiedJson.getBytes());
                    jos.closeEntry();
                    continue;
                }

                // Copy other entries
                jos.putNextEntry(new JarEntry(entryName));
                try (InputStream is = jarFile.getInputStream(entry)) {
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        jos.write(buffer, 0, read);
                    }
                }
                jos.closeEntry();
            }
        }

        // Replace original JAR
        Files.move(tempJar, jarPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String generateVersionExpression(String filename, String[] supportedVersions) {
        if (filename.contains(".json")) {
            if (supportedVersions.length == 1) {
                return supportedVersions[0];
            } else if (supportedVersions.length > 1) {
                if (Objects.equals(supportedVersions[supportedVersions.length - 1], "*")) {
                    return ">=" + supportedVersions[0];
                } else {
                    return ">=" + supportedVersions[0] + " <=" + supportedVersions[supportedVersions.length - 1];
                }
            } else {
                return "*";
            }
        } else if (filename.contains(".toml")) {
            if (supportedVersions.length == 1) {
                return "[" + supportedVersions[0] + "]";
            } else if (supportedVersions.length > 1) {
                if (Objects.equals(supportedVersions[supportedVersions.length - 1], "*")) {
                    return "[" + supportedVersions[0] + ",)";
                } else {
                    return "[" + supportedVersions[0] + "," + supportedVersions[supportedVersions.length - 1] + "]";
                }
            } else {
                return "(,)";
            }
        } else {
            return "";
        }
    }

    private static void modifyGradleProperties(MultiMCExtension ext, Path workingDir, String mcVer) {
        // Project code
        {
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

        // Common code
        {
            for (Path common : ext.getCommonDirs()) {
                Path gradleProperties = common.resolve("gradle.properties");
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
        }
    }

    private static boolean modifySourceCode(Path workingDir, String mcVer, Project project, MultiMCExtension ext) {
        final boolean[] markChanged = {false}; // If a build is necessary

        Semver mcSemver = new Semver(mcVer, Semver.SemverType.NPM);
        // Project code
        {
            Path newWorkingDir = workingDir.resolve("src/main/java");
            try (Stream<Path> stream = Files.walk(newWorkingDir)) {
                stream.filter(path -> path.toString().endsWith(".java"))
                        .forEach(entry -> {
                            if (modifyClassFile(entry, mcSemver, project)) {
                                markChanged[0] = true;
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // Common code
        {
            for (Path commonPath : ext.getCommonDirs()) {
                Path commonWorkingDir = commonPath.resolve("src/main/java");
                try (Stream<Path> stream = Files.walk(commonWorkingDir)) {
                    stream.filter(path -> path.toString().endsWith(".java"))
                            .forEach(entry -> {
                                if (modifyClassFile(entry, mcSemver, project)) {
                                    markChanged[0] = true;
                                }
                            });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return markChanged[0];
    }

    private static boolean modifyClassFile(Path classFilePath, Semver mcVer, Project project) {
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
                    if (subString.startsWith("END")) {
                        if (modified) {
                            modified = false;
                            line = swapEndingComment(line, false, project);
                            file.seek(pointer);
                            file.writeBytes(line + System.lineSeparator());
                        }
                    } else {
                        String semverReq = modifiedLine.substring(4);
                        // Check if it should be disabled
                        if (!mcVer.satisfies(semverReq)) {
                            modified = true;
                            markChanged = true; // Needs a build
                            line = swapStartingComment(line, false, project);
                            file.seek(pointer);
                            file.writeBytes(line + System.lineSeparator());
                        }
                    }
                } else if (modifiedLine.startsWith("/*\\ ")) {
                    String semverReq = modifiedLine.substring(4);
                    if (mcVer.satisfies(semverReq)) {
                        modified = true;
                        markChanged = true; // Needs a build
                        line = swapStartingComment(line, true, project);
                        file.seek(pointer);
                        file.writeBytes(line + System.lineSeparator());
                    }
                } else if (modified && modifiedLine.startsWith("\\END */")) {
                    modified = false;
                    line = swapEndingComment(line, true, project);
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

    private static String swapStartingComment(String line, boolean enable, Project project) {
        if (enable) {
            project.getLogger().info("Replacing {} with {}", line, line.replace("/*\\", "//:"));
            return line.replace("/*\\", "//:");
        } else {
            project.getLogger().info("Replacing {} with {}", line, line.replace("//:", "/*\\"));
            return line.replace("//:", "/*\\");
        }
    }

    private static String swapEndingComment(String line, boolean enable, Project project) {
        if (enable) {
            project.getLogger().info("Replacing {} with {}", line, line.replace("\\END */", "//: END"));
            return line.replace("\\END */", "//: END");
        } else {
            project.getLogger().info("Replacing {} with {}", line, line.replace("//: END", "\\END */"));
            return line.replace("//: END", "\\END */");
        }
    }

    private static Path migrateOutputFile(Path subprojectPath, String projectName, String projectVer, String mcVer, String loader, Path finalOutputPath, Project project) {
        Path outputDir = subprojectPath.resolve("build/libs/");
        Path outputPath = outputDir;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir)) {
            for (Path entry : stream) {
                if (!entry.getFileName().toString().endsWith("-sources.jar")) {
                    outputPath = entry;
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (Files.exists(outputPath)) {
            File outputJar = outputPath.toFile();
            String renamedFileName = projectName + "-" + loader + "-" + projectVer + "+mc" + mcVer + ".jar";
            File renamedFile = subprojectPath.resolve("build/libs/" + renamedFileName).toFile();
            if (!outputJar.renameTo(renamedFile)) {
                project.getLogger().warn("Failed to move {} Minecraft {}", loader, mcVer);
            }
            try {
                finalOutputPath = finalOutputPath.toAbsolutePath().resolve(renamedFileName);
                Files.move(renamedFile.toPath(), finalOutputPath, StandardCopyOption.REPLACE_EXISTING);
                return finalOutputPath;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            project.getLogger().warn("Failed to compile project for {} Minecraft {}", loader, mcVer);
            project.getLogger().warn("Couldn't find file: {}", outputPath);
            return null;
        }
    }
}

package com.github.tatercertified.tasks;

import com.github.tatercertified.MultiMCExtension;
import com.github.tatercertified.utils.RemoteGradleRunner;
import com.vdurmont.semver4j.Semver;
import org.gradle.api.GradleException;
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
                modifyGradleProperties(ext, entry.getValue(), mcVer, project);
                if (modifySourceCode(entry.getValue(), mcVer, project, ext) || lastJarFile == null) {
                    project.getLogger().info("{} is incompatible with the previous version", mcVer);
                    if (!RemoteGradleRunner.runBuildOnSubmodule(entry.getValue().toFile(), project)) {
                        project.getLogger().warn("{} failed to compile; Ignoring...", mcVer);
                        continue;
                    }

                    String childName = entry.getValue().getFileName().toString();
                    Project child = project.getChildProjects().get(childName);
                    String projectName = project.getName();
                    String projectVer = child.getVersion().toString();
                    Path lastOutput = migrateOutputFile(entry.getValue(), projectName, projectVer, mcVer, entry.getKey(), ext.getOutputDir(), child, ext);
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

    /**
     * Switches the current Minecraft version to the one specified in the "multimc" block
     * @param ext MultiMCExtension instance
     * @param project Project instance
     */
    public static void switchVersion(MultiMCExtension ext, Project project) {
        // Fix paths
        for (Map.Entry<String, Path> entry : ext.getLoaderSpecificPaths().entrySet()) {
            entry.setValue(project.getRootDir().toPath().resolve(entry.getValue()));
        }
        for (int i = 0; i < ext.getCommonDirs().length; i++) {
            Path common = ext.getCommonDirs()[i];
            ext.getCommonDirs()[i] = project.getRootDir().toPath().resolve(common);
        }

        String mcVer = ext.getCurrentMinecraftVer();
        if (mcVer == null) {
            project.getLogger().error("currentMinecraftVer is not specified in build.gradle");
            return;
        }
        project.getLogger().info("--- Switching to {} ---", mcVer);
        for (Map.Entry<String, Path> entry : ext.getLoaderSpecificPaths().entrySet()) {
            modifyGradleProperties(ext, entry.getValue(), mcVer, project);
            modifySourceCode(entry.getValue(), mcVer, project, ext);
        }
        project.getLogger().info("--- Switched to {} ---", mcVer);
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
        try {
            // Project
            {
                Path gradleProperties = workingDir.resolve("gradle.properties");
                Files.copy(gradleProperties, workingDir.resolve("gradle.txt"), StandardCopyOption.REPLACE_EXISTING);
            }

            // Common
            {
                for (Path common : ext.getCommonDirs()) {
                    Path gradleProperties = common.resolve("gradle.properties");
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

    private static void modifyGradleProperties(MultiMCExtension ext, Path workingDir, String mcVer, Project project) {
        // Debug printout
        project.getLogger().info("Loading {} Gradle Overrides", ext.getGradleConfig().getDependencies().get(mcVer).size());
        HashMap<String, String> vars = ext.getGradleConfig().getDependencies().get(mcVer);
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            project.getLogger().info("Setting {} to {}", entry.getKey(), entry.getValue());
        }

        // Project code
        {
            Path gradleProperties = workingDir.resolve("gradle.properties");
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(gradleProperties)) {
                properties.load(input);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                if (properties.containsKey(entry.getKey())) {
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
                for (Map.Entry<String, String> entry : vars.entrySet()) {
                    if (properties.containsKey(entry.getKey())) {
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
            int lineCount = 0;
            boolean modified = false;
            boolean nextAnnotationOpening = true;
            while ((line = file.readLine()) != null) {
                long currentPointer = file.getFilePointer();
                String modifiedLine = line.stripLeading();
                if (modifiedLine.startsWith("//: ")) {
                    String subString = modifiedLine.substring(4);
                    if (subString.startsWith("END")) {
                        // Ending
                        if (nextAnnotationOpening) {
                            printMalformedVersionSyntaxError(lineCount, line, classFilePath.getFileName().toString(), project, false);
                        }

                        if (modified) {
                            modified = false;
                            line = swapEndingComment(line, false, project);
                            file.seek(pointer);
                            file.writeBytes(line + System.lineSeparator());
                        }

                        nextAnnotationOpening = true;
                    } else {
                        // Opening
                        if (!nextAnnotationOpening) {
                            printMalformedVersionSyntaxError(lineCount, line, classFilePath.getFileName().toString(), project, true);
                        }

                        String semverReq = modifiedLine.substring(4);
                        // Check if it should be disabled
                        if (!mcVer.satisfies(semverReq)) {
                            modified = true;
                            markChanged = true; // Needs a build
                            line = swapStartingComment(line, false, project);
                            file.seek(pointer);
                            file.writeBytes(line + System.lineSeparator());
                        }

                        nextAnnotationOpening = false;
                    }
                } else if (modifiedLine.startsWith("/*\\ ")) {
                    // Opening
                    if (!nextAnnotationOpening) {
                        printMalformedVersionSyntaxError(lineCount, line, classFilePath.getFileName().toString(), project, true);
                    }

                    String semverReq = modifiedLine.substring(4);
                    if (mcVer.satisfies(semverReq)) {
                        modified = true;
                        markChanged = true; // Needs a build
                        line = swapStartingComment(line, true, project);
                        file.seek(pointer);
                        file.writeBytes(line + System.lineSeparator());
                    }

                    nextAnnotationOpening = false;
                } else if (modified && modifiedLine.startsWith("\\END */")) {
                    // Closing
                    if (nextAnnotationOpening) {
                        printMalformedVersionSyntaxError(lineCount, line, classFilePath.getFileName().toString(), project, false);
                    }

                    modified = false;
                    line = swapEndingComment(line, true, project);
                    file.seek(pointer);
                    file.writeBytes(line + System.lineSeparator());

                    nextAnnotationOpening = true;
                }
                pointer = currentPointer;
                lineCount++;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return markChanged;
    }

    private static void printMalformedVersionSyntaxError(int line, String lineStr, String classFileName, Project project, boolean expectedOpening) {
        if (expectedOpening) {
            project.getLogger().error("Expected opening syntax in {} at line {}: {}", classFileName, line, lineStr);;
        } else {
            project.getLogger().error("Expected closing syntax in {} at line {}: {}", classFileName, line, lineStr);
        }
        throw new GradleException("Task failed: Syntax mismatch");
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

    private static Path migrateOutputFile(Path subprojectPath, String projectName, String projectVer, String mcVer, String loader, Path finalOutputPath, Project project, MultiMCExtension ext) {
        Path outputDir = subprojectPath.resolve("build/libs/");
        Path outputPath = getChildProjectJarOutput(outputDir, ext, project);
        project.getLogger().info("Detected Built Jar at {}", outputPath);

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

    private static Path getChildProjectJarOutput(Path outputDir, MultiMCExtension ext, Project child) {
        String outputFileName;
        if (ext.getOutputFileOverride() == null) {
            outputFileName = child.getName() + "-" + child.getVersion() + ".jar";
        } else {
            outputFileName = ext.getOutputFileOverride().replace("{name}", child.getName()).replace("{version}", child.getVersion().toString());
        }
        child.getLogger().info("Searching for {}", outputFileName);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir)) {
            for (Path entry : stream) {
                if (entry.getFileName().toString().equals(outputFileName)) {
                    return entry;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Second try, just grab the largest file. It's probably right
        Path largestFile = null;
        long sizeBytes = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir)) {
            for (Path entry : stream) {
                long bytes = Files.size(entry);
                if (bytes > sizeBytes) {
                    sizeBytes = bytes;
                    largestFile = entry;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return largestFile;
    }
}

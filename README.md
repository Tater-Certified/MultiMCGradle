# MultiMC Gradle
### A multiversion compilation and programming solution for Minecraft modding projects
-- -
[![](https://jitpack.io/v/Tater-Certified/MultiMCGradle.svg)](https://jitpack.io/#Tater-Certified/MultiMCGradle)
## About
MultiMC Gradle provides a simple way to create a single mod compatible with various versions of Minecraft. 
This Gradle plugin is loader-independent and can even support working on multi-module projects if the mod supports
various mod loaders.

## Features
- Simple Gradle Extension configuration
- Integrated `multiCompile` gradle task
- Swap dependency versions for each Minecraft version
- Automatically select the correct code for the Minecraft version
- Simple annotations for marking version-specific code
- Automatic merging of jars for code-identical versions
- Support for common subprojects
- Swap Minecraft versions during development

## Usage
Include the plugin in the Gradle project in `build.gradle`:

```groovy
import com.github.tatercertified.MCBuildConfig
import com.github.tatercertified.utils.DependencyBuilder
import com.github.tatercertified.utils.MCGradleBuilder
import java.nio.file.Paths

buildscript() {
    repositories {
        maven {
            url 'https://jitpack.io'
        }
    }
    dependencies {
        classpath 'com.github.Tater-Certified:MultiMCGradle:1.0.0-beta.12'
    }
}

apply plugin: 'com.github.tatercertified.multimc'

multimc {
    // The actively developed version
    currentMinecraftVer = "1.21.7"
    // If future Minecraft versions should be supported (default: false)
    futureCompatible = true
    // Path where jars should be placed
    outputDir = Paths.get("outputFolder")
    // The name of a variable in gradle.properties
    modConfigFileRelativePath = "configPath"
    // The loader name and where the subproject directory is located
    loaderSpecificPaths = ["fabric": Paths.get("fabricFolder"), "neoforge": Paths.get("neoforgeFolder")]
    // The paths to the common code subprojects (default: None)
    commonDirs = [Paths.get("commonFolder1"), Paths.get("commonFolder2")]
    // Specifies how to handle gradle properties between versions
    gradleConfig = new MCBuildConfig((MCGradleBuilder builder) -> {
        builder.mcVer("1.21.5", (DependencyBuilder depBuilder) ->
                depBuilder.dep("fabric-api", "0.129.0+1.21.7")
        )
        builder.mcVer("1.21.6", (DependencyBuilder depBuilder) ->
                depBuilder.depCopyAll("1.21.5")
        )
        builder.mcVer("1.21.7", (DependencyBuilder depBuilder) -> {
            depBuilder.depCopy("fabric-api", "1.21.6")
            depBuilder.depExclude("polymer")
        })
    })
}
```
In `settings.gradle`, add:
```groovy
pluginManagement {
    repositories {
        maven { url 'https://jitpack.io' }
        mavenCentral()
        gradlePluginPortal()
    }
}
```

In the submodules' `gradle.properties`, include the mod configuration path (inside a compiled jar):
```properties
# For Fabric
modConfig=fabric.mod.json
```
```properties
# For (Neo)Forge
modConfig=META-INF/mods.toml
```

To declare code as being Minecraft version specific, simple comment out the code and tag it:

```java
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
/*\ <1.21.7
import java.util.concurrent.atomic.AtomicInteger;
\END */

//: >=1.21.7
public Executor getExecutor(int threads) {
    return Executors.newFixedThreadPool(threads);
}
//: END

/*\ <1.21.7
public Executor getExecutor(AtomicInteger threads) {
    return Executors.newFixedThreadPool(threads.get() * 2);
}
\END */
```
<p>

To compile for all versions, run `gradlew multiCompile`<p>
To switch the current development version, set the `currentMinecraftVer` variable and run `gradlew switchMCVersion`
**Note:** The `%mcVer%` field in mod config files (such as fabric.mod.json) will **not** be changed. Those must be changed
manually before running the Minecraft server/client in the development environment.

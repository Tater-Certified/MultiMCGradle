# MultiMC Gradle
### A multiversion compilation and programming solution for Minecraft modding projects
-- -
## About
MultiMC Gradle provides a simple way to create a single mod compatible with various versions of Minecraft. 
This Gradle plugin is loader-independent and can even support working on multi-module projects if the mod supports
various mod loaders.

## Features
- Simple Gradle Extension configuration
- Integrated `multi-compile` gradle task
- Swap dependency versions for each Minecraft version
- Automatically select the correct code for the Minecraft version
- Simple annotations for marking version-specific code
- Automatic merging of jars for code-identical versions

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
        classpath 'com.github.Tater-Certified:MultiMCGradle:1.0.0-beta.3'
    }
}

apply plugin: 'com.github.tatercertified.multimc'

multimc {
    // If future Minecraft versions should be supported (default: false)
    futureCompatible = true
    // Path where jars should be placed
    outputDir = Paths.get("outputFolder")
    // The name of a variable in gradle.properties
    modConfigFileRelativePath = "configPath"
    // The loader name and where the subproject directory is located
    loaderSpecificPaths = ["fabric": Paths.get("fabricFolder"), "neoforge": Paths.get("neoforgeFolder")]
    // Specifies how to handle gradle properties between versions
    gradleConfig = new MCBuildConfig((MCGradleBuilder builder) -> {
        builder.mcVer("1.21.5", (DependencyBuilder depBuilder) ->
                depBuilder.dep("fabric-api", "0.129.0+1.21.7")
        )
        builder.mcVer("1.21.6", (DependencyBuilder depBuilder) ->
                depBuilder.copyAll("1.21.5")
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
        maven { url = 'https://jitpack.io' }
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

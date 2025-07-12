package com.github.tatercertified.multimctest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Multimctest implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("TEST");

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(minecraftServer -> {
            //: >=1.21.6
            LOGGER.info("This is 1.21.6 or newer");
            //: END

            /*\ <=1.21.5
            LOGGER.info("This is 1.21.5 or newer");
            \END */

            LOGGER.info("I'll fire regardless of the version!");
        });
    }
}

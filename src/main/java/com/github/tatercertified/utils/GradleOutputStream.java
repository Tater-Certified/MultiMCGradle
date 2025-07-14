package com.github.tatercertified.utils;

import org.gradle.api.Project;
import org.slf4j.Logger;

import java.io.OutputStream;

public class GradleOutputStream extends OutputStream {
    private final Logger logger;
    private final boolean isError;
    private final StringBuilder buffer = new StringBuilder();

    public GradleOutputStream(Project project, boolean isError) {
        this.logger = project.getLogger();
        this.isError = isError;
    }

    @Override
    public void write(int b) {
        if (b == '\n') {
            flush();
        } else {
            buffer.append((char) b);
        }
    }

    @Override
    public void flush() {
        if (!buffer.isEmpty()) {
            if (isError) {
                logger.error(buffer.toString());
            } else {
                logger.info(buffer.toString());
            }
            buffer.setLength(0);
        }
    }
}

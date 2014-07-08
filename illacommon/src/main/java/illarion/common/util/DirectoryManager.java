/*
 * This file is part of the Illarion project.
 *
 * Copyright © 2014 - Illarion e.V.
 *
 * Illarion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Illarion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package illarion.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class is used to manage the global directory manager that takes care for the directories the applications need
 * to use.
 *
 * @author Martin Karing &lt;nitram@illarion.org&gt;
 */
public final class DirectoryManager {
    private static final Logger log = LoggerFactory.getLogger(DirectoryManager.class);

    /**
     * The enumeration of directories that are managed by this manager.
     */
    public enum Directory {
        /**
         * The user directory that stores the user related data like log files, character data and settings.
         */
        User,

        /**
         * The data directory that stores the application binary data required to launch the applications.
         */
        Data
    }

    /**
     * The singleton instance of this class.
     */
    @Nonnull
    private static final DirectoryManager INSTANCE = new DirectoryManager();

    /**
     * The detected working directory.
     */
    @Nonnull
    private final Path workingDirectory;

    /**
     * Private constructor to ensure that only the singleton instance exists.
     */
    @SuppressWarnings("nls")
    private DirectoryManager() {
        String installationDir = System.getProperty("org.illarion.install.dir");
        workingDirectory = Paths.get((installationDir == null) ? "." : installationDir);

        Path userDir = getDirectory(Directory.User);
        if (Files.isRegularFile(userDir)) {
            try {
                Files.delete(userDir);
            } catch (IOException e) {
                log.error("Failed to delete old .illarion file.", e);
            }
        }
    }

    /**
     * Get the singleton instance of this class.
     *
     * @return the singleton instance
     */
    @Nonnull
    public static DirectoryManager getInstance() {
        return INSTANCE;
    }

    /**
     * Get the location of the specified directory in the local file system.
     *
     * @param dir the directory
     * @return the location of the directory in the local file system or {@code null} in case the directory is not set
     */
    @Nonnull
    public Path getDirectory(@Nonnull Directory dir) {
        switch (dir) {
            case User:
                return Paths.get(System.getProperty("user.home"), ".illarion");
            case Data:
                return getBinaryDirectory();
        }
        throw new IllegalArgumentException("Parameter 'dir' was set to an illegal value: " + dir);
    }

    private Path getBinaryDirectory() {
        Path firstChoice = workingDirectory.resolve("bin");
        if (!Files.exists(firstChoice)) {
            try {
                return Files.createDirectories(firstChoice);
            } catch (IOException ignored) {
                // not accessible
            }
        }
        if (Files.isDirectory(firstChoice) && Files.isWritable(firstChoice) && Files.isReadable(firstChoice)) {
            return firstChoice;
        }
        return getDirectory(Directory.User).resolve("bin");
    }

    @Nonnull
    public Path resolveFile(@Nonnull Directory dir, @Nonnull String... segments) {
        Path result = getDirectory(dir);
        for (String segment : segments) {
            result = result.resolve(segment);
        }
        return result;
    }

    /**
     * In case the directory manager supports relative directories, this is the working directory the client needs to
     * be launched in.
     *
     * @return the working directory or {@code null} in case none is supported
     */
    @Nonnull
    public Path getWorkingDirectory() {
        return workingDirectory;
    }
}

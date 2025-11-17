package org.maze.infrastructure.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * File system utilities for the maze server infrastructure.
 * Handles file operations, path resolution, and resource loading.
 */
public class FileUtils {

	private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

	// Prevent instantiation
	private FileUtils() {
		throw new AssertionError("Utility class - do not instantiate");
	}

	/**
	 * Returns a list of file names matching the given pattern.
	 *
	 * @param pattern path that may include wildcards (*)
	 * @return set of absolute file paths matching the pattern
	 * @throws IOException if directory access fails
	 */
	public static Set<String> listFiles(String pattern) throws IOException {
		boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

		// Detect if the pattern contains wildcards
		boolean hasWildcards = pattern.contains("*") || pattern.contains("?");

		// Fast path: direct file
		if (!hasWildcards) {
			Path p = Paths.get(pattern);
			if (Files.isRegularFile(p)) {
				return Collections.singleton(p.toAbsolutePath().toString());
			}
			return Collections.emptySet();
		}

		// Separate directory part and wildcard part
		int sepIndex = Math.max(pattern.lastIndexOf('/'), pattern.lastIndexOf('\\'));
		String dirPart = sepIndex >= 0 ? pattern.substring(0, sepIndex) : ".";
		String globPart = sepIndex >= 0 ? pattern.substring(sepIndex + 1) : pattern;

		Path baseDir = Paths.get(dirPart).toAbsolutePath().normalize();
		if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
			return Collections.emptySet();
		}

		if (isWindows) {
			globPart = globPart.replace("\\", "\\\\");
		}

		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPart);

		Set<String> results = new HashSet<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir)) {
			for (Path entry : stream) {
				Path fileName = entry.getFileName();
				if (fileName != null && matcher.matches(fileName)) {
					results.add(entry.toAbsolutePath().toString());
				}
			}
		}

		return results;
	}

	/**
	 * Determines the common prefix of two {@link Path}s. The {@link Path}s must
	 * have a common root.
	 *
	 * @param absoluteOne   one of the paths. Must be absolute.
	 * @param absoluteOther the other path. Must also be absolute.
	 * @return the common prefix, or null if the {@link Path}s do not have a common
	 *         root
	 * @throws IllegalArgumentException if the paths are not absolute.
	 */
	public static Path getCommonPrefix(Path absoluteOne, Path absoluteOther) {

		if (!absoluteOne.isAbsolute() || !absoluteOther.isAbsolute()) {
			throw new IllegalArgumentException("Must supply absolute Paths");
		}

		if (!absoluteOne.getRoot().equals(absoluteOther.getRoot())) {
			log.warn("No common root found between {} and {}.", absoluteOne, absoluteOther);
			return Paths.get("");
		}

		Path subOne, subOther;

		// Traversing the paths from root in parallel
		int i = 0;
		do {
			++i;
			try {
				subOne = absoluteOne.subpath(0, i);
				subOther = absoluteOther.subpath(0, i);
			} catch (IllegalArgumentException e) {
				// i too large for one of the paths, which is OK
				break;
			}
		} while (subOne.equals(subOther));
		// we need to resolve against root, as the subpath comes without root.
		return absoluteOne.getRoot().resolve(absoluteOne.subpath(0, i - 1));
	}

	/**
	 * Creates subdirectories included in path if these do not exist (prior to
	 * writing files at the end of that path)
	 *
	 * @param pattern path that may include wildcards (in which case, no
	 *                subdirectory is created)
	 *                or format specifiers (%s, %d, ...)
	 */
	public static void makePath(String pattern) {
		if (pattern == null)
			return;

		int i = pattern.lastIndexOf("/");

		if (i >= 0) {
			String head = pattern.substring(0, i);
			new File(head).mkdirs();
		}
	}


    /**
     * Opens a file from the file system or classpath resource.
     * Uses the provided ClassLoader to search for resources.
     * 
     * @param filename path to file or resource
     * @param classLoader the ClassLoader to use for resource loading
     * @return input stream to the file or resource
     * @throws IOException if file/resource cannot be found
     */
    public static InputStream getFileOrResource(String filename, ClassLoader classLoader) throws IOException {
        Path filePath = Paths.get(filename);
        if (Files.exists(filePath)) {
            return Files.newInputStream(filePath);
        }
        // Try as classpath resource
        InputStream resourceStream = classLoader.getResourceAsStream(filename);
        if (resourceStream != null) {
            return resourceStream;
        }
        throw new IOException("File or resource not found: " + filename);
    }

	/**
	 * Buffers the content of an input stream into a string.
	 *
	 * @param is the input stream
	 * @return the content of the stream buffered into a string
	 * @throws IOException if stream reading fails
	 */
	public static String asString(InputStream is) throws IOException {
		StringWriter w = new StringWriter();

		int buf = -1;
		while ((buf = is.read()) > -1)
			w.write(buf);

		return w.toString();
	}

}

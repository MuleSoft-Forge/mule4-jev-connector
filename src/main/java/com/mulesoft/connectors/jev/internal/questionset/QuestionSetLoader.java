package com.mulesoft.connectors.jev.internal.questionset;

import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Loads question-set files from the classpath. A file lives under the configured folder (default {@code questions/})
 * and is referenced by its file name, e.g. {@code ticket-triage.json}. The loader is used both at runtime (to resolve
 * the {@code questionSet} parameter into questions) and at design time (to list the folder for the value provider and
 * to build the DataSense output type).
 *
 * <p>
 * Listing walks whichever form the classpath entry takes — an exploded directory during development, or a jar entry
 * once the app is packaged — so the value provider works in both Studio and a deployed app.
 */
public final class QuestionSetLoader {

  private QuestionSetLoader() {
  }

  /**
   * Resolves {@code name} against {@code location} on the classpath and parses it.
   *
   * @param location
   *          the classpath folder, e.g. {@code questions/}; a missing trailing slash is tolerated.
   * @param name
   *          the file name, e.g. {@code ticket-triage.json}; a missing {@code .json} suffix is tolerated.
   * @return the parsed question set.
   * @throws ModuleException
   *           {@code JEV:INVALID_QUESTION_SET} when the file is absent or not valid JSON.
   */
  public static QuestionSet load(String location, String name) {
    String resource = resourcePath(location, name);
    try (InputStream in = classLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new ModuleException("No question-set file '" + resource + "' on the classpath",
            JevErrorType.INVALID_QUESTION_SET);
      }
      JsonNode root = Json.read(in);
      return parse(fileId(name), root);
    } catch (IOException e) {
      throw new ModuleException("Could not read question-set file '" + resource + "'",
          JevErrorType.INVALID_QUESTION_SET, e);
    }
  }

  /**
   * Parses an already-read question-set document. The {@code fallbackId} is used when the file declares no {@code id}.
   */
  public static QuestionSet parse(String fallbackId, JsonNode root) {
    if (root == null || !root.isObject()) {
      throw new ModuleException("A question-set file must be a JSON object", JevErrorType.INVALID_QUESTION_SET);
    }
    JsonNode questions = root.get("questions");
    if (questions == null || !questions.isObject() || questions.isEmpty()) {
      throw new ModuleException("A question-set file must have a non-empty 'questions' object",
          JevErrorType.INVALID_QUESTION_SET);
    }
    String id = root.path("id").asText(fallbackId);
    String version = root.hasNonNull("version") ? root.get("version").asText() : null;
    String description = root.hasNonNull("description") ? root.get("description").asText() : null;
    ObjectNode policy = root.get("policy") instanceof ObjectNode ? (ObjectNode) root.get("policy") : null;
    return new QuestionSet(id, version, description, (ObjectNode) questions, policy);
  }

  /**
   * Lists the question-set file names under {@code location}. Returns file names (with the {@code .json} suffix),
   * sorted, best-effort: an unreadable or absent folder yields an empty set rather than an error, so design-time
   * editing never breaks on a packaging quirk.
   */
  public static TreeSet<String> list(String location) {
    TreeSet<String> names = new TreeSet<>();
    String folder = normalizeFolder(location);
    try {
      Enumeration<URL> urls = classLoader().getResources(folder);
      while (urls.hasMoreElements()) {
        collect(urls.nextElement(), folder, names);
      }
    } catch (IOException ignored) {
      // Fall through to whatever was collected; listing is advisory.
    }
    return names;
  }

  private static void collect(URL url, String folder, TreeSet<String> names) {
    try {
      URLConnection connection = url.openConnection();
      if (connection instanceof JarURLConnection) {
        collectFromJar((JarURLConnection) connection, folder, names);
      } else if ("file".equals(url.getProtocol())) {
        collectFromDirectory(Paths.get(url.toURI()), names);
      }
    } catch (Exception ignored) {
      // A single unreadable source must not abort the whole listing.
    }
  }

  private static void collectFromJar(JarURLConnection connection, String folder, TreeSet<String> names)
      throws IOException {
    try (JarFile jar = connection.getJarFile()) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        String entry = entries.nextElement().getName();
        if (entry.startsWith(folder) && entry.toLowerCase().endsWith(".json")) {
          String name = entry.substring(folder.length());
          if (!name.isEmpty() && !name.contains("/")) {
            names.add(name);
          }
        }
      }
    }
  }

  private static void collectFromDirectory(Path dir, TreeSet<String> names) throws IOException {
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (Stream<Path> files = Files.list(dir)) {
      files.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
          .filter(n -> n.toLowerCase().endsWith(".json")).forEach(names::add);
    }
  }

  private static String resourcePath(String location, String name) {
    return normalizeFolder(location) + fileName(name);
  }

  private static String fileName(String name) {
    String trimmed = name == null ? "" : name.trim();
    return trimmed.toLowerCase().endsWith(".json") ? trimmed : trimmed + ".json";
  }

  private static String fileId(String name) {
    String file = fileName(name);
    int slash = file.lastIndexOf('/');
    String base = slash >= 0 ? file.substring(slash + 1) : file;
    return base.substring(0, base.length() - ".json".length());
  }

  private static String normalizeFolder(String location) {
    if (location == null || location.isBlank()) {
      return "questions/";
    }
    String trimmed = location.trim();
    return trimmed.endsWith("/") ? trimmed : trimmed + "/";
  }

  private static ClassLoader classLoader() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    return loader != null ? loader : QuestionSetLoader.class.getClassLoader();
  }
}

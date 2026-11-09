package com.example.email;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/** Serves the public docs from a fixed list of paths, never a file chosen by the request. */
final class DocsFiles {

    record DocsFile(String resource, String contentType) {
    }

    private static final String HTML = "text/html; charset=utf-8";
    private static final String JAVASCRIPT = "text/javascript; charset=utf-8";
    private static final String CSS = "text/css; charset=utf-8";
    private static final String YAML = "application/yaml; charset=utf-8";

    private static final String SWAGGER_UI = "META-INF/resources/webjars/swagger-ui/" + swaggerUiVersion() + "/";

    private static final Map<String, DocsFile> FILES = Map.of(
        "/docs", new DocsFile("docs/index.html", HTML),
        "/docs/swagger-initializer.js", new DocsFile("docs/swagger-initializer.js", JAVASCRIPT),
        "/docs/swagger-ui-bundle.js", new DocsFile(SWAGGER_UI + "swagger-ui-bundle.js", JAVASCRIPT),
        "/docs/swagger-ui.css", new DocsFile(SWAGGER_UI + "swagger-ui.css", CSS),
        "/openapi.yaml", new DocsFile("docs/openapi.yaml", YAML));

    private static final Map<String, String> CONTENTS = new ConcurrentHashMap<>();

    private DocsFiles() {
    }

    static Optional<DocsFile> find(String path) {
        return Optional.ofNullable(FILES.get(path));
    }

    static String read(DocsFile file) {
        return CONTENTS.computeIfAbsent(file.resource(), DocsFiles::load);
    }

    private static String load(String resource) {
        try (InputStream in = open(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String swaggerUiVersion() {
        try (InputStream in = open("docs/swagger-ui.properties")) {
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("swagger.ui.version");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream open(String resource) {
        InputStream in = DocsFiles.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing docs resource: " + resource);
        }
        return in;
    }
}

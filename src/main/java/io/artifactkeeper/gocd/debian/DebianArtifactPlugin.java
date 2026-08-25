package io.artifactkeeper.gocd.debian;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * GoCD Artifact extension (v2.0) plugin: publishes {@code .deb} files built
 * by a job to a hosted Debian repository on ArtifactKeeper, and lets a
 * downstream job fetch them back.
 */
@Extension
public class DebianArtifactPlugin implements GoPlugin {

    static final String EXTENSION_NAME = "artifact";
    static final List<String> SUPPORTED_API_VERSIONS = List.of("2.0");

    private static final Logger LOGGER = Logger.getLoggerFor(DebianArtifactPlugin.class);

    private static final String REQ_GET_ICON = "cd.go.artifact.get-icon";
    private static final String REQ_GET_CAPABILITIES = "cd.go.artifact.get-capabilities";

    private static final String REQ_STORE_GET_METADATA = "cd.go.artifact.store.get-metadata";
    private static final String REQ_STORE_GET_VIEW = "cd.go.artifact.store.get-view";
    private static final String REQ_STORE_VALIDATE = "cd.go.artifact.store.validate";

    private static final String REQ_PUBLISH_GET_METADATA = "cd.go.artifact.publish.get-metadata";
    private static final String REQ_PUBLISH_GET_VIEW = "cd.go.artifact.publish.get-view";
    private static final String REQ_PUBLISH_VALIDATE = "cd.go.artifact.publish.validate";
    private static final String REQ_PUBLISH_ARTIFACT = "cd.go.artifact.publish-artifact";

    private static final String REQ_FETCH_GET_METADATA = "cd.go.artifact.fetch.get-metadata";
    private static final String REQ_FETCH_GET_VIEW = "cd.go.artifact.fetch.get-view";
    // The published extension docs show this request reusing the literal
    // string "cd.go.artifact.publish.validate" in the fetch-config section;
    // every other pair (store/publish) is symmetrically named
    // "<scope>.validate", so that is treated as a documentation typo and the
    // symmetric name is implemented here. If a GoCD server ever proves that
    // wrong in practice, this is the line to change.
    private static final String REQ_FETCH_VALIDATE = "cd.go.artifact.fetch.validate";
    private static final String REQ_FETCH_ARTIFACT = "cd.go.artifact.fetch-artifact";

    private final ArtifactKeeperClient client = new ArtifactKeeperClient();
    private GoApplicationAccessor accessor;

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor accessor) {
        this.accessor = accessor;
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest request) throws UnhandledRequestTypeException {
        try {
            return switch (request.requestName()) {
                case REQ_GET_ICON -> getIcon();
                case REQ_GET_CAPABILITIES -> success("{}");

                case REQ_STORE_GET_METADATA -> metadataResponse(StoreConfig.METADATA);
                case REQ_STORE_GET_VIEW -> viewResponse("/store.template.html");
                case REQ_STORE_VALIDATE -> validateResponse(StoreConfig.validate(configMap(request)));

                case REQ_PUBLISH_GET_METADATA -> metadataResponse(PublishConfig.METADATA);
                case REQ_PUBLISH_GET_VIEW -> viewResponse("/publish.template.html");
                case REQ_PUBLISH_VALIDATE -> validateResponse(PublishConfig.validate(configMap(request)));
                case REQ_PUBLISH_ARTIFACT -> publishArtifact(request);

                case REQ_FETCH_GET_METADATA -> metadataResponse(FetchConfig.METADATA);
                case REQ_FETCH_GET_VIEW -> viewResponse("/fetch.template.html");
                case REQ_FETCH_VALIDATE -> validateResponse(FetchConfig.validate(configMap(request)));
                case REQ_FETCH_ARTIFACT -> fetchArtifact(request);

                default -> throw new UnhandledRequestTypeException(request.requestName());
            };
        } catch (UnhandledRequestTypeException e) {
            throw e;
        } catch (RuntimeException e) {
            LOGGER.warn("Request '" + request.requestName() + "' failed", e);
            return error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return new GoPluginIdentifier(EXTENSION_NAME, SUPPORTED_API_VERSIONS);
    }

    // -------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------

    private GoPluginApiResponse getIcon() {
        try (InputStream in = getClass().getResourceAsStream("/icon.svg")) {
            if (in == null) {
                return error("icon.svg missing from plugin jar");
            }
            String base64 = Base64.getEncoder().encodeToString(in.readAllBytes());
            JsonObject body = new JsonObject();
            body.addProperty("content_type", "image/svg+xml");
            body.addProperty("data", base64);
            return success(JsonUtil.GSON.toJson(body));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private GoPluginApiResponse publishArtifact(GoPluginApiRequest request) {
        JsonObject body = JsonUtil.GSON.fromJson(request.requestBody(), JsonObject.class);

        JsonObject storeJson = body.getAsJsonObject("artifact_store");
        StoreConfig store = StoreConfig.fromMap(asStringMap(storeJson.getAsJsonObject("configuration")));

        JsonObject planJson = body.getAsJsonObject("artifact_plan");
        PublishConfig publishConfig = PublishConfig.fromMap(asStringMap(planJson.getAsJsonObject("configuration")));

        Path workingDirectory = Path.of(body.get("agent_working_directory").getAsString());
        Consumer<String> log = consoleLogger();

        List<Path> files = ArtifactResolver.resolve(workingDirectory, publishConfig.pattern);
        List<PublishedFile> published = new ArrayList<>();
        for (Path file : files) {
            published.add(client.publish(store, publishConfig.component, file, log));
        }

        JsonObject metadata = new JsonObject();
        metadata.addProperty("component", publishConfig.component);
        JsonArray filesJson = new JsonArray();
        for (PublishedFile pf : published) {
            JsonObject fileJson = new JsonObject();
            fileJson.addProperty("filename", pf.filename);
            fileJson.addProperty("packageName", pf.packageName);
            fileJson.addProperty("poolLetter", pf.poolLetter);
            fileJson.addProperty("url", pf.url);
            fileJson.addProperty("sha256", pf.sha256);
            fileJson.addProperty("sizeBytes", pf.sizeBytes);
            filesJson.add(fileJson);
        }
        metadata.add("files", filesJson);

        JsonObject response = new JsonObject();
        response.add("metadata", metadata);
        return success(JsonUtil.GSON.toJson(response));
    }

    private GoPluginApiResponse fetchArtifact(GoPluginApiRequest request) {
        JsonObject body = JsonUtil.GSON.fromJson(request.requestBody(), JsonObject.class);

        StoreConfig store = StoreConfig.fromMap(asStringMap(body.getAsJsonObject("store_configuration")));
        FetchConfig fetchConfig = FetchConfig.fromMap(asStringMap(body.getAsJsonObject("fetch_artifact_configuration")));
        JsonObject metadata = body.getAsJsonObject("artifact_metadata");
        Path workingDirectory = Path.of(body.get("agent_working_directory").getAsString());
        Path destDir = workingDirectory.resolve(fetchConfig.destinationOnAgent);
        Consumer<String> log = consoleLogger();

        for (var element : metadata.getAsJsonArray("files")) {
            JsonObject fileJson = element.getAsJsonObject();
            PublishedFile pf = new PublishedFile(
                    fileJson.get("filename").getAsString(),
                    fileJson.get("packageName").getAsString(),
                    fileJson.get("poolLetter").getAsString(),
                    fileJson.get("url").getAsString(),
                    fileJson.get("sha256").getAsString(),
                    fileJson.get("sizeBytes").getAsLong());
            client.fetch(store, pf, destDir, log);
        }

        return success("[]");
    }

    // -------------------------------------------------------------------
    // Response / request helpers
    // -------------------------------------------------------------------

    private Consumer<String> consoleLogger() {
        return new ConsoleLogger(accessor, pluginIdentifier());
    }

    private static Map<String, String> configMap(GoPluginApiRequest request) {
        return asStringMap(JsonUtil.GSON.fromJson(request.requestBody(), JsonObject.class));
    }

    private static Map<String, String> asStringMap(JsonObject object) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        if (object == null) {
            return map;
        }
        for (var entry : object.entrySet()) {
            if (!entry.getValue().isJsonNull()) {
                map.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return map;
    }

    private static GoPluginApiResponse metadataResponse(List<ConfigProperty> properties) {
        JsonArray array = new JsonArray();
        for (ConfigProperty property : properties) {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", property.key);
            JsonObject meta = new JsonObject();
            meta.addProperty("required", property.required);
            meta.addProperty("secure", property.secure);
            entry.add("metadata", meta);
            array.add(entry);
        }
        return success(JsonUtil.GSON.toJson(array));
    }

    private GoPluginApiResponse viewResponse(String templateResource) {
        try (InputStream in = getClass().getResourceAsStream(templateResource)) {
            if (in == null) {
                return error(templateResource + " missing from plugin jar");
            }
            String template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject body = new JsonObject();
            body.addProperty("template", template);
            return success(JsonUtil.GSON.toJson(body));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static GoPluginApiResponse validateResponse(List<ValidationError> errors) {
        JsonArray array = new JsonArray();
        for (ValidationError error : errors) {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", error.key);
            entry.addProperty("message", error.message);
            array.add(entry);
        }
        return success(JsonUtil.GSON.toJson(array));
    }

    private static GoPluginApiResponse success(String body) {
        return DefaultGoPluginApiResponse.success(body);
    }

    private static GoPluginApiResponse error(String message) {
        JsonObject body = new JsonObject();
        body.addProperty("message", message);
        return DefaultGoPluginApiResponse.error(JsonUtil.GSON.toJson(body));
    }
}

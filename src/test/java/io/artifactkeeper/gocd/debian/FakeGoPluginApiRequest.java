package io.artifactkeeper.gocd.debian;

import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;

import java.util.Map;

final class FakeGoPluginApiRequest extends GoPluginApiRequest {

    private final String requestName;
    private final String requestBody;

    FakeGoPluginApiRequest(String requestName, String requestBody) {
        this.requestName = requestName;
        this.requestBody = requestBody;
    }

    @Override
    public String extension() {
        return DebianArtifactPlugin.EXTENSION_NAME;
    }

    @Override
    public String extensionVersion() {
        return "2.0";
    }

    @Override
    public String requestName() {
        return requestName;
    }

    @Override
    public Map<String, String> requestParameters() {
        return Map.of();
    }

    @Override
    public Map<String, String> requestHeaders() {
        return Map.of();
    }

    @Override
    public String requestBody() {
        return requestBody;
    }
}

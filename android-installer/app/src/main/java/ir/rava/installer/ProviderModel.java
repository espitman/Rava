package ir.rava.installer;

import java.util.Objects;

final class ProviderModel {
    final String providerId;
    final String modelId;
    final String displayName;

    ProviderModel(String providerId, String modelId, String displayName) {
        this.providerId = Objects.requireNonNull(providerId);
        this.modelId = Objects.requireNonNull(modelId);
        this.displayName = Objects.requireNonNull(displayName);
    }

    String archiveId() {
        return providerId + "/" + modelId;
    }

    @Override public String toString() {
        return displayName;
    }
}

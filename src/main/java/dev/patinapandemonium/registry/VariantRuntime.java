package dev.patinapandemonium.registry;

import java.util.Optional;

public class VariantRuntime {

    public static Optional<VariantData> next(VariantData data) {
        OxidationStage stage = data.waxed() ? null : data.stage().next();
        return stage == null ? Optional.empty() : Optional.of(data.withStage(stage));
    }

    public static Optional<VariantData> previous(VariantData data) {
        OxidationStage stage = data.stage().previous();
        return stage == null ? Optional.empty() : Optional.of(data.withStage(stage));
    }

    public static Optional<VariantData> waxed(VariantData data) {
        return data.waxed() ? Optional.empty() : Optional.of(data.withWaxed(true));
    }

    public static Optional<VariantData> unwaxed(VariantData data) {
        return data.waxed() ? Optional.of(data.withWaxed(false)) : Optional.empty();
    }

}
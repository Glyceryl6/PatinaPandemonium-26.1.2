package dev.patinapandemonium.data;

import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.resource.GeneratedPackWriter;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.server.packs.PackType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

abstract class AbstractGeneratedProvider implements DataProvider {

    protected final PackOutput output;
    private final PackType packType;

    AbstractGeneratedProvider(PackOutput output, PackType packType) {
        this.output = output;
        this.packType = packType;
    }

    protected abstract boolean accepts(String path);

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        try {
            for (var entry : GeneratedPackWriter.build(DynamicVariantRegistry.entries(), this.packType).entrySet()) {
                if (!this.accepts(entry.getKey())) continue;
                Path path = this.output.getOutputFolder().resolve(entry.getKey());
                if (Files.exists(path) && Arrays.equals(Files.readAllBytes(path), entry.getValue())) continue;
                Files.createDirectories(path.getParent());
                Files.write(path, entry.getValue());
            }
            return CompletableFuture.completedFuture(null);
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }

}
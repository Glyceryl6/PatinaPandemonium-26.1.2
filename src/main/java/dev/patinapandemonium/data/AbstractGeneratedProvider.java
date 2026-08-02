package dev.patinapandemonium.data;

import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.resource.GeneratedPackWriter;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

abstract class AbstractGeneratedProvider implements DataProvider {
    protected final PackOutput output;

    AbstractGeneratedProvider(PackOutput output) {
        this.output = output;
    }

    protected abstract boolean accepts(String path);

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        try {
            for (var e : GeneratedPackWriter.build(DynamicVariantRegistry.entries()).entrySet())
                if (accepts(e.getKey())) {
                    Path p = output.getOutputFolder().resolve(e.getKey());
                    Files.createDirectories(p.getParent());
                    Files.write(p, e.getValue());
                }
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}

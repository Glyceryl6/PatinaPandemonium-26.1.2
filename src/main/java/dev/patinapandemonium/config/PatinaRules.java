package dev.patinapandemonium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.patinapandemonium.PatinaPandemonium;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class PatinaRules {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve(PatinaPandemonium.MOD_ID + "-rules.json");

    public static final PatinaRules INSTANCE = load();

    public int schemaVersion = 5;
    public Set<String> excludedNamespaces = new HashSet<>();
    public Set<String> excludedBlocks = new HashSet<>();
    public int warningGeneratedBlocks = 200_000;
    public int warningGeneratedBlockStates = 5_000_000;
    public boolean memoryAwareRegistrationWarning = true;
    public int estimatedBytesPerGeneratedBlock = 16_384;
    public int estimatedBytesPerGeneratedBlockState = 4_096;
    public double warningGeneratedHeapFraction = 0.60D;
    public boolean abortWhenRegistrationEstimateExceedsWarnings = false;
    public boolean registerEverySupportedVariant = true;
    public boolean enableOptionalRunDataExport = false;
    public int maximumCreativeTabItems = 0;
    public int maximumCachedModelParts = 512;
    public int maximumCachedItemQuads = 2_048;
    public boolean dyedVariants = true;
    public boolean slabs = true;
    public boolean stairs = true;
    public boolean walls = true;
    public boolean fences = true;
    public boolean fenceGates = true;
    public boolean carpets = true;
    public boolean buttons = true;
    public boolean pressurePlates = true;
    public JsonObject textureOverrides = new JsonObject();
    public JsonObject existingFormOverrides = new JsonObject();

    public boolean namespaceAllowed(String namespace) {
        return !namespace.equals(PatinaPandemonium.MOD_ID)
            && (this.excludedNamespaces == null || !this.excludedNamespaces.contains(namespace));
    }

    private static PatinaRules load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.exists(FILE)) {
                JsonObject source = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
                PatinaRules loaded = GSON.fromJson(source, PatinaRules.class);
                PatinaRules rules = loaded == null ? new PatinaRules() : loaded;
                PatinaRules defaults = new PatinaRules();
                if (!source.has("warningGeneratedBlocks")) {
                    rules.warningGeneratedBlocks = source.has("maximumGeneratedBlocks")
                        ? Math.max(0, source.get("maximumGeneratedBlocks").getAsInt())
                        : defaults.warningGeneratedBlocks;
                }
                if (!source.has("warningGeneratedBlockStates")) {
                    rules.warningGeneratedBlockStates = source.has("maximumGeneratedBlockStates")
                        ? Math.max(0, source.get("maximumGeneratedBlockStates").getAsInt())
                        : defaults.warningGeneratedBlockStates;
                }
                if (!source.has("memoryAwareRegistrationWarning")) {
                    rules.memoryAwareRegistrationWarning = !source.has("memoryAwareBlockStateLimit")
                        || source.get("memoryAwareBlockStateLimit").getAsBoolean();
                }
                if (!source.has("warningGeneratedHeapFraction")) {
                    rules.warningGeneratedHeapFraction = source.has("maximumGeneratedHeapFraction")
                        ? source.get("maximumGeneratedHeapFraction").getAsDouble()
                        : defaults.warningGeneratedHeapFraction;
                }
                if (!source.has("abortWhenRegistrationEstimateExceedsWarnings")) {
                    rules.abortWhenRegistrationEstimateExceedsWarnings = defaults.abortWhenRegistrationEstimateExceedsWarnings;
                }
                if (!source.has("registerEverySupportedVariant")) {
                    rules.registerEverySupportedVariant = defaults.registerEverySupportedVariant;
                }
                if (!source.has("enableOptionalRunDataExport")) {
                    rules.enableOptionalRunDataExport = defaults.enableOptionalRunDataExport;
                }
                if (!source.has("maximumCreativeTabItems")) {
                    rules.maximumCreativeTabItems = defaults.maximumCreativeTabItems;
                }
                if (!source.has("schemaVersion") && rules.estimatedBytesPerGeneratedBlockState == 32_768) {
                    rules.estimatedBytesPerGeneratedBlockState = defaults.estimatedBytesPerGeneratedBlockState;
                }
                rules.schemaVersion = defaults.schemaVersion;
                Files.writeString(FILE, GSON.toJson(rules));
                return rules;
            }
            PatinaRules rules = new PatinaRules();
            Files.writeString(FILE, GSON.toJson(rules));
            return rules;
        } catch (IOException | RuntimeException error) {
            LOGGER.error("Could not load Patina Pandemonium rules; defaults will be used", error);
            return new PatinaRules();
        }
    }

}

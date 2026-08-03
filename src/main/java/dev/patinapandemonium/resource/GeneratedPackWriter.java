package dev.patinapandemonium.resource;

import com.google.gson.*;
import dev.patinapandemonium.PatinaPandemonium;
import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantEntry;
import dev.patinapandemonium.registry.VariantForm;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A single deterministic writer is shared by runData and the runtime compatibility packs.
 * The returned map intentionally has no pack.mcmeta. RuntimePack exposes these resources through
 * an in-memory pack, while runData can still export a selected pack type for inspection.
 */
public class GeneratedPackWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Map<String, byte[]> build(List<VariantEntry> entries, @Nullable PackType packType) {
        boolean clientResources = packType == null || packType == PackType.CLIENT_RESOURCES;
        boolean serverData = packType == null || packType == PackType.SERVER_DATA;
        Map<String, byte[]> output = new TreeMap<>();
        Map<String, VariantEntry> lookup = new HashMap<>();
        Map<Identifier, AssetResolver.ModelInfo> models = new HashMap<>();
        Set<String> writtenTextures = new HashSet<>();

        for (VariantEntry entry : entries) {
            lookup.put(key(entry.data()), entry);
        }

        for (VariantEntry entry : entries) {
            if (!entry.generated()) {
                continue;
            }
            if (clientResources) {
                AssetResolver.ModelInfo model = models.computeIfAbsent(entry.data().sourceId(), AssetResolver::resolve);
                writeAssets(output, entry, model, writtenTextures);
            }
            if (serverData) {
                writeLoot(output, entry);
                writeRecipes(output, entry, lookup);
            }
        }

        if (serverData) {
            writeDataMaps(output, entries, lookup);
            writeTags(output, entries);
            writeManifest(output, entries);
        }
        return output;
    }

    private static void writeAssets(
            Map<String, byte[]> output,
            VariantEntry entry,
            AssetResolver.ModelInfo modelInfo,
            Set<String> writtenTextures
    ) {
        Identifier blockId = entry.blockId();
        VariantData data = entry.data();
        String namespace = blockId.getNamespace();
        String path = blockId.getPath();
        int stage = data.stage().ordinal();

        LinkedHashMap<String, String> textures = new LinkedHashMap<>();
        for (Map.Entry<String, Identifier> textureEntry : modelInfo.textures().entrySet()) {
            Identifier sourceTexture = textureEntry.getValue();
            String generatedTexture = "generated/" + data.sourceId().getNamespace() + "/"
                    + data.sourceId().getPath() + "/" + data.stage().id() + "/" + sanitize(textureEntry.getKey());
            String generatedFile = "assets/" + PatinaPandemonium.MOD_ID + "/textures/" + generatedTexture + ".png";
            if (stage > 0 && writtenTextures.add(generatedFile)) {
                byte[] png = AssetResolver.tinted(sourceTexture, stage);
                if (png != null) {
                    output.put(generatedFile, png);
                    byte[] metadata = AssetResolver.textureMetadata(sourceTexture);
                    if (metadata != null) {
                        output.put(generatedFile + ".mcmeta", metadata);
                    }
                }
            }

            String selected = stage > 0 && output.containsKey(generatedFile)
                    ? PatinaPandemonium.MOD_ID + ":" + generatedTexture
                    : sourceTexture.toString();
            textures.put(textureEntry.getKey(), selected);
        }

        Identifier sourcePrimary = AssetResolver.primaryTexture(modelInfo, data.sourceId());
        String primary = choosePrimaryTexture(textures, sourcePrimary.toString());
        String modelPath = "block/" + path;

        switch (data.form()) {
            case FULL -> full(output, namespace, path, modelInfo, textures);
            case SLAB -> slab(output, namespace, path, primary);
            case STAIRS -> stairs(output, namespace, path, primary);
            case WALL -> wall(output, namespace, path, primary);
            case FENCE -> fence(output, namespace, path, primary);
            case FENCE_GATE -> fenceGate(output, namespace, path, primary);
            case BUTTON -> button(output, namespace, path, primary);
            case PRESSURE_PLATE -> pressurePlate(output, namespace, path, primary);
        }

        String inventoryModel = switch (data.form()) {
            case FULL, SLAB, STAIRS, PRESSURE_PLATE -> modelPath;
            default -> modelPath + "_inventory";
        };

        putJson(output, "assets/" + namespace + "/items/" + path + ".json",
                Map.of("model", Map.of("type", "minecraft:model", "model", namespace + ":" + inventoryModel)));
    }

    private static void full(
            Map<String, byte[]> output,
            String namespace,
            String path,
            AssetResolver.ModelInfo modelInfo,
            LinkedHashMap<String, String> textures
    ) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("parent", modelInfo.model() == null ? "minecraft:block/cube_all" : modelInfo.model().toString());
        model.put("textures", textures);
        putJson(output, "assets/" + namespace + "/models/block/" + path + ".json", model);
        simpleBlockState(output, namespace, path, "block/" + path);
    }

    private static void slab(Map<String, byte[]> output, String namespace, String path, String texture) {
        templateModel(output, namespace, path, "minecraft:block/slab", texture);
        templateModel(output, namespace, path + "_top", "minecraft:block/slab_top", texture);
        templateModel(output, namespace, path + "_double", "minecraft:block/cube_all", texture);
        putJson(output, "assets/" + namespace + "/blockstates/" + path + ".json", Map.of(
                "variants", Map.of(
                        "type=bottom", Map.of("model", namespace + ":block/" + path),
                        "type=top", Map.of("model", namespace + ":block/" + path + "_top"),
                        "type=double", Map.of("model", namespace + ":block/" + path + "_double"))));
    }

    private static void stairs(Map<String, byte[]> output, String namespace, String path, String texture) {
        templateModel(output, namespace, path, "minecraft:block/stairs", texture);
        templateModel(output, namespace, path + "_inner", "minecraft:block/inner_stairs", texture);
        templateModel(output, namespace, path + "_outer", "minecraft:block/outer_stairs", texture);
        JsonObject variants = new JsonObject();
        String[] facings = {"north", "east", "south", "west"};
        int[] baseRotations = {0, 90, 180, 270};
        for (int facingIndex = 0; facingIndex < facings.length; facingIndex++) {
            for (String half : new String[]{"bottom", "top"}) {
                for (String shape : new String[]{"straight", "inner_left", "inner_right", "outer_left", "outer_right"}) {
                    JsonObject variant = new JsonObject();
                    String suffix = shape.equals("straight") ? "" : shape.startsWith("inner") ? "_inner" : "_outer";
                    variant.addProperty("model", namespace + ":block/" + path + suffix);
                    int rotation = baseRotations[facingIndex];
                    if (shape.endsWith("left")) {
                        rotation -= 90;
                    }
                    rotation = Math.floorMod(rotation, 360);
                    if (half.equals("top")) {
                        variant.addProperty("x", 180);
                        variant.addProperty("uvlock", true);
                    }
                    if (rotation != 0) {
                        variant.addProperty("y", rotation);
                    }
                    variants.add("facing=" + facings[facingIndex] + ",half=" + half + ",shape=" + shape, variant);
                }
            }
        }

        JsonObject root = new JsonObject();
        root.add("variants", variants);
        putJson(output, "assets/" + namespace + "/blockstates/" + path + ".json", root);
    }

    private static void wall(Map<String, byte[]> output, String namespace, String path, String texture) {
        templateModel(output, namespace, path + "_post", "minecraft:block/template_wall_post", texture);
        templateModel(output, namespace, path + "_side", "minecraft:block/template_wall_side", texture);
        templateModel(output, namespace, path + "_side_tall", "minecraft:block/template_wall_side_tall", texture);
        templateModel(output, namespace, path + "_inventory", "minecraft:block/wall_inventory", texture);
        List<Object> multipart = new ArrayList<>();
        multipart.add(part("up", "true", namespace + ":block/" + path + "_post", 0));
        for (int index = 0; index < 4; index++) {
            String direction = new String[]{"north", "east", "south", "west"}[index];
            int rotation = index * 90;
            multipart.add(part(direction, "low", namespace + ":block/" + path + "_side", rotation));
            multipart.add(part(direction, "tall", namespace + ":block/" + path + "_side_tall", rotation));
        }

        putJson(output, "assets/" + namespace + "/blockstates/" + path + ".json", Map.of("multipart", multipart));
    }

    private static Map<String, Object> part(String property, String value, String model, int rotation) {
        Map<String, Object> apply = new LinkedHashMap<>();
        apply.put("model", model);
        if (rotation != 0) {
            apply.put("y", rotation);
        }
        apply.put("uvlock", true);
        return Map.of("when", Map.of(property, value), "apply", apply);
    }

    private static void fence(Map<String, byte[]> output, String namespace, String path, String texture) {
        templateModel(output, namespace, path + "_post", "minecraft:block/fence_post", texture);
        templateModel(output, namespace, path + "_side", "minecraft:block/fence_side", texture);
        templateModel(output, namespace, path + "_inventory", "minecraft:block/fence_inventory", texture);
        List<Object> multipart = new ArrayList<>();
        multipart.add(Map.of("apply", Map.of("model", namespace + ":block/" + path + "_post")));
        for (int index = 0; index < 4; index++) {
            String direction = new String[]{"north", "east", "south", "west"}[index];
            Map<String, Object> apply = new LinkedHashMap<>();
            apply.put("model", namespace + ":block/" + path + "_side");
            if (index > 0) {
                apply.put("y", index * 90);
            }
            apply.put("uvlock", true);
            multipart.add(Map.of("when", Map.of(direction, "true"), "apply", apply));
        }

        putJson(output, "assets/" + namespace + "/blockstates/" + path + ".json", Map.of("multipart", multipart));
    }

    private static void fenceGate(Map<String, byte[]> output, String namespace, String path, String texture) {
        templateModel(output, namespace, path, "minecraft:block/template_fence_gate", texture);
        templateModel(output, namespace, path + "_open", "minecraft:block/template_fence_gate_open", texture);
        templateModel(output, namespace, path + "_wall", "minecraft:block/template_fence_gate_wall", texture);
        templateModel(output, namespace, path + "_wall_open", "minecraft:block/template_fence_gate_wall_open", texture);
        templateModel(output, namespace, path + "_inventory", "minecraft:block/template_fence_gate", texture);
        JsonObject variants = new JsonObject();
        for (String facing : new String[]{"north", "east", "south", "west"}) {
            for (boolean open : new boolean[]{false, true}) {
                for (boolean inWall : new boolean[]{false, true}) {
                    JsonObject variant = new JsonObject();
                    String suffix = (inWall ? "_wall" : "") + (open ? "_open" : "");
                    variant.addProperty("model", namespace + ":block/" + path + suffix);
                    int rotation = switch (facing) {
                        case "east" -> 90;
                        case "south" -> 180;
                        case "west" -> 270;
                        default -> 0;
                    };
                    if (rotation != 0) {
                        variant.addProperty("y", rotation);
                    }
                    variant.addProperty("uvlock", true);
                    variants.add("facing=" + facing + ",in_wall=" + inWall + ",open=" + open, variant);
                }
            }
        }

        JsonObject root = new JsonObject();
        root.add("variants", variants);
        putJson(output, "assets/" + namespace + "/blockstates/" + path + ".json", root);
    }

    private static void button(Map<String, byte[]> output, String namespace, String path, String texture) {
        templateModel(output, namespace, path, "minecraft:block/button", texture);
        templateModel(output, namespace, path + "_pressed", "minecraft:block/button_pressed", texture);
        templateModel(output, namespace, path + "_inventory", "minecraft:block/button_inventory", texture);

        JsonObject variants = new JsonObject();
        for (String face : new String[]{"floor", "wall", "ceiling"}) {
            for (String facing : new String[]{"north", "east", "south", "west"}) {
                for (boolean powered : new boolean[]{false, true}) {
                    JsonObject variant = new JsonObject();
                    variant.addProperty("model", namespace + ":block/" + path + (powered ? "_pressed" : ""));
                    int x = face.equals("wall") ? 90 : face.equals("ceiling") ? 180 : 0;
                    int y = switch (facing) {
                        case "east" -> 90;
                        case "south" -> 180;
                        case "west" -> 270;
                        default -> 0;
                    };
                    if (x != 0) {
                        variant.addProperty("x", x);
                    }
                    if (y != 0) {
                        variant.addProperty("y", y);
                    }
                    variant.addProperty("uvlock", true);
                    variants.add("face=" + face + ",facing=" + facing + ",powered=" + powered, variant);
                }
            }
        }

        JsonObject root = new JsonObject();
        root.add("variants", variants);
        putJson(output, "assets/" + namespace + "/blockstates/" + path + ".json", root);
    }

    private static void pressurePlate(Map<String, byte[]> output, String namespace, String path, String texture) {
        templateModel(output, namespace, path, "minecraft:block/pressure_plate_up", texture);
        templateModel(output, namespace, path + "_down", "minecraft:block/pressure_plate_down", texture);
        putJson(output, "assets/" + namespace + "/blockstates/" + path + ".json", Map.of(
                "variants", Map.of(
                        "powered=false", Map.of("model", namespace + ":block/" + path),
                        "powered=true", Map.of("model", namespace + ":block/" + path + "_down"))));
    }

    private static void templateModel(
            Map<String, byte[]> output,
            String namespace,
            String path,
            String parent,
            String texture
    ) {
        Map<String, String> textures = new LinkedHashMap<>();
        for (String key : new String[]{"texture", "all", "side", "top", "bottom", "wall", "particle"}) {
            textures.put(key, texture);
        }

        putJson(output, "assets/" + namespace + "/models/block/" + path + ".json",
                Map.of("parent", parent, "textures", textures));
    }

    private static void simpleBlockState(
            Map<String, byte[]> output,
            String namespace,
            String path,
            String model
    ) {
        putJson(output, "assets/" + namespace + "/blockstates/" + path + ".json",
                Map.of("variants", Map.of("", Map.of("model", namespace + ":" + model))));
    }

    private static void writeLoot(Map<String, byte[]> output, VariantEntry entry) {
        String dropId = entry.blockId().toString();
        putJson(
                output,
                "data/" + entry.blockId().getNamespace() + "/loot_table/blocks/" + entry.blockId().getPath() + ".json",
                Map.of(
                        "type", "minecraft:block",
                        "pools", List.of(Map.of(
                                "rolls", 1,
                                "bonus_rolls", 0,
                                "entries", List.of(Map.of("type", "minecraft:item", "name", dropId)),
                                "conditions", List.of(Map.of("condition", "minecraft:survives_explosion"))
                        ))));
    }

    private static void writeRecipes(
            Map<String, byte[]> output,
            VariantEntry entry,
            Map<String, VariantEntry> lookup
    ) {
        VariantData data = entry.data();
        if (data.form() != VariantForm.FULL) {
            VariantEntry base = lookup.get(key(new VariantData(
                    data.sourceId(),
                    data.stage(),
                    data.waxed(),
                    VariantForm.FULL)));
            if (base != null) {
                Map<String, Object> shaped = formRecipe(data.form(), base.blockId().toString(), entry.blockId().toString());
                if (shaped != null) {
                    putJson(output, "data/" + entry.blockId().getNamespace() + "/recipe/" + entry.blockId().getPath() + ".json", shaped);
                }
            }
        }

        if (data.waxed()) {
            VariantEntry unwaxed = lookup.get(key(new VariantData(
                    data.sourceId(),
                    data.stage(),
                    false,
                    data.form())));
            if (unwaxed != null) {
                putJson(
                        output,
                        "data/" + entry.blockId().getNamespace() + "/recipe/" + entry.blockId().getPath()
                                + "_from_honeycomb.json",
                        Map.of(
                                "type", "minecraft:crafting_shapeless",
                                "category", "building",
                                "ingredients", List.of(unwaxed.blockId().toString(), "minecraft:honeycomb"),
                                "result", Map.of("id", entry.blockId().toString(), "count", 1)));
            }
        }
    }

    @Nullable
    private static Map<String, Object> formRecipe(VariantForm form, String base, String result) {
        return switch (form) {
            case SLAB -> shaped(List.of("###"), Map.of("#", base), result, 6);
            case STAIRS -> shaped(List.of("#  ", "## ", "###"), Map.of("#", base), result, 4);
            case WALL -> shaped(List.of("###", "###"), Map.of("#", base), result, 6);
            case FENCE -> shaped(
                    List.of("#S#", "#S#"),
                    Map.of("#", base, "S", "minecraft:stick"),
                    result, 3);
            case FENCE_GATE -> shaped(
                    List.of("S#S", "S#S"),
                    Map.of("#", base, "S", "minecraft:stick"),
                    result, 1);
            case BUTTON -> shaped(List.of("#"), Map.of("#", base), result, 1);
            case PRESSURE_PLATE -> shaped(List.of("##"), Map.of("#", base), result, 1);
            default -> null;
        };
    }

    private static Map<String, Object> shaped(
            List<String> pattern,
            Map<String, String> ingredients,
            String result,
            int count
    ) {
        Map<String, Object> key = new LinkedHashMap<>(ingredients);
        return Map.of(
                "type", "minecraft:crafting_shaped",
                "category", "building",
                "pattern", pattern,
                "key", key,
                "result", Map.of("id", result, "count", count));
    }

    private static void writeDataMaps(
            Map<String, byte[]> output,
            List<VariantEntry> entries,
            Map<String, VariantEntry> lookup
    ) {
        Map<String, Object> oxidizables = new TreeMap<>();
        Map<String, Object> waxables = new TreeMap<>();
        for (VariantEntry entry : entries) {
            VariantData data = entry.data();
            if (!data.waxed() && data.stage().next() != null) {
                VariantEntry next = lookup.get(key(new VariantData(
                        data.sourceId(),
                        data.stage().next(),
                        false,
                        data.form())));
                if (next != null) {
                    oxidizables.put(entry.blockId().toString(), Map.of("next_oxidation_stage", next.blockId().toString()));
                }
            }

            if (!data.waxed()) {
                VariantEntry waxed = lookup.get(key(new VariantData(
                        data.sourceId(),
                        data.stage(),
                        true,
                        data.form())));
                if (waxed != null) {
                    waxables.put(entry.blockId().toString(), Map.of("waxed", waxed.blockId().toString()));
                }
            }
        }

        putJson(output, "data/neoforge/data_maps/block/oxidizables.json",
                Map.of("replace", false, "values", oxidizables));
        putJson(output, "data/neoforge/data_maps/block/waxables.json",
                Map.of("replace", false, "values", waxables));
    }

    private static void writeTags(Map<String, byte[]> output, List<VariantEntry> entries) {
        Map<String, LinkedHashSet<String>> blockTags = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> itemTags = new LinkedHashMap<>();
        for (VariantEntry entry : entries) {
            String id = entry.blockId().toString();
            switch (entry.data().form()) {
                case SLAB -> addBoth(blockTags, itemTags, "slabs", id);
                case STAIRS -> addBoth(blockTags, itemTags, "stairs", id);
                case WALL -> addBoth(blockTags, itemTags, "walls", id);
                case FENCE -> addBoth(blockTags, itemTags, "fences", id);
                case FENCE_GATE -> addBoth(blockTags, itemTags, "fence_gates", id);
                case BUTTON -> addBoth(blockTags, itemTags, "buttons", id);
                case PRESSURE_PLATE -> addBoth(blockTags, itemTags, "pressure_plates", id);
                default -> {}
            }
        }

        for (Map.Entry<String, LinkedHashSet<String>> tag : blockTags.entrySet()) {
            tag(output, "block", tag.getKey(), tag.getValue());
        }

        for (Map.Entry<String, LinkedHashSet<String>> tag : itemTags.entrySet()) {
            tag(output, "item", tag.getKey(), tag.getValue());
        }
    }

    private static void addBoth(
            Map<String, LinkedHashSet<String>> blocks,
            Map<String, LinkedHashSet<String>> items,
            String tag,
            String id
    ) {
        add(blocks, tag, id);
        add(items, tag, id);
    }

    private static void add(Map<String, LinkedHashSet<String>> tags, String tag, String id) {
        tags.computeIfAbsent(tag, ignored -> new LinkedHashSet<>()).add(id);
    }

    private static void tag(
            Map<String, byte[]> output,
            String registry,
            String name,
            Set<String> values
    ) {
        putJson(output, "data/minecraft/tags/" + registry + "/" + name + ".json",
                Map.of("replace", false, "values", values));
    }

    private static void writeManifest(Map<String, byte[]> output, List<VariantEntry> entries) {
        putJson(output, "data/" + PatinaPandemonium.MOD_ID + "/patina_manifest.json", Map.of(
                "entry_count", entries.size(),
                "generated_count", entries.stream().filter(VariantEntry::generated).count(),
                "generated", entries.stream().filter(VariantEntry::generated)
                        .map(entry -> entry.blockId().toString()).toList()));
    }

    private static String choosePrimaryTexture(Map<String, String> textures, String fallback) {
        for (String preferred : new String[]{"all", "texture", "side", "top", "end", "particle"}) {
            String found = textures.get(preferred);
            if (found != null) return found;
        }

        return textures.values().stream().findFirst().orElse(fallback);
    }

    private static String key(VariantData data) {
        return data.sourceId() + "|" + data.stage() + "|" + data.waxed() + "|" + data.form();
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-z0-9_./-]", "_");
    }

    private static byte[] json(Object value) {
        JsonElement element = value instanceof JsonElement json ? json : GSON.toJsonTree(value);
        return GSON.toJson(element).getBytes(StandardCharsets.UTF_8);
    }

    private static void putJson(Map<String, byte[]> output, String path, Object value) {
        output.put(path, json(value));
    }

}
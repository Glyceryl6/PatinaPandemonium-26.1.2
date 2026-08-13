package dev.patina_pandemonium.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.patina_pandemonium.config.PatinaRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingInput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compact manufacturing provenance stored as a flat DAG. The graph is the identity source; names, colors and
 * chemistry text are projections and may be truncated independently without making two different histories equal.
 */
public class VariantProvenance {

    public static final int SCHEMA_VERSION = 1;
    private static final int IMPORT_RESERVE = 12;
    private static final int MAX_ATTRIBUTE_LENGTH = 192;

    public static final Codec<NodeType> NODE_TYPE_CODEC = Codec.STRING.xmap(
        value -> NodeType.valueOf(value.toUpperCase(Locale.ROOT)),
        value -> value.name().toLowerCase(Locale.ROOT));

    public static final Codec<Node> NODE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        NODE_TYPE_CODEC.fieldOf("type").forGetter(Node::type),
        Codec.STRING.fieldOf("operation").forGetter(Node::operation),
        Codec.INT.listOf().fieldOf("inputs").forGetter(Node::inputs),
        Codec.INT.listOf().fieldOf("slots").forGetter(Node::slots),
        Codec.intRange(0, 255).fieldOf("width").forGetter(Node::width),
        Codec.intRange(0, 255).fieldOf("height").forGetter(Node::height),
        Codec.STRING.listOf().fieldOf("attributes").forGetter(Node::attributes),
        Codec.LONG.fieldOf("fingerprint").forGetter(Node::fingerprint),
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("depth").forGetter(Node::depth)
    ).apply(instance, Node::new));

    public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("schema_version").forGetter(Data::schemaVersion),
        Codec.INT.fieldOf("root").forGetter(Data::root),
        Codec.LONG.fieldOf("root_fingerprint").forGetter(Data::rootFingerprint),
        NODE_CODEC.listOf().fieldOf("nodes").forGetter(Data::nodes),
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("generation").forGetter(Data::generation),
        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("source_count").forGetter(Data::sourceCount),
        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("process_count").forGetter(Data::processCount),
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("maximum_depth").forGetter(Data::maximumDepth),
        Codec.BOOL.fieldOf("truncated").forGetter(Data::truncated)
    ).apply(instance, Data::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Nullable
    public static Data get(ItemStack stack) {
        return stack.get(DynamicVariantRegistry.PROVENANCE.get());
    }

    public static Data ensure(ItemStack stack) {
        Data existing = get(stack);
        if (existing != null) return existing;
        Builder builder = new Builder();
        return builder.build(builder.stackRoot(stack, 0));
    }

    public static Data defaultData() {
        Builder builder = new Builder();
        return builder.build(builder.node(NodeType.SOURCE, "unknown_source", List.of(), List.of(), 0, 0, List.of()));
    }

    public static Data entitySource(Entity entity) {
        Builder builder = new Builder();
        ItemVariantData variant = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        CraftingChemistry.Data chemistry = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_CHEMISTRY.get());
        VariantGenetics.Data genetics = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_GENETICS.get());
        return builder.build(builder.node(NodeType.SOURCE, "entity_source", List.of(), List.of(), 0, 0, attributes(
            "entity", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
            "oxidation", variant == null ? "fresh/unwaxed" : stageName(variant.stage()) + "/" + (variant.waxed() ? "waxed" : "unwaxed"),
            "chemistry", chemistry == null ? "none" : Long.toUnsignedString(chemistry.signature(), 16),
            "genetics", genetics == null ? "none" : VariantGenetics.shortSignature(genetics.lineageSignature()))));
    }

    public static Data breed(Data parentAlpha, Data parentBeta, Entity child, VariantGenetics.Data genetics) {
        Builder builder = new Builder();
        int alpha = builder.importData(parentAlpha);
        int beta = builder.importData(parentBeta);
        int root = builder.node(NodeType.BREED, "meiotic_recombination", List.of(alpha, beta), List.of(0, 1), 2, 1, attributes(
            "child", BuiltInRegistries.ENTITY_TYPE.getKey(child.getType()),
            "genetic_generation", genetics.generation(),
            "lineage", VariantGenetics.shortSignature(genetics.lineageSignature()),
            "parent_alpha", VariantGenetics.shortSignature(genetics.parentAlpha()),
            "parent_beta", VariantGenetics.shortSignature(genetics.parentBeta()),
            "recombinations", genetics.recombinations(),
            "mutations", genetics.mutations(),
            "heterozygosity_permille", genetics.heterozygosityPermille(),
            "inbreeding_permille", genetics.inbreedingPermille()));
        return builder.build(root);
    }

    public static ItemStack attachSourceIfMissing(ItemStack stack) {
        if (!stack.isEmpty() && get(stack) == null) stack.set(DynamicVariantRegistry.PROVENANCE.get(), ensure(stack));
        return stack;
    }

    /** Records grid size, absolute occupied slots and every input's prior provenance. */
    public static ItemStack craft(CraftingInput input, ItemStack output, String operation) {
        if (output.isEmpty() || input.isEmpty()) return output;
        Builder builder = new Builder();
        ArrayList<Integer> roots = new ArrayList<>();
        ArrayList<Integer> slots = new ArrayList<>();
        ArrayList<Integer> pigmentSlots = new ArrayList<>();
        ArrayList<Long> pigmentFingerprints = new ArrayList<>();
        ArrayList<String> inputFingerprints = new ArrayList<>();
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack ingredient = input.getItem(slot);
            if (ingredient.isEmpty()) continue;
            Data ingredientData = get(ingredient);
            int ingredientRoot = builder.stackRoot(ingredient, 0);
            long ingredientFingerprint = ingredientData == null ? builder.fingerprint(ingredientRoot) : ingredientData.rootFingerprint();
            roots.add(ingredientRoot);
            slots.add(slot);
            inputFingerprints.add("input_" + slot + "=" + Long.toUnsignedString(ingredientFingerprint, 16));
            if (hasPigment(ingredient)) {
                pigmentSlots.add(slot);
                pigmentFingerprints.add(ingredientFingerprint);
            }
        }
        if (roots.isEmpty()) return output;

        ArrayList<String> craftAttributes = new ArrayList<>(attributes(
            "output", itemId(output),
            "occupied", roots.size(),
            "workstation", workstation(input.width(), input.height()),
            "grid", input.width() + "x" + input.height()));
        craftAttributes.addAll(inputFingerprints);
        int root = builder.node(NodeType.CRAFT, operation, roots, slots, input.width(), input.height(), craftAttributes);

        if (roots.size() > 1 && PatinaRules.INSTANCE.automaticPolymerLineage) {
            root = builder.node(NodeType.POLYMER, "ordered_crafting_sequence", List.of(root), List.of(-1), input.width(), input.height(), attributes(
                "monomers", roots.size(),
                "topology", gridTopology(input)));
        }

        if (!pigmentSlots.isEmpty()) {
            ArrayList<String> pigmentAttributes = new ArrayList<>(attributes(
                "pigments", pigmentSlots.size(),
                "output_color", colorSignature(output)));
            for (int index = 0; index < pigmentSlots.size(); index++) {
                pigmentAttributes.add("pigment_" + index + "=" + pigmentSlots.get(index) + "@"
                    + Long.toUnsignedString(pigmentFingerprints.get(index), 16));
            }
            root = builder.node(NodeType.PIGMENT, "crafting_pigment_lineage", List.of(root), List.of(-1), input.width(), input.height(), pigmentAttributes);
        }

        output.set(DynamicVariantRegistry.PROVENANCE.get(), builder.build(root));
        return output;
    }

    public static ItemStack process(ItemStack input, ItemStack output, NodeType type, String operation, List<ItemStack> catalysts, List<String> extraAttributes) {
        if (input.isEmpty() || output.isEmpty()) return output;
        Builder builder = new Builder();
        ArrayList<Integer> roots = new ArrayList<>(catalysts.size() + 1);
        roots.add(builder.stackRoot(input, 0));
        for (ItemStack catalyst : catalysts) if (!catalyst.isEmpty()) roots.add(builder.stackRoot(catalyst, 0));
        int root = builder.node(type, operation, roots, sequentialSlots(roots.size()), 0, 0, extraAttributes);
        output.set(DynamicVariantRegistry.PROVENANCE.get(), builder.build(root));
        return output;
    }

    public static ItemStack singleItemProcess(ItemStack input, ItemStack output, NodeType type, String operation, List<String> extraAttributes) {
        return process(input, output, type, operation, List.of(), extraAttributes);
    }

    public static Data process(Data input, NodeType type, String operation, List<ItemStack> catalysts, List<String> extraAttributes) {
        Builder builder = new Builder();
        ArrayList<Integer> roots = new ArrayList<>(catalysts.size() + 1);
        roots.add(builder.importData(input));
        for (ItemStack catalyst : catalysts) if (!catalyst.isEmpty()) roots.add(builder.stackRoot(catalyst, 0));
        return builder.build(builder.node(type, operation, roots, sequentialSlots(roots.size()), 0, 0, extraAttributes));
    }

    /**
     * Records one independent constituent occurrence. The target is a root-to-source occurrence path, not only a
     * SOURCE hash, so nine identical ingredients occupying nine slots remain independently addressable after DAG deduplication.
     */
    public static ItemStack localStateEdit(ItemStack input, ItemStack output, String operation, @Nullable OxidationStage fromStage,
                                           @Nullable OxidationStage toStage, @Nullable Boolean waxed) {
        if (input.isEmpty() || output.isEmpty()) return output;
        Builder builder = new Builder();
        int parent = builder.stackRoot(input, 0);
        int root = builder.node(NodeType.LOCAL_STATE_EDIT, operation, List.of(parent), List.of(-1), 0, 0, attributes(
            "target_occurrence", Long.toUnsignedString(builder.localTarget(parent, operation), 16),
            "from_stage", stageName(fromStage),
            "to_stage", stageName(toStage),
            "waxed", waxed == null ? "unchanged" : waxed,
            "from_color", colorSignature(input),
            "to_color", colorSignature(output)));
        output.set(DynamicVariantRegistry.PROVENANCE.get(), builder.build(root));
        return output;
    }

    public static Data localStateEdit(Data input, String operation, @Nullable OxidationStage fromStage, @Nullable OxidationStage toStage,
                                      @Nullable Boolean waxed) {
        Builder builder = new Builder();
        int parent = builder.importData(input);
        int root = builder.node(NodeType.LOCAL_STATE_EDIT, operation, List.of(parent), List.of(-1), 0, 0, attributes(
            "target_occurrence", Long.toUnsignedString(builder.localTarget(parent, operation), 16),
            "from_stage", stageName(fromStage),
            "to_stage", stageName(toStage),
            "waxed", waxed == null ? "unchanged" : waxed));
        return builder.build(root);
    }

    public static ItemStack toolProcess(ItemStack input, ItemStack output, ItemStack tool, String operation, String target) {
        return process(input, output, NodeType.TOOL, operation, tool.isEmpty() ? List.of() : List.of(tool), attributes(
            "target", target,
            "tool", tool.isEmpty() ? "none" : itemId(tool),
            "tool_damage", tool.getOrDefault(DataComponents.DAMAGE, 0),
            "tool_enchantments", enchantmentSignature(tool)));
    }

    public static ItemStack anvil(ItemStack left, ItemStack right, ItemStack output, int xpCost, int materialCost, @Nullable String name) {
        if (left.isEmpty() || output.isEmpty()) return output;
        Builder builder = new Builder();
        ArrayList<Integer> roots = new ArrayList<>();
        roots.add(builder.stackRoot(left, 0));
        if (!right.isEmpty()) roots.add(builder.stackRoot(right, 0));
        int root = builder.node(NodeType.ENCHANT, "anvil", roots, sequentialSlots(roots.size()), 2, 1, attributes(
            "xp_cost", xpCost,
            "material_cost", materialCost,
            "renamed", name != null,
            "result_enchantments", enchantmentSignature(output)));
        output.set(DynamicVariantRegistry.PROVENANCE.get(), builder.build(root));
        return output;
    }

    public static ItemStack enchantingTable(ItemStack stack, List<?> appliedEnchantments) {
        if (stack.isEmpty()) return stack;
        ItemStack pedigree = stack.copy();
        return process(pedigree, stack, NodeType.ENCHANT, "enchanting_table", List.of(), attributes(
            "applied_enchantments", appliedEnchantments,
            "result_enchantments", enchantmentSignature(stack)));
    }

    public static ItemStack equipment(ItemStack input, ItemStack output, String equipment, @Nullable ItemStack equipmentStack,
                                      List<String> extraAttributes) {
        ArrayList<ItemStack> catalysts = new ArrayList<>();
        if (equipmentStack != null && !equipmentStack.isEmpty()) catalysts.add(equipmentStack);
        ArrayList<String> allAttributes = new ArrayList<>(extraAttributes);
        allAttributes.add("equipment=" + sanitize(equipment));
        return process(input, output, NodeType.EQUIPMENT, equipment, catalysts, allAttributes);
    }

    public static ItemStack equipmentInstance(ItemStack output, Data equipmentProvenance, String equipment) {
        if (output.isEmpty()) return output;
        Builder builder = new Builder();
        int outputRoot = builder.stackRoot(output, 0);
        int equipmentRoot = builder.importData(equipmentProvenance);
        int root = builder.node(NodeType.EQUIPMENT, equipment + "_instance", List.of(outputRoot, equipmentRoot), List.of(0, 1), 0, 0, attributes(
            "equipment", equipment,
            "physical_instance", Long.toUnsignedString(equipmentProvenance.rootFingerprint(), 16)));
        output.set(DynamicVariantRegistry.PROVENANCE.get(), builder.build(root));
        return output;
    }

    public static ItemStack splitMerge(ItemStack input, ItemStack output, String operation, int outputIndex, int outputCount) {
        return process(input, output, NodeType.SPLIT_MERGE, operation, List.of(), attributes(
            "output_index", outputIndex,
            "output_count", outputCount));
    }

    public static List<String> attributes(Object... values) {
        ArrayList<String> result = new ArrayList<>(values.length / 2);
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.add(sanitize(String.valueOf(values[index])) + "=" + sanitize(String.valueOf(values[index + 1])));
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    public static String shortFingerprint(Data data) {
        return String.format(Locale.ROOT, "%016x", data.rootFingerprint());
    }

    private static List<Integer> sequentialSlots(int size) {
        ArrayList<Integer> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) result.add(index);
        return List.copyOf(result);
    }

    private static String workstation(int width, int height) {
        if (width <= 2 && height <= 2) return "inventory_crafting";
        if (width == 3 && height == 3) return "crafting_table";
        return "modded_grid_" + width + "x" + height;
    }

    private static String gridTopology(CraftingInput input) {
        int width = Math.max(1, input.width());
        int height = Math.max(1, input.height());
        boolean[] occupied = new boolean[input.size()];
        int vertices = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            occupied[slot] = !input.getItem(slot).isEmpty();
            if (occupied[slot]) vertices++;
        }
        if (vertices <= 1) return "monomer";

        int edges = 0;
        int maximumDegree = 0;
        int endpoints = 0;
        for (int slot = 0; slot < occupied.length; slot++) {
            if (!occupied[slot]) continue;
            int row = slot / width;
            int column = slot % width;
            int degree = 0;
            if (column > 0 && occupied[slot - 1]) degree++;
            if (column + 1 < width && slot + 1 < occupied.length && occupied[slot + 1]) degree++;
            if (row > 0 && occupied[slot - width]) degree++;
            if (row + 1 < height && slot + width < occupied.length && occupied[slot + width]) degree++;
            edges += degree;
            maximumDegree = Math.max(maximumDegree, degree);
            if (degree == 1) endpoints++;
        }
        edges /= 2;

        boolean[] visited = new boolean[occupied.length];
        int components = 0;
        for (int start = 0; start < occupied.length; start++) {
            if (!occupied[start] || visited[start]) continue;
            components++;
            ArrayList<Integer> queue = new ArrayList<>();
            queue.add(start);
            visited[start] = true;
            for (int cursor = 0; cursor < queue.size(); cursor++) {
                int slot = queue.get(cursor);
                int row = slot / width;
                int column = slot % width;
                int[] neighbours = {slot - 1, slot + 1, slot - width, slot + width};
                for (int index = 0; index < neighbours.length; index++) {
                    int neighbour = neighbours[index];
                    if (neighbour < 0 || neighbour >= occupied.length || !occupied[neighbour] || visited[neighbour]) continue;
                    if (index == 0 && column == 0 || index == 1 && column + 1 >= width || index == 2 && row == 0 || index == 3 && row + 1 >= height) continue;
                    visited[neighbour] = true;
                    queue.add(neighbour);
                }
            }
        }
        if (components > 1) return "disconnected";
        if (edges >= vertices && maximumDegree > 2) return "crosslinked";
        if (edges >= vertices) return "cyclic";
        if (maximumDegree > 2) return "branched";
        if (endpoints <= 2) return "linear";
        return "network";
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String stageName(@Nullable OxidationStage stage) {
        return stage == null ? "none" : stage.name().toLowerCase(Locale.ROOT);
    }

    private static String oxidationSignature(ItemStack stack) {
        ItemVariantData itemData = DynamicVariantRegistry.peekItemData(stack);
        if (itemData != null) return stageName(itemData.stage()) + "/" + (itemData.waxed() ? "waxed" : "unwaxed");
        VariantData blockData = stack.get(DynamicVariantRegistry.VARIANT_DATA.get());
        if (blockData != null) return stageName(blockData.stage()) + "/" + (blockData.waxed() ? "waxed" : "unwaxed");
        return "fresh/unwaxed";
    }

    private static boolean hasPigment(ItemStack stack) {
        return stack.get(DataComponents.DYE) != null;
    }

    private static String colorSignature(ItemStack stack) {
        Object dyed = stack.get(DataComponents.DYED_COLOR);
        if (dyed != null) return sanitize(String.valueOf(dyed));
        ItemVariantData itemData = DynamicVariantRegistry.peekItemData(stack);
        if (itemData != null) {
            if (itemData.customColor() != null) return String.format(Locale.ROOT, "#%06x", itemData.customColor());
            DyeColor dye = itemData.dyeColor();
            if (dye != null) return dye.getSerializedName();
        }
        VariantData blockData = stack.get(DynamicVariantRegistry.VARIANT_DATA.get());
        if (blockData != null) {
            if (blockData.customColor() != null) return String.format(Locale.ROOT, "#%06x", blockData.customColor());
            if (blockData.dyeColor() != null) return blockData.dyeColor().getSerializedName();
        }
        return "none";
    }

    private static String enchantmentSignature(ItemStack stack) {
        Object enchantments = stack.get(DataComponents.ENCHANTMENTS);
        Object stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (enchantments == null && stored == null) return "none";
        return sanitize(String.valueOf(enchantments) + "/" + String.valueOf(stored));
    }

    private static String sanitize(String value) {
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').replace('=', ':');
        return cleaned.length() <= MAX_ATTRIBUTE_LENGTH ? cleaned : cleaned.substring(0, MAX_ATTRIBUTE_LENGTH);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static long mixString(long seed, String value) {
        long result = seed;
        for (int index = 0; index < value.length(); index++) result = mix64(result ^ value.charAt(index) ^ (long) index << 32);
        return result;
    }

    public enum NodeType {
        SOURCE,
        CRAFT,
        PROCESS,
        POLYMER,
        PIGMENT,
        ENCHANT,
        HEAT,
        EQUIPMENT,
        TOOL,
        LOCAL_STATE_EDIT,
        CONTAINER,
        SPLIT_MERGE,
        BREED,
        SUMMARY
    }

    public record Node(NodeType type, String operation, List<Integer> inputs, List<Integer> slots, int width, int height,
                       List<String> attributes, long fingerprint, int depth) {
        public Node {
            operation = sanitize(operation);
            inputs = List.copyOf(inputs);
            slots = List.copyOf(slots);
            attributes = attributes.stream().map(VariantProvenance::sanitize).sorted().toList();
        }
    }

    public record Data(int schemaVersion, int root, long rootFingerprint, List<Node> nodes, int generation, int sourceCount,
                       int processCount, int maximumDepth, boolean truncated) {
        public Data {
            nodes = List.copyOf(nodes);
            if (nodes.isEmpty()) throw new IllegalArgumentException("Provenance requires at least one node");
            root = Math.clamp(root, 0, nodes.size() - 1);
            rootFingerprint = nodes.get(root).fingerprint();
            generation = Math.max(1, generation);
            sourceCount = Math.max(0, sourceCount);
            processCount = Math.max(0, processCount);
            maximumDepth = Math.max(1, maximumDepth);
        }
    }

    private static class Builder {
        private final int maximumNodes = Math.max(32, PatinaRules.INSTANCE.maximumProvenanceNodes);
        private final ArrayList<Node> nodes = new ArrayList<>();
        private final Map<NodeKey, Integer> interned = new HashMap<>();
        private boolean truncated;

        int stackRoot(ItemStack stack, int containerDepth) {
            Data existing = get(stack);
            int root = existing == null ? this.node(NodeType.SOURCE, "source", List.of(), List.of(), 0, 0, attributes(
                "item", itemId(stack),
                "oxidation", oxidationSignature(stack),
                "color", colorSignature(stack),
                "enchantments", enchantmentSignature(stack),
                "damage", stack.getOrDefault(DataComponents.DAMAGE, 0))) : this.importData(existing);

            if (!PatinaRules.INSTANCE.trackContainerProvenance || containerDepth >= PatinaRules.INSTANCE.maximumProvenanceContainerDepth) return root;
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents == null) return root;
            ArrayList<Integer> inputs = new ArrayList<>();
            ArrayList<Integer> slots = new ArrayList<>();
            inputs.add(root);
            slots.add(-1);
            int entries = 0;
            List<ItemStack> contained = contents.allItemsCopyStream().toList();
            for (int slot = 0; slot < contained.size(); slot++) {
                if (entries >= PatinaRules.INSTANCE.maximumProvenanceContainerEntries) {
                    this.truncated = true;
                    break;
                }
                ItemStack child = contained.get(slot);
                if (child.isEmpty()) continue;
                inputs.add(this.stackRoot(child, containerDepth + 1));
                slots.add(slot);
                entries++;
            }
            return inputs.size() <= 1 ? root : this.node(NodeType.CONTAINER, "container_contents_snapshot", inputs, slots, 0, 0, attributes(
                "entries", inputs.size() - 1,
                "container_depth", containerDepth));
        }

        int importData(Data data) {
            int[] remap = new int[data.nodes().size()];
            Arrays.fill(remap, -1);
            return this.importNode(data, data.root(), remap, 0);
        }

        int importNode(Data data, int oldIndex, int[] remap, int recursionDepth) {
            if (oldIndex < 0 || oldIndex >= data.nodes().size()) return this.summaryNode(0L, "invalid_reference", data.nodes().size());
            if (remap[oldIndex] >= 0) return remap[oldIndex];
            Node old = data.nodes().get(oldIndex);
            if (this.nodes.size() >= this.maximumNodes - IMPORT_RESERVE || recursionDepth > PatinaRules.INSTANCE.maximumProvenanceDepth) {
                this.truncated = true;
                int summary = this.summaryNode(old.fingerprint(), "import_budget", data.nodes().size());
                remap[oldIndex] = summary;
                return summary;
            }
            ArrayList<Integer> inputs = new ArrayList<>(old.inputs().size());
            for (int child : old.inputs()) inputs.add(this.importNode(data, child, remap, recursionDepth + 1));
            int imported = this.node(old.type(), old.operation(), inputs, old.slots(), old.width(), old.height(), old.attributes());
            remap[oldIndex] = imported;
            if (data.truncated()) this.truncated = true;
            return imported;
        }

        int node(NodeType type, String operation, List<Integer> inputs, List<Integer> slots, int width, int height, List<String> attributes) {
            ArrayList<String> normalizedAttributes = new ArrayList<>(attributes);
            normalizedAttributes.replaceAll(VariantProvenance::sanitize);
            normalizedAttributes.sort(Comparator.naturalOrder());
            int depth = 1;
            ArrayList<Long> inputFingerprints = new ArrayList<>(inputs.size());
            for (int input : inputs) {
                if (input < 0 || input >= this.nodes.size()) continue;
                Node child = this.nodes.get(input);
                inputFingerprints.add(child.fingerprint());
                depth = Math.max(depth, child.depth() + 1);
            }
            if (depth > PatinaRules.INSTANCE.maximumProvenanceDepth || this.nodes.size() >= this.maximumNodes - 1) {
                this.truncated = true;
                return this.summaryNode(inputFingerprints.isEmpty() ? 0L : inputFingerprints.getFirst(), operation, this.nodes.size());
            }

            NodeKey key = new NodeKey(type, sanitize(operation), List.copyOf(inputFingerprints), List.copyOf(slots),
                Math.clamp(width, 0, 255), Math.clamp(height, 0, 255), List.copyOf(normalizedAttributes));
            Integer existing = this.interned.get(key);
            if (existing != null) return existing;
            Node node = new Node(type, operation, inputs, slots, key.width(), key.height(), normalizedAttributes, key.fingerprint(), depth);
            int index = this.nodes.size();
            this.nodes.add(node);
            this.interned.put(key, index);
            return index;
        }

        int summaryNode(long fingerprint, String reason, int originalNodes) {
            NodeKey key = new NodeKey(NodeType.SUMMARY, "summary", List.of(fingerprint), List.of(), 0, 0, attributes(
                "reason", reason,
                "original_nodes", originalNodes,
                "root", Long.toUnsignedString(fingerprint, 16)));
            Integer existing = this.interned.get(key);
            if (existing != null) return existing;
            if (this.nodes.size() >= this.maximumNodes) return Math.max(0, this.nodes.size() - 1);
            Node node = new Node(NodeType.SUMMARY, "summary", List.of(), List.of(), 0, 0, key.attributes(), key.fingerprint(), 1);
            int index = this.nodes.size();
            this.nodes.add(node);
            this.interned.put(key, index);
            return index;
        }

        long localTarget(int root, String operation) {
            HashMap<Integer, Long> counts = new HashMap<>();
            long occurrences = this.sourceOccurrences(root, counts, 0);
            if (occurrences <= 0L) return this.fingerprint(root);
            long mixed = mix64(this.fingerprint(root) ^ mixString(0x6A09E667F3BCC909L, operation) ^ this.nodes.size());
            long selected = Math.floorMod(mixed, occurrences);
            return this.sourceOccurrence(root, selected, 0xBB67AE8584CAA73BL, counts, 0);
        }

        private long sourceOccurrences(int index, Map<Integer, Long> counts, int depth) {
            if (index < 0 || index >= this.nodes.size() || depth > PatinaRules.INSTANCE.maximumProvenanceDepth) return 0L;
            Long cached = counts.get(index);
            if (cached != null) return cached;
            Node node = this.nodes.get(index);
            if (node.type() == NodeType.SOURCE || node.type() == NodeType.SUMMARY) {
                counts.put(index, 1L);
                return 1L;
            }
            long total = 0L;
            for (int child : node.inputs()) total = saturatedAdd(total, this.sourceOccurrences(child, counts, depth + 1));
            counts.put(index, total);
            return total;
        }

        private long sourceOccurrence(int index, long selected, long path, Map<Integer, Long> counts, int depth) {
            if (index < 0 || index >= this.nodes.size() || depth > PatinaRules.INSTANCE.maximumProvenanceDepth) return mix64(path);
            Node node = this.nodes.get(index);
            if (node.type() == NodeType.SOURCE || node.type() == NodeType.SUMMARY || node.inputs().isEmpty()) {
                return mix64(path ^ node.fingerprint() ^ selected);
            }
            for (int inputIndex = 0; inputIndex < node.inputs().size(); inputIndex++) {
                int child = node.inputs().get(inputIndex);
                long childCount = counts.getOrDefault(child, 0L);
                if (selected >= childCount) {
                    selected -= childCount;
                    continue;
                }
                int slot = inputIndex < node.slots().size() ? node.slots().get(inputIndex) : inputIndex;
                long childPath = mix64(path ^ node.fingerprint() ^ (long) inputIndex << 32 ^ slot);
                return this.sourceOccurrence(child, selected, childPath, counts, depth + 1);
            }
            return mix64(path ^ node.fingerprint());
        }

        private static long saturatedAdd(long first, long second) {
            if (first >= Long.MAX_VALUE - second) return Long.MAX_VALUE;
            return first + second;
        }

        long fingerprint(int index) {
            return index >= 0 && index < this.nodes.size() ? this.nodes.get(index).fingerprint() : 0L;
        }

        Data build(int root) {
            if (this.nodes.isEmpty()) root = this.node(NodeType.SOURCE, "empty_source", List.of(), List.of(), 0, 0, List.of());
            root = Math.clamp(root, 0, this.nodes.size() - 1);
            int sourceCount = 0;
            int processCount = 0;
            int maximumDepth = 1;
            for (Node node : this.nodes) {
                if (node.type() == NodeType.SOURCE) sourceCount++;
                if (node.type() != NodeType.SOURCE && node.type() != NodeType.SUMMARY) processCount++;
                maximumDepth = Math.max(maximumDepth, node.depth());
            }
            return new Data(SCHEMA_VERSION, root, this.nodes.get(root).fingerprint(), List.copyOf(this.nodes),
                maximumDepth, sourceCount, processCount, maximumDepth, this.truncated);
        }
    }

    private record NodeKey(NodeType type, String operation, List<Long> inputFingerprints, List<Integer> slots, int width, int height,
                           List<String> attributes) {
        long fingerprint() {
            long result = mixString(0x243F6A8885A308D3L, this.type.name());
            result = mixString(result, this.operation);
            result = mix64(result ^ this.width ^ (long) this.height << 32);
            for (long input : this.inputFingerprints) result = mix64(result ^ input);
            for (int slot : this.slots) result = mix64(result ^ slot ^ 0x9E3779B97F4A7C15L);
            for (String attribute : this.attributes) result = mixString(result, attribute);
            return result;
        }
    }
}

package dev.patinapandemonium.registry;

import dev.patinapandemonium.PatinaPandemonium;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class DynamicWoodTypes {

    private static final Map<String, WoodType> TYPES = new LinkedHashMap<>();

    public static WoodType getOrCreate(VariantData data) {
        String key = familyKey(data);
        return TYPES.computeIfAbsent(key, ignored -> WoodType.register(new WoodType(textureId(data).toString(), BlockSetType.OAK)));
    }

    public static Collection<WoodType> values() {
        return java.util.List.copyOf(TYPES.values());
    }

    public static Identifier textureId(VariantData data) {
        return PatinaPandemonium.id("generated_signs/" + data.sourceId().getNamespace() + "/" + data.sourceId().getPath() + "/" + data.stage().id());
    }

    private static String familyKey(VariantData data) {
        return data.sourceId() + "|" + data.stage();
    }

}
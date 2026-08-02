package dev.patinapandemonium.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.patinapandemonium.config.PatinaRules;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads packaged assets directly from the installed mod files, including composite development
 * outputs. The class-loader fallback remains for game resources not represented by a mod file.
 */
class AssetResolver {

    private static final Map<String, Optional<byte[]>> RESOURCE_CACHE = new ConcurrentHashMap<>();

    record ModelInfo(@Nullable Identifier model, LinkedHashMap<String, Identifier> textures) {
    }

    static ModelInfo resolve(Identifier blockId) {
        Identifier model = firstModel(blockId);
        LinkedHashMap<String, Identifier> textures = new LinkedHashMap<>();
        collectModel(model, textures, new HashSet<>());
        Identifier override = override(blockId);
        if (override != null) {
            textures.put("all", override);
        }

        if (textures.isEmpty()) {
            textures.put("all", Identifier.fromNamespaceAndPath(blockId.getNamespace(), "block/" + blockId.getPath()));
        }

        return new ModelInfo(model, textures);
    }

    static Identifier primaryTexture(ModelInfo info, Identifier sourceId) {
        for (String preferred : new String[]{"all", "texture", "side", "top", "end", "particle"}) {
            Identifier found = info.textures().get(preferred);
            if (found != null) return found;
        }

        return info.textures().values().stream().findFirst()
                .orElse(Identifier.fromNamespaceAndPath(sourceId.getNamespace(), "block/" + sourceId.getPath()));
    }

    static byte @Nullable [] tinted(Identifier texture, int stage) {
        BufferedImage image = readAndTint(texture, stage);
        return image == null ? null : png(image);
    }

    static byte @Nullable [] textureMetadata(Identifier texture) {
        String path = "assets/" + texture.getNamespace() + "/textures/" + texture.getPath() + ".png.mcmeta";
        return RESOURCE_CACHE.computeIfAbsent(path, AssetResolver::findResource).orElse(null);
    }

    /**
     * Sign render layers require a 64x32 atlas-shaped texture. The source texture is tiled with
     * nearest-neighbour sampling, so the result remains a pure recolour of the source artwork.
     */
    static byte @Nullable [] tiledSignTexture(Identifier texture, int stage) {
        BufferedImage source = readAndTint(texture, stage);
        if (source == null) return null;
        BufferedImage output = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) {
            for (int x = 0; x < output.getWidth(); x++) {
                output.setRGB(x, y, source.getRGB(x % source.getWidth(), y % source.getHeight()));
            }
        }

        return png(output);
    }

    @Nullable
    private static BufferedImage readAndTint(Identifier texture, int stage) {
        String path = "assets/" + texture.getNamespace() + "/textures/" + texture.getPath() + ".png";
        try (InputStream input = open(path)) {
            if (input == null) {
                return null;
            }
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                return null;
            }
            if (stage <= 0) {
                return image;
            }

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    int alpha = argb >>> 24 & 255;
                    int red = argb >>> 16 & 255;
                    int green = argb >>> 8 & 255;
                    int blue = argb & 255;

                    // Deliberately conservative palette transforms. They preserve luminance and
                    // source detail rather than painting a fixed copper overlay on every pixel.
                    if (stage == 1) {
                        red = (int) (red * 0.94);
                        green = (int) (green * 0.82);
                        blue = (int) (blue * 0.68);
                    } else if (stage == 2) {
                        red = (int) (red * 0.62 + 92 * 0.38);
                        green = (int) (green * 0.62 + 157 * 0.38);
                        blue = (int) (blue * 0.62 + 122 * 0.38);
                    } else {
                        red = (int) (red * 0.45 + 53 * 0.55);
                        green = (int) (green * 0.45 + 155 * 0.55);
                        blue = (int) (blue * 0.45 + 145 * 0.55);
                    }
                    image.setRGB(x, y, alpha << 24 | clamp(red) << 16 | clamp(green) << 8 | clamp(blue));
                }
            }
            return image;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static byte @Nullable [] png(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", output);
            return output.toByteArray();
        } catch (IOException error) {
            return null;
        }
    }

    private static int clamp(int value) {
        return Math.clamp(value, 0, 255);
    }

    private static Identifier firstModel(Identifier blockId) {
        String path = "assets/" + blockId.getNamespace() + "/blockstates/" + blockId.getPath() + ".json";
        try (InputStream input = open(path)) {
            if (input == null) {
                return Identifier.fromNamespaceAndPath(blockId.getNamespace(), "block/" + blockId.getPath());
            }
            JsonElement root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            Identifier found = findModel(root);
            return found == null
                    ? Identifier.fromNamespaceAndPath(blockId.getNamespace(), "block/" + blockId.getPath())
                    : found;
        } catch (Exception ignored) {
            return Identifier.fromNamespaceAndPath(blockId.getNamespace(), "block/" + blockId.getPath());
        }
    }

    @Nullable
    private static Identifier findModel(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("model") && object.get("model").isJsonPrimitive()) {
                return Identifier.tryParse(object.get("model").getAsString());
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                Identifier nested = findModel(entry.getValue());
                if (nested != null) {
                    return nested;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                Identifier nested = findModel(child);
                if (nested != null) {
                    return nested;
                }
            }
        }

        return null;
    }

    private static void collectModel(Identifier model, LinkedHashMap<String, Identifier> output, Set<Identifier> visited) {
        if (!visited.add(model)) return;
        String path = "assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json";
        try (InputStream input = open(path)) {
            if (input == null) return;
            JsonObject object = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            if (object.has("parent")) {
                Identifier parent = Identifier.tryParse(object.get("parent").getAsString());
                if (parent != null) {
                    collectModel(parent, output, visited);
                }
            }

            if (object.has("textures")) {
                Map<String, String> aliases = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("textures").entrySet()) {
                    String value = entry.getValue().getAsString();
                    if (value.startsWith("#")) {
                        aliases.put(entry.getKey(), value.substring(1));
                        continue;
                    }
                    Identifier texture = Identifier.tryParse(value);
                    if (texture != null) {
                        output.put(entry.getKey(), texture);
                    }
                }

                boolean resolved;
                do {
                    resolved = aliases.entrySet().removeIf(entry -> {
                        Identifier texture = output.get(entry.getValue());
                        if (texture != null) {
                            output.put(entry.getKey(), texture);
                            return true;
                        }
                        return false;
                    });
                } while (resolved && !aliases.isEmpty());
            }
        } catch (Exception ignored) {
            // A custom model loader can still be handled by textureOverrides.
        }
    }

    @Nullable
    private static Identifier override(Identifier blockId) {
        if (PatinaRules.INSTANCE.textureOverrides == null) return null;
        JsonElement value = PatinaRules.INSTANCE.textureOverrides.get(blockId.toString());
        return value != null && value.isJsonPrimitive() ? Identifier.tryParse(value.getAsString()) : null;
    }

    @Nullable
    private static InputStream open(String path) {
        byte[] bytes = RESOURCE_CACHE.computeIfAbsent(path, AssetResolver::findResource).orElse(null);
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }

    private static Optional<byte[]> findResource(String path) {
        ModList modList = ModList.get();
        if (modList != null) {
            IModFileInfo preferred = modList.getModFileById(namespace(path));
            byte[] bytes = read(preferred, path);
            if (bytes != null) {
                return Optional.of(bytes);
            }

            List<IModFileInfo> modFiles = modList.getModFiles();
            for (IModFileInfo modFile : modFiles) {
                if (modFile == preferred) {
                    continue;
                }
                bytes = read(modFile, path);
                if (bytes != null) {
                    return Optional.of(bytes);
                }
            }
        }

        ClassLoader context = Thread.currentThread().getContextClassLoader();
        byte[] bytes = read(context, path);
        if (bytes == null) {
            bytes = read(AssetResolver.class.getClassLoader(), path);
        }
        return Optional.ofNullable(bytes);
    }

    private static byte @Nullable [] read(@Nullable IModFileInfo modFile, String path) {
        if (modFile == null) return null;
        JarContents contents = modFile.getFile().getContents();
        try {
            return contents.readFile(path);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static byte @Nullable [] read(@Nullable ClassLoader classLoader, String path) {
        if (classLoader == null) return null;
        try (InputStream input = classLoader.getResourceAsStream(path)) {
            return input == null ? null : input.readAllBytes();
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String namespace(String path) {
        int namespaceStart = path.indexOf('/') + 1;
        int namespaceEnd = path.indexOf('/', namespaceStart);
        return namespaceStart > 0 && namespaceEnd > namespaceStart ? path.substring(namespaceStart, namespaceEnd) : "";
    }

}
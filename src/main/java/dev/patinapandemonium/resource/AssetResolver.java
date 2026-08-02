package dev.patinapandemonium.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.patinapandemonium.config.PatinaRules;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Best-effort reader for vanilla and mod resources already present on the game class path.
 * Custom-loader models can opt into a known source texture through textureOverrides.
 */
class AssetResolver {

    record ModelInfo(Identifier model, LinkedHashMap<String, Identifier> textures) { }

    static ModelInfo resolve(Identifier blockId) {
        Identifier model = firstModel(blockId);
        LinkedHashMap<String, Identifier> textures = new LinkedHashMap<>();
        if (model != null) {
            collectModel(model, textures, new HashSet<>());
        }

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
            if (found != null) {
                return found;
            }
        }

        return info.textures().values().stream().findFirst()
                .orElse(Identifier.fromNamespaceAndPath(sourceId.getNamespace(), "block/" + sourceId.getPath()));
    }

    static byte[] tinted(Identifier texture, int stage) {
        BufferedImage image = readAndTint(texture, stage);
        return image == null ? null : png(image);
    }

    /**
     * Sign render layers require a 64x32 atlas-shaped texture. The source texture is tiled with
     * nearest-neighbour sampling, so the result remains a pure recolour of the source artwork.
     */
    static byte[] tiledSignTexture(Identifier texture, int stage) {
        BufferedImage source = readAndTint(texture, stage);
        if (source == null) {
            return null;
        }

        BufferedImage output = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) {
            for (int x = 0; x < output.getWidth(); x++) {
                output.setRGB(x, y, source.getRGB(x % source.getWidth(), y % source.getHeight()));
            }
        }

        return png(output);
    }

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

    private static byte[] png(BufferedImage image) {
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
            JsonElement root = JsonParser.parseReader(new InputStreamReader(input));
            Identifier found = findModel(root);
            return found == null
                    ? Identifier.fromNamespaceAndPath(blockId.getNamespace(), "block/" + blockId.getPath())
                    : found;
        } catch (Exception ignored) {
            return Identifier.fromNamespaceAndPath(blockId.getNamespace(), "block/" + blockId.getPath());
        }
    }

    private static Identifier findModel(JsonElement element) {
        if (element == null) {
            return null;
        }
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

    private static void collectModel(
            Identifier model,
            LinkedHashMap<String, Identifier> output,
            Set<Identifier> visited) {
        if (!visited.add(model)) {
            return;
        }
        String path = "assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json";
        try (InputStream input = open(path)) {
            if (input == null) {
                return;
            }
            JsonObject object = JsonParser.parseReader(new InputStreamReader(input)).getAsJsonObject();
            if (object.has("parent")) {
                Identifier parent = Identifier.tryParse(object.get("parent").getAsString());
                if (parent != null) {
                    collectModel(parent, output, visited);
                }
            }

            if (object.has("textures")) {
                for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("textures").entrySet()) {
                    String value = entry.getValue().getAsString();
                    if (!value.startsWith("#")) {
                        Identifier texture = Identifier.tryParse(value);
                        if (texture != null) {
                            output.put(entry.getKey(), texture);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // A custom model loader can still be handled by textureOverrides.
        }
    }

    private static Identifier override(Identifier blockId) {
        if (PatinaRules.INSTANCE.textureOverrides == null) return null;
        JsonElement value = PatinaRules.INSTANCE.textureOverrides.get(blockId.toString());
        return value != null && value.isJsonPrimitive() ? Identifier.tryParse(value.getAsString()) : null;
    }

    private static InputStream open(String path) {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        InputStream input = context == null ? null : context.getResourceAsStream(path);
        return input != null ? input : AssetResolver.class.getClassLoader().getResourceAsStream(path);
    }

}
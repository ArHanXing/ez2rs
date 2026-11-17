package ar.hxi.ez2rs.storage;

import ar.hxi.ez2rs.Ez2rs;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * 无限存储方块的配置文件。
 *
 * <p>一个条目 = 一个方块（见 {@link InfiniteStorageContent}），条目只能属于物品或流体之一：
 * RS2 的 {@code ResourceFactory} 与过滤器容器都是单资源类型的，混在一起就需要自定义菜单与界面，
 * 代价远大于收益。要同时提供物品和流体就写两条。</p>
 *
 * <p><b>解析失败一律抛异常（游戏直接在启动阶段崩掉）。</b>这里刻意不做"坏配置就当空配置"的兜底：
 * 方块/物品/方块实体类型全部由这份配置生成，一旦静默降级，玩家看到的是"模组装了但方块没了"，
 * 而且没有任何线索；直接崩并把行号无关的原因写进日志，反而是最好排查的形态。只有两种情况例外，
 * 它们都会 warn 后跳过而不是崩：单条资源 id 写错（跳过该资源）、条目同时给了 item 与 fluid
 * 或两者都空（跳过该条目）。</p>
 *
 * <p><b>资源 id 在这一步不查注册表</b>，只做语法解析，保存成字符串交给
 * {@link InfiniteStorageResources} 延迟解析。原因见那个类的类注释：我们的注册时机比大多数模组早，
 * 这时候查 {@code BuiltInRegistries} 必然失败（{@code techreborn:cell} 之类就是这种情况）。</p>
 *
 * <p>用手写 {@code JsonObject} 而不是 Gson 反射绑定：反射绑定会把字段名、null、缺省值、
 * {@code FieldNamingPolicy} 的行为都藏进注解里，出错时报的还是 {@code UnsupportedOperationException}，
 * 而这份配置只有五个字段，手写反而更短、更容易给出准确的错误信息。</p>
 */
public final class InfiniteStorageConfig {
    /** 条目属于物品还是流体。混合类型不支持，见类注释。 */
    public enum Kind {
        ITEM,
        FLUID
    }

    /**
     * 一条配置解析后的结果。
     *
     * @param id     方块 / 物品 / 方块实体类型共用的注册 id，形如 {@code ez2rs:infinite_storage_water}
     * @param name   显示名；方块实体（{@code Nameable#getName}）、方块与物品都用它，
     *               所以没有 lang 文件也能显示中文
     * @param kind   资源类型，决定用哪套 RS2 菜单 / 资源工厂
     * @param items  物品 id 字符串（<b>尚未</b>查注册表，见 {@link InfiniteStorageResources}）
     * @param fluids 流体 id 字符串（同上）
     */
    public record Entry(ResourceLocation id,
                        MutableComponent name,
                        Kind kind,
                        List<String> items,
                        List<String> fluids) {
    }

    private static final String FILE_NAME = "ez2rs-infinite-storage.json";
    private static final String ID_PREFIX = "infinite_storage_";

    /** 首次启动写出的示例配置：两条各给一种类型，玩家照着改就行。 */
    private static final String DEFAULT_JSON = """
        {
          "blocks": [
            { "id": "water", "name": "无限水存储方块", "fluid": ["minecraft:water"], "item": [] },
            { "id": "cobblestone", "name": "无限圆石存储方块", "fluid": [], "item": ["minecraft:cobblestone"] }
          ]
        }
        """;

    private InfiniteStorageConfig() {
    }

    /**
     * 读取配置；文件不存在就先写一份默认配置再读。
     *
     * @throws IllegalStateException 配置无法写入、不是合法 JSON、结构不符合约定（缺 {@code blocks}、
     *                               {@code blocks} 不是数组、元素不是对象、字段类型不对、id 重复）
     */
    public static List<Entry> load() {
        final Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        final JsonElement root = readRoot(path);
        if (!root.isJsonObject()) {
            throw configError(path, "root must be a JSON object");
        }
        final JsonElement blocksElement = root.getAsJsonObject().get("blocks");
        if (blocksElement == null) {
            throw configError(path, "missing required field \"blocks\"");
        }
        if (!blocksElement.isJsonArray()) {
            throw configError(path, "\"blocks\" must be an array");
        }
        final JsonArray blocks = blocksElement.getAsJsonArray();
        final List<Entry> entries = new ArrayList<>();
        final Set<ResourceLocation> usedIds = new HashSet<>();
        // 下标序号是 id 的缺省来源，所以即使某条被跳过，下标也必须跟着它的位置走，
        // 不能"跳过一个就整体前移"，否则改一条配置就会让后面所有方块的 id 全变（老存档全失效）。
        for (int index = 0; index < blocks.size(); index++) {
            final JsonElement element = blocks.get(index);
            if (!element.isJsonObject()) {
                throw configError(path, "blocks[" + index + "] must be a JSON object");
            }
            final ResourceLocation id = resolveId(path, index, element.getAsJsonObject());
            if (!usedIds.add(id)) {
                throw configError(path, "duplicate block id " + id + " in blocks[" + index + "]");
            }
            final Entry entry = parseEntry(path, index, element.getAsJsonObject(), id);
            // entry 为 null 表示"该条目按规则跳过"（类型冲突或没有任何有效资源），已经 warn 过。
            if (entry != null) {
                entries.add(entry);
            }
        }
        Ez2rs.LOGGER.info("Loaded {} infinite storage block(s) from {}", entries.size(), path);
        return List.copyOf(entries);
    }

    private static JsonElement readRoot(final Path path) {
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, DEFAULT_JSON, StandardCharsets.UTF_8);
                Ez2rs.LOGGER.info("Wrote default infinite storage config to {}", path);
            }
        } catch (final IOException e) {
            throw configError(path, "could not write default config: " + e);
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (final IOException e) {
            throw configError(path, "could not read config: " + e);
        } catch (final RuntimeException e) {
            // JsonSyntaxException / IllegalStateException：不覆盖用户的文件，直接把原因抛出去。
            throw configError(path, "not valid JSON: " + e.getMessage());
        }
    }

    /**
     * 决定条目的注册 id：优先用配置里的 {@code id} 字段，缺省或非法时退回下标序号。
     *
     * <p>{@code id} 会被拼成 {@code ez2rs:infinite_storage_<id>}，所以要通过
     * {@link ResourceLocation#tryParse} 校验（小写、只允许 {@code [a-z0-9_.-/]}）。</p>
     */
    private static ResourceLocation resolveId(final Path path, final int index, final JsonObject object) {
        final JsonElement idElement = object.get("id");
        if (idElement == null) {
            return defaultId(path, index, "no \"id\" field");
        }
        if (!idElement.isJsonPrimitive() || !idElement.getAsJsonPrimitive().isString()) {
            throw configError(path, "blocks[" + index + "].id must be a string");
        }
        final String slug = idElement.getAsString();
        final ResourceLocation id = ResourceLocation.tryParse(Ez2rs.MOD_ID + ":" + ID_PREFIX + slug);
        if (id == null) {
            return defaultId(path, index, "\"" + slug + "\" is not a valid resource location");
        }
        return id;
    }

    private static ResourceLocation defaultId(final Path path, final int index, final String reason) {
        Ez2rs.LOGGER.warn("Infinite storage blocks[{}]: {}, falling back to index-based id", index, reason);
        return ResourceLocation.fromNamespaceAndPath(Ez2rs.MOD_ID, ID_PREFIX + index);
    }

    /** @return 解析结果，或 {@code null} 表示该条目应被跳过 */
    private static Entry parseEntry(final Path path,
                                    final int index,
                                    final JsonObject object,
                                    final ResourceLocation id) {
        final JsonElement nameElement = object.get("name");
        if (nameElement == null) {
            // 没有 name 就没有物品名与方块名（我们没有 lang 文件，也不该为动态条目生成 lang），
            // 与其显示 block.ez2rs.infinite_storage_water 这种原始键，不如直接让玩家把配置写全。
            throw configError(path, "blocks[" + index + "].name is required");
        }
        if (!nameElement.isJsonPrimitive() || !nameElement.getAsJsonPrimitive().isString()) {
            throw configError(path, "blocks[" + index + "].name must be a string");
        }
        final MutableComponent name = Component.literal(nameElement.getAsString());
        final List<String> items = parseResourceIds(path, index, "item", object.get("item"));
        final List<String> fluids = parseResourceIds(path, index, "fluid", object.get("fluid"));
        if (!items.isEmpty() && !fluids.isEmpty()) {
            Ez2rs.LOGGER.warn(
                "Skipping infinite storage block {}: it configures both item and fluid, write two entries instead",
                id
            );
            return null;
        }
        if (items.isEmpty() && fluids.isEmpty()) {
            Ez2rs.LOGGER.warn("Skipping infinite storage block {}: it configures neither item nor fluid", id);
            return null;
        }
        final Kind kind = items.isEmpty() ? Kind.FLUID : Kind.ITEM;
        Ez2rs.LOGGER.info("Infinite storage block {} ({}) configures {} {} resource id(s)",
            id, kind, items.size() + fluids.size(), kind);
        return new Entry(id, name, kind, items, fluids);
    }

    /** 字段缺省 = 空列表；字段存在但不是字符串数组 = 配置写错，崩溃。 */
    private static List<String> parseResourceIds(final Path path,
                                                final int index,
                                                final String field,
                                                final JsonElement element) {
        if (element == null) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw configError(path, "blocks[" + index + "]." + field + " must be an array of strings");
        }
        final List<String> result = new ArrayList<>();
        final JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            final JsonElement value = array.get(i);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw configError(path, "blocks[" + index + "]." + field + "[" + i + "] must be a string");
            }
            result.add(value.getAsString());
        }
        return result;
    }

    /** 既写日志又返回异常，调用处直接 {@code throw configError(...)}。 */
    private static IllegalStateException configError(final Path path, final String message) {
        Ez2rs.LOGGER.error("Invalid infinite storage config {}: {}", path, message);
        return new IllegalStateException("Invalid infinite storage config " + path + ": " + message);
    }
}

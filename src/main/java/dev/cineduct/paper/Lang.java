// SPDX-License-Identifier: MIT
// Copyright (c) 2026 hxwi1

package dev.cineduct.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简 i18n：**以中文原文为 key** 的翻译表。
 *
 * <p>为什么不用 key（{@code screen.created} 之类）：这个插件的文案散在 70 多处调用里，
 * 换成 key 意味着把每个调用点重写一遍，改动量大、收益只是"看起来规范"。而"原文即 key"
 * 只需要维护一份 yml，代码一个字符都不用改：
 *
 * <pre>{@code
 * 中文原文（代码里写死的）            en_us.yml 里的条目
 * "屏幕 main 已创建：2 × 2 格"   →   "屏幕 {} 已创建：{}" : "Screen {0} created: {1}"
 * }</pre>
 *
 * <p>匹配顺序：先精确匹配，再按"带 {@code {}} 通配的模板"匹配（按字面量长度从长到短，
 * 避免短模板抢走长模板的句子）。匹配不到就原样返回中文 —— 宁可露出中文，也不要显示
 * {@code key.not.found}。
 *
 * <p>语言判定：玩家取 {@link Player#locale()}，控制台取 {@code config.yml} 的 {@code language}，
 * {@code auto} 时按中文环境收中文、其它收英文。中文环境直接跳过查表（原样输出）。
 */
final class Lang {

    /** 一条"模板 → 译文"。模板里的 {@code {}} 是通配，译文里的 {@code {0}} 是参数占位。 */
    private record Entry(Pattern pattern, String[] literalParts, String translation) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Map<String, String> EXACT = new LinkedHashMap<>();
    private static volatile String fallback = "zh";

    private Lang() {
    }

    /**
     * 载入语言文件（jar 内置 + 数据目录覆盖）。
     *
     * @param plugin    插件实例（取数据目录与 config）
     */
    static void load(CinePlayPlugin plugin) {
        ENTRIES.clear();
        EXACT.clear();
        fallback = plugin.configLanguage();

        // 内置的英文（跟 jar 走），数据目录的 lang/en_us.yml 可以补充或覆盖
        readResource(plugin, "lang/en_us.yml");
        readOverride(plugin, "en_us");
    }

    private static void readResource(CinePlayPlugin plugin, String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) return;
            apply(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            plugin.getLogger().warning("读取内置语言文件失败: " + path + "（" + e.getMessage() + "）");
        }
    }

    private static void readOverride(CinePlayPlugin plugin, String name) {
        final File f = new File(new File(plugin.getDataFolder(), "lang"), name + ".yml");
        if (!f.isFile()) return;
        try {
            apply(YamlConfiguration.loadConfiguration(f));
        } catch (Exception e) {
            plugin.getLogger().warning("读取语言文件失败: " + f.getName() + "（" + e.getMessage() + "）");
        }
    }

    private static void apply(YamlConfiguration yaml) {
        for (String key : yaml.getKeys(false)) {
            final String value = yaml.getString(key);
            if (value == null || value.isBlank()) continue;
            final String normalized = key.replace("%", "%%");   // yml 里 % 不是特殊字符，这里只是防呆
            EXACT.put(normalized, value);
            final String[] parts = normalized.split(Pattern.quote("{}"), -1);
            if (parts.length == 1) continue;                    // 没有通配：只参与精确匹配
            final StringBuilder regex = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) regex.append("(.+?)");
                regex.append(Pattern.quote(parts[i]));
            }
            ENTRIES.add(new Entry(Pattern.compile("^" + regex + "$"), parts, value));
        }
        // 长的模板优先：避免 "第 {} 项" 抢走 "第 {} 项：{}"
        ENTRIES.sort(Comparator.comparingInt((Entry e) -> longestLiteral(e.literalParts())).reversed());
    }

    private static int longestLiteral(String[] parts) {
        int max = 0;
        for (String p : parts) max = Math.max(max, p.length());
        return max;
    }

    /** 这个发送者用什么语言（{@code zh} / {@code en}）。 */
    static String localeOf(CommandSender sender) {
        if (sender instanceof Player player) {
            final String tag = player.locale() == null ? "" : player.locale().toString().toLowerCase(Locale.ROOT);
            return tag.startsWith("zh") ? "zh" : "en";
        }
        return fallback;
    }

    /** 翻译一条已经拼好的中文文案；译不出来就原样返回。 */
    static String render(CommandSender to, String chinese) {
        if (chinese == null || chinese.isEmpty()) return "";
        if ("zh".equals(localeOf(to))) return chinese;

        final String exact = EXACT.get(chinese);
        if (exact != null) return exact;

        for (Entry entry : ENTRIES) {
            final Matcher m = entry.pattern().matcher(chinese);
            if (!m.matches()) continue;
            String out = entry.translation();
            for (int i = 1; i <= m.groupCount(); i++) {
                out = out.replace("{" + (i - 1) + "}", m.group(i));
            }
            return out;
        }
        return chinese;   // 没翻译：露中文也比露 key 好
    }

    // =====================================================================
    // 便捷包装（顺手把中文原文翻掉）
    // =====================================================================

    static Component text(CommandSender to, String chinese, NamedTextColor color) {
        return Component.text(render(to, chinese), color);
    }
}

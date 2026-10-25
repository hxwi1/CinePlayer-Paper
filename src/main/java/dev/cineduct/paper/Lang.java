// SPDX-License-Identifier: MIT
// Copyright (c) 2026 hxwi1

package dev.cineduct.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文案表：**一个文件、一种语言、服主自己改**。
 *
 * <p>文件是 {@code plugins/CinePlay/lang.yml}（首次启动自动生成，内置默认值＝英文）。
 * 想换中文/日文/别的，直接改右边的值就完事 —— 没有语言开关、没有按玩家语言切换、
 * 也没有 {@code zh_cn.yml} / {@code en_us.yml} 这种命名。
 *
 * <h2>key 长什么样</h2>
 *
 * <p>key 就是**代码里那句原文**（中文），右边是实际显示的内容。所以：
 * <pre>
 * "屏幕 {} 已创建：{}": "Screen {0} created: {1}"
 * </pre>
 * 规则：
 * <ul>
 *   <li><b>key 不要改</b> —— 它就是代码里的原文；改了这行就再也匹配不上，会退回显示原文</li>
 *   <li>{@code {}} 是通配（代码里拼进去的参数），数量必须和右边的 {@code {0}} {@code {1}} 对得上</li>
 *   <li>右边留空或删掉某行 = 这条退回原文显示</li>
 * </ul>
 *
 * <p>匹配顺序：先精确匹配，再按带 {@code {}} 的模板匹配（字面量长的优先，
 * 避免 {@code 第 {} 项} 抢走 {@code 第 {} 项：{}}）。匹配不到就显示代码里的原文。
 */
final class Lang {

    /** 文件名（数据目录 + jar 内同名资源）。 */
    private static final String FILE = "lang.yml";

    private record Entry(Pattern pattern, String[] literalParts, String translation) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Map<String, String> EXACT = new LinkedHashMap<>();
    private static volatile boolean loaded;

    private Lang() {
    }

    /** 载入 jar 内的默认表 + 数据目录的覆盖表（后者优先）。 */
    static void load(JavaPlugin plugin) {
        ENTRIES.clear();
        EXACT.clear();
        loaded = true;

        readInto(plugin, true);
        readInto(plugin, false);

        if (EXACT.isEmpty()) {
            plugin.getLogger().warning("语言文件为空或读不出来，界面将显示代码里的原文");
        }
    }

    private static void readInto(JavaPlugin plugin, boolean fromJar) {
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (fromJar) {
                try (InputStream in = plugin.getResource(FILE)) {
                    if (in == null) return;
                    yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } else {
                final File f = new File(plugin.getDataFolder(), FILE);
                if (!f.isFile()) return;
                yaml.load(f);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("读取 " + (fromJar ? "内置" : "数据目录") + " lang.yml 失败："
                    + e.getMessage());
            return;
        }
        apply(yaml);
    }

    private static void apply(YamlConfiguration yaml) {
        for (String key : yaml.getKeys(false)) {
            final String value = yaml.getString(key);
            if (value == null || value.isBlank()) continue;      // 留空 = 用原文
            EXACT.put(key, value);

            final String[] parts = key.split(Pattern.quote("{}"), -1);
            if (parts.length == 1) continue;                     // 无通配：只参与精确匹配
            final StringBuilder regex = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) regex.append("(.+?)");
                regex.append(Pattern.quote(parts[i]));
            }
            ENTRIES.add(new Entry(Pattern.compile("^" + regex + "$"), parts, value));
        }
        // 长的模板优先
        ENTRIES.sort(Comparator.comparingInt((Entry e) -> longestLiteral(e.literalParts())).reversed());
    }

    private static int longestLiteral(String[] parts) {
        int max = 0;
        for (String p : parts) max = Math.max(max, p.length());
        return max;
    }

    /** 查表；查不到原样返回（原文通常就是中文，服主没翻译的条目会看到中文）。 */
    static String render(String source) {
        if (source == null || source.isEmpty()) return "";
        if (!loaded) return source;

        final String exact = EXACT.get(source);
        if (exact != null) return exact;

        for (Entry entry : ENTRIES) {
            final Matcher m = entry.pattern().matcher(source);
            if (!m.matches()) continue;
            String out = entry.translation();
            for (int i = 1; i <= m.groupCount(); i++) {
                out = out.replace("{" + (i - 1) + "}", m.group(i));
            }
            return out;
        }
        return source;
    }

    static Component text(String source, NamedTextColor color) {
        return Component.text(render(source), color);
    }
}

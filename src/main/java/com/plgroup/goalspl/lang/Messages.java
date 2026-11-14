package com.plgroup.goalspl.lang;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Textos del plugin en MiniMessage. El servidor usa {@code plugins/GoalsPL/messages.yml}, creado la
 * primera vez a partir del idioma elegido en config.yml ({@code lang/messages_<idioma>.yml} del JAR). Al cargar
 * se añaden las claves nuevas sin tocar las que la administración haya cambiado; si falta una, se usa la del JAR.
 */
public final class Messages {

    /** Idiomas incluidos en el JAR. */
    public static final List<String> LANGUAGES = List.of("es", "en");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final YamlConfiguration lang;
    private final YamlConfiguration fallback;
    private final Component prefix;

    private Messages(YamlConfiguration lang, YamlConfiguration fallback) {
        this.lang = lang;
        this.fallback = fallback;
        this.prefix = MM.deserialize(raw("prefix"));
    }

    public static Messages load(JavaPlugin plugin, String language) {
        String chosen = LANGUAGES.contains(language) ? language : "es";
        if (!chosen.equals(language)) {
            plugin.getLogger().warning("Idioma desconocido '" + language + "', se usa es.");
        }
        String resource = "lang/messages_" + chosen + ".yml";
        YamlConfiguration defaults = readResource(plugin, resource);
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource(resource, false);
            File copied = new File(plugin.getDataFolder(), resource);
            if (!copied.renameTo(file)) {
                plugin.getLogger().warning("No se pudo crear messages.yml; se usan los textos del JAR.");
            }
            File langDir = new File(plugin.getDataFolder(), "lang");
            String[] left = langDir.list();
            if (left != null && left.length == 0) {
                langDir.delete();
            }
        }
        YamlConfiguration lang = YamlConfiguration.loadConfiguration(file);
        String fileLang = lang.getString("language", chosen);
        if (!fileLang.equals(chosen)) {
            plugin.getLogger().warning("messages.yml está en '" + fileLang + "' y config.yml pide '" + chosen
                    + "'. Borra messages.yml para regenerarlo en el idioma nuevo.");
        }
        int added = 0;
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !lang.contains(key)) {
                lang.set(key, defaults.get(key));
                added++;
            }
        }
        if (added > 0 && file.exists()) {
            try {
                lang.save(file);
                plugin.getLogger().info("Añadidas " + added + " claves nuevas a messages.yml");
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo guardar messages.yml: " + e.getMessage());
            }
        }
        return new Messages(lang, defaults);
    }

    public static YamlConfiguration readResource(JavaPlugin plugin, String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new YamlConfiguration();
        }
    }

    /** Variable de texto literal (lo que escriba alguien no se interpreta como formato). */
    public static TagResolver ph(String name, Object value) {
        return Placeholder.unparsed(name, value == null ? "" : String.valueOf(value));
    }

    /** Variable con formato MiniMessage escrito por la administración (nombre de la campaña…). */
    public static TagResolver parsed(String name, String value) {
        return Placeholder.parsed(name, value == null ? "" : value);
    }

    public static TagResolver component(String name, Component value) {
        return Placeholder.component(name, value);
    }

    public String raw(String key) {
        String v = lang.getString(key);
        if (v == null) {
            v = fallback.getString(key, "<red>[" + key + "]");
        }
        return v;
    }

    public List<String> rawList(String key) {
        if (lang.isList(key)) {
            return lang.getStringList(key);
        }
        if (lang.isString(key)) {
            return List.of(lang.getString(key, ""));
        }
        if (fallback.isList(key)) {
            return fallback.getStringList(key);
        }
        return fallback.isString(key) ? List.of(fallback.getString(key, "")) : List.of("<red>[" + key + "]");
    }

    public boolean has(String key) {
        return lang.contains(key) || fallback.contains(key);
    }

    public static Component mini(String text, TagResolver... resolvers) {
        return MM.deserialize(text == null ? "" : text, resolvers);
    }

    public static String escape(String text) {
        return MM.escapeTags(text == null ? "" : text);
    }

    public static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public Component text(String key, TagResolver... resolvers) {
        return MM.deserialize(raw(key), resolvers);
    }

    /** Texto para nombres de objeto (sin la cursiva por defecto). */
    public Component item(String key, TagResolver... resolvers) {
        return noItalic(text(key, resolvers));
    }

    /** Líneas para lore (sin cursiva). */
    public List<Component> lore(String key, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        for (String line : rawList(key)) {
            out.add(noItalic(MM.deserialize(line, resolvers)));
        }
        return out;
    }

    /** Mensaje con prefijo. */
    public Component msg(String key, TagResolver... resolvers) {
        return prefix.append(text(key, resolvers));
    }

    public void send(Audience to, String key, TagResolver... resolvers) {
        to.sendMessage(msg(key, resolvers));
    }

    /** Envía sin prefijo (líneas de listas, ayuda…). */
    public void line(Audience to, String key, TagResolver... resolvers) {
        to.sendMessage(text(key, resolvers));
    }
}

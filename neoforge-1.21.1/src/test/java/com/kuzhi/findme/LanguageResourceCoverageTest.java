package com.kuzhi.findme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LanguageResourceCoverageTest {
    private static final Pattern STATIC_SCREEN_KEY =
            Pattern.compile("screen\\.find_me\\.[A-Za-z0-9_.]+");
    private static final Path LANG_ROOT =
            Path.of("src", "main", "resources", "assets", "find_me", "lang");

    @Test
    void englishAndChineseExposeTheSameKeys() throws IOException {
        assertEquals(languageKeys("zh_cn.json"), languageKeys("en_us.json"));
    }

    @Test
    void everyStaticScreenKeyUsedByJavaHasBothTranslations() throws IOException {
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = STATIC_SCREEN_KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String key = matcher.group();
                    if (!key.endsWith(".")) used.add(key);
                }
            }
        }

        for (String language : new String[]{"zh_cn.json", "en_us.json"}) {
            Set<String> missing = new TreeSet<>(used);
            missing.removeAll(languageKeys(language));
            assertTrue(missing.isEmpty(), language + " is missing " + missing);
        }
    }

    private static Set<String> languageKeys(String fileName) throws IOException {
        try (Reader reader = Files.newBufferedReader(LANG_ROOT.resolve(fileName), StandardCharsets.UTF_8)) {
            JsonObject language = JsonParser.parseReader(reader).getAsJsonObject();
            return new TreeSet<>(language.keySet());
        }
    }
}

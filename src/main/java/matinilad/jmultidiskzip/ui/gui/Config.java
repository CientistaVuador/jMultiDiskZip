/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <https://unlicense.org>
 */
package matinilad.jmultidiskzip.ui.gui;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Cien
 */
public class Config {

    private static final Logger LOGGER = Logger.getLogger(Config.class.getName());

    public static final Properties DEFAULTS;

    static {
        Properties prop = new Properties();
        try {
            final InputStream in = Config.class.getResourceAsStream("default.properties");
            if (in == null) {
                throw new IOException("default.properties not found");
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                prop.load(reader);
            }
        } catch (IOException | IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "Failed to load default.properties", ex);
        }
        DEFAULTS = prop;
    }

    public static final Path DIRECTORY;

    static {
        String os = System.getProperty("os.name");
        if (os != null) {
            os = os.toLowerCase();
        } else {
            os = "";
        }

        String userHome = System.getProperty("user.home");
        if (userHome == null) {
            userHome = "";
        }

        Path homePath = Path.of(userHome);
        if (os.startsWith("windows") && !userHome.isEmpty()) {
            homePath = homePath.resolve(Path.of("AppData", "Roaming"));
        }

        String directory = DEFAULTS.getProperty("program.directory", ".jmultidiskzip_fallback");
        String version = DEFAULTS.getProperty("program.version", "0.0.0_fallback");

        Path configPath = homePath.resolve(Path.of(directory, version));
        try {
            Files.createDirectories(configPath);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to create directories for config directory", ex);
        }
        DIRECTORY = configPath;
    }

    public static final Path CONFIG_FILE = DIRECTORY.resolve("config.properties");

    public static final Properties CONFIG = new Properties(DEFAULTS);
    static {
        try {
            if (Files.isRegularFile(CONFIG_FILE)) {
                try (Reader reader = new InputStreamReader(Files.newInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                    CONFIG.load(reader);
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "Failed to load config file", ex);
        }
    }

    static {
        Thread saveConfig = new Thread(() -> {
            try {
                Files.createDirectories(DIRECTORY);
                try (Writer writer = new OutputStreamWriter(Files.newOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                    CONFIG.store(writer, null);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Failed to save config file", ex);
            }
        });
        Runtime.getRuntime().addShutdownHook(saveConfig);
    }
    
    public static String get(String property) {
        return CONFIG.getProperty(property);
    }
    
    public static String get(String property, String defaultValue) {
        return CONFIG.getProperty(property, defaultValue);
    }
    
    public static int getInt(String property, int defaultValue) {
        String result = get(property);
        if (result == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(result);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
    
    public static long getLong(String property, long defaultValue) {
        String result = get(property);
        if (result == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(result);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
    
    public static boolean getBoolean(String property, boolean defaultValue) {
        String result = get(property);
        if (result == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(result);
    }
    
    public static <T> T getObject(String property, Function<String, T> function, T defaultValue) {
        String result = get(property);
        if (result == null) {
            return defaultValue;
        }
        if (result.equalsIgnoreCase("NULL")) {
            return null;
        }
        if (result.length() >= "VALUE?".length() && result.substring(0, "VALUE?".length()).equalsIgnoreCase("VALUE?")) {
            result = result.substring("VALUE?".length());
        }
        try {
            T after = function.apply(result);
            if (after == null) {
                return defaultValue;
            }
            return after;
        } catch (Throwable t) {
            return defaultValue;
        }
    }
    
    public static void set(String property, String value) {
        if (value == null) {
            CONFIG.remove(property);
            return;
        }
        CONFIG.setProperty(property, value);
    }
    
    public static void set(String property, int value) {
        CONFIG.setProperty(property, Integer.toString(value));
    }
    
    public static void set(String property, long value) {
        CONFIG.setProperty(property, Long.toString(value));
    }
    
    public static void set(String property, boolean value) {
        CONFIG.setProperty(property, Boolean.toString(value));
    }
    
    public static <T> void setObject(String property, Function<T, String> function, T obj) {
        if (obj == null) {
            CONFIG.setProperty(property, "NULL");
            return;
        }
        String after = function.apply(obj);
        if (after == null) {
            CONFIG.remove(property);
            return;
        }
        CONFIG.setProperty(property, "VALUE?"+after);
    }
}

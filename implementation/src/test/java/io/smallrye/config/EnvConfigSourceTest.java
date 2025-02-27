/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.smallrye.config;

import static io.smallrye.config.Converters.BOOLEAN_CONVERTER;
import static io.smallrye.config.Converters.STRING_CONVERTER;
import static io.smallrye.config.KeyValuesConfigSource.config;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.StreamSupport.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import io.smallrye.config.EnvConfigSource.EnvName;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2018 Red Hat inc.
 */
class EnvConfigSourceTest {
    @Test
    void conversionOfEnvVariableNames() {
        String envProp = System.getenv("SMALLRYE_MP_CONFIG_PROP");
        assertNotNull(envProp);

        ConfigSource cs = new EnvConfigSource();
        assertEquals(envProp, cs.getValue("SMALLRYE_MP_CONFIG_PROP"));
        // the config source returns only the name of the actual env variable
        assertTrue(cs.getPropertyNames().contains("SMALLRYE_MP_CONFIG_PROP"));

        assertEquals(envProp, cs.getValue("smallrye_mp_config_prop"));
        assertFalse(cs.getPropertyNames().contains("smallrye_mp_config_prop"));

        assertEquals(envProp, cs.getValue("smallrye.mp.config.prop"));
        assertTrue(cs.getPropertyNames().contains("smallrye.mp.config.prop"));

        assertEquals(envProp, cs.getValue("SMALLRYE.MP.CONFIG.PROP"));
        assertFalse(cs.getPropertyNames().contains("SMALLRYE.MP.CONFIG.PROP"));

        assertEquals(envProp, cs.getValue("smallrye-mp-config-prop"));
        assertFalse(cs.getPropertyNames().contains("smallrye-mp-config-prop"));

        assertEquals(envProp, cs.getValue("SMALLRYE-MP-CONFIG-PROP"));
        assertFalse(cs.getPropertyNames().contains("SMALLRYE-MP-CONFIG-PROP"));

        assertEquals("1234", cs.getValue("smallrye_mp_config_prop_lower"));
        assertTrue(cs.getPropertyNames().contains("smallrye_mp_config_prop_lower"));

        assertEquals("1234", cs.getValue("smallrye/mp/config/prop"));
    }

    @Test
    void profileEnvVariables() {
        assertNotNull(System.getenv("SMALLRYE_MP_CONFIG_PROP"));
        assertNotNull(System.getenv("_ENV_SMALLRYE_MP_CONFIG_PROP"));

        SmallRyeConfig config = new SmallRyeConfigBuilder().addDefaultSources().withProfile("env").build();

        assertEquals("5678", config.getRawValue("smallrye.mp.config.prop"));
    }

    @Test
    void empty() {
        SmallRyeConfig config = new SmallRyeConfigBuilder().addDefaultSources().build();
        assertThrows(NoSuchElementException.class, () -> config.getValue("SMALLRYE_MP_CONFIG_EMPTY", String.class));
        assertTrue(
                stream(config.getPropertyNames().spliterator(), false).collect(toList()).contains("SMALLRYE_MP_CONFIG_EMPTY"));

        Optional<ConfigSource> envConfigSource = config.getConfigSource("EnvConfigSource");

        assertTrue(envConfigSource.isPresent());
        assertEquals("", envConfigSource.get().getValue("SMALLRYE_MP_CONFIG_EMPTY"));
    }

    @Test
    void ordinal() {
        SmallRyeConfig config = new SmallRyeConfigBuilder().withSources(new EnvConfigSource()).build();
        ConfigSource configSource = config.getConfigSources().iterator().next();

        assertTrue(configSource instanceof EnvConfigSource);
        assertEquals(301, configSource.getOrdinal());
    }

    @Test
    void indexed() {
        Map<String, String> env = new HashMap<>() {
            {
                put("INDEXED_0_", "foo");
                put("INDEXED_0__PROP", "bar");
                put("INDEXED_0__PROPS_0_", "0");
                put("INDEXED_0__PROPS_1_", "1");
            }
        };

        EnvConfigSource envConfigSource = new EnvConfigSource(env, 300);
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(envConfigSource)
                .build();

        assertTrue(config.getValues("indexed", String.class, ArrayList::new).contains("foo"));
        assertTrue(config.getValues("indexed[0].props", String.class, ArrayList::new).contains("0"));
        assertTrue(config.getValues("indexed[0].props", String.class, ArrayList::new).contains("1"));
    }

    @Test
    void numbers() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new EnvConfigSource(Map.of("999_MY_VALUE", "foo", "_999_MY_VALUE", "bar"), 300))
                .build();

        assertEquals("foo", config.getRawValue("999.my.value"));
        assertEquals("foo", config.getRawValue("999_MY_VALUE"));
        assertEquals("bar", config.getRawValue("_999_MY_VALUE"));
        assertEquals("bar", config.getRawValue("%999.my.value"));
    }

    @Test
    void map() {
        Map<String, String> env = new HashMap<>() {
            {
                put("TEST_LANGUAGE__DE_ETR__", "Einfache Sprache");
                put("TEST_LANGUAGE_PT_BR", "FROM ENV");
            }
        };

        EnvConfigSource envConfigSource = new EnvConfigSource(env, 300);
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(config("test.language.pt-br", "value"))
                .withSources(envConfigSource)
                .build();

        assertEquals("Einfache Sprache", config.getRawValue("test.language.\"de.etr\""));

        Map<String, String> map = config.getValues("test.language", STRING_CONVERTER, STRING_CONVERTER);
        assertEquals(map.get("de.etr"), "Einfache Sprache");
        assertEquals(map.get("pt-br"), "FROM ENV");
    }

    @Test
    void envEquals() {
        assertTrue(EnvName.equals("", new String("")));
        assertTrue(EnvName.equals(" ", new String(" ")));
        assertFalse(EnvName.equals(" ", new String("foo.bar")));
        assertFalse(EnvName.equals(" ", new String("FOO_BAR")));
        assertFalse(EnvName.equals("foo.bar", new String("")));
        assertFalse(EnvName.equals("FOO_BAR", new String("")));

        assertFalse(EnvName.equals("BAR", new String("foo.bar")));
        assertFalse(EnvName.equals("foo.bar", new String("BAR")));

        assertTrue(envSourceEquals("FOO_BAR", new String("FOO_BAR")));
        assertTrue(envSourceEquals("FOO_BAR", new String("foo.bar")));
        assertTrue(envSourceEquals("FOO_BAR", new String("FOO.BAR")));
        assertTrue(envSourceEquals("FOO_BAR", new String("foo-bar")));
        assertTrue(envSourceEquals("FOO_BAR", new String("foo_bar")));

        assertTrue(EnvName.equals("foo.bar", new String("foo.bar")));
        assertTrue(EnvName.equals("foo-bar", new String("foo-bar")));
        assertTrue(EnvName.equals("foo.bar", new String("FOO_BAR")));
        assertTrue(EnvName.equals("FOO.BAR", new String("FOO_BAR")));
        assertTrue(EnvName.equals("foo-bar", new String("FOO_BAR")));
        assertTrue(EnvName.equals("foo_bar", new String("FOO_BAR")));

        assertTrue(EnvName.equals("FOO__BAR__BAZ", new String("foo.\"bar\".baz")));
        assertTrue(EnvName.equals("foo.\"bar\".baz", new String("FOO__BAR__BAZ")));
        assertTrue(envSourceEquals("FOO__BAR__BAZ", new String("foo.\"bar\".baz")));
        assertTrue(EnvName.equals("FOO__BAR__BAZ_0__Z_0_", new String("foo.\"bar\".baz[0].z[0]")));
        assertTrue(envSourceEquals("FOO__BAR__BAZ_0__Z_0_", new String("foo.\"bar\".baz[0].z[0]")));

        assertTrue(EnvName.equals("_DEV_FOO_BAR", new String("%dev.foo.bar")));
        assertTrue(EnvName.equals("%dev.foo.bar", new String("_DEV_FOO_BAR")));
        assertTrue(envSourceEquals("_DEV_FOO_BAR", new String("%dev.foo.bar")));
        assertTrue(EnvName.equals("_ENV_SMALLRYE_MP_CONFIG_PROP", new String("_ENV_SMALLRYE_MP_CONFIG_PROP")));
        assertTrue(EnvName.equals("%env.smallrye.mp.config.prop", new String("%env.smallrye.mp.config.prop")));
        assertTrue(EnvName.equals("_ENV_SMALLRYE_MP_CONFIG_PROP", new String("%env.smallrye.mp.config.prop")));
        assertTrue(EnvName.equals("%env.smallrye.mp.config.prop", new String("_ENV_SMALLRYE_MP_CONFIG_PROP")));
        assertTrue(envSourceEquals("%env.smallrye.mp.config.prop", new String("%env.smallrye.mp.config.prop")));
        assertTrue(envSourceEquals("_ENV_SMALLRYE_MP_CONFIG_PROP", new String("%env.smallrye.mp.config.prop")));

        assertTrue(EnvName.equals("indexed[0]", new String("indexed[0]")));
        assertTrue(EnvName.equals("INDEXED_0_", new String("INDEXED_0_")));
        assertTrue(EnvName.equals("indexed[0]", new String("INDEXED_0_")));
        assertTrue(EnvName.equals("INDEXED_0_", new String("indexed[0]")));
        assertTrue(envSourceEquals("indexed[0]", new String("indexed[0]")));
        assertTrue(envSourceEquals("INDEXED_0_", new String("INDEXED_0_")));
        assertTrue(envSourceEquals("INDEXED_0_", new String("indexed[0]")));
        assertTrue(envSourceEquals("foo.bar.indexed[0]", new String("foo.bar.indexed[0]")));
        assertTrue(envSourceEquals("FOO_BAR_INDEXED_0_", new String("foo.bar.indexed[0]")));
        assertTrue(envSourceEquals("foo.bar[0].indexed[0]", new String("foo.bar[0].indexed[0]")));
        assertTrue(envSourceEquals("FOO_BAR_0__INDEXED_0_", new String("foo.bar[0].indexed[0]")));

        assertTrue(EnvName.equals("env.\"quoted.key\".value", new String("env.\"quoted.key\".value")));
        assertTrue(EnvName.equals("ENV__QUOTED_KEY__VALUE", new String("ENV__QUOTED_KEY__VALUE")));
        assertTrue(EnvName.equals("ENV__QUOTED_KEY__VALUE", new String("env.\"quoted.key\".value")));
        assertTrue(EnvName.equals("env.\"quoted.key\".value", new String("ENV__QUOTED_KEY__VALUE")));
        assertTrue(EnvName.equals("env.\"quoted.key\".value", new String("env.\"quoted-key\".value")));
        assertTrue(EnvName.equals("env.\"quoted-key\".value", new String("env.\"quoted.key\".value")));
        assertTrue(envSourceEquals("env.\"quoted.key\".value", new String("env.\"quoted.key\".value")));
        assertTrue(envSourceEquals("ENV__QUOTED_KEY__VALUE", new String("ENV__QUOTED_KEY__VALUE")));
        assertTrue(envSourceEquals("ENV__QUOTED_KEY__VALUE", new String("env.\"quoted.key\".value")));
        assertTrue(envSourceEquals("env.\"quoted.key\".value", new String("env.\"quoted-key\".value")));
        assertTrue(envSourceEquals("env.\"quoted-key\".value", new String("env.\"quoted.key\".value")));

        assertTrue(EnvName.equals("TEST_LANGUAGE__DE_ETR__", new String("test.language.\"de.etr\"")));
        assertTrue(EnvName.equals("test.language.\"de.etr\"", new String("TEST_LANGUAGE__DE_ETR__")));
        assertEquals(new EnvName("TEST_LANGUAGE__DE_ETR_").hashCode(), new EnvName("test.language.\"de.etr\"").hashCode());

        assertTrue(envSourceEquals("SMALLRYE_MP_CONFIG_PROP", new String("smallrye/mp/config/prop")));
    }

    @Test
    void sameSemanticMeaning() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(config("foo.bar-baz", "fromOther"))
                .withSources(new EnvConfigSource(Map.of("FOO_BAR_BAZ", "fromEnv"), 300))
                .build();

        Set<String> properties = stream(config.getPropertyNames().spliterator(), false).collect(toSet());
        assertTrue(properties.contains("FOO_BAR_BAZ"));
        assertTrue(properties.contains("foo.bar-baz"));
        assertFalse(properties.contains("foo.bar.baz"));

        assertEquals("fromEnv", config.getRawValue("foo.bar-baz"));
    }

    @Test
    void sameNames() {
        assertTrue(envSourceEquals("FOOBAR", "foobar"));
        assertTrue(envSourceEquals("FOOBAR", "fooBar"));

        EnvConfigSource envConfigSource = new EnvConfigSource(
                Map.of("my_string_property", "lower", "MY_STRING_PROPERTY", "upper"), 100);
        assertEquals(2, envConfigSource.getProperties().size());
        assertEquals("lower", envConfigSource.getValue("my_string_property"));
        assertEquals("upper", envConfigSource.getValue("MY_STRING_PROPERTY"));
    }

    @Test
    void dashedEnvNames() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withMapping(DashedEnvNames.class)
                .withSources(new EnvConfigSource(Map.of(
                        "DASHED_ENV_NAMES_VALUE", "value",
                        "DASHED_ENV_NAMES_NESTED__DASHED_KEY__ANOTHER", "value"), 100))
                .build();

        DashedEnvNames mapping = config.getConfigMapping(DashedEnvNames.class);

        assertEquals("value", mapping.value());
        // Unfortunately, we still don't have a good way to determine if the Map key is dashed or not
        assertEquals("value", mapping.nested().get("dashed.key").another());
    }

    @Test
    void dottedDashedEnvNames() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withMapping(DashedEnvNames.class)
                .withSources(new EnvConfigSource(Map.of(
                        "dashed-env-names.value", "value",
                        "dashed-env-names.nested.dashed-key.another", "value"), 100))
                .build();

        DashedEnvNames mapping = config.getConfigMapping(DashedEnvNames.class);

        assertEquals("value", mapping.value());
        assertEquals("value", mapping.nested().get("dashed-key").another());
    }

    @ConfigMapping(prefix = "dashed-env-names")
    interface DashedEnvNames {
        String value();

        Map<String, Nested> nested();

        interface Nested {
            String another();
        }
    }

    private static boolean envSourceEquals(String name, String lookup) {
        return BOOLEAN_CONVERTER.convert(new EnvConfigSource(Map.of(name, "true"), 100).getValue(lookup));
    }
}

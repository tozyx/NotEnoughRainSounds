package com.tozyx.notenoughrainsounds.client.config;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SmoothPauseConfigTest {
    @TempDir Path directory;

    @Test void oldConfigWithoutPauseFieldKeepsTheDefaultWithoutRewritingTheFile() throws Exception {
        Path path = directory.resolve("notenoughrainsounds-client.json");
        String old = "{\"schemaVersion\":1,\"rain\":{\"enabled\":false},\"weather\":{\"rain\":{\"enabled\":false}}}";
        Files.writeString(path, old);
        var store = new ConfigStore(directory);
        assertTrue(store.reload());
        assertTrue(store.current().smoothPause);
        assertFalse(store.current().rain.enabled);
        assertFalse(store.current().weather.rain.enabled);
        assertEquals(old, Files.readString(path));
    }

    @Test void theToggleRoundTripsOffAndOnWithoutChangingOtherSettings() throws Exception {
        var store = new ConfigStore(directory);
        assertTrue(store.reload());
        var draft = store.snapshot();
        draft.smoothPause = false;
        draft.weather.rain.volume = .4;
        store.save(draft);
        var reloaded = new ConfigStore(directory);
        assertTrue(reloaded.reload());
        assertFalse(reloaded.current().smoothPause);
        assertEquals(.4, reloaded.current().weather.rain.volume);
        var enabled = reloaded.snapshot();
        enabled.smoothPause = true;
        reloaded.save(enabled);
        assertTrue(store.reload());
        assertTrue(store.current().smoothPause);
        assertEquals(1, store.current().schemaVersion);
    }

    @Test void cancellingTheEditingDraftDoesNotChangeTheLivePauseOptionOrFile() throws Exception {
        var store = new ConfigStore(directory);
        assertTrue(store.reload());
        Path path = directory.resolve("notenoughrainsounds-client.json");
        String before = Files.readString(path);
        var draft = store.snapshot();
        draft.smoothPause = false;
        assertTrue(store.current().smoothPause);
        assertEquals(before, Files.readString(path));
    }

    @Test void freshConfigWritesTheDefaultOnOption() throws Exception {
        var store = new ConfigStore(directory);
        assertTrue(store.reload());
        var json = JsonParser.parseString(Files.readString(directory.resolve("notenoughrainsounds-client.json"))).getAsJsonObject();
        assertTrue(json.get("smoothPause").getAsBoolean());
    }
}

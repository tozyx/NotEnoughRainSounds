package com.tozyx.notenoughrainsounds.client.rain;

import com.tozyx.notenoughrainsounds.NotEnoughRainSounds;
import com.tozyx.notenoughrainsounds.client.config.SoundConfig;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Budgeted surface detection; playback reuses this query to reject stale samples. */
public final class RainSampler {
    private final Map<Long, RainSurface> surfaces = new HashMap<>();
    private final BoatRainSampler boats = new BoatRainSampler();
    private ClientLevel level;
    private ColumnScanPlan plan;
    private BlockRainSampler blocks;
    private SoundConfig.Rain settings;
    private BlockPos lastCenter;
    private long ticks;

    public void configure(SoundConfig.Rain settings) {
        this.settings = settings;
        plan = new ColumnScanPlan(settings.radius);
        blocks = new BlockRainSampler(settings);
        clear();
    }

    public void clear() {
        surfaces.clear();
        boats.clear();
        level = null;
        lastCenter = null;
        ticks = 0;
        if (plan != null) plan.reset();
    }

    public List<RainSurface> surfaces(ClientLevel world, Vec3 listener, Entity vehicle, Set<RainSound> available) {
        List<RainSurface> candidates = new ArrayList<>(surfaces.values());
        if (settings != null && settings.boatImpacts && world.getRainLevel(1.0F) > 0
                && (available.contains(RainSound.WOOD) || available.contains(RainSound.BAMBOO))) {
            candidates.addAll(boats.sample(world, listener, vehicle, settings, ticks, available));
        }
        return candidates;
    }

    public void tick(ClientLevel world, Vec3 listener) {
        if (settings == null) return;
        if (world != level) {
            clear();
            level = world;
        }
        if (!settings.enabled || world.getRainLevel(1.0F) <= 0.0F) {
            surfaces.clear();
            boats.clear();
            ticks = 0;
            plan.reset();
            return;
        }
        BlockPos center = BlockPos.containing(listener);
        if (lastCenter != null && (Math.abs(center.getX() - lastCenter.getX()) > settings.radius
                || Math.abs(center.getZ() - lastCenter.getZ()) > settings.radius
                || Math.abs(center.getY() - lastCenter.getY()) > settings.verticalRange)) {
            surfaces.clear();
            boats.clear();
            plan.reset();
        }
        lastCenter = center;
        long currentTick = ticks++;
        // Keep samples for two full scan cycles; playback revalidates each chosen surface.
        long ttl = Math.max(40L, 2L * ((plan.size() + settings.scanBudget - 1) / settings.scanBudget)
                * settings.scanIntervalTicks);
        surfaces.values().removeIf(surface -> currentTick - surface.checkedAt() > ttl
                || !blocks.inside(surface, listener) || !world.hasChunkAt(surface.block()));
        if (currentTick % settings.scanIntervalTicks != 0) {
            if (settings.debugLogging && currentTick % 100 == 0) logSnapshot(world, listener);
            return;
        }
        int markers = 0;
        for (ColumnScanPlan.Offset offset : plan.next(settings.scanBudget)) {
            int x = center.getX() + offset.x();
            int z = center.getZ() + offset.z();
            RainSurface surface = blocks.inspect(world, listener, x, z, currentTick);
            long key = ((long) x << 32) | (z & 0xffffffffL);
            if (surface == null) surfaces.remove(key);
            else {
                surfaces.put(key, surface);
                if (settings.debugParticles && markers++ < 8) showMarker(world, surface);
            }
        }
        if (settings.debugLogging && currentTick % 100 == 0) logSnapshot(world, listener);
    }

    public RainSurface revalidate(ClientLevel world, Vec3 listener, RainSurface cached) {
        if (settings == null || !settings.enabled || world.getRainLevel(1.0F) <= 0) return null;
        if (cached.onBoat()) {
            if (!settings.boatImpacts || !(world.getEntity(cached.boatId()) instanceof Boat boat)) return null;
            return BoatRainSampler.inspect(world, listener, boat, cached.riddenBoat(), settings, ticks);
        }
        RainSurface current = blocks.inspect(world, listener, cached.block().getX(), cached.block().getZ(), ticks);
        long key = ((long) cached.block().getX() << 32) | (cached.block().getZ() & 0xffffffffL);
        // A removed/covered anchor must not remain a preferred stale candidate until the next scan pass.
        if (current == null) surfaces.remove(key);
        else surfaces.put(key, current);
        return current != null && current.block().equals(cached.block()) ? current : null;
    }

    private static void showMarker(ClientLevel world, RainSurface surface) {
        Vector3f color = switch (surface.match().material()) {
            case WOOD -> new Vector3f(0.7F, 0.4F, 0.15F);
            case BAMBOO -> new Vector3f(0.65F, 0.85F, 0.25F);
            case METAL -> new Vector3f(1.0F, 0.75F, 0.1F);
            case GLASS -> new Vector3f(0.4F, 1.0F, 1.0F);
            case FOLIAGE -> new Vector3f(0.2F, 1.0F, 0.2F);
            case WATER -> new Vector3f(0.2F, 0.4F, 1.0F);
            default -> new Vector3f(0.9F, 0.9F, 0.9F);
        };
        if (!surface.match().enhanced()) color.set(1.0F, 0.1F, 0.1F);
        Vec3 point = surface.position();
        world.addParticle(new DustParticleOptions(color, 0.8F), point.x, point.y + 0.1, point.z, 0, 0, 0);
    }

    private void logSnapshot(ClientLevel world, Vec3 listener) {
        Map<RainMaterial, Integer> counts = new EnumMap<>(RainMaterial.class);
        int enhanced = 0;
        int drips = 0;
        RainSurface nearest = null;
        for (RainSurface surface : surfaces.values()) {
            counts.merge(surface.match().material(), 1, Integer::sum);
            if (surface.match().enhanced()) enhanced++;
            if (surface.match().plantDrips()) drips++;
            if (nearest == null || surface.position().distanceToSqr(listener) < nearest.position().distanceToSqr(listener)) nearest = surface;
        }
        NotEnoughRainSounds.LOGGER.info("Rain scan: candidates={}, enhanced={}, plantDrips={}, materials={}, nearest={}",
                surfaces.size(), enhanced, drips, counts, nearest == null ? "none" : nearest.block() + " "
                        + world.getBlockState(nearest.block()) + " -> " + nearest.match().material().id());
    }
}

/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.world;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.MapMaker;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.ServerWorldEventHandler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.storage.AnvilSaveHandler;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.api.GameState;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.world.UnloadWorldEvent;
import org.spongepowered.api.util.file.CopyFileVisitor;
import org.spongepowered.api.util.file.DeleteFileVisitor;
import org.spongepowered.api.util.file.ForwardingFileVisitor;
import org.spongepowered.api.world.DimensionTypes;
import org.spongepowered.api.world.SerializationBehaviors;
import org.spongepowered.api.world.WorldArchetype;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.bridge.world.WorldInfoBridge;
import org.spongepowered.common.config.SpongeConfig;
import org.spongepowered.common.config.type.GeneralConfigBase;
import org.spongepowered.common.config.type.GlobalConfig;
import org.spongepowered.common.data.util.DataUtil;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.phase.general.GeneralPhase;
import org.spongepowered.common.interfaces.IMixinIntegratedServer;
import org.spongepowered.common.interfaces.IMixinMinecraftServer;
import org.spongepowered.common.bridge.entity.player.ServerPlayerEntityBridge;
import org.spongepowered.common.bridge.world.DimensionTypeBridge;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.bridge.world.ServerWorldBridge;
import org.spongepowered.common.bridge.world.WorldSettingsBridge;
import org.spongepowered.common.bridge.world.chunk.ServerChunkProviderBridge;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.util.SpongeHooks;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

public final class WorldManager {

    private static final DirectoryStream.Filter<Path> LEVEL_AND_SPONGE =
            entry -> Files.isDirectory(entry) && Files.exists(entry.resolve("level.dat")) && Files.exists(entry.resolve("level_sponge.dat"));

    private static final Int2ObjectMap<DimensionType> dimensionTypeByTypeId = new Int2ObjectOpenHashMap<>(3);
    private static final Int2ObjectMap<DimensionType> dimensionTypeByDimensionId = new Int2ObjectOpenHashMap<>(3);
    private static final Int2ObjectMap<Path> dimensionPathByDimensionId = new Int2ObjectOpenHashMap<>(3);
    private static final Int2ObjectOpenHashMap<WorldServer> worldByDimensionId = new Int2ObjectOpenHashMap<>(3);
    private static final Map<String, WorldProperties> worldPropertiesByFolderName = new HashMap<>(3);
    private static final Map<UUID, WorldProperties> worldPropertiesByWorldUuid =  new HashMap<>(3);
    private static final Map<Integer, String> worldFolderByDimensionId = new HashMap<>();
    private static final BiMap<String, UUID> worldUuidByFolderName =  HashBiMap.create(3);
    private static final IntSet usedDimensionIds = new IntOpenHashSet();
    private static final Map<WorldServer, WorldServer> weakWorldByWorld = new MapMaker().weakKeys().weakValues().concurrencyLevel(1).makeMap();
    private static final Queue<WorldServer> unloadQueue = new ArrayDeque<>();
    private static final Comparator<WorldServer>
            WORLD_SERVER_COMPARATOR =
            (world1, world2) -> {
                final int world1DimId = ((ServerWorldBridge) world1).bridge$getDimensionId();

                if (world2 == null) {
                    return world1DimId;
                }

                final int world2DimId = ((ServerWorldBridge) world2).bridge$getDimensionId();
                return world1DimId - world2DimId;
            };

    private static boolean isVanillaRegistered = false;
    private static int lastUsedDimensionId = 0;

    public static void registerVanillaTypesAndDimensions() {
        if (!isVanillaRegistered) {
            WorldManager.registerDimensionType(0, DimensionType.OVERWORLD);
            WorldManager.registerDimensionType(-1, DimensionType.NETHER);
            WorldManager.registerDimensionType(1, DimensionType.THE_END);

            WorldManager.registerDimension(0, DimensionType.OVERWORLD);
            WorldManager.registerDimension(-1, DimensionType.NETHER);
            WorldManager.registerDimension(1, DimensionType.THE_END);
        }

        isVanillaRegistered = true;
    }

    public static void registerDimensionType(DimensionType type) {
        checkNotNull(type);
        final Optional<Integer> optNextDimensionTypeId = getNextFreeDimensionTypeId();
        optNextDimensionTypeId.ifPresent(integer -> registerDimensionType(integer, type));

    }

    public static void registerDimensionType(int dimensionTypeId, DimensionType type) {
        checkNotNull(type);
        if (dimensionTypeByTypeId.containsKey(dimensionTypeId)) {
            return;
        }

        dimensionTypeByTypeId.put(dimensionTypeId, type);
    }

    private static Optional<Integer> getNextFreeDimensionTypeId() {
        Integer highestDimensionTypeId = null;

        for (Integer dimensionTypeId : dimensionTypeByTypeId.keySet()) {
            if (highestDimensionTypeId == null || highestDimensionTypeId < dimensionTypeId) {
                highestDimensionTypeId = dimensionTypeId;
            }
        }

        if (highestDimensionTypeId != null && highestDimensionTypeId < 127) {
            return Optional.of(++highestDimensionTypeId);
        }
        return Optional.empty();
    }

    public static Integer getNextFreeDimensionId() {
        int next = lastUsedDimensionId;
        while (usedDimensionIds.contains(next) || !checkAvailable(next)) {
            next++;
        }
        return lastUsedDimensionId = next;
    }

    private static boolean checkAvailable(int dimensionId) {
        if (worldByDimensionId.containsKey(dimensionId)) {
            usedDimensionIds.add(dimensionId);
            return false;
        }
        return true;
    }

    public static void registerDimension(int dimensionId, DimensionType type) {
        checkNotNull(type);
        if (!dimensionTypeByTypeId.containsValue(type)) {
            return;
        }

        if (dimensionTypeByDimensionId.containsKey(dimensionId)) {
            return;
        }
        dimensionTypeByDimensionId.put(dimensionId, type);
        if (dimensionId >= 0) {
            usedDimensionIds.add(dimensionId);
        }
    }

    public static void unregisterDimension(int dimensionId) {
        if (!dimensionTypeByDimensionId.containsKey(dimensionId))
        {
            throw new IllegalArgumentException("Failed to unregister dimension [" + dimensionId + "] as it is not registered!");
        }
        dimensionTypeByDimensionId.remove(dimensionId);
    }

    private static void registerVanillaDimensionPaths(final Path savePath) {
        WorldManager.registerDimensionPath(0, savePath);
        WorldManager.registerDimensionPath(-1, savePath.resolve("DIM-1"));
        WorldManager.registerDimensionPath(1, savePath.resolve("DIM1"));
    }

    public static void registerDimensionPath(int dimensionId, Path dimensionDataRoot) {
        checkNotNull(dimensionDataRoot);
        dimensionPathByDimensionId.put(dimensionId, dimensionDataRoot);
    }

    public static Path getDimensionPath(int dimensionId) {
        return dimensionPathByDimensionId.get(dimensionId);
    }

    public static Optional<DimensionType> getDimensionType(int dimensionId) {
        return Optional.ofNullable(dimensionTypeByDimensionId.get(dimensionId));
    }

    public static Optional<DimensionType> getDimensionTypeByTypeId(int dimensionTypeId) {
        return Optional.ofNullable(dimensionTypeByTypeId.get(dimensionTypeId));
    }

    public static Optional<DimensionType> getDimensionType(Class<? extends WorldProvider> providerClass) {
        checkNotNull(providerClass);
        for (Object rawDimensionType : dimensionTypeByTypeId.values()) {
            final DimensionType dimensionType = (DimensionType) rawDimensionType;
            if (((org.spongepowered.api.world.DimensionType) (Object) dimensionType).getDimensionClass().equals(providerClass)) {
                return Optional.of(dimensionType);
            }
        }

        return Optional.empty();
    }

    public static Collection<DimensionType> getDimensionTypes() {
        return dimensionTypeByTypeId.values();
    }

    public static int[] getRegisteredDimensionIdsFor(DimensionType type) {
        return dimensionTypeByDimensionId.int2ObjectEntrySet().stream()
                .filter(entry -> entry.getValue() == type)
                .mapToInt(Int2ObjectMap.Entry::getIntKey)
                .toArray();
    }

    public static int[] getRegisteredDimensionIds() {
        return dimensionTypeByDimensionId.keySet().toIntArray();
    }

    @Nullable
    private static Path getWorldFolder(DimensionType dimensionType, int dimensionId) {
        return dimensionPathByDimensionId.get(dimensionId);
    }

    public static boolean isDimensionRegistered(int dimensionId) {
        return dimensionTypeByDimensionId.containsKey(dimensionId);
    }

    private static Map<Integer, DimensionType> sortedDimensionMap() {
        Int2ObjectMap<DimensionType> copy = new Int2ObjectOpenHashMap<>(dimensionTypeByDimensionId);

        HashMap<Integer, DimensionType> newMap = new LinkedHashMap<>();

        newMap.put(0, copy.remove(0));

        DimensionType removed = copy.remove(-1);
        if (removed != null) {
            newMap.put(-1, removed);
        }

        removed = copy.remove(1);
        if (removed != null) {
            newMap.put(1, removed);
        }

        int[] ids = copy.keySet().toIntArray();
        Arrays.sort(ids);

        for (int id : ids) {
            newMap.put(id, copy.get(id));
        }

        return newMap;
    }

    public static ObjectIterator<Int2ObjectMap.Entry<WorldServer>> worldsIterator() {
        return worldByDimensionId.int2ObjectEntrySet().fastIterator();
    }

    public static Collection<WorldServer> getWorlds() {
        return worldByDimensionId.values();
    }

    public static Optional<WorldServer> getWorldByDimensionId(int dimensionId) {
        return Optional.ofNullable(worldByDimensionId.get(dimensionId));
    }

    public static Optional<String> getWorldFolderByDimensionId(int dimensionId) {
        return Optional.ofNullable(worldFolderByDimensionId.get(dimensionId));
    }

    public static int[] getLoadedWorldDimensionIds() {
        return worldByDimensionId.keySet().toIntArray();
    }

    public static Optional<WorldServer> getWorld(String worldName) {
        for (WorldServer worldServer : getWorlds()) {
            final org.spongepowered.api.world.World apiWorld = (org.spongepowered.api.world.World) worldServer;
            if (apiWorld.getName().equals(worldName)) {
                return Optional.of(worldServer);
            }
        }
        return Optional.empty();
    }

    private static void registerWorldProperties(WorldProperties properties) {
        checkNotNull(properties);
        worldPropertiesByFolderName.put(properties.getWorldName(), properties);
        worldPropertiesByWorldUuid.put(properties.getUniqueId(), properties);
        worldUuidByFolderName.put(properties.getWorldName(), properties.getUniqueId());
        worldFolderByDimensionId.put(((WorldInfoBridge) properties).getDimensionId(), properties.getWorldName());
        usedDimensionIds.add(((WorldInfoBridge) properties).getDimensionId());
    }

    public static void unregisterWorldProperties(WorldProperties properties, boolean freeDimensionId) {
        checkNotNull(properties);
        worldPropertiesByFolderName.remove(properties.getWorldName());
        worldPropertiesByWorldUuid.remove(properties.getUniqueId());
        worldUuidByFolderName.remove(properties.getWorldName());
        worldFolderByDimensionId.remove(((WorldInfoBridge) properties).getDimensionId());
        if (((WorldInfoBridge) properties).getDimensionId() != null && freeDimensionId) {
            usedDimensionIds.remove(((WorldInfoBridge) properties).getDimensionId());
        }
    }

    // used by SpongeForge client
    public static void unregisterAllWorldSettings() {
        worldPropertiesByFolderName.clear();
        worldPropertiesByWorldUuid.clear();
        worldUuidByFolderName.clear();
        worldByDimensionId.clear();
        worldFolderByDimensionId.clear();
        dimensionTypeByDimensionId.clear();
        dimensionPathByDimensionId.clear();
        usedDimensionIds.clear();
        weakWorldByWorld.clear();

        isVanillaRegistered = false;
        // This is needed to ensure that DimensionType is usable by GuiListWorldSelection, which is only ever used when the server isn't running
        registerVanillaTypesAndDimensions();
    }

    public static Optional<WorldProperties> getWorldProperties(String folderName) {
        checkNotNull(folderName);
        return Optional.ofNullable(worldPropertiesByFolderName.get(folderName));
    }

    public static Collection<WorldProperties> getAllWorldProperties() {
        return Collections.unmodifiableCollection(worldPropertiesByFolderName.values());
    }

    public static Optional<WorldProperties> getWorldProperties(UUID uuid) {
        checkNotNull(uuid);
        return Optional.ofNullable(worldPropertiesByWorldUuid.get(uuid));
    }

    public static Optional<UUID> getUuidForFolder(String folderName) {
        checkNotNull(folderName);
        return Optional.ofNullable(worldUuidByFolderName.get(folderName));
    }

    public static Optional<String> getFolderForUuid(UUID uuid) {
        checkNotNull(uuid);
        return Optional.ofNullable(worldUuidByFolderName.inverse().get(uuid));
    }

    public static WorldProperties createWorldProperties(String folderName, WorldArchetype archetype) {
        return createWorldProperties(folderName, archetype, null);
    }

    public static WorldProperties createWorldProperties(String folderName, WorldArchetype archetype, @Nullable Integer dimensionId) {
        checkNotNull(folderName);
        checkNotNull(archetype);
        final Optional<WorldServer> optWorldServer = getWorld(folderName);
        if (optWorldServer.isPresent()) {
            return ((org.spongepowered.api.world.World) optWorldServer.get()).getProperties();
        }

        final Optional<WorldProperties> optWorldProperties = WorldManager.getWorldProperties(folderName);

        if (optWorldProperties.isPresent()) {
            return optWorldProperties.get();
        }

        final ISaveHandler saveHandler = new AnvilSaveHandler(WorldManager.getCurrentSavesDirectory().get().toFile(), folderName, true, SpongeImpl
                .getDataFixer());
        WorldInfo worldInfo = saveHandler.loadWorldInfo();

        if (worldInfo == null) {
            worldInfo = new WorldInfo((WorldSettings) (Object) archetype, folderName);
            // Don't want to randomize the seed if there is an existing save file!
            if (archetype.isSeedRandomized()) {
                ((WorldProperties) worldInfo).setSeed(SpongeImpl.random.nextLong());
            }
        } else {
            // DimensionType must be set before world config is created to get proper path
            ((WorldInfoBridge) worldInfo).setDimensionType(archetype.getDimensionType());
            ((WorldInfoBridge) worldInfo).createWorldConfig();
            ((WorldProperties) worldInfo).setGeneratorModifiers(archetype.getGeneratorModifiers());
        }

        setUuidOnProperties(getCurrentSavesDirectory().get(), (WorldProperties) worldInfo);
        if (dimensionId != null) {
            ((WorldInfoBridge) worldInfo).setDimensionId(dimensionId);
        } else if (((WorldInfoBridge) worldInfo).getDimensionId() == null
                //|| ((WorldInfoBridge) worldInfo).bridge$getDimensionId() == Integer.MIN_VALUE // TODO: Evaulate all uses of Integer.MIN_VALUE for dimension ids
                || getWorldByDimensionId(((WorldInfoBridge) worldInfo).getDimensionId()).isPresent()) {
            // DimensionID is null or 0 or the dimensionID is already assinged to a loaded world
            ((WorldInfoBridge) worldInfo).setDimensionId(WorldManager.getNextFreeDimensionId());
        }
        ((WorldProperties) worldInfo).setGeneratorType(archetype.getGeneratorType());
        ((WorldInfoBridge) worldInfo).getConfigAdapter().save();
        registerWorldProperties((WorldProperties) worldInfo);

        SpongeImpl.postEvent(SpongeEventFactory.createConstructWorldPropertiesEvent(Sponge.getCauseStackManager().getCurrentCause(), archetype,
                (WorldProperties) worldInfo));

        saveHandler.saveWorldInfoWithPlayer(worldInfo, SpongeImpl.getServer().getPlayerList().getHostPlayerData());

        return (WorldProperties) worldInfo;

    }

    public static boolean saveWorldProperties(WorldProperties properties) {
        checkNotNull(properties);
        final Optional<WorldServer> optWorldServer = getWorldByDimensionId(((WorldInfoBridge) properties).getDimensionId());
        // If the World represented in the properties is still loaded, save the properties and have the World reload its info
        if (optWorldServer.isPresent()) {
            final WorldServer worldServer = optWorldServer.get();
            worldServer.getSaveHandler().saveWorldInfo((WorldInfo) properties);
            worldServer.getSaveHandler().loadWorldInfo();
        } else {
            new AnvilSaveHandler(WorldManager.getCurrentSavesDirectory().get().toFile(), properties.getWorldName(), true, SpongeImpl
                    .getDataFixer()).saveWorldInfo((WorldInfo) properties);
        }
        ((WorldInfoBridge) properties).getConfigAdapter().save();
        // No return values or exceptions so can only assume true.
        return true;
    }

    public static void unloadQueuedWorlds() {
        WorldServer server;

        while ((server = unloadQueue.poll()) != null) {
            unloadWorld(server, true, false);
        }

        unloadQueue.clear();
    }

    public static void queueWorldToUnload(WorldServer worldServer) {
        checkNotNull(worldServer);

        unloadQueue.add(worldServer);
    }

    // TODO Result
    public static boolean unloadWorld(WorldServer worldServer, boolean checkConfig, boolean isShuttingDown) {
        checkNotNull(worldServer);

        final MinecraftServer server = SpongeImpl.getServer();

        // Likely leaked, don't want to drop leaked world data
        if (!worldByDimensionId.containsValue(worldServer)) {
            return false;
        }

        final SpongeConfig<GlobalConfig> globalConfigAdapter = SpongeImpl.getGlobalConfigAdapter();

        if (globalConfigAdapter.getConfig().getModules().useOptimizations() &&
                globalConfigAdapter.getConfig().getOptimizations().useAsyncLighting()) {

            // The world is unloading - there's no point in running any more lighting tasks
            ((ServerWorldBridge) worldServer).bridge$getLightingExecutor().shutdownNow();
        }

        // Vanilla sometimes doesn't remove player entities from world first
        if (!isShuttingDown) {
            if (!worldServer.playerEntities.isEmpty()) {
                return false;
            }

            // We only check config if base game wants to unload world. If mods/plugins say unload, we unload
            if (checkConfig) {
                if (((WorldProperties) worldServer.getWorldInfo()).doesKeepSpawnLoaded()) {
                    return false;
                }
            }
        }

        try (final PhaseContext<?> ignored = GeneralPhase.State.WORLD_UNLOAD.createPhaseContext().source(worldServer)) {
            ignored.buildAndSwitch();
            final UnloadWorldEvent event = SpongeEventFactory.createUnloadWorldEvent(Sponge.getCauseStackManager().getCurrentCause(),
                (org.spongepowered.api.world.World) worldServer);
            final boolean isCancelled = SpongeImpl.postEvent(event);

            if (!isShuttingDown && isCancelled) {
                return false;
            }

            final ServerWorldBridge mixinWorldServer = (ServerWorldBridge) worldServer;
            final int dimensionId = mixinWorldServer.bridge$getDimensionId();

            try {
                // Don't save if server is stopping to avoid duplicate saving.
                if (!isShuttingDown) {
                    saveWorld(worldServer, true);
                }

                ((WorldInfoBridge) worldServer.getWorldInfo()).getConfigAdapter().save();
            } catch (MinecraftException e) {
                e.printStackTrace();
            } finally {
                SpongeImpl.getLogger().info("Unloading world [{}] ({}/{})", worldServer.getWorldInfo().getWorldName(),
                    ((org.spongepowered.api.world.World) worldServer).getDimension().getType().getId(), dimensionId);
                worldByDimensionId.remove(dimensionId);
                weakWorldByWorld.remove(worldServer);
                ((IMixinMinecraftServer) server).removeWorldTickTimes(dimensionId);
                reorderWorldsVanillaFirst();
            }
        }
        return true;
    }

    public static void saveWorld(WorldServer worldServer, boolean flush) throws MinecraftException {
        if (((WorldProperties) worldServer.getWorldInfo()).getSerializationBehavior() == SerializationBehaviors.NONE) {
            return;
        } else {
            worldServer.saveAllChunks(true, null);
        }
        if (flush) {
            worldServer.flush();
        }
    }

    public static Optional<WorldServer> loadWorld(UUID uuid) {
        checkNotNull(uuid);
        // If someone tries to load loaded world, return it
        Optional<org.spongepowered.api.world.World> optWorld = Sponge.getServer().getWorld(uuid);
        if (optWorld.isPresent()) {
            return Optional.of((WorldServer) optWorld.get());
        }
        // Check if we even know of this UUID's folder
        final String worldFolder = worldUuidByFolderName.inverse().get(uuid);
        // We don't know of this UUID at all.
        if (worldFolder == null) {
            return Optional.empty();
        }
        return loadWorld(worldFolder, null);
    }

    public static Optional<WorldServer> loadWorld(String worldName) {
        checkNotNull(worldName);
        return loadWorld(worldName, null);
    }

    public static Optional<WorldServer> loadWorld(WorldProperties properties) {
        checkNotNull(properties);
        return loadWorld(properties.getWorldName(), properties);
    }

    private static Optional<WorldServer> loadWorld(String worldName, @Nullable WorldProperties properties) {
        checkNotNull(worldName);
        final Path currentSavesDir = WorldManager.getCurrentSavesDirectory().orElseThrow(() -> new IllegalStateException("Attempt "
                + "made to load world too early!"));
        final MinecraftServer server = SpongeImpl.getServer();
        final Optional<WorldServer> optExistingWorldServer = getWorld(worldName);
        if (optExistingWorldServer.isPresent()) {
            return optExistingWorldServer;
        }

        if (!server.getAllowNether()) {
            SpongeImpl.getLogger().error("Unable to load world [{}]. Multi-world is disabled via [allow-nether] in [server.properties].", worldName);
            return Optional.empty();
        }

        final Path worldFolder = currentSavesDir.resolve(worldName);
        if (!Files.isDirectory(worldFolder)) {
            SpongeImpl.getLogger().error("Unable to load world [{}]. We cannot find its folder under [{}].", worldFolder, currentSavesDir);
            return Optional.empty();
        }

        final ISaveHandler saveHandler = new AnvilSaveHandler(currentSavesDir.toFile(), worldName, true, SpongeImpl.getDataFixer());

        // We weren't given a properties, see if one is cached
        if (properties == null) {
            properties = (WorldProperties) saveHandler.loadWorldInfo();

            // We tried :'(
            if (properties == null) {
                SpongeImpl.getLogger().error("Unable to load world [{}]. No world properties was found!", worldName);
                return Optional.empty();
            }
        }

        // TODO: Evaulate all uses of Integer.MIN_VALUE for dimension ids
        if (((WorldInfoBridge) properties).getDimensionId() == null /*|| ((WorldInfoBridge) properties).bridge$getDimensionId() == Integer.MIN_VALUE*/) {
            ((WorldInfoBridge) properties).setDimensionId(getNextFreeDimensionId());
        }
        setUuidOnProperties(getCurrentSavesDirectory().get(), properties);
        registerWorldProperties(properties);

        final WorldInfo worldInfo = (WorldInfo) properties;
        ((WorldInfoBridge) worldInfo).createWorldConfig();

        // check if enabled
        if (!((WorldProperties) worldInfo).isEnabled()) {
            SpongeImpl.getLogger().error("Unable to load world [{}]. It is disabled.", worldName);
            return Optional.empty();
        }

        final int dimensionId = ((WorldInfoBridge) properties).getDimensionId();
        registerDimension(dimensionId, (DimensionType) (Object) properties.getDimensionType());
        registerDimensionPath(dimensionId, worldFolder);
        SpongeImpl.getLogger().info("Loading world [{}] ({}/{})", properties.getWorldName(), properties.getDimensionType().getId(), dimensionId);

        final WorldServer worldServer = createWorldFromProperties(dimensionId, saveHandler, (WorldInfo) properties, new WorldSettings((WorldInfo)
                        properties));

        // Set the worlds on the Minecraft server
        reorderWorldsVanillaFirst();

        return Optional.of(worldServer);
    }

    public static void loadAllWorlds(long defaultSeed, WorldType defaultWorldType, String generatorOptions) {
        final MinecraftServer server = SpongeImpl.getServer();

        // We cannot call getCurrentSavesDirectory here as that would generate a savehandler and trigger a session lock.
        // We'll go ahead and make the directories for the save name here so that the migrator won't fail
        final Path currentSavesDir = server.anvilFile.toPath().resolve(server.getFolderName());
        try {
            // Symlink needs special handling
            if (Files.isSymbolicLink(currentSavesDir)) {
                final Path actualPathLink = Files.readSymbolicLink(currentSavesDir);
                if (Files.notExists(actualPathLink)) {
                    Files.createDirectories(actualPathLink);
                } else if (!Files.isDirectory(actualPathLink)) {
                    throw new IOException("Saves directory [" + currentSavesDir + "] symlinked to [" + actualPathLink + "] is not a directory!");
                }
            } else {
                Files.createDirectories(currentSavesDir);
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        WorldManager.registerVanillaDimensionPaths(currentSavesDir);

        WorldMigrator.migrateWorldsTo(currentSavesDir);

        registerExistingSpongeDimensions(currentSavesDir);

        for (Map.Entry<Integer, DimensionType> entry: sortedDimensionMap().entrySet()) {

            final int dimensionId = entry.getKey();
            final DimensionType dimensionType = entry.getValue();
            final org.spongepowered.api.world.DimensionType apiDimensionType = (org.spongepowered.api.world.DimensionType) (Object) dimensionType;
            // Skip all worlds besides dimension 0 if multi-world is disabled
            if (dimensionId != 0 && !server.getAllowNether()) {
                continue;
            }

            // Skip already loaded worlds by plugins
            if (getWorldByDimensionId(dimensionId).isPresent()) {
                continue;
            }

            // Step 1 - Grab the world's data folder
            final Path worldFolder = getWorldFolder(dimensionType, dimensionId);
            if (worldFolder == null) {
                SpongeImpl.getLogger().error("An attempt was made to load a world in dimension [{}] ({}) that has no registered world folder!",
                        apiDimensionType.getId(), dimensionId);
                continue;
            }

            final String worldFolderName = worldFolder.getFileName().toString();

            // Step 2 - See if we are allowed to load it
            if (dimensionId != 0) {
                final SpongeConfig<? extends GeneralConfigBase> spongeConfig = SpongeHooks.getConfigAdapter(((DimensionTypeBridge)(Object) dimensionType).getConfigPath(), worldFolderName);
                if (!spongeConfig.getConfig().getWorld().isWorldEnabled()) {
                    SpongeImpl.getLogger().warn("World [{}] ({}/{}) is disabled. World will not be loaded...", worldFolder,
                        apiDimensionType.getId(), dimensionId);
                    continue;
                }
            }

            // Step 3 - Get our world information from disk
            final ISaveHandler saveHandler;
            if (dimensionId == 0) {
                saveHandler = server.getActiveAnvilConverter().getSaveLoader(server.getFolderName(), true);
            } else {
                saveHandler = new AnvilSaveHandler(WorldManager.getCurrentSavesDirectory().get().toFile(), worldFolderName, true,
                        SpongeImpl.getDataFixer());
            }

            WorldInfo worldInfo = saveHandler.loadWorldInfo();

            WorldSettings worldSettings;

            // If this is integrated server, we need to use the WorldSettings from the client's Single Player menu to construct the worlds
            if (server instanceof IMixinIntegratedServer) {
                worldSettings = ((IMixinIntegratedServer) server).getSettings();

                // If this is overworld and a new save, the WorldInfo has already been made but we want to still fire the construct event.
                if (dimensionId == 0 && ((IMixinIntegratedServer) server).isNewSave()) {
                    SpongeImpl.postEvent(SpongeEventFactory.createConstructWorldPropertiesEvent(Sponge.getCauseStackManager().getCurrentCause(), (WorldArchetype)
                            (Object) worldSettings, (WorldProperties) worldInfo));
                }
            } else {
                // WorldSettings will be null here on dedicated server so we need to build one
                worldSettings = new WorldSettings(defaultSeed, server.getGameType(), server.canStructuresSpawn(), server.isHardcore(),
                        defaultWorldType);
            }

            if (worldInfo == null) {
                // Step 4 - At this point, we have either have the WorldInfo or we have none. If we have none, we'll use the settings built above to
                // create the WorldInfo
                worldInfo = createWorldInfoFromSettings(currentSavesDir, apiDimensionType,
                        dimensionId, worldFolderName, worldSettings, generatorOptions);
            } else {
                // create config
                ((WorldInfoBridge) worldInfo).setDimensionType(apiDimensionType);
                ((WorldInfoBridge) worldInfo).createWorldConfig();
                ((WorldProperties) worldInfo).setGenerateSpawnOnLoad(((DimensionTypeBridge) (Object) dimensionType).shouldGenerateSpawnOnLoad());
            }

            // Safety check to ensure we'll get a unique id no matter what
            UUID uniqueId = ((WorldProperties) worldInfo).getUniqueId();
            if (uniqueId == null) {
                setUuidOnProperties(dimensionId == 0 ? currentSavesDir.getParent() : currentSavesDir, (WorldProperties) worldInfo);
                uniqueId = ((WorldProperties) worldInfo).getUniqueId();
            }

            // Check if this world's unique id has already been registered
            final String previousWorldForUUID = worldUuidByFolderName.inverse().get(uniqueId);
            if (previousWorldForUUID != null) {
                SpongeImpl.getLogger().error("UUID [{}] has already been registered by world [{}] but is attempting to be registered by world [{}]."
                    + " This means worlds have been copied outside of Sponge. Skipping world load...", uniqueId, previousWorldForUUID, worldInfo.getWorldName());
                continue;
            }


            // Safety check to ensure the world info has the dimension id set
            if (((WorldInfoBridge) worldInfo).getDimensionId() == null) {
                ((WorldInfoBridge) worldInfo).setDimensionId(dimensionId);
            }

            // Keep the LevelName in the LevelInfo up to date with the directory name
            if (!worldInfo.getWorldName().equals(worldFolderName)) {
                worldInfo.setWorldName(worldFolderName);
            }

            // Step 5 - Load server resource pack from dimension 0
            if (dimensionId == 0) {
                server.setResourcePackFromWorld(worldFolderName, saveHandler);
            }

            // Step 6 - Cache the WorldProperties we've made so we don't load from disk later.
            registerWorldProperties((WorldProperties) worldInfo);

            if (dimensionId != 0 && !((WorldProperties) worldInfo).loadOnStartup()) {
                SpongeImpl.getLogger().warn("World [{}] ({}/{}) is set to not load on startup. To load it later, enable "
                    + "[load-on-startup] in config or use a plugin.", worldInfo.getWorldName(), apiDimensionType.getId(), dimensionId);
                continue;
            }

            // Step 7 - Finally, we can create the world and tell it to load
            final WorldServer worldServer = createWorldFromProperties(dimensionId, saveHandler, worldInfo, worldSettings);
            ;
            SpongeImpl.getLogger().info("Loading world [{}] ({}/{})", ((org.spongepowered.api.world.World) worldServer).getName(),
                apiDimensionType.getId(), dimensionId);
        }

        // Set the worlds on the Minecraft server
        reorderWorldsVanillaFirst();
    }

    private static WorldInfo createWorldInfoFromSettings(Path currentSaveRoot, org.spongepowered.api.world.DimensionType dimensionType, int
      dimensionId, String worldFolderName, WorldSettings worldSettings, String generatorOptions) {

        worldSettings.setGeneratorOptions(generatorOptions);

        ((WorldSettingsBridge) (Object) worldSettings).bridge$setDimensionType(dimensionType);
        ((WorldSettingsBridge)(Object) worldSettings).bridge$setGenerateSpawnOnLoad(((DimensionTypeBridge) dimensionType).shouldGenerateSpawnOnLoad());

        final WorldInfo worldInfo = new WorldInfo(worldSettings, worldFolderName);
        setUuidOnProperties(dimensionId == 0 ? currentSaveRoot.getParent() : currentSaveRoot, (WorldProperties) worldInfo);
        ((WorldInfoBridge) worldInfo).setDimensionId(dimensionId);
        SpongeImpl.postEvent(SpongeEventFactory.createConstructWorldPropertiesEvent(Sponge.getCauseStackManager().getCurrentCause(),
                (WorldArchetype) (Object) worldSettings, (WorldProperties) worldInfo));

        return worldInfo;

    }

    @SuppressWarnings("ConstantConditions")
    private static WorldServer createWorldFromProperties(int dimensionId, ISaveHandler saveHandler, WorldInfo worldInfo, @Nullable WorldSettings
        worldSettings) {
        final MinecraftServer server = SpongeImpl.getServer();
        final WorldServer worldServer = new WorldServer(server, saveHandler, worldInfo, dimensionId, server.profiler);

        worldByDimensionId.put(dimensionId, worldServer);
        weakWorldByWorld.put(worldServer, worldServer);

        WorldManager.reorderWorldsVanillaFirst();

        ((IMixinMinecraftServer) server).putWorldTickTimes(dimensionId, new long[100]);

        worldServer.init();

        worldServer.addEventListener(new ServerWorldEventHandler(server, worldServer));

        // This code changes from Mojang's to account for per-world API-set GameModes.
        if (!server.isSinglePlayer() && worldServer.getWorldInfo().getGameType() == GameType.NOT_SET) {
            worldServer.getWorldInfo().setGameType(server.getGameType());
        }

        ((ServerChunkProviderBridge) worldServer.getChunkProvider()).bridge$setForceChunkRequests(true);
        try {
            SpongeImpl.postEvent(SpongeEventFactory.createLoadWorldEvent(Sponge.getCauseStackManager().getCurrentCause(),
                (org.spongepowered.api.world.World) worldServer));

            // WorldSettings is only non-null here if this is a newly generated WorldInfo and therefore we need to initialize to calculate spawn.
            if (worldSettings != null) {
                worldServer.initialize(worldSettings);
            }

            if (((DimensionTypeBridge) ((org.spongepowered.api.world.World) worldServer).getDimension().getType()).shouldLoadSpawn()) {
                ((IMixinMinecraftServer) server).prepareSpawnArea(worldServer);
            }

            // While we try to prevnt mods from changing a worlds' WorldInfo, we aren't always
            // successful. We re-do the fake world check to catch any changes made to WorldInfo
            // that would make it invalid
            ((WorldBridge) worldServer).clearFakeCheck();

            return worldServer;
        } finally {
            ((ServerChunkProviderBridge) worldServer.getChunkProvider()).bridge$setForceChunkRequests(false);
        }
    }

    /**
     * Internal use only - Namely for SpongeForge.
     * @param dimensionId The world instance dimension id
     * @param worldServer The world server
     */
    public static void forceAddWorld(int dimensionId, WorldServer worldServer) {
        worldByDimensionId.put(dimensionId, worldServer);
        weakWorldByWorld.put(worldServer, worldServer);

        ((IMixinMinecraftServer) SpongeImpl.getServer()).putWorldTickTimes(dimensionId, new long[100]);
    }

    public static void reorderWorldsVanillaFirst() {
        final List<WorldServer> sorted = new LinkedList<>();

        List<Integer> vanillaWorldIds = new ArrayList<>();
        WorldServer worldServer = worldByDimensionId.get(0);

        if (worldServer != null) {
            vanillaWorldIds.add(0);
            sorted.add(worldServer);
        }

        worldServer = worldByDimensionId.get(-1);

        if (worldServer != null) {
            vanillaWorldIds.add(-1);
            sorted.add(worldServer);
        }

        worldServer = worldByDimensionId.get(1);

        if (worldServer != null) {
            vanillaWorldIds.add(1);
            sorted.add(worldServer);
        }

        final List<WorldServer> worlds = new ArrayList<>(worldByDimensionId.values());
        final Iterator<WorldServer> iterator = worlds.iterator();
        while(iterator.hasNext()) {
            final ServerWorldBridge mixinWorld = (ServerWorldBridge) iterator.next();
            final int dimensionId = mixinWorld.bridge$getDimensionId();
            if (vanillaWorldIds.contains(dimensionId)) {
                iterator.remove();
            }
        }

        worlds.sort(WORLD_SERVER_COMPARATOR);
        sorted.addAll(worlds);
        SpongeImpl.getServer().worlds = sorted.toArray(new WorldServer[0]);
    }

    /**
     * Parses a {@link UUID} from disk from other known plugin platforms and sets it on the
     * {@link WorldProperties}. Currently only Bukkit is supported.
     */
    private static void setUuidOnProperties(Path savesRoot, WorldProperties properties) {
        checkNotNull(properties);

        UUID uuid;
        if (properties.getUniqueId() == null || properties.getUniqueId().equals
                (UUID.fromString("00000000-0000-0000-0000-000000000000"))) {
            // Check if Bukkit's uid.dat file is here and use it
            final Path uidPath = savesRoot.resolve(properties.getWorldName()).resolve("uid.dat");
            if (Files.notExists(uidPath)) {
                uuid = UUID.randomUUID();
            } else {
                try(final DataInputStream dis = new DataInputStream(Files.newInputStream(uidPath))) {
                    uuid = new UUID(dis.readLong(), dis.readLong());
                } catch (IOException e) {
                    SpongeImpl.getLogger().error("World folder [{}] has an existing Bukkit unique identifier for it but we encountered issues parsing "
                            + "the file. We will have to use a new unique id. Please report this to Sponge ASAP.", properties.getWorldName(), e);
                    uuid = UUID.randomUUID();
                }
            }
        } else {
            uuid = properties.getUniqueId();
        }

        ((WorldInfoBridge) properties).setUniqueId(uuid);
    }

    /**
     * Handles registering existing Sponge dimensions that are not the root dimension (known as overworld).
     */
    private static void registerExistingSpongeDimensions(Path rootPath) {
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(rootPath, LEVEL_AND_SPONGE)) {
            for (Path worldPath : stream) {
                final Path spongeLevelPath = worldPath.resolve("level_sponge.dat");
                final String worldFolderName = worldPath.getFileName().toString();

                NBTTagCompound compound;
                try {
                    compound = CompressedStreamTools.readCompressed(Files.newInputStream(spongeLevelPath));
                } catch (IOException e) {
                    SpongeImpl.getLogger().error("Failed loading Sponge data for World [{}]}. Report to Sponge ASAP.", worldFolderName, e);
                    continue;
                }

                NBTTagCompound spongeDataCompound = compound.getCompoundTag(Constants.Sponge.SPONGE_DATA);

                if (!compound.hasKey(Constants.Sponge.SPONGE_DATA)) {
                    SpongeImpl.getLogger()
                            .error("World [{}] has Sponge related data in the form of [level-sponge.dat] but the structure is not proper."
                                            + " Generally, the data is within a [{}] tag but it is not for this world. Report to Sponge ASAP.",
                                    worldFolderName, Constants.Sponge.SPONGE_DATA);
                    continue;
                }

                if (!spongeDataCompound.hasKey(Constants.Sponge.World.DIMENSION_ID)) {
                    SpongeImpl.getLogger().error("World [{}] has no dimension id. Report this to Sponge ASAP.", worldFolderName);
                    continue;
                }

                int dimensionId = spongeDataCompound.getInteger(Constants.Sponge.World.DIMENSION_ID);

                // TODO: Evaulate all uses of Integer.MIN_VALUE for dimension ids
                /*if (dimensionId == Integer.MIN_VALUE) {
                    // temporary fix for existing worlds created with wrong dimension id
                    dimensionId = WorldManager.getNextFreeDimensionId();
                }*/
                // We do not handle Vanilla dimensions, skip them
                if (dimensionId == 0 || dimensionId == -1 || dimensionId == 1) {
                    continue;
                }

                spongeDataCompound = DataUtil.spongeDataFixer.process(FixTypes.LEVEL, spongeDataCompound);

                String dimensionTypeId = "overworld";

                if (spongeDataCompound.hasKey(Constants.Sponge.World.DIMENSION_TYPE)) {
                    dimensionTypeId = spongeDataCompound.getString(Constants.Sponge.World.DIMENSION_TYPE);
                } else {
                    SpongeImpl.getLogger().warn("World [{}] (DIM{}) has no specified dimension type. Defaulting to [{}}]...", worldFolderName,
                            dimensionId, DimensionTypes.OVERWORLD.getName());
                }

                dimensionTypeId = fixDimensionTypeId(dimensionTypeId);
                org.spongepowered.api.world.DimensionType dimensionType
                        = Sponge.getRegistry().getType(org.spongepowered.api.world.DimensionType.class, dimensionTypeId).orElse(null);
                if (dimensionType == null) {
                    SpongeImpl.getLogger().warn("World [{}] (DIM{}) has specified dimension type that is not registered. Skipping...",
                            worldFolderName, dimensionId);
                    continue;
                }

                spongeDataCompound.setString(Constants.Sponge.World.DIMENSION_TYPE, dimensionTypeId);
                if (!spongeDataCompound.hasUniqueId(Constants.UUID)) {
                    SpongeImpl.getLogger().error("World [{}] (DIM{}) has no valid unique identifier. This is a critical error and should be reported"
                            + " to Sponge ASAP.", worldFolderName, dimensionId);
                    continue;
                }

                worldFolderByDimensionId.put(dimensionId, worldFolderName);
                registerDimensionPath(dimensionId, rootPath.resolve(worldFolderName));
                registerDimension(dimensionId, (DimensionType)(Object) dimensionType);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Checks if the saved dimension type contains a modid and if not, attempts to locate one
    public static String fixDimensionTypeId(String name) {
        // Since we now store the modid, we need to support older save files that only include id without modid.
        if (!name.contains(":")) {
            for (org.spongepowered.api.world.DimensionType type : Sponge.getRegistry().getAllOf(org.spongepowered.api.world.DimensionType.class)) {
                String typeId = (type.getId().substring(type.getId().lastIndexOf(":") + 1));
                if (typeId.equals(name)) {
                    return type.getId();
                    // Note: We don't update the NBT here but instead fix it on next
                    //       world save in case there are 2 types using same name.
                }
            }
        }

        return name;
    }

    public static CompletableFuture<Optional<WorldProperties>> copyWorld(WorldProperties worldProperties, String copyName) {
        checkArgument(worldPropertiesByFolderName.containsKey(worldProperties.getWorldName()), "World properties not registered!");
        checkArgument(!worldPropertiesByFolderName.containsKey(copyName), "Destination world name already is registered!");
        final WorldInfo info = (WorldInfo) worldProperties;

        final WorldServer worldServer = worldByDimensionId.get(((WorldInfoBridge) info).getDimensionId().intValue());
        if (worldServer != null) {
            try {
                saveWorld(worldServer, true);
            } catch (MinecraftException e) {
                throw new RuntimeException(e);
            }

            ((IMixinMinecraftServer) SpongeImpl.getServer()).setSaveEnabled(false);
        }

        final CompletableFuture<Optional<WorldProperties>> future = SpongeImpl.getScheduler().submitAsyncTask(new CopyWorldTask(info, copyName));
        if (worldServer != null) { // World was loaded
            future.thenRun(() -> ((IMixinMinecraftServer) SpongeImpl.getServer()).setSaveEnabled(true));
        }
        return future;
    }

    public static Optional<WorldProperties> renameWorld(WorldProperties worldProperties, String newName) {
        checkNotNull(worldProperties);
        checkNotNull(newName);
        checkState(!worldByDimensionId.containsKey(((WorldInfoBridge) worldProperties).getDimensionId()), "World is still loaded!");

        final Path oldWorldFolder = getCurrentSavesDirectory().get().resolve(worldProperties.getWorldName());
        final Path newWorldFolder = oldWorldFolder.resolveSibling(newName);
        if (Files.exists(newWorldFolder)) {
            return Optional.empty();
        }

        try {
            Files.move(oldWorldFolder, newWorldFolder);
        } catch (IOException e) {
            return Optional.empty();
        }

        unregisterWorldProperties(worldProperties, false);

        final WorldInfo info = new WorldInfo((WorldInfo) worldProperties);
        info.setWorldName(newName);

        // As we are moving a world, we want to move the dimension ID and UUID with the world to ensure
        // plugins and Sponge do not break.
        ((WorldInfoBridge) info).setUniqueId(worldProperties.getUniqueId());
        if (((WorldInfoBridge) worldProperties).getDimensionId() != null) {
            ((WorldInfoBridge) info).setDimensionId(((WorldInfoBridge) worldProperties).getDimensionId());
        }

        ((WorldInfoBridge) info).createWorldConfig();
        new AnvilSaveHandler(WorldManager.getCurrentSavesDirectory().get().toFile(), newName, true, SpongeImpl.getDataFixer())
                .saveWorldInfo(info);
        registerWorldProperties((WorldProperties) info);
        return Optional.of((WorldProperties) info);
    }

    public static CompletableFuture<Boolean> deleteWorld(WorldProperties worldProperties) {
        checkNotNull(worldProperties);
        checkArgument(worldPropertiesByWorldUuid.containsKey(worldProperties.getUniqueId()), "World properties not registered!");
        checkState(!worldByDimensionId.containsKey(((WorldInfoBridge) worldProperties).getDimensionId()), "World not unloaded!");
        return SpongeImpl.getScheduler().submitAsyncTask(new DeleteWorldTask(worldProperties));
    }

    /**
     * Called when the server wants to update the difficulty on all worlds.
     *
     * If the world has a difficulty set via external means (command, plugin, mod) then we honor that difficulty always.
     */
    public static void updateServerDifficulty() {
        final EnumDifficulty serverDifficulty = SpongeImpl.getServer().getDifficulty();

        for (WorldServer worldServer : getWorlds()) {
            final boolean alreadySet = ((WorldInfoBridge) worldServer.getWorldInfo()).hasCustomDifficulty();
            adjustWorldForDifficulty(worldServer, alreadySet ? worldServer.getWorldInfo().getDifficulty() : serverDifficulty, false);
        }
    }

    public static void adjustWorldForDifficulty(WorldServer worldServer, EnumDifficulty difficulty, boolean isCustom) {
        final MinecraftServer server = SpongeImpl.getServer();
        final boolean alreadySet = ((WorldInfoBridge) worldServer.getWorldInfo()).hasCustomDifficulty();

        if (worldServer.getWorldInfo().isHardcoreModeEnabled()) {
            difficulty = EnumDifficulty.HARD;
            worldServer.setAllowedSpawnTypes(true, true);
        } else if (SpongeImpl.getServer().isSinglePlayer()) {
            worldServer.setAllowedSpawnTypes(worldServer.getDifficulty() != EnumDifficulty.PEACEFUL, true);
        } else {
            worldServer.setAllowedSpawnTypes(server.allowSpawnMonsters(), server.getCanSpawnAnimals());
        }

        if (!alreadySet) {
            if (!isCustom) {
                ((WorldInfoBridge) worldServer.getWorldInfo()).forceSetDifficulty(difficulty);
            } else {
                worldServer.getWorldInfo().setDifficulty(difficulty);
            }
        }
    }

    private static class CopyWorldTask implements Callable<Optional<WorldProperties>> {

        private final WorldInfo oldInfo;
        private final String newName;

        CopyWorldTask(WorldInfo info, String newName) {
            this.oldInfo = info;
            this.newName = newName;
        }

        @Override
        public Optional<WorldProperties> call() throws Exception {
            Path oldWorldFolder = getCurrentSavesDirectory().get().resolve(this.oldInfo.getWorldName());
            final Path newWorldFolder = getCurrentSavesDirectory().get().resolve(this.newName);

            if (Files.exists(newWorldFolder)) {
                return Optional.empty();
            }

            FileVisitor<Path> visitor = new CopyFileVisitor(newWorldFolder);
            if (((WorldInfoBridge) this.oldInfo).getDimensionId() == 0) {
                oldWorldFolder = getCurrentSavesDirectory().get();
                visitor = new ForwardingFileVisitor<Path>(visitor) {

                    private boolean root = true;

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if (!this.root && Files.exists(dir.resolve("level.dat"))) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }

                        this.root = false;
                        return super.preVisitDirectory(dir, attrs);
                    }
                };
            }

            // Copy the world folder
            Files.walkFileTree(oldWorldFolder, visitor);

            final WorldInfo info = new WorldInfo(this.oldInfo);
            info.setWorldName(this.newName);
            ((WorldInfoBridge) info).setDimensionId(getNextFreeDimensionId());
            ((WorldInfoBridge) info).setUniqueId(UUID.randomUUID());
            ((WorldInfoBridge) info).createWorldConfig();
            registerWorldProperties((WorldProperties) info);
            new AnvilSaveHandler(WorldManager.getCurrentSavesDirectory().get().toFile(), this.newName, true, SpongeImpl.getDataFixer())
                    .saveWorldInfo(info);
            return Optional.of((WorldProperties) info);
        }
    }

    private static class DeleteWorldTask implements Callable<Boolean> {

        private final WorldProperties props;

        DeleteWorldTask(WorldProperties props) {
            this.props = props;
        }

        @Override
        public Boolean call() {
            final Path worldFolder = getCurrentSavesDirectory().get().resolve(this.props.getWorldName());
            if (!Files.exists(worldFolder)) {
                unregisterWorldProperties(this.props, true);
                return true;
            }

            try {
                Files.walkFileTree(worldFolder, DeleteFileVisitor.INSTANCE);
                unregisterWorldProperties(this.props, true);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

    }

    public static void sendDimensionRegistration(EntityPlayerMP playerMP, WorldProvider provider) {
        // Do nothing in Common
    }

    public static void loadDimensionDataMap(@Nullable NBTTagCompound compound) {
        usedDimensionIds.clear();
        lastUsedDimensionId = 0;

        if (compound == null) {
            dimensionTypeByDimensionId.keySet().stream().filter(dimensionId -> dimensionId >= 0).forEach(usedDimensionIds::add);
        } else {
            for (int id : compound.getIntArray(Constants.Forge.USED_DIMENSION_IDS)) {
                usedDimensionIds.add(id);
            }

            // legacy data (load but don't save)
            int[] intArray = compound.getIntArray(Constants.Legacy.LEGACY_DIMENSION_ARRAY);
            for (int i = 0; i < intArray.length; i++) {
                int data = intArray[i];
                if (data == 0) continue;
                for (int j = 0; j < Integer.SIZE; j++) {
                    if ((data & (1 << j)) != 0) usedDimensionIds.add(i * Integer.SIZE + j);
                }
            }
        }
    }

    public static NBTTagCompound saveDimensionDataMap() {
        NBTTagCompound dimMap = new NBTTagCompound();
        dimMap.setIntArray(Constants.Forge.USED_DIMENSION_IDS, usedDimensionIds.toIntArray());
        return dimMap;
    }

    public static Optional<Path> getCurrentSavesDirectory() {
        final Optional<WorldServer> optWorldServer = getWorldByDimensionId(0);

        if (optWorldServer.isPresent()) {
            return Optional.of(optWorldServer.get().getSaveHandler().getWorldDirectory().toPath());
        } else if (SpongeImpl.getGame().getState().ordinal() >= GameState.SERVER_ABOUT_TO_START.ordinal()) {
            SaveHandler saveHandler = (SaveHandler) SpongeImpl.getServer().getActiveAnvilConverter().getSaveLoader(SpongeImpl.getServer().getFolderName(), false);
            return Optional.of(saveHandler.getWorldDirectory().toPath());
        }

        return Optional.empty();
    }

    public static Map<WorldServer, WorldServer> getWeakWorldMap() {
        return weakWorldByWorld;
    }

    public static int getClientDimensionId(EntityPlayerMP player, World world) {
        if (!((ServerPlayerEntityBridge) player).bridge$usesCustomClient()) {
            DimensionType type = world.provider.getDimensionType();
            if (type == DimensionType.OVERWORLD) {
                return 0;
            } else if (type == DimensionType.NETHER) {
                return -1;
            }

            return 1;
        }

        return ((ServerWorldBridge) world).bridge$getDimensionId();
    }

    public static boolean isKnownWorld(WorldServer world) {
        return weakWorldByWorld.containsKey(world);
    }
}

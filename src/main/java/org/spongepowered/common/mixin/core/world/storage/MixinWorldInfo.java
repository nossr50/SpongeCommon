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
package org.spongepowered.common.mixin.core.world.storage;

import static com.google.common.base.Preconditions.checkNotNull;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import com.google.common.base.MoreObjects;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonParseException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketServerDifficulty;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;
import org.apache.logging.log4j.Level;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.persistence.DataFormats;
import org.spongepowered.api.entity.living.player.gamemode.GameMode;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.DimensionTypes;
import org.spongepowered.api.world.GeneratorType;
import org.spongepowered.api.world.PortalAgentType;
import org.spongepowered.api.world.PortalAgentTypes;
import org.spongepowered.api.world.SerializationBehavior;
import org.spongepowered.api.world.SerializationBehaviors;
import org.spongepowered.api.world.WorldArchetype;
import org.spongepowered.api.world.difficulty.Difficulty;
import org.spongepowered.api.world.gen.WorldGeneratorModifier;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.bridge.world.GameRulesBridge;
import org.spongepowered.common.bridge.world.WorldSettingsBridge;
import org.spongepowered.common.config.SpongeConfig;
import org.spongepowered.common.config.type.WorldConfig;
import org.spongepowered.common.data.persistence.NbtTranslator;
import org.spongepowered.common.data.util.DataUtil;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.bridge.world.DimensionTypeBridge;
import org.spongepowered.common.bridge.world.WorldInfoBridge;
import org.spongepowered.common.registry.type.world.DimensionTypeRegistryModule;
import org.spongepowered.common.registry.type.world.PortalAgentRegistryModule;
import org.spongepowered.common.registry.type.world.WorldGeneratorModifierRegistryModule;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.util.FunctionalUtil;
import org.spongepowered.common.world.WorldManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

@NonnullByDefault
@Mixin(WorldInfo.class)
@Implements(@Interface(iface = WorldProperties.class, prefix = "worldproperties$"))
public abstract class MixinWorldInfo implements WorldProperties, WorldInfoBridge {

    @Shadow public long randomSeed;
    @Shadow private WorldType terrainType;
    @Shadow private String generatorOptions;
    @Shadow private int spawnX;
    @Shadow private int spawnY;
    @Shadow private int spawnZ;
    @Shadow private long totalTime;
    @Shadow private long worldTime;
    @Shadow private long lastTimePlayed;
    @Shadow private long sizeOnDisk;
    @Shadow private NBTTagCompound playerTag;
    @Shadow private String levelName;
    @Shadow private int saveVersion;
    @Shadow private int cleanWeatherTime;
    @Shadow private boolean raining;
    @Shadow private int rainTime;
    @Shadow private boolean thundering;
    @Shadow private int thunderTime;
    @Shadow private GameType gameType;
    @Shadow private boolean mapFeaturesEnabled;
    @Shadow private boolean hardcore;
    @Shadow private boolean allowCommands;
    @Shadow private boolean initialized;
    @Shadow public EnumDifficulty difficulty;
    @Shadow private boolean difficultyLocked;
    @Shadow private double borderCenterX;
    @Shadow private double borderCenterZ;
    @Shadow private double borderSize;
    @Shadow private long borderSizeLerpTime;
    @Shadow private double borderSizeLerpTarget;
    @Shadow private double borderSafeZone;
    @Shadow private double borderDamagePerBlock;
    @Shadow private int borderWarningDistance;
    @Shadow private int borderWarningTime;
    @Shadow private GameRules gameRules;
    private ServerScoreboard scoreboard;
    private boolean hasCustomDifficulty = false;

    @Shadow public abstract void setDifficulty(EnumDifficulty newDifficulty);
    @Shadow public abstract NBTTagCompound cloneNBTCompound(@Nullable NBTTagCompound nbt);

    @Nullable private UUID uuid;
    @Nullable private Integer dimensionId;
    private DimensionType dimensionType = DimensionTypes.OVERWORLD;
    private SerializationBehavior serializationBehavior = SerializationBehaviors.AUTOMATIC;
    private boolean isMod = false;
    private boolean generateBonusChest;
    private NBTTagCompound spongeRootLevelNbt = new NBTTagCompound();
    private NBTTagCompound spongeNbt = new NBTTagCompound();
    private final NBTTagList playerUniqueIdNbt = new NBTTagList();
    private final BiMap<Integer, UUID> playerUniqueIdMap = HashBiMap.create();
    private final List<UUID> pendingUniqueIds = new ArrayList<>();
    private int trackedUniqueIdCount = 0;
    @Nullable private SpongeConfig<WorldConfig> worldConfig;
    @Nullable private PortalAgentType portalAgentType;

    //     protected WorldInfo()
    @Inject(method = "<init>", at = @At("RETURN") )
    private void onConstruction(CallbackInfo ci) {
        this.onConstructionCommon();
    }

    //     public WorldInfo(NBTTagCompound nbt)
    @Inject(method = "<init>(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("RETURN") )
    private void onConstruction(NBTTagCompound nbt, CallbackInfo ci) {
        if (SpongeImplHooks.isMainThread() && !PhaseTracker.getInstance().getCurrentContext().state.isConvertingMaps()) {
            this.onConstructionCommon();
        }
    }

    //     public WorldInfo(WorldSettings settings, String name)
    @Inject(method = "<init>(Lnet/minecraft/world/WorldSettings;Ljava/lang/String;)V", at = @At("RETURN"))
    private void onConstruction(WorldSettings settings, String name, CallbackInfo ci) {
        if (!this.isValid()) {
            return;
        }

        this.onConstructionCommon();

        final WorldArchetype archetype = (WorldArchetype) (Object) settings;
        this.setDimensionType(archetype.getDimensionType());

        this.createWorldConfig();
        this.setEnabled(archetype.isEnabled());
        this.setLoadOnStartup(archetype.loadOnStartup());
        if (((WorldSettingsBridge)(Object) settings).bridge$internalKeepSpawnLoaded() != null) {
            this.setKeepSpawnLoaded(archetype.doesKeepSpawnLoaded());
        }
        this.setGenerateSpawnOnLoad(archetype.doesGenerateSpawnOnLoad());
        this.forceSetDifficulty((EnumDifficulty) (Object) archetype.getDifficulty());
        Collection<WorldGeneratorModifier> modifiers = this.getGeneratorModifiers();
        if (modifiers.isEmpty()) {
            this.setGeneratorModifiers(archetype.getGeneratorModifiers());
        } else {
            // use config modifiers
            this.setGeneratorModifiers(modifiers);
        }
        this.setDoesGenerateBonusChest(archetype.doesGenerateBonusChest());
        this.setSerializationBehavior(archetype.getSerializationBehavior());
    }

    //     public WorldInfo(WorldInfo worldInformation)
    @Inject(method = "<init>(Lnet/minecraft/world/storage/WorldInfo;)V", at = @At("RETURN") )
    private void onConstruction(WorldInfo worldInformation, CallbackInfo ci) {
        this.onConstructionCommon();

        MixinWorldInfo info = (MixinWorldInfo) (Object) worldInformation;
        this.getConfigAdapter(); // Create the config now if it has not yet been created.
        this.portalAgentType = info.portalAgentType;
        this.setDimensionType(info.dimensionType);
    }

    // used in all init methods
    private void onConstructionCommon() {
        this.spongeNbt.setTag(Constants.Sponge.SPONGE_PLAYER_UUID_TABLE, this.playerUniqueIdNbt);
        this.spongeRootLevelNbt.setTag(Constants.Sponge.SPONGE_DATA, this.spongeNbt);
    }

    @Inject(method = "updateTagCompound", at = @At("HEAD"))
    private void ensureLevelNameMatchesDirectory(NBTTagCompound compound, NBTTagCompound player, CallbackInfo ci) {
        if (this.dimensionId == null) {
            return;
        }

        final String name = WorldManager.getWorldFolderByDimensionId(this.dimensionId).orElse(this.levelName);
        if (!this.levelName.equalsIgnoreCase(name)) {
            this.levelName = name;
        }
    }

    @Override
    public boolean createWorldConfig() {
        if (this.worldConfig != null) {
             return false;
        }

        if (this.isValid()) {
            this.worldConfig =
                    new SpongeConfig<>(SpongeConfig.Type.WORLD, ((DimensionTypeBridge) this.dimensionType).getConfigPath()
                            .resolve(this.levelName)
                            .resolve("world.conf"),
                            SpongeImpl.ECOSYSTEM_ID,
                            ((DimensionTypeBridge) getDimensionType()).getDimensionConfig(),
                            false);
        } else {
            this.worldConfig = SpongeConfig.newDummyConfig(SpongeConfig.Type.WORLD);
        }

        return true;
    }

    @Override
    public boolean isValid() {
        return !(this.levelName == null || this.levelName.equals("") || this.levelName.equals("MpServer") || this.levelName.equals("sponge$dummy_world"));
    }

    @Override
    public Vector3i getSpawnPosition() {
        return new Vector3i(this.spawnX, this.spawnY, this.spawnZ);
    }

    @Override
    public void setSpawnPosition(Vector3i position) {
        checkNotNull(position);
        this.spawnX = position.getX();
        this.spawnY = position.getY();
        this.spawnZ = position.getZ();
    }

    @Override
    public GeneratorType getGeneratorType() {
        return (GeneratorType) this.terrainType;
    }

    @Override
    public void setGeneratorType(GeneratorType type) {
        this.terrainType = (WorldType) type;
    }

    @Intrinsic
    public long worldproperties$getSeed() {
        return this.randomSeed;
    }

    @Override
    public void setSeed(long seed) {
        this.randomSeed = seed;
    }

    @Override
    public long getTotalTime() {
        return this.totalTime;
    }

    @Intrinsic
    public long worldproperties$getWorldTime() {
        return this.worldTime;
    }

    @Override
    public void setWorldTime(long time) {
        this.worldTime = time;
    }

    @Override
    public DimensionType getDimensionType() {
        return this.dimensionType;
    }

    @Override
    public void setDimensionType(DimensionType type) {
        this.dimensionType = type;
        String modId = SpongeImplHooks.getModIdFromClass(this.dimensionType.getDimensionClass());
        if (!modId.equals("minecraft")) {
            this.isMod = true;
        }
    }

    @Override
    public PortalAgentType getPortalAgentType() {
        if (this.portalAgentType == null) {
            this.portalAgentType = PortalAgentTypes.DEFAULT;
        }
        return this.portalAgentType;
    }

    @Override
    public void setPortalAgentType(PortalAgentType type) {
        this.portalAgentType = type;
    }

    @Intrinsic
    public boolean worldproperties$isRaining() {
        return this.raining;
    }

    @Override
    public void setRaining(boolean state) {
        this.raining = state;
    }

    @Intrinsic
    public int worldproperties$getRainTime() {
        return this.rainTime;
    }

    @Intrinsic
    public void worldproperties$setRainTime(int time) {
        this.rainTime = time;
    }

    @Intrinsic
    public boolean worldproperties$isThundering() {
        return this.thundering;
    }

    @Intrinsic
    public void worldproperties$setThundering(boolean state) {
        this.thundering = state;
    }

    @Override
    public int getThunderTime() {
        return this.thunderTime;
    }

    @Override
    public void setThunderTime(int time) {
        this.thunderTime = time;
    }

    @Override
    public GameMode getGameMode() {
        return (GameMode) (Object) this.gameType;
    }

    @Override
    public void setGameMode(GameMode gamemode) {
        this.gameType = (GameType) (Object) gamemode;
    }

    @Override
    public boolean usesMapFeatures() {
        return this.mapFeaturesEnabled;
    }

    @Override
    public void setMapFeaturesEnabled(boolean state) {
        this.mapFeaturesEnabled = state;
    }

    @Override
    public boolean isHardcore() {
        return this.hardcore;
    }

    @Override
    public void setHardcore(boolean state) {
        this.hardcore = state;
    }

    @Override
    public boolean areCommandsAllowed() {
        return this.allowCommands;
    }

    @Override
    public void setCommandsAllowed(boolean state) {
        this.allowCommands = state;
    }

    @Override
    public boolean isInitialized() {
        return this.initialized;
    }

    @Override
    public Difficulty getDifficulty() {
        return (Difficulty) (Object) this.difficulty;
    }

    @Inject(method = "setDifficulty", at = @At("HEAD"), cancellable = true)
    private void onSetDifficultyVanilla(EnumDifficulty newDifficulty, CallbackInfo ci) {
        this.hasCustomDifficulty = true;
        if (newDifficulty == null) {
            // This is an error from someone
            new PrettyPrinter(60).add("Null Difficulty being set!").centre().hr()
                .add("Someone (not Sponge) is attempting to set a null difficulty to this WorldInfo setup! Please report to the mod/plugin author!")
                .add()
                .addWrapped(60, " %s : %s", "WorldInfo", this)
                .add()
                .add(new Exception("Stacktrace"))
                .log(SpongeImpl.getLogger(), Level.ERROR);
            ci.cancel(); // We cannot let the null set the field.
            return;
        }

        // If the difficulty is set, we need to sync it to the players in the world attached to the worldinfo (if any)
        WorldManager.getWorlds()
          .stream()
          .filter(world -> world.getWorldInfo() == (WorldInfo) (Object) this)
          .flatMap(world -> world.playerEntities.stream())
          .filter(player -> player instanceof EntityPlayerMP)
          .map(player -> (EntityPlayerMP) player)
          .forEach(player -> player.connection.sendPacket(new SPacketServerDifficulty(newDifficulty, ((WorldInfo) (Object) this).isDifficultyLocked
            ())));
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        this.setDifficulty((EnumDifficulty) (Object) difficulty);
    }

    @Override
    public boolean hasCustomDifficulty() {
        return this.hasCustomDifficulty;
    }

    @Override
    public void forceSetDifficulty(EnumDifficulty difficulty) {
        this.difficulty = difficulty;
    }

    @Override
    public boolean isPVPEnabled() {
        return this.getConfigAdapter().getConfig().getWorld().getPVPEnabled();
    }

    @Override
    public void setPVPEnabled(boolean enabled) {
        this.getConfigAdapter().getConfig().getWorld().setPVPEnabled(enabled);
    }

    @Override
    public boolean doesGenerateBonusChest() {
        return this.generateBonusChest;
    }

    private void setDoesGenerateBonusChest(boolean state) {
        this.generateBonusChest = state;
    }

    @Override
    public Vector3d getWorldBorderCenter() {
        return new Vector3d(this.borderCenterX, 0, this.borderCenterZ);
    }

    @Override
    public void setWorldBorderCenter(double x, double z) {
        this.borderCenterX = x;
        this.borderCenterZ = z;
    }

    @Override
    public double getWorldBorderDiameter() {
        return this.borderSize;
    }

    @Override
    public void setWorldBorderDiameter(double diameter) {
        this.borderSize = diameter;
    }

    @Override
    public double getWorldBorderTargetDiameter() {
        return this.borderSizeLerpTarget;
    }

    @Override
    public void setWorldBorderTargetDiameter(double diameter) {
        this.borderSizeLerpTarget = diameter;
    }

    @Override
    public double getWorldBorderDamageThreshold() {
        return this.borderSafeZone;
    }

    @Override
    public void setWorldBorderDamageThreshold(double distance) {
        this.borderSafeZone = distance;
    }

    @Override
    public double getWorldBorderDamageAmount() {
        return this.borderDamagePerBlock;
    }

    @Override
    public void setWorldBorderDamageAmount(double damage) {
        this.borderDamagePerBlock = damage;
    }

    @Override
    public int getWorldBorderWarningTime() {
        return this.borderWarningTime;
    }

    @Override
    public void setWorldBorderWarningTime(int time) {
        this.borderWarningTime = time;
    }

    @Override
    public int getWorldBorderWarningDistance() {
        return this.borderWarningDistance;
    }

    @Override
    public void setWorldBorderWarningDistance(int distance) {
        this.borderWarningDistance = distance;
    }

    @Override
    public long getWorldBorderTimeRemaining() {
        return this.borderSizeLerpTime;
    }

    @Override
    public void setWorldBorderTimeRemaining(long time) {
        this.borderSizeLerpTime = time;
    }

    @Override
    public Optional<String> getGameRule(String gameRule) {
        checkNotNull(gameRule, "The gamerule cannot be null!");
        if (this.gameRules.hasRule(gameRule)) {
            return Optional.of(this.gameRules.getString(gameRule));
        }
        return Optional.empty();
    }

    @Override
    public Map<String, String> getGameRules() {
        ImmutableMap.Builder<String, String> ruleMap = ImmutableMap.builder();
        for (String rule : this.gameRules.getRules()) {
            ruleMap.put(rule, this.gameRules.getString(rule));
        }
        return ruleMap.build();
    }

    @Override
    public void setGameRule(String gameRule, String value) {
        checkNotNull(gameRule, "The gamerule cannot be null!");
        checkNotNull(value, "The gamerule value cannot be null!");
        this.gameRules.setOrCreateGameRule(gameRule, value);
    }

    @Override
    public boolean removeGameRule(String gameRule) {
        checkNotNull(gameRule, "The gamerule cannot be null!");
        return ((GameRulesBridge) this.gameRules).removeGameRule(gameRule);
    }

    @Override
    public void setDimensionId(int id) {
        this.dimensionId = id;
    }

    @Override
    public Integer getDimensionId() {
        return this.dimensionId;
    }

    @Override
    public UUID getUniqueId() {
        return this.uuid;
    }

    @Override
    public int getContentVersion() {
        return 0;
    }

    @Override
    public DataContainer toContainer() {
        return NbtTranslator.getInstance().translateFrom(this.cloneNBTCompound(null));
    }

    @Override
    public boolean isEnabled() {
        return this.getConfigAdapter().getConfig().getWorld().isWorldEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.getConfigAdapter().getConfig().getWorld().setWorldEnabled(enabled);
    }

    @Override
    public boolean loadOnStartup() {
        Boolean loadOnStartup = this.getConfigAdapter().getConfig().getWorld().loadOnStartup();
        if (loadOnStartup == null) {
           loadOnStartup = ((DimensionTypeBridge) this.dimensionType).shouldGenerateSpawnOnLoad();
           this.setLoadOnStartup(loadOnStartup);
        }
        return loadOnStartup;
    }

    @Override
    public void setLoadOnStartup(boolean state) {
        this.getConfigAdapter().getConfig().getWorld().setLoadOnStartup(state);
        this.saveConfig();
    }

    @Override
    public boolean doesKeepSpawnLoaded() {
        Boolean keepSpawnLoaded = this.getConfigAdapter().getConfig().getWorld().getKeepSpawnLoaded();
        if (keepSpawnLoaded == null) {
            keepSpawnLoaded = ((DimensionTypeBridge) this.dimensionType).shouldLoadSpawn();
        } else if (this.isMod && !keepSpawnLoaded) { // If disabled and a mod dimension, validate
            if (this.dimensionId == ((net.minecraft.world.DimensionType)(Object) this.dimensionType).getId()) {
                keepSpawnLoaded = ((DimensionTypeBridge) this.dimensionType).shouldKeepSpawnLoaded();
                this.setKeepSpawnLoaded(keepSpawnLoaded);
            }
        }
        return keepSpawnLoaded;
    }

    @Override
    public void setKeepSpawnLoaded(boolean loaded) {
        this.getConfigAdapter().getConfig().getWorld().setKeepSpawnLoaded(loaded);
        this.saveConfig();
    }

    @Override
    public boolean doesGenerateSpawnOnLoad() {
        Boolean shouldGenerateSpawn = this.getConfigAdapter().getConfig().getWorld().getGenerateSpawnOnLoad();
        if (shouldGenerateSpawn == null) {
            shouldGenerateSpawn = ((DimensionTypeBridge) this.dimensionType).shouldGenerateSpawnOnLoad();
            this.setGenerateSpawnOnLoad(shouldGenerateSpawn);
        }
        return shouldGenerateSpawn;
    }

    @Override
    public void setGenerateSpawnOnLoad(boolean state) {
        this.getConfigAdapter().getConfig().getWorld().setGenerateSpawnOnLoad(state);
    }

    @Override
    public void setUniqueId(UUID uniqueId) {
        this.uuid = uniqueId;
    }

    @Override
    public void setIsMod(boolean flag) {
        this.isMod = flag;
    }

    @Override
    public void setScoreboard(ServerScoreboard scoreboard) {
        this.scoreboard = scoreboard;
    }

    @Override
    public boolean getIsMod() {
        return this.isMod;
    }

    @Override
    public SpongeConfig<WorldConfig> getConfigAdapter() {
        if (this.worldConfig == null) {
            this.createWorldConfig();
        }
        return this.worldConfig;
    }

    @Override
    public Collection<WorldGeneratorModifier> getGeneratorModifiers() {
        return WorldGeneratorModifierRegistryModule.getInstance().toModifiers(this.getConfigAdapter().getConfig().getWorldGenModifiers());
    }

    @Override
    public void setGeneratorModifiers(Collection<WorldGeneratorModifier> modifiers) {
        checkNotNull(modifiers, "modifiers");

        this.getConfigAdapter().getConfig().getWorldGenModifiers().clear();
        this.getConfigAdapter().getConfig().getWorldGenModifiers().addAll(WorldGeneratorModifierRegistryModule.getInstance().toIds(modifiers));
    }

    @Override
    public DataContainer getGeneratorSettings() {
        // Minecraft uses a String, we want to return a fancy DataContainer
        // Parse the world generator settings as JSON
        try {
            return DataFormats.JSON.read(this.generatorOptions);
        } catch (JsonParseException | IOException ignored) {
        }
        return DataContainer.createNew().set(Constants.Sponge.World.WORLD_CUSTOM_SETTINGS, this.generatorOptions);
    }

    @Override
    public SerializationBehavior getSerializationBehavior() {
        return this.serializationBehavior;
    }

    @Override
    public void setSerializationBehavior(SerializationBehavior behavior) {
        this.serializationBehavior = behavior;
    }

    @Override
    public Optional<DataView> getPropertySection(DataQuery path) {
        if (this.spongeRootLevelNbt.hasKey(path.toString())) {
            return Optional
                    .<DataView>of(NbtTranslator.getInstance().translateFrom(this.spongeRootLevelNbt.getCompoundTag(path.toString())));
        }
        return Optional.empty();
    }

    @Override
    public void setPropertySection(DataQuery path, DataView data) {
        NBTTagCompound nbt = NbtTranslator.getInstance().translateData(data);
        this.spongeRootLevelNbt.setTag(path.toString(), nbt);
    }

    @Override
    public int getIndexForUniqueId(UUID uuid) {
        final Integer index = this.playerUniqueIdMap.inverse().get(uuid);
        if (index != null) {
            return index;
        }

        this.playerUniqueIdMap.put(this.trackedUniqueIdCount, uuid);
        this.pendingUniqueIds.add(uuid);
        return this.trackedUniqueIdCount++;
    }

    @Override
    public Optional<UUID> getUniqueIdForIndex(int index) {
        return Optional.ofNullable(this.playerUniqueIdMap.get(index));
    }

    @Override
    public NBTTagCompound getSpongeRootLevelNbt() {
        this.writeSpongeNbt();
        return this.spongeRootLevelNbt;
    }

    @Override
    public NBTTagCompound getSpongeNbt() {
        this.writeSpongeNbt();
        return this.spongeNbt;
    }

    @Override
    public void setSpongeRootLevelNBT(NBTTagCompound nbt) {
        this.spongeRootLevelNbt = nbt;
        if (nbt.hasKey(Constants.Sponge.SPONGE_DATA)) {
            this.spongeNbt = nbt.getCompoundTag(Constants.Sponge.SPONGE_DATA);
        }
    }

    @Override
    public void readSpongeNbt(NBTTagCompound nbt) {
        final UUID nbtUniqueId = nbt.getUniqueId(Constants.UUID);
        if (UUID.fromString("00000000-0000-0000-0000-000000000000").equals(nbtUniqueId)) {
            return;
        }
        this.uuid = nbtUniqueId;
        this.dimensionId = nbt.getInteger(Constants.Sponge.World.DIMENSION_ID);
        final String dimensionTypeId = nbt.getString(Constants.Sponge.World.DIMENSION_TYPE);
        final DimensionType dimensionType = (org.spongepowered.api.world.DimensionType)(Object) WorldManager.getDimensionType(this.dimensionId).orElse(null);
        this.setDimensionType(dimensionType != null ? dimensionType : DimensionTypeRegistryModule.getInstance().getById(dimensionTypeId)
                .orElseThrow(FunctionalUtil.invalidArgument("Could not find a DimensionType registered for world '" + this.getWorldName() + "' with dim id: " + this.dimensionId)));
        this.generateBonusChest = nbt.getBoolean(Constants.World.GENERATE_BONUS_CHEST);
        this.portalAgentType = PortalAgentRegistryModule.getInstance().validatePortalAgent(nbt.getString(Constants.Sponge.World.PORTAL_AGENT_TYPE), this.levelName);
        this.hasCustomDifficulty = nbt.getBoolean(Constants.Sponge.World.HAS_CUSTOM_DIFFICULTY);
        this.trackedUniqueIdCount = 0;
        if (nbt.hasKey(Constants.Sponge.World.WORLD_SERIALIZATION_BEHAVIOR)) {
            short saveBehavior = nbt.getShort(Constants.Sponge.World.WORLD_SERIALIZATION_BEHAVIOR);
            if (saveBehavior == 1) {
                this.serializationBehavior = SerializationBehaviors.AUTOMATIC;
            } else if (saveBehavior == 0) {
                this.serializationBehavior = SerializationBehaviors.MANUAL;
            } else {
                this.serializationBehavior = SerializationBehaviors.NONE;
            }
        }
        if (nbt.hasKey(Constants.Sponge.SPONGE_PLAYER_UUID_TABLE, Constants.NBT.TAG_LIST)) {
            final NBTTagList playerIdList = nbt.getTagList(Constants.Sponge.SPONGE_PLAYER_UUID_TABLE, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < playerIdList.tagCount(); i++) {
                final NBTTagCompound playerId = playerIdList.getCompoundTagAt(i);
                final UUID playerUuid = playerId.getUniqueId(Constants.UUID);
                final Integer playerIndex = this.playerUniqueIdMap.inverse().get(playerUuid);
                if (playerIndex == null) {
                    this.playerUniqueIdMap.put(this.trackedUniqueIdCount++, playerUuid);
                } else {
                    playerIdList.removeTag(i);
                }
            }

        }
    }

    private void writeSpongeNbt() {
        // Never save Sponge data if we have no UUID
        if (this.uuid != null && this.isValid()) {
            this.spongeNbt.setInteger(Constants.Sponge.DATA_VERSION, DataUtil.DATA_VERSION);
            this.spongeNbt.setUniqueId(Constants.UUID, this.uuid);
            this.spongeNbt.setInteger(Constants.Sponge.World.DIMENSION_ID, this.dimensionId);
            this.spongeNbt.setString(Constants.Sponge.World.DIMENSION_TYPE, this.dimensionType.getId());
            this.spongeNbt.setBoolean(Constants.World.GENERATE_BONUS_CHEST, this.generateBonusChest);
            if (this.portalAgentType == null) {
                this.portalAgentType = PortalAgentTypes.DEFAULT;
            }
            this.spongeNbt.setString(Constants.Sponge.World.PORTAL_AGENT_TYPE, this.portalAgentType.getPortalAgentClass().getName());
            short saveBehavior = 1;
            if (this.serializationBehavior == SerializationBehaviors.NONE) {
                saveBehavior = -1;
            } else if (this.serializationBehavior == SerializationBehaviors.MANUAL) {
                saveBehavior = 0;
            }
            this.spongeNbt.setShort(Constants.Sponge.World.WORLD_SERIALIZATION_BEHAVIOR, saveBehavior);
            this.spongeNbt.setBoolean(Constants.Sponge.World.HAS_CUSTOM_DIFFICULTY, this.hasCustomDifficulty);
            final Iterator<UUID> iterator = this.pendingUniqueIds.iterator();
            final NBTTagList playerIdList = this.spongeNbt.getTagList(Constants.Sponge.SPONGE_PLAYER_UUID_TABLE, Constants.NBT.TAG_COMPOUND);
            while (iterator.hasNext()) {
                final NBTTagCompound compound = new NBTTagCompound();
                compound.setUniqueId(Constants.UUID, iterator.next());
                playerIdList.appendTag(compound);
                iterator.remove();
            }
        }
    }

    // In development, this methos is replaced
    // by the overwrite below.
    // In production, this method calls the (re-obfuscated)
    // overwritten method below
    public String worldproperties$getWorldName() {
        return this.getWorldName();
    }

    /**
     * @reason Some mods set a null levelName, which is incompatible with Spongne's policy
     * of never returning null from our API. Since this method has the same deobfuscated
     * name as an API method, I'm choosing to overwrite it to keep development and production
     * consistent. If we end up breaking mods because of this, we'll probably need to switch
     * to using a wrapepr type for WorldInfo
     *
     * @author Aaron1011 - August 9th, 2018
     */
    @Override
    @Overwrite
    public String getWorldName() {
        if (this.levelName == null) {
            this.levelName = "";
        }
        return this.levelName;
    }

    @Override
    public DataContainer getAdditionalProperties() {
        NBTTagCompound additionalProperties = this.spongeRootLevelNbt.copy();
        additionalProperties.removeTag(SpongeImpl.ECOSYSTEM_NAME);
        return NbtTranslator.getInstance().translateFrom(additionalProperties);
    }

    private void saveConfig() {
        this.getConfigAdapter().save();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("levelName", this.levelName)
            .add("terrainType", this.terrainType)
            .add("uuid", this.uuid)
            .add("dimensionId", this.dimensionId)
            .add("dimensionType", this.dimensionType)
            .add("spawnX", this.spawnX)
            .add("spawnY", this.spawnY)
            .add("spawnZ", this.spawnZ)
            .add("gameType", this.gameType)
            .add("hardcore", this.hardcore)
            .add("difficulty", this.difficulty)
            .add("isMod", this.isMod)
            .toString();
    }
}

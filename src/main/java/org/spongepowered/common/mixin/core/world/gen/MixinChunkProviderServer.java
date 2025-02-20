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
package org.spongepowered.common.mixin.core.world.gen;

import com.flowpowered.math.vector.Vector3i;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import org.spongepowered.api.world.SerializationBehaviors;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.bridge.world.ServerWorldBridge;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.bridge.world.WorldInfoBridge;
import org.spongepowered.common.bridge.world.chunk.ChunkBridge;
import org.spongepowered.common.bridge.world.chunk.ChunkProviderBridge;
import org.spongepowered.common.bridge.world.chunk.ServerChunkProviderBridge;
import org.spongepowered.common.config.category.WorldCategory;
import org.spongepowered.common.event.tracking.IPhaseState;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.phase.generation.GenerationPhase;
import org.spongepowered.common.util.CachedLong2ObjectMap;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.world.SpongeEmptyChunk;
import org.spongepowered.common.world.storage.WorldStorageUtil;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

@Mixin(ChunkProviderServer.class)
public abstract class MixinChunkProviderServer implements ServerChunkProviderBridge, ChunkProviderBridge {

    private SpongeEmptyChunk impl$EMPTY_CHUNK;
    private boolean impl$denyChunkRequests = true;
    private boolean impl$forceChunkRequests = false;
    private long impl$chunkUnloadDelay = Constants.World.DEFAULT_CHUNK_UNLOAD_DELAY;
    private int impl$maxChunkUnloads = Constants.World.MAX_CHUNK_UNLOADS;

    @Shadow @Final public WorldServer world;
    @Shadow @Final public IChunkLoader chunkLoader;
    @Shadow public IChunkGenerator chunkGenerator;
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Shadow @Final @Mutable public Long2ObjectMap<Chunk> loadedChunks = new CachedLong2ObjectMap();

    @Shadow public abstract Chunk getLoadedChunk(int x, int z);
    @Shadow public abstract Chunk loadChunk(int x, int z);
    @Shadow protected abstract Chunk loadChunkFromFile(int x, int z);
    @Shadow public abstract Chunk provideChunk(int x, int z);
    @Shadow protected abstract void saveChunkExtraData(Chunk chunkIn);
    @Shadow protected abstract void saveChunkData(Chunk chunkIn);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void impl$setUpCommonFields(
        final WorldServer worldObjIn, final IChunkLoader chunkLoaderIn, final IChunkGenerator chunkGeneratorIn, final CallbackInfo ci) {
        if (((WorldBridge) worldObjIn).isFake()) {
            return;
        }
        this.impl$EMPTY_CHUNK = new SpongeEmptyChunk(worldObjIn, 0, 0);
        final WorldCategory worldCategory = ((WorldInfoBridge) this.world.getWorldInfo()).getConfigAdapter().getConfig().getWorld();

        ((ServerWorldBridge) worldObjIn).bridge$updateConfigCache();

        this.impl$denyChunkRequests = worldCategory.getDenyChunkRequests();
        this.impl$chunkUnloadDelay = worldCategory.getChunkUnloadDelay() * 1000;
        this.impl$maxChunkUnloads = worldCategory.getMaxChunkUnloads();
    }

    @Override
    public CompletableFuture<Boolean> bridge$doesChunkExistSync(final Vector3i chunkCoords) {
        return WorldStorageUtil.doesChunkExistSync(this.world, this.chunkLoader, chunkCoords);
    }

    /**
     * @author blood - October 25th, 2016
     * @reason Removes usage of droppedChunksSet in favor of unloaded flag.
     *
     * @param chunkIn The chunk to queue
     */
    @Overwrite
    public void queueUnload(final Chunk chunkIn)
    {
        if (!((ChunkBridge) chunkIn).isPersistedChunk() && this.world.provider.canDropChunk(chunkIn.x, chunkIn.z))
        {
            // Sponge - we avoid using the queue and simply check the unloaded flag during unloads
            //this.droppedChunksSet.add(Long.valueOf(ChunkPos.asLong(chunkIn.x, chunkIn.z)));
            chunkIn.unloadQueued = true;
        }
    }



    @Redirect(method = "provideChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/gen/ChunkProviderServer;loadChunk(II)Lnet/minecraft/world/chunk/Chunk;"))
    private Chunk impl$ProvideChunkForced(final ChunkProviderServer chunkProviderServer, final int x, final int z) {
        if (!this.impl$denyChunkRequests) {
            return this.loadChunk(x, z);
        }

        Chunk chunk = this.getLoadedChunk(x, z);
        if (chunk == null && this.impl$canDenyChunkRequest()) {
            return this.impl$EMPTY_CHUNK;
        }

        if (chunk == null) {
            chunk = this.impl$loadChunkForce(x, z);
        }

        return chunk;
    }

    @Inject(method = "provideChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/math/ChunkPos;asLong(II)J"))
    private void impl$StartTerrainGenerationPhase(final int x, final int z, final CallbackInfoReturnable<Chunk> cir) {
        GenerationPhase.State.TERRAIN_GENERATION.createPhaseContext()
            .world(this.world)
            .buildAndSwitch();
    }

    @Inject(method = "provideChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/Chunk;populate(Lnet/minecraft/world/chunk/IChunkProvider;Lnet/minecraft/world/gen/IChunkGenerator;)V",
            shift = Shift.AFTER))
    private void impl$EndTerrainGenerationPhase(final int x, final int z, final CallbackInfoReturnable<Chunk> ci) {
        PhaseTracker.getInstance().getCurrentContext().close();
    }

    @Inject(method = "provideChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/crash/CrashReportCategory;addCrashSection(Ljava/lang/String;Ljava/lang/Object;)V",
            ordinal = 2,
            shift = Shift.AFTER
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void impl$StopGenerationPhaseFromError(final int x, final int z, final CallbackInfoReturnable<Chunk> cir, final Chunk ungenerated,
        final long chunkIndex, final Throwable error, final CrashReport report, final CrashReportCategory chunkGenerationCategory,
        final ChunkProviderServer provider, final int someVar, final int someOther) {

        final PhaseContext<?> currentContext = PhaseTracker.getInstance().getCurrentContext();
        report.makeCategory("Current PhaseState")
            .addDetail(currentContext.state.toString(), () -> {

                final PrettyPrinter printer = new PrettyPrinter(50);
                PhaseTracker.CONTEXT_PRINTER.accept(printer, currentContext);
                final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                printer.print(new PrintStream(stream));

                return stream.toString();
            });
        // Since we still want to complete the phase in the case we can recover, we still must close the current context.
        currentContext.close();
    }

    private boolean impl$canDenyChunkRequest() {
        if (!SpongeImpl.getServer().isCallingFromMinecraftThread()) {
            return true;
        }

        if (this.impl$forceChunkRequests) {
            return false;
        }

        final PhaseTracker phaseTracker = PhaseTracker.getInstance();
        final IPhaseState<?> currentState = phaseTracker.getCurrentState();
        return currentState.doesDenyChunkRequests();
    }

    @Override
    public boolean bridge$getForceChunkRequests() {
        return this.impl$forceChunkRequests;
    }

    @Override
    public void bridge$setMaxChunkUnloads(final int maxUnloads) {
        this.impl$maxChunkUnloads = maxUnloads;
    }

    @Override
    public void bridge$setForceChunkRequests(final boolean flag) {
        this.impl$forceChunkRequests = flag;
    }

    @Override
    public void bridge$setDenyChunkRequests(final boolean flag) {
        this.impl$denyChunkRequests = flag;
    }

    @Override
    public long bridge$getChunkUnloadDelay() {
        return this.impl$chunkUnloadDelay;
    }

    /**
     * @author blood - October 20th, 2016
     * @reason Refactors entire method to not use the droppedChunksSet by
     * simply looping through all loaded chunks and determining whether it
     * can unload or not.
     *
     * @return true if unload queue was processed
     */
    @Overwrite
    public boolean tick()
    {
        if (!this.world.disableLevelSaving && !((WorldBridge) this.world).isFake())
        {
            ((ServerWorldBridge) this.world).bridge$getTimingsHandler().doChunkUnload.startTiming();
            final Iterator<Chunk> iterator = this.loadedChunks.values().iterator();
            int chunksUnloaded = 0;
            final long now = System.currentTimeMillis();
            while (chunksUnloaded < this.impl$maxChunkUnloads && iterator.hasNext()) {
                final Chunk chunk = iterator.next();
                final ChunkBridge spongeChunk = (ChunkBridge) chunk;
                if (chunk != null && chunk.unloadQueued && !spongeChunk.isPersistedChunk()) {
                    if (this.bridge$getChunkUnloadDelay() > 0) {
                        if ((now - spongeChunk.getScheduledForUnload()) < this.impl$chunkUnloadDelay) {
                            continue;
                        }
                        spongeChunk.setScheduledForUnload(-1);
                    }
                    chunk.onUnload();
                    this.saveChunkData(chunk);
                    this.saveChunkExtraData(chunk);
                    iterator.remove();
                    chunksUnloaded++;
                }
            }
            ((ServerWorldBridge) this.world).bridge$getTimingsHandler().doChunkUnload.stopTiming();
        }

        this.chunkLoader.chunkTick();
        return false;
    }

    // Copy of getLoadedChunk without marking chunk active.
    // This allows the chunk to unload if currently queued.
    @Override
    public Chunk bridge$getLoadedChunkWithoutMarkingActive(final int x, final int z){
        final long i = ChunkPos.asLong(x, z);
        return this.loadedChunks.get(i);
    }

    @Inject(method = "canSave", at = @At("HEAD"), cancellable = true)
    private void impl$IgnoreIfWorldSaveDisabled(final CallbackInfoReturnable<Boolean> cir) {
        if (((WorldProperties)this.world.getWorldInfo()).getSerializationBehavior() == SerializationBehaviors.NONE) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "saveChunkData", at = @At("HEAD"), cancellable = true)
    private void impl$IgnoreIfWorldSaveDisabled(final Chunk chunkIn, final CallbackInfo ci) {
        if (((WorldProperties)this.world.getWorldInfo()).getSerializationBehavior() == SerializationBehaviors.NONE) {
            ci.cancel();
        }
    }

    @Inject(method = "flushToDisk", at = @At("HEAD"), cancellable = true)
    private void impl$IgnoreIfWorldSaveDisabled(final CallbackInfo ci) {
        if (((WorldProperties)this.world.getWorldInfo()).getSerializationBehavior() == SerializationBehaviors.NONE) {
            ci.cancel();
        }
    }

    @Override
    public void bridge$unloadChunkAndSave(final Chunk chunk) {
        boolean saveChunk = false;
        if (chunk.needsSaving(true)) {
            saveChunk = true;
        }

        chunk.onUnload();

        if (saveChunk) {
            this.saveChunkData(chunk);
        }

        this.loadedChunks.remove(ChunkPos.asLong(chunk.x, chunk.z));
        ((ChunkBridge) chunk).setScheduledForUnload(-1);
    }
}

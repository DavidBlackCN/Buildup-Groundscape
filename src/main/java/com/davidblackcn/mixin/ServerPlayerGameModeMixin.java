package com.davidblackcn.mixin;

import com.davidblackcn.block.SodPatchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;

    // AFTER this invocation is also after Fabric's cancellable BEFORE break event.
    // Vanilla game-mode/tool restrictions have already been checked. Bare patches pass through.
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;",
            shift = At.Shift.AFTER), cancellable = true)
    private void groundscape$breakVegetationFirst(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SodPatchBlock.removeVegetationForPlayer(level, pos, player)) cir.setReturnValue(true);
    }
}

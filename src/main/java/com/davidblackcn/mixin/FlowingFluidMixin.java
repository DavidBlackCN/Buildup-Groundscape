package com.davidblackcn.mixin;

import com.davidblackcn.block.LeafPatchBlock;
import com.davidblackcn.block.SodPatchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Patches collide with entities, but must not form a fluid-proof wall, even at eight layers.
 * Only the native wall test sees air; admission, fluid replacement and drops remain native. */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @ModifyVariable(method = "canPassThroughWall(Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static BlockState groundscape$source(BlockState state) { return groundscape$permeable(state); }
    @ModifyVariable(method = "canPassThroughWall(Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static BlockState groundscape$target(BlockState state) { return groundscape$permeable(state); }
    @Unique
    private static BlockState groundscape$permeable(BlockState state) {
        return state.getBlock() instanceof SodPatchBlock || state.getBlock() instanceof LeafPatchBlock ? Blocks.AIR.defaultBlockState() : state;
    }
}

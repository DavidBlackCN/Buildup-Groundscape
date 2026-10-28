package com.davidblackcn.mixin;

import com.davidblackcn.block.LeafPatchBlock;
import com.davidblackcn.block.SodPatchBlock;
import com.davidblackcn.block.PatchWaterRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity collision is independent of fluid passage. Water admission additionally rejects thick
 * patches; other fluids retain their existing behavior. Native replacement and drops are retained. */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @Inject(method = "canHoldSpecificFluid(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/Fluid;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void groundscape$waterAdmission(BlockGetter level, BlockPos pos, BlockState state,
            Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
        if (fluid.is(FluidTags.WATER) && PatchWaterRules.blocksWater(state)) cir.setReturnValue(false);
    }

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

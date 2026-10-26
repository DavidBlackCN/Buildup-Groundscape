package com.davidblackcn.mixin;

import com.davidblackcn.block.PlantSupport;
import com.davidblackcn.block.SodPatchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VegetationBlock.class)
abstract class VegetationSupportMixin {
    @Inject(method = "canSurvive(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private void groundscape$fullPatchSupport(BlockState plant, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState below = level.getBlockState(pos.below());
        if (SodPatchBlock.canContain(plant) && below.getBlock() instanceof SodPatchBlock patch
                && below.getValue(SodPatchBlock.LAYERS) == 8 && below.getValue(SodPatchBlock.VEGETATION) == SodPatchBlock.Vegetation.NONE) {
            cir.setReturnValue(PlantSupport.accepts(plant, patch.substrate().defaultBlockState(), level, pos.below()));
        }
    }
}

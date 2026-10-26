package com.davidblackcn.test;

import com.davidblackcn.registry.GroundscapeBlocks;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Server-side checks exercise registered blocks and loaded data in the real game. */
public final class GroundscapeGameTests {
    private static final BlockPos CELL = new BlockPos(3, 2, 3);
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    @GameTest
    public void allThirtyEightBlocksAndItemsAreRegistered(GameTestHelper helper) {
        Set<Identifier> ids = new HashSet<>();
        helper.assertValueEqual(GroundscapeBlocks.variants().size(), 11, "eleven material families");
        for (var variant : GroundscapeBlocks.variants()) {
            List<Block> forms = forms(variant);
            var suffixes = variant.family().forms();
            for (int i = 0; i < forms.size(); i++) {
                Block block = forms.get(i);
                Identifier expected = Identifier.fromNamespaceAndPath("buildup_groundscape",
                        variant.family().path(suffixes.get(i)));
                helper.assertValueEqual(BuiltInRegistries.BLOCK.getKey(block), expected, "stable block ID");
                helper.assertTrue(ids.add(expected), "unique variant " + expected);
                helper.assertTrue(block.asItem() instanceof BlockItem, "BlockItem for " + expected);
                helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(block.asItem()), expected, "matching item ID");
                helper.assertTrue(((BlockItem) block.asItem()).getBlock() == block, "item places its own block");
                helper.assertFalse(block.defaultBlockState().hasBlockEntity(), "no unnecessary block entity");
                helper.assertFalse(block.defaultBlockState().isRandomlyTicking(), "decorative sod does not propagate");
            }
        }
        helper.assertValueEqual(ids.size(), 38, "thirty-eight unique variants");
        helper.succeed();
    }

    @GameTest
    public void allPatchShapesMatchTheirPhysicalHeight(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(CELL);
        for (var variant : GroundscapeBlocks.variants()) {
            for (int layers = 1; layers <= 8; layers++) {
                BlockState state = variant.patch().defaultBlockState().setValue(BlockStateProperties.LAYERS, layers);
                VoxelShape expected = Shapes.box(0, 0, 0, 1, layers / 8.0, 1);
                assertShape(helper, state.getShape(helper.getLevel(), absolute), expected, "patch outline " + layers);
                assertShape(helper, state.getCollisionShape(helper.getLevel(), absolute), expected, "patch collision " + layers);
                assertShape(helper, state.getBlockSupportShape(helper.getLevel(), absolute), expected, "patch support " + layers);
            }
            assertShape(helper, variant.block().defaultBlockState().getCollisionShape(helper.getLevel(), absolute),
                    Shapes.block(), "full sod collision");
        }
        helper.succeed();
    }

    @GameTest
    public void patchPlacementStacksConsumesAndPreservesFullLayerIdentity(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        for (var variant : GroundscapeBlocks.variants()) {
            resetCell(helper);
            ItemStack stack = new ItemStack(variant.patch(), 10);
            helper.assertTrue(place(helper, player, stack, CELL.below(), Direction.UP).consumesAction(), "first patch placement");
            helper.assertBlockProperty(CELL, BlockStateProperties.LAYERS, 1);
            for (int layers = 2; layers <= 8; layers++) {
                player.setShiftKeyDown(layers % 2 == 0);
                helper.assertTrue(place(helper, player, stack, CELL, Direction.UP).consumesAction(), "top stacking " + layers);
                helper.assertBlockProperty(CELL, BlockStateProperties.LAYERS, layers);
                helper.assertValueEqual(stack.getCount(), 10 - layers, "one item per layer");
            }
            helper.assertBlockPresent(variant.patch(), CELL);
            helper.assertTrue(place(helper, player, stack, CELL, Direction.UP).consumesAction(), "place above full patch");
            helper.assertBlockProperty(CELL, BlockStateProperties.LAYERS, 8);
            helper.assertBlockProperty(CELL.above(), BlockStateProperties.LAYERS, 1);
            helper.assertValueEqual(stack.getCount(), 1, "full-layer boundary consumes only one item");
            player.setShiftKeyDown(false);
        }
        helper.succeed();
    }

    @GameTest
    public void patchSideClicksAndDifferentItemsDoNotReplaceExistingLayers(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        for (var variant : GroundscapeBlocks.variants()) {
            resetCell(helper);
            helper.setBlock(CELL, variant.patch().defaultBlockState().setValue(BlockStateProperties.LAYERS, 3));
            ItemStack stack = new ItemStack(variant.patch(), 2);
            helper.setBlock(CELL.east().below(), Blocks.STONE);
            helper.assertTrue(place(helper, player, stack, CELL, Direction.EAST).consumesAction(), "side places adjacent");
            helper.assertBlockProperty(CELL, BlockStateProperties.LAYERS, 3);
            helper.assertBlockProperty(CELL.east(), BlockStateProperties.LAYERS, 1);
            helper.assertValueEqual(stack.getCount(), 1, "side placement consumes one");

            BlockState existing = helper.getBlockState(CELL);
            ItemStack other = new ItemStack(Items.STICK);
            BlockPlaceContext context = context(helper, player, other, CELL, Direction.UP);
            helper.assertFalse(existing.canBeReplaced(context), "unrelated item must not replace patch");
            Block otherPatch = GroundscapeBlocks.variants().stream()
                    .filter(candidate -> candidate.patch() != variant.patch()).findFirst().orElseThrow().patch();
            helper.assertFalse(existing.canBeReplaced(context(helper, player, new ItemStack(otherPatch), CELL, Direction.UP)),
                    "different material must not merge");
        }
        helper.succeed();
    }

    @GameTest
    public void patchesRejectUnsupportedAndFluidPlacement(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        for (var variant : GroundscapeBlocks.variants()) {
            resetCell(helper);
            helper.setBlock(CELL.below(), Blocks.AIR);
            helper.setBlock(CELL.west(), Blocks.STONE);
            ItemStack stack = new ItemStack(variant.patch(), 3);
            helper.assertFalse(place(helper, player, stack, CELL.west(), Direction.EAST).consumesAction(), "unsupported placement rejected");
            helper.assertValueEqual(stack.getCount(), 3, "failed placement consumes nothing");
            helper.assertTrue(helper.getBlockState(CELL).isAir(), "unsupported cell remains air");
            helper.setBlock(CELL.below(), Blocks.STONE);
            for (Block fluid : List.of(Blocks.WATER, Blocks.LAVA)) {
                helper.setBlock(CELL, fluid);
                helper.assertFalse(place(helper, player, stack, CELL.below(), Direction.UP).consumesAction(), "patch rejects fluid " + fluid);
                helper.assertValueEqual(stack.getCount(), 3, "fluid rejection consumes nothing");
                helper.assertBlockPresent(fluid, CELL);
            }
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void supportRemovalDropsAllLayers(GameTestHelper helper) {
        for (int i = 0; i < GroundscapeBlocks.variants().size(); i++) {
            var variant = GroundscapeBlocks.variants().get(i);
            BlockPos pos = new BlockPos(i + 1, 2, 3);
            helper.setBlock(pos.below(2), Blocks.STONE);
            helper.setBlock(pos.below(), Blocks.STONE);
            helper.setBlock(pos, variant.patch().defaultBlockState().setValue(BlockStateProperties.LAYERS, 5));
            helper.setBlock(pos.below(), Blocks.AIR);
            helper.assertTrue(helper.getBlockState(pos).isAir(), "unsupported patch removes itself");
        }
        helper.runAfterDelay(2, () -> {
            for (int i = 0; i < GroundscapeBlocks.variants().size(); i++) {
                helper.assertItemEntityCountIs(GroundscapeBlocks.variants().get(i).patch().asItem(),
                        new BlockPos(i + 1, 2, 3), 2.0, 5);
            }
            helper.succeed();
        });
    }

    @GameTest
    public void patchesBlockNativeGroundSpawningAtEveryHeight(GameTestHelper helper) {
        resetCell(helper);
        BlockPos absolute = helper.absolutePos(CELL);
        helper.setBlock(CELL, Blocks.GRASS_BLOCK);
        helper.assertTrue(helper.getBlockState(CELL).isValidSpawn(helper.getLevel(), absolute, EntityTypes.ZOMBIE),
                "grass control is a valid spawning surface");
        helper.assertTrue(SpawnPlacements.isSpawnPositionOk(EntityTypes.ZOMBIE, helper.getLevel(), absolute.above()),
                "grass control has valid empty spawn space");
        for (var variant : GroundscapeBlocks.variants()) {
            for (int layers = 1; layers <= 8; layers++) {
                helper.setBlock(CELL, variant.patch().defaultBlockState().setValue(BlockStateProperties.LAYERS, layers));
                helper.assertFalse(helper.getBlockState(CELL).isValidSpawn(helper.getLevel(), absolute, EntityTypes.ZOMBIE),
                        "patch surface rejects spawn at layer " + layers);
                helper.assertFalse(SpawnPlacements.isSpawnPositionOk(EntityTypes.ZOMBIE, helper.getLevel(), absolute.above()),
                        "native spawn placement rejects patch top " + layers);
                helper.assertFalse(SpawnPlacements.isSpawnPositionOk(EntityTypes.ZOMBIE, helper.getLevel(), absolute),
                        "native spawn placement rejects space inside patch " + layers);
            }
        }
        helper.succeed();
    }

    @GameTest
    public void slabsPlaceMergeWaterlogAndHaveNativeShapes(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(CELL);
        for (var variant : GroundscapeBlocks.variants()) {
            resetCell(helper);
            ItemStack stack = new ItemStack(variant.slab(), 5);
            helper.assertTrue(place(helper, player, stack, CELL.below(), Direction.UP).consumesAction(), "bottom slab");
            helper.assertBlockProperty(CELL, BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
            helper.assertTrue(place(helper, player, stack, CELL, Direction.UP).consumesAction(), "merge slabs");
            helper.assertBlockProperty(CELL, BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE);
            helper.assertValueEqual(stack.getCount(), 3, "double slab costs two");

            helper.setBlock(CELL, Blocks.AIR);
            helper.setBlock(CELL.above(), Blocks.STONE);
            helper.assertTrue(place(helper, player, stack, CELL.above(), Direction.DOWN).consumesAction(), "top slab");
            helper.assertBlockProperty(CELL, BlockStateProperties.SLAB_TYPE, SlabType.TOP);
            helper.setBlock(CELL.above(), Blocks.AIR);
            helper.setBlock(CELL, Blocks.WATER);
            helper.assertTrue(place(helper, player, stack, CELL.below(), Direction.UP).consumesAction(), "waterlogged slab");
            helper.assertBlockProperty(CELL, BlockStateProperties.WATERLOGGED, true);
            helper.assertTrue(helper.getBlockState(CELL).getFluidState().is(Fluids.WATER), "water is retained");
            helper.assertTrue(place(helper, player, stack, CELL, Direction.UP).consumesAction(), "wet slab merges");
            helper.assertBlockProperty(CELL, BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE);
            helper.assertBlockProperty(CELL, BlockStateProperties.WATERLOGGED, false);
            helper.assertValueEqual(stack.getCount(), 0, "each successful slab placement consumes one");

            for (SlabType type : SlabType.values()) {
                BlockState state = variant.slab().defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, type);
                BlockState nativeState = Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, type);
                assertShape(helper, state.getCollisionShape(helper.getLevel(), absolute), nativeState.getCollisionShape(helper.getLevel(), absolute), "slab collision " + type);
                assertShape(helper, state.getShape(helper.getLevel(), absolute), nativeState.getShape(helper.getLevel(), absolute), "slab outline " + type);
            }
        }
        helper.succeed();
    }

    @GameTest
    public void everyStairStateHasNativeCollisionAndOutline(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(CELL);
        for (var variant : GroundscapeBlocks.variants()) {
            for (Direction facing : HORIZONTAL) {
                for (Half half : Half.values()) {
                    for (StairsShape shape : StairsShape.values()) {
                        BlockState state = stair(variant.stairs(), facing, half).setValue(BlockStateProperties.STAIRS_SHAPE, shape);
                        BlockState vanilla = stair(Blocks.OAK_STAIRS, facing, half).setValue(BlockStateProperties.STAIRS_SHAPE, shape);
                        assertShape(helper, state.getCollisionShape(helper.getLevel(), absolute), vanilla.getCollisionShape(helper.getLevel(), absolute), "stairs collision " + state);
                        assertShape(helper, state.getShape(helper.getLevel(), absolute), vanilla.getShape(helper.getLevel(), absolute), "stairs outline " + state);
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest
    public void stairsPlaceInEveryDirectionHalfAndWater(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        for (var variant : GroundscapeBlocks.variants()) {
            for (int index = 0; index < HORIZONTAL.length; index++) {
                Direction facing = HORIZONTAL[index];
                // Vanilla yaw 0 faces south; the ordinal array starts at north.
                player.setYRot(180 + index * 90);
                for (Half half : Half.values()) {
                    resetCell(helper);
                    ItemStack stack = new ItemStack(variant.stairs(), 1);
                    if (half == Half.TOP) {
                        helper.setBlock(CELL.above(), Blocks.STONE);
                    }
                    BlockPos clicked = half == Half.TOP ? CELL.above() : CELL.below();
                    Direction face = half == Half.TOP ? Direction.DOWN : Direction.UP;
                    helper.assertTrue(place(helper, player, stack, clicked, face).consumesAction(), "stairs placed");
                    helper.assertBlockProperty(CELL, BlockStateProperties.HORIZONTAL_FACING, facing);
                    helper.assertBlockProperty(CELL, BlockStateProperties.HALF, half);
                    helper.assertValueEqual(stack.getCount(), 0, "stairs placement consumes one");
                }
            }
            resetCell(helper);
            helper.setBlock(CELL, Blocks.WATER);
            ItemStack stack = new ItemStack(variant.stairs(), 1);
            helper.assertTrue(place(helper, player, stack, CELL.below(), Direction.UP).consumesAction(), "stairs water placement");
            helper.assertBlockProperty(CELL, BlockStateProperties.WATERLOGGED, true);
            helper.assertTrue(helper.getBlockState(CELL).getFluidState().is(Fluids.WATER), "stairs retains water");
        }
        helper.succeed();
    }

    @GameTest
    public void stairCornersUpdateFromRealNeighbors(GameTestHelper helper) {
        for (var variant : GroundscapeBlocks.variants()) {
            for (Direction facing : HORIZONTAL) {
                for (Half half : Half.values()) {
                    for (boolean inner : List.of(false, true)) {
                        for (boolean left : List.of(false, true)) {
                            resetCell(helper);
                            helper.setBlock(CELL, stair(variant.stairs(), facing, half));
                            BlockPos neighbor = CELL.relative(inner ? facing.getOpposite() : facing);
                            Direction turn = left ? facing.getCounterClockWise() : facing.getClockWise();
                            helper.setBlock(neighbor, stair(variant.stairs(), turn, half));
                            StairsShape expected = inner
                                    ? (left ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT)
                                    : (left ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT);
                            helper.assertBlockProperty(CELL, BlockStateProperties.STAIRS_SHAPE, expected);
                            helper.setBlock(neighbor, Blocks.AIR);
                            helper.assertBlockProperty(CELL, BlockStateProperties.STAIRS_SHAPE, StairsShape.STRAIGHT);
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest
    public void stateCodecsRoundTripAllVariantsAndStates(GameTestHelper helper) {
        int checked = 0;
        for (var variant : GroundscapeBlocks.variants()) {
            for (Block block : forms(variant)) {
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    var json = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
                    BlockState decoded = BlockState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                    helper.assertValueEqual(decoded, state, "state serialization preserves " + state);
                    checked++;
                }
            }
        }
        helper.assertTrue(checked >= 475, "all full/patch/slab/stair states were serialized");
        helper.succeed();
    }

    @GameTest
    public void familyMapColorsSoundsHardnessAndToolsArePreserved(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(CELL);
        for (var variant : GroundscapeBlocks.variants()) {
            BlockState vanilla = variant.family().vanillaBlock().defaultBlockState();
            boolean nylium = variant.family().id().endsWith("nylium");
            ItemStack tool = new ItemStack(nylium ? Items.DIAMOND_PICKAXE : Items.DIAMOND_SHOVEL);
            for (Block block : forms(variant)) {
                BlockState state = block.defaultBlockState();
                helper.assertValueEqual(state.getMapColor(helper.getLevel(), absolute), vanilla.getMapColor(helper.getLevel(), absolute), "family map color");
                helper.assertValueEqual(state.getSoundType(), vanilla.getSoundType(), "family sounds");
                helper.assertValueEqual(state.getDestroySpeed(helper.getLevel(), absolute), vanilla.getDestroySpeed(helper.getLevel(), absolute), "family hardness");
                helper.assertTrue(state.is(nylium ? BlockTags.MINEABLE_WITH_PICKAXE : BlockTags.MINEABLE_WITH_SHOVEL), "correct mining tag");
                helper.assertTrue(tool.getDestroySpeed(state) > new ItemStack(Items.STICK).getDestroySpeed(state), "correct tool mines faster");
            }
        }
        helper.succeed();
    }

    @GameTest
    public void lootCountsConserveMaterialsWithAndWithoutEnchantments(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(CELL);
        ItemStack fortune = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        fortune.enchant(enchantments.getOrThrow(Enchantments.FORTUNE), 3);
        silk.enchant(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
        for (var variant : GroundscapeBlocks.variants()) {
            for (ItemStack tool : List.of(ItemStack.EMPTY, fortune, silk)) {
                for (int layers = 1; layers <= 8; layers++) {
                    assertDrops(helper, variant.patch().defaultBlockState().setValue(BlockStateProperties.LAYERS, layers), absolute, tool, variant.patch(), layers);
                }
                if (variant.family().hasSodBlock()) assertDrops(helper, variant.block().defaultBlockState(), absolute, tool, variant.block(), 1);
                assertDrops(helper, variant.stairs().defaultBlockState(), absolute, tool, variant.stairs(), 1);
                for (SlabType type : SlabType.values()) {
                    assertDrops(helper, variant.slab().defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, type), absolute, tool, variant.slab(), type == SlabType.DOUBLE ? 2 : 1);
                }
            }
        }
        helper.succeed();
    }

    @GameTest
    public void creativePlacementDoesNotConsumeItems(GameTestHelper helper) {
        Player player = player(helper, GameType.CREATIVE);
        for (var variant : GroundscapeBlocks.variants()) {
            resetCell(helper);
            ItemStack stack = new ItemStack(variant.patch(), 1);
            helper.assertTrue(place(helper, player, stack, CELL.below(), Direction.UP).consumesAction(), "creative placement");
            helper.assertTrue(place(helper, player, stack, CELL, Direction.UP).consumesAction(), "creative stacking");
            helper.assertValueEqual(stack.getCount(), 1, "creative stack unchanged");
            helper.assertBlockProperty(CELL, BlockStateProperties.LAYERS, 2);
        }
        helper.succeed();
    }

    @GameTest
    public void fullBlocksPlaceAsIndependentSurvivalItems(GameTestHelper helper) {
        Player player = player(helper, GameType.SURVIVAL);
        for (var variant : GroundscapeBlocks.variants()) {
            if (!variant.family().hasSodBlock()) continue;
            resetCell(helper);
            ItemStack stack = new ItemStack(variant.block(), 2);
            helper.assertTrue(place(helper, player, stack, CELL.below(), Direction.UP).consumesAction(), "full block placement");
            helper.assertBlockPresent(variant.block(), CELL);
            helper.assertFalse(helper.getBlockState(CELL).is(variant.family().vanillaBlock()), "full sod retains independent identity");
            helper.assertValueEqual(stack.getCount(), 1, "survival full block consumes one");
            assertShape(helper, helper.getBlockState(CELL).getCollisionShape(helper.getLevel(), helper.absolutePos(CELL)),
                    Shapes.block(), "placed full block collision");
        }
        helper.succeed();
    }

    @GameTest
    public void allEightyTwoRecipesLoadMatchAndConserveVolume(GameTestHelper helper) {
        for (var variant : GroundscapeBlocks.variants()) {
            String prefix = variant.family().path("");
            if (variant.family().hasSodBlock()) assertRecipe(helper, prefix + "block", CraftingInput.of(1, 1,
                    List.of(new ItemStack(variant.family().vanillaBlock()))), variant.block(), 1, 8, 8);
            assertRecipe(helper, prefix + "patch", CraftingInput.of(1, 1,
                    List.of(new ItemStack(variant.block()))), variant.patch(), 8, 8, 1);
            assertRecipe(helper, prefix + "block_from_patches", CraftingInput.of(3, 3,
                    List.of(new ItemStack(variant.patch()), new ItemStack(variant.patch()), new ItemStack(variant.patch()),
                            new ItemStack(variant.patch()), new ItemStack(variant.patch()), new ItemStack(variant.patch()),
                            new ItemStack(variant.patch()), new ItemStack(variant.patch()), ItemStack.EMPTY)), variant.block(), 1, 8, 8);
            assertRecipe(helper, prefix + "slab", CraftingInput.of(3, 1,
                    List.of(new ItemStack(variant.block()), new ItemStack(variant.block()), new ItemStack(variant.block()))), variant.slab(), 6, 24, 4);
            assertRecipe(helper, prefix + "block_from_slabs", CraftingInput.of(2, 1,
                    List.of(new ItemStack(variant.slab()), new ItemStack(variant.slab()))), variant.block(), 1, 8, 8);
            assertRecipe(helper, prefix + "stairs", CraftingInput.of(3, 3,
                    List.of(new ItemStack(variant.block()), ItemStack.EMPTY, ItemStack.EMPTY,
                            new ItemStack(variant.block()), new ItemStack(variant.block()), ItemStack.EMPTY,
                            new ItemStack(variant.block()), new ItemStack(variant.block()), new ItemStack(variant.block()))), variant.stairs(), 8, 48, 6);
            assertRecipe(helper, prefix + "patch_from_stairs", CraftingInput.of(1, 1,
                    List.of(new ItemStack(variant.stairs()))), variant.patch(), 6, 6, 1);
            assertRecipe(helper, prefix + "patch_from_slab", CraftingInput.of(1, 1,
                    List.of(new ItemStack(variant.slab()))), variant.patch(), 4, 4, 1);
        }
        helper.succeed();
    }

    private static List<Block> forms(GroundscapeBlocks.Variant variant) {
        return variant.blocks();
    }

    private static BlockState stair(Block block, Direction facing, Half half) {
        return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.HALF, half);
    }

    private static Player player(GameTestHelper helper, GameType gameType) {
        Player player = helper.makeMockPlayer(gameType);
        // Unlike makeMockServerPlayer, vanilla's lightweight mock does not initialize abilities.
        // BlockItem consumption reads hasInfiniteMaterials(), which reads abilities.instabuild.
        gameType.updatePlayerAbilities(player.getAbilities());
        helper.assertValueEqual(player.hasInfiniteMaterials(), gameType == GameType.CREATIVE, "mock game mode abilities");
        player.setPos(helper.absoluteVec(new Vec3(0.5, 6, 0.5)));
        return player;
    }

    private static BlockPlaceContext context(GameTestHelper helper, Player player, ItemStack stack,
                                             BlockPos clicked, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(clicked);
        Vec3 hit = Vec3.atCenterOf(absolute).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(hit, face, absolute, false));
    }

    private static InteractionResult place(GameTestHelper helper, Player player, ItemStack stack,
                                            BlockPos clicked, Direction face) {
        return ((BlockItem) stack.getItem()).place(context(helper, player, stack, clicked, face));
    }

    private static void resetCell(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                for (int y = 1; y <= 5; y++) {
                    helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)),
                            Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
        helper.setBlock(CELL.below(), Blocks.STONE);
    }

    private static void assertShape(GameTestHelper helper, VoxelShape actual, VoxelShape expected, String message) {
        helper.assertTrue(Shapes.equal(actual, expected), message);
    }

    private static void assertDrops(GameTestHelper helper, BlockState state, BlockPos absolute,
                                    ItemStack tool, Block expectedItem, int expectedCount) {
        List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), absolute, null, null, tool);
        helper.assertTrue(drops.stream().allMatch(drop -> drop.is(expectedItem.asItem())), "only expected drops for " + state);
        helper.assertValueEqual(drops.stream().mapToInt(ItemStack::getCount).sum(), expectedCount, "loot count for " + state);
    }

    private static void assertRecipe(GameTestHelper helper, String path, CraftingInput input,
                                     Block output, int count, int inputVolume, int outputUnitVolume) {
        var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("buildup_groundscape", path));
        var manager = helper.getLevel().getServer().getRecipeManager();
        var holder = manager.byKey(key).orElseThrow(() -> new AssertionError("Recipe not loaded: " + path));
        helper.assertTrue(holder.value() instanceof CraftingRecipe, "crafting recipe " + path);
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "recipe matches actual grid " + path);
        ItemStack result = recipe.assemble(input);
        helper.assertTrue(result.is(output.asItem()), "recipe output item " + path);
        helper.assertValueEqual(result.getCount(), count, "recipe output count " + path);
        helper.assertValueEqual(inputVolume, count * outputUnitVolume, "recipe material conservation " + path);
        var selected = manager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        helper.assertValueEqual(selected.id(), key, "normal crafting lookup selects " + path);
    }
}

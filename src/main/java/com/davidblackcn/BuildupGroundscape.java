package com.davidblackcn;

import com.davidblackcn.registry.GroundscapeBlocks;
import com.davidblackcn.worldgen.GroundscapeWorldgen;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildupGroundscape implements ModInitializer {
	public static final String MOD_ID = "buildup_groundscape";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        com.davidblackcn.config.ConfigNetworking.initialize();
		GroundscapeBlocks.initialize();
        com.davidblackcn.registry.GroundscapeLeaves.initialize();
		GroundscapeWorldgen.initialize();
		LOGGER.info("Registered {} Buildup Groundscape sod blocks", GroundscapeBlocks.allBlocks().size());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

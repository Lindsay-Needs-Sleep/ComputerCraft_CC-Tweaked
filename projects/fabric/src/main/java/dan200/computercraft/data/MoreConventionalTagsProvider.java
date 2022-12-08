/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */
package dan200.computercraft.data;

import dan200.computercraft.shared.platform.MoreConventionalTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

public class MoreConventionalTagsProvider extends FabricTagProvider.ItemTagProvider {
    public MoreConventionalTagsProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> providers) {
        super(output, providers);
    }

    @Override
    protected void addTags(HolderLookup.Provider arg) {
        getOrCreateTagBuilder(MoreConventionalTags.SKULLS).add(
            Items.CREEPER_HEAD, Items.DRAGON_HEAD, Items.PLAYER_HEAD, Items.SKELETON_SKULL, Items.WITHER_SKELETON_SKULL,
            Items.ZOMBIE_HEAD
        );
        getOrCreateTagBuilder(MoreConventionalTags.WOODEN_CHESTS).add(Items.CHEST, Items.TRAPPED_CHEST);
    }
}

package com.imnot.nbteditorgradients.mixin;

import com.luneruniverse.minecraft.mod.nbteditor.commands.get.GetLostItemCommand;
import com.luneruniverse.minecraft.mod.nbteditor.nbtreferences.itemreferences.ClientChestItemReference;
import com.luneruniverse.minecraft.mod.nbteditor.nbtreferences.itemreferences.ContainerItemReference;
import com.luneruniverse.minecraft.mod.nbteditor.nbtreferences.itemreferences.HandItemReference;
import com.luneruniverse.minecraft.mod.nbteditor.nbtreferences.itemreferences.InventoryItemReference;
import com.luneruniverse.minecraft.mod.nbteditor.nbtreferences.itemreferences.ItemReference;
import com.luneruniverse.minecraft.mod.nbteditor.nbtreferences.itemreferences.ServerItemReference;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every way NBT Editor has to write an item back where it took it (a hand, a slot, a container, its client chest,
 * a chest of the server): an item that is edited goes to the history of /get lostitem, as it was and as it becomes.
 */
@Mixin(value = {ClientChestItemReference.class, ContainerItemReference.class, HandItemReference.class, InventoryItemReference.class, ServerItemReference.class}, remap = false)
public class ItemReferenceMixin {
	@Inject(method = "saveItem", at = @At("HEAD"))
	private void nbteditorgradients$history(ItemStack toSave, Runnable onFinished, CallbackInfo ci) {
		// the history leaves out an item it already has on top: a chain of edits gives one entry per state
		GetLostItemCommand.addToHistory(((ItemReference) (Object) this).getItem());
		GetLostItemCommand.addToHistory(toSave);
	}
}

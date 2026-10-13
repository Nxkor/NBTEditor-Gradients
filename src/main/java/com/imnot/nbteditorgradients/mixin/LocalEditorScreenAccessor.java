package com.imnot.nbteditorgradients.mixin;

import com.luneruniverse.minecraft.mod.nbteditor.localnbt.LocalNBT;
import com.luneruniverse.minecraft.mod.nbteditor.screens.LocalEditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = LocalEditorScreen.class, remap = false)
public interface LocalEditorScreenAccessor {
	@Accessor("localNBT")
	LocalNBT getLocalNBT();

	@Accessor("localNBT")
	void setLocalNBT(LocalNBT nbt);

	/** Lights the save button when the item differs from the saved one. */
	@Invoker("checkSave")
	void invokeCheckSave();
}

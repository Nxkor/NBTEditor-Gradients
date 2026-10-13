package com.imnot.nbteditorgradients.mixin;

import com.luneruniverse.minecraft.mod.nbteditor.screens.factories.DisplayScreen;
import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.FormattedTextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DisplayScreen.class, remap = false)
public interface DisplayScreenAccessor {
	@Accessor("nameFormatted")
	FormattedTextFieldWidget getNameFormatted();

	@Accessor("nameFormatted")
	void setNameFormatted(FormattedTextFieldWidget field);

	/** Null when the screen shows an entity: only items have a lore. */
	@Accessor("lore")
	FormattedTextFieldWidget getLore();

	@Accessor("lore")
	void setLore(FormattedTextFieldWidget field);
}

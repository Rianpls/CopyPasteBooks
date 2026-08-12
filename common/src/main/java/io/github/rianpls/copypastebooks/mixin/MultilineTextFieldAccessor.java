package io.github.rianpls.copypastebooks.mixin;

import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The selection's other end. {@code getSelected()} returns the bounds directly, but its
 * StringView type is a protected nested class — unnameable outside the package — so the
 * bounds are min/max of the public {@code cursor()} and this private field instead.
 */
@Mixin(MultilineTextField.class)
public interface MultilineTextFieldAccessor {

    @Accessor("selectCursor")
    int copypastebooks$selectCursor();
}

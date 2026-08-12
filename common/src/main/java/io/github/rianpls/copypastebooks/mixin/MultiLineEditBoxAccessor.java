package io.github.rianpls.copypastebooks.mixin;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Smart paste needs the caret/selection, which live in the box's private text field. */
@Mixin(MultiLineEditBox.class)
public interface MultiLineEditBoxAccessor {

    @Accessor("textField")
    MultilineTextField copypastebooks$textField();
}

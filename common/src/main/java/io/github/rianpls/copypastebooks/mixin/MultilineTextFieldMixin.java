package io.github.rianpls.copypastebooks.mixin;

import io.github.rianpls.copypastebooks.mc.TextFieldEditBridge;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the caret and selection anchor before insert/delete collapses them. */
@Mixin(MultilineTextField.class)
public abstract class MultilineTextFieldMixin implements TextFieldEditBridge {

    @Shadow private int cursor;
    @Shadow private int selectCursor;

    @Unique private boolean cpb$hasPreEditSelection;
    @Unique private int cpb$preEditCaret;
    @Unique private int cpb$preEditAnchor;

    @Inject(method = "insertText", at = @At("HEAD"))
    private void copypastebooks$capturePreEditSelection(String inserted, CallbackInfo ci) {
        cpb$preEditCaret = this.cursor;
        cpb$preEditAnchor = this.selectCursor;
        cpb$hasPreEditSelection = true;
    }

    @Inject(method = "insertText", at = @At("RETURN"))
    private void copypastebooks$clearPreEditSelection(String inserted, CallbackInfo ci) {
        // The value listener is invoked synchronously before insertText returns.
        cpb$hasPreEditSelection = false;
    }

    @Override
    public boolean copypastebooks$hasPreEditSelection() {
        return cpb$hasPreEditSelection;
    }

    @Override
    public int copypastebooks$preEditCaret() {
        return cpb$preEditCaret;
    }

    @Override
    public int copypastebooks$preEditAnchor() {
        return cpb$preEditAnchor;
    }
}

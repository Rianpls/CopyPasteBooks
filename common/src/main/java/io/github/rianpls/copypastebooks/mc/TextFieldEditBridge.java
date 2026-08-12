package io.github.rianpls.copypastebooks.mc;

/**
 * Exposes the selection captured immediately before Minecraft mutates a multiline
 * text field. The value listener runs after the mutation, when the original selection
 * has already collapsed, so the undo journal cannot recover it from public state.
 */
public interface TextFieldEditBridge {

    boolean copypastebooks$hasPreEditSelection();

    int copypastebooks$preEditCaret();

    int copypastebooks$preEditAnchor();
}

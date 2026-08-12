#!/usr/bin/env python3
"""Checks that every @Shadow/@Inject target used by the mixins exists in the given
Minecraft client jars (unobfuscated, 26.1+). Usage:

    python3 tools/audit_mixin_targets.py <client-26.1.x.jar> <client-26.2.jar> ...

Members in TARGETS_ALL must exist in EVERY jar. TARGETS_ANY lists alternatives:
for each group, at least one of the (class, member) pairs must exist per jar
(used for the hotbar renderer that moved from Gui to Hud in 26.2).
Update these tables when mixins change.
"""
import subprocess
import sys

TARGETS_ALL = {
    'net.minecraft.client.gui.screens.inventory.BookEditScreen': [
        'private final java.util.List<java.lang.String> pages;',
        'private final net.minecraft.world.InteractionHand hand;',
        'private int currentPage;',
        'private net.minecraft.client.gui.components.MultiLineEditBox page;',
        'private void updatePageContent();',
        'private void updateLocalCopy();',
        'private void updateButtonVisibility();',
        'private void saveChanges();',
        'protected void init();',
        'public boolean keyPressed(net.minecraft.client.input.KeyEvent);',
    ],
    'net.minecraft.client.gui.components.MultiLineEditBox': [
        'private final net.minecraft.client.gui.components.MultilineTextField textField;',
    ],
    'net.minecraft.client.gui.components.MultilineTextField': [
        'private int cursor;',
        'public int cursor();',
        'private int selectCursor;',
        'public void insertText(java.lang.String);',
        'public void seekCursor(net.minecraft.client.gui.components.Whence, int);',
        'public void setSelecting(boolean);',
        'public java.lang.String value();',
    ],
    'net.minecraft.client.gui.screens.inventory.BookViewScreen': [
        'private net.minecraft.client.gui.screens.inventory.BookViewScreen$BookAccess bookAccess;',
        'protected void init();',
    ],
    'net.minecraft.client.gui.screens.inventory.BookSignScreen': [
        'private final net.minecraft.client.gui.screens.inventory.BookEditScreen bookEditScreen;',
        'private final net.minecraft.world.InteractionHand hand;',
        'private net.minecraft.client.gui.components.EditBox titleBox;',
        'private java.lang.String titleValue;',
        'protected void init();',
        'private void saveChanges();',
    ],
    'net.minecraft.client.gui.screens.Screen': [
        'public void onClose();',
    ],
    'net.minecraft.world.inventory.AbstractContainerMenu': [
        'public void clicked(int, int, net.minecraft.world.inventory.ContainerInput,'
        ' net.minecraft.world.entity.player.Player);',
    ],
    'net.minecraft.world.inventory.Slot': [
        'public int getContainerSlot();',
    ],
    'net.minecraft.client.gui.screens.inventory.AbstractContainerScreen': [
        'protected void extractSlot(net.minecraft.client.gui.GuiGraphicsExtractor,'
        ' net.minecraft.world.inventory.Slot, int, int);',
    ],
}

HOTBAR_SLOT_MEMBER = ('private void extractSlot(net.minecraft.client.gui.GuiGraphicsExtractor,'
                      ' int, int, net.minecraft.client.DeltaTracker,'
                      ' net.minecraft.world.entity.player.Player,'
                      ' net.minecraft.world.item.ItemStack, int);')
TARGETS_ANY = [
    # HotbarSlotMixin: the renderer lives in Gui on 26.1.x and in Hud on 26.2+.
    [('net.minecraft.client.gui.Gui', HOTBAR_SLOT_MEMBER),
     ('net.minecraft.client.gui.Hud', HOTBAR_SLOT_MEMBER)],
]


def members(jar, cls):
    out = subprocess.run(['javap', '-p', '-classpath', jar, cls],
                         capture_output=True, text=True).stdout
    return {line.strip() for line in out.splitlines()}


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    ok = True
    for jar in sys.argv[1:]:
        for cls, wanted in TARGETS_ALL.items():
            have = members(jar, cls)
            for member in wanted:
                if member not in have:
                    ok = False
                    print(f'MISSING in {jar}: {cls} :: {member}')
        for group in TARGETS_ANY:
            if not any(member in members(jar, cls) for cls, member in group):
                ok = False
                print(f'MISSING in {jar}: none of {[c for c, _ in group]} has the hotbar extractSlot')
    print('AUDIT OK' if ok else 'AUDIT FAILED')
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())

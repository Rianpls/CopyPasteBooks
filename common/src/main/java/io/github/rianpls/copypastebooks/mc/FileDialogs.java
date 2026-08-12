package io.github.rianpls.copypastebooks.mc;

import io.github.rianpls.copypastebooks.CopyPasteBooks;
import io.github.rianpls.copypastebooks.core.TinyfdArgs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Native open/save/folder dialogs.
 *
 * On Windows and macOS dialogs run in a separate PowerShell/osascript process. Keeping
 * the native modal window outside the GLFW process avoids message-loop deadlocks when
 * the game regains focus while a dialog is still open. Linux uses tinyfd in-process.
 *
 * If the subprocess can't be launched, we fall back to in-process tinyfd. Every dialog
 * runs on a background thread and delivers its result on the client thread via
 * {@link CPB#later}. Only one dialog may be open at a time.
 */
public final class FileDialogs {

    /** cancelled: user closed the dialog — do nothing, silently. failed: dialog machinery broke. */
    public record DialogResult(Path path, boolean failed) {
        public boolean cancelled() {
            return path == null && !failed;
        }
    }

    @FunctionalInterface
    private interface DialogCall {
        DialogResult call() throws Exception;
    }

    private static final AtomicBoolean BUSY = new AtomicBoolean();
    /** Incrementing invalidates both an active operation and a callback already queued. */
    private static final AtomicLong EPOCH = new AtomicLong();
    private static final ThreadLocal<Long> RUN_EPOCH = new ThreadLocal<>();
    /** The dialog subprocess currently on screen, if any (Windows/macOS path). */
    private static volatile Process activeDialog;

    static {
        // A dialog subprocess can outlive the game unless it is closed explicitly.
        Runtime.getRuntime().addShutdownHook(new Thread(FileDialogs::abortActive, "CPB-dialog-abort"));
    }

    private FileDialogs() {
    }

    /**
     * Closes the currently open dialog window, if any. Called on world/server disconnect
     * and on game shutdown; the interrupted dialog resolves as a plain cancel, so nothing
     * is applied and previous values stay.
     */
    public static void abortActive() {
        EPOCH.incrementAndGet();
        Process process = activeDialog;
        if (process != null) {
            process.destroy();
        }
    }

    // ------------------------------------------------------------------ public API

    public static void openTxt(String title, Consumer<DialogResult> callback) {
        run(() -> {
            if (isWindows()) {
                return firstOk(() -> powershell(winOpenScript(), title, null, null), () -> tinyfdOpen(title));
            }
            if (isMac()) {
                return firstOk(() -> mac("choose file of type {\"txt\", \"public.plain-text\"} with prompt "
                        + as(title)), () -> tinyfdOpen(title));
            }
            return tinyfdOpen(title);
        }, callback);
    }

    public static void saveTxt(String title, String suggestedName, Consumer<DialogResult> callback) {
        run(() -> ensureTxt(
                isWindows() ? firstOk(() -> powershell(winSaveScript(), title, suggestedName, null),
                        () -> tinyfdSave(title, suggestedName))
                : isMac() ? firstOk(() -> mac("choose file name with prompt " + as(title)
                        + " default name " + as(suggestedName)), () -> tinyfdSave(title, suggestedName))
                : tinyfdSave(title, suggestedName)), callback);
    }

    public static void selectFolder(String title, String currentPath, Consumer<DialogResult> callback) {
        run(() -> {
            if (isWindows()) {
                return firstOk(() -> powershell(winFolderScript(), title, null, currentPath),
                        () -> tinyfdFolder(title, startDir(currentPath)));
            }
            if (isMac()) {
                return firstOk(() -> mac("choose folder with prompt " + as(title)),
                        () -> tinyfdFolder(title, startDir(currentPath)));
            }
            return tinyfdFolder(title, startDir(currentPath));
        }, callback);
    }

    // ------------------------------------------------------------------ threading

    private static void run(DialogCall operation, Consumer<DialogResult> callback) {
        if (!BUSY.compareAndSet(false, true)) {
            Feedback.error("err.dialog.busy");
            return;
        }
        long epoch = EPOCH.get();
        Thread thread = new Thread(() -> {
            if (epoch != EPOCH.get()) {
                BUSY.set(false);
                return;
            }
            RUN_EPOCH.set(epoch);
            DialogResult result;
            try {
                result = operation.call();
            } catch (Throwable t) {
                CopyPasteBooks.LOGGER.error("File dialog failed", t);
                result = new DialogResult(null, true);
            } finally {
                RUN_EPOCH.remove();
            }
            BUSY.set(false);
            if (epoch != EPOCH.get()) {
                return; // disconnect/shutdown: never deliver stale UI work
            }
            DialogResult delivered = result;
            CPB.later(() -> {
                if (epoch == EPOCH.get()) {
                    callback.accept(delivered);
                }
            });
        }, "CopyPasteBooks-FileDialog");
        thread.setDaemon(true);
        thread.start();
    }

    /** Runs {@code primary}; if it throws (e.g. the subprocess couldn't launch), runs {@code fallback}. */
    private static DialogResult firstOk(DialogCall primary, DialogCall fallback) throws Exception {
        try {
            return primary.call();
        } catch (Exception e) {
            CopyPasteBooks.LOGGER.warn("Native dialog subprocess failed, falling back to in-process dialog", e);
            return fallback.call();
        }
    }

    // ------------------------------------------------------------------ subprocess plumbing

    private static DialogResult subprocess(List<String> command) throws Exception {
        return subprocess(new ProcessBuilder(command));
    }

    private static DialogResult subprocess(ProcessBuilder builder) throws Exception {
        Process process = builder.redirectError(ProcessBuilder.Redirect.DISCARD).start();
        activeDialog = process;
        Long epoch = RUN_EPOCH.get();
        if (epoch != null && epoch != EPOCH.get()) {
            process.destroy();
        }
        byte[] bytes;
        int exitCode;
        try {
            try (var in = process.getInputStream()) {
                bytes = in.readAllBytes();
            }
            exitCode = process.waitFor();
        } catch (Exception e) {
            if (epoch != null && epoch != EPOCH.get()) {
                return new DialogResult(null, false);
            }
            throw e;
        } finally {
            if (activeDialog == process) {
                activeDialog = null;
            }
        }
        if (epoch != null && epoch != EPOCH.get()) {
            return new DialogResult(null, false);
        }
        if (exitCode != 0) {
            throw new IOException("Native dialog exited with code " + exitCode);
        }
        String out = new String(bytes, StandardCharsets.UTF_8);
        if (out.startsWith("\uFEFF")) {
            out = out.substring(1);
        }
        out = out.trim();
        return out.isEmpty() ? new DialogResult(null, false) : new DialogResult(Path.of(out), false);
    }

    private static List<String> psCommand(String script) {
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell", "-NoProfile", "-STA", "-WindowStyle", "Hidden", "-EncodedCommand", encoded);
    }

    private static DialogResult powershell(String script, String title, String name, String current) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(psCommand(script));
        builder.environment().put("CPB_DIALOG_TITLE", valueOrEmpty(title));
        builder.environment().put("CPB_DIALOG_NAME", valueOrEmpty(name));
        builder.environment().put("CPB_DIALOG_CURRENT", valueOrEmpty(current));
        return subprocess(builder);
    }

    private static DialogResult mac(String chooseExpr) throws Exception {
        // Turn only AppleScript's user-cancel error (-128) into an empty successful
        // result. Any script/runtime failure stays non-zero and triggers the fallback.
        String script = "try\nPOSIX path of (" + chooseExpr
                + ")\non error number -128\n\"\"\nend try";
        return subprocess(List.of("osascript", "-e", script));
    }

    // ------------------------------------------------------------------ script builders

    /** Hidden, top-most owner form so the dialog reliably appears in front of the game. */
    private static final String PS_HEAD =
            "$ErrorActionPreference='Stop';"
            + "[Console]::OutputEncoding=New-Object System.Text.UTF8Encoding($false);"
            + "Add-Type -AssemblyName System.Windows.Forms;"
            + "$o=New-Object System.Windows.Forms.Form -Property @{TopMost=$true;ShowInTaskbar=$false;Opacity=0};"
            + "[void]$o.Show();";

    private static String winOpenScript() {
        return PS_HEAD
                + "$d=New-Object System.Windows.Forms.OpenFileDialog;"
                + "$d.Title=$env:CPB_DIALOG_TITLE;"
                + "$d.Filter='Text files (*.txt)|*.txt|All files (*.*)|*.*';"
                + "$r=$d.ShowDialog($o);$o.Dispose();"
                + "if($r -eq 'OK'){[Console]::Out.Write($d.FileName)}";
    }

    private static String winSaveScript() {
        return PS_HEAD
                + "$d=New-Object System.Windows.Forms.SaveFileDialog;"
                + "$d.Title=$env:CPB_DIALOG_TITLE;"
                + "$d.Filter='Text files (*.txt)|*.txt|All files (*.*)|*.*';"
                + "$d.FileName=$env:CPB_DIALOG_NAME;$d.AddExtension=$true;$d.DefaultExt='txt';"
                + "$r=$d.ShowDialog($o);$o.Dispose();"
                + "if($r -eq 'OK'){[Console]::Out.Write($d.FileName)}";
    }

    private static String winFolderScript() {
        return PS_HEAD
                + "$d=New-Object System.Windows.Forms.FolderBrowserDialog;"
                + "$d.Description=$env:CPB_DIALOG_TITLE;$d.ShowNewFolderButton=$true;"
                + "if(-not [string]::IsNullOrWhiteSpace($env:CPB_DIALOG_CURRENT)){"
                + "try{$d.SelectedPath=$env:CPB_DIALOG_CURRENT}catch{}};"
                + "$r=$d.ShowDialog($o);$o.Dispose();"
                + "if($r -eq 'OK'){[Console]::Out.Write($d.SelectedPath)}";
    }

    /** AppleScript double-quoted string literal. */
    private static String as(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ------------------------------------------------------------------ Linux (in-process tinyfd)

    private static DialogResult tinyfdOpen(String title) {
        boolean shellBacked = !isWindows();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer patterns = stack.mallocPointer(1);
            patterns.put(stack.UTF8("*.txt"));
            patterns.flip();
            String picked = TinyFileDialogs.tinyfd_openFileDialog(TinyfdArgs.display(title, shellBacked),
                    (CharSequence) null, patterns, "Text files (*.txt)", false);
            return tinyfdResult(picked);
        }
    }

    private static DialogResult tinyfdSave(String title, String suggestedName) {
        boolean shellBacked = !isWindows();
        String shownName = TinyfdArgs.pathDisplay(suggestedName, shellBacked);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer patterns = stack.mallocPointer(1);
            patterns.put(stack.UTF8("*.txt"));
            patterns.flip();
            String picked = TinyFileDialogs.tinyfd_saveFileDialog(TinyfdArgs.display(title, shellBacked),
                    shownName, patterns, "Text files (*.txt)");
            DialogResult result = tinyfdResult(picked);
            return result.path() == null ? result : new DialogResult(
                    TinyfdArgs.restoreSuggestedName(result.path(), shownName, suggestedName), false);
        }
    }

    private static DialogResult tinyfdFolder(String title, String start) {
        boolean shellBacked = !isWindows();
        String picked = TinyFileDialogs.tinyfd_selectFolderDialog(TinyfdArgs.display(title, shellBacked),
                TinyfdArgs.startPath(start, shellBacked));
        return tinyfdResult(picked);
    }

    // ------------------------------------------------------------------ helpers

    private static DialogResult ensureTxt(DialogResult result) {
        if (result.path() == null) {
            return result;
        }
        String s = result.path().toString();
        return s.toLowerCase(Locale.ROOT).endsWith(".txt") ? result : new DialogResult(Path.of(s + ".txt"), false);
    }

    private static String startDir(String currentPath) {
        return currentPath == null || currentPath.isBlank()
                ? System.getProperty("user.home", "")
                : currentPath;
    }

    private static DialogResult tinyfdResult(String picked) {
        if (picked == null) {
            return new DialogResult(null, false);
        }
        Path path = TinyfdArgs.pathOrNull(picked);
        if (path == null) {
            CopyPasteBooks.LOGGER.warn("Native file dialog returned an unsupported path");
            return new DialogResult(null, true);
        }
        return new DialogResult(path, false);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac") || os.contains("darwin");
    }
}

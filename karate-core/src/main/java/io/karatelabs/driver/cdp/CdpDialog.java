/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.driver.cdp;

import io.karatelabs.driver.Dialog;
import io.karatelabs.driver.DriverException;

/**
 * CDP-based implementation of Dialog.
 * Represents a JavaScript dialog (alert, confirm, prompt, or beforeunload).
 * <p>
 * Dialog types:
 * <ul>
 *   <li>{@code "alert"} - Simple message dialog with OK button</li>
 *   <li>{@code "confirm"} - Dialog with OK and Cancel buttons</li>
 *   <li>{@code "prompt"} - Dialog with text input, OK and Cancel buttons</li>
 *   <li>{@code "beforeunload"} - Dialog shown when leaving page</li>
 * </ul>
 */
public class CdpDialog implements Dialog {

    private final CdpClient cdp;
    private final String message;
    private final String type;
    private final String defaultPrompt;
    private volatile boolean handled = false;

    CdpDialog(CdpClient cdp, String message, String type, String defaultPrompt) {
        this.cdp = cdp;
        this.message = message;
        this.type = type;
        this.defaultPrompt = defaultPrompt;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getDefaultPrompt() {
        return defaultPrompt;
    }

    @Override
    public void accept() {
        accept(null);
    }

    @Override
    public void accept(String promptText) {
        if (handled) {
            // Already handled - silently return to avoid race condition errors
            // This can happen when multiple dialog events fire or auto-dismiss races with handler
            return;
        }
        handled = true;
        CdpMessage message = cdp.method("Page.handleJavaScriptDialog")
                .param("accept", true);
        if (promptText != null) {
            message.param("promptText", promptText);
        }
        CdpResponse response = message.send();
        // If CDP call failed (e.g., dialog already gone), still consider it handled
        // to prevent retry attempts that will also fail
        if (response.isError()) {
            // Log but don't throw - the dialog is effectively handled (gone)
            throw new DriverException("dialog accept failed: " + response.getError());
        }
    }

    @Override
    public void dismiss() {
        if (handled) {
            // Already handled - silently return to avoid race condition errors
            return;
        }
        handled = true;
        CdpResponse response = cdp.method("Page.handleJavaScriptDialog")
                .param("accept", false)
                .send();
        if (response.isError()) {
            throw new DriverException("dialog dismiss failed: " + response.getError());
        }
    }

    @Override
    public boolean isHandled() {
        return handled;
    }

    @Override
    public String toString() {
        return "Dialog{type='" + type + "', message='" + message + "'}";
    }

}

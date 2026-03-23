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
package io.karatelabs.driver;

/**
 * Interface for JavaScript dialogs (alert, confirm, prompt, or beforeunload).
 * <p>
 * Dialog types:
 * <ul>
 *   <li>{@code "alert"} - Simple message dialog with OK button</li>
 *   <li>{@code "confirm"} - Dialog with OK and Cancel buttons</li>
 *   <li>{@code "prompt"} - Dialog with text input, OK and Cancel buttons</li>
 *   <li>{@code "beforeunload"} - Dialog shown when leaving page</li>
 * </ul>
 * <p>
 * Phase 8: Extracted as interface to enable multi-backend support.
 */
public interface Dialog {

    /**
     * Get the dialog message.
     *
     * @return the message displayed in the dialog
     */
    String getMessage();

    /**
     * Get the dialog type.
     *
     * @return one of: "alert", "confirm", "prompt", "beforeunload"
     */
    String getType();

    /**
     * Get the default prompt value (for prompt dialogs).
     *
     * @return the default value for prompt dialogs, or null for other types
     */
    String getDefaultPrompt();

    /**
     * Accept the dialog (click OK/Yes).
     * For prompt dialogs, this accepts with an empty string.
     */
    void accept();

    /**
     * Accept the dialog with input text (for prompt dialogs).
     *
     * @param promptText the text to enter in the prompt (ignored for non-prompt dialogs)
     */
    void accept(String promptText);

    /**
     * Dismiss the dialog (click Cancel/No).
     */
    void dismiss();

    /**
     * Check if this dialog has been handled.
     *
     * @return true if accept() or dismiss() has been called
     */
    boolean isHandled();

}

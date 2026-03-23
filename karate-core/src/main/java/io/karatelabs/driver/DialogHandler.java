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
 * Functional interface for handling JavaScript dialogs (alert, confirm, prompt, beforeunload).
 * <p>
 * Usage:
 * <pre>
 * driver.onDialog(dialog -> {
 *     if (dialog.getType().equals("confirm")) {
 *         dialog.accept();
 *     } else if (dialog.getType().equals("prompt")) {
 *         dialog.accept("user input");
 *     } else {
 *         dialog.dismiss();
 *     }
 * });
 * </pre>
 */
@FunctionalInterface
public interface DialogHandler {

    /**
     * Handle a JavaScript dialog.
     * The handler must call either {@link Dialog#accept()}, {@link Dialog#accept(String)},
     * or {@link Dialog#dismiss()} to resolve the dialog.
     *
     * @param dialog the dialog to handle
     */
    void handle(Dialog dialog);

}

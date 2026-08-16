/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.js;

import io.karatelabs.js.GeneratorActivation.OutcomeKind;
import io.karatelabs.js.GeneratorActivation.ResumeKind;
import io.karatelabs.js.GeneratorActivation.StepOutcome;

/**
 * A generator object — the result of calling a {@code function*}. Implements
 * the iterator protocol ({@code next} / {@code return} / {@code throw} +
 * {@code @@iterator → this} on the shared prototype), driving the body
 * coroutine in {@link GeneratorActivation}. Spec §27.5.1 edge-state semantics
 * (steps on NOT_STARTED / DONE generators) live here; the running/suspended
 * handoff lives in the activation.
 */
class JsGenerator extends JsObject {

    final GeneratorActivation activation;

    JsGenerator(Engine engine, JsFunctionNode function, CoreContext functionContext, Object[] args) {
        super(null, JsGeneratorPrototype.INSTANCE);
        this.activation = new GeneratorActivation(engine, function, functionContext, args);
    }

    static JsObject result(Object value, boolean done) {
        JsObject r = new JsObject();
        r.putMember("value", value);
        r.putMember("done", done);
        return r;
    }

    Object next(CoreContext ctx, Object value) {
        if (activation.isDone()) {
            return result(Terms.UNDEFINED, true);
        }
        return drive(ctx, ResumeKind.NEXT, value);
    }

    Object returnValue(CoreContext ctx, Object value) {
        if (activation.isDone() || !activation.isStarted()) {
            if (activation.isRunning()) {
                throw JsErrorException.typeError("Generator is already running");
            }
            activation.retire(); // NOT_STARTED: no body code runs
            return result(value, true);
        }
        return drive(ctx, ResumeKind.RETURN, value);
    }

    Object throwValue(CoreContext ctx, Object value) {
        if (activation.isDone() || !activation.isStarted()) {
            if (activation.isRunning()) {
                throw JsErrorException.typeError("Generator is already running");
            }
            activation.retire(); // NOT_STARTED: no body code runs
            ctx.stopAndThrow(value);
            return Terms.UNDEFINED;
        }
        return drive(ctx, ResumeKind.THROW, value);
    }

    private Object drive(CoreContext ctx, ResumeKind kind, Object value) {
        StepOutcome outcome = activation.step(kind, value);
        return switch (outcome.kind) {
            case YIELDED -> result(outcome.value, false);
            case RETURNED -> result(outcome.value, true);
            case THREW -> {
                ctx.stopAndThrow(outcome.value);
                yield Terms.UNDEFINED;
            }
            case HOST_CANCELLED -> throw new EngineInterruptedException();
        };
    }

}

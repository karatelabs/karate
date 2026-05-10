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
package io.karatelabs.markup;

import io.karatelabs.js.Engine;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.context.IEngineContext;
import org.thymeleaf.context.IdentifierSequences;
import org.thymeleaf.engine.IterationStatusVar;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.expression.IExpressionObjects;
import org.thymeleaf.inline.IInliner;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.*;

public class MarkupTemplateContext implements IEngineContext, MarkupScope {

    final IEngineContext wrapped;
    private final Engine engine;
    private final Map<String, Object> vars = new HashMap<>();

    // `_` is exposed as a dual-lookup ObjectLike: writes go to `vars`
    // (the per-eval underscore map, unchanged); reads check `vars` first
    // and fall through to the wrapped Thymeleaf scope when a name isn't
    // present. This lets a fragment write `_.spacing` and read it back, OR
    // read `_.spacing` when a parent template's `th:with` bound the name.
    // Distinct from `lookup()` (used by context.get): getMember preserves
    // an explicit `_.foo = null`, while lookup treats null as "use default".
    private final io.karatelabs.js.ObjectLike underscoreView = new io.karatelabs.js.ObjectLike() {
        @Override
        public Object getMember(String name) {
            if (vars.containsKey(name)) {
                return vars.get(name);
            }
            if (wrapped.containsVariable(name)) {
                return wrapped.getVariable(name);
            }
            return null;
        }

        @Override
        public void putMember(String name, Object value) {
            vars.put(name, value);
        }

        @Override
        public void removeMember(String name) {
            vars.remove(name);
        }

        @Override
        public Map<String, Object> toMap() {
            // Enumeration (Object.keys(_)) intentionally returns ONLY the
            // underscore namespace — names written via `_.foo = ...`. The
            // wrapped Thymeleaf scope is reachable through read fall-through
            // for convenience but is not part of the underscore namespace.
            return new java.util.LinkedHashMap<>(vars);
        }

        @Override
        public boolean isOwnProperty(String name) {
            // Mirrors getMember's read fall-through so `'foo' in _` agrees
            // with `_.foo`: both report present when the name is bound either
            // in the underscore vars or in the wrapped Thymeleaf scope.
            // Object.keys(_) stays scoped to the underscore namespace only.
            return vars.containsKey(name) || wrapped.containsVariable(name);
        }
    };

    MarkupTemplateContext(IEngineContext wrapped, MarkupConfig config) {
        this.wrapped = wrapped;
        this.engine = config.getEngineSupplier().get();
        this.engine.put("_", underscoreView);
        // Use existing MarkupContext from template variables if present (e.g., ServerMarkupContext in server mode)
        // Otherwise create a SimpleMarkupContext for plain templating mode
        // In server mode the engine is shared with ServerRequestCycle (via ThreadLocal supplier),
        // and `session` is bound there as a Supplier — so reads always see the live value.
        MarkupContext markupContext;
        Object existingContext = wrapped.getVariable("context");
        if (existingContext instanceof MarkupContext mc) {
            markupContext = mc;
        } else {
            markupContext = new SimpleMarkupContext(this, config.getResolver());
        }
        this.engine.put("context", markupContext);
        // Inject self as the MarkupScope so `context.get(name, default?)`
        // can resolve names against the live `_` + Thymeleaf scope of this eval.
        markupContext.setMarkupScope(this);
        // install eager-dispatch hook on the actions registry. The host's
        // context.actions view fires this on every put; if the put just
        // registered the matching handler for the inbound POST, dispatch it
        // immediately so any state reads later in the same script see
        // post-mutation data. Plain templating contexts implement a default
        // no-op hook, so this is a silent no-op outside server mode.
        if (markupContext instanceof ActionDispatchHost host) {
            host.setEagerDispatchHook(name -> maybeDispatchAction());
        }
    }

    /**
     * {@link MarkupScope} implementation. Looks up {@code name} in the
     * underscore map first, then the wrapped Thymeleaf scope. Returns the
     * bound non-null value if found, else null. Used by
     * {@code context.get(name, default?)} (see {@link MarkupContext#jsGet}).
     */
    @Override
    public Object lookup(String name) {
        if (vars.containsKey(name)) {
            Object v = vars.get(name);
            if (v != null) return v;
        }
        if (wrapped.containsVariable(name)) {
            Object v = wrapped.getVariable(name);
            if (v != null) return v;
        }
        return null;
    }

    void evalGlobal(String src) {
        getVariableNames().forEach(name -> engine.put(name, getVariable(name)));
        engine.eval(src);
    }

    /**
     * After a {@code ka:scope="global"} block has run, check if the
     * inbound POST has a matching handler in {@code context.actions} and
     * dispatch it. The handler runs at most once per request even with
     * multiple global blocks. Plain (non-server) contexts are a silent
     * no-op since they don't implement {@link ActionDispatchHost}.
     *
     * <p>The dispatch JS pre-decodes {@code request.paramJson('form')}
     * (the karate-markup form-payload convention) and passes it as the
     * single positional argument. Handlers that don't need {@code form}
     * just don't declare a parameter; handlers that need different params
     * read them off the {@code request} global directly inside the body.
     *
     * <p>Uncaught exceptions from the handler are translated to
     * {@code context.flash.error} so a thrown handler degrades the same
     * way a hand-written {@code switch}/{@code try-catch} does today.
     */
    void maybeDispatchAction() {
        Object ctxObj = wrapped.getVariable("context");
        if (!(ctxObj instanceof ActionDispatchHost host)) return;
        if (host.isActionDispatched()) return;
        if (host.getActions() == null || host.getActions().isEmpty()) return;
        Object dispatched = engine.eval(DISPATCH_JS);
        if (Boolean.TRUE.equals(dispatched)) {
            host.markActionDispatched();
        }
    }

    private static final String DISPATCH_JS =
            "(function() {"
            + "  if (typeof request === 'undefined' || !request.post) return false;"
            + "  var name = request.param('action');"
            + "  if (!name) return false;"
            + "  var fn = context.actions[name];"
            + "  if (typeof fn !== 'function') return false;"
            + "  try { fn(request.paramJson('form')); }"
            + "  catch (e) { context.flash.error = (e && e.message) || (e + ''); }"
            + "  return true;"
            + "})()";

    public Object evalLocalAsObject(String src) {
        String temp;
        if (src.startsWith("${")) {
            temp = "`" + src + "`";
        } else {
            temp = "({" + src + "})";
        }
        try {
            return evalLocal(temp);
        } catch (io.karatelabs.parser.ParserException pe) {
            // th:attr / ka:with / hx-vals / ka:dispatch values whose keys
            // contain hyphens or colons (e.g. `data-foo: 'bar'`,
            // `hx-target: t`, `ka:get: url`) confuse the JS object-literal
            // parser. Augment the bare parser failure with a hint that names
            // the offending keys and shows the corrected (quoted) form.
            String hint = buildAttrKeyHint(src);
            if (hint != null) {
                throw new RuntimeException(hint, pe);
            }
            throw pe;
        }
    }

    // Detects unquoted attribute-style keys (containing `-` or `:`) at the
    // start of an object-literal pair. Anchored at start-of-string or after a
    // comma so it ignores already-quoted keys (`'data-foo':`) and identifiers
    // inside expression values (`bar - baz`, `obj.x`).
    private static final java.util.regex.Pattern UNQUOTED_HYPHEN_COLON_KEY =
            java.util.regex.Pattern.compile(
                    "(^|,)\\s*([a-zA-Z_$][\\w$]*(?:[-:][\\w$]+)+)\\s*:");

    private static String buildAttrKeyHint(String src) {
        java.util.regex.Matcher m = UNQUOTED_HYPHEN_COLON_KEY.matcher(src);
        java.util.LinkedHashSet<String> badKeys = new java.util.LinkedHashSet<>();
        while (m.find()) {
            badKeys.add(m.group(2));
        }
        if (badKeys.isEmpty()) {
            return null;
        }
        String corrected = src;
        for (String key : badKeys) {
            corrected = corrected.replaceAll(
                    "(^|,)(\\s*)" + java.util.regex.Pattern.quote(key) + "(\\s*):",
                    "$1$2'" + java.util.regex.Matcher.quoteReplacement(key) + "'$3:");
        }
        StringBuilder hint = new StringBuilder();
        hint.append("SyntaxError parsing object-literal expression — attribute key");
        hint.append(badKeys.size() > 1 ? "s " : " ");
        boolean first = true;
        for (String k : badKeys) {
            if (!first) hint.append(", ");
            hint.append("`").append(k).append("`");
            first = false;
        }
        hint.append(badKeys.size() > 1 ? " contain" : " contains");
        hint.append(" hyphens or colons and must be quoted. Try: ").append(corrected);
        return hint.toString();
    }

    public Object evalLocal(String src) {
        Map<String, Object> localVars = new HashMap<>();
        for (String name : getVariableNames()) {
            localVars.put(name, getVariable(name));
        }
        // Bind `_` as the dual-lookup ObjectLike (not the raw vars Map)
        // so template-attribute reads of `_.<name>` fall through to the
        // wrapped Thymeleaf scope when the underscore map is empty.
        localVars.put("_", underscoreView);
        // Strict ReferenceError on missing names — augment with an actionable
        // hint pointing at either the `_.<name>` discipline or the
        // th:with-at-call-site / context.get(...) optional-param pattern.
        try {
            return engine.evalWith(src, localVars);
        } catch (io.karatelabs.js.EngineException e) {
            String missing = extractMissingName(e);
            if (missing == null) {
                throw e;
            }
            throw new RuntimeException(buildMissingNameHint(missing, e), e);
        }
    }

    private String buildMissingNameHint(String missing, Throwable cause) {
        String base = "ReferenceError: '" + missing + "' is not defined";
        if (vars.containsKey(missing)) {
            return base + " — did you mean `_." + missing + "`? "
                    + "ka:scope blocks namespace template state via the `_` map; "
                    + "bare names must come from a th:with or a parent template binding.";
        }
        return base + " — if `" + missing + "` is a fragment parameter, "
                + "declare it via th:with at the call site "
                + "(e.g. <div th:insert=\"~{file::frag}\" th:with=\"" + missing + ": value\">). "
                + "If it's optional, read it inside the fragment via "
                + "context.get('" + missing + "') or context.get('" + missing + "', defaultValue) — "
                + "returns null (or the default) when the name is unbound.";
    }

    // The unframed JS-side message for a ReferenceError is `<name> is not defined`
    // (per spec). We only run this regex when getJsErrorName() == "ReferenceError",
    // and we read getJsMessage() rather than getMessage() — no host-side framing
    // to skip past, just the bare JS message.
    private static final java.util.regex.Pattern NOT_DEFINED_PATTERN =
            java.util.regex.Pattern.compile("([A-Za-z_$][\\w$]*) is not defined");

    private static String extractMissingName(io.karatelabs.js.EngineException e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof io.karatelabs.js.EngineException ee
                    && "ReferenceError".equals(ee.getJsErrorName())) {
                String jsMsg = ee.getJsMessage();
                if (jsMsg != null) {
                    java.util.regex.Matcher m = NOT_DEFINED_PATTERN.matcher(jsMsg);
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            }
            current = current.getCause();
        }
        return null;
    }

    void setLocal(String name, Object value) {
        vars.put(name, value);
    }

    @Override
    public void increaseLevel() {
        if (!vars.isEmpty()) {
            wrapped.setVariables(vars);
            vars.clear();
        }
        wrapped.increaseLevel();
    }

    @Override
    public void setVariable(String name, Object value) {
        wrapped.setVariable(name, value);
    }

    @Override
    public void setVariables(Map<String, Object> variables) {
        wrapped.setVariables(variables);
    }

    @Override
    public void removeVariable(String name) {
        wrapped.removeVariable(name);
    }

    @Override
    public void setTemplateData(TemplateData template) {
        wrapped.setTemplateData(template);
    }

    @Override
    public void decreaseLevel() {
        wrapped.decreaseLevel();
    }

    @Override
    public boolean containsVariable(String name) {
        return wrapped.containsVariable(name);
    }

    @Override
    public Set<String> getVariableNames() {
        return wrapped.getVariableNames();
    }

    @Override
    public Object getVariable(String name) {
        Object value = wrapped.getVariable(name);
        // Convert Thymeleaf's IterationStatusVar to a JS-friendly Map
        // This enables iteration status properties like iter.first, iter.last, iter.index
        if (value instanceof IterationStatusVar status) {
            Map<String, Object> statusMap = new LinkedHashMap<>();
            statusMap.put("index", status.getIndex());
            statusMap.put("count", status.getCount());
            statusMap.put("size", status.getSize());
            statusMap.put("current", status.getCurrent());
            statusMap.put("even", status.isEven());
            statusMap.put("odd", status.isOdd());
            statusMap.put("first", status.isFirst());
            statusMap.put("last", status.isLast());
            return statusMap;
        }
        return value;
    }

    @Override
    public boolean isVariableLocal(String name) {
        return wrapped.isVariableLocal(name);
    }

    @Override
    public void setSelectionTarget(Object selectionTarget) {
        wrapped.setSelectionTarget(selectionTarget);
    }

    @Override
    public void setInliner(IInliner inliner) {
        wrapped.setInliner(inliner);
    }

    @Override
    public void setElementTag(IProcessableElementTag elementTag) {
        wrapped.setElementTag(elementTag);
    }

    @Override
    public List<IProcessableElementTag> getElementStackAbove(int contextLevel) {
        return wrapped.getElementStackAbove(contextLevel);
    }

    @Override
    public int level() {
        return wrapped.level();
    }

    @Override
    public TemplateData getTemplateData() {
        return wrapped.getTemplateData();
    }

    @Override
    public TemplateMode getTemplateMode() {
        return wrapped.getTemplateMode();
    }

    @Override
    public List<TemplateData> getTemplateStack() {
        return wrapped.getTemplateStack();
    }

    @Override
    public List<IProcessableElementTag> getElementStack() {
        return wrapped.getElementStack();
    }

    @Override
    public Map<String, Object> getTemplateResolutionAttributes() {
        return wrapped.getTemplateResolutionAttributes();
    }

    @Override
    public IModelFactory getModelFactory() {
        return wrapped.getModelFactory();
    }

    @Override
    public boolean hasSelectionTarget() {
        return wrapped.hasSelectionTarget();
    }

    @Override
    public Object getSelectionTarget() {
        return wrapped.getSelectionTarget();
    }

    @Override
    public IInliner getInliner() {
        return wrapped.getInliner();
    }

    @Override
    public String getMessage(Class<?> origin, String key, Object[] messageParameters, boolean useAbsent) {
        return wrapped.getMessage(origin, key, messageParameters, useAbsent);
    }

    @Override
    public String buildLink(String base, Map<String, Object> parameters) {
        return wrapped.buildLink(base, parameters);
    }

    @Override
    public IdentifierSequences getIdentifierSequences() {
        return wrapped.getIdentifierSequences();
    }

    @Override
    public IEngineConfiguration getConfiguration() {
        return wrapped.getConfiguration();
    }

    @Override
    public IExpressionObjects getExpressionObjects() {
        return wrapped.getExpressionObjects();
    }

    @Override
    public Locale getLocale() {
        return wrapped.getLocale();
    }

}

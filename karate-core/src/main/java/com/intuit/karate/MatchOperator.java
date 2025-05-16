package com.intuit.karate;

public interface MatchOperator {

    class EachOperator implements MatchOperator {
        final MatchOperator delegate;

        EachOperator(MatchOperator delegate) {
            this.delegate = delegate;
        }

        public String toString() {
            return "EACH_"+delegate;
        }
    }

    class NotOperator implements MatchOperator {
        final CoreOperator delegate;
        final String failureMessage;

        NotOperator(CoreOperator delegate, String failureMessage) {
            this.delegate = delegate;
            this.failureMessage = failureMessage;
        }

        public String toString() {
            return "NOT_"+delegate;
        }
    }

    class CoreOperator implements MatchOperator {

        static final CoreOperator EQUALS = new CoreOperator(true, false, false, false, false);
        static final CoreOperator CONTAINS = new CoreOperator(false, true, false, false, false);
        static final CoreOperator CONTAINS_ANY = new CoreOperator(false, false, true, false, false);
        static final CoreOperator CONTAINS_ONLY = new CoreOperator(false, false, false, true, false);

        private final boolean isEquals;
        private final boolean isContains;
        private final boolean isContainsAny;
        private final boolean isContainsOnly;
        private final boolean isDeep;

        private CoreOperator(boolean isEquals, boolean isContains, boolean isContainsAny, boolean isContainsOnly) {
            this(isEquals, isContains, isContainsAny, isContainsOnly, false);
        }

        private CoreOperator(boolean isEquals, boolean isContains, boolean isContainsAny, boolean isContainsOnly, boolean isDeep) {
            this.isEquals = isEquals;
            this.isContains = isContains;
            this.isContainsAny = isContainsAny;
            this.isContainsOnly = isContainsOnly;
            this.isDeep = isDeep;
        }

        CoreOperator deep() {
            return new CoreOperator(isEquals, isContains, isContainsAny, isContainsOnly, true);
        }

        boolean isEquals() {
            return isEquals;
        }

        boolean isContains() {
            return isContains;
        }

        boolean isContainsAny() {
            return isContainsAny;
        }

        boolean isContainsOnly() {
            return isContainsOnly;
        }

        boolean isContainsFamily() {
            return isContains() || isContainsOnly() || isContainsAny();
        }

        MatchOperator childOperator(Match.Value value) {
            // TODO why force equals here?
            // match [['foo'], ['bar']] contains deep 'fo'
            // will fail if leaves are matched with equals, but should it not pass?
            return isDeep && value.isMapOrListOrXml()?this:EQUALS;
        }

        /**
         * Hook to adjust the operator used for macro.
         * <p>
         * Whatever operator the user specified (^, ^+, ...) will be supplied as the specifiedOperator parameter.
         * However, the Contains operator may need to tweak it a little bit.
         * <p>
         * Given
         * * def actual = [{ a: 1, b: 'x' }, { a: 2, b: 'y' }]
         * * def part = { a: 1 }
         * * match actual contains '#(^part)'
         * <p>
         * specifiedOperator will be Contains. However, in this example:
         * - the specified operator will be applied while processing the list
         * - child operators will be applied while processing the objects within the list.
         * And per {@link #childOperator(Match.Value)}, Contains' child operators are Equals, so the code would end up
         * trying to match { a: 1, b: 'x' } equals { a: 1 }, which would fail.
         * <p>
         * What we really want here is to keep both Contains, the one from the match instruction and the one from the macro.
         * This method does just that by creating a custom Operator that will apply 2 contains.
         * <p>
         * Note that should a third processing be needed (e.g. because the objects in actual contain other objects),
         * it would use the child operator of the child operator, which would be Equals.
         * This behavior differs from the Legacy implementation that would force a Deep Contains which would in turn cause issue #2515.
         * <p>
         * However, Contains Deep may still be specified at user's discretion e.g. to handle objects in objects in lists.
         */
        protected MatchOperator macroOperator(MatchOperator specifiedOperator) {
            if (isContainsFamily()) {
                return isDeep ? this : new CoreOperator(false, isContains(), isContainsAny(), isContainsOnly()) {
                    protected MatchOperator childOperator(Match.Value actual) {
                        return specifiedOperator;
                    }
                };
            }
            return specifiedOperator;
        }

        public String toString() {
            String operatorString = isEquals?"EQUALS":isContains?"CONTAINS":isContainsAny?"CONTAINS_ANY":"CONTAINS_ONLY";
            return isDeep?operatorString+"_DEEP":operatorString;
        }


    }
}

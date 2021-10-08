package com.clientScript.value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.clientScript.exception.InternalExpressionException;

public abstract class LazyListValue extends AbstractListValue implements Iterator<Value> {
    public List<Value> unroll() {
        List<Value> result = new ArrayList<>();
        forEachRemaining(result::add);
        fatality();
        return result;
    }

    public static LazyListValue rangeLong(long from, long to, long step) {
        return new LazyListValue() {
            {
                if (step == 0)
                    throw new InternalExpressionException("Range will never end with a zero step");
                this.start = from;
                this.current = this.start;
                this.limit = to;
                this.stepp = step;
            }

            private final long start;
            private long current;
            private final long limit;
            private final long stepp;

            @Override
            public Value next() {
                Value val = new NumericValue(this.current);
                this.current += this.stepp;
                return val;
            }

            @Override
            public void reset() {
                this.current = start;
            }

            @Override
            public boolean hasNext() {
                return this.stepp > 0 ? (this.current < this.limit) : (this.current > this.limit);
            }

            @Override
            public String getString() {
                return String.format(Locale.ROOT, "[%s, %s, ..., %s)", NumericValue.of(this.start).getString(), NumericValue.of(this.start + this.stepp).getString(), NumericValue.of(this.limit).getString());
            }
        };
    }

    public static LazyListValue rangeDouble(double from, double to, double step) {
        return new LazyListValue() {
            {
                if (step == 0)
                    throw new InternalExpressionException("Range will never end with a zero step");
                this.start = from;
                this.current = this.start;
                this.limit = to;
                this.stepp = step;
            }

            private final double start;
            private double current;
            private final double limit;
            private final double stepp;
            
            @Override
            public Value next() {
                Value val = new NumericValue(this.current);
                this.current += this.stepp;
                return val;
            }

            @Override
            public void reset() {
                this.current = start;
            }

            @Override
            public boolean hasNext() {
                return this.stepp > 0 ? (this.current < this.limit) : (this.current > this.limit);
            }

            @Override
            public String getString() {
                return String.format(Locale.ROOT, "[%s, %s, ..., %s)", NumericValue.of(this.start).getString(), NumericValue.of(this.start + this.stepp).getString(), NumericValue.of(this.limit).getString());
            }
        };
    }

    @Override
    public String getString() {
        return "[...]";
    }

    @Override
    public boolean getBoolean() {
        return hasNext();
    }

    public abstract boolean hasNext();
    public abstract void reset();

    @Override
    public void fatality() {
        reset();
    }

    @Override
    public Iterator<Value> iterator() {
        return this;
    }

    @Override
    public Value slice(long from, Long to) {
        if (to == null || to < 0)
            to = (long)Integer.MAX_VALUE;
        if (from < 0)
            from = 0;
        if (from > to)
            return ListValue.of();
        List<Value> result = new ArrayList<>();
        for (int i=0; i<from; i++) {
            if (hasNext())
                next();
            else {
                fatality();
                return ListValue.wrap(result);
            }
        }
        for (int i=(int)from; i<to; i++)  {
            if (hasNext())
                result.add(next());
            else {
                fatality();
                return ListValue.wrap(result);
            }
        }
        return ListValue.wrap(result);
    }

    @Override
    public Value add(Value other) {
        throw new InternalExpressionException("Cannot add to iterators");
    }

    @Override
    public boolean equals(final Object o) {
        return false;
    }

    @Override
    public String getTypeString() {
        return "iterator";
    }

    @Override
    public Object clone() {
        Object copy;
        try {
            copy = super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new InternalExpressionException("Cannot copy iterators");
        }
        ((LazyListValue)copy).reset();
        return copy;
    }

    @Override
    public Value fromConstant() {
        return (Value)clone();
    }

    @Override
    public int hashCode() {
        return ("i" + getString()).hashCode();
    }
}

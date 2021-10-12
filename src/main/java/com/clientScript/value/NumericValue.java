package com.clientScript.value;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

import com.clientScript.exception.InternalExpressionException;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import org.apache.commons.lang3.StringUtils;

import net.minecraft.nbt.AbstractNbtNumber;

public class NumericValue extends Value {
    private Long longValue;
    private final double value;
    private final static double epsilon = Math.abs(32 * ((7 * 0.1) * 10-7));
    private final static MathContext displayRounding = new MathContext(12, RoundingMode.HALF_EVEN);

    public NumericValue(long value) {
        this.longValue = value;
        this.value = (double)value;
    }

    public NumericValue(double value) {
        this.value = value;
    }

    public NumericValue(String value) {
        BigDecimal decimal = new BigDecimal(value);
        if (decimal.stripTrailingZeros().scale() <= 0) {
            try {
                this.longValue = decimal.longValueExact();
            }
            catch (ArithmeticException ignored) {}
        }
        this.value = decimal.doubleValue();
    }

    @Override
    public String getString() {
        if (this.longValue != null)
            return Long.toString(getLong());
        try {
            if (Double.isInfinite(this.value))
                return "INFINITY";
            if (Double.isNaN(this.value))
                return "NaN";
            if (Math.abs(this.value) < NumericValue.epsilon)
                return (Math.signum(this.value) < 0) ? "-0" : "0";
            return BigDecimal.valueOf(this.value).round(NumericValue.displayRounding).stripTrailingZeros().toPlainString();
        }
        catch (NumberFormatException exc) {
            throw new InternalExpressionException("Incorrect number format for " + this.value);
        }
    }

    @Override
    public String getPrettyString() {

        if (this.longValue!= null || getDouble() == (double)getLong())
            return Long.toString(getLong());
        else
            return String.format(Locale.ROOT, "%.1f..", getDouble());
    }

    @Override
    public boolean getBoolean() {
        return Math.abs(this.value) > NumericValue.epsilon;
    }

    private static long floor(double double_1) {
        long int_1 = (long)double_1;
        return double_1 < (double)int_1 ? int_1 - 1 : int_1;
    }

    public int getInt() {
        return (int)getLong();
    }
    
    public long getLong() {
        if (this.longValue != null)
            return this.longValue;
        return floor(this.value + NumericValue.epsilon);
    }

    public float getFloat() {
        return (float)this.value;
    }

    public double getDouble() {
        return this.value;
    }

    @Override
    public double readDoubleNumber() {
        return this.value;
    }

    @Override
    public long readInteger() {
        return getLong();
    }

    @Override
    public Value add(Value v) {  // TODO test if definintn add(NumericVlaue) woud solve the casting
        if (v instanceof NumericValue) {
            NumericValue nv = (NumericValue)v;
            if (this.longValue != null && nv.longValue != null)
                return new NumericValue(this.longValue + nv.longValue);
            return new NumericValue(this.value + nv.value);
        }
        if (v instanceof ListValue)
            return v.add(this);
        if (v instanceof NBTSerializableValue && ((NBTSerializableValue)v).getNbt() instanceof AbstractNbtNumber)
            return new NumericValue(this.value + ((AbstractNbtNumber)((NBTSerializableValue)v).getNbt()).doubleValue());
        return super.add(v);
    }

    @Override
    public Value subtract(Value v) {  // TODO test if definintn add(NumericVlaue) woud solve the casting
        if (v instanceof NumericValue) {
            NumericValue nv = (NumericValue)v;
            if (this.longValue != null && nv.longValue != null)
                return new NumericValue(this.longValue - nv.longValue);
            return new NumericValue(this.value - nv.value);
        }
        if (v instanceof NBTSerializableValue && ((NBTSerializableValue)v).getNbt() instanceof AbstractNbtNumber)
            return new NumericValue(this.value - ((AbstractNbtNumber)((NBTSerializableValue)v).getNbt()).doubleValue());
        return super.subtract(v);
    }

    @Override
    public Value multiply(Value v) {
        if (v instanceof NumericValue) {
            NumericValue nv = (NumericValue)v;
            if (this.longValue != null && nv.longValue != null)
                return new NumericValue(this.longValue * nv.longValue);
            return new NumericValue(this.value * nv.value);
        }
        if (v instanceof ListValue)
            return v.multiply(this);
        if (v instanceof NBTSerializableValue && ((NBTSerializableValue)v).getNbt() instanceof AbstractNbtNumber)
            return new NumericValue(this.value * ((AbstractNbtNumber)((NBTSerializableValue)v).getNbt()).doubleValue());
        return new StringValue(StringUtils.repeat(v.getString(), (int)getLong()));
    }

    @Override
    public Value divide(Value v) {
        if (v instanceof NumericValue)
            return new NumericValue(getDouble() / ((NumericValue)v).getDouble());
        if (v instanceof NBTSerializableValue && ((NBTSerializableValue)v).getNbt() instanceof AbstractNbtNumber)
            return new NumericValue(this.value / ((AbstractNbtNumber)((NBTSerializableValue)v).getNbt()).doubleValue());
        return super.divide(v);
    }

    public static NumericValue asNumber(Value v1) {
        if (!(v1 instanceof NumericValue))
            throw new InternalExpressionException("Operand has to be of a numeric type");
        return (NumericValue)v1;
    }

    public static NumericValue asNumber(Value v1, String id) {
        if (!(v1 instanceof NumericValue))
            throw new InternalExpressionException("Argument " + id + " has to be of a numeric type");
        return (NumericValue)v1;
    }

    public static <T extends Number> Value of(T value) {
        if (value == null)
            return Value.NULL;
        if (value.doubleValue() == value.longValue())
            return new NumericValue(value.longValue());
        if (value instanceof Float)
            return new NumericValue(0.000_001D * Math.round(1_000_000.0D * value.doubleValue()));
        return new NumericValue(value.doubleValue());
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof NullValue)
            return -o.compareTo(this);
        if (o instanceof NumericValue) {
            NumericValue no = (NumericValue)o;
            if (this.longValue != null && no.longValue != null)
                return this.longValue.compareTo(no.longValue);
            return Double.compare(this.value, no.value);
        }
        return getString().compareTo(o.getString());
    }

    @Override
    public JsonElement toJson() {
        if (this.longValue != null)
            return new JsonPrimitive(this.longValue);
        long lv = getLong();
        if (this.value == (double)lv)
            return new JsonPrimitive(getLong());
        else
            return new JsonPrimitive(this.value);
    }

    @Override
    public int hashCode() {
        if (this.longValue != null || Math.abs(Math.floor(this.value + 0.5D) - this.value) < NumericValue.epsilon) // is sufficiently close to the integer value
            return Long.hashCode(getLong());
        return Double.hashCode(this.value);
    }

    @Override
    public int length() {
        return Long.toString(getLong()).length();
    }

    @Override
    public String getTypeString() {
        return "number";
    }

    public NumericValue opposite() {
        if (this.longValue != null) return new NumericValue(-this.longValue);
        return new NumericValue(-this.value);
    }

    public boolean isInteger() {
        return this.longValue != null ||  getDouble() == (double)getLong();
    }

    public Value mod(NumericValue n2) {
        if (this.longValue != null && n2.longValue != null)
            return new NumericValue(Math.floorMod(this.longValue, n2.longValue));
        double x = this.value, y = n2.value;
        if (y == 0)
            throw new ArithmeticException("Division by zero");
        return new NumericValue(x - Math.floor(x / y) * y);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof NullValue)
            return o.equals(this);
        if (o instanceof NumericValue) {
            NumericValue no = (NumericValue)o;
            if (this.longValue != null && no.longValue != null)
                return this.longValue.equals(no.longValue);
            return !subtract(no).getBoolean();
        }
        return super.equals(o);
    }
}

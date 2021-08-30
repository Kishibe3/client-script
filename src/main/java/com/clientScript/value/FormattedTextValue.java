package com.clientScript.value;

import net.minecraft.text.BaseText;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public class FormattedTextValue extends StringValue{
    Text text;

    public FormattedTextValue(Text text) {
        super(null);
        this.text = text;
    }

    @Override
    public String getString() {
        return this.text.getString();
    }

    @Override
    public boolean getBoolean() {
        return !this.text.getString().isEmpty();
    }

    public static Value combine(Value left, Value right) {
        BaseText text;
        if (left instanceof FormattedTextValue)
            text = (BaseText)((FormattedTextValue)left).getText().shallowCopy();
        else {
            if (left instanceof NullValue)
                return right;
            text = new LiteralText(left.getString());
        }

        if (right instanceof FormattedTextValue) {
            text.append(((FormattedTextValue)right).getText().shallowCopy());
            return new FormattedTextValue(text);
        }
        else {
            if (right instanceof NullValue)
                return left;
            text.append(right.getString());
            return new FormattedTextValue(text);
        }
    }

    public Text getText() {
        return this.text;
    }

    @Override
    public Value clone() {
        return new FormattedTextValue(this.text);
    }

    @Override
    public String getTypeString() {
        return "text";
    }

    @Override
    public Value add(Value o) {
        return combine(this, o);
    }

    public static Value of(Text text) {
        if (text == null)
            return Value.NULL;
        return new FormattedTextValue(text);
    }
}

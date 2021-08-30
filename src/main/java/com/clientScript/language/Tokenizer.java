package com.clientScript.language;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.clientScript.exception.ExpressionException;

public class Tokenizer implements Iterator<Tokenizer.Token> {
    /** What character to use for decimal separators. */
    private static final char decimalSeparator = '.';
    /** What character to use for minus sign (negative values). */
    private static final char minusSign = '-';
    /** Actual position in expression string. */
    private int pos = 0;
    private int lineno = 0;
    private int linepos = 0;
    private boolean comments;
    private boolean newLinesMarkers;
    /** The original input expression. */
    private String input;
    /** The previous token or <code>null</code> if none. */
    private Token previousToken;

    private Expression expression;
    private Context context;

    Tokenizer(Context c, Expression expr, String input, boolean allowComments, boolean allowNewLineMakers) {
        this.input = input;
        this.expression = expr;
        this.context = c;
        this.comments = allowComments;
        this.newLinesMarkers = allowNewLineMakers;
    }

    public List<Token> postProcess() {
        Iterable<Token> iterable = () -> this;
        List<Token> originalTokens = StreamSupport.stream(iterable.spliterator(), false).collect(Collectors.toList());
        List<Token> cleanedTokens = new ArrayList<>();
        Token last = null;
        while (originalTokens.size() > 0) {
            Token current = originalTokens.remove(originalTokens.size() - 1);
            // skipping comments
            if (current.type == Token.TokenType.MARKER && current.surface.startsWith("//"))
                continue;
            if (!isSemicolon(current) || (last != null && last.type != Token.TokenType.CLOSE_PAREN && last.type != Token.TokenType.COMMA && !isSemicolon(last))) {
                if (isSemicolon(current)) {
                    current.surface = ";";
                    current.type = Token.TokenType.OPERATOR;
                }
                if (current.type == Token.TokenType.MARKER) {
                    // dealing with tokens in reversed order
                    if ("{".equals(current.surface)) {
                        cleanedTokens.add(current.morphedInto(Token.TokenType.OPEN_PAREN, "("));
                        current.morph(Token.TokenType.FUNCTION, "m");
                    }
                    else if ("[".equals(current.surface)) {
                        cleanedTokens.add(current.morphedInto(Token.TokenType.OPEN_PAREN, "("));
                        current.morph(Token.TokenType.FUNCTION, "l");
                    }
                    else if ("}".equals(current.surface) || "]".equals(current.surface))
                        current.morph(Token.TokenType.CLOSE_PAREN, ")");
                }
                cleanedTokens.add(current);
            }
            if (!(current.type == Token.TokenType.MARKER && current.surface.equals("$")))
                last = current;
        }
        Collections.reverse(cleanedTokens);
        return cleanedTokens;
    }

    private static boolean isSemicolon(Token tok) {
        return (tok.type == Token.TokenType.OPERATOR && tok.surface.equals(";"))
            || (tok.type == Token.TokenType.UNARY_OPERATOR && tok.surface.equals(";u"));
    }
    
    @Override
    public boolean hasNext() {
        return this.pos < this.input.length();
    }

    @Override
    public Token next() {
        Token token = new Token();

        if (this.pos >= this.input.length())
            return this.previousToken = null;
        char ch = this.input.charAt(this.pos);
        while (Character.isWhitespace(ch) && this.pos < this.input.length()) {
            this.linepos++;
            if (ch == '\n') {
                this.lineno++;
                this.linepos = 0;
            }
            ch = this.input.charAt(++pos);
        }
        token.pos = this.pos;
        token.lineno = this.lineno;
        token.linepos = this.linepos;

        boolean isHex = false;
        if (Character.isDigit(ch)) {
            if (ch == '0' && (peekNextChar() == 'x' || peekNextChar() == 'X'))
                isHex = true;
            while ((isHex && isHexDigit(ch))
                    || (Character.isDigit(ch) || ch == Tokenizer.decimalSeparator || ch == 'e' || ch == 'E'
                    || (ch == Tokenizer.minusSign && token.length() > 0
                    && ('e' == token.charAt(token.length() - 1)
                    || 'E' == token.charAt(token.length() - 1)))
                    || (ch == '+' && token.length() > 0
                    && ('e' == token.charAt(token.length() - 1)
                    || 'E' == token.charAt(token.length() - 1))))
                    && (this.pos < this.input.length())) {
                token.append(this.input.charAt(this.pos++));
                this.linepos++;
                ch = this.pos == this.input.length() ? 0 : this.input.charAt(this.pos);
            }
            token.type = isHex ? Token.TokenType.HEX_LITERAL : Token.TokenType.LITERAL;
        }
        else if (ch == '\'') {
            this.pos++;
            this.linepos++;
            token.type = Token.TokenType.STRINGPARAM;
            if (this.pos == this.input.length() && this.expression != null && this.context != null)
                throw new ExpressionException(this.context, this.expression, token, "Program truncated");
            ch = this.input.charAt(this.pos);
            while (ch != '\'') {
                if (ch == '\\') {
                    char nextChar = peekNextChar();
                    if (nextChar == 'n')
                        token.append('\n');
                    else if (nextChar == 't') {
                        //throw new ExpressionException(context, this.expression, token,
                        //        "Tab character is not supported");
                        token.append('\t');
                    }
                    else if (nextChar == 'r')
                    {
                        throw new ExpressionException(this.context, this.expression, token, "Carriage return character is not supported");
                        //token.append('\r');
                    }
                    else if (nextChar == '\\' || nextChar == '\'')
                        token.append(nextChar);
                    else {
                        this.pos--;
                        this.linepos--;
                    }
                    this.pos += 2;
                    this.linepos += 2;
                    if (this.pos == this.input.length() && this.expression != null && this.context != null)
                        throw new ExpressionException(this.context, this.expression, token, "Program truncated");
                }
                else {
                    token.append(this.input.charAt(this.pos++));
                    this.linepos++;
                    if (ch=='\n') {
                        this.lineno++;
                        this.linepos = 0;
                    }
                    if (this.pos == this.input.length() && this.expression != null && this.context != null)
                        throw new ExpressionException(this.context, this.expression, token, "Program truncated");
                }
                ch = this.input.charAt(this.pos);
            }
            this.pos++;
            this.linepos++;
        }
        else if (Character.isLetter(ch) || "_".indexOf(ch) >= 0) {
            while ((Character.isLetter(ch) || Character.isDigit(ch) || "_".indexOf(ch) >= 0 || token.length() == 0 && "_".indexOf(ch) >= 0) && (this.pos < this.input.length())) {
                token.append(this.input.charAt(this.pos++));
                this.linepos++;
                ch = this.pos == this.input.length() ? 0 : this.input.charAt(this.pos);
            }
            // Remove optional white spaces after function or variable name
            if (Character.isWhitespace(ch)) {
                while (Character.isWhitespace(ch) && this.pos < this.input.length()) {
                    ch = this.input.charAt(this.pos++);
                    this.linepos++;
                    if (ch=='\n') {
                        this.lineno++;
                        this.linepos = 0;
                    }
                }
                this.pos--;
                this.linepos--;
            }
            token.type = ch == '(' ? Token.TokenType.FUNCTION : Token.TokenType.VARIABLE;
        }
        else if (ch == '(' || ch == ')' || ch == ',' || ch == '{' || ch == '}' || ch == '[' || ch == ']') {
            if (ch == '(')
                token.type = Token.TokenType.OPEN_PAREN;
            else if (ch == ')')
                token.type = Token.TokenType.CLOSE_PAREN;
            else if (ch == ',')
                token.type = Token.TokenType.COMMA;
            else
                token.type = Token.TokenType.MARKER;
            token.append(ch);
            this.pos++;
            this.linepos++;

            if (this.expression != null && this.context != null && this.previousToken != null &&
                    this.previousToken.type == Token.TokenType.OPERATOR &&
                    (ch == ')' || ch == ',' || ch == ']' || ch == '}') &&
                    !this.previousToken.surface.equalsIgnoreCase(";"))
                throw new ExpressionException(this.context, this.expression, this.previousToken, "Can't have operator " + this.previousToken.surface + " at the end of a subexpression");
        }
        else {
            String greedyMatch = "";
            int initialPos = this.pos;
            int initialLinePos = this.linepos;
            ch = this.input.charAt(this.pos);
            int validOperatorSeenUntil = -1;
            while (!Character.isLetter(ch) && !Character.isDigit(ch) && "_".indexOf(ch) < 0
                    && !Character.isWhitespace(ch) && ch != '(' && ch != ')' && ch != ','
                    && (this.pos < this.input.length())) {
                greedyMatch += ch;
                if (this.comments && "//".equals(greedyMatch)) {
                    while ( ch != '\n' && this.pos < this.input.length()) {
                        ch = this.input.charAt(this.pos++);
                        this.linepos++;
                        greedyMatch += ch;
                    }
                    if (ch=='\n') {
                        this.lineno++;
                        this.linepos = 0;
                    }
                    token.append(greedyMatch);
                    token.type = Token.TokenType.MARKER;
                    return token; // skipping setting previous
                }
                this.pos++;
                this.linepos++;
                if (Expression.none.isAnOperator(greedyMatch))
                    validOperatorSeenUntil = this.pos;
                ch = this.pos == this.input.length() ? 0 : this.input.charAt(this.pos);
            }
            if (newLinesMarkers && "$".equals(greedyMatch)) {
                this.lineno++;
                this.linepos = 0;
                token.type = Token.TokenType.MARKER;
                token.append('$');
                return token; // skipping previous token lookback
            }
            if (validOperatorSeenUntil != -1)
            {
                token.append(this.input.substring(initialPos, validOperatorSeenUntil));
                this.pos = validOperatorSeenUntil;
                this.linepos = initialLinePos + validOperatorSeenUntil - initialPos;
            }
            else
                token.append(greedyMatch);

            if (this.previousToken == null || this.previousToken.type == Token.TokenType.OPERATOR
                    || this.previousToken.type == Token.TokenType.OPEN_PAREN || this.previousToken.type == Token.TokenType.COMMA
                    || (this.previousToken.type == Token.TokenType.MARKER && (this.previousToken.surface.equals("{") || this.previousToken.surface.equals("[")))) {
                token.surface += "u";
                token.type = Token.TokenType.UNARY_OPERATOR;
            }
            else
                token.type = Token.TokenType.OPERATOR;
        }

        if (this.expression != null && this.context != null && this.previousToken != null &&
            (
                token.type == Token.TokenType.LITERAL ||
                token.type == Token.TokenType.HEX_LITERAL ||
                token.type == Token.TokenType.VARIABLE ||
                token.type == Token.TokenType.STRINGPARAM ||
                (token.type == Token.TokenType.MARKER && (this.previousToken.surface.equalsIgnoreCase("{") || this.previousToken.surface.equalsIgnoreCase("["))) ||
                token.type == Token.TokenType.FUNCTION
            ) && (
                this.previousToken.type == Token.TokenType.VARIABLE ||
                this.previousToken.type == Token.TokenType.FUNCTION ||
                this.previousToken.type == Token.TokenType.LITERAL ||
                this.previousToken.type == Token.TokenType.CLOSE_PAREN ||
                (this.previousToken.type == Token.TokenType.MARKER && (this.previousToken.surface.equalsIgnoreCase("}") || this.previousToken.surface.equalsIgnoreCase("]"))) ||
                this.previousToken.type == Token.TokenType.HEX_LITERAL ||
                this.previousToken.type == Token.TokenType.STRINGPARAM
            )
        )
            throw new ExpressionException(this.context, this.expression, this.previousToken, "'" + token.surface +"' is not allowed after '" + this.previousToken.surface + "'");
        
        return this.previousToken = token;
    }

    /**
     * Peek at the next character, without advancing the iterator.
     *
     * @return The next character or character 0, if at end of string.
     */
    private char peekNextChar()
    {
        return (pos < (input.length() - 1)) ? input.charAt(pos + 1) : 0;
    }

    private boolean isHexDigit(char ch)
    {
        return ch == 'x' || ch == 'X' || (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
    }
    
    public static class Token {
        enum TokenType {
            FUNCTION(true, false), OPERATOR(true, false), UNARY_OPERATOR(true, false),
            VARIABLE(false, false), CONSTANT(false, true),
            LITERAL(false, true), HEX_LITERAL(false, true), STRINGPARAM(false, true),
            OPEN_PAREN(false, true), COMMA(false, true), CLOSE_PAREN(false, true), MARKER(false, true);

            boolean functional;
            boolean constant;

            TokenType(boolean functional, boolean constant) {
                this.functional = functional;
                this.constant = constant;
            }

            public boolean isFunctional() {
                return this.functional;
            }

            public boolean isConstant() {
                return this.constant;
            }
        }

        public String surface = "";
        public TokenType type;
        public int pos;
        public int linepos;
        public int lineno;
        public static final Token NONE = new Token();

        public void append(char c) {
            this.surface += c;
        }

        public void append(String s) {
            this.surface += s;
        }

        public char charAt(int pos) {
            return this.surface.charAt(pos);
        }

        public int length() {
            return this.surface.length();
        }

        public Token morphedInto(TokenType newType, String newSurface) {
            Token created = new Token();
            created.surface = newSurface;
            created.type = newType;
            created.pos = pos;
            created.linepos = linepos;
            created.lineno= lineno;
            return created;
        }

        public void morph(TokenType type, String s) {
            this.type = type;
            this.surface = s;
        }
    }
}

package net.runelite.client.plugins.tscripts.lexer;

import net.runelite.client.plugins.tscripts.lexer.models.Token;
import net.runelite.client.plugins.tscripts.lexer.models.TokenType;
import net.runelite.client.plugins.tscripts.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizer
 */
public class Tokenizer
{
    /**
     * Parse the code into tokens
     *
     * @param code the code to parse
     * @return the list of tokens
     */
    public static List<Token> parse(String code)
    {
        return new Tokenizer().tokenize(code);
    }

    /**
     * Tokenize the code
     *
     * @param script the code to tokenize
     * @return the list of tokens
     */
    private List<Token> tokenize(String script) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inString = false;
        boolean inComment = false;
        boolean inMultiLineComment = false;
        boolean inArrayAccess = false;

        char[] chars = script.toCharArray();
        int line = 1;

        for (int i = 0; i < chars.length; i++)
        {
            char c = chars[i];
            char next;
            char previous;
            if (i + 1 < chars.length) next = chars[i + 1];
            else next = ' ';
            if (i - 1 > 0) previous = chars[i - 1];
            else previous = ' ';
            if (c == '\n') line++;

            // Add check for array access start (e.g., $variable[)
            if (c == '[' && currentToken.length() > 0 && currentToken.charAt(0) == '$') {
                inArrayAccess = true;
                currentToken.append(c);
                continue; // Skip further checks and continue to the next character
            }

            // Add check for array access end (e.g., ])
            if (c == ']' && inArrayAccess) {
                inArrayAccess = false;
                currentToken.append(c);
                flushToken(currentToken, tokens, line, TokenType.ARRAY_ACCESS);
                continue;
            }

            if (inString && (c != '"' || previous == '\\'))
            {
                currentToken.append(c);
            }
            else if (c == '"')
            {
                currentToken.append(c);
                if (inString)
                {
                    flushToken(currentToken, tokens, line);
                    inString = false;
                }
                else
                    inString = true;
            }
            else if(inComment && c == '\n')
            {
                inComment = false;
            }
            else if(c == '/' && next == '/' || inComment)
            {
                inComment = true;
            }
            else if (c == '/' && previous == '*' && inMultiLineComment)
            {
                inMultiLineComment = false;
            }
            else if (c == '/' && next == '*' || inMultiLineComment)
            {
                inMultiLineComment = true;
            }
            else if (Character.isWhitespace(c))
            {
                flushToken(currentToken, tokens, line);
            }
            else if (c == '{' || c == '}' || c == '(' || c == ')' || c == ',' || c == ';')
            {
                flushToken(currentToken, tokens, line);
                tokens.add(new Token(getTokenType(String.valueOf(c)), String.valueOf(c), line));
            }
            else if (isOperatorStart(c, next))
            {
                if (currentToken.length() != 0)
                {
                    if (currentToken.length() != 1 || !isOperatorStart(currentToken.charAt(0), next))
                    {
                        flushToken(currentToken, tokens, line);
                    }
                }

                if (isTwoCharOperator(String.valueOf(c) + next))
                {
                    currentToken.append(c);
                }
                else
                {
                    currentToken.append(c);
                    flushToken(currentToken, tokens, line);
                }
            }
            else
            {
                currentToken.append(c);
            }
        }

        flushToken(currentToken, tokens, line);
        tokens.add(new Token(TokenType.EOF, "", line));

        return tokens;
    }

    /**
     * Check if the character is the start of an operator
     *
     * @param c    the character
     * @param next the next character
     * @return true if the character is the start of an operator
     */
    private boolean isOperatorStart(char c, char next) {
        if(c == '-' && Character.isDigit(next))
        {
            return false;
        }
        return   c == '>' || c == '<' || c == '=' || c == '!' || c == '+' || c == '-';
    }

    /**
     * Check if the operator is a two character operator
     *
     * @param operator the operator
     * @return true if the operator is a two character operator
     */
    private boolean isTwoCharOperator(String operator) {
        return operator.equals("!=") || operator.equals(">=") || operator.equals("<=") || operator.equals("+=") || operator.equals("-=") || operator.equals("==") || operator.equals("++") || operator.equals("--") || operator.equals("&&") || operator.equals("||");
    }

    /**
     * Flush the current token
     *
     * @param currentToken the current token
     * @param tokens       the list of tokens
     */
    private void flushToken(StringBuilder currentToken, List<Token> tokens, int line) {
        if (currentToken.length() != 0) {
            TokenType tokenType = getTokenType(currentToken.toString());
            flushToken(currentToken, tokens, line, tokenType);
        }
    }

    /**
     * Flush the current token
     *
     * @param currentToken the current token
     * @param tokens       the list of tokens
     * @param line         the line number
     * @param tokenType    the token type
     */
    private void flushToken(StringBuilder currentToken, List<Token> tokens, int line, TokenType tokenType) {
        if (currentToken.length() != 0) {
            if(tokenType == TokenType.STRING && !currentToken.toString().equals("null"))
            {
                currentToken.deleteCharAt(currentToken.length() - 1);
            }
            else if(tokenType == TokenType.STRING)
            {
                currentToken.insert(0, "\"");
            }
            tokens.add(new Token(tokenType, currentToken.toString(), line));
            currentToken.setLength(0);
        }
    }

    /**
     * Get the token type
     *
     * @param tokenValue the token value
     * @return the token type
     */
    private TokenType getTokenType(String tokenValue)
    {
        switch (tokenValue.toLowerCase())
        {
            case "if": return TokenType.KEYWORD_IF;
            case "else": return TokenType.KEYWORD_ELSE;
            case "while": return TokenType.KEYWORD_WHILE;
            case "for": return TokenType.KEYWORD_FOR;
            case "subscribe": return TokenType.KEYWORD_SUBSCRIBE;
            case "function":
            case "func": return TokenType.KEYWORD_USER_DEFINED_FUNCTION;
            case ">": return TokenType.CONDITION_GT;
            case "<": return TokenType.CONDITION_LT;
            case ">=": return TokenType.CONDITION_GTEQ;
            case "<=": return TokenType.CONDITION_LTEQ;
            case "==": return TokenType.CONDITION_EQ;
            case "!=": return TokenType.CONDITION_NEQ;
            case "&&": return TokenType.CONDITION_AND;
            case "||": return TokenType.CONDITION_OR;
            case "!": return TokenType.NEGATE;
            case "=": return TokenType.VARIABLE_ASSIGNMENT;
            case "+=": return TokenType.VARIABLE_INCREMENT;
            case "-=": return TokenType.VARIABLE_DECREMENT;
            case "++": return TokenType.VARIABLE_ADD_ONE;
            case "--": return TokenType.VARIABLE_REMOVE_ONE;
            case "{": return TokenType.OPEN_BRACE;
            case "}": return TokenType.CLOSE_BRACE;
            case "(": return TokenType.OPEN_PAREN;
            case ")": return TokenType.CLOSE_PAREN;
            case ",": return TokenType.COMMA;
            case ";": return TokenType.SEMICOLON;
            case "null": return TokenType.STRING;
            default:
                if (tokenValue.startsWith("$")) return TokenType.VARIABLE;
                if (tokenValue.startsWith("//")) return TokenType.COMMENT;
                if (tokenValue.startsWith("/*")) return TokenType.MULTI_LINE_COMMENT;
                if (tokenValue.startsWith("\"")) return TokenType.STRING;
                if (TextUtil.isNumeric(tokenValue)) return TokenType.INTEGER;
                if (tokenValue.equalsIgnoreCase("true") || tokenValue.equalsIgnoreCase("false")) return TokenType.BOOLEAN;
                if (tokenValue.contains(".")) return TokenType.STATIC_VALUE;
                return TokenType.IDENTIFIER;
        }
    }
}
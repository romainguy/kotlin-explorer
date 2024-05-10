package dev.romainguy.kotlin.explorer.code;

import java.io.*;
import javax.swing.text.Segment;

import org.fife.ui.rsyntaxtextarea.*;
%%

%public
%class DexTokenMarker
%extends AbstractJFlexTokenMaker
%unicode
%ignorecase
%type org.fife.ui.rsyntaxtextarea.Token

%{
    public DexTokenMarker() {
        super();
    }

    private void addToken(int tokenType) {
        addToken(zzStartRead, zzMarkedPos-1, tokenType);
    }

    private void addToken(int start, int end, int tokenType) {
        int so = start + offsetShift;
        addToken(zzBuffer, start,end, tokenType, so);
    }

    @Override
    public void addToken(char[] array, int start, int end, int tokenType, int startOffset) {
        super.addToken(array, start,end, tokenType, startOffset);
        zzStartRead = zzMarkedPos;
    }

    @Override
    public String[] getLineCommentStartAndEnd(int languageIndex) {
        return new String[] { ";", null };
    }

    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {

        resetTokenList();
        this.offsetShift = -text.offset + startOffset;

        // Start off in the proper state.
        int state = Token.NULL;

        s = text;
        try {
            yyreset(zzReader);
            yybegin(state);
            return yylex();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return new TokenImpl();
        }

    }

    private boolean zzRefill() {
        return zzCurrentPos>=s.offset+s.count;
    }

    public final void yyreset(Reader reader) {
        // 's' has been updated.
        zzBuffer = s.array;
        /*
         * We replaced the line below with the two below it because zzRefill
         * no longer "refills" the buffer (since the way we do it, it's always
         * "full" the first time through, since it points to the segment's
         * array).  So, we assign zzEndRead here.
         */
        //zzStartRead = zzEndRead = s.offset;
        zzStartRead = s.offset;
        zzEndRead = zzStartRead + s.count - 1;
        zzCurrentPos = zzMarkedPos = zzPushbackPos = s.offset;
        zzLexicalState = YYINITIAL;
        zzReader = reader;
        zzAtBOL  = true;
        zzAtEOF  = false;
    }
%}

Letter				    = ([A-Za-z_])
LowerCaseLetter		    = ([a-z])
Digit				    = ([0-9])
Number				    = (({Digit}|{LowerCaseLetter})+)

Identifier			    = (({Letter}|{Digit})[^ \t\f\n\,\.\+\-\*\/\%\[\]]+)

OpCode  			    = ({LowerCaseLetter}({LowerCaseLetter}|{Digit})*[^ \t\f\n\,\.\+\*\%\[\]]+)

UnclosedStringLiteral	= ([\"][^\"]*)
StringLiteral			= ({UnclosedStringLiteral}[\"])
UnclosedCharLiteral		= ([\'][^\']*)
CharLiteral			    = ({UnclosedCharLiteral}[\'])

CommentBegin			= ("//")

LineTerminator			= (\n)
WhiteSpace			    = ([ \t\f])

Label				    = ({Digit}({Letter}|{Digit})*[\:])

%state CODE
%state FUNCTION_SIGNATURE

%%

<YYINITIAL> {
    "class"		{ addToken(Token.RESERVED_WORD); }
}

<YYINITIAL> {
    {LineTerminator}				{ addNullToken(); return firstToken; }

    {WhiteSpace}+					{ addToken(Token.WHITESPACE); }

    {Label}					    	{ addToken(Token.PREPROCESSOR); yybegin(CODE); }

    ^{WhiteSpace}+({Letter}|[<])({Letter}|{Digit}|[-\>$])* {
            String text = yytext();
            int index = text.indexOf('-');
            if (index == -1) {
                addToken(Token.FUNCTION);
            } else {
                int start = zzStartRead;
                addToken(start, start + index - 1, Token.FUNCTION);
                addToken(start + index, zzMarkedPos-1, Token.COMMENT_MULTILINE);
            }
            yybegin(FUNCTION_SIGNATURE);
    }

    {CommentBegin}.*				{ addToken(Token.COMMENT_EOL); addNullToken(); return firstToken; }

    <<EOF>>						    { addNullToken(); return firstToken; }

    {Identifier}					{ addToken(Token.IDENTIFIER); }
    .							    { addToken(Token.IDENTIFIER); }
}

<FUNCTION_SIGNATURE> {
    {LineTerminator}				{ addNullToken(); return firstToken; }

    {WhiteSpace}+					{ addToken(Token.WHITESPACE); }

    {CommentBegin}.*				{ addToken(Token.COMMENT_EOL); addNullToken(); return firstToken; }

    <<EOF>>						    { addNullToken(); return firstToken; }

    {Identifier}					{ addToken(Token.IDENTIFIER); }
    .							    { addToken(Token.IDENTIFIER); }
}

<CODE> {
    "#int" |
    "#long" |
    "#char" |
    "#short" |
    "#double" |
    "#float"	{ addToken(Token.DATA_TYPE); }

    /* Registers */
    "v0" |
    "v1" |
    "v2" |
    "v3" |
    "v4" |
    "v5" |
    "v6" |
    "v7" |
    "v8" |
    "v9" |
    "v10" |
    "v11" |
    "v12" |
    "v13" |
    "v14" |
    "v15" |
    "v16"		{ addToken(Token.VARIABLE); }
}

<CODE> {
    {CharLiteral}					{ addToken(Token.LITERAL_CHAR); }
    {UnclosedCharLiteral}			{ addToken(Token.ERROR_CHAR); }
    {StringLiteral}				    { addToken(Token.LITERAL_STRING_DOUBLE_QUOTE); }
    {UnclosedStringLiteral}			{ addToken(Token.ERROR_STRING_DOUBLE); addNullToken(); return firstToken; }

    {CommentBegin}.*				{ addToken(Token.COMMENT_EOL); addNullToken(); return firstToken; }

    {Label}					    	{ addToken(Token.PREPROCESSOR); }

    ([{].+[}])                      { addToken(Token.VARIABLE); }

    {OpCode}                        { addToken(Token.RESERVED_WORD); }

    (L[^ \t\f]+)  		    		{ addToken(Token.IDENTIFIER); }

    {Number}						{ addToken(Token.LITERAL_NUMBER_DECIMAL_INT); }

    <<EOF>>						    { addNullToken(); return firstToken; }

    {Identifier}					{ addToken(Token.IDENTIFIER); }
    .							    { addToken(Token.IDENTIFIER); }
}
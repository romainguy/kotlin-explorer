package dev.romainguy.kotlin.explorer.code;

import java.io.*;
import javax.swing.text.Segment;

import org.fife.ui.rsyntaxtextarea.*;
%%

%public
%class OatTokenMarker
%extends AbstractJFlexTokenMaker
%unicode
%ignorecase
%type org.fife.ui.rsyntaxtextarea.Token

%{
    public OatTokenMarker() {
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

    @Override
	public boolean getMarkOccurrencesOfTokenType(int type) {
		return type == Token.RESERVED_WORD || type == Token.FUNCTION || type == Token.VARIABLE;
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
HexLetter				= ([A-Fa-f])
LowerCaseLetter		    = ([a-z])
Digit				    = ([0-9])
Number				    = ({Digit}+)
HexNumber				= (0x({Digit}|{HexLetter})+)

Operator                = ([ \t\f\n\#\,\.\+\-\*\/\%\[\]\(\)])

Identifier			    = (({Letter}|{Digit})[^ \t\f\n\,\.\+\-\*\/\%\[\]]+)

OpCode  			    = ({LowerCaseLetter}+)

UnclosedStringLiteral	= ([\"][^\"]*)
StringLiteral			= ({UnclosedStringLiteral}[\"])
UnclosedCharLiteral		= ([\'][^\']*)
CharLiteral			    = ({UnclosedCharLiteral}[\'])

CommentBegin			= ((";")|("//"))
MetadataBegin			= ("--")

LineTerminator			= (\n)
WhiteSpace			    = ([ \t\f])

Label				    = (0x({Digit}|{HexLetter})+[\:])

%state CODE
%state CLASS
%state FUNCTION_SIGNATURE

%%

<YYINITIAL> {
    "class"		                    { addToken(Token.RESERVED_WORD_2); yybegin(CLASS); }

    {LineTerminator}				{ addNullToken(); return firstToken; }

    {WhiteSpace}+					{ addToken(Token.WHITESPACE); }

    {Label}					    	{ addToken(Token.PREPROCESSOR); yybegin(CODE); }

    ^{WhiteSpace}+{Letter}({Letter}|{Digit}|[.])* {
        addToken(Token.DATA_TYPE);
        yybegin(FUNCTION_SIGNATURE);
    }

    {MetadataBegin}.*				{ addToken(Token.MARKUP_CDATA); addNullToken(); return firstToken; }
    {CommentBegin}.*				{ addToken(Token.COMMENT_EOL); addNullToken(); return firstToken; }

    <<EOF>>						    { addNullToken(); return firstToken; }

    {Identifier}					{ addToken(Token.IDENTIFIER); }
    .							    { addToken(Token.IDENTIFIER); }
}

<CLASS> {
    {LineTerminator}				{ addNullToken(); return firstToken; }

    {WhiteSpace}+					{ addToken(Token.WHITESPACE); }

    {CommentBegin}.*				{ addToken(Token.COMMENT_EOL); addNullToken(); return firstToken; }

    <<EOF>>						    { addNullToken(); return firstToken; }

    {Identifier}					{ addToken(Token.FUNCTION); }
    .							    { addToken(Token.IDENTIFIER); }
}

<FUNCTION_SIGNATURE> {
    {LineTerminator}				{ addNullToken(); return firstToken; }

    {WhiteSpace}+					{ addToken(Token.WHITESPACE); }

    {CommentBegin}.*				{ addToken(Token.COMMENT_EOL); addNullToken(); return firstToken; }

    {Letter}({Letter}|{Digit}|[$.\<\>])+ {
        addToken(Token.FUNCTION);
    }

    (-({Letter}|{Digit}|[$.])+) {
        addToken(Token.COMMENT_MULTILINE);
    }

    ([\(].+[\)])					{ addToken(Token.IDENTIFIER); }

    <<EOF>>						    { addNullToken(); return firstToken; }

    {Identifier}					{ addToken(Token.IDENTIFIER); }
    .							    { addToken(Token.IDENTIFIER); }
}

<CODE> {
    "addr"  	{ addToken(Token.DATA_TYPE); }

    "sp" |
    "pc" |
    "zr" |
    "xzr" |
    "wzr" |
    "lr" |
    "nzcv" |
    "fpcr" |
    "fpsr" |
    "daif" |
    "w0" |
    "w1" |
    "w2" |
    "w3" |
    "w4" |
    "w5" |
    "w6" |
    "w7" |
    "w8" |
    "w9" |
    "w10" |
    "w11" |
    "w12" |
    "w13" |
    "w14" |
    "w15" |
    "w16" |
    "w17" |
    "w18" |
    "w19" |
    "w20" |
    "w21" |
    "w22" |
    "w23" |
    "w24" |
    "w25" |
    "w26" |
    "w27" |
    "w28" |
    "w29" |
    "w30" |
    "w0" |
    "r1" |
    "r2" |
    "r3" |
    "r4" |
    "r5" |
    "r6" |
    "r7" |
    "r8" |
    "r9" |
    "r10" |
    "r11" |
    "r12" |
    "r13" |
    "r14" |
    "r15" |
    "r16" |
    "r17" |
    "r18" |
    "r19" |
    "r20" |
    "r21" |
    "r22" |
    "r23" |
    "r24" |
    "r25" |
    "r26" |
    "r27" |
    "r28" |
    "r29" |
    "r30" |
    "x0" |
    "x1" |
    "x2" |
    "x3" |
    "x4" |
    "x5" |
    "x6" |
    "x7" |
    "x8" |
    "x9" |
    "x10" |
    "x11" |
    "x12" |
    "x13" |
    "x14" |
    "x15" |
    "x16" |
    "x17" |
    "x18" |
    "x19" |
    "x20" |
    "x21" |
    "x22" |
    "x23" |
    "x24" |
    "x25" |
    "x26" |
    "x27" |
    "x28" |
    "x29" |
    "x30"		{ addToken(Token.VARIABLE); }

    "oshld" |
    "oshst" |
    "nshld" |
    "nshst" |
    "ishld" |
    "ishst" |
    "ld" |
    "st" |
    "sy" |
    "eq" |
    "ne" |
    "cs" |
    "hs" |
    "cc" |
    "lo" |
    "mi" |
    "pl" |
    "vs" |
    "vc" |
    "hi" |
    "ls" |
    "ge" |
    "lt" |
    "gt" |
    "le" |
    "al"       { addToken(Token.RESERVED_WORD_2); }
}

<CODE> {
    {CharLiteral}					{ addToken(Token.LITERAL_CHAR); }
    {UnclosedCharLiteral}			{ addToken(Token.ERROR_CHAR); }
    {StringLiteral}				    { addToken(Token.LITERAL_STRING_DOUBLE_QUOTE); }
    {UnclosedStringLiteral}			{ addToken(Token.ERROR_STRING_DOUBLE); addNullToken(); return firstToken; }

    {CommentBegin}.*				{ addToken(Token.COMMENT_EOL); addNullToken(); return firstToken; }

    {OpCode}                        { addToken(Token.RESERVED_WORD); }

    {HexNumber}						{ addToken(Token.LITERAL_NUMBER_HEXADECIMAL); }
    {Number}						{ addToken(Token.LITERAL_NUMBER_DECIMAL_INT); }

    <<EOF>>						    { addNullToken(); return firstToken; }

    {Operator}			            { addToken(Token.OPERATOR); }
    [^]                		        { addToken(Token.IDENTIFIER); }
}
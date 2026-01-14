/*
 [The "BSD licence"]
 Copyright (c) 2007-2008 Terence Parr
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/*
author: Stefan Taranu
mail: stefan.taranu@gmail.com
Built with : java org.antlr.Tool ASN.g
antlr version: 3.1.1

The grammar is by far not complete. I have no experience in ANTLR, still
it was not so difficult to write this grammar.

In broad lines it is copied  from the ASN specification files (from the Annex):
X.680, X.681, X.682, X.683 and compiled it into one file. I removed some
of the predicates since it was too much ambiguity.

If you have some comments/improvements, send me an e-mail.
*/

grammar ASN;

fileContent :
    BOM? moduleDefinition* SUBSTITUTE?
    ;

moduleDefinition :
    identifier objectIdentifierValue? ( '{' modulePath? '}' )?
    'DEFINITIONS' ('EXPLICIT'|'IMPLICIT')? 'TAGS'?
    '::='
    'BEGIN'
    moduleBody
    'END'
    ;

modulePath :
    (identifier ('(' NUMBER ')')? NUMBER? )+
    ;

moduleBody :
    (exports imports assignmentList)?
    ;

exports :
    ('EXPORTS' symbolsExported ';')?
    ;

symbolsExported :
    ( symbolList )?
    ;

imports :
    ('IMPORTS' symbolsImported ';'? )?
    ;

symbolsImported
    : symbolsFromModuleList?
    ;

symbolsFromModuleList
    : symbolsFromModule+
    ;

symbolsFromModule :
    symbolList 'FROM' globalModuleReference
    ;

globalModuleReference :
    identifier objectIdentifierValue?
    ;

symbolList :
    symbol (','? symbol)* ','?
    ;

symbol :
    identifier
    | 'OBJECT-TYPE'
    | 'TRAP-TYPE'
    | 'MODULE-IDENTITY'
    | 'OBJECT-IDENTITY'
    | 'OBJECT-GROUP'
    | 'MODULE-COMPLIANCE'
    | 'NOTIFICATION-TYPE'
    | 'TEXTUAL-CONVENTION'
    | 'NOTIFICATION-GROUP'
    | 'AGENT-CAPABILITIES'
    | 'INTEGER' (('{'  '}'))?
    | 'BITS'
    ;

assignmentList :
    assignment*
    ;

assignment
    : (id='OBJECT-TYPE'
    | id='TRAP-TYPE'
    | id='MODULE-IDENTITY'
    | id='OBJECT-IDENTITY'
    | id='OBJECT-GROUP'
    | id='MODULE-COMPLIANCE'
    | id='NOTIFICATION-TYPE'
    | id='TEXTUAL-CONVENTION'
    | id='NOTIFICATION-GROUP'
    | id='AGENT-CAPABILITIES'
    | identifier)
    assignementType
   ;

assignementType
    : complexAssignement
    | typeAssignment
    | valueAssignment
    | textualConventionAssignement
    | objectTypeAssignement
    | trapTypeAssignement
    | moduleIdentityAssignement
    | moduleComplianceAssignement
    | macroAssignement
    ;

//Found missing or extra comma in sequence
sequenceType :
    ('SEQUENCE' | 'SET') '{' (sequenceElement ','* )+ '}'
    ;

sequenceElement :
    identifier '[' NUMBER ']' ('EXPLICIT' | 'IMPLICIT') identifier ('DEFAULT' identifier)? 'OPTIONAL'?
    | identifier '[' NUMBER ']' ('EXPLICIT' | 'IMPLICIT') type ('DEFAULT' identifier)? 'OPTIONAL'?
    | identifier identifier 'DEFINED' 'BY' identifier 'OPTIONAL'?
    | identifier 'BOOLEAN' ('DEFAULT' ('TRUE' | 'FALSE'))? 'OPTIONAL'?
    | identifier '[' NUMBER ']' 'ANY' 'DEFINED' 'BY' identifier
    | namedType ('DEFAULT' identifier)? 'OPTIONAL'?
    | identifier identifier '.' '&' identifier '(' '{' identifier '}' ('{' '@' identifier '}')?  ')' 'OPTIONAL'?
    | choiceType
    ;

sequenceOfType  : ('SEQUENCE' | 'SET') sizeConstraint? 'OF' (type | namedType )
    ;

typeAssignment :
    ('{' class=identifier ':' val=identifier '}')?
      '::='
    ( '[' universal_details ']' )?
    ( '[' application_details ']' )?
    ('IMPLICIT')?
      type
;

application_details:
    'APPLICATION'? NUMBER;

universal_details:
    'UNIVERSAL' NUMBER;

complexAssignement :
    macroName
     (complexAttribut ','*)+
      '::='
      value
    ;

macroName :
    'OBJECT-GROUP'
    | 'OBJECT-IDENTITY'
    | 'NOTIFICATION-TYPE'
    | 'NOTIFICATION-GROUP'
    | 'AGENT-CAPABILITIES'
    ;

complexAttribut:
    access
    | status
    | name='GROUP' IDENTIFIER
    | name='OBJECT' IDENTIFIER
    | name='SUPPORTS' IDENTIFIER
    | name='VARIATION' IDENTIFIER
    | name='SYNTAX' type
    | name='REVISION' stringValue
    | name='CONTACT-INFO' stringValue
    | name='ORGANIZATION' stringValue
    | name='LAST-UPDATED' stringValue
    | name='UNITS' stringValue
    | name='REFERENCE' stringValue
    | name='DESCRIPTION' stringValue
    | name='MODULE' identifier?
    | name='INCLUDES' groups
    | name='OBJECTS' objects
    | name='VARIABLES' variables
    | name='INDEX' index
    | name='DEFVAL' '{' defValue '}'
    | name='DISPLAY-HINT' stringValue
    | name='NOTIFICATIONS' notifications
    | name='AUGMENTS' augments
    | name='WRITE-SYNTAX' type
    | name='PRODUCT-RELEASE' stringValue
    | name='CREATION-REQUIRES' groups
    | name='DISPLAY-HINT' stringValue
    | name='REFERENCE' stringValue
;

access:
    ( name='MAX-ACCESS' | name='ACCESS' | name='MIN-ACCESS') identifier
    ;

status:
    name='STATUS' identifier
    ;

groups:
    '{' identifier (','? identifier)* ','? '}'
    ;

objects:
    '{' value (','? value)* ','? '}'
    ;

variables:
    '{' identifier (',' identifier)* ','? '}'
    ;

notifications:
    '{' identifier (',' identifier)* ','? '}'
    ;

augments:
    '{' identifier '}'
    ;

index:
    '{' indexTypes (','? indexTypes)* ','? '}'
    ;

indexTypes:
    'IMPLIED'? type
    ;

moduleIdentityAssignement:
    'MODULE-IDENTITY'
    ('LAST-UPDATED' lu=stringValue
    | 'ORGANIZATION' stringValue
    | 'CONTACT-INFO' stringValue
    | 'DESCRIPTION' stringValue)+
    moduleRevisions
    '::='
    objectIdentifierValue
    ;


moduleRevisions:
    moduleRevision*
    ;

moduleRevision:
    'REVISION' stringValue
    'DESCRIPTION' stringValue
    ;

textualConventionAssignement :
    '::=' 'TEXTUAL-CONVENTION' (complexAttribut ','*)+
    ;

moduleComplianceAssignement :
    'MODULE-COMPLIANCE'
    status
    'DESCRIPTION' stringValue
    ('REFERENCE' stringValue)?
    (complianceModules)+
    '::='
    objectIdentifierValue
    ;
    
complianceModules :
    'MODULE' identifier?
    ('MANDATORY-GROUPS' groups)?
    compliance*
    ;
    
compliance:
    ('GROUP' identifier 'DESCRIPTION' stringValue)
    | ('OBJECT' identifier ('SYNTAX' type)? ('WRITE-SYNTAX' type)? ('MIN-ACCESS' identifier)? ('DESCRIPTION' stringValue)?)
    ;

trapTypeAssignement :
    'TRAP-TYPE'
    enterpriseAttribute
     (complexAttribut ','*)+
      '::='
      integerValue
    ;

enterpriseAttribute :
    'ENTERPRISE' (identifier | objectIdentifierValue)
    ;

objectTypeAssignement :
    'OBJECT-TYPE'
     (complexAttribut ','*)+
      '::='
      value
    ;

macroAssignement : 
    'MACRO' '::=' 'BEGIN' macroContent+ 'END'
    ;

macroContent:
    identifier 'NOTATION'? ? '::=' macroVal+ ( '|' macroVal+ )*
    ;

macroVal:
    CSTRING 
    | identifier
    | identifier? '(' (identifier | 'OBJECT' | 'identifier'| type ) * ')'
    ;

valueAssignment :
      type
      '::='
       value
;

type
    : ('{' '{' identifier 'IDENTIFIED' 'BY' identifier '}' ',' '...' '}')
    | (('EXPLICIT' | 'IMPLICIT')? (builtinType | referencedType) constraint* ('{' namedNumberList '}')?)
    ;

builtinType :
   octetStringType
 | bitStringType
 | choiceType
 | integerType
 | sequenceOfType
 | sequenceType
 | objectIdentifierType
 | nullType
 | bitsType
    ;

bitsType:
    'BITS' ('{' bitsEnumeration '}')?
    ;

bitsEnumeration:
    bitDescription ( ',' bitDescription)+
    ;

bitDescription:
    identifier '(' NUMBER ')'
    ;

nullType:
    'NULL'
    ;

referencedType :
    identifier ('.' identifier)?
    ;

elements :
    ( value '..' value )
    | value
    | '...'
    ;

constraintElements :
    elements ( '|' elements)*
    ;

constraint
    : '(' (fromConstraint | sizeConstraint | valuesConstraint) ')'
    ;

sizeConstraint
    : 'SIZE' '(' constraintElements ')'
    ;

valuesConstraint
    : constraintElements (',' constraintElements)*
    ;

fromConstraint
    : 'FROM' '(' constraintElements ')'
    ;

defValue
    : referenceValue
    |   integerValue
    |   choiceValue
    |   booleanValue
    |   stringValue
    |   bitsValue
    |   objectIdentifierValue
    |   ipValue
    ;

value
    : referenceValue
    |   integerValue
    |   choiceValue
    |   objectIdentifierValue
    |   booleanValue
    |   stringValue
    ;

bitsValue:
    '{' (identifier ','?)* '}'
    ;

referenceValue
    : identifier
    ;

objectIdentifierValue :
    '{' identifier ? objIdComponentsList '}'
    ;

objIdComponentsList :
    (objIdComponents ','? )*
    ;

objIdComponents 
    : NUMBER
    | id=(OIDIDENTIFIER|IDENTIFIER) ( '(' NUMBER ')' )
    ;

integerValue :
     signedNumber
    | hexaNumber
    | binaryNumber
    ;

choiceValue  :
    identifier ':' value
    ;

stringValue
    : CSTRING
    ;

ipValue
    : IP
    ;

signedNumber:
    NUMBER
    ;

binaryNumber
    :  BINARYNUMBER 
    ;

hexaNumber
    : HEXANUMBER
    ;

choiceType    : 'CHOICE' '{' (namedType ','*)+ '}'
;

namedType :
    identifier ('[' NUMBER ']')? type
    ;

namedNumber :
    (name=IDENTIFIER | name='TRUE' | name='FALSE' | name='true' | name='false' ) '(' signedNumber ')'
    ;

integerType :
    'INTEGER'  ('{' namedNumberList '}')?
    ;

namedNumberList :
    (namedNumber) (','? namedNumber)* ','?
    ;

objectIdentifierType:
    'OBJECT' 'IDENTIFIER'
    ;

octetStringType :
    'OCTET' 'STRING'
    ;

bitStringType    : ('BIT' 'STRING') ('{' namedBitList '}')?
;
namedBitList: (namedBit) (',' namedBit)*
;
namedBit      : identifier '(' NUMBER ')'
    ;

booleanValue:
    'TRUE' | 'FALSE' | 'true' | 'false'
    ;

fragment DIGIT
    : '0'..'9'
    ;

fragment UPPER
    : ('A'..'Z')
    ;

fragment LOWER
    : ('a'..'z')
    ;
identifier
   : 'ANY'
   | 'identifier'
   | IDENTIFIER
   ;

IP :
    DIGIT+ '.' DIGIT+  '.' DIGIT+  '.' DIGIT+ 
    ;

NUMBER
    : '-'? DIGIT+
    ;

fragment Exponent
    : ('e'|'E') ('+'|'-')? NUMBER
    ;

COMMENT :
    ( '\r'* '\n' ('--' ~( '\n' |'\r')* '\r'* '\n')+ // A comments at the line starts comments the whole line
    | '-- CIM' ~( '\n' |'\r')* '\r'? '\n'           // -- CIM--# is a construct found in some Compaq's MIB
    | '--' ~( '\n' |'\r' ) (.*? ( ~('-' | '\n') '--' | EOF | '\r'* '\n')) 
    | '--' '-'? (EOF | '\r'* '\n')
    ) -> skip
    ;

//| '--' ~( '\n' |'\r' ) (.*? ( ~('-' | '\n') '--'('#'.*? '\r'? '\n')? | EOF | '\r'? '\n')) 
//COMMENT : '--' ~( '\n' |'\r')* '\r'? '\n' -> skip;

WS
    :  (' '|'\r'|'\t'|'\u000C'|'\n') -> skip
    ;

fragment HEXDIGIT
    : (DIGIT|'a'..'f'|'A'..'F')
    ;

HEXANUMBER :
    '\'' HEXDIGIT*  '\'' ( 'h' | 'H') 
    ;
    

fragment BINARYDIGIT :
    '0' | '1'
    ;

BINARYNUMBER:
    '\'' BINARYDIGIT* '\'' 'B'
    ;

CSTRING
    :  QUOTATIONMARK (  ~( '"' | '“' | '”') )* QUOTATIONMARK
    ;

fragment
QUOTATIONMARK:
    '"'
    | '“'
    | '”'
    ;


//fragment

/**I found this char range in JavaCC's grammar, but Letter and Digit overlap.
   Still works, but...
 */
fragment
LETTER :
    '\u0024' |
    '\u002d' |
    '\u0041'..'\u005a' |
    '\u005f' |
    '\u0061'..'\u007a' |
    '\u00c0'..'\u00d6' |
    '\u00d8'..'\u00f6' |
    '\u00f8'..'\u00ff' |
    '\u0100'..'\u1fff' |
    '\u3040'..'\u318f' |
    '\u3300'..'\u337f' |
    '\u3400'..'\u3d2d' |
    '\u4e00'..'\u9fff' |
    '\uf900'..'\ufaff'
    ;

fragment
JavaIDDigit
    :  '\u0030'..'\u0039' |
       '\u0660'..'\u0669' |
       '\u06f0'..'\u06f9' |
       '\u0966'..'\u096f' |
       '\u09e6'..'\u09ef' |
       '\u0a66'..'\u0a6f' |
       '\u0ae6'..'\u0aef' |
       '\u0b66'..'\u0b6f' |
       '\u0be7'..'\u0bef' |
       '\u0c66'..'\u0c6f' |
       '\u0ce6'..'\u0cef' |
       '\u0d66'..'\u0d6f' |
       '\u0e50'..'\u0e59' |
       '\u0ed0'..'\u0ed9' |
       '\u1040'..'\u1049'
   ;

fragment
NameChar
    :   NameStartChar
    |   '0'..'9'
    | '-'
    |   '_'
    |   '\u00B7'
    |   '\u0300'..'\u036F'
    |   '\u203F'..'\u2040'
    ; 

fragment
NameStartChar
    :   'A'..'Z' | 'a'..'z'
    |   '\u00C0'..'\u00D6'
    |   '\u00D8'..'\u00F6'
    |   '\u00F8'..'\u02FF'
    |   '\u0370'..'\u037D'
    |   '\u037F'..'\u1FFF'
    |   '\u200C'..'\u200D'
    |   '\u2070'..'\u218F'
    |   '\u2C00'..'\u2FEF'
    |   '\u3001'..'\uD7FF'
    |   '\uF900'..'\uFDCF'
    |   '\uFDF0'..'\uFFFD'
    ;

IDENTIFIER
    : LETTER (LETTER|JavaIDDigit)*
    ;

OIDIDENTIFIER
    : (LETTER|JavaIDDigit)+
    ;

BOM :
    '\ufffd' -> skip
    ;
    
SUBSTITUTE :
    '\u001a' -> skip
    ;

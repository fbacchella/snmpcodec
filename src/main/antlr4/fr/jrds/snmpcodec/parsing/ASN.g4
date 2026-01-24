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
    moduleReference objectIdentifierValue? ( '{' modulePath? '}' )?
    'DEFINITIONS' tagDefault? extensionDefault?
    '::='
    'BEGIN'
    moduleBody
    'END'
    ;

moduleReference
    : typeReference (valueReference | objectIdentifierValue)?
    ;

tagDefault
    : 'EXPLICIT' 'TAGS'
    | 'IMPLICIT' 'TAGS'
    | 'AUTOMATIC' 'TAGS'
    ;

extensionDefault
    : 'EXTENSIBILITY' 'IMPLIED'
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
    symbolList 'FROM' moduleReference
    ;

symbolList :
    symbol (','? symbol)* ','?
    ;

symbol
    : typeReference
    | valueReference
    | identifier
    | macroReference
    | moduleReference
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
    : typeAssignment
    | valueAssignment
    | macroAssignement
    // macros from SNMP
    | moduleComplianceAssignement
    | moduleIdentityAssignement
    | notificationGroupAssignement
    | notificationTypeAssignement
    | objectIdentityAssignement
    | objectGroupAssignement
    | objectTypeAssignement
    | textualConventionAssignement
    | trapTypeAssignement
   ;

//Found missing or extra comma in sequence
sequenceType :
    'SEQUENCE' constraint? '{' (sequenceElement ','* )+ '}'
    ;

setType :
    'SET' constraint? '{' (sequenceElement ','* )+ '}'
    ;

sequenceElement
    : identifier '[' NUMBER ']' ('EXPLICIT' | 'IMPLICIT')? 'ANY' 'DEFINED' 'BY' identifier
    | identifier '[' NUMBER ']' ('EXPLICIT' | 'IMPLICIT')? type default? 'OPTIONAL'?
    | identifier identifier 'DEFINED' 'BY' identifier 'OPTIONAL'?
    | identifier '[' NUMBER ']' ('EXPLICIT' | 'IMPLICIT') identifier default? 'OPTIONAL'?
    | namedType default? 'OPTIONAL'? ('{{' identifier '}}')?
    | identifier identifier '.' '&' identifier '(' '{' identifier '}' ('{' '@' identifier '}')?  ')' 'OPTIONAL'?
    | choiceType
    | identifier 'ANY' ('DEFINED' 'BY' identifier)?
    ;

default
    : 'DEFAULT' (value | identifier)
    ;

sequenceOfType  : ('SEQUENCE' | 'SET') constraint? 'OF' (type | namedType )
    ;

typeAssignment :
    typeReference
    '::='
    type
    ;

complexAttribut:
    access
    | status
    | name='GROUP' IDENTIFIER
    | name='OBJECT' IDENTIFIER
    | name='SUPPORTS' IDENTIFIER
    | name='VARIATION' IDENTIFIER
    | syntax
    | name='REVISION' stringValue
    | name='CONTACT-INFO' stringValue
    | name='ORGANIZATION' stringValue
    | name='LAST-UPDATED' stringValue
    | name='UNITS' stringValue
    | name='REFERENCE' stringValue
    | description
    | name='MODULE' identifier?
    | name='INCLUDES' groups
    | name='OBJECTS' objects
    | name='VARIABLES' variables
    | name='INDEX' index
    | name='DEFVAL' '{' defValue '}'
    | name='DISPLAY-HINT' stringValue
    | name='NOTIFICATIONS' notifications
    | name='AUGMENTS' augments
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

description:
    name='DESCRIPTION' value
    ;

reference:
    name='REFERENCE' value
    ;

syntax:
    ('SYNTAX' | 'WRITE-SYNTAX') type syntaxSubType?
    ;
groups:
    '{' identifier (','? identifier)* ','? '}'
    ;

objects:
    '{' value (','? value)* ','? '}'
    ;

variables:
    '{' typeReference (',' typeReference)* ','? '}'
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
    'IMPLIED'? valueReference
    ;

syntaxSubType:
    ('{' namedNumberList '}')?
    ;

moduleIdentityAssignement:
    valueReference
    'MODULE-IDENTITY'
    ('LAST-UPDATED' lu=stringValue
    | 'ORGANIZATION' stringValue
    | 'CONTACT-INFO' stringValue
    | description)+
    moduleRevisions
    '::='
    objectIdentifierValue
    ;

notificationGroupAssignement:
    valueReference 'NOTIFICATION-GROUP' (complexAttribut ','*)+ '::=' objectIdentifierValue
    ;

notificationTypeAssignement:
    valueReference 'NOTIFICATION-TYPE' (complexAttribut ','*)+ '::=' objectIdentifierValue
    ;

objectIdentityAssignement
    : valueReference
    'OBJECT-IDENTITY'
    (status | description | reference)+
    '::='
    objectIdentifierValue
    ;

objectGroupAssignement:
    valueReference 'OBJECT-GROUP' (complexAttribut ','*)+ '::=' objectIdentifierValue;

objectTypeAssignement
    : valueReference 'OBJECT-TYPE' (complexAttribut ','*)+ '::=' objectIdentifierValue;


moduleRevisions:
    moduleRevision*
    ;

moduleRevision:
    'REVISION' stringValue
    description
    ;

textualConventionAssignement :
    typeReference '::=' 'TEXTUAL-CONVENTION' (complexAttribut ','*)+
    ;

moduleComplianceAssignement :
    valueReference
    'MODULE-COMPLIANCE'
    status
    description
    ('REFERENCE' stringValue)?
    (complianceModules)+
    '::='
    objectIdentifierValue
    ;
    
complianceModules :
    'MODULE' typeReference?
    ('MANDATORY-GROUPS' groups)?
    compliance*
    ;
    
compliance:
    ('GROUP' identifier description)
    | ('OBJECT' identifier (syntax)* ('MIN-ACCESS' identifier)? (description)?)
    ;

trapTypeAssignement :
    identifier
    'TRAP-TYPE'
    enterpriseAttribute
    (complexAttribut ','*)+
    '::='
    integerValue
    ;

enterpriseAttribute :
    'ENTERPRISE' (identifier | objectIdentifierValue)
    ;

macroAssignement :
    macroReference 'MACRO' '::=' 'BEGIN' macroContent+ 'END'
    ;

macroContent:
    'TYPE' 'NOTATION' '::=' (typeReference | (CSTRING (typeReference | 'value' '(' type type?')' | 'type' '(' 'TYPE' type ')')))+
    'VALUE' 'NOTATION' '::=' 'value' '(' 'VALUE' type ')'
    (macroElement)*
    ;

macroElement:
    typeReference '::=' macroVal+ ('|' macroVal)*
    ;

macroVal:
    (CSTRING
    | (identifier|typeReference)
    | identifier? '(' (identifier | type | 'OBJECT' ) * ')'
    | identifier '"("' (identifier | 'OBJECT' | type ) * '")"'
    | CSTRING? '"{"' typeReference '"}"'
    | CSTRING typeReference
    | typeReference ('","' type)*
    | CSTRING? 'value' '(' type+ ')') macroVal*
    ;

valueAssignment
    : valueReference type '::=' value
;

type
    : builtinType constraint?
    | referencedType constraint?
    | typeWithConstraint
    ;

builtinType
    : bitStringType
    | booleanType
    | choiceType
    | octetStringType
    | integerType
    | sequenceOfType
    | sequenceType
    | setType
    | objectIdentifierType
    | nullType
    | bitsType
    | prefixedType
    ;

bitsType:
    'BITS' ('{' bitsEnumeration '}')?
    ;

prefixedType
    : taggedType
    ;

taggedType
    : tag ('EXPLICIT' | 'IMPLICIT')type
    ;

tag
    : '[' ('UNIVERSAL' | 'APPLICATION' | 'PRIVATE') ? NUMBER ']'
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

booleanType
    : 'BOOLEAN'
    ;

referencedType
    : typeReference ('.' identifier)? constraint?
    ;

typeWithConstraint
    : 'typeWithConstraint'
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

definedValue
    : referenceValue
    | VALUEREFERENCE
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
    | integerValue
    | choiceValue
    | objectIdentifierValue
    | booleanValue
    | stringValue
    | nullValue
    ;

bitsValue:
    '{' (identifier ','?)* '}'
    ;

referenceValue
    : identifier
    ;

objectIdentifierValue
    : '{' objIdComponent+ '}'
    ;

objIdComponent
    : identifier ('(' NUMBER ')' )?
    | NUMBER
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

nullValue
    : 'NULL'
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
    'INTEGER'  ('{' namedNumberList '}')? constraint?
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
   : 'value' | 'type' | IDENTIFIER
   ;

macroReference
    : 'AGENT-CAPABILITIES'
    | 'MODULE-COMPLIANCE'
    | 'MODULE-IDENTITY'
    | 'NOTIFICATION-GROUP'
    | 'NOTIFICATION-TYPE'
    | 'OBJECT-GROUP'
    | 'OBJECT-IDENTITY'
    | 'OBJECT-TYPE'
    | 'TEXTUAL-CONVENTION'
    ;

typeReference
   : TYPEIDENTIFIER
   ;

valueReference
   : IDENTIFIER
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

TYPEIDENTIFIER
    : [A-Z] ([A-Za-z0-9-]* [A-Za-z0-9])?
    ;

IDENTIFIER
    :[a-z] ([A-Za-z0-9-]* [A-Za-z0-9])?
    ;

BOM :
    '\ufffd' -> skip
    ;
    
SUBSTITUTE :
    '\u001a' -> skip
    ;

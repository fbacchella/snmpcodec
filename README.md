snmpcodec is a resilient SNMP parser and formater. It's purpose is not to manage a mib, check module or similar tasks. It's used to help processing SNMP messages, and be able to use all the broken modules that can be found all around.

For this taks, it uses the excelent ANLTR parser generator that recover from many failure in module source. It can also take hint and help about the way to process file or missing symbols.

It can process table index

    MibTree resolver = new MibTree();
    OID vacmAccessContextMatch = new OID("1.3.6.1.6.3.16.1.4.1.4.7.118.51.103.114.111.117.112.0.3.1");
    Map<String, Object> parts = resolver.parseIndexOID(vacmAccessContextMatch.getValue());
    parts.forEach( (i,j)-> System.out.format("%s '%s' %s\n", i, j, j.getClass().getName()));

will output.

    vacmAccessTable 'vacmAccessContextMatch' java.lang.String
    vacmGroupName 'v3group' java.lang.String
    vacmAccessContextPrefix '' java.lang.String
    vacmAccessSecurityModel '3' java.lang.Integer
    vacmAccessSecurityLevel 'noAuthNoPriv' java.lang.String


It can also be used with SNMP4J as it provides an help class that implement OIDTextFormat and VariableTextFormat.

To use it, just call

        OIDFormatter.register()

It will then used the property `snmpcodec.mibdirssnmpcodec.mibdirs` formatted as path using the JVM's path separator. If this property is not set, modules
are searched in `/usr/share/snmp/mibs/usr/share/snmp/mibs`.
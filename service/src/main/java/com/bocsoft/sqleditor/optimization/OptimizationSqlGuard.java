package com.bocsoft.sqleditor.optimization;

import com.bocsoft.sqleditor.common.ApiException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Conservative lexical supplement to the engine scanner: reject writable CTEs and SELECT INTO. */
final class OptimizationSqlGuard {
    private static final Set<String> WRITES=new HashSet<String>(Arrays.asList("INSERT","UPDATE","DELETE","MERGE","UPSERT","REPLACE","INTO","OUTFILE","DUMPFILE","CALL","DO","LOCK","UNLOCK","CREATE","ALTER","DROP","TRUNCATE","GRANT","REVOKE"));
    private OptimizationSqlGuard(){ }
    static void requireReadOnly(String sql, String engineId){
        boolean postgres = "POSTGRESQL".equals(engineId);
        boolean mysql = "MYSQL".equals(engineId) || "GBASE_8A".equals(engineId);
        for(int i=0;i<sql.length();){
            char c=sql.charAt(i);
            if(c=='\''||c=='"'||c=='`'){
                boolean escaped = !postgres || (c=='\'' && i>0 && (sql.charAt(i-1)=='E'||sql.charAt(i-1)=='e'));
                char quote=c;i++;
                while(i<sql.length()){
                    char q=sql.charAt(i++);
                    if(escaped&&q=='\\'&&i<sql.length()){i++;continue;}
                    if(q==quote){if(i<sql.length()&&sql.charAt(i)==quote){i++;continue;}break;}
                }
            }else if(c=='/'&&i+1<sql.length()&&sql.charAt(i+1)=='*'){
                if(i+2<sql.length()&&sql.charAt(i+2)=='!')reject();
                int end=sql.indexOf("*/",i+2);if(end<0)reject();i=end+2;
            }else if((mysql&&c=='#')||(c=='-'&&i+1<sql.length()&&sql.charAt(i+1)=='-')){
                while(i<sql.length()&&sql.charAt(i)!='\r'&&sql.charAt(i)!='\n')i++;
            }else if(postgres&&c=='$'){
                java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\$(?:[A-Za-z_][A-Za-z0-9_]*)?\\$").matcher(sql.substring(i));
                if(m.lookingAt()){String tag=m.group();int end=sql.indexOf(tag,i+tag.length());if(end<0)reject();i=end+tag.length();}else i++;
            }else if(Character.isLetter(c)||c=='_'){
                int start=i++;while(i<sql.length()&&(Character.isLetterOrDigit(sql.charAt(i))||sql.charAt(i)=='_'))i++;
                if(WRITES.contains(sql.substring(start,i).toUpperCase(Locale.ROOT)))reject();
            }else i++;
        }
    }
    private static void reject(){throw ApiException.validation("sql","READ_ONLY_VIOLATION","仅支持单条只读 SELECT SQL");}
}

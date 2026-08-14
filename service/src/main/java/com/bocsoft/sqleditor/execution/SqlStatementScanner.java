package com.bocsoft.sqleditor.execution;

import com.bocsoft.sqleditor.common.ApiException;
import java.util.ArrayList;import java.util.List;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Component;

@Component
public class SqlStatementScanner {
    public String requireSingle(String sql){List<String>statements=split(sql);if(statements.isEmpty())throw ApiException.validation("statement","REQUIRED","SQL 不能为空");if(statements.size()!=1)throw new ApiException(HttpStatus.BAD_REQUEST,"MULTI_STATEMENT_NOT_SUPPORTED","暂不支持批量执行");return statements.get(0);}
    public List<String>split(String sql){List<String>out=new ArrayList<String>();if(sql==null)return out;int start=0;State state=State.NORMAL;for(int i=0;i<sql.length();i++){char c=sql.charAt(i),n=i+1<sql.length()?sql.charAt(i+1):'\0';switch(state){case NORMAL:if(c=='\'')state=State.SINGLE;else if(c=='"')state=State.DOUBLE;else if(c=='`')state=State.BACKTICK;else if(c=='#')state=State.LINE;else if(c=='-'&&n=='-'&&(i+2>=sql.length()||Character.isWhitespace(sql.charAt(i+2)))){state=State.LINE;i++;}else if(c=='/'&&n=='*'){state=State.BLOCK;i++;}else if(c==';'){add(out,sql.substring(start,i));start=i+1;}break;case SINGLE:if(c=='\\')i++;else if(c=='\''&&n=='\'')i++;else if(c=='\'')state=State.NORMAL;break;case DOUBLE:if(c=='\\')i++;else if(c=='"'&&n=='"')i++;else if(c=='"')state=State.NORMAL;break;case BACKTICK:if(c=='`'&&n=='`')i++;else if(c=='`')state=State.NORMAL;break;case LINE:if(c=='\n'||c=='\r')state=State.NORMAL;break;case BLOCK:if(c=='*'&&n=='/'){state=State.NORMAL;i++;}break;default:break;}}if(state==State.SINGLE||state==State.DOUBLE||state==State.BACKTICK||state==State.BLOCK)throw ApiException.validation("statement","INVALID","SQL 引号或注释未闭合");add(out,sql.substring(start));return out;}
    private void add(List<String>out,String v){String clean=v.trim();if(!clean.isEmpty())out.add(clean);}private enum State{NORMAL,SINGLE,DOUBLE,BACKTICK,LINE,BLOCK}
}

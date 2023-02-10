package com.moxa.mybatis.antlr;

import com.moxa.dream.antlr.expr.PackageExpr;
import com.moxa.dream.antlr.read.ExprReader;
import com.moxa.dream.antlr.sql.*;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
})
public class MyBatisAntlrInterceptor implements Interceptor {
    private ToSQL toSQL = new ToMYSQL();
    private Map<String, String> antlrMap = new HashMap();
    private int cap = 1000;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = statementHandler.getBoundSql();
        //获取到原始sql语句
        String sql = boundSql.getSql();
        String antlrSql;
        if (sql.startsWith(AnlrConstant.NOT_ANTLR)) {
            antlrSql = sql.substring(1);
        } else {
            antlrSql = antlrMap.get(sql);
            if (antlrSql == null) {
                synchronized (this) {
                    antlrSql = antlrMap.get(sql);
                    if (antlrSql == null) {
                        if (antlrMap.size() > cap) {
                            antlrMap.clear();
                        }
                        try {
                            antlrSql = toSQL.toStr(new PackageExpr(new ExprReader(sql)).expr(), null, null);
                        } catch (Exception e) {
                            throw new AntlrRunTimeException(e);
                        }
                        antlrMap.put(sql, antlrSql);
                    }
                }
            }
        }
        //通过反射修改sql语句
        Field field = boundSql.getClass().getDeclaredField(AnlrConstant.SQL);
        field.setAccessible(true);
        field.set(boundSql, antlrSql);
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        String dbType = properties.getProperty(AnlrConstant.DB_TYPE);
        String cacheSize = properties.getProperty(AnlrConstant.CACHE_SIZE);
        if (dbType != null) {
            switch (dbType.toLowerCase()) {
                case AnlrConstant.MYSQL:
                    toSQL = new ToMYSQL();
                    break;
                case AnlrConstant.SQLSERVER:
                    toSQL = new ToMSSQL();
                    break;
                case AnlrConstant.POSTGRES:
                    toSQL = new ToPGSQL();
                    break;
                case AnlrConstant.ORACLE:
                    toSQL = new ToORACLE();
                    break;
                default:
                    throw new AntlrRunTimeException(AnlrConstant.DB_TYPE + ":" + dbType + "识别错误");
            }
        }
        if (cacheSize != null && !cacheSize.isEmpty()) {
            Integer size = Integer.valueOf(cacheSize);
            if (size <= 0) {
                throw new AntlrRunTimeException(AnlrConstant.CACHE_SIZE + "必须大于0");
            }
            this.cap = size;
        }
    }
}

package com.moxa.mybatis.antlr;

public class AntlrRunTimeException extends RuntimeException{
    public AntlrRunTimeException(String msg) {
        super(msg);
    }
    public AntlrRunTimeException(Exception e) {
        super(e);
    }
}

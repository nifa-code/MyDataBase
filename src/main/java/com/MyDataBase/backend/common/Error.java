package com.MyDataBase.backend.common;

public class Error {
    //TM相关错误
    public static final Exception BadXIDFileException = new RuntimeException("Bad XID file!");
    // common
    public static final Exception CacheFullException = new RuntimeException("Cache is full!");
    public static final Exception FileExistsException = new RuntimeException("File already exists!");
    public static final Exception FileNotExistsException = new RuntimeException("File does not exists!");
    public static final Exception FileCannotRWException = new RuntimeException("File cannot read or write!");

}

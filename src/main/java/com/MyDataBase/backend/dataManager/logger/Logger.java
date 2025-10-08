package com.MyDataBase.backend.dataManager.logger;


import com.MyDataBase.backend.util.Panic;
import com.MyDataBase.backend.util.Error;
import com.MyDataBase.backend.util.Parser;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface Logger {
    void log(byte []data);
    void truncate(long xid) throws Exception;
    byte[] next();
    void rewind();
    void close();

    static Logger create(String path){
        return createOrOpen(path,true);
    }

    static Logger open(String path){
        return createOrOpen(path,false);
    }

    static Logger createOrOpen(String path, boolean create){
        Path filePath= Paths.get(path+LoggerImpl.LOG_SUFFIX);
        RandomAccessFile raf = null;
        FileChannel ch = null;
        try {
            if (create) {
                if(Files.exists(filePath)){
                    Panic.panic(Error.FileExistsException);
                }
                raf = new RandomAccessFile(filePath.toFile(), "rw");
                ch = raf.getChannel();
                ByteBuffer bff = ByteBuffer.wrap(Parser.int2byte(0));
                ch.position(0);
                ch.write(bff);
                ch.force(false);
            }else{
                //如果只需要打开文件
                if(!Files.exists(filePath)){
                    Panic.panic(Error.FileNotExistsException);
                }

                raf = new RandomAccessFile(filePath.toFile(), "rw");
                ch = raf.getChannel();
            }
        }catch(Exception e){
                Panic.panic(e);
            }
        if(!Files.isReadable(filePath)||!Files.isWritable(filePath)){
            Panic.panic(Error.FileCannotRWException);
        }
        return create? new LoggerImpl(raf,ch,0):new LoggerImpl(raf,ch);
    }

}

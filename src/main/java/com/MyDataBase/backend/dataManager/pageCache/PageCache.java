package com.MyDataBase.backend.dataManager.pageCache;
import com.MyDataBase.backend.dataManager.page.Page;
import com.MyDataBase.backend.util.Panic;
import com.MyDataBase.backend.util.Error;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface PageCache {
    public static final int PAGE_SIZE = 1 << 13;
    int newPage(byte[] initData);
    Page getPage(int pageNo) throws Exception;
    void  close();
    void release(Page page);
    void flushPage(Page page);
    void truncateByBgno(int maxPgno);
    int getPageNumber();


    //创建或者打开缓存
    static PageCacheImpl create(String path,long memory){
        return createOrOpen(path,memory,true);
    }


    static PageCacheImpl open(String path,long memory){
        return createOrOpen(path,memory,false);
    }

    static PageCacheImpl createOrOpen(String path,long memory,boolean create){
        File file=new File(path+PageCacheImpl.DB_SUFFIX);
        if(create){
            try{
            if(!file.createNewFile()) {
                Panic.panic(Error.FileExistsException);
            }
            } catch(IOException e){
                Panic.panic(e);
            }
        }else{
            if(!file.exists()){
                Panic.panic(Error.FileNotExistsException);
            }
        }
        //统一检查文件是否能正常读写不
        if(!file.canRead()||!file.canWrite()){
            Panic.panic(Error.FileCannotRWException);
        }
        RandomAccessFile raf=null;
        FileChannel fc=null;
        try {
            raf = new RandomAccessFile(file, "rw");
            fc = raf.getChannel();
           // MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_WRITE, 0, memory);
        }catch(Exception e){
            Panic.panic(e);
        }
        return new PageCacheImpl(raf, fc,(int) (memory / PAGE_SIZE));

    }


}

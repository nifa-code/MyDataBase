package com.MyDataBase.backend.dataManager.pageCache;

import com.MyDataBase.backend.common.AbstractCache;
import com.MyDataBase.backend.dataManager.page.Page;
import com.MyDataBase.backend.dataManager.page.PageImpl;
import com.MyDataBase.backend.util.Panic;
import com.MyDataBase.backend.util.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 主要功能是管理页面缓存，包括创建新页面、获取页面、释放页面、刷新页面到磁盘
 */
public class PageCacheImpl extends AbstractCache<Page> implements PageCache {
    private static final int MEM_MIN_LIM = 10;
    public static final String DB_SUFFIX = ".db";
    private RandomAccessFile file;
    private FileChannel fc;
    private Lock fileLock;

    private AtomicInteger pageNumbers;
    PageCacheImpl(RandomAccessFile file, FileChannel fc,int maxResource) {
        super(maxResource);
        if(maxResource < MEM_MIN_LIM)
        {
            Panic.panic(Error.MemTooSmallException);
        }
        long length=0;
        try{
            length=file.length();
        }catch(IOException e){
            Panic.panic(e);
        }
        this.file = file;
        this.fc = fc;
        this.fileLock = new ReentrantLock();
        this.pageNumbers=new AtomicInteger((int)(length/PAGE_SIZE));
    }
    @Override
    public int newPage(byte[] initData){
        int pgno=pageNumbers.incrementAndGet();
        Page pg=new PageImpl(pgno,initData,null);
        flushPage(pg);
        return pgno;
    }
    @Override
    public Page getPage(int pageNo) throws Exception {
        return get((long)(pageNo));
    }

    @Override
    public void close() {
        try {
            super.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try{
        fc.close();
        file.close();
    }catch(IOException e){
        Panic.panic(e);
    }
    }

    /**getForPage
     * 作用：从文件中读取页面数据并创建新的 PageImpl 对象。
     * 逻辑：
     * 计算页面的偏移量。
     * 使用 FileChannel 读取页面数据到 ByteBuffer。
     * 创建新的 PageImpl 对象并返回。
     * @param key
     * @return
     * @throws Exception
     */
    @Override
    protected Page getForCache(long key) throws Exception {
        int pgNo=(int)key;
        long offset=PageCacheImpl.pageOffset(pgNo);

        ByteBuffer bb= ByteBuffer.allocate(PAGE_SIZE);
        fileLock.lock();
        try{
           fc.position(offset);
           fc.read(bb);
        }catch(IOException e){
            Panic.panic(e);
        }
        fileLock.unlock();
        return new PageImpl(pgNo,bb.array(),this);
    }
    @Override
    protected void releaseForCache(Page page) throws Exception {
        if(page.isDirty()){
            flushPage(page);
            page.setDirty(false);
        }
    }

    @Override
    public void release(Page page){
        try {
            release((long)page.getPageNumber());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void flushPage(Page page) {
         flush(page);
    }
    private void flush(Page page){
        int pgNo=page.getPageNumber();
        long offset=PageCacheImpl.pageOffset(pgNo);
        fileLock.lock();
        try{
            ByteBuffer bb= ByteBuffer.wrap(page.getData());
            fc.position(offset);
            fc.write(bb);
            fc.force(false);
        }catch(IOException e){
            Panic.panic(e);
        }finally{
            fileLock.unlock();
        }
    }

    @Override
    public void truncateByBgno(int maxPgno) {
        long size = pageOffset(maxPgno + 1);
        try {
            file.setLength(size);
        } catch (IOException e) {
            Panic.panic(e);
        }
        pageNumbers.set(maxPgno);
    }

    @Override
    public int getPageNumber() {
        return pageNumbers.intValue();//获取AtomicInteger 当前的值
    }
    private static long pageOffset(int pgno) {
        return (pgno-1) * PAGE_SIZE;
    }
}


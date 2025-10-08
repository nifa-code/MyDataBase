package com.MyDataBase.backend.dataManager.DataItem;

import com.MyDataBase.backend.common.SubArray;
import com.MyDataBase.backend.dataManager.DataManagerImpl;
//import sun.jvm.hotspot.debugger.Page;
import com.MyDataBase.backend.dataManager.page.Page
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DataItemImpl implements DataItem {

    static final int OF_VALID = 0;
    static final int OF_SIZE = 1;
    static final int OF_DATA = 3;
    private SubArray raw;
    private byte[] oldRaw;//支持多版本并发控制
    private Lock rLock;
    private Lock wLock;
    private DataManagerImpl dm;
    private long uid;
    private Page page;

    public DataItemImpl(SubArray raw, byte[] oldRaw, Page pg, long uid, DataManagerImpl dm) {
        this.raw = raw;
        this.oldRaw = oldRaw;
        ReadWriteLock lock = new ReentrantReadWriteLock();//支持多读单写并发模型，允许多个线程同时读取，但只有一个线程可以写入。
        rLock = lock.readLock();
        wLock = lock.writeLock();
        this.dm = dm;
        this.uid = uid;
        this.page = pg;
    }

    //默认有效字段 设置为 数据有效的
    public boolean isValid(){
        return raw.raw[raw.start+OF_VALID] == (byte)0;
    };

    @Override
    public SubArray data() {
        return new SubArray(raw.raw, raw.start+OF_DATA, raw.end);
    }

    @Override
    public void before() {
        wLock.lock();
        page.setDirty(true);
        System.arraycopy(raw.raw,raw.start,oldRaw,0,oldRaw.length);
    }

    @Override
    public void unBefore() {
        System.arraycopy(oldRaw,0,raw.raw,raw.start,oldRaw.length);
        wLock.unlock();
    }

    @Override
    public void after(long xid) {
        //设置数据线Id
        dm.logDataItem(xid,this);
        wLock.unlock();
    }

    @Override
    public void release() {
        this.releaseDataItem(this);
    }

    @Override
    public void lock() {
        wLock.lock();
    }

    @Override
    public void unLock() {
        wLock.unlock();
    }

    @Override
    public void rLock() {
        rLock.lock();
    }

    @Override
    public void unRLock() {
        rLock.unlock();
    }

    @Override
    public Page page() {
        return page;
    }

    @Override
    public long getUid() {
        return uid;
    }

    @Override
    public byte[] getOldRow() {
        return oldRaw;
    }

    @Override
    public SubArray getRow() {
        return raw;
    }
}

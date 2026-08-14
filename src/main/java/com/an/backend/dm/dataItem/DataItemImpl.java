package top.guoziyang.mydb.backend.dm.dataItem;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import top.guoziyang.mydb.backend.common.SubArray;
import top.guoziyang.mydb.backend.dm.DataManagerImpl;
import top.guoziyang.mydb.backend.dm.page.Page;

/**
 * dataItem 结构如下：
 * [ValidFlag] [DataSize] [Data]
 * ValidFlag 1字节，0为合法，1为非法
 * DataSize  2字节，标识Data的长度
 */
public class DataItemImpl implements DataItem {

    static final int OF_VALID = 0;
    static final int OF_SIZE = 1;
    static final int OF_DATA = 3;

    private SubArray raw;
    private byte[] oldRaw;
    private Lock rLock;
    private Lock wLock;
    private DataManagerImpl dm;
    private long uid;
    private Page pg;

    public DataItemImpl(SubArray raw, byte[] oldRaw, Page pg, long uid, DataManagerImpl dm) {
        this.raw = raw;
        this.oldRaw = oldRaw;
        ReadWriteLock lock = new ReentrantReadWriteLock();
        rLock = lock.readLock();
        wLock = lock.writeLock();
        this.dm = dm;
        this.uid = uid;
        this.pg = pg;
    }
    public boolean isValid() {
        return raw.raw[raw.start+OF_VALID] == (byte)0;
    }
    @Override
    public SubArray data() {
        return new SubArray(raw.raw, raw.start+OF_DATA, raw.end);
    }
    /*before
     * 在操作执行前进行准备工作
     * <p>
     * 该方法主要完成以下工作：
     * 1. 获取写锁，确保操作的原子性
     * 2. 标记页面为脏页，表示数据已被修改
     * 3. 备份原始数据，用于后续可能的回滚操作
     * </p>
     */
    @Override
    public void before() {
        wLock.lock();
        pg.setDirty(true);
        System.arraycopy(raw.raw, raw.start, oldRaw, 0, oldRaw.length);
    }
    @Override
    public void unBefore() {
        // 恢复原始数据：将oldRaw数组中的数据复制回raw对象的原始位置
        System.arraycopy(oldRaw, 0, raw.raw, raw.start, oldRaw.length);
        // 释放写锁
        wLock.unlock();
    }

    @Override
    public void after(long xid) {
        // 记录数据项到日志
        dm.logDataItem(xid, this);
        // 释放写锁
        wLock.unlock();
    }
    /**release
     * 释放当前数据项资源
     *
     * <p>调用数据管理器的releaseDataItem方法来释放当前对象占用的资源，
     * 将当前对象作为参数传递给数据管理器进行相应的清理操作。</p>
     */
    @Override
    public void release() {
        dm.releaseDataItem(this);
    }

    @Override
    public void lock() {
        wLock.lock();
    }

    @Override
    public void unlock() {
        wLock.unlock();
    }

    @Override
    public void rLock() {
        rLock.lock();
    }

    @Override
    public void rUnLock() {
        rLock.unlock();
    }

    @Override
    public Page page() {
        return pg;
    }

    @Override
    public long getUid() {
        return uid;
    }

    @Override
    public byte[] getOldRaw() {
        return oldRaw;
    }

    @Override
    public SubArray getRaw() {
        return raw;
    }
    
}

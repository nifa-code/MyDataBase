package com.MyDataBase.backend.dataManager;

import com.MyDataBase.backend.dataManager.DataItem.DataItem;
import com.MyDataBase.backend.dataManager.page.PageOne;
import com.MyDataBase.backend.dataManager.pageCache.PageCache;
import com.MyDataBase.backend.dataManager.logger.Logger;
import com.MyDataBase.backend.dataManager.pageCache.PageCacheImpl;
import com.MyDataBase.backend.transactionManager.TransactionManager;
import com.MyDataBase.backend.transactionManager.TransactionManagerImpl;

/**
 * 功能：管理数据库文件和日志文件。
 *
 * 实现步骤：
 * 创建DataManager类。
 * 实现分页管理DB文件并进行缓存。
 * 管理日志文件以支持错误恢复。
 * 提供数据读写接口。
 */
public interface DataManager {
    DataItem read(long uid) throws Exception;
    long insert(long xid, byte[] data) throws Exception;
    void close() throws Exception;

    static DataManager create(String path, long mem, TransactionManager tm){
        //创建Page缓存
        PageCache pc= PageCache.create(path,mem);
        //创建日志
        Logger log=Logger.create(path);
        //数据管理器实例，将页面缓存、日志管理器和事务管理器传递给数据管理器。
        DataManagerImpl dm=new DataManagerImpl(pc,log,tm);
        dm.initPageOne();
        return dm;
    }
    //创建和初始化日志管理器（模拟日志管理器）
    static DataManager open(String path, long mem, TransactionManager tm){
        PageCache pc=PageCache.open(path,mem);
        Logger log=Logger.open(path);
        DataManagerImpl dm=new DataManagerImpl(pc,log,tm);
        if(!dm.loadCheckPageOne()){
            Recover.recover(tm,log,pc);
        }
        dm.fillPageIndex();
        //设置版本控制为打开状态
        PageOne.setVcOpen(dm.pageOne);
        //刷新元数据到页面
        dm.pc.flushPage(dm.PageOne);
        return dm;
    }
}

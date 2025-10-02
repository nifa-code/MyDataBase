package com.MyDataBase.backend.transactionManager;

import com.MyDataBase.backend.util.Panic;
import com.MyDataBase.backend.common.Error;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public interface TransactionManager {
    /*
    ************************事物管理器实现**************************
    事务状态管理
        维护XID文件：通过XID文件来维护事务的状态。每个事务都有一个XID，XID从1开始编号，自增且不可重复。XID文件给每个事务分配一个字节的空间来保存其状态，文件头部保存一个8字节的数字记录事务个数。
        事务状态：事务有三种状态，分别是active（进行中）、committed（已提交）、aborted（已撤销）。
     接口提供
        事务操作接口：提供begin（开启新事务）、commit（提交事务）、abort（取消事务）等接口。
        事务状态查询接口：提供isActive（查询事务是否进行中）、isCommitted（查询事务是否已提交）、isAborted（查询事务是否已取消）等接口。
    特殊事务处理
        超级事务：规定XID为0的事务是超级事务，其状态永远是committed。当一些操作想在没有申请事务的情况下进行时，可将操作的XID设置为0。
*/
    //维护XID的手段
    long begin();
    void commit(long xid);
    void abort(long xid);
    boolean isActive(long xid);
    boolean isCommitted(long xid);
    boolean isAborted(long xid);
    void close();

    //接口中静态方法，可以直接通过接口调用，而不需要实现类

    //创建一个新的事务管理器实例，并初始化XID文件或者打开已经存在的XID文件
    static TransactionManagerImpl create(String path){
        File file = new File(path + TransactionManagerImpl.XID_SUFFIX);
        if (file.exists()) {
            Panic.panic(Error.FileExistsException);
        }
        return createOrOpen(file, true);
    }


    static TransactionManagerImpl open(String path){
        File file = new File(path + TransactionManagerImpl.XID_SUFFIX);
        if (!file.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        return createOrOpen(file, false);
    }


    //判断文件是否存在，进行一系列操作(接口中不能定义private的方法，默认是public 的)
    static TransactionManagerImpl createOrOpen(File file, boolean create){
        if (!file.canRead() || !file.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }
        // 写空XID文件头
        try(RandomAccessFile raf=new RandomAccessFile(file,"rw"); FileChannel fc = raf.getChannel()){//自动资源管理
            if (create) {
                ByteBuffer buf = ByteBuffer.wrap(new byte[TransactionManagerImpl.LEN_XID_HEADER_LENGTH]);
                fc.position(0);
                fc.write(buf);
            }
                return new TransactionManagerImpl(raf, fc);
            } catch (IOException e) {
                Panic.panic(e);
            }
        return null;
        }
}

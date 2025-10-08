package com.MyDataBase.backend.transactionManager;
import com.MyDataBase.backend.util.Panic;
import com.MyDataBase.backend.util.Parser;
import com.MyDataBase.backend.util.Error;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TransactionManagerImpl implements TransactionManager{
    // XID文件头长度
    static final int LEN_XID_HEADER_LENGTH = 8;
    // 每个事务的占用长度
    private static final int XID_FIELD_SIZE = 1;
    // 事务的三种状态
    private static final byte FIELD_TRAN_ACTIVE   = 0;
    private static final byte FIELD_TRAN_COMMITTED = 1;
    private static final byte FIELD_TRAN_ABORTED  = 2;

    // 超级事务，永远为commited状态
    public static final long SUPER_XID = 0;

    static final String XID_SUFFIX = ".xid";

    private RandomAccessFile file;
    private FileChannel fc;
    private long xidCounter;//当前已分配的最大XID值，用于生成下一个XID
    //XID是事务管理器中生成事务ID，创建新事物时会增加,生成唯一的事务ID和验证文件的合法性
    private Lock counterLock;

    TransactionManagerImpl(RandomAccessFile raf, FileChannel fc) {
        this.file = raf;
        this.fc = fc;
        counterLock = new ReentrantLock();
        checkXIDCounter();
    }
    //ReentrantLock()锁要注意使用情况


    //需要检查XID文件是否合法，读取XID_FILE_HEADER中的xidcounter，根据它计算文件的理论长度，对比实际长度
    private void checkXIDCounter(){
        long file_len=0;//long定义的好处是
        try{
            file_len=file.length();
        }catch(IOException e1){
            Panic.panic(Error.BadXIDFileException);
        }
        //文件合法性
        if(file_len<LEN_XID_HEADER_LENGTH){
            Panic.panic(Error.BadXIDFileException);
        }
        ByteBuffer  bff = ByteBuffer.allocate(LEN_XID_HEADER_LENGTH);//堆内存中分配一个指定大小(xid头部长度8)的ByteBuffer
        try{
            fc.position(0);//定位到XID文件的起始位置
            fc.read(bff);//从文件通道读取数据到缓冲区
        }catch(IOException e2){
            Panic.panic(Error.BadXIDFileException);
        }
        this.xidCounter= Parser.parserlong(bff.array());//将bff转为ByteBuffer对象
        //从中解析出事务ID
    }

    // 根据事务xid取得其在xid文件中对应的位置
    private long getXidPosition(long xid){
        return LEN_XID_HEADER_LENGTH+XID_FIELD_SIZE*(xid-1);
    }

    //更新事务状态为status
    private void updateXID(long xid, byte status){
        long xidPosition = getXidPosition(xid);
        byte[]tmp=new byte[XID_FIELD_SIZE];
        tmp[0]=status;
        ByteBuffer buf = ByteBuffer.wrap(tmp);
        try{
            fc.position(xidPosition);
            fc.write(buf);
        }catch(IOException e2){
            Panic.panic(e2);
        }

        try {
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }



    //将Xid加1，更新XID Header
    public void incrXIDCounter(){
        xidCounter++;
        ByteBuffer bb=ByteBuffer.wrap(Parser.long2byte(xidCounter));
        try{
            fc.position(0);
            fc.write(bb);
        }catch(IOException e2){
            Panic.panic(e2);
        }
        //强制刷盘
        try {
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }
    @Override
    public long begin() {
        //开启一个事物
        counterLock.lock();
        try{
           long xid=xidCounter+1;
            updateXID(xid, FIELD_TRAN_ACTIVE);
            incrXIDCounter();
            return xid;
        }finally {
            counterLock.unlock();
        }
    }
    //提交事务
    @Override
    public void commit(long xid) {
        updateXID(xid, FIELD_TRAN_COMMITTED);
    }
    //回滚事务
    @Override
    public void abort(long xid) {
        updateXID(xid, FIELD_TRAN_ABORTED);
    }

    //检测XID事务是否处于status状态
    public boolean checkXID(long xid, byte status){
        //计算事务起始位置
        long xidPosition = getXidPosition(xid);
        //创建一字节缓冲区
        ByteBuffer buf = ByteBuffer.wrap(new byte[XID_FIELD_SIZE]);
        //定位事务记录，读取事务状态
        try{
            fc.position(xidPosition);
            fc.read(buf);
        }catch(IOException e2){
            Panic.panic(e2);
        }
        buf.flip();  // 切换为读模式
        byte actualStatus = buf.get();  // 明确读取一个字节
        return actualStatus == status;
    }

    @Override
    public boolean isActive(long xid) {
        if(xid==SUPER_XID) return false;
        return checkXID(xid, FIELD_TRAN_ACTIVE);
    }

    @Override
    public boolean isCommitted(long xid) {
        if(xid==SUPER_XID) return false;
        return checkXID(xid, FIELD_TRAN_COMMITTED);
    }

    @Override
    public boolean isAborted(long xid) {
        if(xid==SUPER_XID) return false;
        return checkXID(xid, FIELD_TRAN_ABORTED);
    }

    @Override
    public void close() {
        try {
            fc.close();    // 关闭文件通道
            file.close();  // 关闭文件对象
        } catch (IOException e) {
            Panic.panic(e);
        }
    }
}

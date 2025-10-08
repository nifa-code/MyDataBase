package com.MyDataBase.backend.dataManager.logger;
import com.google.common.primitives.Bytes;
import com.MyDataBase.backend.util.Panic;
import com.MyDataBase.backend.util.Error;
import com.MyDataBase.backend.util.Parser;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LoggerImpl implements Logger {

    private static final int SEED = 13331;

    private static final int OF_SIZE = 0;
    private static final int OF_CHECKSUM = OF_SIZE + 4;
    private static final int OF_DATA = OF_CHECKSUM + 4;
    public static final String LOG_SUFFIX = ".log";
    private RandomAccessFile  file;
    private FileChannel fc;
    private Lock lock;
    private long position;
    private long fileSize;
    private int  xChecksum;
    //构造函数初始化log
    LoggerImpl(RandomAccessFile  raf,FileChannel fc){
        this.file = raf;
        this.fc = fc;
        lock = new ReentrantLock();
    }

    LoggerImpl(RandomAccessFile  raf,FileChannel fc,int xChecksum){
        this.file = raf;
        this.fc = fc;
        this.xChecksum = xChecksum;
        lock = new ReentrantLock();
    }

    //初始化日志文件保证格式正确

    /**
     * Init()
     * 初始化日志文件：获取文件大小并读取校验和。
     * 验证文件格式：确保文件大小合法，校验和正确。
     * 修复文件：如果文件存在坏尾部，截断文件并移除坏尾部。
     */
    void init() throws IOException {
        fileSize = getFileSize();
        if(fileSize < 4){
            Panic.panic(Error.BadLogFileException);
        }
        xChecksum=readInt(0);
        checkAndRemoveTail();
    }
    private long  getFileSize(){
        try {
            return file.length();
        }catch(IOException e){
            Panic.panic(e);
        }
        return -1;
    }

    private int readInt(long position){
        ByteBuffer raw= ByteBuffer.allocate(4);
        try{
            fc.position(position);
            fc.read(raw);
        }catch(IOException e){
            Panic.panic(e);
        }
        return Parser.parserInt(raw.array());
    }

    void checkAndRemoveTail() throws IOException{
        rewind();//将日志指针回退到文件开头

        int xCheck= 0;
        while(true){
            byte[] log=internNext();
            if(log==null){
                break;
            }
            xCheck = calChecksum(xCheck,log);
        }
        if(xCheck!=xChecksum){
            //不相等的话
            Panic.panic(Error.BadLogFileException);
        }

        truncate(position);//截断文件到当前日志指针的位置，移除坏尾部
        rewind();
    }

    public int calChecksum(int xCheck,byte[] log){
        for (byte b : log) {
            xCheck=(xCheck*SEED+b)&0xFFFFFFFF;
        }
        return xCheck;
    }

    /**
     * 将日志数据写入日志文件，并更新校验和
     * @param data
     */
    @Override
    public void log(byte[] data) {
        byte[] log=wrapLog(data);
        ByteBuffer raw= ByteBuffer.wrap(log);
        lock.lock();
        try{
            fc.position(fc.size());
            fc.write(raw);
        }catch(IOException e){
            Panic.panic(e);
        } finally{
            lock.unlock();
        }
        updateChecksum(log);
    }
    /**
     * 结构[Size] [Checksum] [Data]
     * @param data
     * @return
     */
    private byte[]wrapLog(byte[] data){
         byte []checksum = Parser.int2byte(calChecksum(0,data));
         byte []size= Parser.int2byte(data.length);
         return  Bytes.concat(size,checksum,data);
    }
    public void updateChecksum(byte []log){
        xChecksum=calChecksum(this.xChecksum,log);
        //将新校验和写入日志
        try {
            ByteBuffer newXChecksum = ByteBuffer.wrap(Parser.int2byte(xChecksum));
            fc.position(0);
            fc.write(newXChecksum);
            fc.force(false);
        }catch(IOException e){
            Panic.panic(e);
        }
    }
    @Override
    public void truncate(long xid) throws IOException {
        lock.lock();
        try{
            fc.truncate(xid);
        }finally{
            lock.unlock();
        }
    }
    public byte[] internNext() throws IOException {
        if(position+OF_DATA>=fileSize){
            return null;
        }
        int size=readInt(position);
        if(position+OF_DATA+size>fileSize){
            return null;
        }

        ByteBuffer bff=ByteBuffer.allocate(OF_DATA+size);
        try{
            fc.position(position);
            fc.read(bff);
        }catch(IOException e){
            Panic.panic(e);
        }

        //读取到数据之后
        byte []log=bff.array();
        //计算日志数据校验和
        int checksum1=calChecksum(0, Arrays.copyOfRange(log, OF_DATA, log.length));
        int checksum2=Parser.parserInt(Arrays.copyOfRange(log,OF_CHECKSUM, OF_DATA));

        if(checksum1!=checksum2){
            return null;
        }
        position+=log.length;
        return log;
    }

    @Override
    public byte[] next() {
        lock.lock();
        try{
            byte[]log = internNext();
            return log == null ? null : Arrays.copyOfRange(log, OF_DATA, log.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally{
            lock.unlock();
        }
    }
    @Override
    public void rewind() {
        position=4;
    }
    @Override
    public void close() {
        try(FileChannel fc = this.fc;RandomAccessFile raf = this.file){

        }catch (IOException e) {
            Panic.panic(e);
        }
    }
}

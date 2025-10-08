package com.MyDataBase.backend.dataManager.DataItem;

import com.MyDataBase.backend.common.SubArray;
import com.MyDataBase.backend.dataManager.DataManager;
import com.MyDataBase.backend.dataManager.DataManagerImpl;
import com.google.common.primitives.Bytes;
import com.MyDataBase.backend.dataManager.page.Page;
import com.MyDataBase.backend.util.Error;
import com.MyDataBase.backend.util.Parser;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;

public interface DataItem {
    SubArray data();
    //数据项可能的各个状态
    void before();
    void unBefore();
    void after(long xid);
    void release();

    //涉及的锁
    void lock();
    void unLock();
    void rLock();
    void unRLock();

    //缓存页相关信息
    Page page();
    long getUid();
    byte[] getOldRow();
    SubArray getRow();
    //格式化数据项，有效位，大小，字段内容
    static byte[] wrapDataItemRaw(byte []raw){
        byte[] valid=new byte[1];
        byte[] size=Parser.short2Byte((short)raw.length);
        return Bytes.concat(valid,size,raw);
    }

    //解析页面指定数据项  [有效位 (1字节)] [大小字段 (2字节)] [实际数据内容 (size 字节)]
    static DataItem parseDataItem(Page page, short offset, DataManagerImpl dm){
        byte []raw=page.getData();
        short size=Parser.parserShort(Arrays.copyOfRange(raw,offset+DataManagerImpl.OF_SIZE,offset+ DataManagerImpl.OF_DATA));
        short length=(short)(size+DataManagerImpl.OF_DATA);//DataItemImpl.OF_DATA 包含有效位的大小字段长度
        long uid=Types.AddressToUid(page.getPageNumber(),offset);
        return new DataItemImpl(new SubArray(raw, offset, offset+length), new byte[length], page, uid, dm);
    }
    //设置无效字节
    static void  setDataItemRawInvalid(byte []raw){
        raw[DataManagerImpl.OF_VALID]=(byte)1;
    }
}

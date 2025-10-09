package com.MyDataBase.backend.dataManager;
import com.MyDataBase.backend.common.SubArray;
import com.MyDataBase.backend.dataManager.DataItem.DataItem;
import com.MyDataBase.backend.dataManager.DataItem.DataItemImpl;
import com.MyDataBase.backend.dataManager.logger.Logger;
import com.MyDataBase.backend.dataManager.logger.LoggerImpl;
import com.MyDataBase.backend.dataManager.page.Page;
import com.MyDataBase.backend.dataManager.page.PageOne;
import com.MyDataBase.backend.dataManager.page.PageX;
import com.MyDataBase.backend.dataManager.pageCache.PageCache;
import com.MyDataBase.backend.dataManager.pageCache.PageCacheImpl;
import com.MyDataBase.backend.dataManager.pageIndex.PageIndex;
import com.MyDataBase.backend.dataManager.pageIndex.PageInfo;
import com.MyDataBase.backend.transactionManager.TransactionManager;
import com.MyDataBase.backend.transactionManager.TransactionManagerImpl;
import com.MyDataBase.backend.util.Panic;
import com.MyDataBase.backend.util.Parser;
import com.google.common.primitives.Bytes;

import java.sql.SQLOutput;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

//undoLog和redoLog
public class Recover {
    private static final byte LOG_TYPE_INSERT = 0;
    private static final byte LOG_TYPE_UPDATE = 1;

    private static final int REDO = 0;
    private static final int UNDO = 1;
    static class InsertLogInfo {
        long xid;
        int pgno;
        short offset;
        byte[] raw;
    }

    static class UpdateLogInfo {
        long xid;
        int pgno;
        short offset;
        byte[] oldRaw;
        byte[] newRaw;
    }

    public static void recover(TransactionManager tm,Logger log,PageCache pc) {
        System.out.println("Recover……");
        log.rewind();
        int maxPgNo = 0;
        while (true) {
            byte[] lg = log.next();
            if (lg == null) break;
            int pgno;
            if (isInsertLog(log)) {
                InsertLogInfo li = ParseInsertLog(log);
                pgno = li.pgno;
            } else {
                UpdateLogInfo li = ParseUpdateLog(log);
                pgno = li.pgno;
            }
            if (pgno > maxPgNo) {
                maxPgNo = pgno;
            }
        }
        if (maxPgNo == 0) {
            maxPgNo = 1;
        }
        //从页面缓存截取到最大页面号
        pc.TruncateByBgNo(maxPgNo);
        System.out.println("Truncate to " + maxPgno + " pages.");

        pc.redoTransactions(tm.lg,pc);
        System.out.println("Redo transactions over ");

        pc.UndoTransactions(tm,lg,pc);
        System.out.println("Undo transactions over ");

        System.out.println("Recover over");

    }


    public static void redoTransactions(TransactionManager tm,Logger lg,PageCache pc) {
        //日志文件的读取指针重置到文件的开头
        log.rewind();

        while (true) {
            byte[] log=lg.next();
            if (log == null) break;
            if(isInsertLog(log)) {
                InsertLogInfo li = ParseInsertLog(log);//解析插入日志，提取事务 ID、页面号、偏移量和原始数据
                long xid = li.xid;
                if (!tm.isActive(xid)) {
                    doInsertLog(pc, log, REDO);
                }
            }else{
                    UpdateLogInfo xi=ParseUpdateLog(log);
                    long  xid=xi.xid;
                    if(!tm.isActive(xid)){
                        doUpdateLog(pc,log,REDO);
                    }
            }
        }


    }
    /**
     * 撤销所有活动事务的日志记录。对于每个活动事务，将日志记录缓存起来，然后按倒序进行撤销操作。
     * @param tm
     * @param lg
     * @param pc
     */
    public static void undoTransactions(TransactionManager tm,Logger lg,PageCache pc) {
        Map<Long,List<byte[]>> logCache=new HashMap<>();
        lg.rewind();

        while (true) {
            byte[] log=lg.next();
            if (log == null) break;
            if(log.isInsertLog()){//尚未提交回滚
                InsertLogInfo  li=ParseInsertLog(log);
                long xid = li.xid;
                if (tm.isActive(xid)) {
                    if(!logCache.containsKey(xid)){
                        logCache.put(xid,new ArrayList<>());
                    }
                    logCache.get(xid).add(log);
                }
            }else{
                UpdateLogInfo li=ParseUpdateLog(log);
                long  xid=li.xid;
                if(tm.isActive(xid)){
                   if(!logCache.containsKey(xid)){
                       logCache.put(xid,new ArrayList<>());
                   }
                   logCache.get(xid).add(log);
                }
            }
            //对所有active log进行倒序undo
            for (Map.Entry<Long, List<byte[]>> entry : logCache.entrySet()) {
                List<byte[]> logs=entry.getValue();
                for(int i=logs.size()-1;i>=0;i--){
                    byte[] log=logs.get(i);
                    if(log.isInsertLog()){
                        doInsertLog(pc,log,undo);
                    }else{
                        doUndoLog(pc,log,undo);
                    }
                }
                tm.abort(entry.getKey());
            }

        }
    }

    private static boolean isInsertLog(byte[] log) {
        return log[0] == LOG_TYPE_INSERT;
    }
    // [LogType] [XID] [UID] [OldRaw] [NewRaw]
    private static final int OF_TYPE = 0;
    private static final int OF_XID = OF_TYPE+1;
    private static final int OF_UPDATE_UID = OF_XID+8;
    private static final int OF_UPDATE_RAW = OF_UPDATE_UID+8;

    public static byte[] updateLog(long xid, DataItem di) {
        byte[] logType = {LOG_TYPE_UPDATE};
        byte[] xidRaw = Parser.long2Byte(xid);
        byte[] uidRaw = Parser.long2Byte(di.getUid());
        byte[] oldRaw = di.getOldRaw();
        SubArray raw = di.getRaw();
        byte[] newRaw = Arrays.copyOfRange(raw.raw, raw.start, raw.end);
        return Bytes.concat(logType, xidRaw, uidRaw, oldRaw, newRaw);
    }

    public static UpdateLogInfo parseUpdateLog(byte[] log) {
        UpdateLogInfo li=new UpdateLogInfo();
        li.xid=Parser.parseLong(Arrays.copyOfRange(log, OF_XID, OF_UPDATE_UID));
        long uid=Parser.parseLong(Arrays.copyOfRange(log,OF_UPDATE_UID,OF_UPDATE_RAW));
        li.offset=(short)(uid&((1L<<16)-1));
        uid>>>=32;
        li.pgno=(short)(uid&((1L<<32)-1));
        int length=(log.length-OF_UPDATE_RAW)/2;
        li.oldRaw= Arrays.copyOfRange(log,OF_UPDATE_RAW,OF_UPDATE_RAW+length);
        li.newRaw= Arrays.copyOfRange(log,OF_UPDATE_RAW+length,OF_UPDATE_RAW+2*length);
        return li;
    }

    private static void doUpdate(PageCache pc,byte[]log,int flag){
        int pgno;
        short offset;
        byte[] raw;
        if(flag == REDO) {
            UpdateLogInfo xi = parseUpdateLog(log);
            pgno = xi.pgno;
            offset = xi.offset;
            raw = xi.newRaw;
        } else {
            UpdateLogInfo xi = parseUpdateLog(log);
            pgno = xi.pgno;
            offset = xi.offset;
            raw = xi.oldRaw;
        }
        Page pg = null;
        try {
            pg = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        try {
            PageX.recoverUpdate(pg, raw, offset);
        } finally {
            pg.release();
        }
    }



    // [LogType] [XID] [Pgno] [Offset] [Raw]
    private static final int OF_INSERT_PGNO = OF_XID+8;
    private static final int OF_INSERT_OFFSET = OF_INSERT_PGNO+4;
    private static final int OF_INSERT_RAW = OF_INSERT_OFFSET+2;
    public static UpdateLogInfo UpdateLog(long xid,Page pg,byte[] raw) {
        byte[] logTypeRaw = {LOG_TYPE_INSERT};
        byte[] xidRaw=Parser.long2byte(xid);
        byte[] pgnoRaw=Parser.long2byte(pg.getPageNumber());
        byte[] offsetRaw = Parser.short2Byte(PageX.getFSO(pg));
        return Bytes.concat(logTypeRaw, xidRaw, pgnoRaw, offsetRaw, raw);

    }
    public static UpdateLogInfo ParseUpdateLog(byte[] log) {
        UpdateLogInfo li=new UpdateLogInfo();
        li.pgno=Parser.parserInt(Arrays.copyOfRange(log, OF_INSERT_PGNO, OF_INSERT_OFFSET));
        li.xid=Parser.parserLong(Arrays.copyOfRange(log,OF_XID,OF_INSERT_PGNO));
        li.offset=Parser.parserShort(Arrays.copyOfRange(log,OF_INSERT_OFFSET,OF_INSERT_RAW));
        li.raw=Arrays.copyOfRange(log,OF_INSERT_RAW,log.length);
        return li;
    }

    public static void doInsertLog(PageCache pc,byte[]log,int flag){
        InsertLogInfo li=ParseInsertLog(log);
        Page pg=null;
        try{
            pg=pc.getPage(li.pgno);
        }catch(Exception e){
            Panic.panic(e);
        }

        try{
            if(flag==UNDO){
                DataItem.setDataItemRawInvalid(li.raw);
            }
            PageX.recoverInsert(pg,li.raw,li.offset);

        }finally{
            pg.release();
        }
    }


}

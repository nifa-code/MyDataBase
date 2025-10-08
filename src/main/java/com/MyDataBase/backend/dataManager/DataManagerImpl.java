package com.MyDataBase.backend.dataManager;
import com.MyDataBase.backend.dataManager.DataItem.DataItemImpl;
import com.MyDataBase.backend.dataManager.logger.Logger;
import com.MyDataBase.backend.common.AbstractCache;
import com.MyDataBase.backend.dataManager.DataItem.DataItem;
import com.MyDataBase.backend.dataManager.page.Page;
import com.MyDataBase.backend.dataManager.page.PageOne;
import com.MyDataBase.backend.dataManager.page.PageX;
import com.MyDataBase.backend.dataManager.pageCache.PageCache;
import com.MyDataBase.backend.dataManager.pageIndex.PageIndex;
import com.MyDataBase.backend.dataManager.pageIndex.PageInfo;
import com.MyDataBase.backend.transactionManager.TransactionManager;
import com.MyDataBase.backend.util.Error;
import com.MyDataBase.backend.util.Panic;

/**
 *
 * 管理数据项（DataItem）的存储、读取、插入和释放等操作，同时结合了事务管理、页面缓存、日志记录等功能。
 */
public class DataManagerImpl  extends AbstractCache<DataItem> implements DataManager{
    TransactionManager tm;
    PageCache pc;
    Logger logger;
    PageIndex pIndex;
    Page pageOne;
    public DataManagerImpl( PageCache pc,Logger logger,TransactionManager tm) {
        super(0);
        this.pc = pc;
        this.logger = logger;
        this.tm = tm;
        pIndex = new PageIndex();
    }
    //读取数据项 read 方法
    @Override
    public DataItem read(long uid) throws Exception {
        DataItemImpl di=(DataItemImpl)super.get(uid);
        if(!di.isValid()){
            di.release();
            return null;
        }
        return di;
    }
    @Override
    public long insert(long xid, byte[] data) throws Exception {
        //封装数据
        byte []raw=DataItem.wrapDataItemRaw(data);
        //检查数据大小
        if(raw.length> PageX.MAX_FREE_SPACE)throw Error.DataTooLargeException;
        //查找合适页面大小
        PageInfo pi=null;
        for(int i=0;i<5;i++){
            pi=pIndex.select(raw.length);
            if(pi==null){
                break;
            }else{
                //创建新的页面将信息添加
                int pageNo=pc.newPage(PageX.initRaw());
                pIndex.add(pageNo, PageX.MAX_FREE_SPACE);
            }
        }
        if(pi == null) {
            throw Error.DatabaseBusyException;
        }

        //插入数据并记录日志
        Page pg=null;
        int freeSpace=0;
        try{
            pg=pc.getPage(pi.pageNo);
            byte[] log=Recover.insertLog(xid,pg,raw);
            logger.log(log);

            short offset=PageX.insert(pg,raw);
            pg.release();

           return Types.addressToUid(pi.pgno, offset)
        }finally{
            //将页面重新插入pIndex中
            if(pg!=null){
                pIndex.add(pi.pageNo, PageX.getFreeSpace(pg));
            }else{
                pIndex.add(pi.pageNo,freeSpace);
            }
        }
    }

    @Override
    public void close(){
        super.close();
        logger.close();
        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
    }

    //生成xid的update日志
    public void logDataItem(long xid,DataItem di){
        byte[] log=Recover.updateLog(xid,di);
        logger.log(log);
    }

    public void releaseDataItem(DataItem di){
        super.release(di.getUid());
    }


    @Override
    protected void releaseForCache(DataItem di) {
        di.page().release();
    }

    public void initPageOne(){
        int pageNo=pc.newPage(PageOne.initRaw());
        assert pageNo==1;
        try{
            pageOne=pc.getPage(pageNo);

        }catch(Exception e){
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }

    /**
     * uid 组成
     * 偏移量  占用低 16 位
     * 页面号  占用高 32 位
     * @param uid
     * @return
     * @throws Exception
     */
    @Override
    protected DataItem getForCache(long uid) throws Exception {
        short offset=(short)((1L << 16) - 1 &uid);
        uid>>>=32;
        int pgno=(int)(uid&((1L<<32)-1));
        Page pg=pc.getPage(pgno);
        return DataItem.parseDataItem(pg,offset,this);
    }
    //对数据的何种操作？
    //导入pageOne 时检查
    boolean loadCheckPageOne(){
        try{
            pageOne=pc.getPage(1);
        }catch(Exception e){
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }

    // 初始化pageIndex
    void fillPageIndex() {
        int pageNumber = pc.getPageNumber();
        for(int i = 2; i <= pageNumber; i ++) {
            Page pg = null;
            try {
                pg = pc.getPage(i);
            } catch (Exception e) {
                Panic.panic(e);
            }
            pIndex.add(pg.getPageNumber(), PageX.getFreeSpace(pg));
            pg.release();
        }
    }


}

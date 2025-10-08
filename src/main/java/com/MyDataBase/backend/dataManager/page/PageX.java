package com.MyDataBase.backend.dataManager.page;
import com.MyDataBase.backend.util.Parser;
import com.MyDataBase.backend.dataManager.pageCache.PageCache;

import java.util.Arrays;

/**
 * PageX管理普通页
 * 普通页结构
 * [FreeSpaceOffset] [Data]
 * FreeSpaceOffset: 2字节 空闲位置开始偏移
 */
public class PageX {
    private static final short OF_FREE = 0;
    private static final short OF_DATA = 2;
    public static final int MAX_FREE_SPACE = PageCache.PAGE_SIZE - OF_DATA;

    public static  byte[] initRaw(){
        byte []row=new byte[PageCache.PAGE_SIZE];
        setFSO(row,OF_DATA);
        return row;
    }

    public static void setFSO(byte []row,short ofData){
        System.arraycopy(Parser.short2Byte(ofData),0,row,OF_FREE,OF_DATA);
    }


    public static short getFSO(Page page){
        return getFSO(page.getData());
    }

    public static short getFSO(byte []raw){
        return Parser.parserShort(Arrays.copyOfRange(raw,0,2));
    }

    /**
     * 获取当前页面偏移量
     * 将数据复制到页面数据空闲位置
     * 更新空闲位置偏移量为新变量
     * 返回数据插入位置
     * @param page
     * @param raw
     * @return
     */
    public static short insert(Page page,byte[]raw){
        page.setDirty(true);
        short offset=getFSO(page);
        System.arraycopy(raw, 0, page.getData(), offset, raw.length);
        setFSO(page.getData(), (short)offset+raw.length);
        return offset;
    }

    public static int getFreeSpace(Page page){
        return PageCache.PAGE_SIZE - (int)getFSO(page.getData());
    }


    public static void recoverInsert(Page page,byte[]raw,short offset){
        page.setDirty(true);
        System.arraycopy(raw, 0, page.getData(), offset, raw.length);
        short rawFSO=getFSO(page.getData());
        if(rawFSO<offset+raw.length){
            setFSO(page.getData(), (short)offset+raw.length);
        }

    }

    /**
     * 将数据插入页面的指定位置，但不更新空闲位置偏移量。
     * @param page
     * @param raw
     * @param offset
     */
    public static void recoverUpdate(Page page,byte[]raw,short offset){
        page.setDirty(true);
        System.arraycopy(raw, 0, page.getData(), offset, raw.length);
    }




}

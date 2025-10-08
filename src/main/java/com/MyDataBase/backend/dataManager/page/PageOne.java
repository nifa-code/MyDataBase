package com.MyDataBase.backend.dataManager.page;

import com.MyDataBase.backend.dataManager.pageCache.PageCache;
import com.MyDataBase.backend.util.RandomUtils;

import java.util.Arrays;

/**
 * 特殊管理第一页
 * ValidCheck
 * db启动时给100~107字节处填入一个随机字节，db关闭时将其拷贝到108~115字节
 * 用于判断上一次数据库是否正常关闭
 * 检测页面数据是否被修改，确保数据的一致性和完整性
 */
public class PageOne {
    //一页大小
    private static final int OF_VC = 100;
    private static final int LEN_VC = 8;

    public static byte[] initRaw(){
        byte[] raw = new byte[PageCache.PAGE_SIZE];
        setVcOpen(raw);
        return raw;
    }

    public static void setVcOpen(Page page){
        page.setDirty(true);
        setVcOpen(page.getData());
    }

    public static void setVcOpen(byte[] raw){
        System.arraycopy(RandomUtils.randomBytes(LEN_VC), 0, raw, OF_VC, LEN_VC);
    }

    public static void setVcClose(Page page){
        page.setDirty(true);
        setVcClose(page.getData());
    }
    public static void setVcClose(byte[] raw){
        System.arraycopy(raw, OF_VC, raw, OF_VC+LEN_VC, LEN_VC);
    }

    public static boolean checkVc(Page pg){
        return checkVc(pg.getData());
    }
    /**
     * 检查字节数组版本是否一致
     * @param raw
     * @return
     */
    public static boolean checkVc(byte[] raw){
        return Arrays.equals(Arrays.copyOfRange(raw, OF_VC, LEN_VC+OF_VC),Arrays.copyOfRange(raw, OF_VC+LEN_VC, OF_VC+2*LEN_VC));
    }


}

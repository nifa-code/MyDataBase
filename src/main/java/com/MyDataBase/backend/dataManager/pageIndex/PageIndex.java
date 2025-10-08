package com.MyDataBase.backend.dataManager.pageIndex;

import com.MyDataBase.backend.dataManager.pageCache.PageCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 管理页面索引，将页面划分为多个区间，每个区间存储具有相似空闲空间大小的页面信息。
 */
public class PageIndex {
    // 将一页划成40个区间
    private static final int INTERVALS_NO = 40;
    private static final int THRESHOLD = PageCache.PAGE_SIZE / INTERVALS_NO;

    private Lock lock;
    private List<PageInfo>[] lists;
    @SuppressWarnings("unChecked")
    public PageIndex(){
        lock = new ReentrantLock();
        lists =new List[INTERVALS_NO+1];
        for(int i=0;i<INTERVALS_NO+1;i++){
            lists[i]=new ArrayList<>();
        }
    }
    public void add(int pageNo,int freeSpace){
        lock.lock();
        try{
            int number=freeSpace/THRESHOLD;
            lists[number].add(new PageInfo(pageNo,freeSpace));
        }finally{
            lock.unlock();
        }

    }

    /**
     * 从索引中选择一个足够大的空间
     * 计算所需空间大小所属的区间编号。
     * 从该区间开始，逐个检查后续区间，直到找到一个非空的区间。
     * 从非空区间中移除并返回第一个页面信息。
     * 使用 ReentrantLock 确保线程安全。
     */
    public PageInfo select(int spaceSize){
        lock.lock();
        try {
            int number = spaceSize / THRESHOLD;
            if (number < INTERVALS_NO) {
                number++;
            }
            while (number < INTERVALS_NO) {
                if (lists[number].size() == 0) {
                    number++;
                    continue;
                }
                return lists[number].remove(0);
            }
            return null;
        }finally{
            lock.unlock();
        }
    }

}

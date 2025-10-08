package com.MyDataBase.backend.common;
import com.MyDataBase.backend.util.Error;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractCache<T> {
    //引用计数法
    //获取缓存资源、释放缓存资源和关闭缓存
    private Lock lock;//可以直接在变量中定义为ReentrantLock吗？
    private int maxResource;
    private int count = 0 ;//缓存中初始资源个数
    private HashMap <Long,T> cache;
    private HashMap <Long,Integer> references;
    private HashMap <Long,Boolean> getting;
    //初始化引用计数器
    public AbstractCache(int maxResource) {
        this.maxResource = maxResource;
        lock = new ReentrantLock();
        cache = new HashMap<>();
        references = new HashMap<>();
        getting = new HashMap<>();
    }
    //获取缓存中的资源
    public T get(long key)  throws Exception{
        while(true) {
            lock.lock();
            if (getting.containsKey(key)) {
                //资源正在获取
                lock.unlock();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
            }

            //资源就在缓存中
            if (cache.containsKey(key)) {
                T object = cache.get(key);
                references.put(key, references.get(key) + 1);
                lock.unlock();
                return object;
            }

            //获取资源缓存不够
            if (maxResource > 0 && count == maxResource) {
                lock.unlock();
                throw Error.CacheFullException;
            }
            count++;
            references.put(key, references.get(key) + 1);
            lock.unlock();
            break;
        }
        //资源不在缓存中时
            T object = null;
            try{
                object = getForCache(key);//不在缓存中的时候获取
            }catch(Exception e){
                lock.lock();
                count--;
                getting.remove(key);
                lock.unlock();
                throw (e);
            }

        lock.lock();
        getting.remove(key);
        cache.put(key, object);
        references.put(key, 1);
        lock.unlock();
        return object;

    }
    //释放资源
    protected void release(long key){
        lock.lock();
        try{
            int reference=references.get(key)-1;
            if(reference==0) {
                //从缓存中释放
                T object = cache.get(key);
                releaseForCache(object);
                cache.remove(key);
                count--;
            }else{
                references.put(key,reference);
            }

        }finally{
            lock.unlock();
        }
    }

    //写回所有的资源
    protected void close() {
        lock.lock();
        try{
            Set<Long> keys =cache.keySet();
            //将引用存储的删除
            for (Long key : keys) {
                T object = cache.get(key);
                releaseForCache(object);
                references.remove(key);
                cache.remove(key);
            }

        }finally{
            lock.unlock();
        }

    }

    //资源不在缓存中获取行为
    protected abstract T getForCache(long key) throws Exception;

    //资源被驱逐时的写回行为
    protected abstract void releaseForCache(T object);



}

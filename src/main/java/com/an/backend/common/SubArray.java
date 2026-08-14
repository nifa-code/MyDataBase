package top.guoziyang.mydb.backend.common;

public class SubArray {
    public byte[] raw;
    public int start;
    public int end;
    public SubArray(byte[] raw, int start, int end) {
        this.raw = raw;
        this.start = start;
        this.end = end;
    }
    /*内存共享: 通过包装 byte[] raw 数组，多个 SubArray 实例可以指向同一块内存区域的不同部分，避免数据拷贝
区间管理: 使用 start 和 end 属性定义逻辑边界，限制对 raw 数组的访问范围
资源优化: 减少不必要的数组复制操作，在数据库系统中提升性能
数据封装: 提供结构化的数组访问方式，增强代码可读性和维护性*/
}

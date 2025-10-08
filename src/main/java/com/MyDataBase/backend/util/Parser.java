package com.MyDataBase.backend.util;

import java.nio.ByteBuffer;

public class Parser {
        public static long parserlong(byte []bff){
            ByteBuffer buffer = ByteBuffer.wrap(bff, 0, 8);
            return buffer.getLong();
        }


    public static byte[] long2byte(long value) {
            ByteBuffer bff=ByteBuffer.allocate(Long.SIZE/Byte.SIZE).putLong(value);
            return bff.array();
    }
    public static byte[] int2byte(int value) {
            return ByteBuffer.allocate(Integer.SIZE/Byte.SIZE).putInt(value).array();
    }
    public static byte[] short2Byte(short value) {
            return ByteBuffer.allocate(Short.SIZE/Byte.SIZE).putShort(value).array();
    }

    /**
     * 将字节数组转为int
     * @param array
     * @return
     */
    public static int parserInt(byte[] array) {
            ByteBuffer buffer = ByteBuffer.wrap(array, 0, 4);
            return buffer.getInt();
    }
    public static short parserShort(byte[] array) {
        ByteBuffer buffer = ByteBuffer.wrap(array, 0, 2);
        return buffer.getShort();
    }
}

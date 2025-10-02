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
}

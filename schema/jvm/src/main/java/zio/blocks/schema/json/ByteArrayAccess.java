/*
 * Copyright 2023 ZIO Blocks Maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.json;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

class ByteArrayAccess {
    private static final VarHandle VH_LONG =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle VH_INT =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle VH_SHORT =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle VH_LONG_REVERSED =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle VH_INT_REVERSED =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

    static void setLong(byte[] buf, int pos, long value) {
        VH_LONG.set(buf, pos, value);
    }

    static long getLong(byte[] buf, int pos) {
        return (long) VH_LONG.get(buf, pos);
    }

    static void setInt(byte[] buf, int pos, int value) {
        VH_INT.set(buf, pos, value);
    }

    static int getInt(byte[] buf, int pos) {
        return (int) VH_INT.get(buf, pos);
    }

    static void setShort(byte[] buf, int pos, short value) {
        VH_SHORT.set(buf, pos, value);
    }

    static void setLongReversed(byte[] buf, int pos, long value) {
        VH_LONG_REVERSED.set(buf, pos, value);
    }
}

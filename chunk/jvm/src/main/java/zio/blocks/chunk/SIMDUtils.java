package zio.blocks.chunk;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorMask;

public class SIMDUtils {

    public static void multiplyByTwo(int[] data, int[] target) {
        VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;
        int len = data.length;
        int upperBound = SPECIES.loopBound(len);
        int i = 0;
        for (; i < upperBound; i += SPECIES.length()) {
            IntVector v = IntVector.fromArray(SPECIES, data, i);
            v.mul(2).intoArray(target, i);
        }
        for (; i < len; i++) {
            target[i] = data[i] * 2;
        }
    }

    public static long checksumScalar(byte[] data) {
        long sum = 0;
        for (byte b : data) {
            sum += b & 0xFF;
        }
        return sum;
    }

    public static long checksumSIMD(byte[] data, int offset, int length) {
        VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;
        VectorSpecies<Short> SHORT_SPECIES = ShortVector.SPECIES_PREFERRED;

        int i = offset;
        int end = offset + length;
        int upperBound = offset + (length & ~(BYTE_SPECIES.length() - 1));

        long totalSum = 0;

        // Inner loop limit to avoid Short overflow: 255 * 256 < 65535, so 256 adds is
        // safe.
        int batchSize = 256 * BYTE_SPECIES.length();

        while (i < upperBound) {
            int batchLimit = Math.min(upperBound, i + batchSize);

            ShortVector acc1 = ShortVector.zero(SHORT_SPECIES);
            ShortVector acc2 = ShortVector.zero(SHORT_SPECIES);

            for (; i < batchLimit; i += BYTE_SPECIES.length()) {
                ByteVector v = ByteVector.fromArray(BYTE_SPECIES, data, i);
                ShortVector s1 = (ShortVector) v.convertShape(VectorOperators.ZERO_EXTEND_B2S, SHORT_SPECIES, 0);
                ShortVector s2 = (ShortVector) v.convertShape(VectorOperators.ZERO_EXTEND_B2S, SHORT_SPECIES, 1);
                acc1 = acc1.add(s1);
                acc2 = acc2.add(s2);
            }

            // Widen to IntVector to avoid signed Short overflow during reduction
            IntVector i1 = (IntVector) acc1.convertShape(VectorOperators.ZERO_EXTEND_S2I, IntVector.SPECIES_PREFERRED,
                    0);
            IntVector i2 = (IntVector) acc1.convertShape(VectorOperators.ZERO_EXTEND_S2I, IntVector.SPECIES_PREFERRED,
                    1);
            IntVector i3 = (IntVector) acc2.convertShape(VectorOperators.ZERO_EXTEND_S2I, IntVector.SPECIES_PREFERRED,
                    0);
            IntVector i4 = (IntVector) acc2.convertShape(VectorOperators.ZERO_EXTEND_S2I, IntVector.SPECIES_PREFERRED,
                    1);

            totalSum += i1.reduceLanes(VectorOperators.ADD) +
                    i2.reduceLanes(VectorOperators.ADD) +
                    i3.reduceLanes(VectorOperators.ADD) +
                    i4.reduceLanes(VectorOperators.ADD);
        }

        for (; i < end; i++) {
            totalSum += data[i] & 0xFF;
        }
        return totalSum;
    }

    public static void toUpperCaseScalar(byte[] data, byte[] target) {
        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            if (b >= 'a' && b <= 'z') {
                target[i] = (byte) (b - 32);
            } else {
                target[i] = b;
            }
        }
    }

    public static void toUpperCaseSIMD(byte[] data, byte[] target) {
        VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;
        int len = data.length;
        int upperBound = BYTE_SPECIES.loopBound(len);
        int i = 0;
        for (; i < upperBound; i += BYTE_SPECIES.length()) {
            ByteVector v = ByteVector.fromArray(BYTE_SPECIES, data, i);
            VectorMask<Byte> mask = v.compare(VectorOperators.GE, (byte) 'a')
                    .and(v.compare(VectorOperators.LE, (byte) 'z'));
            v.sub((byte) 32, mask).intoArray(target, i);
        }
        for (; i < len; i++) {
            byte b = data[i];
            if (b >= 'a' && b <= 'z') {
                target[i] = (byte) (b - 32);
            } else {
                target[i] = b;
            }
        }
    }

    public static void andSIMD(byte[] left, int leftOffset, byte[] right, int rightOffset, byte[] target,
            int targetOffset, int bytes) {
        VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;
        int upperBound = BYTE_SPECIES.loopBound(bytes);
        int i = 0;
        for (; i < upperBound; i += BYTE_SPECIES.length()) {
            ByteVector v1 = ByteVector.fromArray(BYTE_SPECIES, left, leftOffset + i);
            ByteVector v2 = ByteVector.fromArray(BYTE_SPECIES, right, rightOffset + i);
            v1.lanewise(VectorOperators.AND, v2).intoArray(target, targetOffset + i);
        }
        for (; i < bytes; i++) {
            target[targetOffset + i] = (byte) (left[leftOffset + i] & right[rightOffset + i]);
        }
    }

    public static void orSIMD(byte[] left, int leftOffset, byte[] right, int rightOffset, byte[] target,
            int targetOffset, int bytes) {
        VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;
        int upperBound = BYTE_SPECIES.loopBound(bytes);
        int i = 0;
        for (; i < upperBound; i += BYTE_SPECIES.length()) {
            ByteVector v1 = ByteVector.fromArray(BYTE_SPECIES, left, leftOffset + i);
            ByteVector v2 = ByteVector.fromArray(BYTE_SPECIES, right, rightOffset + i);
            v1.lanewise(VectorOperators.OR, v2).intoArray(target, targetOffset + i);
        }
        for (; i < bytes; i++) {
            target[targetOffset + i] = (byte) (left[leftOffset + i] | right[rightOffset + i]);
        }
    }

    public static void xorSIMD(byte[] left, int leftOffset, byte[] right, int rightOffset, byte[] target,
            int targetOffset, int bytes) {
        VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;
        int upperBound = BYTE_SPECIES.loopBound(bytes);
        int i = 0;
        for (; i < upperBound; i += BYTE_SPECIES.length()) {
            ByteVector v1 = ByteVector.fromArray(BYTE_SPECIES, left, leftOffset + i);
            ByteVector v2 = ByteVector.fromArray(BYTE_SPECIES, right, rightOffset + i);
            v1.lanewise(VectorOperators.XOR, v2).intoArray(target, targetOffset + i);
        }
        for (; i < bytes; i++) {
            target[targetOffset + i] = (byte) (left[leftOffset + i] ^ right[rightOffset + i]);
        }
    }

    public static void notSIMD(byte[] data, int dataOffset, byte[] target, int targetOffset, int bytes) {
        VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;
        int upperBound = BYTE_SPECIES.loopBound(bytes);
        int i = 0;
        for (; i < upperBound; i += BYTE_SPECIES.length()) {
            ByteVector v = ByteVector.fromArray(BYTE_SPECIES, data, dataOffset + i);
            v.not().intoArray(target, targetOffset + i);
        }
        for (; i < bytes; i++) {
            target[targetOffset + i] = (byte) (~data[dataOffset + i]);
        }
    }

    public static int findFirstSIMD(byte[] data, int offset, int length, byte target) {
        VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;
        int upperBound = BYTE_SPECIES.loopBound(length);
        int i = 0;
        for (; i < upperBound; i += BYTE_SPECIES.length()) {
            ByteVector v = ByteVector.fromArray(BYTE_SPECIES, data, offset + i);
            VectorMask<Byte> mask = v.compare(VectorOperators.EQ, target);
            if (mask.anyTrue()) {
                return i + mask.firstTrue();
            }
        }
        for (; i < length; i++) {
            if (data[offset + i] == target) {
                return i;
            }
        }
        return -1;
    }

    public static int findFirstNotSIMD(byte[] data, int offset, int length, byte target) {
        VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;
        int upperBound = BYTE_SPECIES.loopBound(length);
        int i = 0;
        for (; i < upperBound; i += BYTE_SPECIES.length()) {
            ByteVector v = ByteVector.fromArray(BYTE_SPECIES, data, offset + i);
            VectorMask<Byte> mask = v.compare(VectorOperators.NE, target);
            if (mask.anyTrue()) {
                return i + mask.firstTrue();
            }
        }
        for (; i < length; i++) {
            if (data[offset + i] != target) {
                return i;
            }
        }
        return -1;
    }

    public static boolean matchAnySIMD(byte[] data, int offset, int length, byte[] candidates) {
        VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;
        int upperBound = BYTE_SPECIES.loopBound(length);
        int i = 0;
        for (; i < upperBound; i += BYTE_SPECIES.length()) {
            ByteVector v = ByteVector.fromArray(BYTE_SPECIES, data, offset + i);
            VectorMask<Byte> mask = BYTE_SPECIES.maskAll(false);
            for (byte cand : candidates) {
                mask = mask.or(v.compare(VectorOperators.EQ, cand));
            }
            if (mask.anyTrue()) {
                return true;
            }
        }
        for (; i < length; i++) {
            byte b = data[offset + i];
            for (byte cand : candidates) {
                if (b == cand)
                    return true;
            }
        }
        return false;
    }
}

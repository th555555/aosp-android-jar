/* GENERATED SOURCE. DO NOT MODIFY. */
package com.android.org.bouncycastle.math.ec.custom.sec;

import java.math.BigInteger;
import java.security.SecureRandom;

import com.android.org.bouncycastle.math.raw.Mod;
import com.android.org.bouncycastle.math.raw.Nat;
import com.android.org.bouncycastle.math.raw.Nat512;
import com.android.org.bouncycastle.util.Pack;

/**
 * @hide This class is not part of the Android public SDK API
 */
public class SecP521R1Field
{
    // 2^521 - 1
    static final int[] P = new int[]{ 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
        0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x1FF };
    private static final int P16 = 0x1FF;

    public static void add(int[] x, int[] y, int[] z)
    {
        int c = Nat.add(16, x, y, z) + x[16] + y[16];
        if (c > P16 || (c == P16 && Nat.eq(16, z, P)))
        {
            c += Nat.inc(16, z);
            c &= P16;
        }
        z[16] = c;
    }

    public static void addOne(int[] x, int[] z)
    {
        int c = Nat.inc(16, x, z) + x[16];
        if (c > P16 || (c == P16 && Nat.eq(16, z, P)))
        {
            c += Nat.inc(16, z);
            c &= P16;
        }
        z[16] = c;
    }

    public static int[] fromBigInteger(BigInteger x)
    {
        int[] z = Nat.fromBigInteger(521, x);
        if (Nat.eq(17, z, P))
        {
            Nat.zero(17, z);
        }
        return z;
    }

    public static void half(int[] x, int[] z)
    {
        int x16 = x[16];
        int c = Nat.shiftDownBit(16, x, x16, z);
        z[16] = (x16 >>> 1) | (c >>> 23);
    }

    public static void inv(int[] x, int[] z)
    {
        Mod.checkedModOddInverse(P, x, z);
    }

    public static int isZero(int[] x)
    {
        int d = 0;
        for (int i = 0; i < 17; ++i)
        {
            d |= x[i];
        }
        d = (d >>> 1) | (d & 1);
        return (d - 1) >> 31;
    }

    public static void multiply(int[] x, int[] y, int[] z)
    {
        int[] tt = Nat.create(33);
        implMultiply(x, y, tt);
        reduce(tt, z);
    }

    public static void multiply(int[] x, int[] y, int[] z, int[] tt)
    {
        implMultiply(x, y, tt);
        reduce(tt, z);
    }

    public static void negate(int[] x, int[] z)
    {
        if (0 != isZero(x))
        {
            Nat.sub(17, P, P, z);
        }
        else
        {
            Nat.sub(17, P, x, z);
        }
    }

    public static void random(SecureRandom r, int[] z)
    {
        byte[] bb = new byte[17 * 4];
        do
        {
            r.nextBytes(bb);
            Pack.littleEndianToInt(bb, 0, z, 0, 17);
            z[16] &= P16;
        }
        while (0 == Nat.lessThan(17, z, P));
    }

    public static void randomMult(SecureRandom r, int[] z)
    {
        do
        {
            random(r, z);
        }
        while (0 != isZero(z));
    }

    public static void reduce(int[] xx, int[] z)
    {
//        assert xx[32] >>> 18 == 0;

        int xx32 = xx[32];
        int c = Nat.shiftDownBits(16, xx, 16, 9, xx32, z, 0) >>> 23;
        c += xx32 >>> 9;
        c += Nat.addTo(16, xx, z);
        if (c > P16 || (c == P16 && Nat.eq(16, z, P)))
        {
            c += Nat.inc(16, z);
            c &= P16;
        }
        z[16] = c;
    }

    public static void reduce23(int[] z)
    {
        int z16 = z[16];
        int c = Nat.addWordTo(16, z16 >>> 9, z) + (z16 & P16);
        if (c > P16 || (c == P16 && Nat.eq(16, z, P)))
        {
            c += Nat.inc(16, z);
            c &= P16;
        }
        z[16] = c;
    }

    public static void square(int[] x, int[] z)
    {
        int[] tt = Nat.create(33);
        implSquare(x, tt);
        reduce(tt, z);
    }

    public static void square(int[] x, int[] z, int[] tt)
    {
        implSquare(x, tt);
        reduce(tt, z);
    }

    public static void squareN(int[] x, int n, int[] z)
    {
//        assert n > 0;

        int[] tt = Nat.create(33);
        implSquare(x, tt);
        reduce(tt, z);

        while (--n > 0)
        {
            implSquare(z, tt);
            reduce(tt, z);
        }
    }

    public static void squareN(int[] x, int n, int[] z, int[] tt)
    {
//        assert n > 0;

        implSquare(x, tt);
        reduce(tt, z);

        while (--n > 0)
        {
            implSquare(z, tt);
            reduce(tt, z);
        }
    }

    public static void subtract(int[] x, int[] y, int[] z)
    {
        int c = Nat.sub(16, x, y, z) + x[16] - y[16];
        if (c < 0)
        {
            c += Nat.dec(16, z);
            c &= P16;
        }
        z[16] = c;
    }

    public static void twice(int[] x, int[] z)
    {
        int x16 = x[16];
        int c = Nat.shiftUpBit(16, x, x16 << 23, z) | (x16 << 1);
        z[16] = c & P16;
    }

    protected static void implMultiply(int[] x, int[] y, int[] zz)
    {
        Nat512.mul(x, y, zz);

        int x16 = x[16], y16 = y[16];
        zz[32] = Nat.mul31BothAdd(16, x16, y, y16, x, zz, 16) + (x16 * y16);
    }

    protected static void implSquare(int[] x, int[] zz)
    {
        Nat512.square(x, zz);

        int x16 = x[16];
        zz[32] = Nat.mulWordAddTo(16, x16 << 1, x, 0, zz, 16) + (x16 * x16);
    }
}

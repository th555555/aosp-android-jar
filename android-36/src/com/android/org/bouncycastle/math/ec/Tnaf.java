/* GENERATED SOURCE. DO NOT MODIFY. */
package com.android.org.bouncycastle.math.ec;

import java.math.BigInteger;

import com.android.org.bouncycastle.util.BigIntegers;

/**
 * Class holding methods for point multiplication based on the window
 * &tau;-adic nonadjacent form (WTNAF). The algorithms are based on the
 * paper "Improved Algorithms for Arithmetic on Anomalous Binary Curves"
 * by Jerome A. Solinas. The paper first appeared in the Proceedings of
 * Crypto 1997.
 */
class Tnaf
{
    private static final BigInteger MINUS_ONE = ECConstants.ONE.negate();
    private static final BigInteger MINUS_TWO = ECConstants.TWO.negate();
    private static final BigInteger MINUS_THREE = ECConstants.THREE.negate();

    /**
     * The window width of WTNAF. The standard value of 4 is slightly less
     * than optimal for running time, but keeps space requirements for
     * precomputation low. For typical curves, a value of 5 or 6 results in
     * a better running time. When changing this value, the
     * <code>&alpha;<sub>u</sub></code>'s must be computed differently, see
     * e.g. "Guide to Elliptic Curve Cryptography", Darrel Hankerson,
     * Alfred Menezes, Scott Vanstone, Springer-Verlag New York Inc., 2004,
     * p. 121-122
     */
    public static final byte WIDTH = 4;

    /**
     * The <code>&alpha;<sub>u</sub></code>'s for <code>a=0</code> as an array
     * of <code>ZTauElement</code>s.
     */
    public static final ZTauElement[] alpha0 =
    {
        null, new ZTauElement(ECConstants.ONE, ECConstants.ZERO),
        null, new ZTauElement(MINUS_THREE, MINUS_ONE),
        null, new ZTauElement(MINUS_ONE, MINUS_ONE),
        null, new ZTauElement(ECConstants.ONE, MINUS_ONE),
        null, new ZTauElement(MINUS_ONE, ECConstants.ONE),
        null, new ZTauElement(ECConstants.ONE, ECConstants.ONE),
        null, new ZTauElement(ECConstants.THREE, ECConstants.ONE),
        null, new ZTauElement(MINUS_ONE, ECConstants.ZERO),
    };

    /**
     * The <code>&alpha;<sub>u</sub></code>'s for <code>a=0</code> as an array
     * of TNAFs.
     */
    public static final byte[][] alpha0Tnaf = {
        null, {1}, null, {-1, 0, 1}, null, {1, 0, 1}, null, {-1, 0, 0, 1}
    };

    /**
     * The <code>&alpha;<sub>u</sub></code>'s for <code>a=1</code> as an array
     * of <code>ZTauElement</code>s.
     */
    public static final ZTauElement[] alpha1 =
    {
        null, new ZTauElement(ECConstants.ONE, ECConstants.ZERO),
        null, new ZTauElement(MINUS_THREE, ECConstants.ONE),
        null, new ZTauElement(MINUS_ONE, ECConstants.ONE),
        null, new ZTauElement(ECConstants.ONE, ECConstants.ONE),
        null, new ZTauElement(MINUS_ONE, MINUS_ONE),
        null, new ZTauElement(ECConstants.ONE, MINUS_ONE),
        null, new ZTauElement(ECConstants.THREE, MINUS_ONE),
        null, new ZTauElement(MINUS_ONE, ECConstants.ZERO),
    };

    /**
     * The <code>&alpha;<sub>u</sub></code>'s for <code>a=1</code> as an array
     * of TNAFs.
     */
    public static final byte[][] alpha1Tnaf = {
        null, {1}, null, {-1, 0, 1}, null, {1, 0, 1}, null, {-1, 0, 0, -1}
    };

    /**
     * Computes the norm of an element <code>&lambda;</code> of
     * <code><b>Z</b>[&tau;]</code>.
     * @param mu The parameter <code>&mu;</code> of the elliptic curve.
     * @param lambda The element <code>&lambda;</code> of
     * <code><b>Z</b>[&tau;]</code>.
     * @return The norm of <code>&lambda;</code>.
     */
    public static BigInteger norm(final byte mu, ZTauElement lambda)
    {
        // s1 = u^2
        BigInteger s1 = lambda.u.multiply(lambda.u);

        // s2 = u * v
//        BigInteger s2 = lambda.u.multiply(lambda.v);

        // s3 = 2 * v^2
//        BigInteger s3 = lambda.v.multiply(lambda.v).shiftLeft(1);

        if (mu == 1)
        {
//            return s1.add(s2).add(s3);
            return lambda.v.shiftLeft(1).add(lambda.u).multiply(lambda.v).add(s1);
        }
        else if (mu == -1)
        {
//            return s1.subtract(s2).add(s3);
            return lambda.v.shiftLeft(1).subtract(lambda.u).multiply(lambda.v).add(s1);
        }
        else
        {
            throw new IllegalArgumentException("mu must be 1 or -1");
        }
    }

    /**
     * Computes the norm of an element <code>&lambda;</code> of
     * <code><b>R</b>[&tau;]</code>, where <code>&lambda; = u + v&tau;</code>
     * and <code>u</code> and <code>u</code> are real numbers (elements of
     * <code><b>R</b></code>). 
     * @param mu The parameter <code>&mu;</code> of the elliptic curve.
     * @param u The real part of the element <code>&lambda;</code> of
     * <code><b>R</b>[&tau;]</code>.
     * @param v The <code>&tau;</code>-adic part of the element
     * <code>&lambda;</code> of <code><b>R</b>[&tau;]</code>.
     * @return The norm of <code>&lambda;</code>.
     */
    public static SimpleBigDecimal norm(final byte mu, SimpleBigDecimal u,
            SimpleBigDecimal v)
    {
        SimpleBigDecimal norm;

        // s1 = u^2
        SimpleBigDecimal s1 = u.multiply(u);

        // s2 = u * v
        SimpleBigDecimal s2 = u.multiply(v);

        // s3 = 2 * v^2
        SimpleBigDecimal s3 = v.multiply(v).shiftLeft(1);

        if (mu == 1)
        {
            norm = s1.add(s2).add(s3);
        }
        else if (mu == -1)
        {
            norm = s1.subtract(s2).add(s3);
        }
        else
        {
            throw new IllegalArgumentException("mu must be 1 or -1");
        }

        return norm;
    }

    /**
     * Rounds an element <code>&lambda;</code> of <code><b>R</b>[&tau;]</code>
     * to an element of <code><b>Z</b>[&tau;]</code>, such that their difference
     * has minimal norm. <code>&lambda;</code> is given as
     * <code>&lambda; = &lambda;<sub>0</sub> + &lambda;<sub>1</sub>&tau;</code>.
     * @param lambda0 The component <code>&lambda;<sub>0</sub></code>.
     * @param lambda1 The component <code>&lambda;<sub>1</sub></code>.
     * @param mu The parameter <code>&mu;</code> of the elliptic curve. Must
     * equal 1 or -1.
     * @return The rounded element of <code><b>Z</b>[&tau;]</code>.
     * @throws IllegalArgumentException if <code>lambda0</code> and
     * <code>lambda1</code> do not have same scale.
     */
    public static ZTauElement round(SimpleBigDecimal lambda0,
            SimpleBigDecimal lambda1, byte mu)
    {
        int scale = lambda0.getScale();
        if (lambda1.getScale() != scale)
        {
            throw new IllegalArgumentException("lambda0 and lambda1 do not " +
                    "have same scale");
        }

        if (!((mu == 1) || (mu == -1)))
        {
            throw new IllegalArgumentException("mu must be 1 or -1");
        }

        BigInteger f0 = lambda0.round();
        BigInteger f1 = lambda1.round();

        SimpleBigDecimal eta0 = lambda0.subtract(f0);
        SimpleBigDecimal eta1 = lambda1.subtract(f1);

        // eta = 2*eta0 + mu*eta1
        SimpleBigDecimal eta = eta0.add(eta0);
        if (mu == 1)
        {
            eta = eta.add(eta1);
        }
        else
        {
            // mu == -1
            eta = eta.subtract(eta1);
        }

        // check1 = eta0 - 3*mu*eta1
        // check2 = eta0 + 4*mu*eta1
        SimpleBigDecimal threeEta1 = eta1.add(eta1).add(eta1);
        SimpleBigDecimal fourEta1 = threeEta1.add(eta1);
        SimpleBigDecimal check1;
        SimpleBigDecimal check2;
        if (mu == 1)
        {
            check1 = eta0.subtract(threeEta1);
            check2 = eta0.add(fourEta1);
        }
        else
        {
            // mu == -1
            check1 = eta0.add(threeEta1);
            check2 = eta0.subtract(fourEta1);
        }

        byte h0 = 0;
        byte h1 = 0;

        // if eta >= 1
        if (eta.compareTo(ECConstants.ONE) >= 0)
        {
            if (check1.compareTo(MINUS_ONE) < 0)
            {
                h1 = mu;
            }
            else
            {
                h0 = 1;
            }
        }
        else
        {
            // eta < 1
            if (check2.compareTo(ECConstants.TWO) >= 0)
            {
                h1 = mu;
            }
        }

        // if eta < -1
        if (eta.compareTo(MINUS_ONE) < 0)
        {
            if (check1.compareTo(ECConstants.ONE) >= 0)
            {
                h1 = (byte)-mu;
            }
            else
            {
                h0 = -1;
            }
        }
        else
        {
            // eta >= -1
            if (check2.compareTo(MINUS_TWO) < 0)
            {
                h1 = (byte)-mu;
            }
        }

        BigInteger q0 = f0.add(BigInteger.valueOf(h0));
        BigInteger q1 = f1.add(BigInteger.valueOf(h1));
        return new ZTauElement(q0, q1);
    }

    /**
     * Approximate division by <code>n</code>. For an integer
     * <code>k</code>, the value <code>&lambda; = s k / n</code> is
     * computed to <code>c</code> bits of accuracy.
     * @param k The parameter <code>k</code>.
     * @param s The curve parameter <code>s<sub>0</sub></code> or
     * <code>s<sub>1</sub></code>.
     * @param vm The Lucas Sequence element <code>V<sub>m</sub></code>.
     * @param a The parameter <code>a</code> of the elliptic curve.
     * @param m The bit length of the finite field
     * <code><b>F</b><sub>m</sub></code>.
     * @param c The number of bits of accuracy, i.e. the scale of the returned
     * <code>SimpleBigDecimal</code>.
     * @return The value <code>&lambda; = s k / n</code> computed to
     * <code>c</code> bits of accuracy.
     */
    public static SimpleBigDecimal approximateDivisionByN(BigInteger k,
            BigInteger s, BigInteger vm, byte a, int m, int c)
    {
        int _k = (m + 5)/2 + c;
        BigInteger ns = k.shiftRight(m - _k - 2 + a);

        BigInteger gs = s.multiply(ns);

        BigInteger hs = gs.shiftRight(m);

        BigInteger js = vm.multiply(hs);

        BigInteger gsPlusJs = gs.add(js);
        BigInteger ls = gsPlusJs.shiftRight(_k-c);
        if (gsPlusJs.testBit(_k-c-1))
        {
            // round up
            ls = ls.add(ECConstants.ONE);
        }

        return new SimpleBigDecimal(ls, c);
    }

    /**
     * Computes the <code>&tau;</code>-adic NAF (non-adjacent form) of an
     * element <code>&lambda;</code> of <code><b>Z</b>[&tau;]</code>.
     * @param mu The parameter <code>&mu;</code> of the elliptic curve.
     * @param lambda The element <code>&lambda;</code> of
     * <code><b>Z</b>[&tau;]</code>.
     * @return The <code>&tau;</code>-adic NAF of <code>&lambda;</code>.
     */
    public static byte[] tauAdicNaf(byte mu, ZTauElement lambda)
    {
        if (!((mu == 1) || (mu == -1)))
        {
            throw new IllegalArgumentException("mu must be 1 or -1");
        }
        
        BigInteger norm = norm(mu, lambda);

        // Ceiling of log2 of the norm 
        int log2Norm = norm.bitLength();

        // If length(TNAF) > 30, then length(TNAF) < log2Norm + 3.52
        int maxLength = log2Norm > 30 ? log2Norm + 4 : 34;

        // The array holding the TNAF
        byte[] u = new byte[maxLength];
        int i = 0;

        // The actual length of the TNAF
        int length = 0;

        BigInteger r0 = lambda.u;
        BigInteger r1 = lambda.v;

        while(!((r0.equals(ECConstants.ZERO)) && (r1.equals(ECConstants.ZERO))))
        {
            // If r0 is odd
            if (r0.testBit(0))
            {
                u[i] = (byte) ECConstants.TWO.subtract((r0.subtract(r1.shiftLeft(1))).mod(ECConstants.FOUR)).intValue();

                // r0 = r0 - u[i]
                if (u[i] == 1)
                {
                    r0 = r0.clearBit(0);
                }
                else
                {
                    // u[i] == -1
                    r0 = r0.add(ECConstants.ONE);
                }
                length = i;
            }
            else
            {
                u[i] = 0;
            }

            BigInteger t = r0;
            BigInteger s = r0.shiftRight(1);
            if (mu == 1)
            {
                r0 = r1.add(s);
            }
            else
            {
                // mu == -1
                r0 = r1.subtract(s);
            }

            r1 = t.shiftRight(1).negate();
            i++;
        }

        length++;

        // Reduce the TNAF array to its actual length
        byte[] tnaf = new byte[length];
        System.arraycopy(u, 0, tnaf, 0, length);
        return tnaf;
    }

    /**
     * Applies the operation <code>&tau;()</code> to an
     * <code>ECPoint.AbstractF2m</code>. 
     * @param p The ECPoint.AbstractF2m to which <code>&tau;()</code> is applied.
     * @return <code>&tau;(p)</code>
     */
    public static ECPoint.AbstractF2m tau(ECPoint.AbstractF2m p)
    {
        return p.tau();
    }

    /**
     * Returns the parameter <code>&mu;</code> of the elliptic curve.
     * @param curve The elliptic curve from which to obtain <code>&mu;</code>.
     * The curve must be a Koblitz curve, i.e. <code>a</code> equals
     * <code>0</code> or <code>1</code> and <code>b</code> equals
     * <code>1</code>. 
     * @return <code>&mu;</code> of the elliptic curve.
     * @throws IllegalArgumentException if the given ECCurve is not a Koblitz
     * curve.
     */
    public static byte getMu(ECCurve.AbstractF2m curve)
    {
        if (!curve.isKoblitz())
        {
            throw new IllegalArgumentException("No Koblitz curve (ABC), TNAF multiplication not possible");
        }

        if (curve.getA().isZero())
        {
            return -1;
        }

        return 1;
    }

    public static byte getMu(ECFieldElement curveA)
    {
        return (byte)(curveA.isZero() ? -1 : 1);
    }

    public static byte getMu(int curveA)
    {
        return (byte)(curveA == 0 ? -1 : 1);
    }

    /**
     * Calculates the Lucas Sequence elements <code>U<sub>k-1</sub></code> and
     * <code>U<sub>k</sub></code> or <code>V<sub>k-1</sub></code> and
     * <code>V<sub>k</sub></code>.
     * @param mu The parameter <code>&mu;</code> of the elliptic curve.
     * @param k The index of the second element of the Lucas Sequence to be
     * returned.
     * @param doV If set to true, computes <code>V<sub>k-1</sub></code> and
     * <code>V<sub>k</sub></code>, otherwise <code>U<sub>k-1</sub></code> and
     * <code>U<sub>k</sub></code>.
     * @return An array with 2 elements, containing <code>U<sub>k-1</sub></code>
     * and <code>U<sub>k</sub></code> or <code>V<sub>k-1</sub></code>
     * and <code>V<sub>k</sub></code>.
     */
    public static BigInteger[] getLucas(byte mu, int k, boolean doV)
    {
        if (!((mu == 1) || (mu == -1)))
        {
            throw new IllegalArgumentException("mu must be 1 or -1");
        }

        BigInteger u0, u1, u2;
        if (doV)
        {
            u0 = ECConstants.TWO;
            u1 = BigInteger.valueOf(mu);
        }
        else
        {
            u0 = ECConstants.ZERO;
            u1 = ECConstants.ONE;
        }

        for (int i = 1; i < k; i++)
        {
            // u2 = mu*u1 - 2*u0;
            BigInteger s = u1;
            if (mu < 0)
            {
                s = s.negate();
            }

            u2 = s.subtract(u0.shiftLeft(1));
            u0 = u1;
            u1 = u2;
        }

        return new BigInteger[]{ u0, u1 };
    }

    /**
     * Computes the auxiliary value <code>t<sub>w</sub></code>. If the width is
     * 4, then for <code>mu = 1</code>, <code>t<sub>w</sub> = 6</code> and for
     * <code>mu = -1</code>, <code>t<sub>w</sub> = 10</code> 
     * @param mu The parameter <code>&mu;</code> of the elliptic curve.
     * @param w The window width of the WTNAF.
     * @return the auxiliary value <code>t<sub>w</sub></code>
     */
    public static BigInteger getTw(byte mu, int w)
    {
        if (w == 4)
        {
            if (mu == 1)
            {
                return BigInteger.valueOf(6);
            }
            else
            {
                // mu == -1
                return BigInteger.valueOf(10);
            }
        }
        else
        {
            // For w <> 4, the values must be computed
            BigInteger[] us = getLucas(mu, w, false);
            BigInteger twoToW = ECConstants.ZERO.setBit(w);
            BigInteger u1invert = us[1].modInverse(twoToW);
            return us[0].shiftLeft(1).multiply(u1invert).mod(twoToW);
        }
    }

    /**
     * Computes the auxiliary values <code>s<sub>0</sub></code> and
     * <code>s<sub>1</sub></code> used for partial modular reduction. 
     * @param curve The elliptic curve for which to compute
     * <code>s<sub>0</sub></code> and <code>s<sub>1</sub></code>.
     * @throws IllegalArgumentException if <code>curve</code> is not a
     * Koblitz curve (Anomalous Binary Curve, ABC).
     */
    public static BigInteger[] getSi(ECCurve.AbstractF2m curve)
    {
        if (!curve.isKoblitz())
        {
            throw new IllegalArgumentException("si is defined for Koblitz curves only");
        }

        return getSi(curve.getFieldSize(), curve.getA().toBigInteger().intValue(), curve.getCofactor());
    }

    public static BigInteger[] getSi(int fieldSize, int curveA, BigInteger cofactor)
    {
        byte mu = getMu(curveA);
        int shifts = getShiftsForCofactor(cofactor);
        int index = fieldSize + 3 - curveA;
        BigInteger[] ui = getLucas(mu, index, false);
        if (mu == 1)
        {
            ui[0] = ui[0].negate();
            ui[1] = ui[1].negate();
        }

        BigInteger dividend0 = ECConstants.ONE.add(ui[1]).shiftRight(shifts);
        BigInteger dividend1 = ECConstants.ONE.add(ui[0]).shiftRight(shifts).negate();

        return new BigInteger[] { dividend0, dividend1 };
    }

    protected static int getShiftsForCofactor(BigInteger h)
    {
        if (h != null)
        {
            if (h.equals(ECConstants.TWO))
            {
                return 1;
            }
            if (h.equals(ECConstants.FOUR))
            {
                return 2;
            }
        }

        throw new IllegalArgumentException("h (Cofactor) must be 2 or 4");
    }

    /**
     * Partial modular reduction modulo
     * <code>(&tau;<sup>m</sup> - 1)/(&tau; - 1)</code>.
     * @param k The integer to be reduced.
     * @param m The bitlength of the underlying finite field.
     * @param a The parameter <code>a</code> of the elliptic curve.
     * @param s The auxiliary values <code>s<sub>0</sub></code> and
     * <code>s<sub>1</sub></code>.
     * @param mu The parameter &mu; of the elliptic curve.
     * @param c The precision (number of bits of accuracy) of the partial
     * modular reduction.
     * @return <code>&rho; := k partmod (&tau;<sup>m</sup> - 1)/(&tau; - 1)</code>
     */
    public static ZTauElement partModReduction(ECCurve.AbstractF2m curve, BigInteger k, byte a, byte mu, byte c)
    {
        int m = curve.getFieldSize();
        BigInteger[] s = curve.getSi();

        // d0 = s[0] + mu*s[1]; mu is either 1 or -1
        BigInteger d0;
        if (mu == 1)
        {
            d0 = s[0].add(s[1]);
        }
        else
        {
            d0 = s[0].subtract(s[1]);
        }

        BigInteger vm;
        if (curve.isKoblitz())
        {
            /*
             * Jerome A. Solinas, "Improved Algorithms for Arithmetic on Anomalous Binary Curves", (21).
             */
            vm = ECConstants.ONE.shiftLeft(m).add(ECConstants.ONE).subtract(
                curve.getOrder().multiply(curve.getCofactor()));
        }
        else
        {
            BigInteger[] v = getLucas(mu, m, true);
            vm = v[1];
        }

        SimpleBigDecimal lambda0 = approximateDivisionByN(k, s[0], vm, a, m, c);
        SimpleBigDecimal lambda1 = approximateDivisionByN(k, s[1], vm, a, m, c);

        ZTauElement q = round(lambda0, lambda1, mu);

        // r0 = n - d0*q0 - 2*s1*q1
        BigInteger r0 = k.subtract(d0.multiply(q.u)).subtract(
            s[1].multiply(q.v).shiftLeft(1));

        // r1 = s1*q0 - s0*q1
        BigInteger r1 = s[1].multiply(q.u).subtract(s[0].multiply(q.v));
        
        return new ZTauElement(r0, r1);
    }

    /**
     * Multiplies a {@link com.android.org.bouncycastle.math.ec.ECPoint.AbstractF2m ECPoint.AbstractF2m}
     * by a <code>BigInteger</code> using the reduced <code>&tau;</code>-adic
     * NAF (RTNAF) method.
     * @param p The ECPoint.AbstractF2m to multiply.
     * @param k The <code>BigInteger</code> by which to multiply <code>p</code>.
     * @return <code>k * p</code>
     */
    public static ECPoint.AbstractF2m multiplyRTnaf(ECPoint.AbstractF2m p, BigInteger k)
    {
        ECCurve.AbstractF2m curve = (ECCurve.AbstractF2m) p.getCurve();
        int a = curve.getA().toBigInteger().intValue();
        byte mu = getMu(a);

        ZTauElement rho = partModReduction(curve, k, (byte)a, mu, (byte)10);

        return multiplyTnaf(p, rho);
    }

    /**
     * Multiplies a {@link com.android.org.bouncycastle.math.ec.ECPoint.AbstractF2m ECPoint.AbstractF2m}
     * by an element <code>&lambda;</code> of <code><b>Z</b>[&tau;]</code>
     * using the <code>&tau;</code>-adic NAF (TNAF) method.
     * @param p The ECPoint.AbstractF2m to multiply.
     * @param lambda The element <code>&lambda;</code> of
     * <code><b>Z</b>[&tau;]</code>.
     * @return <code>&lambda; * p</code>
     */
    public static ECPoint.AbstractF2m multiplyTnaf(ECPoint.AbstractF2m p, ZTauElement lambda)
    {
        ECCurve.AbstractF2m curve = (ECCurve.AbstractF2m)p.getCurve();
        ECPoint.AbstractF2m pNeg = (ECPoint.AbstractF2m)p.negate();
        byte mu = getMu(curve.getA());
        byte[] u = tauAdicNaf(mu, lambda);

        return multiplyFromTnaf(p, pNeg, u);
    }

    /**
    * Multiplies a {@link com.android.org.bouncycastle.math.ec.ECPoint.AbstractF2m ECPoint.AbstractF2m}
    * by an element <code>&lambda;</code> of <code><b>Z</b>[&tau;]</code>
    * using the <code>&tau;</code>-adic NAF (TNAF) method, given the TNAF
    * of <code>&lambda;</code>.
    * @param p The ECPoint.AbstractF2m to multiply.
    * @param u The the TNAF of <code>&lambda;</code>..
    * @return <code>&lambda; * p</code>
    */
    public static ECPoint.AbstractF2m multiplyFromTnaf(ECPoint.AbstractF2m p, ECPoint.AbstractF2m pNeg, byte[] u)
    {
        ECCurve curve = p.getCurve();
        ECPoint.AbstractF2m q = (ECPoint.AbstractF2m)curve.getInfinity();
        int tauCount = 0;
        for (int i = u.length - 1; i >= 0; i--)
        {
            ++tauCount;
            byte ui = u[i];
            if (ui != 0)
            {
                q = q.tauPow(tauCount);
                tauCount = 0;

                ECPoint x = ui > 0 ? p : pNeg;
                q = (ECPoint.AbstractF2m)q.add(x);
            }
        }
        if (tauCount > 0)
        {
            q = q.tauPow(tauCount);
        }
        return q;
    }

    /**
     * Computes the <code>[&tau;]</code>-adic window NAF of an element
     * <code>&lambda;</code> of <code><b>Z</b>[&tau;]</code>.
     * @param mu The parameter &mu; of the elliptic curve.
     * @param lambda The element <code>&lambda;</code> of
     * <code><b>Z</b>[&tau;]</code> of which to compute the
     * <code>[&tau;]</code>-adic NAF.
     * @param width The window width of the resulting WNAF.
     * @param pow2w 2<sup>width</sup>.
     * @param tw The auxiliary value <code>t<sub>w</sub></code>.
     * @param alpha The <code>&alpha;<sub>u</sub></code>'s for the window width.
     * @return The <code>[&tau;]</code>-adic window NAF of
     * <code>&lambda;</code>.
     */
    public static byte[] tauAdicWNaf(byte mu, ZTauElement lambda, int width, int tw, ZTauElement[] alpha)
    {
        if (!(mu == 1 || mu == -1))
        {
            throw new IllegalArgumentException("mu must be 1 or -1");
        }

        BigInteger norm = norm(mu, lambda);

        // Ceiling of log2 of the norm 
        int log2Norm = norm.bitLength();

        // If length(TNAF) > 30, then length(TNAF) < log2Norm + 3.52
        int maxLength = log2Norm > 30 ? log2Norm + 4 + width : 34 + width;

        // The array holding the TNAF
        byte[] u = new byte[maxLength];

        int pow2Width = 1 << width;
        int pow2Mask = pow2Width - 1;
        int s = 32 - width;

        // Split lambda into two BigIntegers to simplify calculations
        BigInteger R0 = lambda.u;
        BigInteger R1 = lambda.v;
        int uPos = 0;

        // while lambda <> (0, 0)
        while (R0.bitLength() > 62 || R1.bitLength() > 62)
        {
            if (R0.testBit(0))
            {
                int uVal = R0.intValue() + (R1.intValue() * tw);
                int alphaPos = uVal & pow2Mask;

                u[uPos] = (byte)((uVal << s) >> s);
                R0 = R0.subtract(alpha[alphaPos].u);
                R1 = R1.subtract(alpha[alphaPos].v);
            }

            ++uPos;

            BigInteger t = R0.shiftRight(1);
            if (mu == 1)
            {
                R0 = R1.add(t);
            }
            else // mu == -1
            {
                R0 = R1.subtract(t);
            }
            R1 = t.negate();
        }

        long r0_64 = BigIntegers.longValueExact(R0);
        long r1_64 = BigIntegers.longValueExact(R1);

        // while lambda <> (0, 0)
        while ((r0_64 | r1_64) != 0L)
        {
            if ((r0_64 & 1L) != 0L)
            {
                int uVal = (int)r0_64 + ((int)r1_64 * tw);
                int alphaPos = uVal & pow2Mask;

                u[uPos] = (byte)((uVal << s) >> s);
                r0_64 -= alpha[alphaPos].u.intValue();
                r1_64 -= alpha[alphaPos].v.intValue();
            }

            ++uPos;

            long t_64 = r0_64 >> 1;
            if (mu == 1)
            {
                r0_64 = r1_64 + t_64;
            }
            else // mu == -1
            {
                r0_64 = r1_64 - t_64;
            }
            r1_64 = -t_64;
        }
        
        return u;
    }

    /**
     * Does the precomputation for WTNAF multiplication.
     * @param p The <code>ECPoint</code> for which to do the precomputation.
     * @param a The parameter <code>a</code> of the elliptic curve.
     * @return The precomputation array for <code>p</code>. 
     */
    public static ECPoint.AbstractF2m[] getPreComp(ECPoint.AbstractF2m p, byte a)
    {
        ECPoint.AbstractF2m pNeg = (ECPoint.AbstractF2m)p.negate();
        byte[][] alphaTnaf = (a == 0) ? Tnaf.alpha0Tnaf : Tnaf.alpha1Tnaf;

        ECPoint.AbstractF2m[] pu = new ECPoint.AbstractF2m[(alphaTnaf.length + 1) >>> 1];
        pu[0] = p;

        int precompLen = alphaTnaf.length;
        for (int i = 3; i < precompLen; i += 2)
        {
            pu[i >>> 1] = Tnaf.multiplyFromTnaf(p, pNeg, alphaTnaf[i]);
        }

        p.getCurve().normalizeAll(pu);

        return pu;
    }
}

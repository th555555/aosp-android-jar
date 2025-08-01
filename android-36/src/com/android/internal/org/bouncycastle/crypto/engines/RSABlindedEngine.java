/* GENERATED SOURCE. DO NOT MODIFY. */
package com.android.internal.org.bouncycastle.crypto.engines;

import java.math.BigInteger;
import java.security.SecureRandom;

import com.android.internal.org.bouncycastle.crypto.AsymmetricBlockCipher;
import com.android.internal.org.bouncycastle.crypto.CipherParameters;
import com.android.internal.org.bouncycastle.crypto.CryptoServicesRegistrar;
import com.android.internal.org.bouncycastle.crypto.DataLengthException;
import com.android.internal.org.bouncycastle.crypto.params.ParametersWithRandom;
import com.android.internal.org.bouncycastle.crypto.params.RSAKeyParameters;
import com.android.internal.org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import com.android.internal.org.bouncycastle.util.BigIntegers;

/**
 * this does your basic RSA algorithm with blinding
 * @hide This class is not part of the Android public SDK API
 */
public class RSABlindedEngine
    implements AsymmetricBlockCipher
{
    private static final BigInteger ONE = BigInteger.valueOf(1);

    private RSACoreEngine    core = new RSACoreEngine();
    private RSAKeyParameters key;
    private SecureRandom     random;

    /**
     * initialise the RSA engine.
     *
     * @param forEncryption true if we are encrypting, false otherwise.
     * @param param the necessary RSA key parameters.
     */
    public void init(
        boolean             forEncryption,
        CipherParameters    param)
    {
        core.init(forEncryption, param);

        if (param instanceof ParametersWithRandom)
        {
            ParametersWithRandom rParam = (ParametersWithRandom)param;

            this.key = (RSAKeyParameters)rParam.getParameters();

            if (key instanceof RSAPrivateCrtKeyParameters)
            {
                this.random = rParam.getRandom();
            }
            else
            {
                this.random = null;
            }
        }
        else
        {
            this.key = (RSAKeyParameters)param;

            if (key instanceof RSAPrivateCrtKeyParameters)
            {
                this.random = CryptoServicesRegistrar.getSecureRandom();
            }
            else
            {
                this.random = null;
            }
        }
    }

    /**
     * Return the maximum size for an input block to this engine.
     * For RSA this is always one byte less than the key size on
     * encryption, and the same length as the key size on decryption.
     *
     * @return maximum size for an input block.
     */
    public int getInputBlockSize()
    {
        return core.getInputBlockSize();
    }

    /**
     * Return the maximum size for an output block to this engine.
     * For RSA this is always one byte less than the key size on
     * decryption, and the same length as the key size on encryption.
     *
     * @return maximum size for an output block.
     */
    public int getOutputBlockSize()
    {
        return core.getOutputBlockSize();
    }

    /**
     * Process a single block using the basic RSA algorithm.
     *
     * @param in the input array.
     * @param inOff the offset into the input buffer where the data starts.
     * @param inLen the length of the data to be processed.
     * @return the result of the RSA process.
     * @exception DataLengthException the input block is too large.
     */
    public byte[] processBlock(
        byte[]  in,
        int     inOff,
        int     inLen)
    {
        if (key == null)
        {
            throw new IllegalStateException("RSA engine not initialised");
        }

        BigInteger input = core.convertInput(in, inOff, inLen);
        BigInteger result = processInput(input);
        return core.convertOutput(result);
    }

    private BigInteger processInput(BigInteger input)
    {
        if (key instanceof RSAPrivateCrtKeyParameters)
        {
            RSAPrivateCrtKeyParameters crtKey = (RSAPrivateCrtKeyParameters)key;

            BigInteger e = crtKey.getPublicExponent();
            if (e != null)   // can't do blinding without a public exponent
            {
                BigInteger m = crtKey.getModulus();

                BigInteger r = BigIntegers.createRandomInRange(ONE, m.subtract(ONE), random);
                BigInteger blind = r.modPow(e, m);
                BigInteger unblind = BigIntegers.modOddInverse(m, r);

                BigInteger blindedInput = blind.multiply(input).mod(m);
                BigInteger blindedResult = core.processBlock(blindedInput);
                return unblind.multiply(blindedResult).mod(m);
            }
        }

        return core.processBlock(input);
    }
}

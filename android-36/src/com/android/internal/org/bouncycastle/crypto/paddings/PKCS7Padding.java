/* GENERATED SOURCE. DO NOT MODIFY. */
package com.android.internal.org.bouncycastle.crypto.paddings;

import java.security.SecureRandom;

import com.android.internal.org.bouncycastle.crypto.InvalidCipherTextException;

/**
 * A padder that adds PKCS7/PKCS5 padding to a block.
 * @hide This class is not part of the Android public SDK API
 */
public class PKCS7Padding
    implements BlockCipherPadding
{
    /**
     * Initialise the padder.
     *
     * @param random - a SecureRandom if available.
     */
    public void init(SecureRandom random)
        throws IllegalArgumentException
    {
        // nothing to do.
    }

    /**
     * Return the name of the algorithm the padder implements.
     *
     * @return the name of the algorithm the padder implements.
     */
    public String getPaddingName()
    {
        return "PKCS7";
    }

    /**
     * add the pad bytes to the passed in block, returning the
     * number of bytes added.
     */
    public int addPadding(
        byte[]  in,
        int     inOff)
    {
        byte code = (byte)(in.length - inOff);

        while (inOff < in.length)
        {
            in[inOff] = code;
            inOff++;
        }

        return code;
    }

    /**
     * return the number of pad bytes present in the block.
     */
    public int padCount(byte[] in)
        throws InvalidCipherTextException
    {
        byte countAsByte = in[in.length - 1];
        int count = countAsByte & 0xFF;
        int position = in.length - count;

        int failed = (position | (count - 1)) >> 31;
        for (int i = 0; i < in.length; ++i)
        {
            failed |= (in[i] ^ countAsByte) & ~((i - position) >> 31);
        }
        if (failed != 0)
        {
            throw new InvalidCipherTextException("pad block corrupted");
        }

        return count;
    }
}

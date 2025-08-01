/* GENERATED SOURCE. DO NOT MODIFY. */
package com.android.org.bouncycastle.jcajce.provider.asymmetric.x509;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
// Android-added: Use PushbackInputStream
import java.io.PushbackInputStream;
import java.security.cert.CRL;
import java.security.cert.CRLException;
import java.security.cert.CertPath;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactorySpi;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.android.org.bouncycastle.asn1.ASN1InputStream;
import com.android.org.bouncycastle.asn1.ASN1ObjectIdentifier;
import com.android.org.bouncycastle.asn1.ASN1Sequence;
import com.android.org.bouncycastle.asn1.ASN1Set;
import com.android.org.bouncycastle.asn1.ASN1TaggedObject;
import com.android.org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import com.android.org.bouncycastle.asn1.pkcs.SignedData;
import com.android.org.bouncycastle.asn1.x509.Certificate;
import com.android.org.bouncycastle.asn1.x509.CertificateList;
import com.android.org.bouncycastle.jcajce.util.BCJcaJceHelper;
import com.android.org.bouncycastle.jcajce.util.JcaJceHelper;
import com.android.org.bouncycastle.util.io.Streams;

/**
 * class for dealing with X509 certificates.
 * <p>
 * At the moment this will deal with "-----BEGIN CERTIFICATE-----" to "-----END CERTIFICATE-----"
 * base 64 encoded certs, as well as the BER binaries of certificates and some classes of PKCS#7
 * objects.
 * @hide This class is not part of the Android public SDK API
 */
public class CertificateFactory
    extends CertificateFactorySpi
{
    private final JcaJceHelper bcHelper = new BCJcaJceHelper();

    private static final PEMUtil PEM_CERT_PARSER = new PEMUtil("CERTIFICATE");
    private static final PEMUtil PEM_CRL_PARSER = new PEMUtil("CRL");
    private static final PEMUtil PEM_PKCS7_PARSER = new PEMUtil("PKCS7");

    private ASN1Set sData = null;
    private int                sDataObjectCount = 0;
    private InputStream currentStream = null;
    
    private ASN1Set sCrlData = null;
    private int                sCrlDataObjectCount = 0;
    private InputStream currentCrlStream = null;

    private java.security.cert.Certificate readDERCertificate(
        ASN1InputStream dIn)
        throws IOException, CertificateParsingException
    {
        return getCertificate(ASN1Sequence.getInstance(dIn.readObject()));
    }

    private java.security.cert.Certificate readPEMCertificate(
        InputStream in,
        boolean isFirst)
        throws IOException, CertificateParsingException
    {
        return getCertificate(PEM_CERT_PARSER.readPEMObject(in, isFirst));
    }

    private java.security.cert.Certificate getCertificate(ASN1Sequence seq)
        throws CertificateParsingException
    {
        if (seq == null)
        {
            return null;
        }
        
        if (seq.size() > 1
                && seq.getObjectAt(0) instanceof ASN1ObjectIdentifier)
        {
            if (seq.getObjectAt(0).equals(PKCSObjectIdentifiers.signedData))
            {
                sData = SignedData.getInstance(ASN1Sequence.getInstance(
                    (ASN1TaggedObject)seq.getObjectAt(1), true)).getCertificates();

                return getCertificate();
            }
        }

        return new X509CertificateObject(bcHelper,
                            Certificate.getInstance(seq));
    }

    private java.security.cert.Certificate getCertificate()
        throws CertificateParsingException
    {
        if (sData != null)
        {
            while (sDataObjectCount < sData.size())
            {
                Object obj = sData.getObjectAt(sDataObjectCount++);

                if (obj instanceof ASN1Sequence)
                {
                   return new X509CertificateObject(bcHelper,
                                    Certificate.getInstance(obj));
                }
            }
        }

        return null;
    }


    protected CRL createCRL(CertificateList c)
        throws CRLException
    {
        return new X509CRLObject(bcHelper, c);
    }
    
    private CRL readPEMCRL(
        InputStream in,
        boolean isFirst)
        throws IOException, CRLException
    {
        return getCRL(PEM_CRL_PARSER.readPEMObject(in, isFirst));
    }

    private CRL readDERCRL(
        ASN1InputStream aIn)
        throws IOException, CRLException
    {
        return getCRL(ASN1Sequence.getInstance(aIn.readObject()));
    }

    private CRL getCRL(ASN1Sequence seq)
        throws CRLException
    {
        if (seq == null)
        {
            return null;
        }
        
        if (seq.size() > 1
                && seq.getObjectAt(0) instanceof ASN1ObjectIdentifier)
        {
            if (seq.getObjectAt(0).equals(PKCSObjectIdentifiers.signedData))
            {
                sCrlData = SignedData.getInstance(ASN1Sequence.getInstance(
                    (ASN1TaggedObject)seq.getObjectAt(1), true)).getCRLs();

                return getCRL();
            }
        }

        return createCRL(
                     CertificateList.getInstance(seq));
    }

    private CRL getCRL()
        throws CRLException
    {
        if (sCrlData == null || sCrlDataObjectCount >= sCrlData.size())
        {
            return null;
        }

        return createCRL(
                            CertificateList.getInstance(
                                sCrlData.getObjectAt(sCrlDataObjectCount++)));
    }

    /**
     * Generates a certificate object and initializes it with the data
     * read from the input stream inStream.
     */
    public java.security.cert.Certificate engineGenerateCertificate(
        InputStream in)
        throws CertificateException
    {
        return doGenerateCertificate(in, true);
    }

   private java.security.cert.Certificate doGenerateCertificate(
            InputStream in,
            boolean isFirst)
            throws CertificateException
   {
        if (currentStream == null)
        {
            currentStream = in;
            sData = null;
            sDataObjectCount = 0;
        }
        else if (currentStream != in) // reset if input stream has changed
        {
            currentStream = in;
            sData = null;
            sDataObjectCount = 0;
        }

        try
        {
            if (sData != null)
            {
                if (sDataObjectCount != sData.size())
                {
                    return getCertificate();
                }
                else
                {
                    sData = null;
                    sDataObjectCount = 0;
                    return null;
                }
            }

            InputStream pis;

            if (in.markSupported())
            {
                pis = in;
            }
            else
            {
                // Android-changed: Use PushbackInputStream instead of ByteArrayInputStream.
                // we want {@code in.available()} to return the number of available bytes if
                // there is trailing data (otherwise it breaks
                // libcore.java.security.cert.X509CertificateTest#test_Provider
                // ). Which is not possible if we read the whole stream at this point.
                // // pis = new ByteArrayInputStream(Streams.readAll(in));
                pis = new PushbackInputStream(in);
            }

            // BEGIN Android-changed: Use PushbackInputStream
            // pis.mark(1);
            if (in.markSupported()) {
                pis.mark(1);
            }
            // END Android-changed: Use PushbackInputStream

            int tag = pis.read();

            if (tag == -1)
            {
                return null;
            }

            // BEGIN Android-changed: Use PushbackInputStream
            // pis.reset
            if (in.markSupported()) {
                pis.reset();
            }
            else
            {
                ((PushbackInputStream) pis).unread(tag);
            }
            // END Android-changed: Use PushbackInputStream

            if (tag != 0x30)  // assume ascii PEM encoded.
            {
                return readPEMCertificate(pis, isFirst);
            }
            else
            {
                return readDERCertificate(new ASN1InputStream(pis));
            }
        }
        catch (Exception e)
        {
            throw new ExCertificateException("parsing issue: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a (possibly empty) collection view of the certificates
     * read from the given input stream inStream.
     */
    public Collection engineGenerateCertificates(
        InputStream inStream)
        throws CertificateException
    {
        java.security.cert.Certificate     cert;
        // Android-removed: Don't read entire stream immediately.
        // we want {@code in.available()} to return the number of available bytes if
        // there is trailing data (otherwise it breaks
        // libcore.java.security.cert.X509CertificateTest#test_Provider
        // ). Which is not possible if we read the whole stream at this point.
        // BufferedInputStream in = new BufferedInputStream(inStream);
        List certs = new ArrayList();

        // Android-changed: Read from original stream
        // while ((cert = engineGenerateCertificate(in)) != null)
        // if we do read some certificates we'll return them even if junk at end of file
        while ((cert = doGenerateCertificate(inStream, certs.isEmpty())) != null)
        {
            certs.add(cert);
        }

        return certs;
    }

    /**
     * Generates a certificate revocation list (CRL) object and initializes
     * it with the data read from the input stream inStream.
     */
    public CRL engineGenerateCRL(
        InputStream in)
        throws CRLException
    {
        return doGenerateCRL(in, true);
    }

    /**
     * Generates a certificate revocation list (CRL) object and initializes
     * it with the data read from the input stream inStream.
     */
    private CRL doGenerateCRL(
        InputStream in,
        boolean     isFirst)
        throws CRLException
    {
        if (currentCrlStream == null)
        {
            currentCrlStream = in;
            sCrlData = null;
            sCrlDataObjectCount = 0;
        }
        else if (currentCrlStream != in) // reset if input stream has changed
        {
            currentCrlStream = in;
            sCrlData = null;
            sCrlDataObjectCount = 0;
        }

        try
        {
            if (sCrlData != null)
            {
                if (sCrlDataObjectCount != sCrlData.size())
                {
                    return getCRL();
                }
                else
                {
                    sCrlData = null;
                    sCrlDataObjectCount = 0;
                    return null;
                }
            }

            InputStream pis;

            if (in.markSupported())
            {
                pis = in;
            }
            else
            {
                pis = new ByteArrayInputStream(Streams.readAll(in));
            }

            pis.mark(1);
            int tag = pis.read();

            if (tag == -1)
            {
                return null;
            }

            pis.reset();
            if (tag != 0x30)  // assume ascii PEM encoded.
            {
                return readPEMCRL(pis, isFirst);
            }
            else
            {       // lazy evaluate to help processing of large CRLs
                return readDERCRL(new ASN1InputStream(pis, true));
            }
        }
        catch (CRLException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new CRLException(e.toString());
        }
    }

    /**
     * Returns a (possibly empty) collection view of the CRLs read from
     * the given input stream inStream.
     *
     * The inStream may contain a sequence of DER-encoded CRLs, or
     * a PKCS#7 CRL set.  This is a PKCS#7 SignedData object, with the
     * only signficant field being crls.  In particular the signature
     * and the contents are ignored.
     */
    public Collection engineGenerateCRLs(
        InputStream inStream)
        throws CRLException
    {
        CRL crl;
        List crls = new ArrayList();
        BufferedInputStream in = new BufferedInputStream(inStream);

        // if we do read some certificates we'll return them even if junk at end of file
        while ((crl = doGenerateCRL(in, crls.isEmpty())) != null)
        {
            crls.add(crl);
        }

        return crls;
    }

    public Iterator engineGetCertPathEncodings()
    {
        return PKIXCertPath.certPathEncodings.iterator();
    }

    public CertPath engineGenerateCertPath(
        InputStream inStream)
        throws CertificateException
    {
        return engineGenerateCertPath(inStream, "PkiPath");
    }

    public CertPath engineGenerateCertPath(
        InputStream inStream,
        String encoding)
        throws CertificateException
    {
        return new PKIXCertPath(inStream, encoding);
    }

    public CertPath engineGenerateCertPath(
        List certificates)
        throws CertificateException
    {
        Iterator iter = certificates.iterator();
        Object obj;
        while (iter.hasNext())
        {
            obj = iter.next();
            if (obj != null)
            {
                if (!(obj instanceof X509Certificate))
                {
                    throw new CertificateException("list contains non X509Certificate object while creating CertPath\n" + obj.toString());
                }
            }
        }
        return new PKIXCertPath(certificates);
    }

    private static class ExCertificateException
        extends CertificateException
    {
        private Throwable cause;

        public ExCertificateException(Throwable cause)
        {
            this.cause = cause;
        }

        public ExCertificateException(String msg, Throwable cause)
        {
            super(msg);

            this.cause = cause;
        }

        public Throwable getCause()
        {
            return cause;
        }
    }
}

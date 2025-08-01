/* GENERATED SOURCE. DO NOT MODIFY. */
package com.android.internal.org.bouncycastle.asn1.x509;

import java.io.IOException;

import com.android.internal.org.bouncycastle.asn1.ASN1Boolean;
import com.android.internal.org.bouncycastle.asn1.ASN1Encodable;
import com.android.internal.org.bouncycastle.asn1.ASN1EncodableVector;
import com.android.internal.org.bouncycastle.asn1.ASN1Object;
import com.android.internal.org.bouncycastle.asn1.ASN1ObjectIdentifier;
import com.android.internal.org.bouncycastle.asn1.ASN1OctetString;
import com.android.internal.org.bouncycastle.asn1.ASN1Primitive;
import com.android.internal.org.bouncycastle.asn1.ASN1Sequence;
import com.android.internal.org.bouncycastle.asn1.DEROctetString;
import com.android.internal.org.bouncycastle.asn1.DERSequence;
import com.android.internal.org.bouncycastle.util.Arrays;

/**
 * an object for the elements in the X.509 V3 extension block.
 * @hide This class is not part of the Android public SDK API
 */
public class Extension
    extends ASN1Object
{
    /**
     * Subject Directory Attributes
     */
    public static final ASN1ObjectIdentifier subjectDirectoryAttributes = new ASN1ObjectIdentifier("2.5.29.9").intern();
    
    /**
     * Subject Key Identifier 
     */
    public static final ASN1ObjectIdentifier subjectKeyIdentifier = new ASN1ObjectIdentifier("2.5.29.14").intern();

    /**
     * Key Usage 
     */
    public static final ASN1ObjectIdentifier keyUsage = new ASN1ObjectIdentifier("2.5.29.15").intern();

    /**
     * Private Key Usage Period 
     */
    public static final ASN1ObjectIdentifier privateKeyUsagePeriod = new ASN1ObjectIdentifier("2.5.29.16").intern();

    /**
     * Subject Alternative Name 
     */
    public static final ASN1ObjectIdentifier subjectAlternativeName = new ASN1ObjectIdentifier("2.5.29.17").intern();

    /**
     * Issuer Alternative Name 
     */
    public static final ASN1ObjectIdentifier issuerAlternativeName = new ASN1ObjectIdentifier("2.5.29.18").intern();

    /**
     * Basic Constraints 
     */
    public static final ASN1ObjectIdentifier basicConstraints = new ASN1ObjectIdentifier("2.5.29.19").intern();

    /**
     * CRL Number 
     */
    public static final ASN1ObjectIdentifier cRLNumber = new ASN1ObjectIdentifier("2.5.29.20").intern();

    /**
     * Reason code 
     */
    public static final ASN1ObjectIdentifier reasonCode = new ASN1ObjectIdentifier("2.5.29.21").intern();

    /**
     * Hold Instruction Code 
     */
    public static final ASN1ObjectIdentifier instructionCode = new ASN1ObjectIdentifier("2.5.29.23").intern();

    /**
     * Invalidity Date 
     */
    public static final ASN1ObjectIdentifier invalidityDate = new ASN1ObjectIdentifier("2.5.29.24").intern();

    /**
     * Delta CRL indicator 
     */
    public static final ASN1ObjectIdentifier deltaCRLIndicator = new ASN1ObjectIdentifier("2.5.29.27").intern();

    /**
     * Issuing Distribution Point 
     */
    public static final ASN1ObjectIdentifier issuingDistributionPoint = new ASN1ObjectIdentifier("2.5.29.28").intern();

    /**
     * Certificate Issuer 
     */
    public static final ASN1ObjectIdentifier certificateIssuer = new ASN1ObjectIdentifier("2.5.29.29").intern();

    /**
     * Name Constraints 
     */
    public static final ASN1ObjectIdentifier nameConstraints = new ASN1ObjectIdentifier("2.5.29.30").intern();

    /**
     * CRL Distribution Points 
     */
    public static final ASN1ObjectIdentifier cRLDistributionPoints = new ASN1ObjectIdentifier("2.5.29.31").intern();

    /**
     * Certificate Policies 
     */
    public static final ASN1ObjectIdentifier certificatePolicies = new ASN1ObjectIdentifier("2.5.29.32").intern();

    /**
     * Policy Mappings 
     */
    public static final ASN1ObjectIdentifier policyMappings = new ASN1ObjectIdentifier("2.5.29.33").intern();

    /**
     * Authority Key Identifier 
     */
    public static final ASN1ObjectIdentifier authorityKeyIdentifier = new ASN1ObjectIdentifier("2.5.29.35").intern();

    /**
     * Policy Constraints 
     */
    public static final ASN1ObjectIdentifier policyConstraints = new ASN1ObjectIdentifier("2.5.29.36").intern();

    /**
     * Extended Key Usage 
     */
    public static final ASN1ObjectIdentifier extendedKeyUsage = new ASN1ObjectIdentifier("2.5.29.37").intern();

    /**
     * Freshest CRL
     */
    public static final ASN1ObjectIdentifier freshestCRL = new ASN1ObjectIdentifier("2.5.29.46").intern();
     
    /**
     * Inhibit Any Policy
     */
    public static final ASN1ObjectIdentifier inhibitAnyPolicy = new ASN1ObjectIdentifier("2.5.29.54").intern();

    /**
     * Authority Info Access
     */
    public static final ASN1ObjectIdentifier authorityInfoAccess = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.1").intern();

    /**
     * Subject Info Access
     */
    public static final ASN1ObjectIdentifier subjectInfoAccess = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.11").intern();
    
    /**
     * Logo Type
     */
    public static final ASN1ObjectIdentifier logoType = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.12").intern();

    /**
     * BiometricInfo
     */
    public static final ASN1ObjectIdentifier biometricInfo = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.2").intern();
    
    /**
     * QCStatements
     */
    public static final ASN1ObjectIdentifier qCStatements = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.3").intern();

    /**
     * Audit identity extension in attribute certificates.
     */
    public static final ASN1ObjectIdentifier auditIdentity = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.1.4").intern();
    
    /**
     * NoRevAvail extension in attribute certificates.
     */
    public static final ASN1ObjectIdentifier noRevAvail = new ASN1ObjectIdentifier("2.5.29.56").intern();

    /**
     * TargetInformation extension in attribute certificates.
     */
    public static final ASN1ObjectIdentifier targetInformation = new ASN1ObjectIdentifier("2.5.29.55").intern();

    /**
     * Expired Certificates on CRL extension
     */
    public static final ASN1ObjectIdentifier expiredCertsOnCRL = new ASN1ObjectIdentifier("2.5.29.60").intern();

    /**
     * the subject’s alternative public key information
     */
    public static final ASN1ObjectIdentifier subjectAltPublicKeyInfo = new ASN1ObjectIdentifier("2.5.29.72").intern();

    /**
     * the algorithm identifier for the alternative digital signature algorithm.
     */
    public static final ASN1ObjectIdentifier altSignatureAlgorithm = new ASN1ObjectIdentifier("2.5.29.73").intern();

    /**
     * alternative signature shall be created by the issuer using its alternative private key.
     */
    public static final ASN1ObjectIdentifier altSignatureValue = new ASN1ObjectIdentifier("2.5.29.74").intern();

    /**
     * delta certificate extension - prototype value will change!
     */
    public static final ASN1ObjectIdentifier deltaCertificateDescriptor = new ASN1ObjectIdentifier("2.16.840.1.114027.80.6.1");

    private ASN1ObjectIdentifier extnId;
    private boolean             critical;
    private ASN1OctetString      value;

    /**
     * Constructor using an ASN1Boolean and an OCTET STRING for the value.
     *
     * @param extnId the OID associated with this extension.
     * @param critical will evaluate to true if the extension is critical, false otherwise.
     * @param value the extension's value wrapped in an OCTET STRING.
     */
    public Extension(
        ASN1ObjectIdentifier extnId,
        ASN1Boolean critical,
        ASN1OctetString value)
    {
        this(extnId, critical.isTrue(), value);
    }

    /**
     * Constructor using a byte[] for the value.
     *
     * @param extnId the OID associated with this extension.
     * @param critical true if the extension is critical, false otherwise.
     * @param value the extension's value as a byte[] to be wrapped in an OCTET STRING.
     */
    public Extension(
        ASN1ObjectIdentifier extnId,
        boolean critical,
        byte[] value)
    {
        this(extnId, critical, new DEROctetString(Arrays.clone(value)));
    }

    /**
     * Constructor using an OCTET STRING for the value.
     *
     * @param extnId the OID associated with this extension.
     * @param critical true if the extension is critical, false otherwise.
     * @param value the extension's value wrapped in an OCTET STRING.
     */
    public Extension(
        ASN1ObjectIdentifier extnId,
        boolean critical,
        ASN1OctetString value)
    {
        this.extnId = extnId;
        this.critical = critical;
        this.value = value;
    }

    /**
     * Helper method to create an extension from any ASN.1 encodable object.
     *
     * @param extnId the OID associated with this extension.
     * @param critical true if the extension is critical, false otherwise.
     * @param value the value to be encoded into the extension's OCTET STRING.
     * @return a new Extension with the encoding of value in the bytes of the extension's OCTET STRING.
     * @throws IOException if the value cannot be encoded into bytes.
     */
    public static Extension create(
        ASN1ObjectIdentifier extnId,
        boolean critical,
        ASN1Encodable value)
        throws IOException
    {
        return new Extension(extnId, critical, value.toASN1Primitive().getEncoded());
    }

    private Extension(ASN1Sequence seq)
    {
        if (seq.size() == 2)
        {
            this.extnId = ASN1ObjectIdentifier.getInstance(seq.getObjectAt(0));
            this.critical = false;
            this.value = ASN1OctetString.getInstance(seq.getObjectAt(1));
        }
        else if (seq.size() == 3)
        {
            this.extnId = ASN1ObjectIdentifier.getInstance(seq.getObjectAt(0));
            this.critical = ASN1Boolean.getInstance(seq.getObjectAt(1)).isTrue();
            this.value = ASN1OctetString.getInstance(seq.getObjectAt(2));
        }
        else
        {
            throw new IllegalArgumentException("Bad sequence size: " + seq.size());
        }
    }

    public static Extension getInstance(Object obj)
    {
        if (obj instanceof Extension)
        {
            return (Extension)obj;
        }
        else if (obj != null)
        {
            return new Extension(ASN1Sequence.getInstance(obj));
        }

        return null;
    }

    public ASN1ObjectIdentifier getExtnId()
    {
        return extnId;
    }

    public boolean isCritical()
    {
        return critical;
    }

    public ASN1OctetString getExtnValue()
    {
        return value;
    }

    public ASN1Encodable getParsedValue()
    {
        return convertValueToObject(this);
    }

    public int hashCode()
    {
        if (this.isCritical())
        {
            return this.getExtnValue().hashCode() ^ this.getExtnId().hashCode();
        }

        return ~(this.getExtnValue().hashCode() ^ this.getExtnId().hashCode());
    }

    public boolean equals(
        Object  o)
    {
        if (!(o instanceof Extension))
        {
            return false;
        }

        Extension other = (Extension)o;

        return other.getExtnId().equals(this.getExtnId())
            && other.getExtnValue().equals(this.getExtnValue())
            && (other.isCritical() == this.isCritical());
    }

    public ASN1Primitive toASN1Primitive()
    {
        ASN1EncodableVector v = new ASN1EncodableVector(3);

        v.add(extnId);

        if (critical)
        {
            v.add(ASN1Boolean.getInstance(true));
        }

        v.add(value);

        return new DERSequence(v);
    }

    /**
     * Convert the value of the passed in extension to an object
     * @param ext the extension to parse
     * @return the object the value string contains
     * @exception IllegalArgumentException if conversion is not possible
     */
    private static ASN1Primitive convertValueToObject(
        Extension ext)
        throws IllegalArgumentException
    {
        try
        {
            return ASN1Primitive.fromByteArray(ext.getExtnValue().getOctets());
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("can't convert extension: " +  e);
        }
    }
}

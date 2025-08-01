/* GENERATED SOURCE. DO NOT MODIFY. */
// © 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 ***************************************************************************
 * Copyright (C) 2008-2016 International Business Machines Corporation
 * and others. All Rights Reserved.
 ***************************************************************************
 *
 * Unicode Spoof Detection
 */

package android.icu.text;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.icu.impl.ICUBinary;
import android.icu.impl.ICUBinary.Authenticate;
import android.icu.impl.Utility;
import android.icu.lang.UCharacter;
import android.icu.lang.UCharacter.IdentifierType;
import android.icu.lang.UCharacterCategory;
import android.icu.lang.UProperty;
import android.icu.lang.UScript;
import android.icu.util.ULocale;

/**
 * <p>
 * This class, based on <a href="http://unicode.org/reports/tr36">Unicode Technical Report #36</a> and
 * <a href="http://unicode.org/reports/tr39">Unicode Technical Standard #39</a>, has two main functions:
 *
 * <ol>
 * <li>Checking whether two strings are visually <em>confusable</em> with each other, such as "desparejado" and
 * "ԁеѕрагејаԁо".</li>
 * <li>Checking whether an individual string is likely to be an attempt at confusing the reader (<em>spoof
 * detection</em>), such as "pаypаl" spelled with Cyrillic 'а' characters.</li>
 * </ol>
 *
 * <p>
 * Although originally designed as a method for flagging suspicious identifier strings such as URLs,
 * <code>SpoofChecker</code> has a number of other practical use cases, such as preventing attempts to evade bad-word
 * content filters.
 *
 * <h2>Confusables</h2>
 *
 * <p>
 * The following example shows how to use <code>SpoofChecker</code> to check for confusability between two strings:
 *
 * <pre>
 * <code>
 * SpoofChecker sc = new SpoofChecker.Builder().setChecks(SpoofChecker.CONFUSABLE).build();
 * int result = sc.areConfusable("desparejado", "ԁеѕрагејаԁо");
 * System.out.println(result != 0);  // true
 * </code>
 * </pre>
 *
 * <p>
 * <code>SpoofChecker</code> uses a builder paradigm: options are specified within the context of a lightweight
 * {@link SpoofChecker.Builder} object, and upon calling {@link SpoofChecker.Builder#build}, expensive data loading
 * operations are performed, and an immutable <code>SpoofChecker</code> is returned.
 *
 * <p>
 * The first line of the example creates a <code>SpoofChecker</code> object with confusable-checking enabled; the second
 * line performs the confusability test. For best performance, the instance should be created once (e.g., upon
 * application startup), and the more efficient {@link SpoofChecker#areConfusable} method can be used at runtime.
 *
 * <p>
 * If the paragraph direction used to display the strings is known, it should be passed to {@link SpoofChecker#areConfusable}:
 *
 * <pre>
 * <code>
 * // These strings look identical when rendered in a left-to-right context.
 * // They look distinct in a right-to-left context.
 * String s1 = "A1\u05D0";  // A1א
 * String s2 = "A\u05D01";  // Aא1
 *
 * SpoofChecker sc = new SpoofChecker.Builder().setChecks(SpoofChecker.CONFUSABLE).build();
 * int result = sc.areConfusable(Bidi.DIRECTION_LEFT_TO_RIGHT, s1, s2);
 * System.out.println(result != 0);  // true
 * </code>
 * </pre>
 *
 * <p>
 * UTS 39 defines two strings to be <em>confusable</em> if they map to the same skeleton. A <em>skeleton</em> is a
 * sequence of families of confusable characters, where each family has a single exemplar character.
 * {@link SpoofChecker#getSkeleton} computes the skeleton for a particular string, so the following snippet is
 * equivalent to the example above:
 *
 * <pre>
 * <code>
 * SpoofChecker sc = new SpoofChecker.Builder().setChecks(SpoofChecker.CONFUSABLE).build();
 * boolean result = sc.getSkeleton("desparejado").equals(sc.getSkeleton("ԁеѕрагејаԁо"));
 * System.out.println(result);  // true
 * </code>
 * </pre>
 *
 * <p>
 * If you need to check if a string is confusable with any string in a dictionary of many strings, rather than calling
 * {@link SpoofChecker#areConfusable} many times in a loop, {@link SpoofChecker#getSkeleton} can be used instead, as
 * shown below:
 *
 * <pre>
 * // Setup:
 * String[] DICTIONARY = new String[]{ "lorem", "ipsum" }; // example
 * SpoofChecker sc = new SpoofChecker.Builder().setChecks(SpoofChecker.CONFUSABLE).build();
 * HashSet&lt;String&gt; skeletons = new HashSet&lt;String&gt;();
 * for (String word : DICTIONARY) {
 *   skeletons.add(sc.getSkeleton(word));
 * }
 *
 * // Live Check:
 * boolean result = skeletons.contains(sc.getSkeleton("1orern"));
 * System.out.println(result);  // true
 * </pre>
 *
 * <p>
 * <b>Note:</b> Since the Unicode confusables mapping table is frequently updated, confusable skeletons are <em>not</em>
 * guaranteed to be the same between ICU releases. We therefore recommend that you always compute confusable skeletons
 * at runtime and do not rely on creating a permanent, or difficult to update, database of skeletons.
 *
 * <h2>Spoof Detection</h2>
 *
 * <p>
 * The following snippet shows a minimal example of using <code>SpoofChecker</code> to perform spoof detection on a
 * string:
 *
 * <pre>
 * SpoofChecker sc = new SpoofChecker.Builder()
 *     .setAllowedChars(SpoofChecker.RECOMMENDED.cloneAsThawed().addAll(SpoofChecker.INCLUSION))
 *     .setRestrictionLevel(SpoofChecker.RestrictionLevel.MODERATELY_RESTRICTIVE)
 *     .setChecks(SpoofChecker.ALL_CHECKS &~ SpoofChecker.CONFUSABLE)
 *     .build();
 * boolean result = sc.failsChecks("pаypаl");  // with Cyrillic 'а' characters
 * System.out.println(result);  // true
 * </pre>
 *
 * <p>
 * As in the case for confusability checking, it is good practice to create one <code>SpoofChecker</code> instance at
 * startup, and call the cheaper {@link SpoofChecker#failsChecks} online. In the second line, we specify the set of
 * allowed characters to be those with type RECOMMENDED or INCLUSION, according to the recommendation in UTS 39. In the
 * third line, the CONFUSABLE checks are disabled. It is good practice to disable them if you won't be using the
 * instance to perform confusability checking.
 *
 * <p>
 * To get more details on why a string failed the checks, use a {@link SpoofChecker.CheckResult}:
 *
 * <pre>
 * <code>
 * SpoofChecker sc = new SpoofChecker.Builder()
 *     .setAllowedChars(SpoofChecker.RECOMMENDED.cloneAsThawed().addAll(SpoofChecker.INCLUSION))
 *     .setRestrictionLevel(SpoofChecker.RestrictionLevel.MODERATELY_RESTRICTIVE)
 *     .setChecks(SpoofChecker.ALL_CHECKS &~ SpoofChecker.CONFUSABLE)
 *     .build();
 * SpoofChecker.CheckResult checkResult = new SpoofChecker.CheckResult();
 * boolean result = sc.failsChecks("pаypаl", checkResult);
 * System.out.println(checkResult.checks);  // 16
 * </code>
 * </pre>
 *
 * <p>
 * The return value is a bitmask of the checks that failed. In this case, there was one check that failed:
 * {@link SpoofChecker#RESTRICTION_LEVEL}, corresponding to the fifth bit (16). The possible checks are:
 *
 * <ul>
 * <li><code>RESTRICTION_LEVEL</code>: flags strings that violate the
 * <a href="http://unicode.org/reports/tr39/#Restriction_Level_Detection">Restriction Level</a> test as specified in UTS
 * 39; in most cases, this means flagging strings that contain characters from multiple different scripts.</li>
 * <li><code>INVISIBLE</code>: flags strings that contain invisible characters, such as zero-width spaces, or character
 * sequences that are likely not to display, such as multiple occurrences of the same non-spacing mark.</li>
 * <li><code>CHAR_LIMIT</code>: flags strings that contain characters outside of a specified set of acceptable
 * characters. See {@link SpoofChecker.Builder#setAllowedChars} and {@link SpoofChecker.Builder#setAllowedLocales}.</li>
 * <li><code>MIXED_NUMBERS</code>: flags strings that contain digits from multiple different numbering systems.</li>
 * </ul>
 *
 * <p>
 * These checks can be enabled independently of each other. For example, if you were interested in checking for only the
 * INVISIBLE and MIXED_NUMBERS conditions, you could do:
 *
 * <pre>
 * <code>
 * SpoofChecker sc = new SpoofChecker.Builder()
 *     .setChecks(SpoofChecker.INVISIBLE | SpoofChecker.MIXED_NUMBERS)
 *     .build();
 * boolean result = sc.failsChecks("৪8");
 * System.out.println(result);  // true
 * </code>
 * </pre>
 *
 * <p>
 * <b>Note:</b> The Restriction Level is the most powerful of the checks. The full logic is documented in
 * <a href="http://unicode.org/reports/tr39/#Restriction_Level_Detection">UTS 39</a>, but the basic idea is that strings
 * are restricted to contain characters from only a single script, <em>except</em> that most scripts are allowed to have
 * Latin characters interspersed. Although the default restriction level is <code>HIGHLY_RESTRICTIVE</code>, it is
 * recommended that users set their restriction level to <code>MODERATELY_RESTRICTIVE</code>, which allows Latin mixed
 * with all other scripts except Cyrillic, Greek, and Cherokee, with which it is often confusable. For more details on
 * the levels, see UTS 39 or {@link SpoofChecker.RestrictionLevel}. The Restriction Level test is aware of the set of
 * allowed characters set in {@link SpoofChecker.Builder#setAllowedChars}. Note that characters which have script code
 * COMMON or INHERITED, such as numbers and punctuation, are ignored when computing whether a string has multiple
 * scripts.
 *
 * <h2>Advanced bidirectional usage</h2>
 * If the paragraph direction with which the identifiers will be displayed is not known, there are
 * multiple options for confusable detection depending on the circumstances.
 *
 * <p>
 * In some circumstances, the only concern is confusion between identifiers displayed with the same
 * paragraph direction.
 *
 * <p>
 * An example is the case where identifiers are usernames prefixed with the @ symbol.
 * That symbol will appear to the left in a left-to-right context, and to the right in a
 * right-to-left context, so that an identifier displayed in a left-to-right context can never be
 * confused with an identifier displayed in a right-to-left context:
 * <ul>
 * <li>
 * The usernames "A1א" (A one aleph) and "Aא1" (A aleph 1)
 * would be considered confusable, since they both appear as @A1א in a left-to-right context, and the
 * usernames "אA_1" (aleph A underscore one) and "א1_A" (aleph one underscore A) would be considered
 * confusable, since they both appear as A_1א@ in a right-to-left context.
 * </li>
 * <li>
 * The username "Mark_" would not be considered confusable with the username "_Mark",
 * even though the latter would appear as Mark_@ in a right-to-left context, and the
 * former as @Mark_ in a left-to-right context.
 * </li>
 * </ul>
 * <p>
 * In that case, the caller should check for both LTR-confusability and RTL-confusability:
 *
 * <pre>
 * <code>
 * boolean confusableInEitherDirection =
 *     sc.areConfusable(Bidi.DIRECTION_LEFT_TO_RIGHT, id1, id2) ||
 *     sc.areConfusable(Bidi.DIRECTION_RIGHT_TO_LEFT, id1, id2);
 * </code>
 * </pre>
 *
 * If the bidiSkeleton is used, the LTR and RTL skeleta should be kept separately and compared, LTR
 * with LTR and RTL with RTL.
 *
 * <p>
 * In cases where confusability between the visual appearances of an identifier displayed in a
 * left-to-right context with another identifier displayed in a right-to-left context is a concern,
 * the LTR skeleton of one can be compared with the RTL skeleton of the other.  However, this
 * very broad definition of confusability may have unexpected results; for instance, it treats the
 * ASCII identifiers "Mark_" and "_Mark" as confusable.
 *
 * <h2>Additional Information</h2>
 *
 * <p>
 * A <code>SpoofChecker</code> instance may be used repeatedly to perform checks on any number of identifiers.
 *
 * <p>
 * <b>Thread Safety:</b> The methods on <code>SpoofChecker</code> objects are thread safe. The test functions for
 * checking a single identifier, or for testing whether two identifiers are potentially confusable, may called
 * concurrently from multiple threads using the same <code>SpoofChecker</code> instance.
 *
 * @hide Only a subset of ICU is exposed in Android
 */
public class SpoofChecker {

    /**
     * Constants from UTS 39 for use in setRestrictionLevel.
     *
     * @hide Only a subset of ICU is exposed in Android
     */
    public enum RestrictionLevel {
        /**
         * All characters in the string are in the identifier profile and all characters in the string are in the ASCII
         * range.
         */
        ASCII,
        /**
         * The string classifies as ASCII-Only, or all characters in the string are in the identifier profile and the
         * string is single-script, according to the definition in UTS 39 section 5.1.
         */
        SINGLE_SCRIPT_RESTRICTIVE,
        /**
         * The string classifies as Single Script, or all characters in the string are in the identifier profile and the
         * string is covered by any of the following sets of scripts, according to the definition in UTS 39 section 5.1:
         * <ul>
         * <li>Latin + Han + Bopomofo (or equivalently: Latn + Hanb)</li>
         * <li>Latin + Han + Hiragana + Katakana (or equivalently: Latn + Jpan)</li>
         * <li>Latin + Han + Hangul (or equivalently: Latn +Kore)</li>
         * </ul>
         */
        HIGHLY_RESTRICTIVE,
        /**
         * The string classifies as Highly Restrictive, or all characters in the string are in the identifier profile
         * and the string is covered by Latin and any one other Recommended or Aspirational script, except Cyrillic,
         * Greek, and Cherokee.
         */
        MODERATELY_RESTRICTIVE,
        /**
         * All characters in the string are in the identifier profile. Allow arbitrary mixtures of scripts, such as
         * Ωmega, Teχ, HλLF-LIFE, Toys-Я-Us.
         */
        MINIMALLY_RESTRICTIVE,
        /**
         * Any valid identifiers, including characters outside of the Identifier Profile, such as I♥NY.org
         */
        UNRESTRICTIVE,
    }

    /**
     * Security Profile constant from UTS 39 for use in {@link SpoofChecker.Builder#setAllowedChars}.
     */
    public static final UnicodeSet INCLUSION =
            new UnicodeSet().
            applyIntPropertyValue(UProperty.IDENTIFIER_TYPE, IdentifierType.INCLUSION.ordinal()).
            freeze();

    /**
     * Security Profile constant from UTS 39 for use in {@link SpoofChecker.Builder#setAllowedChars}.
     */
    public static final UnicodeSet RECOMMENDED =
            new UnicodeSet().
            applyIntPropertyValue(UProperty.IDENTIFIER_TYPE, IdentifierType.RECOMMENDED.ordinal()).
            freeze();

    /**
     * Constants for the kinds of checks that USpoofChecker can perform. These values are used both to select the set of
     * checks that will be performed, and to report results from the check function.
     *
     */

    /**
     * When performing the two-string {@link SpoofChecker#areConfusable} test, this flag in the return value indicates
     * that the two strings are visually confusable and that they are from the same script, according to UTS 39 section
     * 4.
     */
    public static final int SINGLE_SCRIPT_CONFUSABLE = 1;

    /**
     * When performing the two-string {@link SpoofChecker#areConfusable} test, this flag in the return value indicates
     * that the two strings are visually confusable and that they are <b>not</b> from the same script, according to UTS
     * 39 section 4.
     */
    public static final int MIXED_SCRIPT_CONFUSABLE = 2;

    /**
     * When performing the two-string {@link SpoofChecker#areConfusable} test, this flag in the return value indicates
     * that the two strings are visually confusable and that they are not from the same script but both of them are
     * single-script strings, according to UTS 39 section 4.
     */
    public static final int WHOLE_SCRIPT_CONFUSABLE = 4;

    /**
     * Enable this flag in {@link SpoofChecker.Builder#setChecks} to turn on all types of confusables. You may set the
     * checks to some subset of SINGLE_SCRIPT_CONFUSABLE, MIXED_SCRIPT_CONFUSABLE, or WHOLE_SCRIPT_CONFUSABLE to make
     * {@link SpoofChecker#areConfusable} return only those types of confusables.
     */
    public static final int CONFUSABLE = SINGLE_SCRIPT_CONFUSABLE | MIXED_SCRIPT_CONFUSABLE | WHOLE_SCRIPT_CONFUSABLE;

    /**
     * This flag is deprecated and no longer affects the behavior of SpoofChecker.
     *
     * @deprecated ICU 58 Any case confusable mappings were removed from UTS 39; the corresponding ICU API was
     * deprecated.
     */
    @Deprecated
    public static final int ANY_CASE = 8;

    /**
     * Check that an identifier satisfies the requirements for the restriction level specified in
     * {@link SpoofChecker.Builder#setRestrictionLevel}. The default restriction level is
     * {@link RestrictionLevel#HIGHLY_RESTRICTIVE}.
     */
    public static final int RESTRICTION_LEVEL = 16;

    /**
     * Check that an identifier contains only characters from a single script (plus chars from the common and inherited
     * scripts.) Applies to checks of a single identifier check only.
     *
     * @deprecated ICU 51 Use RESTRICTION_LEVEL
     */
    @Deprecated
    public static final int SINGLE_SCRIPT = RESTRICTION_LEVEL;

    /**
     * Check an identifier for the presence of invisible characters, such as zero-width spaces, or character sequences
     * that are likely not to display, such as multiple occurrences of the same non-spacing mark. This check does not
     * test the input string as a whole for conformance to any particular syntax for identifiers.
     */
    public static final int INVISIBLE = 32;

    /**
     * Check that an identifier contains only characters from a specified set of acceptable characters. See
     * {@link Builder#setAllowedChars} and {@link Builder#setAllowedLocales}. Note that a string that fails this check
     * will also fail the {@link #RESTRICTION_LEVEL} check.
     */
    public static final int CHAR_LIMIT = 64;

    /**
     * Check that an identifier does not mix numbers from different numbering systems. For more information, see UTS 39
     * section 5.3.
     */
    public static final int MIXED_NUMBERS = 128;

    /**
     * Check that an identifier does not have a combining character following a character in which that
     * combining character would be hidden; for example 'i' followed by a U+0307 combining dot.
     * <p>
     * More specifically, the following characters are forbidden from preceding a U+0307:
     * <ul>
     * <li>Those with the Soft_Dotted Unicode property (which includes 'i' and 'j')</li>
     * <li>Latin lowercase letter 'l'</li>
     * <li>Dotless 'i' and 'j' ('ı' and 'ȷ', U+0131 and U+0237)</li>
     * <li>Any character whose confusable prototype ends with such a character
     * (Soft_Dotted, 'l', 'ı', or 'ȷ')</li>
     * </ul>
     * In addition, combining characters are allowed between the above characters and U+0307 except those
     * with combining class 0 or combining class "Above" (230, same class as U+0307).
     * <p>
     * This list and the number of combing characters considered by this check may grow over time.
     */
    public static final int HIDDEN_OVERLAY = 256;

    // Update CheckResult.toString() when a new check is added.

    /**
     * Enable all spoof checks.
     */
    public static final int ALL_CHECKS = 0xFFFFFFFF;

    // Used for checking for ASCII-Only restriction level
    static final UnicodeSet ASCII = new UnicodeSet(0, 0x7F).freeze();

    /**
     * private constructor: a SpoofChecker has to be built by the builder
     */
    private SpoofChecker() {
    }

    /**
     * SpoofChecker Builder. To create a SpoofChecker, first instantiate a SpoofChecker.Builder, set the desired
     * checking options on the builder, then call the build() function to create a SpoofChecker instance.
     *
     * @hide Only a subset of ICU is exposed in Android
     */
    public static class Builder {
        int fChecks; // Bit vector of checks to perform.
        SpoofData fSpoofData;
        final UnicodeSet fAllowedCharsSet = new UnicodeSet(0, 0x10ffff); // The UnicodeSet of allowed characters.
        // for this Spoof Checker. Defaults to all chars.
        final Set<ULocale> fAllowedLocales = new LinkedHashSet<>(); // The list of allowed locales.
        private RestrictionLevel fRestrictionLevel;

        /**
         * Constructor: Create a default Unicode Spoof Checker Builder, configured to perform all checks except for
         * LOCALE_LIMIT and CHAR_LIMIT. Note that additional checks may be added in the future, resulting in the changes
         * to the default checking behavior.
         */
        public Builder() {
            fChecks = ALL_CHECKS;
            fSpoofData = null;
            fRestrictionLevel = RestrictionLevel.HIGHLY_RESTRICTIVE;
        }

        /**
         * Constructor: Create a Spoof Checker Builder, and set the configuration from an existing SpoofChecker.
         *
         * @param src
         *            The existing checker.
         */
        public Builder(SpoofChecker src) {
            fChecks = src.fChecks;
            fSpoofData = src.fSpoofData; // For the data, we will either use the source data
                                         // as-is, or drop the builder's reference to it
                                         // and generate new data, depending on what our
                                         // caller does with the builder.
            fAllowedCharsSet.set(src.fAllowedCharsSet);
            fAllowedLocales.addAll(src.fAllowedLocales);
            fRestrictionLevel = src.fRestrictionLevel;
        }

        /**
         * Create a SpoofChecker with current configuration.
         *
         * @return SpoofChecker
         */
        public SpoofChecker build() {
            // TODO: Make this data loading be lazy (see #12696).
            if (fSpoofData == null) {
                // read binary file
                fSpoofData = SpoofData.getDefault();
            }

            // Copy all state from the builder to the new SpoofChecker.
            // Make sure that everything is either cloned or copied, so
            // that subsequent re-use of the builder won't modify the built
            // SpoofChecker.
            //
            // One exception to this: the SpoofData is just assigned.
            // If the builder subsequently needs to modify fSpoofData
            // it will create a new SpoofData object first.

            SpoofChecker result = new SpoofChecker();
            result.fChecks = this.fChecks;
            result.fSpoofData = this.fSpoofData;
            result.fAllowedCharsSet = (UnicodeSet) (this.fAllowedCharsSet.clone());
            result.fAllowedCharsSet.freeze();
            result.fAllowedLocales = new HashSet<>(this.fAllowedLocales);
            result.fRestrictionLevel = this.fRestrictionLevel;
            return result;
        }

        /**
         * Specify the source form of the spoof data Spoof Checker. The inputs correspond to the Unicode data file
         * confusables.txt as described in Unicode UAX 39. The syntax of the source data is as described in UAX 39 for
         * these files, and the content of these files is acceptable input.
         *
         * @param confusables
         *            the Reader of confusable characters definitions, as found in file confusables.txt from
         *            unicode.org.
         * @throws ParseException
         *             To report syntax errors in the input.
         */
        public Builder setData(Reader confusables) throws ParseException, IOException {

            // Compile the binary data from the source (text) format.
            // Drop the builder's reference to any pre-existing data, which may
            // be in use in an already-built checker.

            fSpoofData = new SpoofData();
            ConfusabledataBuilder.buildConfusableData(confusables, fSpoofData);
            return this;
        }

        /**
         * Deprecated as of ICU 58; use {@link SpoofChecker.Builder#setData(Reader confusables)} instead.
         *
         * @param confusables
         *            the Reader of confusable characters definitions, as found in file confusables.txt from
         *            unicode.org.
         * @param confusablesWholeScript
         *            No longer supported.
         * @throws ParseException
         *             To report syntax errors in the input.
         *
         * @deprecated ICU 58
         */
        @Deprecated
        public Builder setData(Reader confusables, Reader confusablesWholeScript) throws ParseException, IOException {
            setData(confusables);
            return this;
        }

        /**
         * Specify the bitmask of checks that will be performed by {@link SpoofChecker#failsChecks}. Calling this method
         * overwrites any checks that may have already been enabled. By default, all checks are enabled.
         *
         * To enable specific checks and disable all others,
         * OR together only the bit constants for the desired checks.
         * For example, to fail strings containing characters outside of
         * the set specified by {@link #setAllowedChars} and
         * also strings that contain digits from mixed numbering systems:
         *
         * <pre>
         * {@code
         * builder.setChecks(SpoofChecker.CHAR_LIMIT | SpoofChecker.MIXED_NUMBERS);
         * }
         * </pre>
         *
         * To disable specific checks and enable all others,
         * start with ALL_CHECKS and "AND away" the not-desired checks.
         * For example, if you are not planning to use the {@link SpoofChecker#areConfusable} functionality,
         * it is good practice to disable the CONFUSABLE check:
         *
         * <pre>
         * {@code
         * builder.setChecks(SpoofChecker.ALL_CHECKS & ~SpoofChecker.CONFUSABLE);
         * }
         * </pre>
         *
         * Note that methods such as {@link #setAllowedChars}, {@link #setAllowedLocales}, and
         * {@link #setRestrictionLevel} will enable certain checks when called. Those methods will OR the check they
         * enable onto the existing bitmask specified by this method. For more details, see the documentation of those
         * methods.
         *
         * @param checks
         *            The set of checks that this spoof checker will perform. The value is an 'or' of the desired
         *            checks.
         * @return self
         */
        public Builder setChecks(int checks) {
            // Verify that the requested checks are all ones (bits) that
            // are acceptable, known values.
            if (0 != (checks & ~SpoofChecker.ALL_CHECKS)) {
                throw new IllegalArgumentException("Bad Spoof Checks value.");
            }
            this.fChecks = (checks & SpoofChecker.ALL_CHECKS);
            return this;
        }

        /**
         * Limit characters that are acceptable in identifiers being checked to those normally used with the languages
         * associated with the specified locales. Any previously specified list of locales is replaced by the new
         * settings.
         *
         * A set of languages is determined from the locale(s), and from those a set of acceptable Unicode scripts is
         * determined. Characters from this set of scripts, along with characters from the "common" and "inherited"
         * Unicode Script categories will be permitted.
         *
         * Supplying an empty string removes all restrictions; characters from any script will be allowed.
         *
         * The {@link #CHAR_LIMIT} test is automatically enabled for this SpoofChecker when calling this function with a
         * non-empty list of locales.
         *
         * The Unicode Set of characters that will be allowed is accessible via the {@link #getAllowedChars} function.
         * setAllowedLocales() will <i>replace</i> any previously applied set of allowed characters.
         *
         * Adjustments, such as additions or deletions of certain classes of characters, can be made to the result of
         * {@link #setAllowedChars} by fetching the resulting set with {@link #getAllowedChars}, manipulating it with
         * the Unicode Set API, then resetting the spoof detectors limits with {@link #setAllowedChars}.
         *
         * @param locales
         *            A Set of ULocales, from which the language and associated script are extracted. If the locales Set
         *            is null, no restrictions will be placed on the allowed characters.
         *
         * @return self
         */
        public Builder setAllowedLocales(Set<ULocale> locales) {
            fAllowedCharsSet.clear();

            for (ULocale locale : locales) {
                // Add the script chars for this locale to the accumulating set
                // of allowed chars.
                addScriptChars(locale, fAllowedCharsSet);
            }

            // If our caller provided an empty list of locales, we disable the
            // allowed characters checking
            fAllowedLocales.clear();
            if (locales.size() == 0) {
                fAllowedCharsSet.add(0, 0x10ffff);
                fChecks &= ~CHAR_LIMIT;
                return this;
            }

            // Add all common and inherited characters to the set of allowed
            // chars.
            UnicodeSet tempSet = new UnicodeSet();
            tempSet.applyIntPropertyValue(UProperty.SCRIPT, UScript.COMMON);
            fAllowedCharsSet.addAll(tempSet);
            tempSet.applyIntPropertyValue(UProperty.SCRIPT, UScript.INHERITED);
            fAllowedCharsSet.addAll(tempSet);

            // Store the updated spoof checker state.
            fAllowedLocales.clear();
            fAllowedLocales.addAll(locales);
            fChecks |= CHAR_LIMIT;
            return this;
        }

        /**
         * Limit characters that are acceptable in identifiers being checked to those normally used with the languages
         * associated with the specified locales. Any previously specified list of locales is replaced by the new
         * settings.
         *
         * @param locales
         *            A Set of Locales, from which the language and associated script are extracted. If the locales Set
         *            is null, no restrictions will be placed on the allowed characters.
         *
         * @return self
         */
        public Builder setAllowedJavaLocales(Set<Locale> locales) {
            HashSet<ULocale> ulocales = new HashSet<>(locales.size());
            for (Locale locale : locales) {
                ulocales.add(ULocale.forLocale(locale));
            }
            return setAllowedLocales(ulocales);
        }

        // Add (union) to the UnicodeSet all of the characters for the scripts
        // used for the specified locale. Part of the implementation of
        // setAllowedLocales.
        private void addScriptChars(ULocale locale, UnicodeSet allowedChars) {
            int scripts[] = UScript.getCode(locale);
            if (scripts != null) {
                UnicodeSet tmpSet = new UnicodeSet();
                for (int i = 0; i < scripts.length; i++) {
                    tmpSet.applyIntPropertyValue(UProperty.SCRIPT, scripts[i]);
                    allowedChars.addAll(tmpSet);
                }
            }
            // else it's an unknown script.
            // Maybe they asked for the script of "zxx", which refers to no linguistic content.
            // Maybe they asked for the script of a newer locale that we don't know in the older version of ICU.
        }

        /**
         * Limit the acceptable characters to those specified by a Unicode Set. Any previously specified character limit
         * is replaced by the new settings. This includes limits on characters that were set with the
         * setAllowedLocales() function. Note that the RESTRICTED set is useful.
         *
         * The {@link #CHAR_LIMIT} test is automatically enabled for this SpoofChecker by this function.
         *
         * @param chars
         *            A Unicode Set containing the list of characters that are permitted. The incoming set is cloned by
         *            this function, so there are no restrictions on modifying or deleting the UnicodeSet after calling
         *            this function. Note that this clears the allowedLocales set.
         * @return self
         */
        public Builder setAllowedChars(UnicodeSet chars) {
            fAllowedCharsSet.set(chars);
            fAllowedLocales.clear();
            fChecks |= CHAR_LIMIT;
            return this;
        }

        /**
         * Set the loosest restriction level allowed for strings. The default if this is not called is
         * {@link RestrictionLevel#HIGHLY_RESTRICTIVE}. Calling this method enables the {@link #RESTRICTION_LEVEL} and
         * {@link #MIXED_NUMBERS} checks, corresponding to Sections 5.1 and 5.2 of UTS 39. To customize which checks are
         * to be performed by {@link SpoofChecker#failsChecks}, see {@link #setChecks}.
         *
         * @param restrictionLevel
         *            The loosest restriction level allowed.
         * @return self
         */
        public Builder setRestrictionLevel(RestrictionLevel restrictionLevel) {
            fRestrictionLevel = restrictionLevel;
            fChecks |= RESTRICTION_LEVEL | MIXED_NUMBERS;
            return this;
        }

        /*
         * *****************************************************************************
         * Internal classes for compiling confusable data into its binary (runtime) form.
         * *****************************************************************************
         */
        // ---------------------------------------------------------------------
        //
        // buildConfusableData Compile the source confusable data, as defined by
        // the Unicode data file confusables.txt, into the binary
        // structures used by the confusable detector.
        //
        // The binary structures are described in uspoof_impl.h
        //
        // 1. parse the data, making a hash table mapping from a codepoint to a String.
        //
        // 2. Sort all of the strings encountered by length, since they will need to
        // be stored in that order in the final string table.
        // TODO: Sorting these strings by length is no longer needed since the removal of
        // the string lengths table.  This logic can be removed to save processing time
        // when building confusables data.
        //
        // 3. Build a list of keys (UChar32s) from the mapping table. Sort the
        // list because that will be the ordering of our runtime table.
        //
        // 4. Generate the run time string table. This is generated before the key & value
        // table because we need the string indexes when building those tables.
        //
        // 5. Build the run-time key and value table. These are parallel tables, and
        // are built at the same time

        // class ConfusabledataBuilder
        // An instance of this class exists while the confusable data is being built from source.
        // It encapsulates the intermediate data structures that are used for building.
        // It exports one static function, to do a confusable data build.
        private static class ConfusabledataBuilder {

            private Hashtable<Integer, SPUString> fTable;
            private UnicodeSet fKeySet; // A set of all keys (UChar32s) that go into the
                                        // four mapping tables.

            // The compiled data is first assembled into the following four collections,
            // then output to the builder's SpoofData object.
            private StringBuffer fStringTable;
            private ArrayList<Integer> fKeyVec;
            private ArrayList<Integer> fValueVec;
            private SPUStringPool stringPool;
            private Pattern fParseLine;
            private Pattern fParseHexNum;
            private int fLineNum;

            ConfusabledataBuilder() {
                fTable = new Hashtable<>();
                fKeySet = new UnicodeSet();
                fKeyVec = new ArrayList<>();
                fValueVec = new ArrayList<>();
                stringPool = new SPUStringPool();
            }

            void build(Reader confusables, SpoofData dest) throws ParseException, java.io.IOException {
                StringBuffer fInput = new StringBuffer();

                // Convert the user input data from UTF-8 to char (UTF-16)
                LineNumberReader lnr = new LineNumberReader(confusables);
                do {
                    String line = lnr.readLine();
                    if (line == null) {
                        break;
                    }
                    fInput.append(line);
                    fInput.append('\n');
                } while (true);

                // Regular Expression to parse a line from Confusables.txt. The expression will match
                // any line. What was matched is determined by examining which capture groups have a match.
                // Capture Group 1: the source char
                // Capture Group 2: the replacement chars
                // Capture Group 3-6 the table type, SL, SA, ML, or MA (deprecated)
                // Capture Group 7: A blank or comment only line.
                // Capture Group 8: A syntactically invalid line. Anything that didn't match before.
                // Example Line from the confusables.txt source file:
                // "1D702 ; 006E 0329 ; SL # MATHEMATICAL ITALIC SMALL ETA ... "
                fParseLine = Pattern.compile("(?m)^[ \\t]*([0-9A-Fa-f]+)[ \\t]+;" + // Match the source char
                        "[ \\t]*([0-9A-Fa-f]+" + // Match the replacement char(s)
                        "(?:[ \\t]+[0-9A-Fa-f]+)*)[ \\t]*;" + // (continued)
                        "\\s*(?:(SL)|(SA)|(ML)|(MA))" + // Match the table type
                        "[ \\t]*(?:#.*?)?$" + // Match any trailing #comment
                        "|^([ \\t]*(?:#.*?)?)$" + // OR match empty lines or lines with only a #comment
                        "|^(.*?)$"); // OR match any line, which catches illegal lines.

                // Regular expression for parsing a hex number out of a space-separated list of them.
                // Capture group 1 gets the number, with spaces removed.
                fParseHexNum = Pattern.compile("\\s*([0-9A-F]+)");

                // Zap any Byte Order Mark at the start of input. Changing it to a space
                // is benign given the syntax of the input.
                if (fInput.charAt(0) == 0xfeff) {
                    fInput.setCharAt(0, (char) 0x20);
                }

                // Parse the input, one line per iteration of this loop.
                Matcher matcher = fParseLine.matcher(fInput);
                while (matcher.find()) {
                    fLineNum++;
                    if (matcher.start(7) >= 0) {
                        // this was a blank or comment line.
                        continue;
                    }
                    if (matcher.start(8) >= 0) {
                        // input file syntax error.
                        // status = U_PARSE_ERROR;
                        throw new ParseException(
                                "Confusables, line " + fLineNum + ": Unrecognized Line: " + matcher.group(8),
                                matcher.start(8));
                    }

                    // We have a good input line. Extract the key character and mapping
                    // string, and
                    // put them into the appropriate mapping table.
                    int keyChar = Integer.parseInt(matcher.group(1), 16);
                    if (keyChar > 0x10ffff) {
                        throw new ParseException(
                                "Confusables, line " + fLineNum + ": Bad code point: " + matcher.group(1),
                                matcher.start(1));
                    }
                    Matcher m = fParseHexNum.matcher(matcher.group(2));

                    StringBuilder mapString = new StringBuilder();
                    while (m.find()) {
                        int c = Integer.parseInt(m.group(1), 16);
                        if (c > 0x10ffff) {
                            throw new ParseException(
                                    "Confusables, line " + fLineNum + ": Bad code point: " + Integer.toString(c, 16),
                                    matcher.start(2));
                        }
                        mapString.appendCodePoint(c);
                    }
                    assert (mapString.length() >= 1);

                    // Put the map (value) string into the string pool
                    // This a little like a Java intern() - any duplicates will be
                    // eliminated.
                    SPUString smapString = stringPool.addString(mapString.toString());

                    // Add the char . string mapping to the table.
                    // For Unicode 8, the SL, SA and ML tables have been discontinued.
                    // All input data from confusables.txt is tagged MA.
                    fTable.put(keyChar, smapString);

                    fKeySet.add(keyChar);
                }

                // Input data is now all parsed and collected.
                // Now create the run-time binary form of the data.
                //
                // This is done in two steps. First the data is assembled into vectors and strings,
                // for ease of construction, then the contents of these collections are copied
                // into the actual SpoofData object.

                // Build up the string array, and record the index of each string therein
                // in the (build time only) string pool.
                // Strings of length one are not entered into the strings array.
                // (Strings in the table are sorted by length)

                stringPool.sort();
                fStringTable = new StringBuffer();
                int poolSize = stringPool.size();
                int i;
                for (i = 0; i < poolSize; i++) {
                    SPUString s = stringPool.getByIndex(i);
                    int strLen = s.fStr.length();
                    int strIndex = fStringTable.length();
                    if (strLen == 1) {
                        // strings of length one do not get an entry in the string table.
                        // Keep the single string character itself here, which is the same
                        // convention that is used in the final run-time string table index.
                        s.fCharOrStrTableIndex = s.fStr.charAt(0);
                    } else {
                        s.fCharOrStrTableIndex = strIndex;
                        fStringTable.append(s.fStr);
                    }
                }

                // Construct the compile-time Key and Value table.
                //
                // The keys in the Key table follow the format described in uspoof.h for the
                // Cfu confusables data structure.
                //
                // Starting in ICU 58, each code point has exactly one entry in the data
                // structure.

                for (String keyCharStr : fKeySet) {
                    int keyChar = keyCharStr.codePointAt(0);
                    SPUString targetMapping = fTable.get(keyChar);
                    assert targetMapping != null;

                    // Throw a sane exception if trying to consume a long string.  Otherwise,
                    // codePointAndLengthToKey will throw an assertion error.
                    if (targetMapping.fStr.length() > 256) {
                        throw new IllegalArgumentException("Confusable prototypes cannot be longer than 256 entries.");
                    }

                    int key = ConfusableDataUtils.codePointAndLengthToKey(keyChar, targetMapping.fStr.length());
                    int value = targetMapping.fCharOrStrTableIndex;

                    fKeyVec.add(key);
                    fValueVec.add(value);
                }

                // Put the assembled data into the destination SpoofData object.

                // The Key Table
                // While copying the keys to the output array,
                // also sanity check that the keys are sorted.
                int numKeys = fKeyVec.size();
                dest.fCFUKeys = new int[numKeys];
                int previousCodePoint = 0;
                for (i = 0; i < numKeys; i++) {
                    int key = fKeyVec.get(i);
                    int codePoint = ConfusableDataUtils.keyToCodePoint(key);
                    // strictly greater because there can be only one entry per code point
                    assert codePoint > previousCodePoint;
                    dest.fCFUKeys[i] = key;
                    previousCodePoint = codePoint;
                }

                // The Value Table, parallels the key table
                int numValues = fValueVec.size();
                assert (numKeys == numValues);
                dest.fCFUValues = new short[numValues];
                i = 0;
                for (int value : fValueVec) {
                    assert (value < 0xffff);
                    dest.fCFUValues[i++] = (short) value;
                }

                // The Strings Table.
                dest.fCFUStrings = fStringTable.toString();
            }

            public static void buildConfusableData(Reader confusables, SpoofData dest)
                    throws java.io.IOException, ParseException {
                ConfusabledataBuilder builder = new ConfusabledataBuilder();
                builder.build(confusables, dest);
            }

            /*
             * *****************************************************************************
             * Internal classes for compiling confusable data into its binary (runtime) form.
             * *****************************************************************************
             */
            // SPUString
            // Holds a string that is the result of one of the mappings defined
            // by the confusable mapping data (confusables.txt from Unicode.org)
            // Instances of SPUString exist during the compilation process only.

            private static class SPUString {
                String fStr; // The actual string.
                int fCharOrStrTableIndex; // Index into the final runtime data for this string.
                // (or, for length 1, the single string char itself,
                // there being no string table entry for it.)

                SPUString(String s) {
                    fStr = s;
                    fCharOrStrTableIndex = 0;
                }
            }

            // Comparison function for ordering strings in the string pool.
            // Compare by length first, then, within a group of the same length,
            // by code point order.

            private static class SPUStringComparator implements Comparator<SPUString> {
                @Override
                public int compare(SPUString sL, SPUString sR) {
                    int lenL = sL.fStr.length();
                    int lenR = sR.fStr.length();
                    if (lenL < lenR) {
                        return -1;
                    } else if (lenL > lenR) {
                        return 1;
                    } else {
                        return sL.fStr.compareTo(sR.fStr);
                    }
                }

                final static SPUStringComparator INSTANCE = new SPUStringComparator();
            }

            // String Pool A utility class for holding the strings that are the result of
            // the spoof mappings. These strings will utimately end up in the
            // run-time String Table.
            // This is sort of like a sorted set of strings, except that ICU's anemic
            // built-in collections don't support those, so it is implemented with a
            // combination of a uhash and a Vector.
            private static class SPUStringPool {
                public SPUStringPool() {
                    fVec = new Vector<>();
                    fHash = new Hashtable<>();
                }

                public int size() {
                    return fVec.size();
                }

                // Get the n-th string in the collection.
                public SPUString getByIndex(int index) {
                    SPUString retString = fVec.elementAt(index);
                    return retString;
                }

                // Add a string. Return the string from the table.
                // If the input parameter string is already in the table, delete the
                // input parameter and return the existing string.
                public SPUString addString(String src) {
                    SPUString hashedString = fHash.get(src);
                    if (hashedString == null) {
                        hashedString = new SPUString(src);
                        fHash.put(src, hashedString);
                        fVec.addElement(hashedString);
                    }
                    return hashedString;
                }

                // Sort the contents; affects the ordering of getByIndex().
                public void sort() {
                    Collections.sort(fVec, SPUStringComparator.INSTANCE);
                }

                private Vector<SPUString> fVec; // Elements are SPUString *
                private Hashtable<String, SPUString> fHash; // Key: Value:
            }

        }
    }

    /**
     * Get the Restriction Level that is being tested.
     *
     * @return The restriction level
     * @deprecated This API is ICU internal only.
     * @hide draft / provisional / internal are hidden on Android
     */
    @Deprecated
    public RestrictionLevel getRestrictionLevel() {
        return fRestrictionLevel;
    }

    /**
     * Get the set of checks that this Spoof Checker has been configured to perform.
     *
     * @return The set of checks that this spoof checker will perform.
     */
    public int getChecks() {
        return fChecks;
    }

    /**
     * Get a read-only set of locales for the scripts that are acceptable in strings to be checked. If no limitations on
     * scripts have been specified, an empty set will be returned.
     *
     * setAllowedChars() will reset the list of allowed locales to be empty.
     *
     * The returned set may not be identical to the originally specified set that is supplied to setAllowedLocales();
     * the information other than languages from the originally specified locales may be omitted.
     *
     * @return A set of locales corresponding to the acceptable scripts.
     */
    public Set<ULocale> getAllowedLocales() {
        return Collections.unmodifiableSet(fAllowedLocales);
    }

    /**
     * Get a set of {@link java.util.Locale} instances for the scripts that are acceptable in strings to be checked. If
     * no limitations on scripts have been specified, an empty set will be returned.
     *
     * @return A set of locales corresponding to the acceptable scripts.
     */
    public Set<Locale> getAllowedJavaLocales() {
        HashSet<Locale> locales = new HashSet<>(fAllowedLocales.size());
        for (ULocale uloc : fAllowedLocales) {
            locales.add(uloc.toLocale());
        }
        return locales;
    }

    /**
     * Get a UnicodeSet for the characters permitted in an identifier. This corresponds to the limits imposed by the Set
     * Allowed Characters functions. Limitations imposed by other checks will not be reflected in the set returned by
     * this function.
     *
     * The returned set will be frozen, meaning that it cannot be modified by the caller.
     *
     * @return A UnicodeSet containing the characters that are permitted by the CHAR_LIMIT test.
     */
    public UnicodeSet getAllowedChars() {
        return fAllowedCharsSet;
    }

    /**
     * A struct-like class to hold the results of a Spoof Check operation. Tells which check(s) have failed.
     *
     * @hide Only a subset of ICU is exposed in Android
     */
    public static class CheckResult {
        /**
         * Indicates which of the spoof check(s) have failed. The value is a bitwise OR of the constants for the tests
         * in question: RESTRICTION_LEVEL, CHAR_LIMIT, and so on.
         *
         * @see Builder#setChecks
         */
        public int checks;

        /**
         * The index of the first string position that failed a check.
         *
         * @deprecated ICU 51. No longer supported. Always set to zero.
         */
        @Deprecated
        public int position;

        /**
         * The numerics found in the string, if MIXED_NUMBERS was set; otherwise null.  The set will contain the zero
         * digit from each decimal number system found in the input string.
         */
        public UnicodeSet numerics;

        /**
         * The restriction level that the text meets, if RESTRICTION_LEVEL is set; otherwise null.
         */
        public RestrictionLevel restrictionLevel;

        /**
         * Default constructor
         */
        public CheckResult() {
            checks = 0;
            position = 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("checks:");
            if (checks == 0) {
                sb.append(" none");
            } else if (checks == ALL_CHECKS) {
                sb.append(" all");
            } else {
                if ((checks & SINGLE_SCRIPT_CONFUSABLE) != 0) {
                    sb.append(" SINGLE_SCRIPT_CONFUSABLE");
                }
                if ((checks & MIXED_SCRIPT_CONFUSABLE) != 0) {
                    sb.append(" MIXED_SCRIPT_CONFUSABLE");
                }
                if ((checks & WHOLE_SCRIPT_CONFUSABLE) != 0) {
                    sb.append(" WHOLE_SCRIPT_CONFUSABLE");
                }
                if ((checks & ANY_CASE) != 0) {
                    sb.append(" ANY_CASE");
                }
                if ((checks & RESTRICTION_LEVEL) != 0) {
                    sb.append(" RESTRICTION_LEVEL");
                }
                if ((checks & INVISIBLE) != 0) {
                    sb.append(" INVISIBLE");
                }
                if ((checks & CHAR_LIMIT) != 0) {
                    sb.append(" CHAR_LIMIT");
                }
                if ((checks & MIXED_NUMBERS) != 0) {
                    sb.append(" MIXED_NUMBERS");
                }
            }
            sb.append(", numerics: ").append(numerics.toPattern(false));
            sb.append(", position: ").append(position);
            sb.append(", restrictionLevel: ").append(restrictionLevel);
            return sb.toString();
        }
    }

    /**
     * Check the specified string for possible security issues. The text to be checked will typically be an identifier
     * of some sort. The set of checks to be performed was specified when building the SpoofChecker.
     *
     * @param text
     *            A String to be checked for possible security issues.
     * @param checkResult
     *            Output parameter, indicates which specific tests failed. May be null if the information is not wanted.
     * @return True there any issue is found with the input string.
     */
    public boolean failsChecks(String text, CheckResult checkResult) {
        int length = text.length();

        int result = 0;
        if (checkResult != null) {
            checkResult.position = 0;
            checkResult.numerics = null;
            checkResult.restrictionLevel = null;
        }

        if (0 != (this.fChecks & RESTRICTION_LEVEL)) {
            RestrictionLevel textRestrictionLevel = getRestrictionLevel(text);
            if (textRestrictionLevel.compareTo(fRestrictionLevel) > 0) {
                result |= RESTRICTION_LEVEL;
            }
            if (checkResult != null) {
                checkResult.restrictionLevel = textRestrictionLevel;
            }
        }

        if (0 != (this.fChecks & MIXED_NUMBERS)) {
            UnicodeSet numerics = new UnicodeSet();
            getNumerics(text, numerics);
            if (numerics.size() > 1) {
                result |= MIXED_NUMBERS;
            }
            if (checkResult != null) {
                checkResult.numerics = numerics;
            }
        }

        if (0 != (this.fChecks & HIDDEN_OVERLAY)) {
            int index = findHiddenOverlay(text);
            if (index != -1) {
                result |= HIDDEN_OVERLAY;
            }
        }

        if (0 != (this.fChecks & CHAR_LIMIT)) {
            int i;
            int c;
            for (i = 0; i < length;) {
                // U16_NEXT(text, i, length, c);
                c = Character.codePointAt(text, i);
                i = Character.offsetByCodePoints(text, i, 1);
                if (!this.fAllowedCharsSet.contains(c)) {
                    result |= CHAR_LIMIT;
                    break;
                }
            }
        }

        if (0 != (this.fChecks & INVISIBLE)) {
            // This check needs to be done on NFD input
            String nfdText = nfdNormalizer.normalize(text);

            // scan for more than one occurrence of the same non-spacing mark
            // in a sequence of non-spacing marks.
            int i;
            int c;
            int firstNonspacingMark = 0;
            boolean haveMultipleMarks = false;
            UnicodeSet marksSeenSoFar = new UnicodeSet(); // Set of combining marks in a
                                                          // single combining sequence.
            for (i = 0; i < length;) {
                c = Character.codePointAt(nfdText, i);
                i = Character.offsetByCodePoints(nfdText, i, 1);
                if (Character.getType(c) != UCharacterCategory.NON_SPACING_MARK) {
                    firstNonspacingMark = 0;
                    if (haveMultipleMarks) {
                        marksSeenSoFar.clear();
                        haveMultipleMarks = false;
                    }
                    continue;
                }
                if (firstNonspacingMark == 0) {
                    firstNonspacingMark = c;
                    continue;
                }
                if (!haveMultipleMarks) {
                    marksSeenSoFar.add(firstNonspacingMark);
                    haveMultipleMarks = true;
                }
                if (marksSeenSoFar.contains(c)) {
                    // report the error, and stop scanning.
                    // No need to find more than the first failure.
                    result |= INVISIBLE;
                    break;
                }
                marksSeenSoFar.add(c);
            }
        }
        if (checkResult != null) {
            checkResult.checks = result;
        }
        return (0 != result);
    }

    /**
     * Check the specified string for possible security issues. The text to be checked will typically be an identifier
     * of some sort. The set of checks to be performed was specified when building the SpoofChecker.
     *
     * @param text
     *            A String to be checked for possible security issues.
     * @return True there any issue is found with the input string.
     */
    public boolean failsChecks(String text) {
        return failsChecks(text, null);
    }

    /**
     * Check whether two specified strings are visually confusable. The types of confusability to be tested - single
     * script, mixed script, or whole script - are determined by the check options set for the SpoofChecker.
     *
     * The tests to be performed are controlled by the flags SINGLE_SCRIPT_CONFUSABLE MIXED_SCRIPT_CONFUSABLE
     * WHOLE_SCRIPT_CONFUSABLE At least one of these tests must be selected.
     *
     * ANY_CASE is a modifier for the tests. Select it if the identifiers may be of mixed case. If identifiers are case
     * folded for comparison and display to the user, do not select the ANY_CASE option.
     *
     *
     * @param s1
     *            The first of the two strings to be compared for confusability.
     * @param s2
     *            The second of the two strings to be compared for confusability.
     * @return Non-zero if s1 and s1 are confusable. If not 0, the value will indicate the type(s) of confusability
     *         found, as defined by spoof check test constants.
     */
    public int areConfusable(String s1, String s2) {
        //
        // See section 4 of UTS #39 for the algorithm for checking whether two strings are confusable,
        // and for definitions of the types (single, whole, mixed-script) of confusables.

        // We only care about a few of the check flags. Ignore the others.
        // If no tests relevant to this function have been specified, signal an error.
        // TODO: is this really the right thing to do? It's probably an error on
        // the caller's part, but logically we would just return 0 (no error).
        if ((this.fChecks & CONFUSABLE) == 0) {
            throw new IllegalArgumentException("No confusable checks are enabled.");
        }

        // Compute the skeletons and check for confusability.
        String s1Skeleton = getSkeleton(s1);
        String s2Skeleton = getSkeleton(s2);
        if (!s1Skeleton.equals(s2Skeleton)) {
            return 0;
        }

        // If we get here, the strings are confusable. Now we just need to set the flags for the appropriate classes
        // of confusables according to UTS 39 section 4.
        // Start by computing the resolved script sets of s1 and s2.
        ScriptSet s1RSS = new ScriptSet();
        getResolvedScriptSet(s1, s1RSS);
        ScriptSet s2RSS = new ScriptSet();
        getResolvedScriptSet(s2, s2RSS);

        // Turn on all applicable flags
        int result = 0;
        if (s1RSS.intersects(s2RSS)) {
            result |= SINGLE_SCRIPT_CONFUSABLE;
        } else {
            result |= MIXED_SCRIPT_CONFUSABLE;
            if (!s1RSS.isEmpty() && !s2RSS.isEmpty()) {
                result |= WHOLE_SCRIPT_CONFUSABLE;
            }
        }

        // Turn off flags that the user doesn't want
        return result & fChecks;
    }

    /**
     * Check whether two specified strings are visually when displayed in a paragraph with the given direction.
     * The types of confusability to be tested—single script, mixed script, or whole script—are determined by the check options set for the SpoofChecker.
     *
     * The tests to be performed are controlled by the flags SINGLE_SCRIPT_CONFUSABLE MIXED_SCRIPT_CONFUSABLE
     * WHOLE_SCRIPT_CONFUSABLE At least one of these tests must be selected.
     *
     * ANY_CASE is a modifier for the tests. Select it if the identifiers may be of mixed case. If identifiers are case
     * folded for comparison and display to the user, do not select the ANY_CASE option.
     *
     *
     * @param direction The paragraph direction with which the identifiers are displayed.
     *                  Must be either {@link Bidi#DIRECTION_LEFT_TO_RIGHT} or {@link Bidi#DIRECTION_RIGHT_TO_LEFT}.
     * @param s1
     *            The first of the two strings to be compared for confusability.
     * @param s2
     *            The second of the two strings to be compared for confusability.
     * @return Non-zero if s1 and s1 are confusable. If not 0, the value will indicate the type(s) of confusability
     *         found, as defined by spoof check test constants.
     */
    public int areConfusable(int direction, CharSequence s1, CharSequence s2) {
        //
        // See section 4 of UTS #39 for the algorithm for checking whether two strings are confusable,
        // and for definitions of the types (single, whole, mixed-script) of confusables.

        // We only care about a few of the check flags. Ignore the others.
        // If no tests relevant to this function have been specified, signal an error.
        // TODO: is this really the right thing to do? It's probably an error on
        // the caller's part, but logically we would just return 0 (no error).
        if ((this.fChecks & CONFUSABLE) == 0) {
            throw new IllegalArgumentException("No confusable checks are enabled.");
        }

        // Compute the skeletons and check for confusability.
        String s1Skeleton = getBidiSkeleton(direction, s1);
        String s2Skeleton = getBidiSkeleton(direction, s2);
        if (!s1Skeleton.equals(s2Skeleton)) {
            return 0;
        }

        // If we get here, the strings are confusable. Now we just need to set the flags for the appropriate classes
        // of confusables according to UTS 39 section 4.
        // Start by computing the resolved script sets of s1 and s2.
        ScriptSet s1RSS = new ScriptSet();
        getResolvedScriptSet(s1, s1RSS);
        ScriptSet s2RSS = new ScriptSet();
        getResolvedScriptSet(s2, s2RSS);

        // Turn on all applicable flags
        int result = 0;
        if (s1RSS.intersects(s2RSS)) {
            result |= SINGLE_SCRIPT_CONFUSABLE;
        } else {
            result |= MIXED_SCRIPT_CONFUSABLE;
            if (!s1RSS.isEmpty() && !s2RSS.isEmpty()) {
                result |= WHOLE_SCRIPT_CONFUSABLE;
            }
        }

        // Turn off flags that the user doesn't want
        result &= fChecks;

        return result;
    }

    /**
     * Get the "bidiSkeleton" for an identifier string and a direction.
     * Skeletons are a transformation of the input string;
     * Two identifiers are LTR-confusable if their LTR bidiSkeletons are identical;
     * they are RTL-confusable if their RTL bidiSkeletons are identical.
     * See Unicode Technical Standard #39 for additional information:
     * https://www.unicode.org/reports/tr39/#Confusable_Detection.
     *
     * Using skeletons directly makes it possible to quickly check whether an identifier is confusable with any of some
     * large set of existing identifiers, by creating an efficiently searchable collection of the skeletons.
     *
     * Skeletons are computed using the algorithm and data described in UTS #39.
     *
     * @param direction The paragraph direction with which the string is displayed.
     *                  Must be either {@link Bidi#DIRECTION_LEFT_TO_RIGHT} or {@link Bidi#DIRECTION_RIGHT_TO_LEFT}.
     * @param str The input string whose bidiSkeleton will be generated.
     * @return The output skeleton string.
     */
    public String getBidiSkeleton(int direction, CharSequence str) {
        if (direction != Bidi.DIRECTION_LEFT_TO_RIGHT && direction != Bidi.DIRECTION_RIGHT_TO_LEFT) {
            throw new IllegalArgumentException("direction should be DIRECTION_LEFT_TO_RIGHT or DIRECTION_RIGHT_TO_LEFT");
        }
        Bidi bidi = new Bidi(str.toString(), direction);
        return getSkeleton(bidi.writeReordered(Bidi.KEEP_BASE_COMBINING | Bidi.DO_MIRRORING));
    }

    /**
     * Get the "skeleton" for an identifier string. Skeletons are a transformation of the input string; Two strings are
     * confusable if their skeletons are identical. See Unicode UAX 39 for additional information.
     *
     * Using skeletons directly makes it possible to quickly check whether an identifier is confusable with any of some
     * large set of existing identifiers, by creating an efficiently searchable collection of the skeletons.
     *
     * Skeletons are computed using the algorithm and data described in Unicode UAX 39.
     *
     * @param str
     *            The input string whose skeleton will be generated.
     * @return The output skeleton string.
     */
    public String getSkeleton(CharSequence str) {
        // Apply the skeleton mapping to the NFD normalized input string
        // Accumulate the skeleton, possibly unnormalized, in a String.
        String nfdId = nfdNormalizer.normalize(str);
        int normalizedLen = nfdId.length();
        StringBuilder skelSB = new StringBuilder();
        for (int inputIndex = 0; inputIndex < normalizedLen;) {
            int c = Character.codePointAt(nfdId, inputIndex);
            inputIndex += Character.charCount(c);
            if (!UCharacter.hasBinaryProperty(c, UProperty.DEFAULT_IGNORABLE_CODE_POINT)) {
                this.fSpoofData.confusableLookup(c, skelSB);
            }
        }
        String skelStr = skelSB.toString();
        skelStr = nfdNormalizer.normalize(skelStr);
        return skelStr;
    }

    /**
     * Calls {@link SpoofChecker#getSkeleton(CharSequence id)}. Starting with ICU 55, the "type" parameter has been
     * ignored, and starting with ICU 58, this function has been deprecated.
     *
     * @param type
     *            No longer supported. Prior to ICU 55, was used to specify the mapping table SL, SA, ML, or MA.
     * @param id
     *            The input identifier whose skeleton will be generated.
     * @return The output skeleton string.
     *
     * @deprecated ICU 58
     */
    @Deprecated
    public String getSkeleton(int type, String id) {
        return getSkeleton(id);
    }

    /**
     * Equality function. Return true if the two SpoofChecker objects incorporate the same confusable data and have
     * enabled the same set of checks.
     *
     * @param other
     *            the SpoofChecker being compared with.
     * @return true if the two SpoofCheckers are equal.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SpoofChecker)) {
            return false;
        }
        SpoofChecker otherSC = (SpoofChecker) other;
        if (fSpoofData != otherSC.fSpoofData && fSpoofData != null && !fSpoofData.equals(otherSC.fSpoofData)) {
            return false;
        }
        if (fChecks != otherSC.fChecks) {
            return false;
        }
        if (fAllowedLocales != otherSC.fAllowedLocales && fAllowedLocales != null
                && !fAllowedLocales.equals(otherSC.fAllowedLocales)) {
            return false;
        }
        if (fAllowedCharsSet != otherSC.fAllowedCharsSet && fAllowedCharsSet != null
                && !fAllowedCharsSet.equals(otherSC.fAllowedCharsSet)) {
            return false;
        }
        if (fRestrictionLevel != otherSC.fRestrictionLevel) {
            return false;
        }
        return true;
    }

    /**
     * Overrides {@link Object#hashCode()}.
     */
    @Override
    public int hashCode() {
        return fChecks
                ^ fSpoofData.hashCode()
                ^ fAllowedLocales.hashCode()
                ^ fAllowedCharsSet.hashCode()
                ^ fRestrictionLevel.ordinal();
    }

    /**
     * Computes the augmented script set for a code point, according to UTS 39 section 5.1.
     */
    private static void getAugmentedScriptSet(int codePoint, ScriptSet result) {
        result.clear();
        UScript.getScriptExtensions(codePoint, result);

        // Section 5.1 step 1
        if (result.get(UScript.HAN)) {
            result.set(UScript.HAN_WITH_BOPOMOFO);
            result.set(UScript.JAPANESE);
            result.set(UScript.KOREAN);
        }
        if (result.get(UScript.HIRAGANA)) {
            result.set(UScript.JAPANESE);
        }
        if (result.get(UScript.KATAKANA)) {
            result.set(UScript.JAPANESE);
        }
        if (result.get(UScript.HANGUL)) {
            result.set(UScript.KOREAN);
        }
        if (result.get(UScript.BOPOMOFO)) {
            result.set(UScript.HAN_WITH_BOPOMOFO);
        }

        // Section 5.1 step 2
        if (result.get(UScript.COMMON) || result.get(UScript.INHERITED)) {
            result.setAll();
        }
    }

    /**
     * Computes the resolved script set for a string, according to UTS 39 section 5.1.
     */
    private void getResolvedScriptSet(CharSequence input, ScriptSet result) {
        getResolvedScriptSetWithout(input, UScript.CODE_LIMIT, result);
    }

    /**
     * Computes the resolved script set for a string, omitting characters having the specified script. If
     * UScript.CODE_LIMIT is passed as the second argument, all characters are included.
     */
    private void getResolvedScriptSetWithout(CharSequence input, int script, ScriptSet result) {
        result.setAll();

        ScriptSet temp = new ScriptSet();
        for (int utf16Offset = 0; utf16Offset < input.length();) {
            int codePoint = Character.codePointAt(input, utf16Offset);
            utf16Offset += Character.charCount(codePoint);

            // Compute the augmented script set for the character
            getAugmentedScriptSet(codePoint, temp);

            // Intersect the augmented script set with the resolved script set, but only if the character doesn't
            // have the script specified in the function call
            if (script == UScript.CODE_LIMIT || !temp.get(script)) {
                result.and(temp);
            }
        }
    }

    /**
     * Computes the set of numerics for a string, according to UTS 39 section 5.3.
     */
    private void getNumerics(String input, UnicodeSet result) {
        result.clear();

        for (int utf16Offset = 0; utf16Offset < input.length();) {
            int codePoint = Character.codePointAt(input, utf16Offset);
            utf16Offset += Character.charCount(codePoint);

            // Store a representative character for each kind of decimal digit
            if (UCharacter.getType(codePoint) == UCharacterCategory.DECIMAL_DIGIT_NUMBER) {
                // Store the zero character as a representative for comparison.
                // Unicode guarantees it is codePoint - value
                result.add(codePoint - UCharacter.getNumericValue(codePoint));
            }
        }
    }

    /**
     * Computes the restriction level of a string, according to UTS 39 section 5.2.
     */
    private RestrictionLevel getRestrictionLevel(String input) {
        // Section 5.2 step 1:
        if (!fAllowedCharsSet.containsAll(input)) {
            return RestrictionLevel.UNRESTRICTIVE;
        }

        // Section 5.2 step 2:
        if (ASCII.containsAll(input)) {
            return RestrictionLevel.ASCII;
        }

        // Section 5.2 steps 3:
        ScriptSet resolvedScriptSet = new ScriptSet();
        getResolvedScriptSet(input, resolvedScriptSet);

        // Section 5.2 step 4:
        if (!resolvedScriptSet.isEmpty()) {
            return RestrictionLevel.SINGLE_SCRIPT_RESTRICTIVE;
        }

        // Section 5.2 step 5:
        ScriptSet resolvedNoLatn = new ScriptSet();
        getResolvedScriptSetWithout(input, UScript.LATIN, resolvedNoLatn);

        // Section 5.2 step 6:
        if (resolvedNoLatn.get(UScript.HAN_WITH_BOPOMOFO) || resolvedNoLatn.get(UScript.JAPANESE)
                || resolvedNoLatn.get(UScript.KOREAN)) {
            return RestrictionLevel.HIGHLY_RESTRICTIVE;
        }

        // Section 5.2 step 7:
        if (!resolvedNoLatn.isEmpty() && !resolvedNoLatn.get(UScript.CYRILLIC) && !resolvedNoLatn.get(UScript.GREEK)
                && !resolvedNoLatn.get(UScript.CHEROKEE)) {
            return RestrictionLevel.MODERATELY_RESTRICTIVE;
        }

        // Section 5.2 step 8:
        return RestrictionLevel.MINIMALLY_RESTRICTIVE;
    }

    int findHiddenOverlay(String input) {
        boolean sawLeadCharacter = false;
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<input.length();) {
            int cp = input.codePointAt(i);
            if (sawLeadCharacter && cp == 0x0307) {
                return i;
            }
            int combiningClass = UCharacter.getCombiningClass(cp);
            // Skip over characters except for those with combining class 0 (non-combining characters) or with
            // combining class 230 (same class as U+0307)
            assert UCharacter.getCombiningClass(0x0307) == 230;
            if (combiningClass == 0 || combiningClass == 230) {
                sawLeadCharacter = isIllegalCombiningDotLeadCharacter(cp, sb);
            }
            i += UCharacter.charCount(cp);
        }
        return -1;
    }

    boolean isIllegalCombiningDotLeadCharacterNoLookup(int cp) {
        return cp == 'i' || cp == 'j' || cp == 'ı' || cp == 'ȷ' || cp == 'l' ||
               UCharacter.hasBinaryProperty(cp, UProperty.SOFT_DOTTED);
    }

    boolean isIllegalCombiningDotLeadCharacter(int cp, StringBuilder sb) {
        if (isIllegalCombiningDotLeadCharacterNoLookup(cp)) {
            return true;
        }
        sb.setLength(0);
        fSpoofData.confusableLookup(cp, sb);
        int finalCp = UCharacter.codePointBefore(sb, sb.length());
        if (finalCp != cp && isIllegalCombiningDotLeadCharacterNoLookup(finalCp)) {
            return true;
        }
        return false;
    }

    // Data Members
    private int fChecks; // Bit vector of checks to perform.
    private SpoofData fSpoofData;
    private Set<ULocale> fAllowedLocales; // The Set of allowed locales.
    private UnicodeSet fAllowedCharsSet; // The UnicodeSet of allowed characters.
    private RestrictionLevel fRestrictionLevel;

    private static Normalizer2 nfdNormalizer = Normalizer2.getNFDInstance();

    // Confusable Mappings Data Structures, version 2.0
    //
    // This description and the corresponding implementation are to be kept
    // in-sync with the copy in icu4c uspoof_impl.h.
    //
    // For the confusable data, we are essentially implementing a map,
    //     key: a code point
    //     value: a string. Most commonly one char in length, but can be more.
    //
    // The keys are stored as a sorted array of 32 bit ints.
    //         bits 0-23 a code point value
    //         bits 24-31 length of value string, in UChars (between 1 and 256 UChars).
    //     The key table is sorted in ascending code point order. (not on the
    //     32 bit int value, the flag bits do not participate in the sorting.)
    //
    //     Lookup is done by means of a binary search in the key table.
    //
    // The corresponding values are kept in a parallel array of 16 bit ints.
    //     If the value string is of length 1, it is literally in the value array.
    //     For longer strings, the value array contains an index into the strings
    //     table.
    //
    // String Table:
    //     The strings table contains all of the value strings (those of length two or greater)
    //     concatenated together into one long char (UTF-16) array.
    //
    //     There is no nul character or other mark between adjacent strings.
    //
    //----------------------------------------------------------------------------
    //
    //  Changes from format version 1 to format version 2:
    //        1) Removal of the whole-script confusable data tables.
    //        2) Removal of the SL/SA/ML/MA and multi-table flags in the key bitmask.
    //        3) Expansion of string length value in the key bitmask from 2 bits to 8 bits.
    //        4) Removal of the string lengths table since 8 bits is sufficient for the
    //           lengths of all entries in confusables.txt.
    //
    private static final class ConfusableDataUtils {
        public static final int FORMAT_VERSION = 2; // version for ICU 58

        public static final int keyToCodePoint(int key) {
            return key & 0x00ffffff;
        }

        public static final int keyToLength(int key) {
            return ((key & 0xff000000) >> 24) + 1;
        }

        public static final int codePointAndLengthToKey(int codePoint, int length) {
            assert (codePoint & 0x00ffffff) == codePoint;
            assert length <= 256;
            return codePoint | ((length - 1) << 24);
        }
    }

    // -------------------------------------------------------------------------------------
    //
    // SpoofData
    //
    // This class corresponds to the ICU SpoofCheck data.
    //
    // The data can originate with the Binary ICU data that is generated in ICU4C,
    // or it can originate from source rules that are compiled in ICU4J.
    //
    // This class does not include the set of checks to be performed, but only
    // data that is serialized into the ICU binary data.
    //
    // Because Java cannot easily wrap binary data like ICU4C, the binary data is
    // copied into Java structures that are convenient for use by the run time code.
    //
    // ---------------------------------------------------------------------------------------
    private static class SpoofData {

        // The Confusable data, Java data structures for.
        int[] fCFUKeys;
        short[] fCFUValues;
        String fCFUStrings;

        private static final int DATA_FORMAT = 0x43667520; // "Cfu "

        private static final class IsAcceptable implements Authenticate {
            @Override
            public boolean isDataVersionAcceptable(byte version[]) {
                return version[0] == ConfusableDataUtils.FORMAT_VERSION || version[1] != 0 || version[2] != 0
                        || version[3] != 0;
            }
        }

        private static final IsAcceptable IS_ACCEPTABLE = new IsAcceptable();

        private static final class DefaultData {
            private static SpoofData INSTANCE = null;
            private static IOException EXCEPTION = null;

            static {
                // Note: Although this is static, the Java runtime can delay execution of this block until
                // the data is actually requested via SpoofData.getDefault().
                try {
                    INSTANCE = new SpoofData(ICUBinary.getRequiredData("confusables.cfu"));
                } catch (IOException e) {
                    EXCEPTION = e;
                }
            }
        }

        /**
         * @return instance for Unicode standard data
         */
        public static SpoofData getDefault() {
            if (DefaultData.EXCEPTION != null) {
                throw new MissingResourceException(
                        "Could not load default confusables data: " + DefaultData.EXCEPTION.getMessage(),
                        "SpoofChecker", "");
            }
            return DefaultData.INSTANCE;
        }

        // SpoofChecker Data constructor for use from data builder.
        // Initializes a new, empty data area that will be populated later.
        private SpoofData() {
        }

        // Constructor for use when creating from prebuilt default data.
        // A ByteBuffer is what the ICU internal data loading functions provide.
        private SpoofData(ByteBuffer bytes) throws java.io.IOException {
            ICUBinary.readHeader(bytes, DATA_FORMAT, IS_ACCEPTABLE);
            bytes.mark();
            readData(bytes);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof SpoofData)) {
                return false;
            }
            SpoofData otherData = (SpoofData) other;
            if (!Arrays.equals(fCFUKeys, otherData.fCFUKeys))
                return false;
            if (!Arrays.equals(fCFUValues, otherData.fCFUValues))
                return false;
            if (!Utility.sameObjects(fCFUStrings, otherData.fCFUStrings) && fCFUStrings != null
                    && !fCFUStrings.equals(otherData.fCFUStrings))
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(fCFUKeys)
                    ^ Arrays.hashCode(fCFUValues)
                    ^ fCFUStrings.hashCode();
        }

        // Set the SpoofChecker data from pre-built binary data in a byte buffer.
        // The binary data format is as described for ICU4C spoof data.
        //
        private void readData(ByteBuffer bytes) throws java.io.IOException {
            int magic = bytes.getInt();
            if (magic != 0x3845fdef) {
                throw new IllegalArgumentException("Bad Spoof Check Data.");
            }
            @SuppressWarnings("unused")
            int dataFormatVersion = bytes.getInt();
            @SuppressWarnings("unused")
            int dataLength = bytes.getInt();

            int CFUKeysOffset = bytes.getInt();
            int CFUKeysSize = bytes.getInt();

            int CFUValuesOffset = bytes.getInt();
            int CFUValuesSize = bytes.getInt();

            int CFUStringTableOffset = bytes.getInt();
            int CFUStringTableSize = bytes.getInt();

            // We have now read the file header, and obtained the position for each
            // of the data items. Now read each in turn, first seeking the
            // input stream to the position of the data item.

            bytes.reset();
            ICUBinary.skipBytes(bytes, CFUKeysOffset);
            fCFUKeys = ICUBinary.getInts(bytes, CFUKeysSize, 0);

            bytes.reset();
            ICUBinary.skipBytes(bytes, CFUValuesOffset);
            fCFUValues = ICUBinary.getShorts(bytes, CFUValuesSize, 0);

            bytes.reset();
            ICUBinary.skipBytes(bytes, CFUStringTableOffset);
            fCFUStrings = ICUBinary.getString(bytes, CFUStringTableSize, 0);
        }

        /**
         * Append the confusable skeleton transform for a single code point to a StringBuilder. The string to be
         * appended will between 1 and 18 characters as of Unicode 9.
         *
         * This is the heart of the confusable skeleton generation implementation.
         */
        public void confusableLookup(int inChar, StringBuilder dest) {
            // Perform a binary search.
            // [lo, hi), i.e lo is inclusive, hi is exclusive.
            // The result after the loop will be in lo.
            int lo = 0;
            int hi = length();
            do {
                int mid = (lo + hi) / 2;
                if (codePointAt(mid) > inChar) {
                    hi = mid;
                } else if (codePointAt(mid) < inChar) {
                    lo = mid;
                } else {
                    // Found result. Break early.
                    lo = mid;
                    break;
                }
            } while (hi - lo > 1);

            // Did we find an entry? If not, the char maps to itself.
            if (codePointAt(lo) != inChar) {
                dest.appendCodePoint(inChar);
                return;
            }

            // Add the element to the string builder and return.
            appendValueTo(lo, dest);
            return;
        }

        /**
         * Return the number of confusable entries in this SpoofData.
         *
         * @return The number of entries.
         */
        public int length() {
            return fCFUKeys.length;
        }

        /**
         * Return the code point (key) at the specified index.
         *
         * @param index
         *            The index within the SpoofData.
         * @return The code point.
         */
        public int codePointAt(int index) {
            return ConfusableDataUtils.keyToCodePoint(fCFUKeys[index]);
        }

        /**
         * Append the confusable skeleton at the specified index to the StringBuilder dest.
         *
         * @param index
         *            The index within the SpoofData.
         * @param dest
         *            The StringBuilder to which to append the skeleton.
         */
        public void appendValueTo(int index, StringBuilder dest) {
            int stringLength = ConfusableDataUtils.keyToLength(fCFUKeys[index]);

            // Value is either a char (for strings of length 1) or
            // an index into the string table (for longer strings)
            short value = fCFUValues[index];
            if (stringLength == 1) {
                dest.append((char) value);
            } else {
                dest.append(fCFUStrings, value, value + stringLength);
            }
        }
    }

    // -------------------------------------------------------------------------------
    //
    // ScriptSet - Script code bit sets.
    // Extends Java BitSet with input/output support and a few helper methods.
    // Note: The I/O is not currently being used, so it has been commented out. If
    // it is needed again, the code can be restored.
    //
    // -------------------------------------------------------------------------------
    static class ScriptSet extends BitSet {

        // Eclipse default value to quell warnings:
        private static final long serialVersionUID = 1L;

        // // The serialized version of this class can hold INT_CAPACITY * 32 scripts.
        // private static final int INT_CAPACITY = 6;
        // private static final long serialVersionUID = INT_CAPACITY;
        // static {
        // assert ScriptSet.INT_CAPACITY * Integer.SIZE <= UScript.CODE_LIMIT;
        // }
        //
        // public ScriptSet() {
        // }
        //
        // public ScriptSet(ByteBuffer bytes) throws java.io.IOException {
        // for (int i = 0; i < INT_CAPACITY; i++) {
        // int bits = bytes.getInt();
        // for (int j = 0; j < Integer.SIZE; j++) {
        // if ((bits & (1 << j)) != 0) {
        // set(i * Integer.SIZE + j);
        // }
        // }
        // }
        // }
        //
        // public void output(DataOutputStream os) throws java.io.IOException {
        // for (int i = 0; i < INT_CAPACITY; i++) {
        // int bits = 0;
        // for (int j = 0; j < Integer.SIZE; j++) {
        // if (get(i * Integer.SIZE + j)) {
        // bits |= (1 << j);
        // }
        // }
        // os.writeInt(bits);
        // }
        // }

        @android.compat.annotation.UnsupportedAppUsage(maxTargetSdk = 30, trackingBug = 170729553)
        ScriptSet() {
        }

        @android.compat.annotation.UnsupportedAppUsage(maxTargetSdk = 30, trackingBug = 170729553)
        public void and(int script) {
            this.clear(0, script);
            this.clear(script + 1, UScript.CODE_LIMIT);
        }

        @android.compat.annotation.UnsupportedAppUsage(maxTargetSdk = 30, trackingBug = 170729553)
        public void setAll() {
            this.set(0, UScript.CODE_LIMIT);
        }

        @android.compat.annotation.UnsupportedAppUsage(maxTargetSdk = 30, trackingBug = 170729553)
        public boolean isFull() {
            return cardinality() == UScript.CODE_LIMIT;
        }

        public void appendStringTo(StringBuilder sb) {
            sb.append("{ ");
            if (isEmpty()) {
                sb.append("- ");
            } else if (isFull()) {
                sb.append("* ");
            } else {
                for (int script = 0; script < UScript.CODE_LIMIT; script++) {
                    if (get(script)) {
                        sb.append(UScript.getShortName(script));
                        sb.append(" ");
                    }
                }
            }
            sb.append("}");
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("<ScriptSet ");
            appendStringTo(sb);
            sb.append(">");
            return sb.toString();
        }
    }
}

/*
 * Copyright 2016 TomeOkin
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
package com.tomeokin.phonenumberutil;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class PhoneNumberUtilCore {
    static final MetadataLoader DEFAULT_METADATA_LOADER = new MetadataLoader() {
        @Override public InputStream loadMetadata(String metadataFileName) {
            return PhoneNumberUtilCore.class.getResourceAsStream(metadataFileName);
        }
    };
    private static final Logger logger = Logger.getLogger(PhoneNumberUtilCore.class.getName());

    /** Flags to use when compiling regular expressions for phone numbers. */
    static final int REGEX_FLAGS = Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE;
    // The minimum and maximum length of the national significant number.
    private static final int MIN_LENGTH_FOR_NSN = 2;

    private static final int NANPA_COUNTRY_CODE = 1;

    // The PLUS_SIGN signifies the international prefix.
    private static final char PLUS_SIGN = '+';
    private static final char STAR_SIGN = '*';

    private static final String RFC3966_EXTN_PREFIX = ";ext=";

    private static final Map<Integer, String> MOBILE_TOKEN_MAPPINGS;
    private static final Map<Character, Character> DIALLABLE_CHAR_MAPPINGS;
    private static final Map<Character, Character> ALPHA_MAPPINGS;
    private static final Map<Character, Character> ALPHA_PHONE_MAPPINGS;
    private static final Map<Character, Character> ALL_PLUS_NUMBER_GROUPING_SYMBOLS;
    private final Map<Integer, List<String>> countryCodeToRegionCodeMap;
    private final Map<String, Integer> regionCodeToCountryCodeMap;

    // The set of regions that share country calling code 1.
    // There are roughly 26 regions.
    // We set the initial capacity of the HashSet to 35 to offer a load factor of roughly 0.75.
    private final Set<String> nanpaRegions = new HashSet<>(35);

    // A cache for frequently used region-specific regular expressions.
    // The initial capacity is set to 100 as this seems to be an optimal value for Android, based on
    // performance measurements.
    private final RegexCache regexCache = new RegexCache(100);

    // The set of regions the library supports.
    // There are roughly 240 of them and we set the initial capacity of the HashSet to 320 to offer a
    // load factor of roughly 0.75.
    private final Set<String> supportedRegions = new HashSet<>(320);

    // The set of country calling codes that map to the non-geo entity region ("001"). This set
    // currently contains < 12 elements so the default capacity of 16 (load factor=0.75) is fine.
    private final Set<Integer> countryCodesForNonGeographicalRegion = new HashSet<>();

    private final MetadataSource metadataSource;

    static {
        HashMap<Integer, String> mobileTokenMap = new HashMap<>();
        mobileTokenMap.put(52, "1");
        mobileTokenMap.put(54, "9");
        MOBILE_TOKEN_MAPPINGS = Collections.unmodifiableMap(mobileTokenMap);

        // Simple ASCII digits map used to populate ALPHA_PHONE_MAPPINGS and
        // ALL_PLUS_NUMBER_GROUPING_SYMBOLS.
        HashMap<Character, Character> asciiDigitMappings = new HashMap<>();
        asciiDigitMappings.put('0', '0');
        asciiDigitMappings.put('1', '1');
        asciiDigitMappings.put('2', '2');
        asciiDigitMappings.put('3', '3');
        asciiDigitMappings.put('4', '4');
        asciiDigitMappings.put('5', '5');
        asciiDigitMappings.put('6', '6');
        asciiDigitMappings.put('7', '7');
        asciiDigitMappings.put('8', '8');
        asciiDigitMappings.put('9', '9');

        HashMap<Character, Character> alphaMap = new HashMap<>(40);
        alphaMap.put('A', '2');
        alphaMap.put('B', '2');
        alphaMap.put('C', '2');
        alphaMap.put('D', '3');
        alphaMap.put('E', '3');
        alphaMap.put('F', '3');
        alphaMap.put('G', '4');
        alphaMap.put('H', '4');
        alphaMap.put('I', '4');
        alphaMap.put('J', '5');
        alphaMap.put('K', '5');
        alphaMap.put('L', '5');
        alphaMap.put('M', '6');
        alphaMap.put('N', '6');
        alphaMap.put('O', '6');
        alphaMap.put('P', '7');
        alphaMap.put('Q', '7');
        alphaMap.put('R', '7');
        alphaMap.put('S', '7');
        alphaMap.put('T', '8');
        alphaMap.put('U', '8');
        alphaMap.put('V', '8');
        alphaMap.put('W', '9');
        alphaMap.put('X', '9');
        alphaMap.put('Y', '9');
        alphaMap.put('Z', '9');
        ALPHA_MAPPINGS = Collections.unmodifiableMap(alphaMap);

        HashMap<Character, Character> combinedMap = new HashMap<>(100);
        combinedMap.putAll(ALPHA_MAPPINGS);
        combinedMap.putAll(asciiDigitMappings);
        ALPHA_PHONE_MAPPINGS = Collections.unmodifiableMap(combinedMap);

        HashMap<Character, Character> diallableCharMap = new HashMap<>();
        diallableCharMap.putAll(asciiDigitMappings);
        diallableCharMap.put(PLUS_SIGN, PLUS_SIGN);
        diallableCharMap.put(STAR_SIGN, STAR_SIGN);
        DIALLABLE_CHAR_MAPPINGS = Collections.unmodifiableMap(diallableCharMap);

        HashMap<Character, Character> allPlusNumberGroupings = new HashMap<>();
        // Put (lower letter -> upper letter) and (upper letter -> upper letter) mappings.
        for (char c : ALPHA_MAPPINGS.keySet()) {
            allPlusNumberGroupings.put(Character.toLowerCase(c), c);
            allPlusNumberGroupings.put(c, c);
        }
        allPlusNumberGroupings.putAll(asciiDigitMappings);
        // Put grouping symbols.
        allPlusNumberGroupings.put('-', '-');
        allPlusNumberGroupings.put('\uFF0D', '-');
        allPlusNumberGroupings.put('\u2010', '-');
        allPlusNumberGroupings.put('\u2011', '-');
        allPlusNumberGroupings.put('\u2012', '-');
        allPlusNumberGroupings.put('\u2013', '-');
        allPlusNumberGroupings.put('\u2014', '-');
        allPlusNumberGroupings.put('\u2015', '-');
        allPlusNumberGroupings.put('\u2212', '-');
        allPlusNumberGroupings.put('/', '/');
        allPlusNumberGroupings.put('\uFF0F', '/');
        allPlusNumberGroupings.put(' ', ' ');
        allPlusNumberGroupings.put('\u3000', ' ');
        allPlusNumberGroupings.put('\u2060', ' ');
        allPlusNumberGroupings.put('.', '.');
        allPlusNumberGroupings.put('\uFF0E', '.');
        ALL_PLUS_NUMBER_GROUPING_SYMBOLS = Collections.unmodifiableMap(allPlusNumberGroupings);
    }

    // Pattern that makes it easy to distinguish whether a region has a unique international dialing
    // prefix or not. If a region has a unique international prefix (e.g. 011 in USA), it will be
    // represented as a string that contains a sequence of ASCII digits. If there are multiple
    // available international prefixes in a region, they will be represented as a regex string that
    // always contains character(s) other than ASCII digits.
    // Note this regex also includes tilde, which signals waiting for the tone.
    private static final Pattern UNIQUE_INTERNATIONAL_PREFIX =
        Pattern.compile("[\\d]+(?:[~\u2053\u223C\uFF5E][\\d]+)?");

    // Regular expression of acceptable punctuation found in phone numbers. This excludes punctuation
    // found as a leading character only.
    // This consists of dash characters, white space characters, full stops, slashes,
    // square brackets, parentheses and tildes. It also includes the letter 'x' as that is found as a
    // placeholder for carrier information in some phone numbers. Full-width variants are also
    // present.
    static final String VALID_PUNCTUATION = "-x\u2010-\u2015\u2212\u30FC\uFF0D-\uFF0F "
        + "\u00A0\u00AD\u200B\u2060\u3000()\uFF08\uFF09\uFF3B\uFF3D.\\[\\]/~\u2053\u223C\uFF5E";

    private static final String DIGITS = "\\p{Nd}";
    // We accept alpha characters in phone numbers, ASCII only, upper and lower case.
    private static final String VALID_ALPHA =
        Arrays.toString(ALPHA_MAPPINGS.keySet().toArray()).replaceAll("[, \\[\\]]", "") + Arrays.toString(
            ALPHA_MAPPINGS.keySet().toArray()).toLowerCase().replaceAll("[, \\[\\]]", "");

    static final String PLUS_CHARS = "+\uFF0B";
    static final Pattern PLUS_CHARS_PATTERN = Pattern.compile("[" + PLUS_CHARS + "]+");

    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[" + VALID_PUNCTUATION + "]+");
    private static final Pattern CAPTURING_DIGIT_PATTERN = Pattern.compile("(" + DIGITS + ")");

    // Regular expression of acceptable characters that may start a phone number for the purposes of
    // parsing. This allows us to strip away meaningless prefixes to phone numbers that may be
    // mistakenly given to us. This consists of digits, the plus symbol and arabic-indic digits. This
    // does not contain alpha characters, although they may be used later in the number. It also does
    // not include other punctuation, as this will be stripped later during parsing and is of no
    // information value when parsing a number.
    private static final String VALID_START_CHAR = "[" + PLUS_CHARS + DIGITS + "]";
    private static final Pattern VALID_START_CHAR_PATTERN = Pattern.compile(VALID_START_CHAR);

    // Regular expression of characters typically used to start a second phone number for the purposes
    // of parsing. This allows us to strip off parts of the number that are actually the start of
    // another number, such as for: (530) 583-6985 x302/x2303 -> the second extension here makes this
    // actually two phone numbers, (530) 583-6985 x302 and (530) 583-6985 x2303. We remove the second
    // extension so that the first number is parsed correctly.
    private static final String SECOND_NUMBER_START = "[\\\\/] *x";
    static final Pattern SECOND_NUMBER_START_PATTERN = Pattern.compile(SECOND_NUMBER_START);

    // Regular expression of trailing characters that we want to remove. We remove all characters that
    // are not alpha or numerical characters. The hash character is retained here, as it may signify
    // the previous block was an extension.
    private static final String UNWANTED_END_CHARS = "[[\\P{N}&&\\P{L}]&&[^#]]+$";
    static final Pattern UNWANTED_END_CHAR_PATTERN = Pattern.compile(UNWANTED_END_CHARS);

    // We use this pattern to check if the phone number has at least three letters in it - if so, then
    // we treat it as a number where some phone-number digits are represented by letters.
    private static final Pattern VALID_ALPHA_PHONE_PATTERN = Pattern.compile("(?:.*?[A-Za-z]){3}.*");

    // Regular expression of viable phone numbers. This is location independent. Checks we have at
    // least three leading digits, and only valid punctuation, alpha characters and
    // digits in the phone number. Does not include extension data.
    // The symbol 'x' is allowed here as valid punctuation since it is often used as a placeholder for
    // carrier codes, for example in Brazilian phone numbers. We also allow multiple "+" characters at
    // the start.
    // Corresponds to the following:
    // [digits]{minLengthNsn}|
    // plus_sign*(([punctuation]|[star])*[digits]){3,}([punctuation]|[star]|[digits]|[alpha])*
    //
    // The first reg-ex is to allow short numbers (two digits long) to be parsed if they are entered
    // as "15" etc, but only if there is no punctuation in them. The second expression restricts the
    // number of digits to three or more, but then allows them to be in international form, and to
    // have alpha-characters and punctuation.
    //
    // Note VALID_PUNCTUATION starts with a -, so must be the first in the range.
    private static final String VALID_PHONE_NUMBER = DIGITS + "{" + MIN_LENGTH_FOR_NSN + "}" + "|" +
        "[" + PLUS_CHARS + "]*+(?:[" + VALID_PUNCTUATION + STAR_SIGN + "]*" + DIGITS + "){3,}[" +
        VALID_PUNCTUATION + STAR_SIGN + VALID_ALPHA + DIGITS + "]*";

    // Default extension prefix to use when formatting. This will be put in front of any extension
    // component of the number, after the main national number is formatted. For example, if you wish
    // the default extension formatting to be " extn: 3456", then you should specify " extn: " here
    // as the default extension prefix. This can be overridden by region-specific preferences.
    private static final String DEFAULT_EXTN_PREFIX = " ext. ";

    // Pattern to capture digits used in an extension. Places a maximum length of "7" for an
    // extension.
    private static final String CAPTURING_EXTN_DIGITS = "(" + DIGITS + "{1,7})";
    // Regexp of all possible ways to write extensions, for use when parsing. This will be run as a
    // case-insensitive regexp match. Wide character versions are also provided after each ASCII
    // version.
    private static final String EXTN_PATTERNS_FOR_PARSING;
    static final String EXTN_PATTERNS_FOR_MATCHING;

    static {
        // One-character symbols that can be used to indicate an extension.
        String singleExtnSymbolsForMatching = "x\uFF58#\uFF03~\uFF5E";
        // For parsing, we are slightly more lenient in our interpretation than for matching. Here we
        // allow a "comma" as a possible extension indicator. When matching, this is hardly ever used to
        // indicate this.
        String singleExtnSymbolsForParsing = "," + singleExtnSymbolsForMatching;

        EXTN_PATTERNS_FOR_PARSING = createExtnPattern(singleExtnSymbolsForParsing);
        EXTN_PATTERNS_FOR_MATCHING = createExtnPattern(singleExtnSymbolsForMatching);
    }

    /**
     * Helper initialiser method to create the regular-expression pattern to match extensions,
     * allowing the one-char extension symbols provided by {@code singleExtnSymbols}.
     */
    private static String createExtnPattern(String singleExtnSymbols) {
        // There are three regular expressions here. The first covers RFC 3966 format, where the
        // extension is added using ";ext=". The second more generic one starts with optional white
        // space and ends with an optional full stop (.), followed by zero or more spaces/tabs and then
        // the numbers themselves. The other one covers the special case of American numbers where the
        // extension is written with a hash at the end, such as "- 503#".
        // Note that the only capturing groups should be around the digits that you want to capture as
        // part of the extension, or else parsing will fail!
        // Canonical-equivalence doesn't seem to be an option with Android java, so we allow two options
        // for representing the accented o - the character itself, and one in the unicode decomposed
        // form with the combining acute accent.
        return (RFC3966_EXTN_PREFIX + CAPTURING_EXTN_DIGITS + "|" + "[ \u00A0\\t,]*" +
            "(?:e?xt(?:ensi(?:o\u0301?|\u00F3))?n?|\uFF45?\uFF58\uFF54\uFF4E?|" +
            "[" + singleExtnSymbols + "]|int|anexo|\uFF49\uFF4E\uFF54)" +
            "[:\\.\uFF0E]?[ \u00A0\\t,-]*" + CAPTURING_EXTN_DIGITS + "#?|" +
            "[- ]+(" + DIGITS + "{1,5})#");
    }

    // Regexp of all known extension prefixes used by different regions followed by 1 or more valid
    // digits, for use when parsing.
    private static final Pattern EXTN_PATTERN = Pattern.compile("(?:" + EXTN_PATTERNS_FOR_PARSING + ")$", REGEX_FLAGS);

    // We append optionally the extension pattern to the end here, as a valid phone number may
    // have an extension prefix appended, followed by 1 or more digits.
    private static final Pattern VALID_PHONE_NUMBER_PATTERN =
        Pattern.compile(VALID_PHONE_NUMBER + "(?:" + EXTN_PATTERNS_FOR_PARSING + ")?", REGEX_FLAGS);

    static final Pattern NON_DIGITS_PATTERN = Pattern.compile("(\\D+)");

    // The FIRST_GROUP_PATTERN was originally set to $1 but there are some countries for which the
    // first group is not used in the national pattern (e.g. Argentina) so the $1 group does not match
    // correctly.  Therefore, we use \d, so that the first group actually used in the pattern will be
    // matched.
    private static final Pattern FIRST_GROUP_PATTERN = Pattern.compile("(\\$\\d)");
    private static final Pattern NP_PATTERN = Pattern.compile("\\$NP");
    private static final Pattern FG_PATTERN = Pattern.compile("\\$FG");
    private static final Pattern CC_PATTERN = Pattern.compile("\\$CC");

    // A pattern that is used to determine if the national prefix formatting rule has the first group
    // only, i.e., does not start with the national prefix. Note that the pattern explicitly allows
    // for unbalanced parentheses.
    private static final Pattern FIRST_GROUP_ONLY_PREFIX_PATTERN = Pattern.compile("\\(?\\$1\\)?");

    private static PhoneNumberUtilCore instance = null;

    public static final String REGION_CODE_FOR_NON_GEO_ENTITY = "001";

    PhoneNumberUtilCore(MetadataSource metadataSource, Map<Integer, List<String>> countryCodeToRegionCodeMap,
        Map<String, Integer> regionCodeToCountryCodeMap) {
        this.metadataSource = metadataSource;
        this.countryCodeToRegionCodeMap = countryCodeToRegionCodeMap;
        this.regionCodeToCountryCodeMap = regionCodeToCountryCodeMap;

        for (Map.Entry<Integer, List<String>> entry : countryCodeToRegionCodeMap.entrySet()) {
            List<String> regionCodes = entry.getValue();
            // We can assume that if the country calling code maps to the non-geo entity region code then
            // that's the only region code it maps to.
            if (regionCodes.size() == 1 && REGION_CODE_FOR_NON_GEO_ENTITY.equals(regionCodes.get(0))) {
                // This is the subset of all country codes that map to the non-geo entity region code.
                countryCodesForNonGeographicalRegion.add(entry.getKey());
            } else {
                // The supported regions set does not include the "001" non-geo entity region code.
                supportedRegions.addAll(regionCodes);
            }
        }

        // If the non-geo entity still got added to the set of supported regions it must be because
        // there are entries that list the non-geo entity alongside normal regions (which is wrong).
        // If we discover this, remove the non-geo entity from the set of supported regions and log.
        if (supportedRegions.remove(REGION_CODE_FOR_NON_GEO_ENTITY)) {
            logger.log(Level.WARNING, "invalid metadata "
                + "(country calling code was mapped to the non-geo entity as well as specific region(s))");
        }
        nanpaRegions.addAll(countryCodeToRegionCodeMap.get(NANPA_COUNTRY_CODE));
    }

    public static synchronized PhoneNumberUtilCore getInstance(MetadataSource metadataSource) {
        synchronized (PhoneNumberUtilCore.class) {
            if (instance == null) {
                CountryRegionCodeMap.initCountryCodeToRegionCodeMap();
                setInstance(new PhoneNumberUtilCore(metadataSource, CountryRegionCodeMap.countryCodeToRegionCodeMap,
                    CountryRegionCodeMap.regionCodeToCountryCodeMap));
            }
        }
        return instance;
    }

    /**
     * Sets or resets the PhoneNumberUtilCore singleton instance. If set to null, the next call to
     * {@code getInstance()} will load (and return) the default instance.
     */
    private static synchronized void setInstance(PhoneNumberUtilCore util) {
        synchronized (PhoneNumberUtilCore.class) {
            instance = util;
        }
    }

    /**
     * Convenience method to get a list of what regions the library has metadata for.
     */
    public Set<String> getSupportedRegions() {
        return Collections.unmodifiableSet(supportedRegions);
    }

    /**
     * Convenience method to get a list of what global network calling codes the library has metadata
     * for.
     */
    public Set<Integer> getSupportedGlobalNetworkCallingCodes() {
        return Collections.unmodifiableSet(countryCodesForNonGeographicalRegion);
    }

    /**
     * Checks if this is a region under the North American Numbering Plan Administration (NANPA).
     *
     * @return true if regionCode is one of the regions under NANPA
     */
    public boolean isNANPACountry(String regionCode) {
        return nanpaRegions.contains(regionCode);
    }

    /**
     * Helper function to check region code is not unknown or null.
     */
    private boolean isValidRegionCode(String regionCode) {
        return regionCode != null && supportedRegions.contains(regionCode);
    }

    private PhoneMetadata getMetadataForRegion(String regionCode) {
        if (!isValidRegionCode(regionCode)) {
            return null;
        }

        return metadataSource.getMetadataForRegion(regionCode);
    }

    /**
     * Returns the country calling code for a specific region. For example, this would be 1 for the
     * United States, and 64 for New Zealand.
     *
     * @param regionCode the region that we want to get the country calling code for
     * @return the country calling code for the region denoted by regionCode
     */
    public int getCountryCodeForRegion(String regionCode) {
        if (!isValidRegionCode(regionCode)) {
            logger.log(Level.WARNING,
                "Invalid or missing region code (" + ((regionCode == null) ? "null" : regionCode) + ") provided.");
            return 0;
        }

        return getCountryCodeForValidRegion(regionCode);
    }

    public int getCountryCodeForValidRegion(String regionCode) {
        if (regionCodeToCountryCodeMap.containsKey(regionCode)) {
            return regionCodeToCountryCodeMap.get(regionCode);
        } else {
            PhoneMetadata metadata = getMetadataForRegion(regionCode);
            if (metadata == null) {
                throw new IllegalArgumentException("Invalid region code: " + regionCode);
            }
            return metadata.countryCode;
        }
    }

    PhoneMetadata getMetadataForNonGeographicalRegion(int countryCallingCode) {
        if (!countryCodeToRegionCodeMap.containsKey(countryCallingCode)) {
            return null;
        }
        return metadataSource.getMetadataForNonGeographicalRegion(countryCallingCode);
    }

    PhoneMetadata getMetadataForRegionOrCallingCode(int countryCallingCode, String regionCode) {
        return REGION_CODE_FOR_NON_GEO_ENTITY.equals(regionCode) ? getMetadataForNonGeographicalRegion(
            countryCallingCode) : getMetadataForRegion(regionCode);
    }
}

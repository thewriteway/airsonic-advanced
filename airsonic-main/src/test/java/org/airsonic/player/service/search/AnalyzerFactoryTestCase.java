
package org.airsonic.player.service.search;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test case for Analyzer.
 * These cases have the purpose of observing the current situation
 * and observing the impact of upgrading Lucene.
 */
public class AnalyzerFactoryTestCase {

    private AnalyzerFactory analyzerFactory = new AnalyzerFactory();

    /**
     * Test for the number of character separators per field.
     */
    @Test
    public void testTokenCounts() {

        /*
         * Analyzer used in legacy uses the same Tokenizer for all fields.
         * (Some fields are converted to their own input string for integrity.)
         * As a result, specifications for strings are scattered and difficult to understand.
         * Using PerFieldAnalyzerWrapper,
         * it is possible to use different Analyzer (Tokenizer/Filter) for each field.
         * This allows consistent management of parsing definitions.
         * It is also possible to apply definitions such as "id3 delimiters Tokenizer" to specific fields.
         */

        // The number of words excluding articles is 7.
        String query = "The quick brown fox jumps over the lazy dog.";

        Arrays.stream(IndexType.values()).flatMap(i -> Arrays.stream(i.getFields())).forEach(n -> {
            List<String> terms = toTermString(n, query);
            switch (n) {

                /*
                 * In the legacy, these field divide input into 7. It is not necessary to delimit
                 * this field originally.
                 */
                case FieldNames.FOLDER:
                case FieldNames.MEDIA_TYPE:
                case FieldNames.GENRE:
                    assertEquals(7, terms.size(), "oneTokenFields : " + n);
                    break;

                /*
                 * These should be divided into 7.
                 */
                case FieldNames.ARTIST:
                case FieldNames.ALBUM:
                case FieldNames.TITLE:
                    assertEquals(7, terms.size(), "oneTokenFields : " + n);
                    break;
                /*
                 * ID, FOLDER_ID, YEAR
                 * This is not a problem because the input value does not contain a delimiter.
                 */
                default:
                    assertEquals(7, terms.size(), "oneTokenFields : " + n);
                    break;
            }
        });

    }

    /**
     * Detailed tests on Punctuation.
     * In addition to the common delimiters, there are many delimiters.
     */
    @Test
    public void testPunctuation1() {

        String query = "B︴C";
        String expected = "b︴c";

        /*
         * XXX 3.x -> 8.x :
         * The definition of punctuation has changed.
         */
        Arrays.stream(IndexType.values()).flatMap(i -> Arrays.stream(i.getFields())).forEach(n -> {
            List<String> terms = toTermString(n, query);
            switch (n) {

                /*
                 * In the legacy, these field divide input into 2.
                 * It is not necessary to delimit
                 * this field originally.
                 */
                case FieldNames.FOLDER:
                case FieldNames.GENRE:
                case FieldNames.MEDIA_TYPE:
                    assertEquals(1, terms.size(), "tokenized : " + n);
                    assertEquals(expected, terms.get(0), "tokenized : " + n);
                    break;

                /*
                 * What should the fields of this be?
                 * Generally discarded.
                 */
                case FieldNames.ARTIST:
                case FieldNames.ALBUM:
                case FieldNames.TITLE:
                    assertEquals(1, terms.size(), "tokenized : " + n);
                    assertEquals(expected, terms.get(0), "tokenized : " + n);
                    break;
                /*
                 * ID, FOLDER_ID, YEAR
                 * This is not a problem because the input value does not contain a delimiter.
                 */
                default:
                    assertEquals(2, terms.size(), "tokenized : " + n);
                    break;
            }
        });
    }

    /*
     * Detailed tests on Punctuation.
     * Many of the symbols are delimiters or target to be removed.
     */
    @Test
    public void testPunctuation2() {

        String query = "{'“『【【】】[︴○◎@ $〒→+]";
        Arrays.stream(IndexType.values()).flatMap(i -> Arrays.stream(i.getFields())).forEach(n -> {
            List<String> terms = toTermString(n, query);
            switch (n) {
                case FieldNames.FOLDER:
                case FieldNames.MEDIA_TYPE:
                case FieldNames.GENRE:
                case FieldNames.ARTIST:
                case FieldNames.ALBUM:
                case FieldNames.TITLE:
                    assertEquals(0, terms.size(), "removed : " + n);
                    break;
                default:
                    assertEquals(0, terms.size(), "removed : " + n);
            }
        });
    }

    /**
     * Detailed tests on Stopward.
     *
     * @see org.apache.lucene.analysis.core.StopAnalyzer#ENGLISH_STOP_WORDS_SET
     */
    @Test
    public void testStopward() {

        /*
         * Legacy behavior is to remove ENGLISH_STOP_WORDS_SET from the Token stream.
         * (Putting whether or not it matches the specification of the music search.)
         */

        /*
         * article.
         * This is included in ENGLISH_STOP_WORDS_SET.
         */
        String queryArticle = "a an the";

        /*
         * The default set as index stop word.
         * But these are not included in ENGLISH_STOP_WORDS_SET.
         */
        String queryArticle4Index = "el la los las le les";

        /*
         * Non-article in the ENGLISH_STOP_WORDS_SET.
         * Stopwords are essential for newspapers and documents,
         * but offten they are over-processed for song titles.
         * For example, "we will rock you" can not be searched by "will".
         */
        String queryStop = "and are as at be but by for if in into is it no not of on " //
                + "or such that their then there these they this to was will with";

        /*
         * Unique conjunctions often used in artist fields.
         */
        String stopwordsForArtist = "by cv feat vs with";

        Arrays.stream(IndexType.values()).flatMap(i -> Arrays.stream(i.getFields())).forEach(n -> {
            List<String> articleTerms = toTermString(n, queryArticle);
            List<String> indexArticleTerms = toTermString(n, queryArticle4Index);
            List<String> stopedTerms = toTermString(n, queryStop);
            List<String> artistTerms = toTermString(n, stopwordsForArtist);

            switch (n) {

                case FieldNames.ALBUM:
                case FieldNames.TITLE:

                    // Deleted because it is a stopword.
                    assertEquals(0, articleTerms.size(), "article : " + n);
                    // "la los" is not deleted(#1235).
                    assertEquals(2, indexArticleTerms.size(), "sonic server index article: " + n);
                    // Not deleted because it is not a stopword.
                    assertEquals(30, stopedTerms.size(), "non-article stop words : " + n);
                    // Not deleted because it is not a stopword.
                    assertEquals(5, artistTerms.size(), "stop words for artsist : " + n);
                    break;

                case FieldNames.ARTIST:

                    // Deleted because it is a stopword.
                    assertEquals(0, articleTerms.size(), "article : " + n);
                    // "la los" is not deleted(#1235).
                    assertEquals(2, indexArticleTerms.size(), "sonic server index article: " + n);
                    // Not deleted because it is not a stopword(Except by and with).
                    assertEquals(28, stopedTerms.size(), "non-article stop words : " + n);
                    // Deleted because it is a stopword.
                    assertEquals(0, artistTerms.size(), "stop words for artsist : " + n);
                    break;

                default:
                    fail(); // no analyze field is not applicable
                    break;
            }
        });

    }

    /**
     * Simple test on FullWidth.
     */
    @Test
    public void testFullWidth() {
        String query = "ＦＵＬＬ－ＷＩＤＴＨ";
        List<String> terms = toTermString(query);
        assertEquals(2, terms.size());
        assertEquals("full", terms.get(0));
        assertEquals("width", terms.get(1));
    }

    /**
     * Combined case of Stop and full-width.
     */
    @Test
    public void testStopwardAndFullWidth() {

        /*
         * This and is not deleted because they are different from the default stopword.
         */
        String queryHalfWidth = "THIS IS FULL-WIDTH SENTENCES.";
        List<String> terms = toTermString(queryHalfWidth);
        assertEquals(5, terms.size());
        assertEquals("this", terms.get(0));
        assertEquals("is", terms.get(1));
        assertEquals("full", terms.get(2));
        assertEquals("width", terms.get(3));
        assertEquals("sentences", terms.get(4));

        /*
         * Legacy can avoid Stopward if it is full width.
         * It is unclear whether it is a specification or not.
         * (Problems due to a defect in filter application order?
         * or
         * Is it popular in English speaking countries?)
         */
        String queryFullWidth = "ＴＨＩＳ　ＩＳ　ＦＵＬＬ－ＷＩＤＴＨ　ＳＥＮＴＥＮＣＥＳ.";
        terms = toTermString(queryFullWidth);
        /*
         * XXX 3.x -> 8.x :
         *
         * This is not a change due to the library but an intentional change.
         * The filter order has been changed properly
         * as it is probably not a deliberate specification.
         */
        assertEquals(5, terms.size());
        assertEquals("this", terms.get(0));
        assertEquals("is", terms.get(1));
        assertEquals("full", terms.get(2));
        assertEquals("width", terms.get(3));
        assertEquals("sentences", terms.get(4));

    }

    /**
     * Tests on ligature and diacritical marks.
     * In UAX#29, determination of non-practical word boundaries is not considered.
     * Languages ​​that use special strings require "practical word" sample.
     * Unit testing with only ligature and diacritical marks is not possible.
     */
    @Test
    public void testAsciiFoldingStop() {

        String queryLigature = "Cæsar";
        String expectedLigature = "caesar";

        String queryDiacritical = "Café";
        String expectedDiacritical = "cafe";

        Arrays.stream(IndexType.values()).flatMap(i -> Arrays.stream(i.getFields())).forEach(n -> {
            List<String> termsLigature = toTermString(n, queryLigature);
            List<String> termsDiacritical = toTermString(n, queryDiacritical);
            switch (n) {

                /*
                 * It is decomposed into the expected string.
                 */
                case FieldNames.FOLDER:
                case FieldNames.MEDIA_TYPE:
                case FieldNames.GENRE:
                case FieldNames.ARTIST:
                case FieldNames.ALBUM:
                case FieldNames.TITLE:
                    assertEquals(1, termsLigature.size(), "Cæsar : " + n);
                    assertEquals(expectedLigature, termsLigature.get(0), "Cæsar : " + n);
                    assertEquals(1, termsDiacritical.size(), "Café : " + n);
                    assertEquals(expectedDiacritical, termsDiacritical.get(0), "Café : " + n);
                    break;

                // Legacy has common behavior for all fields.
                default:
                    assertEquals(1, termsLigature.size(), "Cæsar : " + n);
                    assertEquals(expectedLigature, termsLigature.get(0), "Cæsar : " + n);
                    assertEquals(1, termsDiacritical.size(), "Café : " + n);
                    assertEquals(expectedDiacritical, termsDiacritical.get(0), "Café : " + n);
                    break;

            }
        });

    }

    /**
     * Detailed tests on LowerCase.
     */
    @Test
    public void testLowerCase() {

        // Filter operation check only. Verify only some settings.
        String query = "ABCDEFG";
        String expected = "abcdefg";

        Arrays.stream(IndexType.values()).flatMap(i -> Arrays.stream(i.getFields())).forEach(n -> {
            List<String> terms = toTermString(n, query);
            switch (n) {

                /*
                 * In legacy, it is converted to lower. (over-processed?)
                 */
                case FieldNames.FOLDER:
                case FieldNames.MEDIA_TYPE:
                    assertEquals(1, terms.size(), "lower : " + n);
                    assertEquals(expected, terms.get(0), "lower : " + n);
                    break;

                /*
                 * These are searchable fields in lower case.
                 */
                case FieldNames.GENRE:
                case FieldNames.ARTIST:
                case FieldNames.ALBUM:
                case FieldNames.TITLE:
                    assertEquals(1, terms.size(), "lower : " + n);
                    assertEquals(expected, terms.get(0), "lower : " + n);
                    break;

                // Legacy has common behavior for all fields.
                default:
                    assertEquals(1, terms.size(), "lower : " + n);
                    assertEquals(expected, terms.get(0), "lower : " + n);
                    break;

            }
        });
    }

    /**
     * Detailed tests on EscapeRequires.
     * The reserved string is discarded unless it is purposely Escape.
     * This is fine as a search specification(if it is considered as a kind of reserved stop word).
     * However, in the case of file path, it may be a problem.
     */
    @Test
    public void testLuceneEscapeRequires() {

        String queryEscapeRequires = "+-&&||!(){}[]^\"~*?:\\/";
        String queryFileUsable = "+-&&!(){}[]^~";

        Arrays.stream(IndexType.values()).flatMap(i -> Arrays.stream(i.getFields())).forEach(n -> {
            List<String> terms = toTermString(n, queryEscapeRequires);
            switch (n) {

                /*
                 * Will be removed. (Can not distinguish the directory of a particular pattern?)
                 */
                case FieldNames.FOLDER:
                    assertEquals(0, terms.size(), "escape : " + n);
                    terms = toTermString(n, queryFileUsable);
                    assertEquals(0, terms.size(), "escape : " + n);
                    break;

                /*
                 * Will be removed.
                 */
                case FieldNames.MEDIA_TYPE:
                case FieldNames.GENRE:
                case FieldNames.ARTIST:
                case FieldNames.ALBUM:
                case FieldNames.TITLE:
                    assertEquals(0, terms.size(), "escape : " + n);
                    break;

                // Will be removed.
                default:
                    assertEquals(0, terms.size(), "escape : " + n);
                    break;

            }
        });

    }

    /**
     * Create an example that makes UAX 29 differences easy to understand.
     */
    @Test
    public void testUax29() {

        /*
         * Case using test resource name
         */

        // Semicolon, comma and hyphen.
        String query = "Bach: Goldberg Variations, BWV 988 - Aria";
        List<String> terms = toTermString(query);
        assertEquals(6, terms.size());
        assertEquals("bach", terms.get(0));
        assertEquals("goldberg", terms.get(1));
        assertEquals("variations", terms.get(2));
        assertEquals("bwv", terms.get(3));
        assertEquals("988", terms.get(4));
        assertEquals("aria", terms.get(5));

        // Underscores around words, ascii and semicolon.
        query = "_ID3_ARTIST_ Céline Frisch: Café Zimmermann";
        terms = toTermString(query);
        assertEquals(5, terms.size());

        /*
         * XXX 3.x -> 8.x : _id3_artist_　in UAX#29.
         * Since the effect is large, trim with Filter.
         */
        assertEquals("id3_artist", terms.get(0));
        assertEquals("celine", terms.get(1));
        assertEquals("frisch", terms.get(2));
        assertEquals("cafe", terms.get(3));
        assertEquals("zimmermann", terms.get(4));

        // Underscores around words and slashes.
        query = "_ID3_ARTIST_ Sarah Walker/Nash Ensemble";
        terms = toTermString(query);
        assertEquals(5, terms.size());

        /*
         * XXX 3.x -> 8.x : _id3_artist_　in UAX#29.
         * Since the effect is large, trim with Filter.
         */
        assertEquals("id3_artist", terms.get(0));
        assertEquals("sarah", terms.get(1));
        assertEquals("walker", terms.get(2));
        assertEquals("nash", terms.get(3));
        assertEquals("ensemble", terms.get(4));

        // Space
        assertEquals(asList("abc", "def"), toTermString(" ABC DEF "));
        assertEquals(asList("abc1", "def"), toTermString(" ABC1 DEF "));

        // trim and delimiter
        assertEquals(asList("abc", "def"), toTermString("+ABC+DEF+"));
        assertEquals(asList("abc", "def"), toTermString("|ABC|DEF|"));
        assertEquals(asList("abc", "def"), toTermString("!ABC!DEF!"));
        assertEquals(asList("abc", "def"), toTermString("(ABC(DEF("));
        assertEquals(asList("abc", "def"), toTermString(")ABC)DEF)"));
        assertEquals(asList("abc", "def"), toTermString("{ABC{DEF{"));
        assertEquals(asList("abc", "def"), toTermString("}ABC}DEF}"));
        assertEquals(asList("abc", "def"), toTermString("[ABC[DEF["));
        assertEquals(asList("abc", "def"), toTermString("]ABC]DEF]"));
        assertEquals(asList("abc", "def"), toTermString("^ABC^DEF^"));
        assertEquals(asList("abc", "def"), toTermString("\\ABC\\DEF\\"));
        assertEquals(asList("abc", "def"), toTermString("\"ABC\"DEF\""));
        assertEquals(asList("abc", "def"), toTermString("~ABC~DEF~"));
        assertEquals(asList("abc", "def"), toTermString("*ABC*DEF*"));
        assertEquals(asList("abc", "def"), toTermString("?ABC?DEF?"));
        assertEquals(asList("abc:def"), toTermString(":ABC:DEF:"));             // XXX 3.x -> 8.x : abc def -> abc:def
        assertEquals(asList("abc", "def"), toTermString("-ABC-DEF-"));
        assertEquals(asList("abc", "def"), toTermString("/ABC/DEF/"));
        /*
         * XXX 3.x -> 8.x : _abc_def_　in UAX#29.
         * Since the effect is large, trim with Filter.
         */
        assertEquals(asList("abc_def"), toTermString("_ABC_DEF_"));             // XXX 3.x -> 8.x : abc def -> abc_def
        assertEquals(asList("abc", "def"), toTermString(",ABC,DEF,"));
        assertEquals(asList("abc.def"), toTermString(".ABC.DEF."));
        assertEquals(asList("abc", "def"), toTermString("&ABC&DEF&"));          // XXX 3.x -> 8.x : abc&def -> abc def
        assertEquals(asList("abc", "def"), toTermString("@ABC@DEF@"));          // XXX 3.x -> 8.x : abc@def -> abc def
        assertEquals(asList("abc'def"), toTermString("'ABC'DEF'"));

        // trim and delimiter and number
        assertEquals(asList("abc1", "def"), toTermString("+ABC1+DEF+"));
        assertEquals(asList("abc1", "def"), toTermString("|ABC1|DEF|"));
        assertEquals(asList("abc1", "def"), toTermString("!ABC1!DEF!"));
        assertEquals(asList("abc1", "def"), toTermString("(ABC1(DEF("));
        assertEquals(asList("abc1", "def"), toTermString(")ABC1)DEF)"));
        assertEquals(asList("abc1", "def"), toTermString("{ABC1{DEF{"));
        assertEquals(asList("abc1", "def"), toTermString("}ABC1}DEF}"));
        assertEquals(asList("abc1", "def"), toTermString("[ABC1[DEF["));
        assertEquals(asList("abc1", "def"), toTermString("]ABC1]DEF]"));
        assertEquals(asList("abc1", "def"), toTermString("^ABC1^DEF^"));
        assertEquals(asList("abc1", "def"), toTermString("\\ABC1\\DEF\\"));
        assertEquals(asList("abc1", "def"), toTermString("\"ABC1\"DEF\""));
        assertEquals(asList("abc1", "def"), toTermString("~ABC1~DEF~"));
        assertEquals(asList("abc1", "def"), toTermString("*ABC1*DEF*"));
        assertEquals(asList("abc1", "def"), toTermString("?ABC1?DEF?"));
        assertEquals(asList("abc1", "def"), toTermString(":ABC1:DEF:"));
        assertEquals(asList("abc1", "def"), toTermString(",ABC1,DEF,"));        // XXX 3.x -> 8.x : abc1,def -> abc1 def
        assertEquals(asList("abc1", "def"), toTermString("-ABC1-DEF-"));        // XXX 3.x -> 8.x : abc1-def -> abc1 def
        assertEquals(asList("abc1", "def"), toTermString("/ABC1/DEF/"));        // XXX 3.x -> 8.x : abc1/def -> abc1 def
        /*
         * XXX 3.x -> 8.x : _abc1_def_　in UAX#29.
         * Since the effect is large, trim with Filter.
         */
        assertEquals(asList("abc1_def"), toTermString("_ABC1_DEF_"));
        assertEquals(asList("abc1", "def"), toTermString(".ABC1.DEF."));        // XXX 3.x -> 8.x : abc1.def -> abc1 def
        assertEquals(asList("abc1", "def"), toTermString("&ABC1&DEF&"));
        assertEquals(asList("abc1", "def"), toTermString("@ABC1@DEF@"));
        assertEquals(asList("abc1", "def"), toTermString("'ABC1'DEF'"));

    }

    /**
     * Special handling of single quotes.
     */
    @Test
    public void testSingleQuotes() {

        /*
         * A somewhat cultural that seems to be related to a specific language.
         */
        String query = "This is Airsonic's analysis.";
        List<String> terms = toTermString(query);
        assertEquals(4, terms.size());
        assertEquals("this", terms.get(0));// Not deleted because it is not a stopword
        assertEquals("is", terms.get(1));// Not deleted because it is not a stopword
        assertEquals("airsonic", terms.get(2));
        assertEquals("analysis", terms.get(3));

        /*
         * XXX 3.x -> 8.x :
         * we ve -> we've
         */
        query = "We’ve been here before.";
        terms = toTermString(query);
        assertEquals(4, terms.size());
        assertEquals("we've", terms.get(0));
        assertEquals("been", terms.get(1));
        assertEquals("here", terms.get(2));
        assertEquals("before", terms.get(3));

        query = "LʼHomme";
        terms = toTermString(query);
        assertEquals(1, terms.size());
        assertEquals("lʼhomme", terms.get(0));

        query = "L'Homme";
        terms = toTermString(query);
        assertEquals(1, terms.size());
        assertEquals("l'homme", terms.get(0));

        query = "aujourd'hui";
        terms = toTermString(query);
        assertEquals(1, terms.size());
        assertEquals("aujourd'hui", terms.get(0));

        query = "fo'c'sle";
        terms = toTermString(query);
        assertEquals(1, terms.size());
        assertEquals("fo'c'sle", terms.get(0));

    }

    /*
     * There is also a filter that converts the tense to correspond to the search by the present
     * tense.
     */
    @Test
    public void testPastParticiple() {

        /*
         * Confirming no conversion to present tense.
         */
        String query = "This is formed with a form of the verb \"have\" and a past participl.";
        List<String> terms = toTermString(query);
        assertEquals(11, terms.size());
        assertEquals("this", terms.get(0));// Not deleted because it is not a stopword
        assertEquals("is", terms.get(1));// Not deleted because it is not a stopword
        assertEquals("formed", terms.get(2));// leave passive / not "form"
        assertEquals("with", terms.get(3));// Not deleted because it is not a stopword
        assertEquals("form", terms.get(4));
        assertEquals("of", terms.get(5));
        assertEquals("verb", terms.get(6));
        assertEquals("have", terms.get(7));
        assertEquals("and", terms.get(8));// Not deleted because it is not a stopword
        assertEquals("past", terms.get(9));
        assertEquals("participl", terms.get(10));

    }

    /*
     * There are also filters that convert plurals to singular.
     */
    @Test
    public void testNumeral() {

        /*
         * Confirming no conversion to singular.
         */

        String query = "books boxes cities leaves men glasses";
        List<String> terms = toTermString(query);
        assertEquals(6, terms.size());
        assertEquals("books", terms.get(0));// leave numeral / not singular
        assertEquals("boxes", terms.get(1));
        assertEquals("cities", terms.get(2));
        assertEquals("leaves", terms.get(3));
        assertEquals("men", terms.get(4));
        assertEquals("glasses", terms.get(5));
    }

    @Test
    public void testGenre() {

        /*
         * Confirming no conversion to singular.
         */

        String query = "{}";
        List<String> terms = toQueryTermString(FieldNames.GENRE, query);
        assertEquals(1, terms.size());
        assertEquals("{ }", terms.get(0));
    }

    private List<String> toTermString(String str) {
        return toTermString(null, str);
    }

    private List<String> toTermString(String field, String str) {
        List<String> result = new ArrayList<>();
        try {
            TokenStream stream = analyzerFactory.getAnalyzer().tokenStream(field,
                    new StringReader(str));
            stream.reset();
            while (stream.incrementToken()) {
                result.add(stream.getAttribute(CharTermAttribute.class).toString()
                        .replaceAll("^term\\=", ""));
            }
            stream.close();
        } catch (IOException e) {
            LoggerFactory.getLogger(AnalyzerFactoryTestCase.class)
                    .error("Error during Token processing.", e);
        }
        return result;
    }

    /*
     * Should be added in later versions.
     */
    public void testWildCard() {
    }

    @SuppressWarnings("unused")
    private List<String> toQueryTermString(String field, String str) {
        List<String> result = new ArrayList<>();
        try {
            TokenStream stream = analyzerFactory.getQueryAnalyzer().tokenStream(field,
                    new StringReader(str));
            stream.reset();
            while (stream.incrementToken()) {
                result.add(stream.getAttribute(CharTermAttribute.class).toString()
                        .replaceAll("^term\\=", ""));
            }
            stream.close();
        } catch (IOException e) {
            LoggerFactory.getLogger(AnalyzerFactoryTestCase.class)
                    .error("Error during Token processing.", e);
        }
        return result;
    }

}

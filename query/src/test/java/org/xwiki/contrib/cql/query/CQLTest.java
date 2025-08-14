/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.cql.query;
    
import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.confluence.resolvers.ConfluencePageIdResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluenceResolverException;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceKeyResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceResolver;
import org.xwiki.contrib.cql.aqlparser.AQLParser;
import org.xwiki.contrib.cql.query.converters.CQLToSolrQueryConverter;
import org.xwiki.contrib.cql.aqlparser.exceptions.ParserException;
import org.xwiki.contrib.cql.query.converters.DefaultCQLToSolrAtomConverter;
import org.xwiki.contrib.cql.query.converters.internal.AncestorCQLToSolrAtomConverter;
import org.xwiki.contrib.cql.query.converters.internal.ContentCQLToSolrAtomConverter;
import org.xwiki.contrib.cql.query.converters.internal.ParentCQLToSolrAtomConverter;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link CQLToSolrQueryConverter}.
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
@ComponentList({
    AncestorCQLToSolrAtomConverter.class,
    ContentCQLToSolrAtomConverter.class,
    ParentCQLToSolrAtomConverter.class,
    DefaultCQLToSolrAtomConverter.class,
    CQLToSolrQueryConverter.class
})
class CQLTest
{
    private static final String WEB_HOME = "WebHome";

    private static final DocumentReference GUEST = new DocumentReference("xwiki", "XWiki", "Guest");

    private static final String PATH_FN_CALL =
        " Path: clauses > clause > atom > atomic value > function call arguments";

    private static final String PATH_DATE_PART =
        " Path: clauses > clause > atom > atomic value > number or date > date > date part";

    private static final String PATH_STRING = " Path: clauses > clause > atom > atomic value > string";

    private static final String FIELD_ONLY_SUPPORTED_WITH_CONTAINS =
        "Field [%s] is only supported with the 'contains' (~) and 'does not contain' (!~) operators "
            + "because we don't index the data as string fields in Solr required for supporting other operators "
            + "(line 1, col 1, pos 0)";

    private static final String TESTWIKI = "testwiki";

    private static final String MY_SPACE_TESTS = "MySpaceTests";

    private static final EntityReference MY_SPACE_TESTS_REF = new EntityReference(
        MY_SPACE_TESTS,
        EntityType.SPACE,
        new WikiReference(TESTWIKI)
    );

    private static final DocumentReference SUBPAGE_REF = new DocumentReference(
        TESTWIKI,
        Arrays.asList(MY_SPACE_TESTS, "My Page", "SubPage"),
        WEB_HOME
    );

    private static final XWikiDocument THE_ANSWER_DOC = new XWikiDocument(new DocumentReference(
        new EntityReference(
            WEB_HOME,
            EntityType.DOCUMENT,
            new EntityReference(
                "TheAnswer",
                EntityType.SPACE,
                SUBPAGE_REF.getParent()
            )
        )
    ));

    private static final XWikiDocument THE_LEET_DOC = new XWikiDocument(new DocumentReference(
        new EntityReference(
            WEB_HOME,
            EntityType.DOCUMENT,
            new EntityReference(
                "LEET",
                EntityType.SPACE,
                SUBPAGE_REF.getParent()
            )
        )
    ));

    @InjectMockComponents
    private CQLToSolrQueryConverter queryConverter;

    @InjectMockitoOldcore
    private MockitoOldcore mockitoOldcore;

    @MockComponent
    private ConfluenceSpaceKeyResolver confluenceSpaceKeyResolver;

    @MockComponent
    private ConfluencePageIdResolver confluencePageIdResolver;

    @MockComponent
    private ConfluenceSpaceResolver confluenceSpaceResolver;

    private String t(String cql) throws ParserException, IOException
    {
        return queryConverter.getSolrStatement(AQLParser.parse(cql));
    }

    private void expectParserException(String expectedMessage, String cql)
    {
        expectParserException(expectedMessage, cql, null);
    }

    private void expectParserException(String expectedMessage, String cql, String causeMessage)
    {
        ParserException thrown = assertThrows(ParserException.class, () -> t(cql));
        assertEquals(expectedMessage, thrown.getMessage());
        if (causeMessage != null) {
            assertEquals(causeMessage, thrown.getCause().getMessage());
        }
    }

    @BeforeEach
    void configure() throws ConfluenceResolverException
    {
        mockitoOldcore.getXWikiContext().setUserReference(GUEST);
        mockitoOldcore.getXWikiContext().setDoc(new XWikiDocument(SUBPAGE_REF));

        when(confluenceSpaceResolver.getSpace(any())).then(invocation -> {
            EntityReference ref = invocation.getArgument(0);
            if (ref.hasParent(MY_SPACE_TESTS_REF)) {
                return MY_SPACE_TESTS_REF;
            }

            return null;
        });

        when(confluenceSpaceResolver.getSpaceKey(any())).then(invocation -> {
            EntityReference ref = invocation.getArgument(0);
            if (ref.hasParent(MY_SPACE_TESTS_REF)) {
                return MY_SPACE_TESTS;
            }
            return null;
        });

        when(confluencePageIdResolver.getDocumentById(anyLong())).then(invocation -> {
            switch (((Long) invocation.getArgument(0)).intValue()) {
                case 42: return THE_ANSWER_DOC.getDocumentReference();
                case 1337: return THE_LEET_DOC.getDocumentReference();
                case 111: throw new ConfluenceResolverException("The query failed", new Exception("Arbitrary cause"));
                default:
                    return null;
            }
        });

        when(confluenceSpaceKeyResolver.getSpaceByKey(anyString()))
            .then(invocation -> new SpaceReference(TESTWIKI, (String) invocation.getArgument(0)));
    }

    @Test
    void testComplex() throws Exception
    {
        assertEquals(
            "(property.XWiki.TagClass.tags:(approved OR tag_under OR tag_400 OR another_tag))"
                + " AND (type:DOCUMENT AND -class:Blog.BlogPostClass) AND (space_facet:0\\/SPA.)",
            t("label in (\"approved\",\"tag_under\",\"tag_400\",\"another_tag\")"
                + " and type = \"page\" and space = \"SPA\""));
    }

    @Test
    void testSpace() throws Exception
    {
        assertEquals(
            "space_facet:0\\/MySpaceTests.",
            t("space = currentSpace()"));
    }

    @Test
    void testIdCurrentContent() throws Exception
    {
        String expected = "fullname:MySpaceTests.My\\ Page.SubPage.WebHome";
        assertEquals(expected, t("id = currentContent()"));
        assertEquals(expected, t("content = currentContent()"));
    }

    @Test
    void testParentCurrentContent() throws Exception
    {
        assertEquals(
            "space_facet:(2\\/MySpaceTests.My\\ Page.SubPage. AND 3\\/* AND -4\\/*)",
            t("parent = currentContent()")
        );
    }

    @Test
    void testAncestorCurrentContent() throws Exception
    {
        assertEquals(
            "space_facet:2\\/MySpaceTests.My\\ Page.SubPage.",
            t("ancestor = currentContent()")
        );
    }

    @Test
    void testSpaceTooManyArguments()
    {
        expectParserException(
            "Function [currentSpace] does not take any argument (line 1, col 9, pos 8)",
            "space = currentSpace('unwanted param')");
    }

    @Test
    void testTwoOrderBy() throws Exception
    {
        assertEquals(
            "(type:DOCUMENT AND class:Blog.BlogPostClass) AND (creationdate:{NOW/DAY-5DAYS TO *])",
            t("type = blogpost AND created > startOfDay(\"-5d\") order by created asc, type desc"));
    }

    @Test
    void testBlogOrderAscImplicit() throws Exception
    {
        assertEquals(
            "(type:DOCUMENT AND class:Blog.BlogPostClass) AND (creationdate:{NOW/DAY-6DAYS TO *])",
            t("type = blogpost AND created > startOfDay(\"-6d\") order by created, type"));
    }

    @Test
    void testBlogOrderDesc() throws Exception
    {
        assertEquals(
            "(type:DOCUMENT AND class:Blog.BlogPostClass) AND (creationdate:{NOW/DAY-4DAYS TO *])",
            t("type = blogpost AND created > startOfDay(\"-4d\") order by created desc"));
    }

    @Test
    void testTextAndSpaceFacet() throws Exception
    {
        assertEquals(
            "(space_facet:(0\\/ProjectA. OR 0\\/ProjectB. OR 0\\/ProjectC.))"
                + " AND (property.XWiki.TagClass.tags:mytag)"
                + " AND (title_sort:query~ OR property.XWiki.TagClass.tags:query~"
                + " OR content:query~)",
            t("space in (ProjectA, ProjectB, ProjectC) AND label = mytag AND text ~ query"));
    }

    @Test
    void testTextEqual()
    {
        expectParserException(
            String.format(FIELD_ONLY_SUPPORTED_WITH_CONTAINS, "text"),
            "text = query");
    }

    @Test
    void testSpaceTitle()
    {
        expectParserException(
            "Field [space.title] is not supported yet (line 1, col 1, pos 0)",
            "space.title ~ Title");
    }

    @Test
    void testTypeComment()
    {
        expectParserException(
            "CQL type [comment] is not supported yet (line 1, col 8, pos 7)",
            "type = comment");
    }

    @Test
    void testMention()
    {
        expectParserException(
            "Field [mention] is not supported yet (line 1, col 1, pos 0)",
            "mention = currentUser()");
    }

    @Test
    void testLabel() throws Exception
    {
        assertEquals(
            "property.XWiki.TagClass.tags:(tag1 OR tag2 OR tag\\ 3)",
            t("label in (tag1, tag2, 'tag 3')")
        );
    }

    @Test
    void testModifiedBetween() throws Exception
    {
        assertEquals(
            "(date:{NOW/WEEK TO *]) AND (date:[* TO NOW+WEEK/WEEK})",
            t("lastmodified > startOfWeek() AND lastmodified < endOfWeek()")
        );
    }

    @Test
    void testBrokenType()
    {
        expectParserException(
            "CQL type [broken] is not supported (line 1, col 8, pos 7)",
            "type = broken");
    }

    @Test
    void testCurrentUser() throws Exception
    {
        assertEquals(
            "creator:*\\:XWiki.Guest",
            t("creator = currentUser()"));
    }

    @Test
    void testCurrentUserWithUnexpectedParam()
    {
        expectParserException(
            "Function [currentUser] does not take any argument (line 1, col 11, pos 10)",
            "creator = currentUser(hello)");
    }

    @Test
    void testCreatorWithString()
    {
        expectParserException(
            "For now, the only supported value with field [creator] is currentUser() (line 1, col 9, pos 8)",
            "creator=\"66:68a30011-16f5-467e-b8d0-5a916bd77443\"");
    }

    @Test
    void testCQLDateLTE() throws Exception
    {
        assertEquals(
            "date:[* TO NOW/YEAR]",
            t("lastModified <= startOfYear()"));
    }


    @Test
    void testCQLCappedFieldAndNot() throws Exception
    {
        assertEquals(
            "(title_sort:mytitle) AND -(space_facet:0\\/SP.)",
            t("title = mytitle and not Space = SP"));
    }

    @Test
    void testCQLNowWeek() throws Exception
    {
        assertEquals(
            "creationdate:{NOW-4WEEKS TO *]",
            t("created > now(\"-4w\")"));
    }

    @Test
    void testCQLStartMonth() throws Exception
    {
        assertEquals(
            "creationdate:[* TO NOW/MONTH}",
            t("created < startOfMonth()"));
    }

    @Test
    void testCQLStartMonthWithParam() throws Exception
    {
        assertEquals(
            "creationdate:{NOW/MONTH+1DAYS TO *]",
            t("created > startOfMonth(\"+1d\")"));
    }

    @Test
    void testCQLStartDayWithMinutes() throws Exception
    {
        assertEquals(
            "creationdate:{NOW/DAY+1MINUTES TO *]",
            t("created > startOfDay(\"+1m\")"));
    }

    @Test
    void testCQLStartDayWithTooManyParameters()
    {
        expectParserException(
            "Too many parameters for date function [startOfDay]."
                + " Expected less than 2 parameters (line 1, col 29, pos 28)",
            "created = startOfDay(\"+1m\", 42)");
    }

    @Test
    void testCQLEndDayWithHours() throws Exception
    {
        assertEquals(
            "creationdate:{NOW+DAY/DAY-3HOURS TO *]",
            t("created > endOfDay(\"-3h\")"));
    }

    @Test
    void testCQLEndDayWithBrokenParameter()
    {
        expectParserException(
            "Failed to parse the inc parameter for endOfDay (line 1, col 20, pos 19)",
            "created > endOfDay(\"broken\")");
    }

    @Test
    void testCQLEndDayWithZero() throws Exception
    {
        assertEquals(
            "creationdate:{NOW/DAY TO *]",
            t("created > startOfDay(\"+0h\")"));
    }

    @Test
    void testCQLEndDayWithBrokenParameter2()
    {
        expectParserException(
            "Expected a string argument (line 1, col 20, pos 19)",
            "created > endOfDay(42)");
    }

    @Test
    void testCQLDate1() throws Exception
    {
        assertEquals(
            "creationdate:[\"2008-05-31T00:00:00.000Z\" TO *]",
            t("created >= \"2008/05/31\""));
    }

    @Test
    void testCQLDate2() throws Exception
    {
        assertEquals(
            "creationdate:\"2008-05-31T08:01:00.000Z\"",
            t("created = \"2008/05/31 8:01\""));
    }

    @Test
    void testCQLDateUnquoted() throws Exception
    {
        assertEquals(
            "creationdate:\"2008-05-30T00:00:00.000Z\"",
            t("created = 2008/05/30"));
    }

    @Test
    void testCQLDateWithDashes() throws Exception
    {
        assertEquals(
            "creationdate:\"2008-05-31T00:00:00.000Z\"",
            t("created = 2008-05-31"));
    }

    @Test
    void testCQLDateBadSep()
    {
        expectParserException(
            "Expected '-' in the date started at [line 1, col 11, pos 10] (line 1, col 18, pos 17)." + PATH_DATE_PART,
            "created = 2008-05/31"
        );
    }

    @Test
    void testCQLDateMissingDay()
    {
        expectParserException(
            "Expected '/' in the date started at [line 1, col 11, pos 10] (line 1, col 17, pos 16)." + PATH_DATE_PART,
            "created = 2008/05"
        );
    }

    @Test
    void testCQLIn() throws Exception
    {
        assertEquals(
            "title_sort:(title\\ 1 OR title\\ 2 OR title3)",
            t("title in (\"title 1\", \"title 2\", title3)"));
    }

    @Test
    void testCQLInEmpty()
    {
        expectParserException(
            "Expected a value before closing parenthesis ')' (line 1, col 11, pos 10)."
                + " Path: clauses > clause > atom > in expr > atomic value",
            "title in ()");
    }

    @Test
    void testCQLInMissingParen1()
    {
        expectParserException(
            "Expected closing parenthesis ')' (line 1, col 17, pos 16). Path: clauses > clause > atom > in expr",
            "title in (salut,");
    }

    @Test
    void testCQLInMissingParen2()
    {
        expectParserException(
            "Expected closing parenthesis ')' (line 1, col 16, pos 15)."
                + " Path: clauses > clause > atom > in expr",
            "title in (salut");
    }

    @Test
    void testCQLTypeInPageAndBlogPost() throws Exception
    {
        assertEquals(
            "(type:DOCUMENT AND -class:Blog.BlogPostClass) OR (type:DOCUMENT AND class:Blog.BlogPostClass)",
            t("type in (page, blogpost)"));
    }

    @Test
    void testCQLTypeInPageAndBlogPost2() throws Exception
    {
        assertEquals(
            "((type:DOCUMENT AND -class:Blog.BlogPostClass) "
                + "OR (type:DOCUMENT AND class:Blog.BlogPostClass)) "
                + "AND (title_sort:MyTitle)",
            t("type in (page, blogpost) and title = MyTitle"));
    }

    @Test
    void testCQLSimpleQuoteString() throws Exception
    {
        assertEquals(
            "(title_sort:(title\\ 1 OR title2 OR title3)) OR (title_sort:My\\ Title)",
            t("title in ('title 1', title2, \"title3\") or title = 'My Title'"));
    }

    @Test
    void testCQLTypeNotInPageAndBlogPost() throws Exception
    {
        assertEquals(
            "-((type:DOCUMENT AND -class:Blog.BlogPostClass) OR (type:DOCUMENT AND class:Blog.BlogPostClass))",
            t("type not in (page, blogpost)"));
    }

    @Test
    void testCQLNotIn() throws Exception
    {
        assertEquals(
            "-title_sort:(title\\ 1337 OR 42)",
            t("title not in (\"title 1337\", 42)"));
    }

    @Test
    void testCQLNotInNLs() throws Exception
    {
        assertEquals(
            "-title_sort:(title\\ 1 OR 42)",
            t("\ntitle \n not \n in \n (\n\"title 1\",\n 42\n)\n"));
    }

    @Test
    void testBadNotIn()
    {
        expectParserException(
            "Expected [in] (line 1, col 11, pos 10). Path: clauses > clause > atom > atom operator",
            "title not ('title 1', 'title2')");
    }

    @Test
    void testCQLIdNotIn() throws Exception
    {
        assertEquals(
            "-fullname:(MySpaceTests.My\\ Page.SubPage.TheAnswer.WebHome OR MySpaceTests.My\\ Page.SubPage.LEET.WebHome)",
            t("id not in (42, 1337)"));
    }

    @Test
    void testCQLContent() throws Exception
    {
        assertEquals(
            "fullname:MySpaceTests.My\\ Page.SubPage.TheAnswer.WebHome",
            t("content = 42"));
    }

    @Test
    void testCQLAncestor() throws Exception
    {
        assertEquals(
            "space_facet:3\\/MySpaceTests.My\\ Page.SubPage.TheAnswer.",
            t("ancestor = 42"));
    }

    @Test
    void testCQLParent() throws Exception
    {
        assertEquals(
            "space_exact:MySpaceTests.My\\ Page.SubPage.TheAnswer",
            t("parent = 42"));
    }

    @Test
    void testCQLIdNotFound()
    {
        expectParserException(
            "Could not find the document matching Confluence id [222] (line 1, col 6, pos 5)",
            "id = 222");
    }


    @Test
    void testCQLParentIncorrectFormat()
    {
        expectParserException(
            "Expected a Confluence content id (a number) (line 1, col 10, pos 9)",
            "parent = bad");
    }

    @Test
    void testCQLParentIncorrectOperator()
    {
        expectParserException(
            "Unsupported operator for id, parent or ancestor field (line 1, col 8, pos 7)",
            "parent < 42");
    }

    @Test
    void testQueryExceptionWhileHandlingConfluenceID()
    {
        expectParserException(
            "Could not find the document matching Confluence id [111] (line 1, col 10, pos 9)",
            "parent = 111",
            "The query failed");
    }

    @Test
    void testStringThatInitiallyLooksLikeANumber() throws Exception
    {
        assertEquals(
            "(title_sort:0xBAD) OR (title_sort:0.1BAD) OR (title_sort:(1eBAD OR 1e5BAD))",
            t("title = 0xBAD or title = 0.1BAD or title in (1eBAD, 1e5BAD)"));
    }

    @Test
    void testCQLFullName()
    {
        expectParserException(
            "For now, the only supported value with field [user.fullname] is currentUser() (line 1, col 16, pos 15)",
            "user.fullname ~\"Marty\""
        );
    }

    @Test
    void testCQLFullNameCurrentUser() throws Exception
    {
        assertEquals(
            "creator:*\\:XWiki.Guest~",
            t("user.fullname ~currentUser()")
        );
    }

    @Test
    void testCQLCurrentUserInUnrelatedField()
    {
        // We don't actually know the value of currentUser, so executing this query is impossible
        expectParserException(
            "currentUser cannot be used with field [title] (line 1, col 1, pos 0)",
            "title ~ currentUser()"
        );
    }

    @Test
    void testCQLReservedWord()
    {
        expectParserException(
            "[after] is a reserved word which cannot be used as a field (line 1, col 1, pos 0)",
            "after = 42"
        );
    }

    @Test
    void testCQLUnknownField()
    {
        expectParserException(
            "Field [unknown] is unknown (line 1, col 1, pos 0)",
            "unknown = 42"
        );
    }

    // Float and boolean tests.
    // Of course nobody would put compare titles with a float , but CQL has numeric fields, and a way to extend itself
    // by adding custom fields.
    // It's hard to know whether floats are possible in numeric field but let's play safe and ensure they
    // work well, so we don't have to come back to the parser, should we support float fields in the future.
    // Moreover , Confluence won't actually match titles with floats and booleans while we do. We are less strict with
    // types.

    @Test
    void testCQLFloat() throws Exception
    {
        assertEquals(
            "title_sort:(1.5 OR 1.5e35 OR 1e35 OR .5e1 OR 1e\\+1 OR 1E\\+1 OR 1e\\-16)",
            t("title in (1.5, 1.5e35, 1e35, .5e1, 1e+1, 1E+1, 1e-16)")
        );
    }

    @Test
    void testCQLFloatNLs() throws Exception
    {
        assertEquals(
            "title_sort:(1.5 OR 1.5e35 OR 1e35 OR .5e1 OR 1e\\+1 OR 1e\\-15)",
            t("title in (1.5, 1.5e35,\n1e35, .5e1, 1e+1\n, 1e-15\n)")
        );
    }

    @Test
    void testCQLFloatMissingExp() throws Exception
    {
        assertEquals(
            "title_sort:1.5e",
            t("title = 1.5e")
        );
    }

    @Test
    void testCQLFavoriteSpaces()
    {
        expectParserException(
            "Unsupported function [favouriteSpaces] (line 1, col 11, pos 10)",
            "space IN (favouriteSpaces(), 'FS', 'TS')");
    }

    @Test
    void testCQLUnknownFunction()
    {
        expectParserException(
            "Unknown function [unknown] (line 1, col 9, pos 8)",
            "space = unknown()");
    }

    @Test
    void testCQLBool() throws Exception
    {
        assertEquals(
            "title_sort:(true OR false OR true OR false)",
            t("title in (true, false, TRUE, FALSE)")
        );
    }

    @Test
    void testNumberInString() throws Exception
    {
        // Should be handled as a string and not as a number.
        // FIXME This doesn't actually show up in the result.
        assertEquals(
            "title_sort:{1337 TO *]",
            t("title > '1337'")
        );
    }

    @Test
    void testGT() throws Exception
    {
        // Should be handled as a string and not as a number.
        assertEquals(
            "title_sort:{42 TO *]",
            t("title > 42")
        );
    }

    @Test
    void testEmptyString() throws Exception
    {
        assertEquals(
            "(-title_sort:\"\") AND (title_sort:(\"\" OR 42))",
            t("title != '' and title in ('', 42)")
        );
    }

    @Test
    void testQuotedString() throws Exception
    {
        assertEquals(
            "(title_sort:hello'world) OR (title_sort:hello\\\"world2) OR (title_sort:') OR (title_sort:\\\")",
            t("title = 'hello\\'world' or title = \"hello\\\"world2\" or title = '\\'' or title = \"\\\"\"")
        );
    }

    @Test
    void testNotActuallyADate() throws Exception
    {
        assertEquals(
            "title_sort:2018\\-01\\-01\\ extra\\ characters",
            t("title = '2018-01-01 extra characters'")
        );
    }

    @Test
    void testStringNotEnded()
    {
        expectParserException(
            "Unexpected end, expected the end of string started at [line 1, col 9, pos 8] (line 1, col 15, pos 14)."
                + PATH_STRING,
            "space = 'hello");
    }

    @Test
    void testStringMissingQuotedChar()
    {
        expectParserException(
            "Unexpected end, expected an escaped character after '\\', and the end of string started at"
                + " [line 1, col 9, pos 8] (line 1, col 16, pos 15)."
                + PATH_STRING,
            "space = 'hello\\");
    }

    @Test
    void testBadAtomicOp()
    {
        expectParserException(
            "Expected a CQL operator among [IN, NOT IN, =, !=, >=, <=, >, <, ~, !~] (line 1, col 7, pos 6)."
                + " Path: clauses > clause > atom > atom operator",
            "space * hello");
    }

    @Test
    void testBadClauseOp()
    {
        expectParserException(
            "Unexpected character '*' while trying to parse the end of the CQL expression."
                + " Maybe a AND or OR operator is missing? (line 1, col 16, pos 15)",
            "space = hello * title = mytitle");
    }

    @Test
    void testClauseEscapeString() throws Exception
    {
        assertEquals(
            "title_sort:(\\OR OR \\AND OR \\NOT)",
            t("title in ('OR', 'AND', 'NOT')")
        );
    }

    @Test
    void testSpaceAfterFunctionName() throws Exception
    {
        assertEquals(
            "creationdate:{NOW-1DAYS TO *]",
            t("created > now ( '-1d' )")
        );
    }

    @Test
    void testSpaceInFunctionArgs() throws Exception
    {
        assertEquals(
            "space_facet:0\\/MySpaceTests.",
            t("space=currentSpace( )")
        );
    }

    @Test
    void testParen1() throws Exception
    {
        assertEquals(
            "((title_sort:{42 TO *]) OR (title_sort:mytitle)) AND (creator:*\\:XWiki.Guest)",
            t("(title > 42 or title = mytitle) and (creator = currentUser())")
        );
    }

    @Test
    void testMissingClauseCloseParen()
    {
        expectParserException(
            "Expected closing parenthesis ')' (line 1, col 61, pos 60)."
                + " Path: clauses > clause",
            "(title > 42 or title = mytitle and (creator = currentUser())"
        );
    }

    @Test
    void testJustOneOpenParen()
    {
        expectParserException(
            "Expected a field (line 1, col 2, pos 1). Path: clauses > clause > clauses > clause > atom > field",
            "("
        );
    }

    @Test
    void testMissingFunctionCallCloseParen1()
    {
        expectParserException(
            "Unexpected end while trying to parse the end of the function call (line 1, col 23, pos 22)."
                + PATH_FN_CALL,
            "creator = currentUser("
        );
    }

    @Test
    void testMissingFunctionCallCloseParen2()
    {
        expectParserException(
            "Unexpected end while trying to parse the end of the function call (line 1, col 28, pos 27)."
                + PATH_FN_CALL,
            "creator = currentUser(hello"
        );
    }

    @Test
    void testMissingFunctionCallCloseParen3()
    {
        expectParserException(
            "Unexpected end while trying to parse the end of the function call (line 1, col 29, pos 28)."
                + PATH_FN_CALL,
            "creator = currentUser(hello,"
        );
    }

    @Test
    void testMissingInParen()
    {
        expectParserException(
            "Expected opening parenthesis '(' (line 1, col 13, pos 12). Path: clauses > clause > atom > in expr",
            "title not in"
        );
    }

    @Test
    void testEmpty()
    {
        expectParserException(
            "The CQL expression is empty (line 0, col 0, pos 0)",
            ""
        );
    }
}

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

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.xwiki.component.internal.ContextComponentManagerProvider;
import org.xwiki.contrib.confluence.resolvers.internal.DefaultConfluencePageResolver;
import org.xwiki.contrib.confluence.resolvers.internal.DefaultConfluenceSpaceResolver;
import org.xwiki.contrib.confluence.resolvers.internal.PageClassConfluenceResolver;
import org.xwiki.contrib.cql.query.converters.CQLToSolrQueryConverter;
import org.xwiki.contrib.cql.query.converters.DefaultCQLToSolrAtomConverter;
import org.xwiki.contrib.cql.query.converters.DefaultCQLToSolrSortParameterConverter;
import org.xwiki.contrib.cql.query.converters.internal.AncestorCQLToSolrAtomConverter;
import org.xwiki.contrib.cql.query.converters.internal.ContentCQLToSolrAtomConverter;
import org.xwiki.contrib.cql.query.converters.internal.ParentCQLToSolrAtomConverter;
import org.xwiki.contrib.cql.query.internal.CQLQueryExecutor;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryExecutor;
import org.xwiki.query.internal.DefaultQueryExecutorManager;
import org.xwiki.query.internal.DefaultQueryManager;
import org.xwiki.query.solr.internal.SolrQueryExecutor;
import org.xwiki.search.solr.internal.api.SolrInstance;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import com.xpn.xwiki.store.hibernate.query.DefaultQueryExecutor;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Basic test for the {@link CQLQueryExecutor}.
 * @version $Id$
 */
@OldcoreTest
@ComponentList({
    DefaultQueryManager.class,
    DefaultQueryExecutorManager.class,
    ContextComponentManagerProvider.class,
    DefaultConfluencePageResolver.class,
    DefaultConfluenceSpaceResolver.class,
    PageClassConfluenceResolver.class,
    AncestorCQLToSolrAtomConverter.class,
    ContentCQLToSolrAtomConverter.class,
    ParentCQLToSolrAtomConverter.class,
    DefaultCQLToSolrAtomConverter.class,
    TestCQLToSolrAtomConverter.class,
    TestCQLToSolrSortParameterConverter.class,
    DefaultCQLToSolrSortParameterConverter.class,
    CQLToSolrQueryConverter.class
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ReferenceComponentList
class CQLQueryExecutorTest
{
    private static final DocumentReference GUEST = new DocumentReference("xwiki", "XWiki", "Guest");

    @InjectMockitoOldcore
    private MockitoOldcore mockitoOldcore;

    @InjectMockComponents
    private DefaultQueryManager queryManager;

    @InjectMockComponents
    private DefaultQueryExecutor queryExecutor;

    @InjectMockComponents
    private CQLQueryExecutor cqlQueryExecutor;

    @InjectMockComponents
    private SolrQueryExecutor solrQueryExecutor;

    @MockComponent
    private SolrInstance solr;

    private final MockitoComponentMockingRule<QueryExecutor> componentManager =
        new MockitoComponentMockingRule<>(SolrQueryExecutor.class);

    @BeforeEach
    void configure()
    {
        mockitoOldcore.getXWikiContext().setUserReference(GUEST);
    }

    @Test
    void execute() throws QueryException, SolrServerException, IOException
    {
        QueryResponse r = mock(QueryResponse.class);
        when(r.getResults()).thenReturn(new SolrDocumentList());
        when(solr.query(any(SolrQuery.class))).then(invocation -> {
            SolrQuery solrQuery = (SolrQuery) invocation.getArguments()[0];
            assertEquals("(creator:*\\:XWiki.Guest) AND (mysolrfieldA:1337 OR mysolrfieldB:1337) "
                + "AND (mysolrfieldA:The\\ answer OR mysolrfieldB:The\\ answer)", solrQuery.getQuery());
            assertEquals(
                "title_sort desc,property.XWiki.TagClass.tags_sortString asc,creator_display asc,test_sort asc",
                solrQuery.getSortField());
            return r;
        });

        Query query = queryManager.createQuery(
            "creator = currentUser() and test1 = 1337 and test2 = 42 order by title desc, label asc, creator, test3",
            "cql");
        assertSame(r, query.execute().get(0));
    }
}

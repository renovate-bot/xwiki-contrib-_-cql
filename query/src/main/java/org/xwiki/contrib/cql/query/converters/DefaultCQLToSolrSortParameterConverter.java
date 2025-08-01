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
package org.xwiki.contrib.cql.query.converters;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.cql.aqlparser.ast.AQLOrderByClause;
import org.xwiki.contrib.cql.aqlparser.ast.AQLStatement;
import org.xwiki.stability.Unstable;

/**
 * Default CQL to Solr Sort Field Converter.
 * @since 0.0.1
 * @version $Id$
 */
@Component
@Singleton
@Unstable
public class DefaultCQLToSolrSortParameterConverter implements CQLToSolrSortFieldConverter
{
    private static final String AUTHOR_SOLR_SORT = "author_display_sort";
    private static final String DOCCONTENTRAW_SORT = "doccontentraw_sortString";

    private static final Map<String, String> SOLR_SORT_FIELD_BY_CQL_FIELD = new HashMap<>();
    static {
        SOLR_SORT_FIELD_BY_CQL_FIELD.put("creator", "creator_display");
        SOLR_SORT_FIELD_BY_CQL_FIELD.put("contributor", AUTHOR_SOLR_SORT);
        SOLR_SORT_FIELD_BY_CQL_FIELD.put("user", AUTHOR_SOLR_SORT);
        SOLR_SORT_FIELD_BY_CQL_FIELD.put("user.fullname", AUTHOR_SOLR_SORT);
        SOLR_SORT_FIELD_BY_CQL_FIELD.put("user.accountid", AUTHOR_SOLR_SORT);
        SOLR_SORT_FIELD_BY_CQL_FIELD.put("content", DOCCONTENTRAW_SORT);
        SOLR_SORT_FIELD_BY_CQL_FIELD.put("title", "title_sort");
        SOLR_SORT_FIELD_BY_CQL_FIELD.put("created", "creationdate");
        SOLR_SORT_FIELD_BY_CQL_FIELD.put("lastmodified", "date");
        SOLR_SORT_FIELD_BY_CQL_FIELD.put("text", DOCCONTENTRAW_SORT);
        SOLR_SORT_FIELD_BY_CQL_FIELD.put("space", "space_facet");
        SOLR_SORT_FIELD_BY_CQL_FIELD.put("label", "property.XWiki.TagClass.tags_sortString");
    }

    /**
     * @return the solr sort field.
     * @param cql the CQL statement in which the field appears
     * @param orderByParameter the order by parameter being handled
     * @param field the field to convert
     * @throws ConversionException if the solr sort field couldn't be determined for some reason
     */
    public String getSolrSortField(AQLStatement cql, AQLOrderByClause orderByParameter, String field)
        throws ConversionException
    {
        return SOLR_SORT_FIELD_BY_CQL_FIELD.get(field.toLowerCase());
    }

    @Override
    public String getSolrSortParameter(AQLStatement cql, AQLOrderByClause orderByParameter, String cqlField)
        throws ConversionException
    {
        String solrField = getSolrSortField(cql, orderByParameter, cqlField);
        if (solrField == null || solrField.isEmpty()) {
            return null;
        }

        return solrField + (orderByParameter.isDesc() ? " desc" : " asc");
    }
}

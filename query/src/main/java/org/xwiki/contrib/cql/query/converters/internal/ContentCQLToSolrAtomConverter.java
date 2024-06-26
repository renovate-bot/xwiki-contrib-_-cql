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
package org.xwiki.contrib.cql.query.converters.internal;

import java.util.Collections;
import java.util.List;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Named;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.cql.aqlparser.ast.AQLAtomicClause;
import org.xwiki.contrib.cql.query.converters.CQLToSolrAtomConverter;
import org.xwiki.contrib.cql.query.converters.ConversionException;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.stability.Unstable;

/**
 * Handler for the CQL ancestor field.
 * @version $Id$
 * @since 0.0.1
 */
@Component (hints = {"id", "content"})
@Unstable
@Priority(900)
public class ContentCQLToSolrAtomConverter extends AbstractIdCQLToSolrAtomConverter implements CQLToSolrAtomConverter
{
    private static final List<String> FULLNAME = Collections.singletonList("fullname");

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> serializer;

    @Override
    protected List<String> getSolrFields(AQLAtomicClause atom) throws ConversionException
    {
        return FULLNAME;
    }

    protected String getValue(EntityReference docRef)
    {
        return serializer.serialize(docRef);
    }
}

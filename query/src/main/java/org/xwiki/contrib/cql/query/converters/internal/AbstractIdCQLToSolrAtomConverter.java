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

import javax.inject.Inject;
import javax.inject.Provider;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.resolvers.ConfluencePageIdResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluenceResolverException;
import org.xwiki.contrib.cql.aqlparser.ast.AQLFunctionCall;
import org.xwiki.contrib.cql.aqlparser.ast.AbstractAQLAtomicValue;
import org.xwiki.contrib.cql.aqlparser.ast.AQLAtomicClause;
import org.xwiki.contrib.cql.query.converters.ConversionException;
import org.xwiki.contrib.cql.query.converters.DefaultCQLToSolrAtomConverter;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.stability.Unstable;


import com.xpn.xwiki.XWikiContext;

import static org.xwiki.contrib.cql.query.converters.Utils.escapeSolr;

/**
 * A helper class to implement CQL fields which deal with Confluence IDs.
 * @version $Id$
 * @since 0.0.1
 */
@Unstable
@Component(staticRegistration = false)
public abstract class AbstractIdCQLToSolrAtomConverter extends DefaultCQLToSolrAtomConverter
{
    @Inject
    private ConfluencePageIdResolver idResolver;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Override
    protected String convertToSolr(AQLAtomicClause atom, AbstractAQLAtomicValue right) throws ConversionException
    {
        EntityReference docRef = tryCurrentContentFunction(right);

        if (docRef == null) {
            docRef = getIdFromValue(atom, right);
        }

        if (docRef == null) {
            return null;
        }

        String v = getValue(docRef);
        if (v == null) {
            return null;
        }

        return escapeSolr(v);
    }

    private EntityReference getIdFromValue(AQLAtomicClause atom, AbstractAQLAtomicValue right)
        throws ConversionException
    {
        String value = super.convertToSolr(atom, right);

        long id;
        try {
            id = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ConversionException("Expected a Confluence content id (a number)", right.getParserState());
        }

        String err = String.format("Could not find the document matching Confluence id [%d]", id);
        try {
            EntityReference docRef = idResolver.getDocumentById(id);
            if (docRef != null) {
                return docRef;
            }
        } catch (ConfluenceResolverException e) {
            throw new ConversionException(err, e, atom.getRight() == null ? null : atom.getRight().getParserState());
        }
        throw new ConversionException(err, atom.getRight().getParserState());
    }

    private EntityReference tryCurrentContentFunction(AbstractAQLAtomicValue right) throws ConversionException
    {
        if (right instanceof AQLFunctionCall) {
            AQLFunctionCall fn = (AQLFunctionCall) right;
            if ("currentcontent".equalsIgnoreCase(fn.getFunctionName())) {
                if (!fn.getArguments().isEmpty()) {
                    throw new ConversionException("Function [currentUser] does not take any argument",
                        right.getParserState());
                }
                return contextProvider.get().getDoc().getDocumentReference();
            }
        }
        return null;
    }

    protected abstract String getValue(EntityReference docRef);
}

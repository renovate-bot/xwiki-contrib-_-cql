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

import javax.annotation.Priority;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.cql.aqlparser.ast.AQLAtomicClause;
import org.xwiki.contrib.cql.aqlparser.ast.AbstractAQLAtomicValue;
import org.xwiki.contrib.cql.query.converters.ConversionException;
import org.xwiki.stability.Unstable;

/**
 * Handler for the CQL ancestor field.
 * @version $Id$
 * @since 0.0.1
 */
@Component
@Named("parent")
@Unstable
@Priority(900)
public class ParentCQLToSolrAtomConverter extends AncestorCQLToSolrAtomConverter
{
    private static final String THIS_IS_A_BUG = "This is a bug in the CQL module, please report.";

    @Override
    protected String convertToSolr(AQLAtomicClause atom, AbstractAQLAtomicValue right) throws ConversionException
    {
        String s = super.convertToSolr(atom, right);
        if (StringUtils.isEmpty(s)) {
            throw new ConversionException("Didn't expect to have an empty ancestor Solr conversion. " + THIS_IS_A_BUG,
                atom.getParserState());
        }
        // We expect s to be of the shape "N\/Space1.Space2...SpaceN+1."
        // This is the wanted parent space.
        // The result should be pages that are direct children of this parent. This means pages that:
        //  - (1) have s in their space_facet
        //  - (2) and also N+1\/* (non-terminal children pages - WebHome pages that have at least one additional space)
        //  - (3) and  not N+2\/* (because they are not direct children)

        int slash = s.indexOf("\\/");
        if (slash == -1) {
            error(atom);
        }

        int n = -1;
        try {
            n = Integer.parseInt(s.substring(0, slash).trim());
        } catch (NumberFormatException e) {
            error(atom);
        }
        //          (1)              (2)                        (3)
        return '(' + s + " AND " + (n + 1) + "\\/* AND -" + (n + 2) + "\\/*)";
    }

    private void error(AQLAtomicClause atom) throws ConversionException
    {
        throw new ConversionException(
            "Expected the ancestor clause to be converted to something like N/*, but it wasn't."
                + THIS_IS_A_BUG, atom.getParserState());
    }
}

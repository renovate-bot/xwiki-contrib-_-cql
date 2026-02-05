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

import org.xwiki.component.annotation.Role;
import org.xwiki.contrib.cql.aqlparser.ast.AQLAtomicClause;
import org.xwiki.stability.Unstable;

/**
 * CQL to Solr Atom Converter.
 * @since 0.0.1
 * @version $Id$
 */
@Role
@Unstable
public interface CQLToSolrAtomConverter
{
    /**
     * @return the given CQL atom converted to a Solr atom, as string
     * @param atom the atom to convert
     * @throws ConversionException if something wrong happens
     */
    String convertToSolr(AQLAtomicClause atom) throws ConversionException;

    /**
     * @return the name(s) of the fields handled by this converter, as a String equal to the name or a Pattern
     * matching the name. If null, the name of the component will be used.
     * @since 0.2.0
     */
    default Object getHandledFields()
    {
        return null;
    }
}

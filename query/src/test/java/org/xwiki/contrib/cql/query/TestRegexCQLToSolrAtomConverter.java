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

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.cql.aqlparser.ast.AQLAtomicClause;
import org.xwiki.contrib.cql.aqlparser.ast.AQLNumberLiteral;
import org.xwiki.contrib.cql.query.converters.CQLToSolrAtomConverter;
import org.xwiki.contrib.cql.query.converters.ConversionException;
import org.xwiki.contrib.cql.query.converters.DefaultCQLToSolrAtomConverter;

import static org.xwiki.contrib.cql.query.converters.Utils.escapeSolr;

/**
 * Test converter.
 * @version $Id$
 */
@Component
@Singleton
@Named("test.*")
public class TestRegexCQLToSolrAtomConverter extends DefaultCQLToSolrAtomConverter implements CQLToSolrAtomConverter
{
    private static final Pattern PATTERN = Pattern.compile("test\\.([a-z]+)");

    @Override
    protected List<String> getSolrFields(AQLAtomicClause atom)
    {
        Matcher matcher = PATTERN.matcher(atom.getField());
        return List.of(matcher.matches() ? "testfield_" + matcher.group(1) : "unknown");
    }

    @Override
    public Object getHandledFields()
    {
        return PATTERN;
    }

    @Override
    protected String convertToSolr(AQLAtomicClause atom, AQLNumberLiteral expression) throws ConversionException
    {
        if (expression.getNumber().equals("42")) {
            return escapeSolr("The answer");
        }
        return super.convertToSolr(atom, expression);
    }
}

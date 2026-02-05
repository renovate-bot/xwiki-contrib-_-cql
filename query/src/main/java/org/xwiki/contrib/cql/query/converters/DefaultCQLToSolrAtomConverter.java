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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceKeyResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluenceResolverException;
import org.xwiki.contrib.cql.aqlparser.AQLOperator;
import org.xwiki.contrib.cql.aqlparser.ast.AQLAtomicClause;
import org.xwiki.contrib.cql.aqlparser.ast.AQLBooleanLiteral;
import org.xwiki.contrib.cql.aqlparser.ast.AQLDateLiteral;
import org.xwiki.contrib.cql.aqlparser.ast.AQLInExpression;
import org.xwiki.contrib.cql.aqlparser.ast.AQLNumberLiteral;
import org.xwiki.contrib.cql.aqlparser.ast.AQLStringLiteral;
import org.xwiki.contrib.cql.aqlparser.ast.AbstractAQLAtomicValue;
import org.xwiki.contrib.cql.aqlparser.ast.AbstractAQLRightHandValue;
import org.xwiki.contrib.cql.aqlparser.ast.AQLAtomicClauseOperator;
import org.xwiki.contrib.cql.aqlparser.ast.AQLFunctionCall;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.stability.Unstable;


import com.xpn.xwiki.XWikiContext;

import static org.xwiki.contrib.cql.query.converters.Utils.betweenParentheses;
import static org.xwiki.contrib.cql.query.converters.Utils.escapeSolr;

/**
 * Default CQL to Solr Atom Converter.
 * @since 0.0.1
 * @version $Id$
 */
@Component
@Named("")
@Singleton
@Unstable
public class DefaultCQLToSolrAtomConverter implements CQLToSolrAtomConverter
{
    private static final String SPACED_OR = " OR ";
    private static final String SPACED_AND = " AND ";

    private static final String UNEXP = " This is unexpected, please report an issue";

    private static final String CREATOR = "creator";
    private static final String CONTRIBUTOR = "contributor";
    private static final String USER = "user";
    private static final String USER_FULLNAME = "user.fullname";
    private static final String USER_ACCOUNTID = "user.accountid";
    private static final String ANCESTOR = "ancestor";
    private static final String CONTENT = "content";
    private static final String ID = "id";
    private static final String PARENT = "parent";
    private static final String TITLE = "title";
    private static final String CREATED = "created";
    private static final String LASTMODIFIED = "lastmodified";
    private static final String TEXT = "text";
    private static final String SPACE = "space";
    private static final String SPACE_KEY = "space.key";
    private static final String LABEL = "label";
    private static final String TYPE = "type";

    private static final String DATE = "date";
    private static final String SOLR_TAGS = "property.XWiki.TagClass.tags";
    private static final String SOLR_CREATIONDATE = "creationdate";
    private static final String SOLR_CONTENT = CONTENT;
    private static final String SOLR_DOCUMENT = "DOCUMENT";
    private static final String SOLR_CLASS = "class";
    private static final String SOLR_TITLE_SORT = "title_sort";

    private static final List<String> CREATED_SOLR_FIELDS = Collections.singletonList(SOLR_CREATIONDATE);
    private static final List<String> LASTMODIFIED_SOLR_FIELDS = Collections.singletonList(DATE);
    private static final List<String> TYPE_SOLR_FIELDS = Collections.singletonList(TYPE);

    private static final List<String> TEXT_SOLR_FIELDS = Arrays.asList(SOLR_TITLE_SORT, SOLR_TAGS, SOLR_CONTENT);
    private static final List<String> TITLE_SOLR_FIELDS = Collections.singletonList(SOLR_TITLE_SORT);
    private static final List<String> SPACES_SOLR_FIELDS = Collections.singletonList("space_facet");
    private static final List<String> TAGS_SOLR_FIELDS = Collections.singletonList(SOLR_TAGS);
    private static final List<String> CREATOR_SOLR_FIELDS = Collections.singletonList(CREATOR);

    private static final String BLOG_POST_CLASS = "Blog.BlogPostClass";

    private static final Pattern INC_PATTERN = Pattern.compile("(?<n>(?:-|\\+|)\\d+)(?<what>[yMwdhm])");

    private static final List<String> CQL_RESERVED_WORDS = Arrays.asList(
        "after", "and", "as", "avg", "before", "begin", "by", "commit", "contains", "count", "distinct", "else",
        "empty", "end", "explain", "from", "having", "if", "in", "inner", "insert", "into", "is", "isnull", "left",
        "like", "limit", "max", "min", "not", "null", "or", "order", "outer", "right", "select", "sum", "then", "was",
        "where", "update"
    );

    private static final Map<String, String> CQL_DATE_FN_TO_SOLR = new HashMap<>(9);
    private static final String FAILED_TO_EVALUATE_CURRENT_SPACE = "Failed to evaluate [currentSpace()]";

    static {
        CQL_DATE_FN_TO_SOLR.put("now", "NOW");
        CQL_DATE_FN_TO_SOLR.put("startOfDay", "NOW/DAY");
        CQL_DATE_FN_TO_SOLR.put("endOfDay", "NOW+DAY/DAY");
        CQL_DATE_FN_TO_SOLR.put("startOfWeek", "NOW/WEEK");
        CQL_DATE_FN_TO_SOLR.put("endOfWeek", "NOW+WEEK/WEEK");
        CQL_DATE_FN_TO_SOLR.put("startOfMonth", "NOW/MONTH");
        CQL_DATE_FN_TO_SOLR.put("endOfMonth", "NOW+MONTH/MONTH");
        CQL_DATE_FN_TO_SOLR.put("startOfYear", "NOW/YEAR");
        CQL_DATE_FN_TO_SOLR.put("endOfYear", "NOW+YEAR/YEAR");
    }

    private static final List<String> USER_RELATED_CQL_FIELDS = Arrays.asList(
        CREATOR, CONTRIBUTOR, USER, USER_FULLNAME, USER_ACCOUNTID);

    // id is an alias of content
    private static final List<String> UNSUPPORTED_CQL_FIELDS = Arrays.asList(
        ANCESTOR, CONTENT, ID, PARENT, "favourite", "favorite", "macro", "mention", "watcher", "space.title");

    private static final Map<String, String> SOLR_UNITS_BY_CQL_SUFFIX = new HashMap<>(6);
    static {
        SOLR_UNITS_BY_CQL_SUFFIX.put("y", "YEARS");
        SOLR_UNITS_BY_CQL_SUFFIX.put("M", "MONTHS");
        SOLR_UNITS_BY_CQL_SUFFIX.put("w", "WEEKS");
        SOLR_UNITS_BY_CQL_SUFFIX.put("d", "DAYS");
        SOLR_UNITS_BY_CQL_SUFFIX.put("h", "HOURS");
        SOLR_UNITS_BY_CQL_SUFFIX.put("m", "MINUTES");
    }

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private ConfluenceSpaceKeyResolver confluenceSpaceKeyResolver;

    @Inject
    private ConfluenceSpaceResolver confluenceSpaceResolver;

    @Override
    public String convertToSolr(AQLAtomicClause atom) throws ConversionException
    {
        List<String> solrFields = getSolrFields(atom);
        String solrValue = getSolrValue(atom);
        if (solrValue == null) {
            return null;
        }
        return convertToSolr(atom, solrFields, solrValue);
    }

    /**
     * Convert a CQL atom to a Solr expression.
     * @return the result of the conversion to Solr as string
     * @param atom the atom being converted
     * @throws ConversionException if this atom can't be converted
     */
    protected String getSolrValue(AQLAtomicClause atom) throws ConversionException
    {
        AbstractAQLRightHandValue right = atom.getRight();

        if (right instanceof AbstractAQLAtomicValue) {
            return convertToSolr(atom, (AbstractAQLAtomicValue) right);
        }

        if (right instanceof AQLInExpression) {
            return convertToSolr(atom, (AQLInExpression) right);
        }

        throw new ConversionException(String.format("BUG: Unexpected right hand value [%s].", right) + UNEXP,
            atom.getOp().getParserState());
    }

    /**
     * Convert a boolean CQL atomic value to a Solr expression.
     * @return the result of the conversion to Solr as string
     * @param atom the atom being converted
     * @param expression the expression corresponding to this atom
     * @throws ConversionException if this expression can't be converted
     */
    protected String convertToSolr(AQLAtomicClause atom, AbstractAQLAtomicValue expression) throws ConversionException
    {
        if (expression instanceof AQLFunctionCall) {
            return convertToSolr(atom, (AQLFunctionCall) expression);
        }

        if (expression instanceof AQLDateLiteral) {
            return convertToSolr(atom, (AQLDateLiteral) expression);
        }

        if (expression instanceof AQLNumberLiteral) {
            return convertToSolr(atom, (AQLNumberLiteral) expression);
        }

        if (expression instanceof AQLStringLiteral) {
            return convertToSolr(atom, (AQLStringLiteral) expression);
        }

        if (expression instanceof AQLBooleanLiteral) {
            return convertToSolr(atom, (AQLBooleanLiteral) expression);
        }

        throw new ConversionException("BUG: Unsupported construct." + UNEXP, expression.getParserState());
    }

    /**
     * Convert a CQL boolean literal to a Solr expression.
     * @return the result of the conversion to Solr as string
     * @param atom the atom being converted
     * @param expression the expression corresponding to this atom
     * @throws ConversionException if this expression can't be converted
     */
    protected String convertToSolr(AQLAtomicClause atom, AQLBooleanLiteral expression) throws ConversionException
    {
        return expression.isTrue() ? "true" : "false";
    }

    /**
     * Convert a CQL String literal to a Solr expression.
     * @return the result of the conversion to Solr as string
     * @param atom the atom being converted
     * @param expression the expression corresponding to this atom
     * @throws ConversionException if this expression can't be converted
     */
    protected String convertToSolr(AQLAtomicClause atom, AQLStringLiteral expression) throws ConversionException
    {
        String v = expression.getString();
        if (atom.getField().equals(SPACE) || atom.getField().equals(SPACE_KEY)) {
            String err = String.format("Failed to find space [%s]", v);
            try {
                EntityReference space = confluenceSpaceKeyResolver.getSpaceByKey(v);
                if (space == null) {
                    throw new ConversionException(err, expression.getParserState());
                }
                return getSpaceFacet(space);
            } catch (ConfluenceResolverException e) {
                throw new ConversionException(err, e, expression.getParserState());
            }
        }
        return escapeSolr(v);
    }

    private String getSpaceFacet(EntityReference space)
    {
        int facetNumber = space.getReversedReferenceChain().size() - 1;
        if (EntityType.WIKI.equals(space.getRoot().getType())) {
            facetNumber--;
        }
        return escapeSolr(facetNumber + "/" + serializer.serialize(space) + '.');
    }

    private EntityReference getCurrentConfluenceSpace(AQLFunctionCall expression) throws ConversionException
    {
        EntityReference res;
        try {
            res = confluenceSpaceResolver.getSpace(contextProvider.get().getDoc().getDocumentReference());
        } catch (ConfluenceResolverException e) {
            throw new ConversionException(FAILED_TO_EVALUATE_CURRENT_SPACE, e, expression.getParserState());
        }

        if (res == null) {
            throw new ConversionException(FAILED_TO_EVALUATE_CURRENT_SPACE, expression.getParserState());
        }

        return res;
    }

    /**
     * Convert a number CQL expression to a Solr expression.
     * @return the result of the conversion to Solr as string
     * @param atom the atom being converted
     * @param expression the expression corresponding to this atom
     * @throws ConversionException if this expression can't be converted
     */
    protected String convertToSolr(AQLAtomicClause atom, AQLNumberLiteral expression) throws ConversionException
    {
        return escapeSolr(expression.getNumber());
    }

    /**
     * Convert a CQL Date literal to a Solr expression.
     * @return the result of the conversion to Solr as string
     * @param atom the atom being converted
     * @param expression the expression corresponding to this atom
     * @throws ConversionException if this expression can't be converted
     */
    protected String convertToSolr(AQLAtomicClause atom, AQLDateLiteral expression) throws ConversionException
    {
        String d = String.format("%s-%s-%s",
            expression.getYear(), pad(expression.getMonth()), pad(expression.getDay()));

        if (expression.getHours() != -1 && expression.getMinutes() != -1) {
            d += String.format("T%s:%s:00.000Z", pad(expression.getHours()), pad(expression.getMinutes()));
        } else {
            d += "T00:00:00.000Z";
        }
        return '"' + d + '"';
    }

    private String getEscapedCurrentUser()
    {
        String currentUser = serializer.serialize(contextProvider.get().getUserReference());
        return (currentUser.indexOf(':') == -1 ? "*\\:" : "") + escapeSolr(currentUser);
    }

    /**
     * Convert a CQL Function call to a Solr expression.
     * @return the result of the conversion to Solr as string
     * @param atom the atom being converted
     * @param expression the expression corresponding to this atom
     * @throws ConversionException if this expression can't be converted
     */
    protected String convertToSolr(AQLAtomicClause atom, AQLFunctionCall expression) throws ConversionException
    {
        String functionName = expression.getFunctionName();
        String baseDate = CQL_DATE_FN_TO_SOLR.get(functionName);
        if (baseDate == null) {
            // this is not a date function
            switch (functionName) {
                case "currentUser":
                    if (!expression.getArguments().isEmpty()) {
                        throw new ConversionException("Function [currentUser] does not take any argument",
                            expression.getParserState());
                    }

                    return getEscapedCurrentUser();

                case "recentlyViewedContent":
                case "recentlyViewedSpaces":
                case "currentContent":
                case "favouriteSpaces":
                    throw new ConversionException(String.format("Unsupported function [%s]", functionName),
                        expression.getParserState());

                case "currentSpace":
                    if (!expression.getArguments().isEmpty()) {
                        throw new ConversionException("Function [currentSpace] does not take any argument",
                            expression.getParserState());
                    }

                    return getSpaceFacet(getCurrentConfluenceSpace(expression));

                default:
                    throw new ConversionException(String.format("Unknown function [%s]", functionName),
                        expression.getParserState());
            }
        } else {
            // this is a date function
            return baseDate + evalDateFn(expression);
        }
    }

    /**
     * Convert a CQL IN expression to a Solr expression.
     * @return the result of the conversion to Solr as string
     * @param atom the atom being converted
     * @param expression the expression corresponding to this atom
     * @throws ConversionException if this expression can't be converted
     */
    protected String convertToSolr(AQLAtomicClause atom, AQLInExpression expression) throws ConversionException
    {
        String solrValue;
        List<AbstractAQLAtomicValue> values = expression.getValues();
        List<String> convertedValues = new ArrayList<>(values.size());
        for (AbstractAQLAtomicValue value : values) {
            convertedValues.add(convertToSolr(atom, value));
        }
        solrValue = betweenParentheses(String.join(SPACED_OR, convertedValues));
        return solrValue;
    }

    /**
     * @return the list of solr fields corresponding to this CQL atom.
     * @param atom the atom
     * @throws ConversionException if the corresponding list of solr fields can't be determined for some reason
     */
    protected List<String> getSolrFields(AQLAtomicClause atom) throws ConversionException
    {
        String field = atom.getField();
        if (UNSUPPORTED_CQL_FIELDS.contains(field)) {
            throw new ConversionException(
                String.format("Field [%s] is not supported yet", field), atom.getParserState());
        }

        AbstractAQLRightHandValue right = atom.getRight();

        boolean isCurrentUserFunctionCall = right instanceof AQLFunctionCall
            && "currentuser".equalsIgnoreCase(((AQLFunctionCall) right).getFunctionName());

        List<String> solrFields;
        if (isCurrentUserFunctionCall) {
            if (USER_RELATED_CQL_FIELDS.contains(field)) {
                solrFields = CREATOR_SOLR_FIELDS;
            } else {
                throw new ConversionException("currentUser cannot be used with field [" + field + ']',
                    atom.getParserState());
            }
        } else if (USER_RELATED_CQL_FIELDS.contains(field)) {
            throw new ConversionException(
                String.format("For now, the only supported value with field [%s] is currentUser()", field),
                atom.getRight().getParserState());
        } else {
            solrFields = getUserUnrelatedSolrFields(atom);
        }
        return solrFields;
    }

    private static List<String> getUserUnrelatedSolrFields(AQLAtomicClause atom)
        throws ConversionException
    {
        List<String> solrFields;

        String field = atom.getField();
        boolean contains = atom.getOp().getOperator().equals(AQLOperator.CONTAINS)
                        || atom.getOp().getOperator().equals(AQLOperator.DOES_NOT_CONTAIN);
        if (TEXT.equals(field) && !contains) {
            throw new ConversionException(
                String.format(
                    "Field [%s] is only supported with the 'contains' (~) and 'does not contain' (!~) operators because"
                        + " we don't index the data as string fields in Solr required for supporting other operators",
                    field),
                atom.getParserState());
        }
        switch (field) {
            case LABEL:
                solrFields = TAGS_SOLR_FIELDS;
                break;
            case SPACE:
            case SPACE_KEY:
                // takes a string, but might have been moved by the migration. We can try our best though.
                solrFields = SPACES_SOLR_FIELDS;
                break;
            case TEXT:
                // searches in title, labels and body
                solrFields = TEXT_SOLR_FIELDS;
                break;
            case TITLE:
                solrFields = TITLE_SOLR_FIELDS;
                break;
            case CREATED:
                solrFields = CREATED_SOLR_FIELDS;
                break;
            case LASTMODIFIED:
                solrFields = LASTMODIFIED_SOLR_FIELDS;
                break;
            case TYPE:
                solrFields = TYPE_SOLR_FIELDS;
                break;
            case CREATOR:
                // This should not happen. User-related fields should be handled elsewhere.
                solrFields = CREATOR_SOLR_FIELDS;
                break;

            default:
                if (CQL_RESERVED_WORDS.contains(field)) {
                    throw new ConversionException(
                        String.format("[%s] is a reserved word which cannot be used as a field", field),
                        atom.getParserState());
                }

                throw new ConversionException(String.format("Field [%s] is unknown", field), atom.getParserState());
        }
        return solrFields;
    }

    private String convertToSolr(AQLAtomicClause atom, List<String> solrFields, String solrValue)
        throws ConversionException
    {
        List<String> solrAtoms = new ArrayList<>(solrFields.size());

        AQLOperator op = atom.getOp().getOperator();
        boolean not = op == AQLOperator.NEQ || op == AQLOperator.NOT_IN || op == AQLOperator.DOES_NOT_CONTAIN;
        String solrClauseOp = SPACED_OR;

        if (TYPE.equals(atom.getField())) {
            solrClauseOp = convertTypeFieldToSolr(atom, solrValue, solrAtoms);
        } else {
            for (String solrField : solrFields) {
                solrAtoms.add(toSolrAtomIgnoringNegativeOp(atom, solrField, solrValue));
            }
        }

        String dash = not ? "-" : "";
        if (solrAtoms.size() > 1) {
            String res = String.join(solrClauseOp, solrAtoms);
            if (not) {
                res = betweenParentheses(res);
            }
            return dash + res;
        }

        if (solrAtoms.isEmpty()) {
            throw  new ConversionException("BUG: No Solr expressions were generated for this CQL expression." + UNEXP,
                atom.getParserState());
        }

        return dash + solrAtoms.get(0);
    }

    private String convertTypeFieldToSolr(AQLAtomicClause atom, String escapedSolrValue, List<String> solrAtoms)
        throws ConversionException
    {
        String solrClauseOp = SPACED_OR;
        switch (atom.getOp().getOperator()) {
            case NOT_IN:
            case IN:
                AbstractAQLRightHandValue right = atom.getRight();
                if (right instanceof AQLInExpression) {
                    AQLInExpression in = (AQLInExpression) right;
                    for (AbstractAQLAtomicValue value : in.getValues()) {
                        List<String> solrTypeAtoms = new ArrayList<>(2);
                        addTypeAtoms(
                            new AQLAtomicClause(
                                atom.getParserState(),
                                atom.getField(),
                                new AQLAtomicClauseOperator(in.getParserState(), AQLOperator.EQ),
                                value
                            ),
                            convertToSolr(atom, value),
                            solrTypeAtoms
                        );
                        solrAtoms.add(betweenParentheses(String.join(SPACED_AND, solrTypeAtoms)));
                    }
                } else {
                    throw  new ConversionException("BUG: IN expression not found after a (NOT) IN operator." + UNEXP,
                        atom.getParserState());
                }
                break;
            default:
                solrClauseOp = SPACED_AND;
                addTypeAtoms(atom, escapedSolrValue, solrAtoms);
        }
        return solrClauseOp;
    }

    private static void addTypeAtoms(AQLAtomicClause atom, String solrValue, List<String> solrAtoms)
        throws ConversionException
    {
        switch (solrValue) {
            case "page":
                solrAtoms.add(toSolrAtomIgnoringNegativeOp(atom, TYPE, SOLR_DOCUMENT));
                // a page is a document that is not a blog post
                solrAtoms.add('-' + toSolrAtomIgnoringNegativeOp(atom, SOLR_CLASS, BLOG_POST_CLASS));
                break;

            case "blogpost":
                solrAtoms.add(toSolrAtomIgnoringNegativeOp(atom, TYPE, SOLR_DOCUMENT));
                solrAtoms.add(toSolrAtomIgnoringNegativeOp(atom, SOLR_CLASS, BLOG_POST_CLASS));
                break;

            case "comment":
            case "attachment":
                throw  new ConversionException(String.format("CQL type [%s] is not supported yet", solrValue),
                    atom.getRight().getParserState());

            default:
                throw  new ConversionException(String.format("CQL type [%s] is not supported", solrValue),
                    atom.getRight().getParserState());
        }
    }

    private static String toSolrAtomIgnoringNegativeOp(AQLAtomicClause atom, String solrField, String solrValue)
        throws ConversionException
    {
        return solrField + ':' + getSolrAtomValueIgnoringNegativeOp(atom.getOp(), solrValue);
    }

    private static String getSolrAtomValueIgnoringNegativeOp(AQLAtomicClauseOperator operator, String solrValue)
        throws ConversionException
    {
        AQLOperator op = operator.getOperator();
        switch (op) {
            case NEQ:
            case EQ:
            case NOT_IN:
            case IN:
                return solrValue;

            case GT:
                return String.format("{%s TO *]", solrValue);
            case GTE:
                return String.format("[%s TO *]", solrValue);
            case LT:
                return String.format("[* TO %s}", solrValue);
            case LTE:
                return String.format("[* TO %s]", solrValue);

            case DOES_NOT_CONTAIN:
            case CONTAINS:
                // The CQL operator '~' (contains) does both fuzzy and wildcard searches.
                // FIXME: check that it mixes well with wildcard searches (with '*')
                return solrValue + "~";

            default:
                throw  new ConversionException(String.format("BUG: Unexpected operator [%s].", op) + UNEXP,
                    operator.getParserState());
        }
    }

    private static String evalDateFn(AQLFunctionCall fnCall)
        throws ConversionException
    {
        List<AbstractAQLAtomicValue> functionArguments = fnCall.getArguments();
        switch (functionArguments.size()) {
            case 1:
                AbstractAQLAtomicValue a = functionArguments.get(0);
                String t = applyDateInc(a);
                if (t == null) {
                    throw new ConversionException(
                        String.format("Failed to parse the inc parameter for %s", fnCall.getFunctionName()),
                        a.getParserState());
                }
                return t;

            case 0:
                return "";

            default:
                throw new ConversionException(
                    String.format("Too many parameters for date function [%s]. Expected less than 2 parameters",
                        fnCall.getFunctionName()),
                    functionArguments.get(functionArguments.size() - 1).getParserState());
        }
    }

    private static String applyDateInc(AbstractAQLAtomicValue inc) throws ConversionException
    {
        if (!(inc instanceof AQLStringLiteral)) {
            throw new ConversionException("Expected a string argument", inc.getParserState());
        }

        Matcher m = INC_PATTERN.matcher(((AQLStringLiteral) inc).getString());
        if (m.matches()) {
            int n = Integer.parseInt(m.group("n"));
            if (n == 0) {
                return "";
            }
            String suffix = m.group("what");
            String unit = SOLR_UNITS_BY_CQL_SUFFIX.get(suffix);
            if (unit == null) {
                throw new ConversionException(String.format("BUG: suffix [%s] is not supported.", suffix) + UNEXP,
                    inc.getParserState());
            }
            return (n > 0 ? "+" : "") + n + unit;
        }
        return null;
    }

    private static String pad(int n)
    {
        if (n < 10) {
            return "0" + n;
        }
        return "" + n;
    }
}

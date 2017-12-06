/*
 * Copyright 2005 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.drools.template.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This utility class exists to convert rule script snippets to actual code. The
 * snippets contain place holders for values to be substituted into. See the
 * test case for how it really works !
 * <p/>
 * Snippet template example: "something.getBlah($param)" $param is the "place
 * holder". This will get replaced with the "cellValue" that is passed in.
 * <p/>
 * 12-Oct-2005 change: moved from regex to using simple character based interpolation.
 * Regex was overkill and couldn't not quite get it right.
 */
//MACRO macro(macroName, "argument")
public class SnippetBuilder {
    public enum SnippetType {
        SINGLE, PARAM, INDEXED, FORALL, MACRO
    }

    public static final String PARAM_PREFIX = "$";
    public static final String PARAM_SUFFIX = "param";
    public static final String PARAM_STRING = PARAM_PREFIX + PARAM_SUFFIX;
    public static final String PARAM_FORALL_STRING = "forall";
    public static final Pattern PARAM_FORALL_PATTERN = Pattern
            .compile(PARAM_FORALL_STRING + "\\(([^{}]*)\\)\\{([^{}]+)\\}");
    public static final Pattern PARAM_MACRO_PATTERN = Pattern.compile("macro\\((\\w+),.*\"(.*)\"\\)");
    private static final String MACRO_MATCHER = "matcher";
    private static final String MACRO_EQUAL = "equal";
    private final String _template;
    private final SnippetType type;
    private final Pattern delimiter;

    /**
     * @param snippetTemplate The snippet including the "place holder" for a parameter. If
     * no "place holder" is present,
     */
    public SnippetBuilder(final String snippetTemplate) {
        if (snippetTemplate == null) {
            throw new RuntimeException("Script template is null - check for missing script definition.");
        }
        this._template = snippetTemplate;
        this.type = getType(_template);
        this.delimiter = Pattern.compile("(.*?[^\\\\])(,|\\z)");
    }

    public static SnippetType getType(String template) {
        Matcher forallMatcher = PARAM_FORALL_PATTERN.matcher(template);
        Matcher macroMatcher = PARAM_MACRO_PATTERN.matcher(template);
        if (forallMatcher.find()) {
            return SnippetType.FORALL;
        } else if (macroMatcher.find()) {
            return SnippetType.MACRO;
        } else if (template.indexOf(PARAM_PREFIX + "1") != -1) {
            return SnippetType.INDEXED;
        } else if (template.indexOf(PARAM_STRING) != -1) {
            return SnippetType.PARAM;
        }
        return SnippetType.SINGLE;
    }

    /**
     * @param cellValue The value from the cell to populate the snippet with. If no
     *                  place holder exists, will just return the snippet.
     * @return The final snippet.
     */
    public String build(final String cellValue) {
        switch (type) {
            case MACRO:
                return buildMacro(cellValue);
            case FORALL:
                return buildForAll(cellValue);
            case INDEXED:
                return buildMulti(cellValue);
            default:
                return buildSingle(cellValue);
        }
    }

    private String buildMacro(final String cellValue) {
        Matcher matcher = PARAM_MACRO_PATTERN.matcher(_template);
        if (matcher.find()) {
            String macroName = matcher.group(1);
            String argument = matcher.group(2);
            if (macroName.equals(MACRO_MATCHER)) {
                return macroStringMatcher(argument, cellValue);
            } else if (macroName.equals(MACRO_EQUAL)) {
                return macroStringEquality(argument, cellValue);
            }
        }
        return null;
    }

    /**
     * macro(equal, "fieldName")
     * <p/>
     * cellValue 例：11,!1122,33,!3344
     * <p/>
     * 转换为：(fieldName == "11")||(fieldName == "33") , (fieldName != "1122") && (fieldName != "3344")
     */
    private String macroStringEquality(String argument, String cellValue) {
        String[] cellValues = split(cellValue);
        List<String> equales = new ArrayList<String>();
        List<String> notEquales = new ArrayList<String>();
        for (String cell : cellValues) {
            cell = cell.trim();
            if (cell.startsWith("!"))
                notEquales.add(cell.substring(1).trim());
            else
                equales.add(cell.trim());
        }
        StringBuffer sb = new StringBuffer();
        boolean first = true;
        for (String equ : equales) {
            if (!first)
                sb.append("||");
            first = false;
            sb.append(String.format("(%s == \"%s\")", argument, equ));
        }
        if (equales.size() > 0 && notEquales.size() > 0) sb.append(",");
        first = true;
        for (String equ : notEquales) {
            if (!first)
                sb.append("&&");
            first = false;
            sb.append(String.format("(%s != \"%s\")", argument, equ));
        }
        return sb.toString();
    }

    /**
     * macro(matcher, "fieldName")
     * <p/>
     * cellValue 例：11,!1122,33,!3344
     * <p/>
     * 转换为：(fieldName matches "11.*")||(fieldName matches "33.*") , (fieldName not matches "1122.*")&&(fieldName not matches "3344.*")
     */
    private String macroStringMatcher(String argument, String cellValue) {
        String[] cellValues = split(cellValue);
        List<String> matches = new ArrayList<String>();
        List<String> notMatches = new ArrayList<String>();
        for (String cell : cellValues) {
            cell = cell.trim();
            if (cell.startsWith("!")) notMatches.add(cell.substring(1).trim());
            else matches.add(cell.trim());
        }
        StringBuffer sb = new StringBuffer();
        boolean first = true;
        for (String match : matches) {
            if (!first) sb.append("||");
            first = false;
            sb.append(String.format("(%s matches \"%s.*\")", argument, match));
        }
        if (matches.size() > 0 && notMatches.size() > 0) sb.append(",");
        first = true;
        for (String match : notMatches) {
            if (!first) sb.append("&&");
            first = false;
            sb.append(String.format("(%s not matches \"%s.*\")", argument, match));
        }
        return sb.toString();
    }

    private String buildForAll(final String cellValue) {
        final String[] cellVals = split(cellValue);
        Map<String, String> replacements = new HashMap<String, String>();
        Matcher forallMatcher = PARAM_FORALL_PATTERN.matcher(_template);
        while (forallMatcher.find()) {
            replacements.put(forallMatcher.group(), "");
            for (int paramNumber = 0; paramNumber < cellVals.length; paramNumber++) {
                replacements.put(forallMatcher.group(), replacements
                        .get(forallMatcher.group())
                        + (paramNumber == 0 ? "" : " " + forallMatcher.group(1)
                        + " ")
                        + replace(forallMatcher.group(2), PARAM_PREFIX,
                        cellVals[paramNumber].trim(), 256));
            }
        }
        String result = _template;
        for (String key : replacements.keySet()) {
            result = replace(result, key, replacements.get(key), 256);
        }
        return result.equals("") ? _template : result;
    }

    private String buildMulti(final String cellValue) {
        final String[] cellVals = split(cellValue);
        String result = this._template;

        //Replace in reverse order so $10 is replaced before $1 etc
        for ( int paramNumber = cellVals.length - 1; paramNumber >= 0; paramNumber-- ) {
            final String replace = PARAM_PREFIX + ( paramNumber + 1 );
            result = replace( result,
                    replace,
                    cellVals[ paramNumber ].trim(),
                    256 );

        }
        return result;
    }

    private String[] split(String input) {
        Matcher m = delimiter.matcher(input);
        List<String> result = new ArrayList<String>();
        while (m.find()) {
            result.add(m.group(1).replaceAll("\\\\,", ","));
        }
        return result.toArray(new String[result.size()]);
    }

    /**
     * @param cellValue
     * @return
     */
    private String buildSingle(final String cellValue) {
        return replace(this._template,
                PARAM_STRING,
                cellValue,
                256);
    }

    /**
     * Simple replacer.
     * jakarta commons provided the inspiration for this.
     */
    private String replace(final String text,
                           final String repl,
                           final String with,
                           int max) {
        if (text == null || repl == null || repl.equals("") || with == null || max == 0) {
            return text;
        }
        final StringBuffer buf = new StringBuffer(text.length());
        int start = 0, end = 0;
        while ((end = text.indexOf(repl,
                start)) != -1) {
            buf.append(text.substring(start,
                    end)).append(with);
            start = end + repl.length();
            if (--max == 0) {
                break;
            }
        }
        buf.append(text.substring(start));
        return buf.toString();
    }
}